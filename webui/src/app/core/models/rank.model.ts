export interface RankingEntry {
    teamId: string;
    matchesPlayed: number;
    wins: number;
    totalScore: number;
    totalPenalties: number;
    highestScore: number;
    rankingPoints: number;
    averageScore: number;
    rank?: number;
}
