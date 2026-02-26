import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { MatchDetailDto } from '../models/match.model';
import { Score } from '../models/score.model';
import { RandomUtils } from '../utils/RandomUtils';
import { environment } from '../../../environments/environment';
import { TimeBlock } from '../models/timeblock.model';

export abstract class MatchService {
  protected abstract apiUrl: string;
  protected abstract scoreApiUrl: string;

  abstract getMatches(matchType: number, withScore: boolean): Observable<MatchDetailDto[]>;
  abstract generateSchedule(scheduleConfig: any): Observable<any>;

  // Add V2 signature
  abstract generateScheduleV2(scheduleConfig: {
    rounds: number;
    startTime: string;        // "yyyy-MM-dd'T'HH:mm"
    matchDuration: number;    // minutes
    fieldCount?: number;    // optional, defaults to number of available fields
    timeBlocks: TimeBlock[];  // breaks
  }): Observable<{ message: string }>;

  abstract generatePlayoffSchedule(scheduleConfig: {
    playoffType: number;
    startTime: string;
    matchDuration: number;
    fieldCount: number;
    allianceTeams: { allianceId: string; teamId: string }[];
    timeBlocks: TimeBlock[];
  }): Observable<{ message: string }>;

  abstract createMatch(matchConfig: {
    matchType: number;
    matchNumber: number;
    matchStartTime: string;
    redTeamIds: string[];
    blueTeamIds: string[];
  }): Observable<{ message: string; matchId?: string }>;

  abstract getScore(allianceId: string): Observable<Score>;
  abstract submitScore(allianceId: string, scoreData: any): Observable<Score>;
}

@Injectable({
  providedIn: 'root'
})
export class ProdMatchService extends MatchService {
  protected apiUrl = environment.apiBaseUrl + '/api/match';
  protected scoreApiUrl = environment.apiBaseUrl + '/api/score';

  constructor(private http: HttpClient) {
    super();
  }

  override getMatches(matchType: number, withScore: boolean = false): Observable<MatchDetailDto[]> {
    if (withScore) {
      // add withScore as query param
      return this.http.get<MatchDetailDto[]>(`${this.apiUrl}/list/details/${matchType}?withScore=true`);
    }
    return this.http.get<MatchDetailDto[]>(`${this.apiUrl}/list/details/${matchType}`);
  }

