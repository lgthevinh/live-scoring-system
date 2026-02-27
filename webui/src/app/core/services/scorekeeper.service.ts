import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {environment} from '../../../environments/environment';

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
    console.log('ScorekeeperService: overrideScore called for', allianceId);
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
}
