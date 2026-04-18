package org.thingai.app.api.ws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.javalin.Javalin;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsMessageContext;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.define.AccountRole;
import org.thingai.app.scoringservice.entity.Score;
import org.thingai.app.scoringservice.matchcontrol.StateManager;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.base.log.ILog;

import java.util.HashMap;
import java.util.Map;

/**
 * Authenticated bidirectional socket at
 * {@code /ws/referee/{matchId}/{alliance}?token=...}.
 *
 * <p>Replaces the legacy STOMP {@code @MessageMapping("live/score/update/...")}
 * controller. One socket per (matchId, alliance) pair per referee tablet.
 *
 * <p>Outbound frames:
 * <ul>
 *   <li>{@link WsMessageType#SNAPSHOT} once on connect: current match state +
 *       the cached draft score for this alliance (so a reconnecting tablet
 *       doesn't lose in-progress entries).</li>
 *   <li>{@link WsMessageType#MATCH_STATE} on every match lifecycle event for
 *       this match, routed via {@code BroadcastService} &rarr;
 *       {@code BroadcastRegistry.refereeSessions(allianceId)}.</li>
 *   <li>{@link WsMessageType#SCORE_ACK} after the server has accepted a
 *       {@code SCORE_DRAFT} from this session.</li>
 * </ul>
 *
 * <p>Inbound frames:
 * <ul>
 *   <li>{@link WsMessageType#SCORE_DRAFT} with payload {@code {state: {...}}}.
 *       Forwarded to
 *       {@link org.thingai.app.scoringservice.matchcontrol.ScoreControl#handleLiveScoreUpdate(String, String)}
 *       using the same JSON shape the old STOMP route used.</li>
 * </ul>
 *
 * <p>Close codes: see {@link WsCloseCode}. Validation order is
 * {@code BAD_REQUEST} (alliance) &rarr; {@code UNAUTHORIZED}/{@code FORBIDDEN}
 * (token + role, via {@link WsAuthFilter}) &rarr; {@code NOT_FOUND} (match).
 * Auth checks happen before the match existence check so we don't leak
 * "which match IDs exist" to unauthenticated callers.
 */
public final class RefereeWs {

    private static final String TAG = "RefereeWs";
    private static final String PATH = "/ws/referee/{matchId}/{alliance}";

    private static final Gson GSON = new Gson();

    /**
     * Key used to stash the {@code allianceId} on the WsContext so
     * {@code onClose} can find the right registry bucket without re-parsing
     * path params.
     */
    private static final String ATTR_ALLIANCE_ID = "refereeAllianceId";

    private RefereeWs() {
    }

    public static void register(Javalin app) {
        app.ws(PATH, ws -> {
            ws.onConnect(RefereeWs::onConnect);
            ws.onClose(ctx -> {
                Object allianceId = ctx.attribute(ATTR_ALLIANCE_ID);
                if (allianceId instanceof String s) {
                    BroadcastRegistry.removeReferee(s, ctx);
                }
            });
            ws.onError(ctx -> {
                Object allianceId = ctx.attribute(ATTR_ALLIANCE_ID);
                if (allianceId instanceof String s) {
                    BroadcastRegistry.removeReferee(s, ctx);
                }
                ILog.w(TAG, "ws error", String.valueOf(ctx.error()));
            });
            ws.onMessage(RefereeWs::onMessage);
        });
    }

    // --- connect --------------------------------------------------------------

    private static void onConnect(WsConnectContext ctx) {
        String matchId = ctx.pathParam("matchId");
        String alliance = ctx.pathParam("alliance");

        // 1. Shape check first -- cheap and independent of auth.
        String normalizedAlliance = normalizeAlliance(alliance);
        if (normalizedAlliance == null) {
            ctx.closeSession(WsCloseCode.BAD_REQUEST, "alliance must be R or B");
            return;
        }

        // 2. Auth (token + role). Failure already closes the session.
        WsAuthFilter.AuthResult auth = WsAuthFilter.authorize(ctx, AccountRole.REFEREE);
        if (!auth.ok()) {
            return;
        }

        // 3. Match must exist in the active event DB. Any failure here closes
        //    with NOT_FOUND rather than SERVER_ERROR so clients behave
        //    consistently when the event isn't loaded.
        if (LocalRepository.matchDao() == null || !matchExists(matchId)) {
            ctx.closeSession(WsCloseCode.NOT_FOUND, "Match not found");
            return;
        }

        String allianceId = matchId + "_" + normalizedAlliance;
        ctx.attribute(ATTR_ALLIANCE_ID, allianceId);

        BroadcastRegistry.addReferee(allianceId, ctx);
        BroadcastRegistry.sendTo(ctx, BroadcastRegistry.refereeSessions(allianceId),
                buildSnapshot(matchId, allianceId));
    }

