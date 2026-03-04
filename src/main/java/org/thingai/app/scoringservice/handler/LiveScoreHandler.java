package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.BroadcastMessageType;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.define.ScoreStatus;
import org.thingai.app.scoringservice.dto.LiveScoreUpdateDto;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.dto.MatchTimeStatusDto;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.app.scoringservice.entity.score.Score;
import org.thingai.app.scoringservice.handler.entityhandler.MatchHandler;
import org.thingai.app.scoringservice.handler.entityhandler.RankingHandler;
import org.thingai.app.scoringservice.handler.entityhandler.ScoreHandler;
import org.thingai.base.log.ILog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LiveScoreHandler {
    private static final String TAG = "ScorekeeperHandler";
    private static final int MATCH_DURATION_SECONDS = 180; // modify this based on season rules
    private static final long SOUND_DEBOUNCE_MS = 5000; // Debounce sound for 5 seconds to prevent overlapping

    private final MatchTimerHandler matchTimerHandler;
    private final MatchHandler matchHandler;
    private final ScoreHandler scoreHandler;
    private final RankingHandler rankingHandler;

    private BroadcastHandler broadcastHandler;

    private MatchDetailDto currentMatch;
    private MatchDetailDto nextMatch;
    private Score currentRedScoreHolder;
    private Score currentBlueScoreHolder;

    private int typicalMatchDuration = 180; // seconds
    /* Flags */
    private boolean isRedCommitable = false;
    private boolean isBlueCommitable = false;
    /* Sound debouncing */
    private volatile boolean isCountdownRunning = false;
    private volatile long lastSoundBroadcastTime = 0;

    public LiveScoreHandler(MatchHandler matchHandler, ScoreHandler scoreHandler, RankingHandler rankingHandler) {
        this.matchHandler = matchHandler;
        this.scoreHandler = scoreHandler;
        this.rankingHandler = rankingHandler;

        matchTimerHandler = new MatchTimerHandler(MATCH_DURATION_SECONDS);
        matchTimerHandler.setCallback(new MatchTimerHandler.TimerCallback() {
            @Override
            public void onTimerEnded(String matchId) {
            }
            @Override
            public void onTimerUpdated(String matchId, String fieldNumber, int remainingSeconds) {
                MatchTimeStatusDto dto = new MatchTimeStatusDto(matchId, remainingSeconds);
                String topic = "/topic/display/field/" + fieldNumber + "/timer";
                broadcastHandler.broadcast(topic, dto, BroadcastMessageType.MATCH_STATUS);
            }
        });
    }

    /**
     * Set the next match to be played, the broadcast notify all clients about the new match set.
     * @param matchId
     * @param callback
     */
    public void setNextMatch(String matchId, RequestCallback<MatchDetailDto> callback) {
        try {
            // Retrieve match detail and return callback response
            nextMatch = matchHandler.getMatchDetailSync(matchId);
            broadcastHandler.broadcast("/topic/match/available", nextMatch, BroadcastMessageType.MATCH_STATUS);

            callback.onSuccess(nextMatch, "Set next match success");
            // Broadcast the next match info to all clients
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to set next match: " + e.getMessage());
        }
    }

    public void startCurrentMatch(RequestCallback<Boolean> callback) {
        try {
            // If no active match yet, try to activate from nextMatch for backward compatibility
            if (currentMatch == null && nextMatch != null) {
                currentMatch = nextMatch;
                nextMatch = null;

                currentRedScoreHolder = ScoreHandler.factoryScore();
                currentBlueScoreHolder = ScoreHandler.factoryScore();

                currentBlueScoreHolder.setAllianceId(currentMatch.getMatch().getId() + "_B");
                currentRedScoreHolder.setAllianceId(currentMatch.getMatch().getId() + "_R");
            }

            if (currentMatch == null) {
                callback.onFailure(ErrorCode.RETRIEVE_FAILED, "No active match to start");
                return;
            }

            // Prevent multiple countdowns from running simultaneously (debouncing)
            if (isCountdownRunning) {
                ILog.w(TAG, "Countdown already running, ignoring duplicate start request");
                callback.onFailure(ErrorCode.CUSTOM_ERR, "Match countdown already in progress");
                return;
            }

            int fieldNumber = currentMatch.getMatch().getFieldNumber();
            String rootTopic = "/topic/display/field/" + fieldNumber;

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime currentTime = LocalDateTime.now();

            currentMatch.getMatch().setActualStartTime(currentTime.format(timeFormatter));

            // Start 3-second countdown before main timer
            final int countdownSeconds = 3;
            final int[] countdown = {countdownSeconds};
            isCountdownRunning = true;

            ScheduledExecutorService countdownScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            countdownScheduler.scheduleAtFixedRate(() -> {
                if (countdown[0] > 0) {
                    // Broadcast countdown value
                    MatchTimeStatusDto countdownDto = new MatchTimeStatusDto(currentMatch.getMatch().getId(), countdown[0]);
                    broadcastHandler.broadcast(rootTopic + "/timer", countdownDto, BroadcastMessageType.MATCH_STATUS);

                    // When countdown reaches 3 (first countdown tick), broadcast PLAY_SOUND for synchronized playback
                    // Use debouncing to prevent overlapping sounds
                    if (countdown[0] == countdownSeconds) {
                        long currentTimeMs = System.currentTimeMillis();
                        if (currentTimeMs - lastSoundBroadcastTime > SOUND_DEBOUNCE_MS) {
                            lastSoundBroadcastTime = currentTimeMs;
                            broadcastHandler.broadcast(rootTopic + "/sound", countdownDto, BroadcastMessageType.PLAY_SOUND);
                        } else {
                            ILog.w(TAG, "Sound broadcast debounced - too soon since last broadcast");
                        }
                    }

                    countdown[0]--;
                } else {
                    // Countdown finished, start main timer at full 3:00 (180 seconds)
                    countdownScheduler.shutdown();
                    isCountdownRunning = false;
                    matchTimerHandler.startTimer(currentMatch.getMatch().getId(), fieldNumber, typicalMatchDuration);
                    MatchTimeStatusDto initialTimerDto = new MatchTimeStatusDto(currentMatch.getMatch().getId(), typicalMatchDuration);
                    broadcastHandler.broadcast(rootTopic + "/timer", initialTimerDto, BroadcastMessageType.MATCH_STATUS);
                }
            }, 0, 1, TimeUnit.SECONDS);

            broadcastHandler.broadcast(rootTopic + "/command", currentMatch, BroadcastMessageType.SHOW_TIMER);
            broadcastHandler.broadcast(rootTopic + "/score/red", currentRedScoreHolder, BroadcastMessageType.SCORE_UPDATE);
            broadcastHandler.broadcast(rootTopic + "/score/blue", currentBlueScoreHolder, BroadcastMessageType.SCORE_UPDATE);

            callback.onSuccess(true, "Match started");
        } catch (Exception e) {
            isCountdownRunning = false;
            e.printStackTrace();
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to start match: " + e.getMessage());
        }
    }

    /**
     * Activate the loaded match into the active match buffer without starting the match timer.
     * This moves nextMatch to currentMatch, initializes score holders, and broadcasts to displays.
     */
    public void activateMatch(RequestCallback<Boolean> callback) {
        if (nextMatch == null) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "No loaded match to activate");
            return;
        }

        try {
            currentMatch = nextMatch;
            nextMatch = null;

            int fieldNumber = currentMatch.getMatch().getFieldNumber();
            String rootTopic = "/topic/display/field/" + fieldNumber;

            currentRedScoreHolder = ScoreHandler.factoryScore();
            currentBlueScoreHolder = ScoreHandler.factoryScore();

            currentBlueScoreHolder.setAllianceId(currentMatch.getMatch().getId() + "_B");
            currentRedScoreHolder.setAllianceId(currentMatch.getMatch().getId() + "_R");

            broadcastHandler.broadcast(rootTopic + "/command", currentMatch, BroadcastMessageType.SHOW_TIMER);
            broadcastHandler.broadcast(rootTopic + "/score/red", currentRedScoreHolder, BroadcastMessageType.SCORE_UPDATE);
            broadcastHandler.broadcast(rootTopic + "/score/blue", currentBlueScoreHolder, BroadcastMessageType.SCORE_UPDATE);

            callback.onSuccess(true, "Match activated without starting timer");
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to activate match: " + e.getMessage());
        }
    }

    public void handleLiveScoreUpdate(LiveScoreUpdateDto liveScoreUpdate, boolean isRedAlliance) {
        try {
            // Create score object for database persistence
            Score liveScore = ScoreHandler.factoryScore();

            // Set alliance ID - use matchId if provided, otherwise use temporary ID
            String allianceId;
            if (liveScoreUpdate.payload.matchId != null && !liveScoreUpdate.payload.matchId.isEmpty()) {
                allianceId = isRedAlliance ?
                    liveScoreUpdate.payload.matchId + "_R" :
                    liveScoreUpdate.payload.matchId + "_B";
            } else {
                // Allow scoring without official match ID - use temporary IDs for logging
                allianceId = isRedAlliance ? "temp_red" : "temp_blue";
            }
            liveScore.setAllianceId(allianceId);

            // Populate score data
            liveScore.fromJson(liveScoreUpdate.payload.state.toString());
            liveScore.calculateTotalScore();
            liveScore.setStatus(ScoreStatus.SCORED); // Mark as scored for live tracking

            // Persist to database for logging/tracking
            scoreHandler.submitScore(liveScore, false, new RequestCallback<Score>() {
                @Override
                public void onSuccess(Score responseObject, String message) {
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                }
            });

            // Now handle broadcasting to display components
            // Try to broadcast to display components if we have a running match
            if (currentMatch != null && liveScoreUpdate.payload.matchId != null &&
                liveScoreUpdate.payload.matchId.equals(currentMatch.getMatch().getId())) {

                int fieldNumber = currentMatch.getMatch().getFieldNumber();
                String rootTopic = "/topic/live/field/" + fieldNumber;

                if (isRedAlliance) {
                    currentRedScoreHolder.fromJson(liveScoreUpdate.payload.state.toString());
                    currentRedScoreHolder.calculateTotalScore();
                    broadcastHandler.broadcast(rootTopic + "/score/red", currentRedScoreHolder, BroadcastMessageType.SCORE_UPDATE);
                } else {
                    currentBlueScoreHolder.fromJson(liveScoreUpdate.payload.state.toString());
                    currentBlueScoreHolder.calculateTotalScore();
                    broadcastHandler.broadcast(rootTopic + "/score/blue", currentBlueScoreHolder, BroadcastMessageType.SCORE_UPDATE);
                }
            } else {
                // Always broadcast live updates regardless of match state
                // This allows score display updates even without official match start
                String broadcastColor = isRedAlliance ? "red" : "blue";

                // Broadcast to any field that might be displaying (use field 1 as default/fallback)
                String fallbackTopic = "/topic/live/field/1/score/" + broadcastColor;
                liveScore.calculateTotalScore(); // Ensure score is calculated
                broadcastHandler.broadcast(fallbackTopic, liveScore, BroadcastMessageType.SCORE_UPDATE);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void commitFinalScore(RequestCallback<Score[]> callback) {
        if (!isRedCommitable || !isBlueCommitable) {
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Scores are not commitable yet");
            return;
        }

        // Re-calculate final scores
        currentRedScoreHolder.calculatePenalties();
        currentRedScoreHolder.calculateTotalScore();
        currentBlueScoreHolder.calculatePenalties();
        currentBlueScoreHolder.calculateTotalScore();

        final Score[] result = new Score[2];
        scoreHandler.submitScore(currentRedScoreHolder, true, new RequestCallback<Score>() {
            @Override
            public void onSuccess(Score responseObject, String message) {
                result[0] = responseObject;
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
            }
        });

        scoreHandler.submitScore(currentBlueScoreHolder, true, new RequestCallback<Score>() {
            @Override
            public void onSuccess(Score responseObject, String message) {
                result[1] = responseObject;
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
            }
        });

        // update current match end time
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime currentTime = LocalDateTime.now();

        currentMatch.getMatch().setMatchEndTime(currentTime.format(timeFormatter));
        matchHandler.updateMatch(currentMatch.getMatch(), new RequestCallback<Match>() {
            @Override
            public void onSuccess(Match responseObject, String message) {
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
            }
        });

        isRedCommitable = false;
        isBlueCommitable = false;

        rankingHandler.updateRankingEntry(currentMatch, currentBlueScoreHolder, currentRedScoreHolder);

        callback.onSuccess(result, "Scores committed successfully");
    }

    /**
     * Override the score for an alliance
     * @param allianceId
     * @param jsonScoreData format for this params should only contain the detail score element according to the season.
     * @param callback
     */
    public void overrideScore(String allianceId, String jsonScoreData, RequestCallback<Boolean> callback) {
        // Validate inputs
        if (allianceId == null || allianceId.isEmpty()) {
            callback.onFailure(ErrorCode.CUSTOM_ERR, "AllianceId is required");
            return;
        }
        if (jsonScoreData == null || jsonScoreData.isEmpty()) {
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Score data is required");
            return;
        }
        
        // Extract matchId from allianceId (e.g., "Q1_R" -> "Q1")
        String matchId;
        if (!allianceId.contains("_")) {
            ILog.w(TAG, "AllianceId does not contain underscore: " + allianceId);
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Invalid allianceId format: " + allianceId + " (expected format: MATCHID_R or MATCHID_B)");
            return;
        }
        matchId = allianceId.substring(0, allianceId.lastIndexOf("_"));
        String otherAllianceId = allianceId.endsWith("_R") ? matchId + "_B" : matchId + "_R";
        
        // Fetch the other alliance's score first
        scoreHandler.getScoreByAllianceId(otherAllianceId, new RequestCallback<Score>() {
            @Override
            public void onSuccess(Score otherScore, String message) {
                // Now process the override for the target alliance
                processScoreOverride(allianceId, jsonScoreData, matchId, otherScore, callback);
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                // If other alliance score not found, proceed anyway (might be a new match)
                processScoreOverride(allianceId, jsonScoreData, matchId, null, callback);
            }
        });
    }

    /**
     * Process the score override and update rankings
     */
    private void processScoreOverride(String allianceId, String jsonScoreData, String matchId, Score otherScore, RequestCallback<Boolean> callback) {
        Score targetScore = ScoreHandler.factoryScore();
        try {
            targetScore.setAllianceId(allianceId);
            targetScore.fromJson(jsonScoreData);
            targetScore.calculatePenalties();
            targetScore.calculateTotalScore();
            targetScore.setStatus(ScoreStatus.SCORED);
            targetScore.setApproved(true); // Auto-approve score overrides from match control

            scoreHandler.submitScore(targetScore, true, new RequestCallback<Score>() {
                @Override
                public void onSuccess(Score responseObject, String message) {
                    // Now update rankings and report success once
                    updateRankingsForOverride(matchId, targetScore, otherScore, callback);
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    callback.onFailure(errorCode, "Score override failed: " + errorMessage);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to parse score data: " + e.getMessage());
        }
    }

    /**
     * Update rankings after a score override
     */
    private void updateRankingsForOverride(String matchId, Score updatedScore, Score otherScore, RequestCallback<Boolean> callback) {
        // Fetch the match details
        matchHandler.getMatchDetail(matchId, new RequestCallback<MatchDetailDto>() {
            @Override
            public void onSuccess(MatchDetailDto matchDetail, String message) {
                // Determine which score is red and which is blue
                final Score redScore;
                final Score blueScore;
                
                if (updatedScore.getAllianceId().endsWith("_R")) {
                    redScore = updatedScore;
                    blueScore = otherScore;
                } else {
                    blueScore = updatedScore;
                    redScore = otherScore;
                }
                
                // Only update rankings if BOTH scores are available
                if (redScore == null || blueScore == null) {
                    System.out.println("[DEBUG] Skipping ranking update in override - missing scores. Red: " + 
                        (redScore != null ? "yes" : "no") + ", Blue: " + (blueScore != null ? "yes" : "no"));
                    callback.onSuccess(true, "Score override successful - rankings skipped (waiting for both scores)");
                    return;
                }
                
                // Update rankings with both scores
                rankingHandler.updateRankingEntry(matchDetail, blueScore, redScore);
                callback.onSuccess(true, "Score override and rankings updated successfully");
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                // Still report success for the score override
                callback.onSuccess(true, "Score override completed, but ranking update failed");
            }
        });
    }

    public void abortCurrentMatch(RequestCallback<Boolean> callback) {
        if (currentMatch == null) {
            callback.onFailure(ErrorCode.CUSTOM_ERR, "No active match to abort");
            return;
        }

        // Stop the timer
        matchTimerHandler.stopTimer();

        // Reset countdown flag to allow new countdowns
        isCountdownRunning = false;

        // Broadcast abort state to all displays
        int fieldNumber = currentMatch.getMatch().getFieldNumber();
        String rootTopic = "/topic/display/field/" + fieldNumber;
        MatchTimeStatusDto abortDto = new MatchTimeStatusDto(currentMatch.getMatch().getId(), -1); // -1 indicates aborted
        broadcastHandler.broadcast(rootTopic + "/timer", abortDto, BroadcastMessageType.MATCH_STATUS);

        // Broadcast STOP_SOUND to stop any playing sound
        broadcastHandler.broadcast(rootTopic + "/sound", abortDto, BroadcastMessageType.STOP_SOUND);

        // Move current match back to loaded state (can be restarted)
        MatchDetailDto abortedMatch = currentMatch;
        currentMatch = null;
        nextMatch = abortedMatch;

        // Reset score holders
        currentRedScoreHolder = null;
        currentBlueScoreHolder = null;

        callback.onSuccess(true, "Match aborted successfully");
    }

    public void getCurrentPlayingMatches(RequestCallback<MatchDetailDto[]> callback) {
        try {
            callback.onSuccess(new MatchDetailDto[]{currentMatch, nextMatch}, "Initial sync success");
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Initial sync failed: " + e.getMessage());
        }
    }

    public void getCurrentMatchField(int fieldNumber, RequestCallback<MatchDetailDto> callback) {
        try {
            if (currentMatch == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "No active or upcoming match found");
                return;
            }

            currentMatch.setBlueScore(currentBlueScoreHolder);
            currentMatch.setRedScore(currentRedScoreHolder);
            if (fieldNumber == 0) {
                if (currentMatch != null) {
                    callback.onSuccess(currentMatch, "Current match field retrieved successfully");
                } else {
                    callback.onFailure(ErrorCode.NOT_FOUND, "No current match loaded");
                }
                return;
            }
            if (currentMatch.getMatch().getFieldNumber() == fieldNumber) {
                callback.onSuccess(currentMatch, "Current match field retrieved successfully");
            } else {
                callback.onFailure(ErrorCode.NOT_FOUND, "No match found for field number: " + fieldNumber);
            }
        } catch (Exception e) {
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to get current match field: " + e.getMessage());
        }
    }

    public void handleScoreSubmission(boolean isRed, String allianceId, String jsonScoreData, RequestCallback<Boolean> callback) {
        // Check if we have an active match with score holders
        Score activeScoreHolder = isRed ? currentRedScoreHolder : currentBlueScoreHolder;
        boolean hasActiveMatch = activeScoreHolder != null;
        
        // If active match exists, verify alliance ID matches
        if (hasActiveMatch) {
            String currentAllianceId = activeScoreHolder.getAllianceId();
            boolean isCommited = isRed ? isRedCommitable : isBlueCommitable;
            
            if (!allianceId.equals(currentAllianceId) && isCommited) {
                callback.onFailure(ErrorCode.CUSTOM_ERR, "Current alliance is not commitable");
                return;
            }
        }
        
        try {
            Score submittedScore;
            
            if (hasActiveMatch) {
                // Use active match score holder
                submittedScore = activeScoreHolder;
                if (isRed) {
                    isRedCommitable = true;
                } else {
                    isBlueCommitable = true;
                }
            } else {
                // No active match - create new score from factory
                submittedScore = ScoreHandler.factoryScore();
                submittedScore.setAllianceId(allianceId);
            }
            
            // Only save temp score if no active match (fallback for buffer service)
            // When buffer service is used, temp score is already created - just update committable status
            if (!hasActiveMatch) {
                saveAsTempScore(allianceId, jsonScoreData, callback);
            } else {
                // Buffer service already created temp score - just confirm submission
                callback.onSuccess(true, "Score submitted - awaiting scorekeeper approval");
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to process score submission: " + e.getMessage());
        }
    }

    /**
     * Save score as temp score for scorekeeper review
     */
    private void saveAsTempScore(String allianceId, String jsonScoreData, RequestCallback<Boolean> callback) {
        // Get default submittedBy (could be enhanced to pass actual user)
        String submittedBy = "referee";

        // Use the temp score handler to save
        TempScoreHandler tempHandler = ScoringService.tempScoreHandler();
        if (tempHandler != null) {
            tempHandler.saveTempScore(allianceId, jsonScoreData, submittedBy, new RequestCallback<String>() {
                @Override
                public void onSuccess(String tempScoreId, String message) {
                    callback.onSuccess(true, "Score saved as temp - awaiting scorekeeper approval");
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    ILog.e(TAG, "Failed to save temp score: " + errorMessage);
                    callback.onFailure(errorCode, "Failed to save temp score: " + errorMessage);
                }
            });
        } else {
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Temp score handler not available");
        }
    }

    /**
     * Updates the current match's end time when both scores are committed.
     */
    private void updateMatchEndTime() {
        try {
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime currentTime = LocalDateTime.now();

            currentMatch.getMatch().setMatchEndTime(currentTime.format(timeFormatter));
            matchHandler.updateMatch(currentMatch.getMatch(), new RequestCallback<Match>() {
                @Override
                public void onSuccess(Match responseObject, String message) {
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                }
            });
        } catch (Exception e) {
            ILog.e(TAG, "Error updating match end time: " + e.getMessage());
        }
    }

    public void getUpNextMatch(RequestCallback<MatchDetailDto> callback) {
        try {
            if (nextMatch != null) {
                callback.onSuccess(nextMatch, "Up next match retrieved successfully");
            } else {
                callback.onFailure(ErrorCode.NOT_FOUND, "No upcoming match set");
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.RETRIEVE_FAILED, "Failed to get up next match: " + e.getMessage());
        }
    }

    public void showUpNext(RequestCallback<Boolean> callback) {
        try {
            if (nextMatch == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "No upcoming match set");
                return;
            }
            int fieldNumber = nextMatch.getMatch().getFieldNumber();
            broadcastHandler.broadcast("/topic/display/field/" + fieldNumber + "/command", nextMatch, BroadcastMessageType.SHOW_UPNEXT);
            callback.onSuccess(true, "Show up next command sent");
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to show up next: " + e.getMessage());
        }
    }

    public void showCurrentMatch(RequestCallback<Boolean> callback) {
        try {
            if (currentMatch == null) {
                callback.onFailure(ErrorCode.NOT_FOUND, "No current match active");
                return;
            }
            int fieldNumber = currentMatch.getMatch().getFieldNumber();
            broadcastHandler.broadcast("/topic/display/field/" + fieldNumber + "/command", currentMatch, BroadcastMessageType.SHOW_MATCH);
            callback.onSuccess(true, "Show current match command sent");
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.CUSTOM_ERR, "Failed to show current match: " + e.getMessage());
        }
    }

    public void setBroadcastHandler(BroadcastHandler broadcastHandler) {
        this.broadcastHandler = broadcastHandler;
    }
}
