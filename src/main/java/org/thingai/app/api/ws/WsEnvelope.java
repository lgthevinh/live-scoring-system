package org.thingai.app.api.ws;

/**
 * Wire envelope for every WebSocket frame, in either direction.
 *
 * <p>Outbound (server &rarr; client) frames use {@link #now(String, Object)} so the
 * server stamps {@code ts} with the current epoch milliseconds. Inbound frames
 * are deserialized via Gson; the {@code ts} field is ignored on inbound but
 * left in the schema so the client need only learn one shape.
 *
 * <p>Lives in {@code api.ws} (not in {@code scoringservice.dto}) because it is
 * a transport-layer concern, not a domain DTO. The legacy
 * {@code BroadcastMessageDto} is retained for callers that still construct it
 * directly, but new code should prefer this envelope.
 */
public final class WsEnvelope {

    private final String type;
    private final long ts;
    private final Object payload;

    public WsEnvelope(String type, long ts, Object payload) {
        this.type = type;
        this.ts = ts;
        this.payload = payload;
    }

    /** Convenience factory: stamp {@code ts} with the current wall clock. */
    public static WsEnvelope now(String type, Object payload) {
        return new WsEnvelope(type, System.currentTimeMillis(), payload);
    }

    public String getType() {
        return type;
    }

    public long getTs() {
        return ts;
    }

    public Object getPayload() {
        return payload;
    }
}
