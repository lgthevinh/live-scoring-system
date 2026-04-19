package org.thingai.app.api.endpoints;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.thingai.app.api.utils.ResponseEntityUtil.PendingResponse;
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

import static org.thingai.app.api.utils.ResponseEntityUtil.badRequest;
import static org.thingai.app.api.utils.ResponseEntityUtil.conflict;
import static org.thingai.app.api.utils.ResponseEntityUtil.internalError;
import static org.thingai.app.api.utils.ResponseEntityUtil.notFound;
import static org.thingai.app.api.utils.ResponseEntityUtil.writeFuture;

/**
 * REST surface for match lifecycle + display broadcasts
 * (base path {@code /api/match-control}).
 *
 * <p>Handles load/activate/start/abort/commit transitions, score overrides on
 * completed matches, and display-panel broadcast requests. Broadcasts are
 * fanned out via {@link BroadcastService} which, during the Javalin migration,
 * is a stub; the vanilla-WS router will pick them up in the next step.
 */
public final class MatchControlApi {

    private static final Gson GSON = new Gson();

    private MatchControlApi() {
    }

    public static void register(Javalin app) {
        app.get("/api/match-control/state", MatchControlApi::getState);
        app.post("/api/match-control/load", MatchControlApi::loadMatch);
        app.post("/api/match-control/activate", MatchControlApi::activateMatch);
        app.post("/api/match-control/start", MatchControlApi::startMatch);
        app.post("/api/match-control/abort", MatchControlApi::abortMatch);
        app.post("/api/match-control/commit", MatchControlApi::commitMatch);
        app.post("/api/match-control/override", MatchControlApi::overrideScore);
        app.post("/api/match-control/display", MatchControlApi::controlDisplay);
    }

    private static void getState(Context ctx) {
        if (!ensureEventReady(ctx)) {
            return;
        }

        StateManager stateManager = ScoringService.stateManager();
        Map<String, Object> response = new HashMap<>();
        response.put("loadedMatchId", stateManager.getLoadedMatchId());
        response.put("currentMatchId", stateManager.getCurrentMatchId());
        response.put("state", stateManager.getCurrentMatchState());
        response.put("timerSecondsRemaining", ScoringService.matchControl().getRemainingSeconds());
        ctx.json(response);
    }

    private static void loadMatch(Context ctx) {
        if (!ensureEventReady(ctx)) {
            return;
        }
        LoadRequest request = ctx.bodyAsClass(LoadRequest.class);
        if (request == null || isBlank(request.matchId())) {
            badRequest(ctx, "matchId is required.");
            return;
        }
        if (!matchExists(request.matchId())) {
            notFound(ctx, "Match not found: " + request.matchId());
            return;
        }
        ScoringService.matchControl().loadMatch(request.matchId());
        ctx.json(Map.of("message", "Match loaded.", "matchId", request.matchId()));
    }

    private static void activateMatch(Context ctx) {
        if (!ensureEventReady(ctx)) {
            return;
        }

        ActivateRequest request = ctx.body().isBlank() ? null : ctx.bodyAsClass(ActivateRequest.class);
        StateManager stateManager = ScoringService.stateManager();
        String matchId = request != null ? request.matchId() : null;
        if (isBlank(matchId)) {
            matchId = stateManager.getLoadedMatchId();
        }
        if (isBlank(matchId)) {
            badRequest(ctx, "matchId is required (or load a match first).");
            return;
        }
        if (!matchExists(matchId)) {
            notFound(ctx, "Match not found: " + matchId);
            return;
        }

        ScoringService.matchControl().activeMatch(matchId);
        ctx.json(Map.of("message", "Match activated.", "matchId", matchId));
    }

    private static void startMatch(Context ctx) {
        if (!ensureEventReady(ctx)) {
            return;
        }

        StateManager stateManager = ScoringService.stateManager();
        String matchId = stateManager.getCurrentMatchId();
        if (isBlank(matchId)) {
            badRequest(ctx, "No active match to start.");
            return;
        }

        int state = stateManager.getCurrentMatchState();
        if (state != MatchState.ACTIVE && state != MatchState.LOADED) {
            badRequest(ctx, "Match must be ACTIVE or LOADED to start.");
            return;
        }

        ScoringService.matchControl().startMatch();
        ctx.json(Map.of("message", "Match started.", "matchId", matchId));
    }

    private static void abortMatch(Context ctx) {
        if (!ensureEventReady(ctx)) {
            return;
        }

        StateManager stateManager = ScoringService.stateManager();
        if (isBlank(stateManager.getCurrentMatchId())) {
            badRequest(ctx, "No active match to abort.");
            return;
        }

        ScoringService.matchControl().abortMatch();
        ctx.json(Map.of("message", "Match aborted.", "matchId", stateManager.getCurrentMatchId()));
    }

