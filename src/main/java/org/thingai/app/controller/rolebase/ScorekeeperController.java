package org.thingai.app.controller.rolebase;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.controller.utils.ResponseEntityUtil;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.app.scoringservice.entity.score.Score;

import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scorekeeper")
public class ScorekeeperController {
    private static final String TAG = "ScorekeeperController";

    @PostMapping("/start-current-match")
    public ResponseEntity<Object> startMatch() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().startCurrentMatch(new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/activate-match")
    public ResponseEntity<Object> activateMatch() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().activateMatch(new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/set-next-match/{id}")
    public ResponseEntity<Object> setNextMatch(@PathVariable("id") String nextMatchId) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().setNextMatch(nextMatchId, new RequestCallback<MatchDetailDto>() {
            @Override
            public void onSuccess(MatchDetailDto responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/commit-final-score")
    public ResponseEntity<Object> submitFinalScore() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().commitFinalScore(new RequestCallback<Score[]>() {
            @Override
            public void onSuccess(Score[] responseObject, String message) {
                // Trigger ranking update after scores are committed
                if (responseObject != null && responseObject.length > 0) {
                    String allianceId = responseObject[0].getAllianceId();
                    updateRankingsForAlliance(allianceId);
                    // Also check if we should set match end time
                    String matchId = allianceId.contains("_") ? allianceId.substring(0, allianceId.indexOf("_")) : allianceId;
                    setMatchEndTimeIfBothScoresApproved(matchId);
                }
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/override-score/{allianceId}")
    public ResponseEntity<Object> overrideScore(@PathVariable("allianceId") String allianceId, @RequestBody String jsonScoreData) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().overrideScore(allianceId, jsonScoreData, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/abort-current-match")
    public ResponseEntity<Object> abortCurrentMatch() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().abortCurrentMatch(new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/show-upnext")
    public ResponseEntity<Object> showUpNext() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().showUpNext(new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/show-current-match")
    public ResponseEntity<Object> showCurrentMatch() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.liveScoreHandler().showCurrentMatch(new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @GetMapping("/pending-scores/{matchId}")
    public ResponseEntity<Object> getPendingScores(@PathVariable("matchId") String matchId) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.scoreHandler().getPendingScores(matchId, new RequestCallback<java.util.Map<String, Score>>() {
            @Override
            public void onSuccess(java.util.Map<String, Score> responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/approve-score/{allianceId}")
    public ResponseEntity<Object> approveScore(@PathVariable("allianceId") String allianceId) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.scoreHandler().approveScore(allianceId, new RequestCallback<Score>() {
            @Override
            public void onSuccess(Score responseObject, String message) {
                // Trigger ranking update
                updateRankingsForAlliance(allianceId);
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/reject-score/{allianceId}")
    public ResponseEntity<Object> rejectScore(@PathVariable("allianceId") String allianceId) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.scoreHandler().rejectScore(allianceId, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/temp-score/{allianceId}")
    public ResponseEntity<Object> saveTempScore(@PathVariable("allianceId") String allianceId, @RequestBody Map<String, Object> requestBody) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        
        String submittedBy = requestBody.get("submittedBy") != null ? requestBody.get("submittedBy").toString() : "referee";
        Object scoreDataObj = requestBody.get("scoreData");
        String jsonScoreData = scoreDataObj != null ? scoreDataObj.toString() : "{}";
        
        ScoringService.tempScoreHandler().saveTempScore(allianceId, jsonScoreData, submittedBy, new RequestCallback<String>() {
            @Override
            public void onSuccess(String tempFilePath, String message) {
                // Extract tempScoreId from file path (e.g., "/temp_scores/Q1_R_20240302_120000_abc123.json" -> "Q1_R_20240302_120000_abc123")
                String tempScoreId = tempFilePath.replace("/temp_scores/", "").replace(".json", "");
                Map<String, String> response = new HashMap<>();
                response.put("tempScoreId", tempScoreId);
                future.complete(ResponseEntity.ok().body(response));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @GetMapping("/temp-scores/{allianceId}")
    public ResponseEntity<Object> getAllTempScores(@PathVariable("allianceId") String allianceId) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.tempScoreHandler().getAllTempScoresForAlliance(allianceId, new RequestCallback<java.util.List<String>>() {
            @Override
            public void onSuccess(java.util.List<String> responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @GetMapping("/temp-score/{allianceId}")
    public ResponseEntity<Object> getSingleTempScore(@PathVariable("allianceId") String allianceId) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.tempScoreHandler().getAllTempScoresForAlliance(allianceId, new RequestCallback<java.util.List<String>>() {
            @Override
            public void onSuccess(java.util.List<String> responseObject, String message) {
                // Return the first non-deleted temp score, or null if none
                if (responseObject != null && !responseObject.isEmpty()) {
                    future.complete(ResponseEntity.ok().body(responseObject.get(0)));
                } else {
                    future.complete(ResponseEntity.ok().body(null));
                }
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/commit-temp-score/{tempScoreId}")
    public ResponseEntity<Object> commitTempScore(@PathVariable("tempScoreId") String tempScoreId) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        String approvedBy = "scorekeeper";
        ScoringService.tempScoreHandler().commitTempScore(tempScoreId, approvedBy, new RequestCallback<Score>() {
            @Override
            public void onSuccess(Score responseObject, String message) {
                // Extract allianceId from tempScoreId (e.g., "Q1_R_20240302_120000_abc123" -> "Q1_R")
                String allianceId = tempScoreId.contains("_") && tempScoreId.lastIndexOf("_") > tempScoreId.indexOf("_")
                    ? tempScoreId.substring(0, tempScoreId.lastIndexOf("_"))
                    : tempScoreId;
                // Further extract the base allianceId if it has timestamp suffix
                if (allianceId.contains("_")) {
                    String[] parts = allianceId.split("_");
                    if (parts.length >= 2) {
                        allianceId = parts[0] + "_" + parts[1];
                    }
                }
                // Also check if we should set match end time
                String matchId = allianceId.contains("_") ? allianceId.substring(0, allianceId.indexOf("_")) : allianceId;
                setMatchEndTimeIfBothScoresApproved(matchId);
                updateRankingsForAlliance(allianceId);
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    @PostMapping("/reject-temp-score/{tempScoreId}")
    public ResponseEntity<Object> rejectTempScore(@PathVariable("tempScoreId") String tempScoreId, @RequestBody Map<String, Object> requestBody) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        String rejectedBy = requestBody.get("rejectedBy") != null ? requestBody.get("rejectedBy").toString() : "scorekeeper";
        String reason = requestBody.get("reason") != null ? requestBody.get("reason").toString() : "Rejected by scorekeeper";
        ScoringService.tempScoreHandler().rejectTempScore(tempScoreId, rejectedBy, reason, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                future.complete(ResponseEntity.ok().body(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.badRequest().body(errorMessage));
            }
        });
        return ResponseEntityUtil.getObjectResponse(future);
    }

    private void updateRankingsForAlliance(String allianceId) {
        try {
            // Extract matchId from allianceId (e.g., "Q4_R" -> "Q4")
            String matchId = allianceId.contains("_") 
                ? allianceId.substring(0, allianceId.lastIndexOf("_")) 
                : allianceId;
            
            System.out.println("[DEBUG] updateRankingsForAlliance called for allianceId=" + allianceId + ", matchId=" + matchId);
            
            // Fetch match details with scores
            ScoringService.matchHandler().getMatchDetail(matchId, new RequestCallback<MatchDetailDto>() {
                @Override
                public void onSuccess(MatchDetailDto matchDetail, String message) {
                    System.out.println("[DEBUG] Got matchDetail: " + (matchDetail != null ? "not null" : "null"));
                    if (matchDetail != null) {
                        System.out.println("[DEBUG] BlueScore: " + (matchDetail.getBlueScore() != null ? "not null" : "null"));
                        System.out.println("[DEBUG] RedScore: " + (matchDetail.getRedScore() != null ? "not null" : "null"));
                    }
                    if (matchDetail != null && matchDetail.getBlueScore() != null && matchDetail.getRedScore() != null) {
                        System.out.println("[DEBUG] Calling updateRankingEntry for match " + matchId);
                        ScoringService.rankingHandler().updateRankingEntry(
                            matchDetail, 
                            matchDetail.getBlueScore(), 
                            matchDetail.getRedScore()
                        );
                    } else {
                        System.out.println("[DEBUG] Skipping ranking update - missing scores");
                    }
                }
                
                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    System.err.println("Failed to update rankings: " + errorMessage);
                }
            });
        } catch (Exception e) {
            System.err.println("Error triggering ranking update: " + e.getMessage());
        }
    }


    /**
     * Set match end time if both scores are approved
     */
    private void setMatchEndTimeIfBothScoresApproved(String matchId) {
        String redAllianceId = matchId + "_R";
        String blueAllianceId = matchId + "_B";
        
        ScoringService.scoreHandler().getScoreByAllianceId(redAllianceId, new RequestCallback<Score>() {
            @Override
            public void onSuccess(Score redScore, String message) {
                ScoringService.scoreHandler().getScoreByAllianceId(blueAllianceId, new RequestCallback<Score>() {
                    @Override
                    public void onSuccess(Score blueScore, String message) {
                        if (redScore != null && blueScore != null && 
                            redScore.isApproved() && blueScore.isApproved()) {
                            // Both scores approved - set match end time
                            ScoringService.matchHandler().getMatchDetail(matchId, new RequestCallback<MatchDetailDto>() {
                                @Override
                                public void onSuccess(MatchDetailDto matchDetail, String message) {
                                    if (matchDetail != null && matchDetail.getMatch() != null && 
                                        matchDetail.getMatch().getMatchEndTime() == null) {
                                        // Set match end time
                                        java.time.LocalDateTime now = java.time.LocalDateTime.now();
                                        String endTime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                                        matchDetail.getMatch().setMatchEndTime(endTime);
                                        
                                        ScoringService.matchHandler().updateMatch(matchDetail.getMatch(), new RequestCallback<Match>() {
                                            @Override
                                            public void onSuccess(Match m, String msg) {
                                                System.out.println("[DEBUG] Set match end time for " + matchId + ": " + endTime);
                                            }
                                            @Override
                                            public void onFailure(int code, String err) {
                                                System.err.println("[ERROR] Failed to set match end time: " + err);
                                            }
                                        });
                                    }
                                }
                                @Override
                                public void onFailure(int errorCode, String errorMessage) {}
                            });
                        }
                    }
                    @Override
                    public void onFailure(int errorCode, String errorMessage) {}
                });
            }
            @Override
            public void onFailure(int errorCode, String errorMessage) {}
        });
    }
}
