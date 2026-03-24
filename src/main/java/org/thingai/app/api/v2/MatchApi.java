package org.thingai.app.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.AllianceTeam;
import org.thingai.app.scoringservice.entity.Match;
import org.thingai.app.scoringservice.entity.TimeBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.createErrorResponse;
import static org.thingai.app.api.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/match")
public class MatchApi {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/create")
    public ResponseEntity<Object> createMatch(@RequestBody Map<String, Object> request) {
        Integer matchType = parseInt(request == null ? null : request.get("matchType"));
        Integer matchNumber = parseInt(request == null ? null : request.get("matchNumber"));
        String matchStartTime = request == null ? null : (String) request.get("matchStartTime");
        Integer fieldNumber = parseInt(request == null ? null : request.get("fieldNumber"));

        String[] redTeamIds = parseStringArray(request == null ? null : request.get("redTeamIds"));
        String[] blueTeamIds = parseStringArray(request == null ? null : request.get("blueTeamIds"));

        if (matchType == null || matchNumber == null || isBlank(matchStartTime)) {
            return ResponseEntity.badRequest().body(Map.of("error", "matchType, matchNumber, and matchStartTime are required."));
        }

        if (redTeamIds.length == 0 || blueTeamIds.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "redTeamIds and blueTeamIds are required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.matchHandler().createMatch(
                matchType,
                matchNumber,
                fieldNumber == null ? 1 : fieldNumber,
                matchStartTime,
                redTeamIds,
                blueTeamIds,
                new RequestCallback<Match>() {
                    @Override
                    public void onSuccess(Match responseObject, String message) {
                        future.complete(ResponseEntity.ok(Map.of("message", message, "matchId", responseObject.getId())));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(createErrorResponse(errorCode, errorMessage));
                    }
                }
        );

        return getObjectResponse(future);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getMatch(@PathVariable String id) {
        if (isBlank(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match id is required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.matchHandler().getMatch(id, new RequestCallback<Match>() {
            @Override
            public void onSuccess(Match responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("")
    public ResponseEntity<Object> listMatches() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.matchHandler().listMatches(new RequestCallback<Match[]>() {
            @Override
            public void onSuccess(Match[] responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
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
            public void onSuccess(Match[] responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
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

        ScoringService.matchHandler().listMatchDetailsByType(matchType, withScore, new RequestCallback<MatchDetailDto[]>() {
            @Override
            public void onSuccess(MatchDetailDto[] responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
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

        ScoringService.matchHandler().updateMatch(match, new RequestCallback<Match>() {
            @Override
            public void onSuccess(Match responseObject, String message) {
                future.complete(ResponseEntity.ok(responseObject));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteMatch(@PathVariable String id) {
        if (isBlank(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match id is required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.matchHandler().deleteMatch(id, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void responseObject, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(createErrorResponse(errorCode, errorMessage));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/schedule/generate")
    public ResponseEntity<Object> generateSchedule(@RequestBody Map<String, Object> request) {
        return handleGenerateSchedule(request);
    }

    @PostMapping("/schedule/generate/v2")
    public ResponseEntity<Object> generateScheduleV2(@RequestBody Map<String, Object> request) {
        return handleGenerateSchedule(request);
    }

    @PostMapping("/playoff/generate")
    public ResponseEntity<Object> generatePlayoffSchedule(@RequestBody Map<String, Object> request) {
        Integer playoffType = parseInt(request == null ? null : request.get("playoffType"));
        Integer fieldCount = parseInt(request == null ? null : request.get("fieldCount"));
        Integer matchDuration = parseInt(request == null ? null : request.get("matchDuration"));
        String startTime = request == null ? null : (String) request.get("startTime");

        AllianceTeam[] allianceTeams = parseAllianceTeams(request == null ? null : request.get("allianceTeams"));
        TimeBlock[] timeBlocks = parseTimeBlocks(request == null ? null : request.get("timeBlocks"));

        if (playoffType == null || matchDuration == null || fieldCount == null || isBlank(startTime)) {
            return ResponseEntity.badRequest().body(Map.of("error", "playoffType, fieldCount, matchDuration, and startTime are required."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.scheduleHandler().generatePlayoffSchedule(
                playoffType,
                fieldCount,
                allianceTeams,
                startTime,
                matchDuration,
                timeBlocks,
                new RequestCallback<Void>() {
                    @Override
                    public void onSuccess(Void responseObject, String message) {
                        future.complete(ResponseEntity.ok(Map.of("message", message)));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(createErrorResponse(errorCode, errorMessage));
                    }
                }
        );

        return getObjectResponse(future);
    }

    @PostMapping("/schedule/generate/playoff")
    public ResponseEntity<Object> generatePlayoffScheduleV2(@RequestBody Map<String, Object> request) {
        return generatePlayoffSchedule(request);
    }

    private ResponseEntity<Object> handleGenerateSchedule(Map<String, Object> request) {
        Integer rounds = parseInt(request == null ? null : request.get("rounds"));
        Integer matchDuration = parseInt(request == null ? null : request.get("matchDuration"));
        Integer fieldCount = parseInt(request == null ? null : request.get("fieldCount"));
        String startTime = request == null ? null : (String) request.get("startTime");
        TimeBlock[] timeBlocks = parseTimeBlocks(request == null ? null : request.get("timeBlocks"));

        if (rounds == null || matchDuration == null || isBlank(startTime)) {
            return ResponseEntity.badRequest().body(Map.of("error", "rounds, matchDuration, and startTime are required."));
        }

        int resolvedFieldCount = fieldCount == null ? 1 : fieldCount;

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.scheduleHandler().generateSchedule(
                rounds,
                startTime,
                matchDuration,
                resolvedFieldCount,
                timeBlocks,
                new RequestCallback<Void>() {
                    @Override
                    public void onSuccess(Void responseObject, String message) {
                        future.complete(ResponseEntity.ok(Map.of("message", message)));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(createErrorResponse(errorCode, errorMessage));
                    }
                }
        );

        return getObjectResponse(future);
    }

    private TimeBlock[] parseTimeBlocks(Object value) {
        if (value == null) {
            return new TimeBlock[0];
        }
        return objectMapper.convertValue(value, TimeBlock[].class);
    }

    private AllianceTeam[] parseAllianceTeams(Object value) {
        if (value == null) {
            return new AllianceTeam[0];
        }
        return objectMapper.convertValue(value, AllianceTeam[].class);
    }

    private String[] parseStringArray(Object value) {
        if (value == null) {
            return new String[0];
        }
        if (value instanceof String strValue) {
            if (strValue.trim().isEmpty()) {
                return new String[0];
            }
            String[] parts = strValue.split(",");
            List<String> cleaned = new ArrayList<>();
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    cleaned.add(part.trim());
                }
            }
            return cleaned.toArray(new String[0]);
        }
        if (value instanceof List<?> list) {
            List<String> cleaned = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = item.toString().trim();
                if (!text.isEmpty()) {
                    cleaned.add(text);
                }
            }
            return cleaned.toArray(new String[0]);
        }
        return objectMapper.convertValue(value, String[].class);
    }

    private Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            if (stringValue.trim().isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
