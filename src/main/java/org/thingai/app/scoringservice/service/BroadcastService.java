package org.thingai.app.scoringservice.service;

import org.thingai.app.scoringservice.dto.BroadcastMessageDto;
import org.thingai.base.log.ILog;

/**
 * Placeholder broadcast service for the Javalin migration.
 *
 * <p>The STOMP/Spring WebSocket implementation was removed during the migration
 * to Javalin + vanilla WebSocket. This stub keeps the static
 * {@link #broadcast(String, Object, String)} signature so the existing call
 * sites ({@code MatchControl}, {@code MatchControlApi}) still compile. It only
 * logs the broadcast payload.
 *
 * <p>TODO (WS redesign): route {@code topic}/{@code messageType}/{@code message}
 * to the vanilla WebSocket session registry once the topic model is designed.
 */
public class BroadcastService {
    private static final String TAG = "BroadcastService";

    private BroadcastService() {
    }

    /**
     * Broadcast a message to a specific topic.
     *
     * @param topic       The topic to broadcast to (e.g., "/live/match").
     * @param message     The message payload.
     * @param messageType A short type discriminator for consumers.
     */
    public static void broadcast(String topic, Object message, String messageType) {
        BroadcastMessageDto payload = new BroadcastMessageDto(messageType, message);
        ILog.d(TAG, "broadcast (stub)", topic, messageType, String.valueOf(payload));
    }
}
