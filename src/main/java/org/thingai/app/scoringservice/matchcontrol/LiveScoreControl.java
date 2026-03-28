package org.thingai.app.scoringservice.matchcontrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.thingai.app.scoringservice.define.ScoreState;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.handler.ScoreHandler;
import org.thingai.base.log.ILog;

import java.util.Map;

public class LiveScoreControl {
    private static final String TAG = "LiveScoreControl";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateManager stateManager;

    public LiveScoreControl(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void handleLiveScoreUpdate(String jsonUpdate, String allianceColor) {
        if (jsonUpdate == null || jsonUpdate.isBlank()) {
            return;
        }
        if (stateManager == null) {
            ILog.w(TAG, "StateManager unavailable for live score update");
            return;
        }

        try {
            Map<String, Object> root = objectMapper.readValue(jsonUpdate, Map.class);
            Object payloadObj = root.get("payload");
            Map<String, Object> payload = payloadObj instanceof Map
                    ? (Map<String, Object>) payloadObj
                    : root;

            String matchId = getString(payload.get("matchId"));
            String alliance = getString(payload.get("alliance"));
            String allianceId = getString(payload.get("allianceId"));
            if (alliance == null || alliance.isBlank()) {
                alliance = allianceColor;
            }

            Object stateObj = payload.get("state");
            if (!(stateObj instanceof Map)) {
                ILog.w(TAG, "Live score update missing state payload");
                return;
            }

            if (matchId == null || matchId.isBlank()) {
                ILog.w(TAG, "Live score update missing matchId");
                return;
            }

            if (allianceId == null || allianceId.isBlank()) {
                allianceId = buildAllianceId(matchId, alliance);
            }
            if (allianceId == null) {
                ILog.w(TAG, "Live score update missing alliance id");
                return;
            }

            String stateJson = objectMapper.writeValueAsString(stateObj);

            Score score = stateManager.getCachedScore(allianceId);
            if (score == null) {
                score = ScoreHandler.factoryScore();
                score.setAllianceId(allianceId);
            } else if (score.getAllianceId() == null || score.getAllianceId().isBlank()) {
                score.setAllianceId(allianceId);
            }
            score.fromJson(stateJson);
            score.calculatePenalties();
            score.calculateTotalScore();
            score.setRawScoreData(stateJson);
            score.setState(ScoreState.ON_REVIEW);

            stateManager.cacheScore(allianceId, score);
        } catch (Exception e) {
            ILog.e(TAG, "handleLiveScoreUpdate", e.getMessage());
        }
    }

    public void handleScoreSubmit(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return;
        }
        if (stateManager == null) {
            ILog.w(TAG, "StateManager unavailable for score submit");
            return;
        }

        try {
            Map<String, Object> root = objectMapper.readValue(jsonBody, Map.class);
            Map<String, Object> payload = extractPayload(root);

            String matchId = getString(payload.get("matchId"));
            String allianceId = getString(payload.get("allianceId"));
            if (allianceId == null || allianceId.isBlank()) {
                String alliance = getString(payload.get("alliance"));
                allianceId = buildAllianceId(matchId, alliance);
            }
            if (allianceId == null || allianceId.isBlank()) {
                ILog.w(TAG, "Score submit missing allianceId");
                return;
            }

            Object scoreObj = payload.get("score");
            if (scoreObj == null) {
                scoreObj = payload.get("scoreData");
            }
            if (scoreObj == null) {
                ILog.w(TAG, "Score submit missing score payload");
                return;
            }

            String scoreJson = objectMapper.writeValueAsString(scoreObj);

            Score score = stateManager.getCachedScore(allianceId);
            if (score == null) {
                score = ScoreHandler.factoryScore();
                score.setAllianceId(allianceId);
            } else if (score.getAllianceId() == null || score.getAllianceId().isBlank()) {
                score.setAllianceId(allianceId);
            }

            score.fromJson(scoreJson);
            score.calculatePenalties();
            score.calculateTotalScore();
            score.setRawScoreData(scoreJson);
            score.setState(ScoreState.READY_TO_COMMIT);

            stateManager.cacheScore(allianceId, score);
        } catch (Exception e) {
            ILog.e(TAG, "handleScoreSubmit", e.getMessage());
        }
    }

    private String buildAllianceId(String matchId, String alliance) {
        if (matchId == null || matchId.isBlank()) {
            return null;
        }
        if (alliance == null) {
            return null;
        }
        String normalized = alliance.trim().toLowerCase();
        if (normalized.endsWith("_r") || normalized.endsWith("_b")) {
            return matchId + normalized.substring(normalized.length() - 2).toUpperCase();
        }
        if (normalized.startsWith("r")) {
            return matchId + "_R";
        }
        if (normalized.startsWith("b")) {
            return matchId + "_B";
        }
        return null;
    }

    private String getString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> extractPayload(Map<String, Object> root) {
        if (root == null) {
            return Map.of();
        }
        Object payloadObj = root.get("payload");
        if (payloadObj instanceof Map) {
            return (Map<String, Object>) payloadObj;
        }
        return root;
    }
}
