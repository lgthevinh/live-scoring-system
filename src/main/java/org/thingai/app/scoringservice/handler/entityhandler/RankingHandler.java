package org.thingai.app.scoringservice.handler.entityhandler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.ranking.IRankingStrategy;
import org.thingai.app.scoringservice.entity.ranking.RankingEntry;
import org.thingai.app.scoringservice.entity.ranking.RankingStat;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.base.dao.Dao;
import org.thingai.base.log.ILog;

import java.util.Arrays;
import java.util.HashMap;

public class RankingHandler {
    private static final String TAG = "RankingHandler";

    private Dao dao;
    private MatchHandler matchHandler;
    private IRankingStrategy rankingStrategy;

    public RankingHandler(Dao dao, MatchHandler matchHandler) {
        this.dao = dao;
        this.matchHandler = matchHandler;
        this.rankingStrategy = new IndividualTeamRankingStrategy();

        ILog.i(TAG, "RankingHandler initialized with IndividualTeamRankingStrategy");
    }

    /**
     * Updates ranking entries for all teams in a completed match.
     *
     * This method processes the match results and updates each team's ranking statistics:
     * - Increments matches played
     * - Adds ranking points based on win/loss/tie
     * - Accumulates total score and penalties
     * - Tracks highest individual match score
     * - Updates win count
     *
     * @param matchDetailDto Complete match information including teams and scores
     * @param blueScore Final score for blue alliance
     * @param redScore Final score for red alliance
     */
    public void updateRankingEntry(MatchDetailDto matchDetailDto, Score blueScore, Score redScore) {
        RankingStat[] stats = rankingStrategy.setRankingStat(matchDetailDto, blueScore, redScore);
        HashMap<String, Boolean> surrogateTeam = matchDetailDto.getSurrogateMap();
        for (RankingStat stat : stats) {
            // Skip surrogate teams
            if (surrogateTeam.containsKey(stat.getTeamId()) && surrogateTeam.get(stat.getTeamId())) {
                continue;
            }

            // Fetch existing ranking entry
            RankingEntry entry = null;
            try{
                RankingEntry[] entries = dao.query(RankingEntry.class, new String[]{"teamId"}, new String[]{stat.getTeamId()});
                if (entries != null && entries.length > 0) {
                    entry = entries[0];
                }
            } catch (Exception e){
                e.printStackTrace();
                ILog.e(TAG, "Error fetching ranking entry: " + e.getMessage());
            }

            if (entry == null) {
                entry = new RankingEntry();
                entry.setTeamId(stat.getTeamId());
                entry.setRankingPoints(stat.getRankingPoints());
                entry.setTotalScore(stat.getScore());
                entry.setTotalPenalties(stat.getPenalties());
                entry.setMatchesPlayed(1);
                entry.setWins(stat.isWin() ? 1 : 0);
            } else {
                entry.setMatchesPlayed(entry.getMatchesPlayed() + 1);
                entry.setTotalScore(entry.getTotalScore() + stat.getScore());
                entry.setTotalPenalties(entry.getTotalPenalties() + stat.getPenalties());
                entry.setRankingPoints(entry.getRankingPoints() + stat.getRankingPoints());
                if (stat.isWin()) {
                    entry.setWins(entry.getWins() + 1);
                }
            }

            if (stat.getScore() > entry.getHighestScore()) {
                entry.setHighestScore(stat.getScore());
            }

            dao.insertOrUpdate(entry);
        }
    }

