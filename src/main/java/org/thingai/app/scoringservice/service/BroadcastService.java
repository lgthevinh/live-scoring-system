package org.thingai.app.scoringservice.service;

import org.thingai.app.api.ws.BroadcastRegistry;
import org.thingai.app.api.ws.WsEnvelope;
import org.thingai.app.api.ws.WsMessageType;
import org.thingai.app.scoringservice.define.LiveBroadcastTopic;
import org.thingai.base.log.ILog;

import java.util.Map;

/**
 * Topic-to-endpoint dispatcher for the WebSocket layer.
 *
 * <p>Public API ({@link #broadcast(String, Object, String)}) is unchanged from
 * the original Spring/STOMP implementation, so existing call sites in
 * {@code MatchControl} and {@code MatchControlApi} keep working without edits.
 * Internally the topic constant decides which {@link BroadcastRegistry} pool
 * receives the frame, and what {@code type} discriminator the frame carries.
 *
 * <p>Mapping (matches the design doc):
 * <pre>
 *   LIVE_MATCH              &rarr; /ws/live           as MATCH_STATE
 *                           +&rarr; /ws/referee/{matchId}_R, _B  as MATCH_STATE
 *   LIVE_SCORE_UPDATE_*     &rarr; /ws/live           as SCORE_UPDATE
 *   LIVE_DISPLAY_CONTROL    &rarr; /ws/live           as DISPLAY_CONTROL
 *   LIVE_DISPLAY_RANKING    &rarr; /ws/ranking        as RANKING_UPDATE
 *   LIVE_DISPLAY_SCORE      &rarr; /ws/live           as SCORE_UPDATE   (legacy alias)
 * </pre>
 *
 * <p>The {@code messageType} argument from legacy callers is intentionally
 * ignored: the wire {@code type} is decided by the topic, not by the caller.
 * This keeps clients from having to handle ad-hoc type strings (e.g. the
 * legacy {@code "SCORE_OVERRIDE"}).
 *
 * <p>Last-broadcast snapshots are remembered per-topic so endpoints can replay
 * them to newly-connected sessions; see {@link #lastFor(String)}.
 */
public final class BroadcastService {
    private static final String TAG = "BroadcastService";

    /**
     * Most recent payload broadcast for each topic. Used by endpoint
     * {@code onConnect} handlers to build SNAPSHOT frames so reconnecting
     * clients don't have to wait for the next live event.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> LAST_BY_TOPIC =
            new java.util.concurrent.ConcurrentHashMap<>();

    private BroadcastService() {
    }

    /**
     * Broadcast a payload to whichever endpoint(s) the topic targets.
     *
     * @param topic       a constant from {@link LiveBroadcastTopic}
     * @param message     the payload (will be JSON-serialized)
     * @param messageType legacy hint, ignored &mdash; kept for signature compatibility
     */
    public static void broadcast(String topic, Object message, String messageType) {
        if (topic == null) {
            ILog.w(TAG, "broadcast called with null topic");
            return;
        }
        LAST_BY_TOPIC.put(topic, message);

        switch (topic) {
            case LiveBroadcastTopic.LIVE_MATCH -> {
                WsEnvelope env = WsEnvelope.now(WsMessageType.MATCH_STATE, message);
                BroadcastRegistry.fanOut(BroadcastRegistry.liveSessions(),
                        BroadcastRegistry.liveSessions(), env);
                fanToRefereesForMatch(message, env);
            }
            case LiveBroadcastTopic.LIVE_SCORE_UPDATE_RED,
                 LiveBroadcastTopic.LIVE_SCORE_UPDATE_BLUE,
                 LiveBroadcastTopic.LIVE_DISPLAY_SCORE -> {
                WsEnvelope env = WsEnvelope.now(WsMessageType.SCORE_UPDATE, message);
                BroadcastRegistry.fanOut(BroadcastRegistry.liveSessions(),
                        BroadcastRegistry.liveSessions(), env);
            }
            case LiveBroadcastTopic.LIVE_DISPLAY_CONTROL -> {
                WsEnvelope env = WsEnvelope.now(WsMessageType.DISPLAY_CONTROL, message);
                BroadcastRegistry.fanOut(BroadcastRegistry.liveSessions(),
                        BroadcastRegistry.liveSessions(), env);
            }
            case LiveBroadcastTopic.LIVE_DISPLAY_RANKING -> {
                WsEnvelope env = WsEnvelope.now(WsMessageType.RANKING_UPDATE, message);
                BroadcastRegistry.fanOut(BroadcastRegistry.rankingSessions(),
                        BroadcastRegistry.rankingSessions(), env);
            }
            default -> ILog.w(TAG, "no fan-out mapping for topic", topic);
        }
    }

    /**
     * Look up the last payload broadcast for {@code topic}, or {@code null}
     * if nothing has been broadcast since startup. Used by endpoint
     * {@code onConnect} handlers to assemble SNAPSHOT frames.
     */
    public static Object lastFor(String topic) {
        return LAST_BY_TOPIC.get(topic);
    }

    /**
     * For a match-state payload, also push to the two referee buckets keyed by
     * {@code matchId_R} and {@code matchId_B} so referee tablets see the same
     * lifecycle frames as displays. Silent no-op if the payload doesn't carry
     * a {@code matchId} we can extract.
     */
    private static void fanToRefereesForMatch(Object payload, WsEnvelope env) {
        String matchId = extractMatchId(payload);
        if (matchId == null) {
            return;
        }
        String redId = matchId + "_R";
        String blueId = matchId + "_B";
        BroadcastRegistry.fanOut(BroadcastRegistry.refereeSessions(redId),
                BroadcastRegistry.refereeSessions(redId), env);
        BroadcastRegistry.fanOut(BroadcastRegistry.refereeSessions(blueId),
                BroadcastRegistry.refereeSessions(blueId), env);
    }

    private static String extractMatchId(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object v = map.get("matchId");
            return v == null ? null : v.toString();
        }
        return null;
    }
}
