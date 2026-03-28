import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface MatchControlState {
  loadedMatchId: string | null;
  currentMatchId: string | null;
  state: number | null;
  timerSecondsRemaining?: number | null;
}

export enum DisplayControlAction {
  SHOW_BLANK = 0,
  SHOW_MATCH = 1,
  SHOW_PREVIEW = 2,
  SHOW_RESULT = 3,
  SHOW_RANKING = 4,
  UPDATE_SCORE = 256,
  UPDATE_RANKING = 257,
  UPDATE_MATCH = 258
}

@Injectable({ providedIn: 'root' })
export class MatchControlService {
  private apiUrl = environment.apiBaseUrl + '/api/match-control';

  constructor(private http: HttpClient) {}

  getState(): Observable<MatchControlState> {
    return this.http.get<MatchControlState>(`${this.apiUrl}/state`);
  }

  loadMatch(matchId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/load`, { matchId });
  }

  activateMatch(matchId?: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/activate`, matchId ? { matchId } : {});
  }

  startMatch(): Observable<any> {
    return this.http.post(`${this.apiUrl}/start`, {});
  }

  abortMatch(): Observable<any> {
    return this.http.post(`${this.apiUrl}/abort`, {});
  }

  commitMatch(): Observable<any> {
    return this.http.post(`${this.apiUrl}/commit`, {});
  }

  overrideScore(allianceId: string, scoreData: any, overrides?: { penaltiesScore?: number; totalScore?: number }): Observable<any> {
    const payload = {
      allianceId,
      scoreData,
      penaltiesScore: overrides?.penaltiesScore,
      totalScore: overrides?.totalScore
    };
    return this.http.post(`${this.apiUrl}/override`, payload);
  }

  displayAction(action: DisplayControlAction, data?: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/display`, { action, data });
  }
}
