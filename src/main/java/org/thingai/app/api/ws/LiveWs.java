package org.thingai.app.api.ws;

import io.javalin.Javalin;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.define.LiveBroadcastTopic;
import org.thingai.app.scoringservice.matchcontrol.StateManager;
import org.thingai.app.scoringservice.service.BroadcastService;
import org.thingai.base.log.ILog;

import java.util.HashMap;
import java.util.Map;

/**
 * Public, server &rarr; client only feed at {@code /ws/live}.
 *
 * <p>Carries the three frame kinds every passive viewer (field display,
 * up-next display, audience screen, dashboard) needs simultaneously:
 * {@link WsMessageType#MATCH_STATE}, {@link WsMessageType#SCORE_UPDATE},
 * {@link WsMessageType#DISPLAY_CONTROL}.
 *
 * <p>No authentication. No inbound message handling &mdash; if a client sends
 * a frame on this socket it is logged and ignored.
 *
 * <p>On connect, sends a single {@link WsMessageType#SNAPSHOT} frame so a
 * tab opened (or reconnecting) mid-event immediately sees the current state
 * rather than waiting for the next live event.
 */
public final class LiveWs {

    private static final String TAG = "LiveWs";
    private static final String PATH = "/ws/live";

    private LiveWs() {
    }

    public static void register(Javalin app) {
        app.ws(PATH, ws -> {
            ws.onConnect(ctx -> {
                BroadcastRegistry.addLive(ctx);
                BroadcastRegistry.sendTo(ctx, BroadcastRegistry.liveSessions(), buildSnapshot());
            });
            ws.onClose(ctx -> BroadcastRegistry.removeLive(ctx));
            ws.onError(ctx -> {
                ILog.w(TAG, "ws error", String.valueOf(ctx.error()));
                BroadcastRegistry.removeLive(ctx);
            });
            ws.onMessage(ctx -> {
                // Read-only feed: drop inbound silently but log so we notice
                // a misbehaving client.
                ILog.d(TAG, "unexpected inbound message dropped");
            });
        });
    }

    /**
     * Build the SNAPSHOT payload from the most recent broadcasts plus
     * {@link StateManager} for fields no broadcast has touched yet (e.g. on
     * a cold server with a match already loaded but no events fired since).
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code matchState} &ndash; last LIVE_MATCH payload, or a freshly
     *       built one from StateManager if none has fired.</li>
     *   <li>{@code lastScoreRed} / {@code lastScoreBlue} &ndash; most recent
     *       payload from each alliance score topic, or {@code null}.</li>
     *   <li>{@code lastDisplay} &ndash; most recent display-control payload,
     *       or {@code null}.</li>
     * </ul>
     */
    private static WsEnvelope buildSnapshot() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("matchState", currentMatchStatePayload());
        snap.put("lastScoreRed", BroadcastService.lastFor(LiveBroadcastTopic.LIVE_SCORE_UPDATE_RED));
        snap.put("lastScoreBlue", BroadcastService.lastFor(LiveBroadcastTopic.LIVE_SCORE_UPDATE_BLUE));
        snap.put("lastDisplay", BroadcastService.lastFor(LiveBroadcastTopic.LIVE_DISPLAY_CONTROL));
        return WsEnvelope.now(WsMessageType.SNAPSHOT, snap);
    }

    /**
     * Prefer the cached last broadcast (richer payload &mdash; includes team
     * ids), fall back to a synthesized snapshot from {@link StateManager}.
     */
    private static Object currentMatchStatePayload() {
        Object cached = BroadcastService.lastFor(LiveBroadcastTopic.LIVE_MATCH);
        if (cached != null) {
            return cached;
        }
        StateManager sm = ScoringService.stateManager();
        if (sm == null) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId", sm.getCurrentMatchId());
        payload.put("loadedMatchId", sm.getLoadedMatchId());
        payload.put("state", sm.getCurrentMatchState());
        payload.put("timerSecondsRemaining",
                ScoringService.matchControl() != null ? ScoringService.matchControl().getRemainingSeconds() : 0);
        return payload;
    }
}
