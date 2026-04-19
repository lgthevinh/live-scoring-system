import { Injectable, OnDestroy } from '@angular/core';
import { Observable, filter, map, share } from 'rxjs';
import { environment } from '../../../environments/environment';
import { WsClient, WsConnectionState, WsEnvelope } from './ws-client';
import {
  DisplayControlPayload,
  LiveSnapshotPayload,
  MatchStatePayload,
  ScoreUpdatePayload,
  WsMessageType,
} from './ws-types';

/**
 * Connects to {@code /ws/live} and exposes typed observables for each
 * frame kind on that endpoint.
 *
 * <p>Singleton (root-provided): one socket per browser tab regardless of how
 * many components subscribe. The socket opens lazily on first subscription
 * to any of the {@code *$} streams &mdash; passive screens that never
 * subscribe never open the connection.
 *
 * <p>Frames carried: {@link WsMessageType.SNAPSHOT}, {@link WsMessageType.MATCH_STATE},
 * {@link WsMessageType.SCORE_UPDATE}, {@link WsMessageType.DISPLAY_CONTROL}.
 *
 * <p>No authentication on this endpoint &mdash; matches the server-side
 * decision to keep all read-only display feeds public.
 */
@Injectable({ providedIn: 'root' })
export class LiveWsService implements OnDestroy {
  private readonly client: WsClient;

  constructor() {
    this.client = new WsClient(buildWsUrl('/ws/live'), 'live');
  }

  /** Connection state, exposed for UI status indicators. */
  state$(): Observable<WsConnectionState> {
    return this.client.state$;
  }

  /**
   * One SNAPSHOT frame per (re)connect. Emits whatever the server's
   * registry has cached &mdash; fields may be null on a cold server.
   */
  snapshot$(): Observable<LiveSnapshotPayload> {
    return this.framesOfType<LiveSnapshotPayload>(WsMessageType.SNAPSHOT);
  }

  matchState$(): Observable<MatchStatePayload> {
    return this.framesOfType<MatchStatePayload>(WsMessageType.MATCH_STATE);
  }

  scoreUpdate$(): Observable<ScoreUpdatePayload> {
    return this.framesOfType<ScoreUpdatePayload>(WsMessageType.SCORE_UPDATE);
  }

  displayControl$(): Observable<DisplayControlPayload> {
    return this.framesOfType<DisplayControlPayload>(WsMessageType.DISPLAY_CONTROL);
  }

  ngOnDestroy(): void {
    this.client.disconnect();
  }

  // --- internals ----------------------------------------------------------

  /**
   * Opens the socket on first subscription, multicasts so multiple
   * subscribers share one stream, filters by frame {@code type}, and
   * unwraps to the payload.
   */
  private framesOfType<T>(type: string): Observable<T> {
    return new Observable<WsEnvelope>((sub) => {
      this.client.connect();
      const inner = this.client.messages$.subscribe(sub);
      return () => inner.unsubscribe();
    }).pipe(
      filter((env) => env.type === type),
      map((env) => env.payload as T),
      share(),
    );
  }
}

/**
 * Build a {@code ws[s]://host[:port]/path} URL from {@link environment.apiBaseUrl}
 * if set, else from the current page origin. Mirrors the legacy
 * BroadcastService URL resolution so dev and prod behave the same.
 */
export function buildWsUrl(path: string, query?: Record<string, string>): string {
  const apiBase = environment.apiBaseUrl?.trim();
  let base: string;
  if (apiBase) {
    base = apiBase.replace(/\/$/, '').replace(/^http/, 'ws');
  } else {
    const proto = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    base = proto + window.location.host;
  }
  let url = base + path;
  if (query) {
    const qs = Object.entries(query)
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&');
    if (qs) {
      url += (url.includes('?') ? '&' : '?') + qs;
    }
  }
  return url;
}
