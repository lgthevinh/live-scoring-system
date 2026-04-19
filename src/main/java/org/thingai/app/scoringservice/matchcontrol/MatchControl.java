package org.thingai.app.scoringservice.matchcontrol;

import org.thingai.app.scoringservice.define.DisplayControlAction;
import org.thingai.app.scoringservice.define.LiveBroadcastTopic;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.define.MatchState;
import org.thingai.app.scoringservice.define.ScoreState;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.AllianceTeam;
import org.thingai.app.scoringservice.entity.Match;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.app.scoringservice.service.BroadcastService;
import org.thingai.app.scoringservice.service.MatchTimerService;
import org.thingai.base.log.ILog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class MatchControl {
    private static final String TAG = "MatchControl";
    private static final int MATCH_DURATION_SECONDS = 180;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final StateManager stateManager;
    private final MatchTimerService matchTimerService;

    public MatchControl(StateManager stateManager) {
        this.stateManager = stateManager;
        this.matchTimerService = new MatchTimerService(MATCH_DURATION_SECONDS);
        this.matchTimerService.setCallback(new MatchTimerService.TimerCallback() {
            @Override
            public void onTimerEnded() {
                ILog.d(TAG, "timerEnded", stateManager.getCurrentMatchId());
                stateManager.setCurrentMatchState(MatchState.ON_REVIEW);
                persistMatchState(stateManager.getCurrentMatchId(), MatchState.ON_REVIEW, false);
                broadcastMatchState(stateManager.getCurrentMatchId(), MatchState.ON_REVIEW, 0);
            }

            @Override
            public void onTimerUpdated(int remainingSeconds) {
                broadcastMatchState(stateManager.getCurrentMatchId(), stateManager.getCurrentMatchState(), remainingSeconds);
            }
        });
    }

    // Match control methods
    public void loadMatch(String matchId) {
        ILog.d(TAG, "loadMatch", matchId);
        stateManager.setLoadedMatchId(matchId);
        if (stateManager.getCurrentMatchId() == null) {
            stateManager.setCurrentMatchId(matchId);
        }
        stateManager.setCurrentMatchState(MatchState.LOADED);
        persistMatchState(matchId, MatchState.LOADED, false);
        broadcastMatchState(matchId, MatchState.LOADED, matchTimerService.getRemainingSeconds());
    }

    public void activeMatch(String matchId) {
        ILog.d(TAG, "activeMatch", matchId);
        stateManager.setCurrentMatchId(matchId);
        stateManager.setLoadedMatchId(matchId);
        stateManager.setCurrentMatchState(MatchState.ACTIVE);
        persistMatchState(matchId, MatchState.ACTIVE, false);
        broadcastMatchState(matchId, MatchState.ACTIVE, matchTimerService.getRemainingSeconds());
    }

    public void startMatch() {
        ILog.d(TAG, "startMatch", stateManager.getCurrentMatchId());
        stateManager.setCurrentMatchState(MatchState.IN_PROGRESS);
        persistMatchState(stateManager.getCurrentMatchId(), MatchState.IN_PROGRESS, false);
        matchTimerService.startTimer(MATCH_DURATION_SECONDS);
        broadcastMatchState(stateManager.getCurrentMatchId(), MatchState.IN_PROGRESS, matchTimerService.getRemainingSeconds());
    }

    public void abortMatch() {
        ILog.d(TAG, "abortMatch", stateManager.getCurrentMatchId());
        matchTimerService.stopTimer();
        stateManager.setCurrentMatchState(MatchState.ACTIVE);
        persistMatchState(stateManager.getCurrentMatchId(), MatchState.ACTIVE, false);
        broadcastMatchState(stateManager.getCurrentMatchId(), MatchState.ACTIVE, matchTimerService.getRemainingSeconds());
    }

    public void commitScore() {
        ILog.d(TAG, "commitScore", stateManager.getCurrentMatchId());
        if (!areScoresReadyToCommit(stateManager.getCurrentMatchId())) {
            ILog.w(TAG, "commitScore", "scores not ready to commit");
            return;
        }
        matchTimerService.stopTimer();
        stateManager.setCurrentMatchState(MatchState.COMPLETED);
        persistMatchState(stateManager.getCurrentMatchId(), MatchState.COMPLETED, true);
        broadcastMatchState(stateManager.getCurrentMatchId(), MatchState.COMPLETED, matchTimerService.getRemainingSeconds());
    }

    public boolean overrideScore(String matchId, Score score) {
        if (matchId == null || matchId.isBlank() || score == null) {
            ILog.w(TAG, "overrideScore", "matchId and score are required");
            return false;
        }

        if (!isMatchCompleted(matchId)) {
            ILog.w(TAG, "overrideScore", "match not completed");
            return false;
        }

        String allianceId = score.getAllianceId();
        if (allianceId == null || allianceId.isBlank()) {
            ILog.w(TAG, "overrideScore", "score missing allianceId");
            return false;
        }
        if (!allianceId.startsWith(matchId + "_")) {
            ILog.w(TAG, "overrideScore", "allianceId does not match matchId");
            return false;
        }

        score.setState(ScoreState.SCORED);
        stateManager.cacheScore(allianceId, score);

        try {
            ScoringService.scoreHandler().updateScore(score);
        } catch (Exception e) {
            ILog.e(TAG, "overrideScore", e.getMessage());
            return false;
        }

        broadcastScoreUpdate(matchId);
        return true;
    }

    // Display control methods
    public void showPreview() {
        broadcastDisplayAction(DisplayControlAction.SHOW_PREVIEW, Map.of("matchId", stateManager.getLoadedMatchId()));
    }

    public void showMatch() {
        broadcastDisplayAction(DisplayControlAction.SHOW_MATCH, Map.of("matchId", stateManager.getCurrentMatchId()));
    }

    public void postScoreResults(String matchId) {
        broadcastDisplayAction(DisplayControlAction.SHOW_RESULT, Map.of("matchId", matchId));
    }

    public int getRemainingSeconds() {
        return matchTimerService.getRemainingSeconds();
    }

    private void persistMatchState(String matchId, int state, boolean setEndTime) {
        if (matchId == null) {
            return;
        }
        try {
            Match match = LocalRepository.matchDao().getMatchById(matchId);
            if (match == null) {
                return;
            }

            match.setMatchStatus(state);
            if (state == MatchState.IN_PROGRESS) {
                if (match.getActualStartTime() == null || match.getActualStartTime().isBlank()) {
                    match.setActualStartTime(LocalDateTime.now().format(TIME_FORMATTER));
                }
            }
            if (setEndTime) {
                match.setMatchEndTime(LocalDateTime.now().format(TIME_FORMATTER));
            }

            LocalRepository.matchDao().updateMatch(match);
        } catch (Exception e) {
            ILog.e(TAG, "persistMatchState", e.getMessage());
        }
    }

    private void broadcastMatchState(String matchId, int state, Integer remainingSeconds) {
        if (matchId == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("state", state);
        payload.put("matchId", matchId);
        if (remainingSeconds != null) {
            payload.put("timerSecondsRemaining", remainingSeconds);
        }

        try {
            MatchDetailDto matchDetail = LocalRepository.matchDao().getMatchDetailById(matchId);
            if (matchDetail != null) {
                payload.put("r", toTeamIds(matchDetail.getRedAllianceTeams()));
                payload.put("b", toTeamIds(matchDetail.getBlueAllianceTeams()));
            }
        } catch (Exception e) {
            ILog.e(TAG, "broadcastMatchState", e.getMessage());
        }

        BroadcastService.broadcast(LiveBroadcastTopic.LIVE_MATCH, payload, "MATCH_STATE");
    }

    private boolean areScoresReadyToCommit(String matchId) {
        if (matchId == null || matchId.isBlank()) {
            return false;
        }
        String redId = matchId + "_R";
        String blueId = matchId + "_B";

        Score redScore = stateManager.getCachedScore(redId);
        Score blueScore = stateManager.getCachedScore(blueId);

        if (redScore == null || blueScore == null) {
            try {
                MatchDetailDto matchDetail = LocalRepository.matchDao().getMatchDetailById(matchId);
                if (redScore == null) {
                    redScore = matchDetail != null ? matchDetail.getRedScore() : null;
                }
                if (blueScore == null) {
                    blueScore = matchDetail != null ? matchDetail.getBlueScore() : null;
                }
            } catch (Exception e) {
                ILog.e(TAG, "areScoresReadyToCommit", e.getMessage());
            }
        }

        return redScore != null
                && blueScore != null
                && redScore.getState() == ScoreState.READY_TO_COMMIT
                && blueScore.getState() == ScoreState.READY_TO_COMMIT;
    }

    private void broadcastDisplayAction(int action, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        if (data != null) {
            payload.put("data", data);
        }
        BroadcastService.broadcast(LiveBroadcastTopic.LIVE_DISPLAY_CONTROL, payload, "DISPLAY_CONTROL");
    }

    private void broadcastScoreUpdate(String matchId) {
        if (matchId == null || matchId.isBlank()) {
            return;
        }
        try {
            MatchDetailDto matchDetail = LocalRepository.matchDao().getMatchDetailById(matchId);
            if (matchDetail == null) {
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("matchId", matchId);
            payload.put("r", matchDetail.getRedScore());
            payload.put("b", matchDetail.getBlueScore());

            BroadcastService.broadcast(LiveBroadcastTopic.LIVE_SCORE_UPDATE_RED, payload, "SCORE_OVERRIDE");
            BroadcastService.broadcast(LiveBroadcastTopic.LIVE_SCORE_UPDATE_BLUE, payload, "SCORE_OVERRIDE");
        } catch (Exception e) {
            ILog.e(TAG, "broadcastScoreUpdate", e.getMessage());
        }
    }

    private boolean isMatchCompleted(String matchId) {
        if (stateManager.getCurrentMatchState() == MatchState.COMPLETED) {
            return true;
        }
        try {
            Match match = LocalRepository.matchDao().getMatchById(matchId);
            return match != null && match.getMatchStatus() == MatchState.COMPLETED;
        } catch (Exception e) {
            ILog.e(TAG, "isMatchCompleted", e.getMessage());
            return false;
        }
    }

    private String[] toTeamIds(AllianceTeam[] allianceTeams) {
        if (allianceTeams == null) {
            return new String[0];
        }
        String[] teamIds = new String[allianceTeams.length];
        for (int i = 0; i < allianceTeams.length; i++) {
            teamIds[i] = allianceTeams[i].getTeamId();
        }
        return teamIds;
    }
}
