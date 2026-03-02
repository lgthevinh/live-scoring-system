package org.thingai.app.scoringservice.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.ScoreStatus;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.handler.entityhandler.ScoreHandler;
import org.thingai.base.dao.Dao;
import org.thingai.base.log.ILog;
import org.thingai.platform.dao.DaoFile;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handler for temporary/pending score storage.
 * Temp scores are saved to a separate folder and only moved to the actual
 * scores folder when committed by a scorekeeper.
 */
public class TempScoreHandler {
    private static final String TAG = "TempScoreHandler";
    private static final String TEMP_SCORES_DIR = "/temp_scores/";
    private static final String FINAL_SCORES_DIR = "/scores/";

    private final Dao dao;
    private final DaoFile daoFile;
    private final ScoreHandler scoreHandler;

    public TempScoreHandler(Dao dao, DaoFile daoFile, ScoreHandler scoreHandler) {
        this.dao = dao;
        this.daoFile = daoFile;
        this.scoreHandler = scoreHandler;
    }

    /**
     * Generate a unique temp score filename.
     * Format: {allianceId}_{timestamp}_{uuid}.json
     * Example: Q1_R_20240302_090956_a3f7b2d9.json
     */
    private String generateTempScoreFilename(String allianceId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return allianceId + "_" + timestamp + "_" + uuid + ".json";
    }

