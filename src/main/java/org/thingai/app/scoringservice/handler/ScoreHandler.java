package org.thingai.app.scoringservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.ScoreStatus;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.entity.score.ScoreDefine;
import org.thingai.base.dao.Dao;
import org.thingai.base.log.ILog;
import org.thingai.platform.dao.DaoFile;

import java.util.HashMap;

public class ScoreHandler {
    private final ObjectMapper objectMapper = new ObjectMapper(); // For converting DTO to JSON

    private final Dao dao;
    private final DaoFile daoFile;

    private static Class<? extends Score> scoreClass;

    public ScoreHandler(Dao dao, DaoFile daoFile) {
        this.dao = dao;
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
            ILog.e("ScoreHandler", "Failed to instantiate score class: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to instantiate score class, define score specific class first.");
        }
    }

    /**
     * Retrieves a score object for a specific alliance, fully populated with its
     * raw details.
     * 
     * @param allianceId The unique ID of the alliance (e.g., "Q1_R").
     * @param callback   Callback to return the populated Score object or an error.
     */
    public void getScoreByAllianceId(String allianceId, RequestCallback<Score> callback) {
        try {
            // 1. Read the base score object from the database.
            Score score = dao.query(Score.class, "id", allianceId)[0];
            if (score == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "Score not found for alliance: " + allianceId);
                return;
            }

            // 2. Read the detailed raw score data from the corresponding JSON file.
            String jsonRawScoreData = daoFile.readJsonFile("/scores/" + allianceId + ".json");
            if (jsonRawScoreData == null || jsonRawScoreData.isEmpty()) {
                // Generate json data
                Score newScore = factoryScore();
                newScore.setAllianceId(allianceId);
                daoFile.writeJsonFile("/scores/" + allianceId + ".json", newScore.getRawScoreData());
                jsonRawScoreData = newScore.getRawScoreData();
            }

            score.setRawScoreData(jsonRawScoreData);

            callback.onSuccess(score, "Score retrieved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve score: " + e.getMessage());
        }
    }

    public void getScoresByMatchId(String matchId, RequestCallback<String> callback) {
        try {
            // Assuming alliance IDs are formatted as "Q{matchId}_R" and "Q{matchId}_B"
            String redAllianceId = matchId + "_R";
            String blueAllianceId = matchId + "_B";

            // 1. Read both score objects from the database.
            Score redScore = dao.query(Score.class, "id", redAllianceId)[0];
            Score blueScore = dao.query(Score.class, "id", blueAllianceId)[0];

            if (redScore == null && blueScore == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "No scores found for match: " + matchId);
                return;
            }

            // 2. Read their detailed raw score data from the corresponding JSON files.
            String redJsonData = daoFile.readJsonFile("/scores/" + redAllianceId + ".json");
            String blueJsonData = daoFile.readJsonFile("/scores/" + blueAllianceId + ".json");

            // 3. Construct a combined JSON response.
            String result = "{";
            if (redScore != null) {
                result += "\"red\":" + (redJsonData != null ? redJsonData : "{}");
            } else {
                result += "\"red\":null";
            }

            result += ",";
            if (blueScore != null) {
                result += "\"blue\":" + (blueJsonData != null ? blueJsonData : "{}");
            } else {
                result += "\"blue\":null";
            }

            callback.onSuccess(result, "Scores retrieved successfully for match: " + matchId);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve scores for match: " + e.getMessage());
        }
    }

    /**
     * Takes raw scoring data (as a DTO), calculates the final score, and persists
     * the result.
     * 
     * @param allianceId      The unique ID of the alliance being scored.
     * @param scoreDetailsDto A DTO representing the raw scoring inputs from the UI.
     * @param callback        Callback to signal completion.
     */
    public void submitScore(String allianceId, Object scoreDetailsDto, boolean isForceUpdate,
            RequestCallback<Score> callback) {
        try {
            // 1. Retrieve the existing score object.
            Score score = dao.query(Score.class, "id", allianceId)[0];
            if (score == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "Cannot submit score, match/alliance not found: " + allianceId);
                return;
            }

            if (score.getStatus() == ScoreStatus.SCORED && !isForceUpdate) {
                callback.onFailure(ErrorCode.UPDATE_FAILED, "Score already submitted for alliance: " + allianceId);
                return;
            }

            // 2. Convert the incoming DTO to a JSON string.
            Score finalScore = factoryScore();
            finalScore.setAllianceId(allianceId);
            String rawJsonData = objectMapper.writeValueAsString(scoreDetailsDto);

            // 3. Use the entity's fromJson method to populate its internal state.
            finalScore.fromJson(rawJsonData);

            // 4. Trigger the calculation logic within the finalScore object.
            finalScore.calculateTotalScore();
            finalScore.calculatePenalties();
            finalScore.setStatus(ScoreStatus.SCORED);

            ILog.d("ScoreHandler", "Final calculated score for alliance " + allianceId + ": Total="
                    + finalScore.getTotalScore() + ", Penalties=" + finalScore.getPenaltiesScore());

            // 5. Call the existing save method to persist the changes.
            updateAndSaveScore(finalScore, new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void result, String message) {
                    callback.onSuccess(finalScore, "Score submitted and calculated successfully.");
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    callback.onFailure(errorCode, errorMessage);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.UPDATE_FAILED, "Failed to submit score: " + e.getMessage());
        }
    }

    public void submitScore(Score score, boolean isForceUpdate, RequestCallback<Score> callback) {
        try {
            String allianceId = score.getAllianceId();
            String rawData = score.getRawScoreData();
            
            ILog.d("ScoreHandler", "=== SUBMIT SCORE START ===");
            ILog.d("ScoreHandler", "AllianceId: " + allianceId);
            ILog.d("ScoreHandler", "RawScoreData: " + rawData);
            ILog.d("ScoreHandler", "Input totalScore: " + score.getTotalScore());
            ILog.d("ScoreHandler", "Input penaltiesScore: " + score.getPenaltiesScore());
            
            // Create a new score object using factory (ensures correct type like FanrocScore)
            Score scoreToSave = factoryScore();
            scoreToSave.setAllianceId(allianceId);
            
            ILog.d("ScoreHandler", "Created score type: " + scoreToSave.getClass().getName());
            
            // Copy the data from the submitted score
            scoreToSave.fromJson(rawData);
            scoreToSave.setRawScoreData(rawData);

            // Copy the approved flag from the input score
            scoreToSave.setApproved(score.isApproved());
            ILog.d("ScoreHandler", "Input isApproved: " + score.isApproved() + ", set on scoreToSave: " + scoreToSave.isApproved());

            ILog.d("ScoreHandler", "After fromJson - score type: " + scoreToSave.getClass().getName());
            ILog.d("ScoreHandler", "After fromJson - rawData: " + scoreToSave.getRawScoreData());
            
            scoreToSave.calculatePenalties();
            scoreToSave.calculateTotalScore();
            scoreToSave.setStatus(ScoreStatus.SCORED);
            
            ILog.d("ScoreHandler", "After calculate - totalScore: " + scoreToSave.getTotalScore());
            ILog.d("ScoreHandler", "After calculate - penaltiesScore: " + scoreToSave.getPenaltiesScore());
            
            // Check if score already exists
            Score existingScore = dao.query(Score.class, "id", allianceId)[0];
            if (existingScore != null && existingScore.getStatus() == ScoreStatus.SCORED && !isForceUpdate) {
                ILog.d("ScoreHandler", "Score already submitted and forceUpdate is false");
                callback.onFailure(ErrorCode.UPDATE_FAILED, "Score already submitted for alliance: " + allianceId);
                return;
            }

            // If we're force-updating an existing approved score, preserve the approved status
            if (existingScore != null && existingScore.isApproved()) {
                scoreToSave.setApproved(true);
                ILog.d("ScoreHandler", "Preserving approved status from existing score");
            }

            // Save the score using the correct type
            updateAndSaveScore(scoreToSave, new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void result, String message) {
                    ILog.d("ScoreHandler", "=== SUBMIT SCORE SUCCESS ===");
                    callback.onSuccess(scoreToSave, "Score submitted and calculated successfully.");
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    ILog.e("ScoreHandler", "=== SUBMIT SCORE FAILED ===" + errorMessage);
                    callback.onFailure(errorCode, errorMessage);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            ILog.e("ScoreHandler", "=== SUBMIT SCORE EXCEPTION ===" + e.getMessage());
            callback.onFailure(ErrorCode.UPDATE_FAILED, "Failed to submit score: " + e.getMessage());
        }
    }

    /**
     * Updates the score in the database and writes its raw data to a JSON file.
     * 
     * @param score    The fully calculated Score object.
     * @param callback Callback to signal completion.
     */
    public void updateAndSaveScore(Score score, RequestCallback<Void> callback) {
        try {
            System.out.println("=== UPDATE AND SAVE SCORE ===");
            System.out.println("AllianceId: " + score.getAllianceId());
            System.out.println("TotalScore: " + score.getTotalScore());
            System.out.println("Status: " + score.getStatus());
            
            // 1. Get the raw data from the entity itself.
            String jsonRawScoreData = score.getRawScoreData();
            System.out.println("Raw JSON to be saved: " + jsonRawScoreData);

            // 2. Update score record in the database.
            dao.insertOrUpdate(Score.class, score);

            // 3. Also write the raw data to a JSON file.
            daoFile.writeJsonFile("/scores/" + score.getAllianceId() + ".json", jsonRawScoreData);

            callback.onSuccess(null, "Score data saved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.UPDATE_FAILED, "Failed to save score data: " + e.getMessage());
        }
    }

    public void getScoreUi(RequestCallback<HashMap<String, ScoreDefine>> callback) {
        try {
            Score score = factoryScore();
            HashMap<String, ScoreDefine> scoreUiMap = score.getScoreDefinitions();

            callback.onSuccess(scoreUiMap, "Score UI definitions retrieved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve score UI definitions: " + e.getMessage());
        }
    }

    public static void setScoreClass(Class<? extends Score> scoreClass) {
        ScoreHandler.scoreClass = scoreClass;
    }

    /**
     * Retrieves all scores with AWAITING_APPROVAL status for a match.
     *
     * @param matchId  The unique ID of the match (e.g., "Q1").
     * @param callback Callback to return pending scores structured as {red: Score, blue: Score} or an error.
     */
    public void getPendingScores(String matchId, RequestCallback<java.util.Map<String, Score>> callback) {
        try {
            String redAllianceId = matchId + "_R";
            String blueAllianceId = matchId + "_B";

            Score redScore = dao.query(Score.class, "id", redAllianceId)[0];
            Score blueScore = dao.query(Score.class, "id", blueAllianceId)[0];

            java.util.Map<String, Score> result = new java.util.HashMap<>();

            if (redScore != null && !redScore.isApproved()) {
                String redJsonData = daoFile.readJsonFile("/scores/" + redAllianceId + ".json");
                redScore.setRawScoreData(redJsonData != null ? redJsonData : "{}");
                result.put("red", redScore);
            }

            if (blueScore != null && !blueScore.isApproved()) {
                String blueJsonData = daoFile.readJsonFile("/scores/" + blueAllianceId + ".json");
                blueScore.setRawScoreData(blueJsonData != null ? blueJsonData : "{}");
                result.put("blue", blueScore);
            }

            callback.onSuccess(result, "Pending scores retrieved successfully for match: " + matchId);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to retrieve pending scores: " + e.getMessage());
        }
    }

    /**
     * Approves a pending score by changing its status to SCORED.
     *
     * @param allianceId The unique ID of the alliance (e.g., "Q1_R").
     * @param callback   Callback to return the approved Score object or an error.
     */
    public void approveScore(String allianceId, RequestCallback<Score> callback) {
        try {
            System.out.println("=== APPROVE SCORE CALLED ===");
            System.out.println("AllianceId: " + allianceId);
            
            Score[] scores = dao.query(Score.class, "id", allianceId);
            System.out.println("Scores found: " + (scores != null ? scores.length : 0));
            
            if (scores == null || scores.length == 0) {
                callback.onFailure(ErrorCode.NOT_FOUND, "Score not found for alliance: " + allianceId);
                return;
            }
            
            Score score = scores[0];
            System.out.println("Before approve - isApproved: " + score.isApproved());
            System.out.println("Before approve - status: " + score.getStatus());
            System.out.println("Before approve - totalScore: " + score.getTotalScore());
            
            score.setApproved(true);
            System.out.println("After setApproved(true): " + score.isApproved());
            
            dao.insertOrUpdate(Score.class, score);
            System.out.println("After insertOrUpdate - saved to database");

            callback.onSuccess(score, "Score approved successfully for alliance: " + allianceId);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.UPDATE_FAILED, "Failed to approve score: " + e.getMessage());
        }
    }

    /**
     * Rejects a pending score by resetting its status to NOT_SCORED.
     *
     * @param allianceId The unique ID of the alliance (e.g., "Q1_R").
     * @param callback   Callback to return true if rejection was successful, or an error.
     */
    public void rejectScore(String allianceId, RequestCallback<Boolean> callback) {
        try {
            Score score = dao.query(Score.class, "id", allianceId)[0];
            if (score == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "Score not found for alliance: " + allianceId);
                return;
            }

            if (score.isApproved()) {
                callback.onFailure(ErrorCode.UPDATE_FAILED, "Score is not awaiting approval for alliance: " + allianceId);
                return;
            }

            score.setApproved(false);
            score.setStatus(ScoreStatus.NOT_SCORED);
            score.setTotalScore(0);
            score.setPenaltiesScore(0);

            Score emptyScore = factoryScore();
            emptyScore.setAllianceId(allianceId);
            String emptyJsonData = emptyScore.getRawScoreData();

            dao.insertOrUpdate(Score.class, score);
            daoFile.writeJsonFile("/scores/" + allianceId + ".json", emptyJsonData);

            callback.onSuccess(true, "Score rejected successfully for alliance: " + allianceId);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.UPDATE_FAILED, "Failed to reject score: " + e.getMessage());
        }
    }
}
