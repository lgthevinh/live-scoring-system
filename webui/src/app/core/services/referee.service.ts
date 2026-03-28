import { environment } from '../../../environments/environment';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class RefereeService {
  private apiUrl = environment.apiBaseUrl + '/api/scores';

  constructor(
    private http: HttpClient
  ) { }

  submitFinalScore(matchId: string, allianceId: string, scoreData: any) {
    console.log('Submitting final score:', matchId, allianceId, scoreData);
    return this.http.post(`${this.apiUrl}/submit`, {
      matchId,
      allianceId,
      score: scoreData
    });
  }

  getScoreUiDefinitions() {
    return this.http.get<any[]>(`${this.apiUrl}/define`);
  }

}
