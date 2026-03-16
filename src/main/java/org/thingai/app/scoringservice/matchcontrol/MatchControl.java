package org.thingai.app.scoringservice.matchcontrol;

import org.thingai.app.scoringservice.define.MatchState;
import org.thingai.app.scoringservice.service.MatchTimerService;
import org.thingai.base.log.ILog;

public class MatchControl {
    private static final String TAG = "MatchControl";
    private static final int MATCH_DURATION_SECONDS = 180;

    private final StateManager stateManager;
    private final MatchTimerService matchTimerService;

    public MatchControl(StateManager stateManager) {
        this.stateManager = stateManager;
        this.matchTimerService = new MatchTimerService(MATCH_DURATION_SECONDS);
        this.matchTimerService.setCallback(new MatchTimerService.TimerCallback() {
            @Override
            public void onTimerEnded() {

            }

            @Override
            public void onTimerUpdated(int remainingSeconds) {

            }
        });
    }

    // Match control methods
    public void loadMatch(String matchId) {
        ILog.d(TAG, "loadMatch", matchId);
        stateManager.setCurrentMatchId(matchId);
    }

    public void activeMatch(String matchId) {
        ILog.d(TAG, "activeMatch", matchId);
        stateManager.setCurrentMatchId(matchId);
        stateManager.setCurrentMatchState(MatchState.ACTIVE);
    }

    public void startMatch() {
        ILog.d(TAG, "startMatch", stateManager.getCurrentMatchId());
        stateManager.setCurrentMatchState(MatchState.IN_PROGRESS);
        matchTimerService.startTimer(MATCH_DURATION_SECONDS);
    }

    public void abortMatch() {
        ILog.d(TAG, "abortMatch", stateManager.getCurrentMatchId());
        stateManager.setCurrentMatchState(MatchState.ACTIVE);
    }

    public void commitScore() {
        ILog.d(TAG, "commitScore", stateManager.getCurrentMatchId());
        stateManager.setCurrentMatchState(MatchState.COMPLETED);
    }

    public void overrideScore() {

    }

    // Display control methods
    public void showPreview() {

    }

    public void showMatch() {

    }

    public void postScoreResults(String matchId) {

    }
}