    private static void commitMatch(Context ctx) {
        if (!ensureEventReady(ctx)) {
            return;
        }

        StateManager stateManager = ScoringService.stateManager();
        String matchId = stateManager.getCurrentMatchId();
        if (isBlank(matchId)) {
            badRequest(ctx, "No active match to commit.");
            return;
        }

        MatchDetailDto matchDetail;
        try {
            matchDetail = LocalRepository.matchDao().getMatchDetailById(matchId);
        } catch (Exception e) {
            internalError(ctx, "Failed to load match details: " + e.getMessage());
            return;
        }

        if (matchDetail == null || matchDetail.getRedScore() == null || matchDetail.getBlueScore() == null) {
            badRequest(ctx, "Scores for current match are missing.");
            return;
        }

        if (matchDetail.getRedScore().getState() != ScoreState.SCORED
                || matchDetail.getBlueScore().getState() != ScoreState.SCORED) {
            badRequest(ctx, "Both alliance scores must be in SCORED state before commit.");
            return;
        }

        ScoringService.matchControl().commitScore();

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.rankingHandler().updateRanking(matchId, new RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean responseObject, String message) {
                ScoringService.rankingHandler().getRankingStatus(new RequestCallback<RankingEntry[]>() {
                    @Override
                    public void onSuccess(RankingEntry[] responseObject, String statusMessage) {
                        BroadcastService.broadcast(LiveBroadcastTopic.LIVE_DISPLAY_RANKING,
                                responseObject, "RANKING_UPDATE");
                        future.complete(PendingResponse.ok(Map.of("message", "Match committed.", "matchId", matchId)));
                    }

                    @Override
                    public void onFailure(int errorCode, String errorMessage) {
                        future.complete(PendingResponse.status(HttpStatus.INTERNAL_SERVER_ERROR,
                                Map.of("error", errorMessage, "matchId", matchId)));
                    }
                });
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(PendingResponse.status(HttpStatus.INTERNAL_SERVER_ERROR,
                        Map.of("error", errorMessage, "matchId", matchId)));
            }
        });
        writeFuture(ctx, future);
    }

    private static void overrideScore(Context ctx) {
        if (!ensureEventReady(ctx)) {
            return;
        }
        OverrideRequest request = ctx.bodyAsClass(OverrideRequest.class);
        if (request == null || isBlank(request.allianceId())) {
            badRequest(ctx, "allianceId is required.");
            return;
        }

        String allianceId = request.allianceId();
        if (!scoreExists(allianceId)) {
            notFound(ctx, "Score not found for alliance: " + allianceId);
            return;
        }

        String matchId = request.matchId();
        if (isBlank(matchId)) {
            matchId = extractMatchId(allianceId);
        }
        if (isBlank(matchId)) {
            badRequest(ctx, "matchId is required.");
            return;
        }

        Score score;
        String rawScoreJson;
        try {
            ScoreBuildResult buildResult = buildScoreFromOverride(request);
            score = buildResult.score();
            rawScoreJson = buildResult.rawJson();
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", "Invalid score override payload: " + e.getMessage()));
            return;
        }

        score.setRawScoreData(rawScoreJson);

        boolean overridden = ScoringService.matchControl().overrideScore(matchId, score);
        if (!overridden) {
            ctx.status(HttpStatus.CONFLICT)
                    .json(Map.of("error", "Override rejected. Match must be completed.", "matchId", matchId));
            return;
        }

        ctx.json(Map.of("message", "Score overridden.", "allianceId", allianceId, "matchId", matchId));
    }

    private static void controlDisplay(Context ctx) {
        DisplayRequest request = ctx.bodyAsClass(DisplayRequest.class);
        if (request == null) {
            badRequest(ctx, "Display action is required.");
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", request.action());
        if (request.data() != null) {
            payload.put("data", request.data());
        }
        BroadcastService.broadcast(LiveBroadcastTopic.LIVE_DISPLAY_CONTROL, payload, "DISPLAY_CONTROL");
        ctx.json(Map.of("message", "Display action broadcast."));
    }

    // --- helpers -------------------------------------------------------------

    private static ScoreBuildResult buildScoreFromOverride(OverrideRequest request) throws Exception {
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

    /**
     * Normalize a loosely-typed score payload to a JSON string. Strings that
     * already look like JSON are returned verbatim; anything else is
     * re-serialized through Gson.
     */
    private static String normalizeScoreData(Object scoreData) {
        if (scoreData instanceof String scoreJson) {
            String trimmed = scoreJson.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return trimmed;
            }
        }
        return GSON.toJson(scoreData);
    }

    /**
     * Guard that writes a 409 and returns false if no event database is loaded.
     */
    private static boolean ensureEventReady(Context ctx) {
        if (LocalRepository.matchDao() == null || LocalRepository.scoreDao() == null) {
            conflict(ctx, "No active event is set.");
            return false;
        }
        return true;
    }

    private static boolean matchExists(String matchId) {
        try {
            return LocalRepository.matchDao().getMatchById(matchId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean scoreExists(String allianceId) {
        try {
            return LocalRepository.scoreDao().getScoreById(allianceId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Alliance ids follow the convention {@code {matchId}_R} / {@code {matchId}_B}. */
    private static String extractMatchId(String allianceId) {
        if (isBlank(allianceId) || allianceId.length() < 3) {
            return null;
        }
        if (allianceId.endsWith("_R") || allianceId.endsWith("_B")) {
            return allianceId.substring(0, allianceId.length() - 2);
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // --- DTOs ----------------------------------------------------------------

    private record LoadRequest(String matchId) {}
    private record ActivateRequest(String matchId) {}
    private record OverrideRequest(String allianceId, String matchId, Object scoreData, Integer penaltiesScore, Integer totalScore) {}
    private record DisplayRequest(int action, Object data) {}
    private record ScoreBuildResult(Score score, String rawJson) {}
}
