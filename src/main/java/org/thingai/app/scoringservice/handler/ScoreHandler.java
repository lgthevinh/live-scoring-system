package org.thingai.app.scoringservice.handler;

import com.google.gson.Gson;
import org.thingai.app.scoringservice.dto.ScoreDetailDto;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.ScoreDefine;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.base.log.ILog;

import java.util.HashMap;

/**
 * ScoringHandler is responsible for managing score-related operations, including
 * creating Score instances and retrieving score definitions. It uses a factory
 * method to create Score objects based on a configurable Score class.
 */
public class ScoreHandler {
    private static final String TAG = "ScoringHandler";
    private static Class<? extends Score> scoreClass;

    private final Gson gson = new Gson(); // For converting DTO to JSON

    public ScoreHandler() {

    }
    /**
     * Factory method to create a Score object. Uses the configured scoreClass.
     * 
     * @return A new Score object instance.
     */
    public static Score factoryScore() {
        try {
            return scoreClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            ILog.e(TAG, "factoryScore", e.getMessage());
            throw new RuntimeException("Failed to instantiate score class, define score specific class first.");
        }
    }

    public static void setScoreClass(Class<? extends Score> scoreClass) {
        ScoreHandler.scoreClass = scoreClass;
    }

    public HashMap<String, ScoreDefine> getScoreDefinitions() {
        Score score = factoryScore();
        return score.getScoreDefinitions();
    }

    public void updateScore(Score score) throws Exception {
        ensureEventReady();
        if (score == null || isBlank(score.getAllianceId())) {
            throw new IllegalArgumentException("score and allianceId are required.");
        }

        String jsonData = score.getRawScoreData();
        if (jsonData == null || jsonData.isBlank()) {
            jsonData = gson.toJson(score);
        }

        LocalRepository.eventFileStore().writeJsonFile(score.getAllianceId() + ".json", jsonData);
        LocalRepository.scoreDao().updateScore(score);
    }

    public Score[] getAllScores() throws Exception {
        ensureEventReady();
        return LocalRepository.scoreDao().listScores();
    }

    public Score[] getScoresByMatchId(String matchId) throws Exception {
        ensureEventReady();
        if (isBlank(matchId)) {
            throw new IllegalArgumentException("matchId is required.");
        }
        Score redScore = LocalRepository.scoreDao().getScoreById(matchId + "_R");
        Score blueScore = LocalRepository.scoreDao().getScoreById(matchId + "_B");
        return new Score[]{redScore, blueScore};
    }

    public ScoreDetailDto[] getScoreDetailsByMatchId(String matchId) throws Exception {
        ensureEventReady();
        if (isBlank(matchId)) {
            throw new IllegalArgumentException("matchId is required.");
        }
        Score[] scores = getScoresByMatchId(matchId);
        ScoreDetailDto redDetail = scores[0] != null ? new ScoreDetailDto(scores[0]) : null;
        ScoreDetailDto blueDetail = scores[1] != null ? new ScoreDetailDto(scores[1]) : null;
        return new ScoreDetailDto[]{redDetail, blueDetail};
    }

    private void ensureEventReady() {
        if (LocalRepository.eventDatabase() == null) {
            throw new IllegalStateException("No active event database.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
