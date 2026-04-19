package org.thingai.app.api.endpoints;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.dto.ScoreDetailDto;
import org.thingai.app.scoringservice.entity.Score;

import java.util.HashMap;
import java.util.Map;

import static org.thingai.app.api.utils.ResponseEntityUtil.badRequest;
import static org.thingai.app.api.utils.ResponseEntityUtil.conflict;
import static org.thingai.app.api.utils.ResponseEntityUtil.internalError;

/**
 * REST surface for score lookup, submission, and UI definitions
 * (base path {@code /api/scores}).
 */
public final class ScoreApi {

    private ScoreApi() {
    }

    public static void register(Javalin app) {
        app.get("/api/scores", ScoreApi::getAllScores);
        app.get("/api/scores/define", ScoreApi::getScoreUIDefinitions);
        app.get("/api/scores/match/{matchId}", ScoreApi::getMatchScore);
        app.get("/api/scores/match/{matchId}/detail", ScoreApi::getMatchScoreDetail);
        app.post("/api/scores/submit", ScoreApi::submitScore);
    }

    private static void getMatchScore(Context ctx) {
        String matchId = ctx.pathParam("matchId");
        if (matchId == null || matchId.isBlank()) {
            badRequest(ctx, "matchId is required.");
            return;
        }
        try {
            Score[] scores = ScoringService.scoreHandler().getScoresByMatchId(matchId);

            Score redScore = scores.length > 0 ? scores[0] : null;
            Score blueScore = scores.length > 1 ? scores[1] : null;
            Integer state = deriveScoreState(redScore, blueScore);

            Map<String, Object> payload = new HashMap<>();
            payload.put("matchId", matchId);
            payload.put("r", redScore);
            payload.put("b", blueScore);
            payload.put("state", state);
            ctx.json(payload);
        } catch (IllegalStateException e) {
            conflict(ctx, e.getMessage());
        } catch (Exception e) {
            internalError(ctx, "Failed to load match score: " + e.getMessage());
        }
    }

    private static void getAllScores(Context ctx) {
        try {
            ctx.json(ScoringService.scoreHandler().getAllScores());
        } catch (IllegalStateException e) {
            conflict(ctx, e.getMessage());
        } catch (Exception e) {
            internalError(ctx, "Failed to load scores: " + e.getMessage());
        }
    }

    private static void getMatchScoreDetail(Context ctx) {
        String matchId = ctx.pathParam("matchId");
        if (matchId == null || matchId.isBlank()) {
            badRequest(ctx, "matchId is required.");
            return;
        }
        try {
            ScoreDetailDto[] details = ScoringService.scoreHandler().getScoreDetailsByMatchId(matchId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("matchId", matchId);
            payload.put("r", details.length > 0 ? details[0] : null);
            payload.put("b", details.length > 1 ? details[1] : null);
            ctx.json(payload);
        } catch (IllegalStateException e) {
            conflict(ctx, e.getMessage());
        } catch (Exception e) {
            internalError(ctx, "Failed to load score details: " + e.getMessage());
        }
    }

    private static void submitScore(Context ctx) {
        String body = ctx.body();
        if (body == null || body.isBlank()) {
            badRequest(ctx, "Score payload is required.");
            return;
        }
        if (ScoringService.liveScoreControl() == null) {
            conflict(ctx, "Scoring service not ready.");
            return;
        }

        ScoringService.liveScoreControl().handleScoreSubmit(body);
        ctx.json(Map.of("message", "Score submitted."));
    }

    private static void getScoreUIDefinitions(Context ctx) {
        if (ScoringService.scoreHandler() == null) {
            conflict(ctx, "Scoring service not ready.");
            return;
        }
        ctx.json(ScoringService.scoreHandler().getScoreDefinitions());
    }

    private static Integer deriveScoreState(Score redScore, Score blueScore) {
        Integer redState = redScore != null ? redScore.getState() : null;
        Integer blueState = blueScore != null ? blueScore.getState() : null;

        if (redState == null) {
            return blueState;
        }
        if (blueState == null) {
            return redState;
        }
        if (redState.equals(blueState)) {
            return redState;
        }
        return Math.max(redState, blueState);
    }
}
