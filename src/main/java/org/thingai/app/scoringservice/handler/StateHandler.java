package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.entity.Score;

/**
 * StateHandler is responsible for managing the state of the scoring system,
 * including tracking the current match status, time blocks, and any relevant events that occur during a match.
 * It serves as a central point for updating and retrieving the current state of the match, allowing other components to
 * react accordingly.
 */
public class StateHandler {
    private static final String TAG = "StateHandler";

    private String currentMatchId;
    private int currentMatchStatus;

    private Score currentRedScore;
    private Score currentBlueScore;

    public StateHandler() {

    }

}
