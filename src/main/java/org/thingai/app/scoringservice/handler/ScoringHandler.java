package org.thingai.app.scoringservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.entity.ScoreDefine;
import org.thingai.base.log.ILog;
import org.thingai.platform.dao.DaoFile;

import java.util.HashMap;

/**
 * ScoringHandler is responsible for managing score-related operations, including
 * creating Score instances and retrieving score definitions. It uses a factory
 * method to create Score objects based on a configurable Score class.
 */
public class ScoringHandler {
    private static final String TAG = "ScoringHandler";
    private static Class<? extends Score> scoreClass;

    private final ObjectMapper objectMapper = new ObjectMapper(); // For converting DTO to JSON
    private DaoFile daoFile;

    public ScoringHandler(DaoFile daoFile) {
        this.daoFile = daoFile;
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
        ScoringHandler.scoreClass = scoreClass;
    }

    public HashMap<String, ScoreDefine> getScoreDefinitions() {
        Score score = factoryScore();
        return score.getScoreDefinitions();
    }

    public void calculateScoreOfMatch(String matchId) {
        
    }
}
