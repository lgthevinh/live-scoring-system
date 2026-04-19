import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Team } from '../models/team.model';
import {environment} from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class TeamService {
  private apiUrl = environment.apiBaseUrl + '/api/teams';

  constructor(private http: HttpClient) { }

  getTeams(): Observable<Team[]> {
    return this.http.get<Team[]>(this.apiUrl);
  }

  addTeam(team: Team): Observable<Team> {
    return this.http.post<Team>(this.apiUrl, team);
  }

  updateTeam(team: Team): Observable<Team> {
    return this.http.put<Team>(`${this.apiUrl}/${team.teamId}`, team);
  }

  deleteTeam(id: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`);
  }

  listTeams(): Observable<Team[]> {
    return this.http.get<Team[]>(this.apiUrl);
  }

  exportTeams(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/export`, { responseType: 'blob' });
  }

  importTeams(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.apiUrl}/import`, formData);
  }
}
