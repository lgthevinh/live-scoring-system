package org.thingai.app.api.ws;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.LiveBroadcastTopic;
import org.thingai.app.scoringservice.entity.RankingEntry;
import org.thingai.app.scoringservice.handler.RankingHandler;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.app.scoringservice.service.BroadcastService;
import org.thingai.base.log.ILog;

import java.util.HashMap;
import java.util.Map;

/**
 * Public, server &rarr; client only feed at {@code /ws/ranking}.
 *
 * <p>Carries only {@link WsMessageType#RANKING_UPDATE}. Kept separate from
 * {@code /ws/live} because rankings fire post-commit (low frequency, larger
 * payload) and some clients (e.g. a standalone ranking projector) only want
 * this stream.
 *
 * <p>No authentication. No inbound message handling.
 *
 * <p>On connect, sends a SNAPSHOT containing the latest known ranking. If the
 * server hasn't broadcast one since startup, the snapshot is fetched from
 * {@link RankingHandler} on the spot &mdash; which means a freshly-started
 * server can serve a ranking projector without waiting for the first commit.
 */
public final class RankingWs {

    private static final String TAG = "RankingWs";
    private static final String PATH = "/ws/ranking";

    private RankingWs() {
    }

    public static void register(Javalin app) {
        app.ws(PATH, ws -> {
            ws.onConnect(ctx -> {
                BroadcastRegistry.addRanking(ctx);
                sendSnapshot(ctx);
            });
            ws.onClose(ctx -> BroadcastRegistry.removeRanking(ctx));
            ws.onError(ctx -> {
                ILog.w(TAG, "ws error", String.valueOf(ctx.error()));
                BroadcastRegistry.removeRanking(ctx);
            });
            ws.onMessage(ctx -> ILog.d(TAG, "unexpected inbound message dropped"));
        });
    }

    private static void sendSnapshot(WsContext ctx) {
        Object cached = BroadcastService.lastFor(LiveBroadcastTopic.LIVE_DISPLAY_RANKING);
        if (cached != null) {
            BroadcastRegistry.sendTo(ctx, BroadcastRegistry.rankingSessions(),
                    WsEnvelope.now(WsMessageType.SNAPSHOT, wrap(cached)));
            return;
        }

        // No broadcast has fired yet; ask the handler directly. If no event
        // is loaded the DAO call will fail -- fall back to an empty snapshot.
        if (LocalRepository.eventDatabase() == null || ScoringService.rankingHandler() == null) {
            BroadcastRegistry.sendTo(ctx, BroadcastRegistry.rankingSessions(),
                    WsEnvelope.now(WsMessageType.SNAPSHOT, wrap(new RankingEntry[0])));
            return;
        }

        ScoringService.rankingHandler().getRankingStatus(new RequestCallback<RankingEntry[]>() {
            @Override
            public void onSuccess(RankingEntry[] ranking, String message) {
                BroadcastRegistry.sendTo(ctx, BroadcastRegistry.rankingSessions(),
                        WsEnvelope.now(WsMessageType.SNAPSHOT, wrap(ranking)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                ILog.w(TAG, "ranking snapshot failed", errorMessage);
                BroadcastRegistry.sendTo(ctx, BroadcastRegistry.rankingSessions(),
                        WsEnvelope.now(WsMessageType.SNAPSHOT, wrap(new RankingEntry[0])));
            }
        });
    }

    private static Map<String, Object> wrap(Object ranking) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("ranking", ranking);
        return snap;
    }
}
