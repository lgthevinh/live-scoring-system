package org.thingai.app.scoringservice.matchcontrol;

import org.thingai.app.scoringservice.define.MatchState;
import org.thingai.app.scoringservice.service.MatchTimerService;

public class MatchControl {
    private static final String TAG = "MatchControl";
    private static final int MATCH_DURATION_SECONDS = 180;

    private StateManager stateManager;
    private MatchTimerService matchTimerService;

    public MatchControl(StateManager stateManager) {
        this.stateManager = stateManager;
        this.matchTimerService = new MatchTimerService(MATCH_DURATION_SECONDS);
    }

    // Match control methods
    public void loadMatch(String matchId) {
        stateManager.setCurrentMatchId(matchId);
    }

    public void activateMatch(String matchId) {
        stateManager.setCurrentMatchId(matchId);
        stateManager.setCurrentMatchState(MatchState.ACTIVE);
    }

    public void startMatch() {
        stateManager.setCurrentMatchState(MatchState.IN_PROGRESS);
    }

    public void abortMatch() {
        stateManager.setCurrentMatchState(MatchState.ACTIVE);
    }

    public void commitScore() {
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
