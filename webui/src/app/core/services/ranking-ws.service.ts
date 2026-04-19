import { Injectable, OnDestroy } from '@angular/core';
import { Observable, filter, map, share } from 'rxjs';
import { WsClient, WsConnectionState, WsEnvelope } from './ws-client';
import { RankingSnapshotPayload, WsMessageType } from './ws-types';
import { buildWsUrl } from './live-ws.service';

/**
 * Connects to {@code /ws/ranking} and exposes typed observables for
 * SNAPSHOT and RANKING_UPDATE frames.
 *
 * <p>Singleton; lazy-connect on first subscription. No authentication.
 *
 * <p>Why separate from {@link import('./live-ws.service').LiveWsService}: a
 * standalone ranking projector only wants this stream and shouldn't pay the
 * traffic cost of the full live feed (which includes per-second timer
 * ticks). Mirrors the server-side endpoint split.
 */
@Injectable({ providedIn: 'root' })
export class RankingWsService implements OnDestroy {
  private readonly client: WsClient;

  constructor() {
    this.client = new WsClient(buildWsUrl('/ws/ranking'), 'ranking');
  }

  state$(): Observable<WsConnectionState> {
    return this.client.state$;
  }

  /** SNAPSHOT frame on (re)connect. */
  snapshot$(): Observable<RankingSnapshotPayload> {
    return this.framesOfType<RankingSnapshotPayload>(WsMessageType.SNAPSHOT);
  }

  /**
   * RANKING_UPDATE frame after each match commit. Payload is the raw
   * ranking array; cast to your season's {@code RankingEntry[]}.
   */
  rankingUpdate$<T = unknown>(): Observable<T[]> {
    return this.framesOfType<T[]>(WsMessageType.RANKING_UPDATE);
  }

  ngOnDestroy(): void {
    this.client.disconnect();
  }

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
