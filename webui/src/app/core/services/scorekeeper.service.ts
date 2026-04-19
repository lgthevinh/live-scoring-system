import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { TempScore } from '../models/score.model';

/**
 * Scorekeeper temp-score workflow client.
 *
 * NOTE: this is a stub added to unblock the Angular build — the matching
 * backend endpoints for temp-score review have not been wired into the
 * Javalin server yet. Each method returns an empty Observable / no-op
 * response so the UI compiles and can be exercised without crashing.
 * Replace method bodies with real {@link HttpClient} calls once the
 * {@code /api/scorekeeper/temp-score/*} endpoints are live.
 */
@Injectable({ providedIn: 'root' })
export class ScorekeeperService {
  constructor(private http: HttpClient) {}

  /** POST a pending score for scorekeeper review. */
  saveTempScore(
    allianceId: string,
    payload: unknown,
    submittedBy: string,
  ): Observable<{ tempScoreId: string }> {
    // TODO: wire to /api/scorekeeper/temp-score
    return of({ tempScoreId: 'stub-' + allianceId + '-' + Date.now() });
  }

  /** Commit a previously-saved temp score to the match as final. */
  commitTempScore(tempScoreId: string, approvedBy: string): Observable<unknown> {
    // TODO: wire to /api/scorekeeper/temp-score/{id}/commit
    return of({ ok: true, tempScoreId, approvedBy });
  }

  /** Reject a temp score with a reason. */
  rejectTempScore(
    tempScoreId: string,
    rejectedBy: string,
    reason: string,
  ): Observable<unknown> {
    // TODO: wire to /api/scorekeeper/temp-score/{id}/reject
    return of({ ok: true, tempScoreId, rejectedBy, reason });
  }

  /** List all temp scores for an alliance (pending review). */
  getTempScores(allianceId: string): Observable<TempScore[]> {
    // TODO: wire to /api/scorekeeper/temp-score?allianceId=...
    return of([]);
  }

  /**
   * Legacy single-temp-score getter. Kept for backwards-compat with
   * {@link ScoreSubmitBufferService.getBackendTempScore}.
   */
  getTempScore(allianceId: string): Observable<TempScore | null> {
    return of(null);
  }
}
