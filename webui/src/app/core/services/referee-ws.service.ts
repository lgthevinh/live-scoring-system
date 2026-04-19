import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription, filter, map, share } from 'rxjs';
import { WS_CLOSE, WsClient, WsConnectionState, WsEnvelope } from './ws-client';
import {
  MatchStatePayload,
  RefereeSnapshotPayload,
  ScoreAckPayload,
  WsMessageType,
} from './ws-types';
import { buildWsUrl } from './live-ws.service';

/**
 * Connects to {@code /ws/referee/{matchId}/{alliance}?token=...} for a single
 * referee tablet. Unlike the public feeds, this service is <strong>not</strong>
 * auto-connected &mdash; the consumer must call {@link connect} with the
 * match and alliance it's refereeing. That matches the UX: the tablet only
 * opens the socket once the referee has picked a match.
 *
 * <p>Auth: reads {@code authToken} from {@code localStorage} and appends it
 * as a query parameter. Browsers can't attach {@code Authorization} headers
 * to WS upgrade requests, so query-param transport is the honest choice.
 *
 * <p>Subscription order: components may subscribe to {@link snapshot$},
 * {@link matchState$}, {@link scoreAck$} <em>before</em> calling
 * {@link connect}. Frames are routed through a service-owned subject and
 * survive client tear-down/reconnect. This matches the natural Angular
 * pattern of wiring observables in {@code ngOnInit} and triggering work
 * later (e.g. after fetching the match).
 *
 * <p>Outbound: {@link sendDraft} writes a {@link WsMessageType.SCORE_DRAFT}
 * frame with the referee's current score state. Replaces the legacy
 * STOMP {@code publishMessage('/app/live/score/update/{color}', ...)}.
 *
 * <p>Close codes that won't auto-reconnect (surfaced via {@link closeCode$}):
 * {@link WS_CLOSE.UNAUTHORIZED}, {@link WS_CLOSE.FORBIDDEN},
 * {@link WS_CLOSE.NOT_FOUND}, {@link WS_CLOSE.BAD_REQUEST}. Everything else
 * retries with backoff.
 */
@Injectable({ providedIn: 'root' })
export class RefereeWsService implements OnDestroy {
  private client: WsClient | null = null;
  private clientSub: Subscription | null = null;
  private clientStateSub: Subscription | null = null;
  private currentUrl: string | null = null;

  /** Bridge: every frame from the active client lands here. */
  private readonly framesSubject = new Subject<WsEnvelope>();
  private readonly stateSubject = new BehaviorSubject<WsConnectionState>('idle');

  /** Emits on fatal (4xxx) close so the UI can show an error banner. */
  private readonly fatalCloseSubject = new Subject<number>();
  readonly closeCode$: Observable<number> = this.fatalCloseSubject.asObservable();

  /**
   * (Re)connect to the given match + alliance. If a socket is already open
   * for a different URL, it is closed first. Calling with the same args
   * while already open is a no-op.
   */
  connect(matchId: string, alliance: 'R' | 'B'): void {
    const token = localStorage.getItem('authToken') ?? '';
    const url = buildWsUrl(`/ws/referee/${encodeURIComponent(matchId)}/${alliance}`, { token });

    if (this.client && this.currentUrl === url) {
      this.client.connect();
      return;
    }

    this.tearDownClient();

    const client = new WsClient(url, `referee:${matchId}_${alliance}`);
    this.clientSub = client.messages$.subscribe((env) => this.framesSubject.next(env));
    this.clientStateSub = client.state$.subscribe((state) => {
      this.stateSubject.next(state);
      if (state === 'closed' && client.lastCloseCode !== null
          && client.lastCloseCode >= 4000 && client.lastCloseCode < 5000) {
        this.fatalCloseSubject.next(client.lastCloseCode);
      }
    });
    client.connect();
    this.client = client;
    this.currentUrl = url;
  }

  /** Close the socket cleanly. Safe to call if never connected. */
  disconnect(): void {
    this.tearDownClient();
  }

  state$(): Observable<WsConnectionState> {
    return this.stateSubject.asObservable();
  }

  /**
   * Emit a SCORE_DRAFT frame. {@code state} is the season-specific score
   * fields object &mdash; the server wraps it into the legacy
   * {@code {matchId, alliance, state}} shape before passing to ScoreControl.
   */
  sendDraft(state: unknown): void {
    if (!this.client) {
      console.warn('[referee] sendDraft called with no active connection');
      return;
    }
    this.client.sendTyped(WsMessageType.SCORE_DRAFT, { state });
  }

  // --- inbound streams ----------------------------------------------------

  snapshot$(): Observable<RefereeSnapshotPayload> {
    return this.framesOfType<RefereeSnapshotPayload>(WsMessageType.SNAPSHOT);
  }

  matchState$(): Observable<MatchStatePayload> {
    return this.framesOfType<MatchStatePayload>(WsMessageType.MATCH_STATE);
  }

  scoreAck$(): Observable<ScoreAckPayload> {
    return this.framesOfType<ScoreAckPayload>(WsMessageType.SCORE_ACK);
  }

  ngOnDestroy(): void {
    this.tearDownClient();
    this.fatalCloseSubject.complete();
    this.framesSubject.complete();
    this.stateSubject.complete();
  }

  private tearDownClient(): void {
    if (this.clientSub) {
      this.clientSub.unsubscribe();
      this.clientSub = null;
    }
    if (this.clientStateSub) {
      this.clientStateSub.unsubscribe();
      this.clientStateSub = null;
    }
    if (this.client) {
      this.client.disconnect();
      this.client = null;
    }
    this.currentUrl = null;
  }

  private framesOfType<T>(type: string): Observable<T> {
    return this.framesSubject.asObservable().pipe(
      filter((env) => env.type === type),
      map((env) => env.payload as T),
      share(),
    );
  }
}
