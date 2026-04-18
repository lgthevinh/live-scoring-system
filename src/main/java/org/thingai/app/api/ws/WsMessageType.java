package org.thingai.app.api.ws;

/**
 * Catalogue of WebSocket frame {@code type} discriminators.
 *
 * <p>These values appear in the {@link WsEnvelope#getType()} field of every
 * frame. Splitting them out in one file keeps the wire vocabulary discoverable
 * &mdash; the client mirrors this catalogue in TypeScript.
 *
 * <p>Topology recap (kept here because the names below only make sense in
 * context):
 * <ul>
 *   <li>{@code /ws/live} carries {@link #SNAPSHOT}, {@link #MATCH_STATE},
 *       {@link #SCORE_UPDATE}, {@link #DISPLAY_CONTROL}.</li>
 *   <li>{@code /ws/ranking} carries {@link #SNAPSHOT} and {@link #RANKING_UPDATE}.</li>
 *   <li>{@code /ws/referee/{matchId}/{alliance}} carries {@link #SNAPSHOT},
 *       {@link #MATCH_STATE}, {@link #SCORE_ACK} (server &rarr; client) and
 *       {@link #SCORE_DRAFT} (client &rarr; server).</li>
 * </ul>
 */
public final class WsMessageType {

    private WsMessageType() {
    }

    // --- common ---------------------------------------------------------------

    /** First frame after a successful connect; payload is endpoint-specific. */
    public static final String SNAPSHOT = "SNAPSHOT";

    // --- /ws/live -------------------------------------------------------------

    /** Match lifecycle update (load / activate / start / abort / commit / timer). */
    public static final String MATCH_STATE = "MATCH_STATE";

    /** Score override committed for a finished match. */
    public static final String SCORE_UPDATE = "SCORE_UPDATE";

    /** Field-display command (preview / show match / show result). */
    public static final String DISPLAY_CONTROL = "DISPLAY_CONTROL";

    // --- /ws/ranking ----------------------------------------------------------

    /** Ranking snapshot pushed after a match commit. */
    public static final String RANKING_UPDATE = "RANKING_UPDATE";

    // --- /ws/referee ----------------------------------------------------------

    /** Inbound: referee tablet pushes an in-progress score draft. */
    public static final String SCORE_DRAFT = "SCORE_DRAFT";

    /** Outbound: server confirms a draft was accepted into ON_REVIEW state. */
    public static final String SCORE_ACK = "SCORE_ACK";
}