    private static WsEnvelope buildSnapshot(String matchId, String allianceId) {
        StateManager sm = ScoringService.stateManager();
        Map<String, Object> matchState = new HashMap<>();
        if (sm != null) {
            matchState.put("matchId", matchId);
            matchState.put("currentMatchId", sm.getCurrentMatchId());
            matchState.put("loadedMatchId", sm.getLoadedMatchId());
            matchState.put("state", sm.getCurrentMatchState());
            matchState.put("timerSecondsRemaining",
                    ScoringService.matchControl() != null
                            ? ScoringService.matchControl().getRemainingSeconds()
                            : 0);
        }

        Score draft = sm != null ? sm.getCachedScore(allianceId) : null;

        Map<String, Object> snap = new HashMap<>();
        snap.put("matchState", matchState);
        snap.put("draft", draft);
        return WsEnvelope.now(WsMessageType.SNAPSHOT, snap);
    }

    // --- inbound --------------------------------------------------------------

    private static void onMessage(WsMessageContext ctx) {
        String raw = ctx.message();
        if (raw == null || raw.isBlank()) {
            return;
        }

        JsonObject env;
        try {
            env = GSON.fromJson(raw, JsonObject.class);
        } catch (JsonSyntaxException e) {
            ILog.w(TAG, "bad json on referee socket", e.getMessage());
            return;
        }
        if (env == null) {
            return;
        }

        String type = env.has("type") && !env.get("type").isJsonNull()
                ? env.get("type").getAsString() : null;
        if (!WsMessageType.SCORE_DRAFT.equals(type)) {
            ILog.d(TAG, "dropping unknown inbound type", String.valueOf(type));
            return;
        }

        String matchId = ctx.pathParam("matchId");
        String normalizedAlliance = normalizeAlliance(ctx.pathParam("alliance"));
        if (normalizedAlliance == null) {
            return;
        }
        String allianceColor = "R".equals(normalizedAlliance) ? "red" : "blue";

        // Build the exact JSON shape the legacy ScoreControl expects:
        //   { matchId, alliance, state: {...} }
        // The inbound envelope already holds `payload.state`; we re-wrap it
        // so we don't have to change ScoreControl's parser.
        JsonObject payload = env.has("payload") && env.get("payload").isJsonObject()
                ? env.getAsJsonObject("payload") : new JsonObject();
        JsonObject forLegacy = new JsonObject();
        forLegacy.addProperty("matchId", matchId);
        forLegacy.addProperty("alliance", normalizedAlliance);
        if (payload.has("state")) {
            forLegacy.add("state", payload.get("state"));
        } else {
            // Older/alternative clients may put the state fields at the payload root.
            forLegacy.add("state", payload);
        }

        try {
            ScoringService.liveScoreControl().handleLiveScoreUpdate(GSON.toJson(forLegacy), allianceColor);
        } catch (Exception e) {
            ILog.e(TAG, "handleLiveScoreUpdate threw", e.getMessage());
            return;
        }

        // Ack back to this session only.
        String allianceId = matchId + "_" + normalizedAlliance;
        Map<String, Object> ackPayload = new HashMap<>();
        ackPayload.put("allianceId", allianceId);
        ackPayload.put("matchId", matchId);
        ackPayload.put("state", "ON_REVIEW");
        BroadcastRegistry.sendTo(ctx,
                BroadcastRegistry.refereeSessions(allianceId),
                WsEnvelope.now(WsMessageType.SCORE_ACK, ackPayload));
    }

    // --- helpers --------------------------------------------------------------

    private static String normalizeAlliance(String raw) {
        if (raw == null) {
            return null;
        }
        String up = raw.trim().toUpperCase();
        return ("R".equals(up) || "B".equals(up)) ? up : null;
    }

    private static boolean matchExists(String matchId) {
        try {
            return LocalRepository.matchDao().getMatchById(matchId) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
