import { BehaviorSubject, Observable, Subject } from 'rxjs';

/**
 * Wire envelope used by every backend WebSocket frame.
 * Mirrors {@code org.thingai.app.api.ws.WsEnvelope} on the server.
 */
export interface WsEnvelope<T = unknown> {
  type: string;
  ts: number;
  payload: T;
}

/** Connection lifecycle state, suitable for binding to a status indicator. */
export type WsConnectionState = 'idle' | 'connecting' | 'open' | 'closed';

/**
 * Reasons returned by {@link WsClient.disconnect} consumers may want to know
 * about. Mirrors the close codes in
 * {@code org.thingai.app.api.ws.WsCloseCode}.
 */
export const WS_CLOSE = {
  NORMAL: 1000,
  BAD_REQUEST: 4400,
  UNAUTHORIZED: 4401,
  FORBIDDEN: 4403,
  NOT_FOUND: 4404,
  SERVER_ERROR: 4500,
} as const;

/**
 * Low-level reconnecting WebSocket wrapper.
 *
 * <p>Owns one native {@link WebSocket} and exposes:
 * <ul>
 *   <li>{@link messages$} &ndash; stream of parsed envelopes (one shape, every
 *       frame).</li>
 *   <li>{@link state$} &ndash; lifecycle state for status indicators.</li>
 *   <li>{@link send} &ndash; serialize and send if connected; queue while
 *       reconnecting (best-effort, drops if connect never succeeds).</li>
 * </ul>
 *
 * <p>Reconnect strategy: exponential backoff capped at 15s. We do <strong>not</strong>
 * reconnect if the server closed with a 4xxx code (auth failed, match not
 * found, etc.) &mdash; those are configuration errors that won't fix
 * themselves and a quiet reconnect loop would hide them.
 *
 * <p>Each WS service ({@code LiveWsService}, {@code RankingWsService},
 * {@code RefereeWsService}) owns one of these. The class is deliberately
 * unaware of message types: routing by {@code type} is the caller's job.
 */
export class WsClient {
  private readonly url: string;

  private socket: WebSocket | null = null;
  private reconnectTimer: any = null;
  private reconnectAttempt = 0;

  /** Buffer of frames the caller asked to send while we were reconnecting. */
  private pendingSends: string[] = [];

  /** Frames that came in: parsed envelopes only. Parse errors are logged. */
  private readonly messagesSubject = new Subject<WsEnvelope>();
  readonly messages$: Observable<WsEnvelope> = this.messagesSubject.asObservable();

  private readonly stateSubject = new BehaviorSubject<WsConnectionState>('idle');
  readonly state$: Observable<WsConnectionState> = this.stateSubject.asObservable();

  /**
   * Most recent close code seen, useful for debugging from the browser
   * console (e.g. `wsClient.lastCloseCode`).
   */
  lastCloseCode: number | null = null;

  /**
   * @param url    full ws:// or wss:// URL including any auth query string
   * @param label  short tag used in console logs to distinguish multiple
   *               clients in a single tab
   */
  constructor(url: string, private readonly label: string) {
    this.url = url;
  }

  /** Open the socket if it isn't already. Safe to call repeatedly. */
  connect(): void {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }
    this.openSocket();
  }

  /**
   * Close the socket and stop reconnecting. After this the client is dead
   * &mdash; create a new one to start over. Used when the consumer (e.g.
   * referee tablet) navigates away from the screen that owned the socket.
   */
  disconnect(code: number = WS_CLOSE.NORMAL, reason: string = 'client disconnect'): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.socket) {
      try {
        this.socket.close(code, reason);
      } catch {
        // ignore -- socket may already be closing
      }
      this.socket = null;
    }
    this.stateSubject.next('closed');
  }

  /**
   * Send a frame. If the socket isn't open right now the JSON is buffered
   * and flushed when the next connect succeeds. The buffer is bounded
   * (drops oldest at 32 entries) so a permanently-failing connection
   * doesn't grow memory without limit.
   */
  send(envelope: WsEnvelope): void {
    const json = JSON.stringify(envelope);
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      try {
        this.socket.send(json);
      } catch (e) {
        console.warn(`[ws:${this.label}] send failed`, e);
      }
      return;
    }
    if (this.pendingSends.length >= 32) {
      this.pendingSends.shift();
    }
    this.pendingSends.push(json);
  }

  /** Convenience: send {type, ts, payload} with the current wall clock. */
  sendTyped(type: string, payload: unknown): void {
    this.send({ type, ts: Date.now(), payload } as WsEnvelope);
  }

  // --- internals -----------------------------------------------------------

  private openSocket(): void {
    this.stateSubject.next('connecting');
    let socket: WebSocket;
    try {
      socket = new WebSocket(this.url);
    } catch (e) {
      console.error(`[ws:${this.label}] new WebSocket threw`, e);
      this.scheduleReconnect();
      return;
    }
    this.socket = socket;

    socket.onopen = () => {
      console.log(`[ws:${this.label}] open ${this.url}`);
      this.reconnectAttempt = 0;
      this.stateSubject.next('open');
      this.flushPending();
    };

    socket.onmessage = (ev: MessageEvent) => {
      const raw = typeof ev.data === 'string' ? ev.data : null;
      if (raw === null) {
        return; // we don't ship binary frames
      }
      try {
        const env = JSON.parse(raw) as WsEnvelope;
        this.messagesSubject.next(env);
      } catch (e) {
        console.warn(`[ws:${this.label}] bad json`, raw, e);
      }
    };

    socket.onerror = (ev) => {
      // The WebSocket spec hides the cause; rely on onclose for the code.
      console.warn(`[ws:${this.label}] error`, ev);
    };

    socket.onclose = (ev: CloseEvent) => {
      this.lastCloseCode = ev.code;
      this.socket = null;
      this.stateSubject.next('closed');

      const fatal = ev.code >= 4000 && ev.code < 5000;
      if (fatal) {
        console.error(`[ws:${this.label}] closed with ${ev.code} (${ev.reason}); not reconnecting`);
        return;
      }
      console.log(`[ws:${this.label}] closed with ${ev.code} (${ev.reason}); reconnecting`);
      this.scheduleReconnect();
    };
  }

  private scheduleReconnect(): void {
    this.reconnectAttempt += 1;
    // 0.5s, 1s, 2s, 4s, 8s, 15s, 15s, ...
    const base = 500 * Math.pow(2, this.reconnectAttempt - 1);
    const delay = Math.min(base, 15000);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, delay);
  }

  private flushPending(): void {
    if (this.pendingSends.length === 0 || !this.socket) {
      return;
    }
    const queue = this.pendingSends;
    this.pendingSends = [];
    for (const json of queue) {
      try {
        this.socket.send(json);
      } catch (e) {
        console.warn(`[ws:${this.label}] flush send failed`, e);
      }
    }
  }
}
