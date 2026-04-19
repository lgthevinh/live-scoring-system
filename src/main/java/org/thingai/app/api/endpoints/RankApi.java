package org.thingai.app.api.endpoints;

import io.javalin.Javalin;

/**
 * Placeholder for ranking-related endpoints. The legacy Spring version was
 * an empty controller with base path {@code /api/rank}; ranking data is
 * currently exposed through {@code /api/scores} and {@code /api/match-control}.
 *
 * <p>Kept as a registration seam so future routes can be added without
 * touching {@link org.thingai.app.api.ApiServer}.
 */
public final class RankApi {

    private RankApi() {
    }

    public static void register(Javalin app) {
        // No routes yet.
    }
}
