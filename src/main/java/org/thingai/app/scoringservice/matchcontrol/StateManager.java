package org.thingai.app.scoringservice.matchcontrol;

import org.thingai.app.scoringservice.entity.Score;
import org.thingai.base.cache.LRUCache;

import java.util.HashMap;

public class StateManager {
    private static final String TAG = "Orchestrator";

    private String currentMatchId;
    private int currentMatchState;
    private String loadedMatchId;

    private LRUCache<String, Score> scoresCache; // map score id, score object

    public StateManager() {
        this.scoresCache = new LRUCache<>(10, new HashMap<>());
    }

    public String getCurrentMatchId() {
        return currentMatchId;
    }

    public void setCurrentMatchId(String currentMatchId) {
        this.currentMatchId = currentMatchId;
    }

    public int getCurrentMatchState() {
        return currentMatchState;
    }

    public void setCurrentMatchState(int currentMatchState) {
        this.currentMatchState = currentMatchState;
    }

    public String getLoadedMatchId() {
        return loadedMatchId;
    }

    public void setLoadedMatchId(String loadedMatchId) {
        this.loadedMatchId = loadedMatchId;
    }

    public void cacheScore(String allianceId, Score score) {
        if (allianceId == null || score == null) {
            return;
        }
        scoresCache.put(allianceId, score);
    }

    public Score getCachedScore(String allianceId) {
        if (allianceId == null) {
            return null;
        }
        return scoresCache.get(allianceId);
    }
}
