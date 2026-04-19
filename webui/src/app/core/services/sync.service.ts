import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { MatchDetailDto } from '../models/match.model';
import { MatchControlState } from './match-control.service';

@Injectable({ providedIn: 'root' })
export class SyncService {
  private matchControlUrl = environment.apiBaseUrl + '/api/match-control';
  private matchApiUrl = environment.apiBaseUrl + '/api/match';

  constructor(private http: HttpClient) {}

  syncPlayingMatches(): Observable<MatchDetailDto[]> {
    return this.getMatchControlState().pipe(
      switchMap(state => {
        const ids = [state.currentMatchId, state.loadedMatchId].filter(Boolean) as string[];
        if (ids.length === 0) {
          return of([] as MatchDetailDto[]);
        }
        return forkJoin(ids.map(id => this.getMatchDetailById(id))).pipe(
          map(matches => matches.filter((m): m is MatchDetailDto => !!m))
        );
      })
    );
  }

  getCurrentMatchField(fieldNumber: number): Observable<MatchDetailDto | null> {
    return this.getMatchControlState().pipe(
      switchMap(state => {
        if (!state.currentMatchId) {
          return of(null);
        }
        return this.getMatchDetailById(state.currentMatchId).pipe(
          map(match => {
            if (!match) {
              return null;
            }
            if (!fieldNumber || fieldNumber === 0) {
              return match;
            }
            return match.match.fieldNumber === fieldNumber ? match : null;
          })
        );
      })
    );
  }

  getUpNextMatch(): Observable<MatchDetailDto | null> {
    return this.getMatchControlState().pipe(
      switchMap(state => {
        if (!state.loadedMatchId) {
          return of(null);
        }
        return this.getMatchDetailById(state.loadedMatchId);
      })
    );
  }

  private getMatchControlState(): Observable<MatchControlState> {
    return this.http.get<MatchControlState>(`${this.matchControlUrl}/state`).pipe(
      catchError(() => of({
        loadedMatchId: null,
        currentMatchId: null,
        state: null,
        timerSecondsRemaining: null
      }))
    );
  }

  private getMatchDetailById(matchId: string): Observable<MatchDetailDto | null> {
    const matchTypes = [0, 1, 2, 3, 4];
    return forkJoin(
      matchTypes.map(matchType =>
        this.http.get<MatchDetailDto[]>(`${this.matchApiUrl}/list/details/${matchType}?withScore=true`).pipe(
          catchError(() => of([] as MatchDetailDto[]))
        )
      )
    ).pipe(
      map(listGroups => listGroups.flat().find(match => match.match.id === matchId) ?? null)
    );
  }
}