    public RankingEntry[] getRankingStatus(RequestCallback<RankingEntry[]> callback) {
        RankingEntry[] entries = dao.readAll(RankingEntry.class);
        RankingEntry[] sortedEntries = rankingStrategy.sortRankingEntries(entries);
        if (callback != null) {
            callback.onSuccess(sortedEntries, "All ranking entries fetched and sorted.");
        }
        return sortedEntries;
    }
    /**
     * Recalculates rankings from scratch using all qualification match data.
     *
     * This operation:
     * 1. Clears all existing ranking data
     * 2. Re-processes every completed qualification match
     * 3. Rebuilds rankings from historical data
     *
     * Useful when match scores have been corrected or ranking logic has changed.
     *
     * @param callback Optional callback for completion notification
     */
    public void recalculateRankings(RequestCallback<Boolean> callback) {
        dao.deleteAll(RankingEntry.class);
        new Thread(() -> {
            try {
                matchHandler.listMatchDetails(MatchType.QUALIFICATION, true, new RequestCallback<MatchDetailDto[]>() {
                    @Override
                    public void onSuccess(MatchDetailDto[] result, String message) {
                        for (MatchDetailDto matchDetail : result) {
                            Score blueScore = matchDetail.getBlueScore();
                            Score redScore = matchDetail.getRedScore();

                            if (matchDetail.getMatch().getActualStartTime() == null) {
                                continue; // Skip matches that haven't started or have no scores
                            }

                            updateRankingEntry(matchDetail, blueScore, redScore);
                        }
                        ILog.i(TAG, "Recalculated rankings for all qualification matches.");
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        ILog.e(TAG, "Failed to fetch match details for recalculating rankings: " + errorMessage);
                    }
                });
                if (callback != null) {
                    callback.onSuccess(true, "Recalculated rankings successfully.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    callback.onFailure(-1, "Error during recalculating rankings: " + e.getMessage());
                }
                ILog.e(TAG, "Error during recalculating rankings: " + e.getMessage());
            }
        }).start();
    }

    public void setRankingStrategy(IRankingStrategy strategy) {
        this.rankingStrategy = strategy;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    static class DefaultRankingStrategy implements IRankingStrategy {
        @Override
        public RankingEntry[] sortRankingEntries(RankingEntry[] entries) {
            // Sort by ranking points descending, then by total score descending, then by penalties ascending
            Arrays.sort(entries, (a, b) -> {
                if (b.getRankingPoints() != a.getRankingPoints()) {
                    return Integer.compare(b.getRankingPoints(), a.getRankingPoints());
                } else if (b.getTotalScore() != a.getTotalScore()) {
                    return Integer.compare(b.getTotalScore(), a.getTotalScore());
                } else {
                    return Integer.compare(a.getTotalPenalties(), b.getTotalPenalties());
                }
            });
            return entries;
        }

        @Override
        public RankingStat[] setRankingStat(MatchDetailDto matchDetailDto, Score blueScore, Score redScore) {
            Team[] redTeams = matchDetailDto.getRedTeams();
            Team[] blueTeams = matchDetailDto.getBlueTeams();
            RankingStat[] stats = new RankingStat[redTeams.length + blueTeams.length];

            int index = 0;
            for (Team team : blueTeams) {
                RankingStat stat = new RankingStat();
                stat.setTeamId(team.getTeamId());
                stat.setScore(blueScore.getTotalScore());
                stat.setPenalties(blueScore.getPenaltiesScore());
                boolean isWin = blueScore.getTotalScore() > redScore.getTotalScore();
                stat.setWin(isWin);
                stat.setRankingPoints(isWin ? 3 : 1);
                stats[index++] = stat;
            }

            for (Team team : redTeams) {
                RankingStat stat = new RankingStat();
                stat.setTeamId(team.getTeamId());
                stat.setScore(redScore.getTotalScore());
                stat.setPenalties(redScore.getPenaltiesScore());
                boolean isWin = redScore.getTotalScore() > blueScore.getTotalScore();
                stat.setWin(isWin);
                stat.setRankingPoints(isWin ? 3 : 1);
                stats[index++] = stat;
            }

            return stats;
        }
    }

    /**
     * Implements individual team ranking strategy for FRC events.
     *
     * Key features:
     * - Each team gets their proportional share of alliance score
     * - Win/loss/tie determined at alliance level but applied to all alliance members
     * - Supports variable alliance sizes (typically 2-3 teams)
     * - Accumulates statistics across all qualification matches
     */
    static class IndividualTeamRankingStrategy implements IRankingStrategy {
        // FRC Official Ranking Points System:
        // - Win: 3 points (beat opponent)
        // - Tie: 2 points (tied with opponent)
        // - Loss: 1 point (lost to opponent)
        private static final int WIN_POINTS = 3;
        private static final int TIE_POINTS = 2;
        private static final int LOSS_POINTS = 1;


        @Override
        public RankingEntry[] sortRankingEntries(RankingEntry[] entries) {
            // Simple score-based ranking:
            // 1. Total Score (descending)
            // 2. Highest Score (descending)
            Arrays.sort(entries, (teamA, teamB) -> {
                // Primary sort: total score
                if (teamB.getTotalScore() != teamA.getTotalScore()) {
                    return Integer.compare(teamB.getTotalScore(), teamA.getTotalScore());
                }
                // Secondary sort: highest single match score
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
}
