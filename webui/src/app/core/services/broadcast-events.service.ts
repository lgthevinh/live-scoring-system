import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { LiveWsService } from './live-ws.service';
import { RankingWsService } from './ranking-ws.service';
import {
  DisplayControlPayload,
  MatchStatePayload,
  ScoreUpdatePayload,
} from './ws-types';

/**
 * Legacy event-shaped surface, preserved so existing consumers
 * (match-control, etc.) keep working after the STOMP → raw-WebSocket
 * migration. New code should use {@link LiveWsService} /
 * {@link RankingWsService} directly.
 *
 * <p>The old API exposed observables of {@code BroadcastEvent<T>} with
 * {@code {type, payload}} shape. We re-wrap each typed stream here so
 * callers that destructure {@code event.payload} don't need to change.
 */

export enum BroadcastEventType {
  MatchState = 'MATCH_STATE',
  ScoreUpdate = 'SCORE_UPDATE',
  ScoreOverride = 'SCORE_OVERRIDE',
  DisplayControl = 'DISPLAY_CONTROL',
  RankingUpdate = 'RANKING_UPDATE',
}

export interface BroadcastEvent<T = any> {
  type: BroadcastEventType | string;
  payload: T;
}

// Re-export payload shapes so files that imported these from the old module
// path keep compiling.
export type { MatchStatePayload, ScoreUpdatePayload, DisplayControlPayload };

@Injectable({ providedIn: 'root' })
export class BroadcastEventsService {
  constructor(
    private live: LiveWsService,
    private ranking: RankingWsService,
  ) {}

  matchState$(): Observable<BroadcastEvent<MatchStatePayload>> {
    return this.live
      .matchState$()
      .pipe(map((payload) => ({ type: BroadcastEventType.MatchState, payload })));
  }

  /**
   * All score updates arrive on one stream in the new WS model (payload
   * carries both alliances in {@code r}/{@code b}). We emit the same event
   * to both {@link scoreRed$} and {@link scoreBlue$} so callers that had
   * separate subscriptions keep working. Red/blue differentiation should
   * be done by reading {@code payload.r} / {@code payload.b}.
   */
  scoreRed$(): Observable<BroadcastEvent<ScoreUpdatePayload>> {
    return this.live
      .scoreUpdate$()
      .pipe(map((payload) => ({ type: BroadcastEventType.ScoreUpdate, payload })));
  }

  scoreBlue$(): Observable<BroadcastEvent<ScoreUpdatePayload>> {
    return this.live
      .scoreUpdate$()
      .pipe(map((payload) => ({ type: BroadcastEventType.ScoreUpdate, payload })));
  }

  displayControl$(): Observable<BroadcastEvent<DisplayControlPayload>> {
    return this.live
      .displayControl$()
      .pipe(map((payload) => ({ type: BroadcastEventType.DisplayControl, payload })));
  }

  rankingUpdate$(): Observable<BroadcastEvent<unknown>> {
    return this.ranking
      .rankingUpdate$<unknown>()
      .pipe(map((payload) => ({ type: BroadcastEventType.RankingUpdate, payload })));
  }
}
