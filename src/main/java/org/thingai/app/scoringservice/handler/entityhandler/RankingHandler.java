package org.thingai.app.scoringservice.handler.entityhandler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.ranking.IRankingStrategy;
import org.thingai.app.scoringservice.entity.ranking.RankingEntry;
import org.thingai.app.scoringservice.entity.ranking.RankingStat;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.base.dao.Dao;
import org.thingai.base.log.ILog;

import java.util.Arrays;
import java.util.HashMap;

public class RankingHandler {
    private static final String TAG = "RankingHandler";

    private final Dao dao;
    private final MatchHandler matchHandler;
    private static IRankingStrategy rankingStrategy;

    public RankingHandler(Dao dao, MatchHandler matchHandler) {
        this.dao = dao;
        this.matchHandler = matchHandler;

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
        ILog.i(TAG, "updateRankingEntry called for match with " +
            (matchDetailDto.getRedTeams() != null ? matchDetailDto.getRedTeams().length : 0) + " red teams, " +
            (matchDetailDto.getBlueTeams() != null ? matchDetailDto.getBlueTeams().length : 0) + " blue teams");
        if (rankingStrategy == null) {
            ILog.e(TAG, "CRITICAL: rankingStrategy is NULL!");
            return;
        }
        RankingStat[] stats = rankingStrategy.setRankingStat(matchDetailDto, blueScore, redScore);
        HashMap<String, Boolean> surrogateTeam = matchDetailDto.getSurrogateMap();
        for (RankingStat stat : stats) {
            ILog.d(TAG, "Processing team " + stat.getTeamId() + " with score " + stat.getScore());
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
                ILog.d(TAG, "Found existing entry for " + stat.getTeamId() + ": " + (entry != null ? "YES" : "NO"));
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

            ILog.i(TAG, "Saving ranking entry for " + entry.getTeamId() + ": matches=" + entry.getMatchesPlayed() +
                ", totalScore=" + entry.getTotalScore() + ", highest=" + entry.getHighestScore());
            try {
                dao.insertOrUpdate(entry);
                System.out.println("[DEBUG] Successfully saved ranking entry for team: " + entry.getTeamId());
            } catch (Exception e) {
                System.err.println("[DEBUG] FAILED to save ranking entry: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void getRankingStatus(RequestCallback<RankingEntry[]> callback) {
        ILog.i(TAG, "getRankingStatus called");
        if (rankingStrategy == null) {
            ILog.e(TAG, "CRITICAL: rankingStrategy is NULL in getRankingStatus!");
            if (callback != null) {
                callback.onFailure(ErrorCode.CUSTOM_ERR, "Ranking strategy not initialized");
            }
            return;
        }
        System.out.println("[DEBUG] getRankingStatus: Querying RankingEntry from dao: " + dao.getClass().getName());
        RankingEntry[] entries = dao.readAll(RankingEntry.class);
        System.out.println("[DEBUG] getRankingStatus: entries array is " + (entries != null ? "not null, length=" + entries.length : "null"));
        ILog.i(TAG, "Found " + (entries != null ? entries.length : 0) + " ranking entries in database");
        if (entries != null && entries.length > 0) {
            for (RankingEntry entry : entries) {
                ILog.d(TAG, "Entry: team=" + entry.getTeamId() + ", matches=" + entry.getMatchesPlayed() + ", score=" + entry.getTotalScore());
            }
        }
        RankingEntry[] sortedEntries = rankingStrategy.sortRankingEntries(entries);
        ILog.i(TAG, "Returning " + (sortedEntries != null ? sortedEntries.length : 0) + " sorted entries");
        if (callback != null) {
            callback.onSuccess(sortedEntries, "All ranking entries fetched and sorted.");
        }
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
                        System.out.println("[DEBUG] recalculateRankings: Processing " + (result != null ? result.length : 0) + " matches");
                        for (MatchDetailDto matchDetail : result) {
                            Score blueScore = matchDetail.getBlueScore();
                            Score redScore = matchDetail.getRedScore();
                            
                            System.out.println("[DEBUG] Match " + matchDetail.getMatch().getMatchCode() + 
                                ": actualStartTime=" + matchDetail.getMatch().getActualStartTime() +
                                ", blueScore=" + (blueScore != null ? "not null" : "null") +
                                ", redScore=" + (redScore != null ? "not null" : "null"));

                            if (blueScore == null || redScore == null) {
                                System.out.println("[DEBUG] Skipping match - missing scores");
                                continue;
                            }
                            
                            // Check if scores are actually scored (status = 1)
                            if (blueScore.getStatus() == 0 || redScore.getStatus() == 0) {
                                System.out.println("[DEBUG] Skipping match - scores not finalized");
                                continue;
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

    public static void setRankingStrategy(IRankingStrategy strategy) {
        rankingStrategy = strategy;
    }
}
