package org.thingai.app.scoringservice.matchcontrol;

public class StateManager {
    private static final String TAG = "StateManager";
    private static StateManager instance;

    private StateManager() {
        // Private constructor to prevent instantiation
    }

    public static synchronized StateManager getInstance() {
        if (instance == null) {
            instance = new StateManager();
        }
        return instance;
    }
}
