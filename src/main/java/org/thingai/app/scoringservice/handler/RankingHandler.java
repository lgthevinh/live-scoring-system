package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.MatchType;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.ranking.IRankingStrategy;
import org.thingai.app.scoringservice.entity.ranking.RankingEntry;
import org.thingai.app.scoringservice.entity.ranking.RankingStat;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.base.dao.Dao;
import org.thingai.base.dao.exceptions.DaoException;
import org.thingai.base.log.ILog;

import java.util.HashMap;

public class RankingHandler {
    private static final String TAG = "RankingHandler";

    private final Dao dao;
    private final ScheduleHandler scheduleHandler;
    private static IRankingStrategy rankingStrategy;

    public RankingHandler(Dao dao, ScheduleHandler scheduleHandler) {
        this.dao = dao;
        this.scheduleHandler = scheduleHandler;

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

            try {
                dao.insertOrUpdate(entry);
            } catch (DaoException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void getRankingStatus(RequestCallback<RankingEntry[]> callback) {
        RankingEntry[] entries = null;
        try {
            entries = dao.readAll(RankingEntry.class);
        } catch (DaoException e) {
            throw new RuntimeException(e);
        }
        RankingEntry[] sortedEntries = rankingStrategy.sortRankingEntries(entries);
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
        try {
            dao.deleteAll(RankingEntry.class);
        } catch (DaoException e) {
            e.printStackTrace();
            ILog.e(TAG, "Failed to clear existing rankings: " + e.getMessage());
            if (callback != null) {
                callback.onFailure(-1, "Failed to clear existing rankings: " + e.getMessage());
            }
            return;
        }
        new Thread(() -> {
            try {
                scheduleHandler.listMatchDetails(MatchType.QUALIFICATION, true, new RequestCallback<MatchDetailDto[]>() {
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

    public static void setRankingStrategy(IRankingStrategy strategy) {
        rankingStrategy = strategy;
    }
}
