import { Injectable } from '@angular/core';
import { Observable, merge } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { BroadcastService } from './broadcast.service';
import { BroadcastMessage } from '../models/broadcast.model';

export const BroadcastTopics = {
  liveMatch: '/live/match',
  liveScoreRed: '/live/score/red',
  liveScoreBlue: '/live/score/blue',
  liveDisplayScore: '/live/display/score',
  liveDisplayControl: '/live/display/control',
  liveDisplayRanking: '/live/display/ranking'
} as const;

export type BroadcastTopic = typeof BroadcastTopics[keyof typeof BroadcastTopics];

export enum BroadcastEventType {
  MatchState = 'MATCH_STATE',
  ScoreUpdate = 'SCORE_UPDATE',
  ScoreOverride = 'SCORE_OVERRIDE',
  DisplayControl = 'DISPLAY_CONTROL',
  RankingUpdate = 'RANKING_UPDATE'
}

export interface BroadcastEvent<T = any> {
  type: BroadcastEventType | string;
  payload: T;
}

export interface MatchStatePayload {
  matchId: string;
  state: number;
  timerSecondsRemaining?: number;
  r?: string[];
  b?: string[];
}

export interface ScoreUpdatePayload {
  matchId: string;
  r?: any;
  b?: any;
}

export interface DisplayControlPayload {
  action: number;
  data?: any;
}

@Injectable({ providedIn: 'root' })
export class BroadcastEventsService {
  constructor(private broadcast: BroadcastService) {}

  listen<T = any>(topic: BroadcastTopic, expectedType?: BroadcastEventType): Observable<BroadcastEvent<T>> {
    const streams = [this.normalizeTopic(topic)].map(dest => this.broadcast.subscribeToTopic(dest));

    return merge(...streams).pipe(
      map((msg: BroadcastMessage) => ({ type: msg.type, payload: msg.payload } as BroadcastEvent<T>)),
      filter(event => !expectedType || event.type === expectedType)
    );
  }

  matchState$(): Observable<BroadcastEvent<MatchStatePayload>> {
    return this.listen<MatchStatePayload>(BroadcastTopics.liveMatch, BroadcastEventType.MatchState);
  }

  scoreRed$(): Observable<BroadcastEvent<ScoreUpdatePayload>> {
    return this.listen<ScoreUpdatePayload>(BroadcastTopics.liveScoreRed);
  }

  scoreBlue$(): Observable<BroadcastEvent<ScoreUpdatePayload>> {
    return this.listen<ScoreUpdatePayload>(BroadcastTopics.liveScoreBlue);
  }

  displayControl$(): Observable<BroadcastEvent<DisplayControlPayload>> {
    return this.listen<DisplayControlPayload>(BroadcastTopics.liveDisplayControl, BroadcastEventType.DisplayControl);
  }

  rankingUpdate$(): Observable<BroadcastEvent<any>> {
    return this.listen(BroadcastTopics.liveDisplayRanking, BroadcastEventType.RankingUpdate);
  }

  private normalizeTopic(topic: string): string {
    if (!topic.startsWith('/')) {
      return `/${topic}`;
    }
    return topic;
  }
}
