package org.thingai.app.fanroc;

import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.ranking.IRankingStrategy;
import org.thingai.app.scoringservice.entity.ranking.RankingEntry;
import org.thingai.app.scoringservice.entity.ranking.RankingStat;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;

import java.util.Arrays;

/*
 * Implements individual team ranking strategy for FRC events.
 *
 * Key features:
 * - Each team gets their proportional share of alliance score
 * - Win/loss/tie determined at alliance level but applied to all alliance members
 * - Supports variable alliance sizes (typically 2-3 teams)
 * - Accumulates statistics across all qualification matches
 */
public class FanrocRankingStrategy implements IRankingStrategy {
    // FRC Official Ranking Points System:
    // - Win: 3 points (beat opponent)
    // - Tie: 2 points (tied with opponent)
    // - Loss: 1 point (lost to opponent)
    private static final int WIN_POINTS = 3;
    private static final int TIE_POINTS = 2;
    private static final int LOSS_POINTS = 1;


    @Override
    public RankingEntry[] sortRankingEntries(RankingEntry[] entries) {
        // Sort by average score (descending), then total score as tiebreaker
        Arrays.sort(entries, (teamA, teamB) -> {
            // Primary sort: average score
            if (teamB.getAverageScore() != teamA.getAverageScore()) {
                return Integer.compare(teamB.getAverageScore(), teamA.getAverageScore());
            }
            // Secondary sort: total score
            if (teamB.getTotalScore() != teamA.getTotalScore()) {
                return Integer.compare(teamB.getTotalScore(), teamA.getTotalScore());
            }
            // Tertiary sort: highest single match score
            return Integer.compare(teamB.getHighestScore(), teamA.getHighestScore());
        });
        return entries;
    }

    @Override
    public RankingStat[] setRankingStat(MatchDetailDto matchDetailDto, Score blueScore, Score redScore) {
        Team[] redAllianceTeams = matchDetailDto.getRedTeams();
        Team[] blueAllianceTeams = matchDetailDto.getBlueTeams();

        // Calculate total teams participating in this match
        int totalTeamsInMatch = redAllianceTeams.length + blueAllianceTeams.length;
        RankingStat[] teamMatchResults = new RankingStat[totalTeamsInMatch];

        // Determine match outcome for alliance-based ranking
        int blueAllianceScore = blueScore.getTotalScore();
        int redAllianceScore = redScore.getTotalScore();

        boolean blueAllianceWins = blueAllianceScore > redAllianceScore;
        boolean redAllianceWins = redAllianceScore > blueAllianceScore;
        boolean matchIsTied = blueAllianceScore == redAllianceScore;

        int blueScorePerTeam = blueAllianceScore;
        int redScorePerTeam = redAllianceScore;

        int bluePenaltiesPerTeam = blueScore.getPenaltiesScore();
        int redPenaltiesPerTeam = redScore.getPenaltiesScore();

        int resultIndex = 0;

        // Award ranking points to blue alliance teams
        for (Team team : blueAllianceTeams) {
            RankingStat teamResult = new RankingStat();
            teamResult.setTeamId(team.getTeamId());
            teamResult.setScore(blueScorePerTeam);
            teamResult.setPenalties(bluePenaltiesPerTeam);

            // Determine ranking points based on alliance performance
            if (blueAllianceWins) {
                teamResult.setWin(true);
                teamResult.setRankingPoints(WIN_POINTS);
            } else if (matchIsTied) {
                teamResult.setWin(false);
                teamResult.setRankingPoints(TIE_POINTS);
            } else {
                teamResult.setWin(false);
                teamResult.setRankingPoints(LOSS_POINTS);
            }

            teamMatchResults[resultIndex++] = teamResult;
        }

        // Award ranking points to red alliance teams
        for (Team team : redAllianceTeams) {
            RankingStat teamResult = new RankingStat();
            teamResult.setTeamId(team.getTeamId());
            teamResult.setScore(redScorePerTeam);
            teamResult.setPenalties(redPenaltiesPerTeam);

            // Determine ranking points based on alliance performance
            if (redAllianceWins) {
                teamResult.setWin(true);
                teamResult.setRankingPoints(WIN_POINTS);
            } else if (matchIsTied) {
                teamResult.setWin(false);
                teamResult.setRankingPoints(TIE_POINTS);
            } else {
                teamResult.setWin(false);
                teamResult.setRankingPoints(LOSS_POINTS);
            }

            teamMatchResults[resultIndex++] = teamResult;
        }

        return teamMatchResults;
    }
}