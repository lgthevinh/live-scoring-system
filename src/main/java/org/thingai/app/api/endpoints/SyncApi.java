package org.thingai.app.api.endpoints;

import io.javalin.Javalin;

/**
 * Placeholder for sync-related endpoints (e.g. cross-site replication). The
 * legacy Spring version was an empty controller with base path
 * {@code /api/sync}. Kept as a registration seam for future work.
 */
public final class SyncApi {

    private SyncApi() {
    }

    public static void register(Javalin app) {
        // No routes yet.
    }
}
