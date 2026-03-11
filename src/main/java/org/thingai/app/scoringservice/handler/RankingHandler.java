package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.AllianceTeam;
import org.thingai.app.scoringservice.strategy.IRankingStrategy;
import org.thingai.app.scoringservice.entity.RankingEntry;
import org.thingai.app.scoringservice.entity.RankingStat;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.base.dao.exceptions.DaoException;
import org.thingai.base.log.ILog;

import java.util.HashMap;

public class RankingHandler {
    private static final String TAG = "RankingHandler";

    private static IRankingStrategy rankingStrategy;

    public RankingHandler() {
        ILog.i(TAG, "ranking handler");
    }

    /**
     * Update ranking entry of a match played on that team
     * @param matchId
     */
    public void updateRankingEntry(String matchId, RequestCallback<Boolean> callback) {
        MatchDetailDto matchDetailDto = new MatchDetailDto();
        try {
            matchDetailDto.setMatch(LocalRepository.matchDao().getMatchById(matchId));

        } catch (Exception e) {

        }
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

        HashMap<String, Boolean> surrogateTeam = getSurrogateMap(matchDetailDto);
        for (RankingStat stat : stats) {
            // Skip surrogate teams
            if (surrogateTeam.containsKey(stat.getTeamId()) && surrogateTeam.get(stat.getTeamId())) {
                continue;
            }

            // Fetch existing ranking entry
            try {
                RankingEntry entry = LocalRepository.rankEntryDao().getRankingEntryById(stat.getTeamId());

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

                LocalRepository.rankEntryDao().updateRankingEntry(entry);
            } catch (DaoException e) {

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void getRankingStatus(RequestCallback<RankingEntry[]> callback) {
        try {
            RankingEntry[] rankingEntries = LocalRepository.rankEntryDao().listRankingEntries();
            callback.onSuccess(rankingEntries, "Get ranking successfully");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Unable to retrieve ranking");
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

    }

    private HashMap<String, Boolean> getSurrogateMap(MatchDetailDto matchDetail) {
        HashMap<String, Boolean> surrMap = new HashMap<>();
        for (AllianceTeam redTeam : matchDetail.getRedAllianceTeams()) {
            surrMap.put(redTeam.getTeamId(), redTeam.isSurrogate());
        }

        for (AllianceTeam blueTeam: matchDetail.getBlueAllianceTeams()) {
            surrMap.put(blueTeam.getTeamId(), blueTeam.isSurrogate());
        }
        return surrMap;
    }

    public static void setRankingStrategy(IRankingStrategy strategy) {
        rankingStrategy = strategy;
    }
}
