package org.thingai.app.scoringservice.handler.entityhandler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.ranking.IRankingStrategy;
import org.thingai.app.scoringservice.entity.ranking.RankingEntry;
import org.thingai.app.scoringservice.entity.ranking.RankingStat;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.team.Team;
import org.thingai.base.dao.Dao;
import org.thingai.base.log.ILog;

import java.util.HashMap;

public class RankingHandler {
    private static final String TAG = "RankingHandler";

    private final Dao dao;
    private final MatchHandler matchHandler;
    private static IRankingStrategy rankingStrategy;

    public RankingHandler(Dao dao, MatchHandler matchHandler) {
        this.dao = dao;
        this.matchHandler = matchHandler;
        ILog.i(TAG, "RankingHandler initialized");
    }

    /**
     * Updates rankings by fetching all match data and recalculating from scratch.
     * This ensures consistency with match results data.
     */
    public void updateRankings(RequestCallback<Boolean> callback) {
        ILog.i(TAG, "Updating rankings from match data...");
        
        // Clear existing rankings
        dao.deleteAll(RankingEntry.class);
        
        // Fetch all qualification matches with scores (like match-results page)
        matchHandler.listMatchDetails(MatchType.QUALIFICATION, true, new RequestCallback<MatchDetailDto[]>() {
            @Override
            public void onSuccess(MatchDetailDto[] matches, String message) {
                if (matches == null || matches.length == 0) {
                    ILog.i(TAG, "No matches found");
                    if (callback != null) callback.onSuccess(true, "No matches to process");
                    return;
                }
                
                ILog.i(TAG, "Processing " + matches.length + " matches for rankings");
                
                // Process each match and update rankings
                int processedMatches = 0;
                for (MatchDetailDto match : matches) {
                    Score blueScore = match.getBlueScore();
                    Score redScore = match.getRedScore();
                    
                    // Skip matches without both scores finalized
                    if (blueScore == null || redScore == null) {
                        continue;
                    }
                    if (blueScore.getStatus() == 0 || redScore.getStatus() == 0) {
                        continue;
                    }
                    
                    // Calculate ranking stats for this match
                    if (rankingStrategy == null) {
                        ILog.w(TAG, "Ranking strategy not initialized, skipping match processing");
                        continue;
                    }
                    RankingStat[] stats = rankingStrategy.setRankingStat(match, blueScore, redScore);
                    HashMap<String, Boolean> surrogateMap = match.getSurrogateMap();
                    
                    // Update each team's ranking entry
                    for (RankingStat stat : stats) {
                        String teamId = stat.getTeamId();
                        
                        // Skip surrogate teams
                        if (surrogateMap.containsKey(teamId) && surrogateMap.get(teamId)) {
                            continue;
                        }
                        
                        updateTeamRanking(teamId, stat);
                    }
                    processedMatches++;
                }
                
                ILog.i(TAG, "Rankings updated from " + processedMatches + " matches");
                if (callback != null) {
                    callback.onSuccess(true, "Rankings updated successfully");
                }
            }
            
            @Override
            public void onFailure(int errorCode, String errorMessage) {
                ILog.e(TAG, "Failed to fetch matches: " + errorMessage);
                if (callback != null) {
                    callback.onFailure(errorCode, errorMessage);
                }
            }
        });
    }
    
    /**
     * Updates a single team's ranking entry with match stats.
     */
    private void updateTeamRanking(String teamId, RankingStat stat) {
        try {
            // Fetch existing entry or create new
            RankingEntry entry = null;
            RankingEntry[] entries = dao.query(RankingEntry.class, new String[]{"teamId"}, new String[]{teamId});
            if (entries != null && entries.length > 0) {
                entry = entries[0];
            }
            
            if (entry == null) {
                // Create new entry
                entry = new RankingEntry();
                entry.setTeamId(teamId);
                entry.setMatchesPlayed(1);
                entry.setTotalScore(stat.getScore());
                entry.setTotalPenalties(stat.getPenalties());
                entry.setRankingPoints(stat.getRankingPoints());
                entry.setWins(stat.isWin() ? 1 : 0);
                entry.setHighestScore(stat.getScore());
                entry.setAverageScore(stat.getScore());
            } else {
                // Update existing entry
                entry.setMatchesPlayed(entry.getMatchesPlayed() + 1);
                entry.setTotalScore(entry.getTotalScore() + stat.getScore());
                entry.setTotalPenalties(entry.getTotalPenalties() + stat.getPenalties());
                entry.setRankingPoints(entry.getRankingPoints() + stat.getRankingPoints());
                if (stat.isWin()) {
                    entry.setWins(entry.getWins() + 1);
                }
                if (stat.getScore() > entry.getHighestScore()) {
                    entry.setHighestScore(stat.getScore());
                }
                entry.setAverageScore((int)(entry.getTotalScore() / (double) entry.getMatchesPlayed()));
            }
            
            dao.insertOrUpdate(entry);
        } catch (Exception e) {
            ILog.e(TAG, "Error updating ranking for team " + teamId + ": " + e.getMessage());
        }
    }

    /**
     * Called when a match score changes - triggers full recalculation.
     */
    public void updateRankingEntry(MatchDetailDto matchDetailDto, Score blueScore, Score redScore) {
        // Simply trigger full recalculation from match data
        updateRankings(new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result, String message) {
                ILog.i(TAG, "Rankings updated after match score change");
            }
            
            @Override
            public void onFailure(int errorCode, String errorMessage) {
                ILog.e(TAG, "Failed to update rankings: " + errorMessage);
            }
        });
    }

    public void getRankingStatus(RequestCallback<RankingEntry[]> callback) {
        if (rankingStrategy == null) {
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Ranking strategy not initialized");
            return;
        }
        
        RankingEntry[] entries = dao.readAll(RankingEntry.class);
        RankingEntry[] sortedEntries = rankingStrategy.sortRankingEntries(entries);
        callback.onSuccess(sortedEntries, "Rankings fetched successfully.");
    }

    /**
     * Recalculates rankings from scratch - now just calls updateRankings.
     */
    public void recalculateRankings(RequestCallback<Boolean> callback) {
        updateRankings(callback);
    }

    public static void setRankingStrategy(IRankingStrategy strategy) {
        rankingStrategy = strategy;
    }
}
