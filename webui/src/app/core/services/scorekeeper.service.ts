import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { TempScore } from '../models/score.model';

/**
 * Scorekeeper temp-score workflow client.
 *
 * Backend endpoints (`/api/scorekeeper/temp-score/*`) are not implemented
 * on the Javalin server yet. Mutating methods return {@code throwError}
 * so callers (e.g. {@link ScoreSubmitBufferService}) correctly mark
 * submissions as errored instead of recording fake success. Read methods
 * return an empty list/null since "nothing pending" is a valid state.
 *
 * Replace bodies with real HttpClient calls once the endpoints land.
 */
@Injectable({ providedIn: 'root' })
export class ScorekeeperService {
  constructor(private http: HttpClient) {}

  saveTempScore(
    allianceId: string,
    payload: unknown,
    submittedBy: string,
  ): Observable<{ tempScoreId: string }> {
    return throwError(() => new Error('ScorekeeperService.saveTempScore: backend not implemented'));
  }

  commitTempScore(tempScoreId: string, approvedBy: string): Observable<unknown> {
    return throwError(() => new Error('ScorekeeperService.commitTempScore: backend not implemented'));
  }

  rejectTempScore(
    tempScoreId: string,
    rejectedBy: string,
    reason: string,
  ): Observable<unknown> {
    return throwError(() => new Error('ScorekeeperService.rejectTempScore: backend not implemented'));
  }

  getTempScores(allianceId: string): Observable<TempScore[]> {
    // Empty list is a valid "nothing pending" state — safe.
    return new Observable(sub => { sub.next([]); sub.complete(); });
  }

  getTempScore(allianceId: string): Observable<TempScore | null> {
    return new Observable(sub => { sub.next(null); sub.complete(); });
  }
}