    /**
     * Check if a temp score JSON represents a deleted marker.
     * Deleted markers are written as {"deleted":true}
     */
    private boolean isDeletedMarker(String json) {
        if (json == null || json.isEmpty()) {
            return true;
        }
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            return obj.has("deleted") && obj.get("deleted").getAsBoolean();
        } catch (Exception e) {
            // If we can't parse it, consider it deleted/invalid
            return true;
        }
    }

    /**
     * Save a temporary score to the temp_scores folder.
     * This does NOT affect the final score or database.
     * Returns the generated tempScoreId (filename).
     */
    public void saveTempScore(String allianceId, String jsonScoreData, String submittedBy,
                              RequestCallback<String> callback) {
        try {
            if (!allianceId.contains("_")) {
                callback.onFailure(ErrorCode.CUSTOM_ERR, "Invalid alliance ID format: " + allianceId);
                return;
            }

            Gson gson = new Gson();
            JsonObject scoreObj;
            try {
                scoreObj = gson.fromJson(jsonScoreData, JsonObject.class);
            } catch (Exception e) {
                callback.onFailure(ErrorCode.CUSTOM_ERR, "Invalid JSON score data: " + e.getMessage());
                return;
            }

            String tempScoreId = generateTempScoreFilename(allianceId);

            JsonObject tempMetadata = new JsonObject();
            tempMetadata.addProperty("tempScoreId", tempScoreId);
            tempMetadata.addProperty("submittedAt", System.currentTimeMillis());
            tempMetadata.addProperty("submittedBy", submittedBy != null ? submittedBy : "unknown");
            tempMetadata.addProperty("allianceId", allianceId);

            JsonObject wrapper = new JsonObject();
            wrapper.add("scoreData", scoreObj);
            wrapper.add("tempMetadata", tempMetadata);

            String tempScoreJson = gson.toJson(wrapper);
            String tempFilePath = TEMP_SCORES_DIR + tempScoreId;
            daoFile.writeJsonFile(tempFilePath, tempScoreJson);

            ILog.d(TAG, "Saved temp score " + tempScoreId + " for " + allianceId + " by " + submittedBy);
            callback.onSuccess(tempScoreId, "Temp score saved successfully");

        } catch (Exception e) {
            ILog.e(TAG, "Failed to save temp score: " + e.getMessage());
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to save temp score: " + e.getMessage());
        }
    }

    /**
     * Get a temporary score by its ID (filename).
     */
    public void getTempScoreById(String tempScoreId, RequestCallback<String> callback) {
        try {
            String tempFilePath = TEMP_SCORES_DIR + tempScoreId;
            String tempScoreJson = daoFile.readJsonFile(tempFilePath);

            if (isDeletedMarker(tempScoreJson)) {
                callback.onFailure(ErrorCode.NOT_FOUND, "No temp score found with ID: " + tempScoreId);
                return;
            }

            callback.onSuccess(tempScoreJson, "Temp score retrieved successfully");

        } catch (Exception e) {
            ILog.e(TAG, "Failed to get temp score: " + e.getMessage());
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to get temp score: " + e.getMessage());
        }
    }

    /**
     * Get all temp scores for a specific alliance.
     * Returns a list of temp score JSON strings.
     */
    public void getAllTempScoresForAlliance(String allianceId, RequestCallback<List<String>> callback) {
        try {
            File tempDir = new File("files/temp_scores");
            if (!tempDir.exists() || !tempDir.isDirectory()) {
                callback.onSuccess(new ArrayList<>(), "No temp scores directory found");
                return;
            }

            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(allianceId + "_") && name.endsWith(".json");
                }
            };

            File[] files = tempDir.listFiles(filter);
            List<String> tempScores = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    String tempScoreJson = daoFile.readJsonFile(TEMP_SCORES_DIR + file.getName());
                    if (!isDeletedMarker(tempScoreJson)) {
                        tempScores.add(tempScoreJson);
                    }
                }
            }

            callback.onSuccess(tempScores, "Found " + tempScores.size() + " temp scores for " + allianceId);

        } catch (Exception e) {
            ILog.e(TAG, "Failed to get temp scores for alliance: " + e.getMessage());
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to get temp scores: " + e.getMessage());
        }
    }

    /**
     * Get the count of temp scores for an alliance.
     */
    public int getTempScoreCount(String allianceId) {
        try {
            File tempDir = new File("files/temp_scores");
            if (!tempDir.exists() || !tempDir.isDirectory()) {
                return 0;
            }

            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(allianceId + "_") && name.endsWith(".json");
                }
            };

            File[] files = tempDir.listFiles(filter);
            int count = 0;
            if (files != null) {
                for (File file : files) {
                    String tempScoreJson = daoFile.readJsonFile(TEMP_SCORES_DIR + file.getName());
                    if (!isDeletedMarker(tempScoreJson)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Commit a specific temp score to the final scores folder and database.
     * Only commits the specified temp score file, doesn't affect others.
     */
    public void commitTempScore(String tempScoreId, String approvedBy,
                                RequestCallback<Score> callback) {
        try {
            String tempFilePath = TEMP_SCORES_DIR + tempScoreId + ".json";
            String tempScoreJson = daoFile.readJsonFile(tempFilePath);

            if (isDeletedMarker(tempScoreJson)) {
                callback.onFailure(ErrorCode.NOT_FOUND, "No temp score found with ID: " + tempScoreId);
                return;
            }

            Gson gson = new Gson();
            JsonObject wrapper = gson.fromJson(tempScoreJson, JsonObject.class);
            JsonObject scoreData = wrapper.getAsJsonObject("scoreData");
            JsonObject tempMetadata = wrapper.getAsJsonObject("tempMetadata");

            if (scoreData == null) {
                callback.onFailure(ErrorCode.CUSTOM_ERR, "Invalid temp score format");
                return;
            }

            String allianceId = tempMetadata != null && tempMetadata.has("allianceId")
                    ? tempMetadata.get("allianceId").getAsString()
                    : tempScoreId.substring(0, tempScoreId.lastIndexOf('_', tempScoreId.lastIndexOf('_') - 1));

            String finalScoreJson = gson.toJson(scoreData);

            Score score = ScoreHandler.factoryScore();
            score.setAllianceId(allianceId);
            score.fromJson(finalScoreJson);
            score.setRawScoreData(finalScoreJson);
            score.calculatePenalties();
            score.calculateTotalScore();
            score.setStatus(ScoreStatus.SCORED);
            score.setApproved(true);

            scoreHandler.submitScore(score, true, new RequestCallback<Score>() {
                @Override
                public void onSuccess(Score savedScore, String message) {
                    ILog.d(TAG, "Committed temp score " + tempScoreId + " by " + approvedBy + " (temp file NOT deleted for debugging)");
                    callback.onSuccess(savedScore, "Score committed successfully");
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    callback.onFailure(errorCode, "Failed to commit score: " + errorMessage);
                }
            });

        } catch (Exception e) {
            ILog.e(TAG, "Failed to commit temp score: " + e.getMessage());
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to commit temp score: " + e.getMessage());
        }
    }

    /**
     * Reject and delete a specific temp score by its ID.
     */
    public void rejectTempScore(String tempScoreId, String rejectedBy, String reason,
                                RequestCallback<Boolean> callback) {
        try {
            String tempFilePath = TEMP_SCORES_DIR + tempScoreId;
            String tempScoreJson = daoFile.readJsonFile(tempFilePath);
            if (isDeletedMarker(tempScoreJson)) {
                callback.onFailure(ErrorCode.NOT_FOUND, "No temp score found with ID: " + tempScoreId);
                return;
            }

            daoFile.writeJsonFile(tempFilePath, "{\"deleted\":true}");
            ILog.d(TAG, "Rejected temp score " + tempScoreId + " by " + rejectedBy + " reason: " + reason);
            callback.onSuccess(true, "Temp score rejected and deleted");

        } catch (Exception e) {
            ILog.e(TAG, "Failed to reject temp score: " + e.getMessage());
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to reject temp score: " + e.getMessage());
        }
    }
}