  override generateSchedule(scheduleConfig: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/schedule/generate`, scheduleConfig);
  }

  // Robust to empty 200 body; always returns an object with message
  override generateScheduleV2(payload: {
    rounds: number;
    startTime: string;
    matchDuration: number;
    fieldCount: number | 1;
    timeBlocks: TimeBlock[];
  }): Observable<{ message: string }> {
    return this.http.post(`${this.apiUrl}/schedule/generate/v2`, payload, { observe: 'response' }).pipe(
      map(res => {
        const body = res.body as any;
        const message = body?.message ?? 'Schedule generated successfully.';
        return { message };
      })
    );
  }

  override generatePlayoffSchedule(payload: {
    playoffType: number;
    startTime: string;
    matchDuration: number;
    fieldCount: number;
    allianceTeams: { allianceId: string; teamId: string }[];
    timeBlocks: TimeBlock[];
  }): Observable<{ message: string }> {
    return this.http.post(`${this.apiUrl}/schedule/generate/playoff`, payload, { observe: 'response' }).pipe(
      map(res => {
        const body = res.body as any;
        const message = body?.message ?? 'Playoff schedule generated successfully.';
        return { message };
      })
    );
  }

  override createMatch(payload: {
    matchType: number;
    matchNumber: number;
    matchStartTime: string;
    redTeamIds: string[];
    blueTeamIds: string[];
  }): Observable<{ message: string; matchId?: string }> {
    return this.http.post(`${this.apiUrl}/create`, payload, { observe: 'response' }).pipe(
      map(res => {
        const body = res.body as any;
        const message = body?.message ?? 'Match created successfully.';
        const matchId = body?.matchId;
        return { message, matchId };
      })
    );
  }

  override getScore(allianceId: string): Observable<Score> {
    return this.http.get<Score>(`${this.scoreApiUrl}/alliance/${allianceId}`);
  }

  override submitScore(allianceId: string, scoreData: any): Observable<Score> {
    return this.http.post<Score>(`${this.scoreApiUrl}/submit/${allianceId}`, scoreData);
  }
}

@Injectable({
  providedIn: 'root'
})
export class MockMatchService extends MatchService {
  protected apiUrl = environment.apiBaseUrl + '/api/match';
  protected scoreApiUrl = environment.apiBaseUrl + '/api/score';

  constructor() { super(); }

  override getMatches(matchType: number, withScore: boolean): Observable<MatchDetailDto[]> {
    return new Observable<MatchDetailDto[]>(observer => {
      const mockMatches: MatchDetailDto[] = [];
      for (let i = 1; i <= 20; i++) {
        mockMatches.push({
          match: {
            id: `match-${i}`,
            matchCode: `Q${i}`,
            matchType: 1,
            matchNumber: i,
            fieldNumber: 1,
            matchStartTime: new Date().toISOString(),
            matchEndTime: null
          },
          redTeams: [
            { teamId: `R${i}1`, teamName: `Red Team ${i}1`, teamSchool: `School ${i}1`, teamRegion: `Region ${i}1` },
            { teamId: `R${i}2`, teamName: `Red Team ${i}2`, teamSchool: `School ${i}2`, teamRegion: `Region ${i}2` },
          ],
          blueTeams: [
            { teamId: `B${i}1`, teamName: `Blue Team ${i}1`, teamSchool: `School ${i}1`, teamRegion: `Region ${i}1` },
            { teamId: `B${i}2`, teamName: `Blue Team ${i}2`, teamSchool: `School ${i}2`, teamRegion: `Region ${i}2` },
          ],
          redScore: {
            id: `red-${i}`,
            status: 1,
            totalScore: 100,
            penaltiesScore: 0,
            rawScoreData: JSON.stringify({
              whiteBallsScored: 5,
              goldenBallsScored: 5,
              allianceBarrierPushed: true,
              opponentBarrierPushed: false,
              imbalanceCategory: 0,
              partialParking: 1,
              fullParking: 2,
              minorPenalties: 1,
              majorPenalties: 0
            })
          },
          blueScore: {
            id: `blue-${i}`,
            status: 1,
            totalScore: 80,
            penaltiesScore: 0,
            rawScoreData: JSON.stringify({
              whiteBallsScored: 3,
              goldenBallsScored: 2,
              allianceBarrierPushed: false,
              opponentBarrierPushed: true,
              imbalanceCategory: 1,
              partialParking: 1,
              fullParking: 1,
              minorPenalties: 0,
              majorPenalties: 0
            })
          }
        });
      }
      observer.next(mockMatches);
      observer.complete();
    });
  }

  override generateSchedule(scheduleConfig: any): Observable<any> {
    return new Observable<any>(observer => {
      observer.next({ message: 'Mock schedule generated' });
      observer.complete();
    });
  }

  override generateScheduleV2(_: {
    rounds: number;
    startTime: string;
    matchDuration: number;
    timeBlocks: TimeBlock[];
  }): Observable<{ message: string }> {
    return new Observable(observer => {
      observer.next({ message: 'Mock schedule generated (V2)' });
      observer.complete();
    });
  }

  override generatePlayoffSchedule(_: any): Observable<{ message: string }> {
    return new Observable(observer => {
      observer.next({ message: 'Mock playoff schedule generated' });
      observer.complete();
    });
  }

  override createMatch(_: any): Observable<{ message: string; matchId?: string }> {
    return new Observable(observer => {
      observer.next({ message: 'Mock match created', matchId: 'mock-match-id' });
      observer.complete();
    });
  }

  override getScore(allianceId: string): Observable<Score> {
    return new Observable<Score>(observer => {
      const mockScore: Score = {
        id: allianceId,
        status: 1,
        penaltiesScore: RandomUtils.generateRandomNumber(0, 50),
        totalScore: RandomUtils.generateRandomNumber(100, 300),
        rawScoreData: JSON.stringify({
          whiteBallsScored: RandomUtils.generateRandomNumber(0, 15),
          goldenBallsScored: RandomUtils.generateRandomNumber(0, 15),
          allianceBarrierPushed: RandomUtils.generateRandomNumber(0, 1) === 1,
          opponentBarrierPushed: RandomUtils.generateRandomNumber(0, 1) === 1,
          imbalanceCategory: RandomUtils.generateRandomNumber(0, 2),
          partialParking: RandomUtils.generateRandomNumber(0, 2),
          fullParking: RandomUtils.generateRandomNumber(0, 2),
          minorPenalties: RandomUtils.generateRandomNumber(0, 5),
          majorPenalties: RandomUtils.generateRandomNumber(0, 3)
        })
      };
      observer.next(mockScore);
      observer.complete();
    });
  }

  override submitScore(allianceId: string, scoreData: any): Observable<Score> {
    return new Observable<Score>(observer => {
      const submittedScore: Score = {
        id: allianceId,
        status: 2,
        penaltiesScore: scoreData.penaltiesScore || 0,
        totalScore: scoreData.totalScore || 0,
        rawScoreData: JSON.stringify(scoreData)
      };
      observer.next(submittedScore);
      observer.complete();
    });
  }
}
