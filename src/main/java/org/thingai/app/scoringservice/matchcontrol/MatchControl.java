package org.thingai.app.scoringservice.matchcontrol;

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

    }

    public void activateMatch(String matchId) {
    }

    public void startMatch() {

    }

    public void abortMatch() {

    }

    public void commitScore() {

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
