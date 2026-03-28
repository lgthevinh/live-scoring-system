package org.thingai.app.api.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.LiveBroadcastTopic;
import org.thingai.app.scoringservice.define.MatchState;
import org.thingai.app.scoringservice.define.ScoreState;
import org.thingai.app.scoringservice.dto.MatchDetailDto;
import org.thingai.app.scoringservice.entity.RankingEntry;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.handler.ScoreHandler;
import org.thingai.app.scoringservice.matchcontrol.StateManager;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.app.scoringservice.service.BroadcastService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/match-control")
public class MatchControlApi {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/state")
    public ResponseEntity<Object> getState() {
        ResponseEntity<Object> readiness = ensureEventReady();
        if (readiness != null) {
            return readiness;
        }

        StateManager stateManager = ScoringService.stateManager();
        Map<String, Object> response = new HashMap<>();
        response.put("loadedMatchId", stateManager.getLoadedMatchId());
        response.put("currentMatchId", stateManager.getCurrentMatchId());
        response.put("state", stateManager.getCurrentMatchState());
        response.put("timerSecondsRemaining", ScoringService.matchControl().getRemainingSeconds());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/load")
    public ResponseEntity<Object> loadMatch(@RequestBody LoadRequest request) {
        ResponseEntity<Object> readiness = ensureEventReady();
        if (readiness != null) {
            return readiness;
        }
        if (request == null || isBlank(request.matchId())) {
            return badRequest("matchId is required.");
        }
        if (!matchExists(request.matchId())) {
            return notFound("Match not found: " + request.matchId());
        }

        ScoringService.matchControl().loadMatch(request.matchId());
        return ResponseEntity.ok(Map.of("message", "Match loaded.", "matchId", request.matchId()));
    }

    @PostMapping("/activate")
    public ResponseEntity<Object> activateMatch(@RequestBody(required = false) ActivateRequest request) {
        ResponseEntity<Object> readiness = ensureEventReady();
        if (readiness != null) {
            return readiness;
        }

        StateManager stateManager = ScoringService.stateManager();
        String matchId = request != null ? request.matchId() : null;
        if (isBlank(matchId)) {
            matchId = stateManager.getLoadedMatchId();
        }
        if (isBlank(matchId)) {
            return badRequest("matchId is required (or load a match first).");
        }
        if (!matchExists(matchId)) {
            return notFound("Match not found: " + matchId);
        }

        ScoringService.matchControl().activeMatch(matchId);
        return ResponseEntity.ok(Map.of("message", "Match activated.", "matchId", matchId));
    }

    @PostMapping("/start")
    public ResponseEntity<Object> startMatch() {
        ResponseEntity<Object> readiness = ensureEventReady();
        if (readiness != null) {
            return readiness;
        }

        StateManager stateManager = ScoringService.stateManager();
        String matchId = stateManager.getCurrentMatchId();
        if (isBlank(matchId)) {
            return badRequest("No active match to start.");
        }

        int state = stateManager.getCurrentMatchState();
        if (state != MatchState.ACTIVE && state != MatchState.LOADED) {
            return badRequest("Match must be ACTIVE or LOADED to start.");
        }

        ScoringService.matchControl().startMatch();
        return ResponseEntity.ok(Map.of("message", "Match started.", "matchId", matchId));
    }

    @PostMapping("/abort")
    public ResponseEntity<Object> abortMatch() {
        ResponseEntity<Object> readiness = ensureEventReady();
        if (readiness != null) {
            return readiness;
        }

        StateManager stateManager = ScoringService.stateManager();
        if (isBlank(stateManager.getCurrentMatchId())) {
            return badRequest("No active match to abort.");
        }

        ScoringService.matchControl().abortMatch();
        return ResponseEntity.ok(Map.of("message", "Match aborted.", "matchId", stateManager.getCurrentMatchId()));
    }

    @PostMapping("/commit")
    public ResponseEntity<Object> commitMatch() {
        ResponseEntity<Object> readiness = ensureEventReady();
        if (readiness != null) {
            return readiness;
        }

        StateManager stateManager = ScoringService.stateManager();
        String matchId = stateManager.getCurrentMatchId();
        if (isBlank(matchId)) {
            return badRequest("No active match to commit.");
        }

        MatchDetailDto matchDetail;
        try {
            matchDetail = LocalRepository.matchDao().getMatchDetailById(matchId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load match details: " + e.getMessage()));
        }

        if (matchDetail == null || matchDetail.getRedScore() == null || matchDetail.getBlueScore() == null) {
            return badRequest("Scores for current match are missing.");
        }

        if (matchDetail.getRedScore().getState() != ScoreState.SCORED
                || matchDetail.getBlueScore().getState() != ScoreState.SCORED) {
            return badRequest("Both alliance scores must be in SCORED state before commit.");
        }

        ScoringService.matchControl().commitScore();

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        ScoringService.rankingHandler().updateRanking(matchId, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                ScoringService.rankingHandler().getRankingStatus(new RequestCallback<RankingEntry[]>() {
                    @Override
                    public void onSuccess(RankingEntry[] responseObject, String statusMessage) {
                        BroadcastService.broadcast(LiveBroadcastTopic.LIVE_DISPLAY_RANKING,
                                responseObject, "RANKING_UPDATE");
                        future.complete(ResponseEntity.ok(Map.of("message", "Match committed.", "matchId", matchId)));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", errorMessage, "matchId", matchId)));
                    }
                });
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", errorMessage, "matchId", matchId)));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/override")
    public ResponseEntity<Object> overrideScore(@RequestBody OverrideRequest request) {
        ResponseEntity<Object> readiness = ensureEventReady();
        if (readiness != null) {
            return readiness;
        }
        if (request == null || isBlank(request.allianceId())) {
            return badRequest("allianceId is required.");
        }

        String allianceId = request.allianceId();
        if (!scoreExists(allianceId)) {
            return notFound("Score not found for alliance: " + allianceId);
        }

        String matchId = request.matchId();
        if (isBlank(matchId)) {
            matchId = extractMatchId(allianceId);
        }
        if (isBlank(matchId)) {
            return badRequest("matchId is required.");
        }

        Score score;
        String rawScoreJson;
        try {
            ScoreBuildResult buildResult = buildScoreFromOverride(request);
            score = buildResult.score();
            rawScoreJson = buildResult.rawJson();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid score override payload: " + e.getMessage()));
        }

        score.setRawScoreData(rawScoreJson);

        boolean overridden = ScoringService.matchControl().overrideScore(matchId, score);
        if (!overridden) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Override rejected. Match must be completed.", "matchId", matchId));
        }

        return ResponseEntity.ok(Map.of("message", "Score overridden.", "allianceId", allianceId, "matchId", matchId));
    }

    @PostMapping("/display")
    public ResponseEntity<Object> controlDisplay(@RequestBody DisplayRequest request) {
        if (request == null) {
            return badRequest("Display action is required.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", request.action());
        if (request.data() != null) {
            payload.put("data", request.data());
        }
        BroadcastService.broadcast(LiveBroadcastTopic.LIVE_DISPLAY_CONTROL, payload, "DISPLAY_CONTROL");
        return ResponseEntity.ok(Map.of("message", "Display action broadcast."));
    }

    private ScoreBuildResult buildScoreFromOverride(OverrideRequest request) throws Exception {
        String allianceId = request.allianceId();
        Score score = ScoreHandler.factoryScore();
        score.setAllianceId(allianceId);

        String rawJson = "{}";
        if (request.scoreData() != null) {
            rawJson = normalizeScoreData(request.scoreData());
            score.fromJson(rawJson);
            score.calculatePenalties();
            score.calculateTotalScore();
        }

        if (request.penaltiesScore() != null) {
            score.setPenaltiesScore(request.penaltiesScore());
        }
        if (request.totalScore() != null) {
            score.setTotalScore(request.totalScore());
        }

        score.setRawScoreData(rawJson);
        score.setState(ScoreState.SCORED);
        return new ScoreBuildResult(score, rawJson);
    }

    private String normalizeScoreData(Object scoreData) throws JsonProcessingException {
        if (scoreData instanceof String scoreJson) {
            String trimmed = scoreJson.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return trimmed;
            }
        }
        return objectMapper.writeValueAsString(scoreData);
    }

    private ResponseEntity<Object> ensureEventReady() {
        if (LocalRepository.matchDao() == null || LocalRepository.scoreDao() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No active event is set."));
        }
        return null;
    }

    private boolean matchExists(String matchId) {
        try {
            return LocalRepository.matchDao().getMatchById(matchId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean scoreExists(String allianceId) {
        try {
            return LocalRepository.scoreDao().getScoreById(allianceId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractMatchId(String allianceId) {
        if (isBlank(allianceId) || allianceId.length() < 3) {
            return null;
        }
        if (allianceId.endsWith("_R") || allianceId.endsWith("_B")) {
            return allianceId.substring(0, allianceId.length() - 2);
        }
        return null;
    }

    private ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private ResponseEntity<Object> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", message));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record LoadRequest(String matchId) {}
    private record ActivateRequest(String matchId) {}
    private record OverrideRequest(String allianceId, String matchId, Object scoreData, Integer penaltiesScore, Integer totalScore) {}
    private record DisplayRequest(int action, Object data) {}
    private record ScoreBuildResult(Score score, String rawJson) {}
}
