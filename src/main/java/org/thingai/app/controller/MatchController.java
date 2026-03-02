package org.thingai.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.match.AllianceTeam;
import org.thingai.app.scoringservice.entity.match.Match;
import org.thingai.app.scoringservice.entity.time.TimeBlock;
import org.thingai.base.log.ILog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.controller.utils.ResponseEntityUtil.createErrorResponse;
import static org.thingai.app.controller.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/create")
    public ResponseEntity<Object> createMatch(@RequestBody Map<String, Object> request) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        try {
            int matchType = Integer.parseInt(request.get("matchType").toString());
            int matchNumber = Integer.parseInt(request.get("matchNumber").toString());
            String matchStartTime = request.get("matchStartTime").toString();
            String[] redTeamIds = ((List<String>) request.get("redTeamIds")).toArray(new String[0]);
            String[] blueTeamIds = ((List<String>) request.get("blueTeamIds")).toArray(new String[0]);

            ScoringService.matchHandler().createMatch(matchType, matchNumber, matchStartTime, redTeamIds, blueTeamIds, createMatchCallback(future));
        } catch (Exception e) {
            future.complete(ResponseEntity.badRequest().body(Map.of("error", "Invalid request format: " + e.getMessage())));
        }
        return getObjectResponse(future);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getMatch(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.matchHandler().getMatch(id, new RequestCallback<Match>() {
            @Override
            public void onSuccess(Match match, String successMessage) {
                future.complete(ResponseEntity.ok(match));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @GetMapping("/list")
    public ResponseEntity<Object> listMatches() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.matchHandler().listMatches(new RequestCallback<Match[]>() {
            @Override
            public void onSuccess(Match[] matches, String successMessage) {
                future.complete(ResponseEntity.ok(matches));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @GetMapping("/list/{matchType}")
    public ResponseEntity<Object> listMatchesByType(@PathVariable int matchType) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.matchHandler().listMatchesByType(matchType, new RequestCallback<Match[]>() {
            @Override
            public void onSuccess(Match[] matches, String successMessage) {
                future.complete(ResponseEntity.ok(matches));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @GetMapping("/list/details/{matchType}")
    public ResponseEntity<Object> listMatchDetails(@PathVariable int matchType, @RequestParam(required = false) boolean withScore) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.matchHandler().listMatchDetails(matchType, withScore, new RequestCallback<MatchDetailDto[]>() {
            @Override
            public void onSuccess(MatchDetailDto[] matchDetails, String successMessage) {
                future.complete(ResponseEntity.ok(matchDetails));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @PutMapping("/update")
    public ResponseEntity<Object> updateMatch(@RequestBody Match match) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.matchHandler().updateMatch(match, createMatchCallback(future));
        return getObjectResponse(future);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteMatch(@PathVariable String id) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.matchHandler().deleteMatch(id, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void result, String successMessage) {
                future.complete(ResponseEntity.ok(Map.of("message", successMessage)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });
        return getObjectResponse(future);
    }

    @PostMapping("/schedule/generate/v2")
    public ResponseEntity<Object> generateScheduleV2(@RequestBody Map<String, Object> request) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        try {
            int rounds = (int) request.get("rounds");
            String startTime = (String) request.get("startTime");
            int matchDuration = (int) request.get("matchDuration");
            int fieldCount = (int) request.get("fieldCount");
            List<Map<String, String>> timeBlockMaps = (List<Map<String, String>>) request.get("timeBlocks");
            TimeBlock[] timeBlocks = objectMapper.convertValue(timeBlockMaps, TimeBlock[].class);
            ScoringService.matchHandler().generateSchedule(rounds, startTime, matchDuration, fieldCount, timeBlocks, new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void result, String successMessage) {
                    future.complete(ResponseEntity.ok(Map.of("message", successMessage)));
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    future.complete(createErrorResponse(errorCode, errorMessage));
                }
            });
        } catch (Exception e) {
            future.complete(ResponseEntity.badRequest().body(Map.of("error", "Invalid request format: " + e.getMessage())));
        }

        return getObjectResponse(future);
    }

    @PostMapping("/schedule/generate/playoff")
    public ResponseEntity<Object> generatePlayoffSchedule(@RequestBody Map<String, Object> request) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        try {
            int playoffType = Integer.parseInt(request.get("playoffType").toString());
            String startTime = request.get("startTime").toString();
            int matchDuration = Integer.parseInt(request.get("matchDuration").toString());
            int fieldCount = Integer.parseInt(request.get("fieldCount").toString());
            AllianceTeam[] allianceTeams = objectMapper.convertValue(request.get("allianceTeams"), AllianceTeam[].class);
            TimeBlock[] timeBlocks = objectMapper.convertValue(request.get("timeBlocks"), TimeBlock[].class);
            ScoringService.matchHandler().generatePlayoffSchedule(playoffType, fieldCount, allianceTeams, startTime, matchDuration, timeBlocks, new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void result, String successMessage) {
                    future.complete(ResponseEntity.ok(Map.of("message", successMessage)));
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    ILog.e("MatchController", "Failed to generate playoff schedule: " + errorMessage);
                    future.complete(createErrorResponse(errorCode, errorMessage));
                }
            });
        } catch (Exception e) {
            future.complete(ResponseEntity.badRequest().body(Map.of("error", "Invalid request format: " + e.getMessage())));
        }
        return getObjectResponse(future);
    }

    // --- Helper Methods for DRY code ---
    private RequestCallback<Match> createMatchCallback(CompletableFuture<ResponseEntity<Object>> future) {
        return new RequestCallback<>() {
            @Override
            public void onSuccess(Match match, String successMessage) {
                future.complete(ResponseEntity.ok(Map.of("message", successMessage, "matchId", match.getId())));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        };
    }
}

