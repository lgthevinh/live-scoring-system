package org.thingai.app.api.ws;

import com.google.gson.Gson;
import io.javalin.websocket.WsContext;
import org.thingai.base.log.ILog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory registry of live WebSocket sessions, grouped by endpoint.
 *
 * <p>Three session pools, matching the three WS endpoints:
 * <ul>
 *   <li>{@link #liveSessions()} &ndash; every {@code /ws/live} connection.</li>
 *   <li>{@link #rankingSessions()} &ndash; every {@code /ws/ranking} connection.</li>
 *   <li>{@link #refereeSessions(String)} &ndash; scoped by {@code allianceId}
 *       (matchId + "_R" / "_B"), because a referee broadcast targets exactly
 *       the tablets working one side of one match.</li>
 * </ul>
 *
 * <p>All collections are lock-free: {@link CopyOnWriteArraySet} is cheap for
 * this workload (writes only on connect/disconnect, reads on every broadcast).
 * At expected scale (tens of sessions) this is strictly better than a
 * synchronized set.
 *
 * <p>Only {@link BroadcastService} and the endpoint classes
 * ({@code LiveWs}/{@code RankingWs}/{@code RefereeWs}) should touch this class.
 * Keeping the registry separate from the service lets us unit-test the fan-out
 * logic without a real Jetty instance.
 */
public final class BroadcastRegistry {

    private static final String TAG = "BroadcastRegistry";
    private static final Gson GSON = new Gson();

    private static final Set<WsContext> LIVE = new CopyOnWriteArraySet<>();
    private static final Set<WsContext> RANKING = new CopyOnWriteArraySet<>();
    private static final ConcurrentHashMap<String, Set<WsContext>> REFEREE = new ConcurrentHashMap<>();

    private BroadcastRegistry() {
    }

    // --- live -----------------------------------------------------------------

    public static void addLive(WsContext ctx) {
        LIVE.add(ctx);
    }

    public static void removeLive(WsContext ctx) {
        LIVE.remove(ctx);
    }

    public static Set<WsContext> liveSessions() {
        return LIVE;
    }

    // --- ranking --------------------------------------------------------------

    public static void addRanking(WsContext ctx) {
        RANKING.add(ctx);
    }

    public static void removeRanking(WsContext ctx) {
        RANKING.remove(ctx);
    }

    public static Set<WsContext> rankingSessions() {
        return RANKING;
    }

    // --- referee --------------------------------------------------------------

    public static void addReferee(String allianceId, WsContext ctx) {
        REFEREE.computeIfAbsent(allianceId, k -> new CopyOnWriteArraySet<>()).add(ctx);
    }

    public static void removeReferee(String allianceId, WsContext ctx) {
        Set<WsContext> pool = REFEREE.get(allianceId);
        if (pool == null) {
            return;
        }
        pool.remove(ctx);
        // Drop the bucket when it empties, so long-lived servers don't
        // accumulate a key per historical match.
        if (pool.isEmpty()) {
            REFEREE.remove(allianceId, pool);
        }
    }

    public static Set<WsContext> refereeSessions(String allianceId) {
        Set<WsContext> pool = REFEREE.get(allianceId);
        return pool != null ? pool : Set.of();
    }

    // --- send helpers ---------------------------------------------------------

    /**
     * Serialize {@code envelope} once and push it to every session in
     * {@code targets}. Sessions that fail the write (closed sockets, I/O
     * errors) are removed from the pool &mdash; otherwise a stale session
     * would poison every subsequent broadcast.
     *
     * <p>The pool reference is passed in so we know where to evict from.
     */
    public static void fanOut(Set<WsContext> targets, Set<WsContext> evictFrom, WsEnvelope envelope) {
        if (targets.isEmpty()) {
            return;
        }
        String json = GSON.toJson(envelope);
        for (WsContext ctx : targets) {
            sendOrEvict(ctx, json, evictFrom);
        }
    }

    /** Send to a single session; remove it from the pool on failure. */
    public static void sendTo(WsContext ctx, Set<WsContext> evictFrom, WsEnvelope envelope) {
        sendOrEvict(ctx, GSON.toJson(envelope), evictFrom);
    }

    private static void sendOrEvict(WsContext ctx, String json, Set<WsContext> evictFrom) {
        try {
            if (ctx.session.isOpen()) {
                ctx.send(json);
            } else if (evictFrom != null) {
                evictFrom.remove(ctx);
            }
        } catch (Exception e) {
            ILog.w(TAG, "send failed, evicting session:", e.getMessage());
            if (evictFrom != null) {
                evictFrom.remove(ctx);
            }
        }
    }
}
