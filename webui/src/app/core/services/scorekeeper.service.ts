import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TempScore } from '../models/score.model';

@Injectable({ providedIn: 'root' })
export class ScorekeeperService {
  private apiUrl = environment.apiBaseUrl + '/api/scorekeeper';

  constructor(private http: HttpClient) {}

  setNextMatch(matchId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/set-next-match/${matchId}`, {});
  }

  startCurrentMatch(): Observable<any> {
    return this.http.post(`${this.apiUrl}/start-current-match`, {});
  }

  activateMatch(): Observable<any> {
    return this.http.post(`${this.apiUrl}/activate-match`, {});
  }

  commitFinalScore(): Observable<any> {
    return this.http.post(`${this.apiUrl}/commit-final-score`, {});
  }

  overrideScore(allianceId: string, scoreData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/override-score/${allianceId}`, scoreData);
  }

  abortCurrentMatch(): Observable<any> {
    return this.http.post(`${this.apiUrl}/abort-current-match`, {});
  }

  showUpNext(): Observable<any> {
    return this.http.post(`${this.apiUrl}/show-upnext`, {});
  }

  showCurrentMatch(): Observable<any> {
    return this.http.post(`${this.apiUrl}/show-current-match`, {});
  }

  getPendingScores(matchId: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/pending-scores/${matchId}`);
  }

  approveScore(allianceId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/approve-score/${allianceId}`, {});
  }

  rejectScore(allianceId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/reject-score/${allianceId}`, {});
  }

  saveTempScore(allianceId: string, scoreData: any, submittedBy: string): Observable<{ tempScoreId: string }> {
    return this.http.post<{ tempScoreId: string }>(`${this.apiUrl}/temp-score/${allianceId}`, {
      scoreData,
      submittedBy
    });
  }

  getTempScores(allianceId: string): Observable<TempScore[]> {
    return this.http.get<TempScore[]>(`${this.apiUrl}/temp-scores/${allianceId}`);
  }

  getTempScore(allianceId: string): Observable<TempScore | null> {
    return this.http.get<TempScore | null>(`${this.apiUrl}/temp-score/${allianceId}`);
  }

  commitTempScore(tempScoreId: string, approvedBy: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/commit-temp-score/${tempScoreId}`, { approvedBy });
  }

  rejectTempScore(tempScoreId: string, rejectedBy: string, reason: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/reject-temp-score/${tempScoreId}`, {
      rejectedBy,
      reason
    });
  }
}
