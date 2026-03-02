import { Team } from "./team.model";
import { Score } from './score.model';

export interface Match {
  id: string;
  matchCode: string;
  matchType: number;
  matchNumber: number;
  fieldNumber: number;
  matchStartTime: string;
  matchEndTime: string | null;
}

export interface MatchDetailDto {
  match: Match;
  redTeams: Team[];
  blueTeams: Team[];
  redScore?: Score;
  blueScore?: Score;
  surrogateMap?: { [teamId: string]: boolean };
  hasTempScore?: boolean;
  redTempScore?: Score;
  blueTempScore?: Score;
}

export function SampleMatchDetailDto(size: number): MatchDetailDto[] {
  let sampleMatchDetails: MatchDetailDto[] = [];
  for (let i = 1; i <= size; i++) {
    sampleMatchDetails.push({
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
      surrogateMap: i === 1 ? { [`B${i}2`]: true } : {}
    });
  }
  return sampleMatchDetails;

}
