package org.thingai.app.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.base.log.ILog;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Strict authentication filter for all {@code /api/*} routes.
 *
 * <p>Every request under {@code /api/} must present a valid
 * {@code Authorization: Bearer <token>} header, EXCEPT for the public
 * endpoints listed in {@link #PUBLIC_PATHS}:
 * <ul>
 *   <li>{@code POST /api/auth/login} &mdash; obtain a token</li>
 *   <li>{@code POST /api/auth/refresh} &mdash; refresh an expiring token</li>
 *   <li>{@code GET  /api/auth/local-ip} &mdash; used by the splash screen before login</li>
 * </ul>
 *
 * <p>{@code OPTIONS} pre-flight requests are always allowed (CORS).
 *
 * <p>Implementation note: {@link org.thingai.app.scoringservice.handler.AuthHandler}
 * exposes {@code handleValidateToken} via an async callback; we block on a
 * {@link CompletableFuture} here because the handler is in fact synchronous
 * under the hood (it immediately invokes {@code validateToken}).
 */
public final class AuthFilter {

    private static final String TAG = "AuthFilter";

    /** Paths that bypass the Bearer-token check. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/local-ip"
    );

    private AuthFilter() {
    }

    public static void register(Javalin app) {
        app.before("/api/*", AuthFilter::handle);
    }

    private static void handle(Context ctx) {
        // Always allow CORS preflight.
        if ("OPTIONS".equalsIgnoreCase(ctx.method().name())) {
            return;
        }

        String path = ctx.path();
        if (PUBLIC_PATHS.contains(path)) {
            return;
        }

        String token = extractBearer(ctx.header("Authorization"));
        if (token == null) {
            reject(ctx, "Missing or malformed Authorization header.");
            return;
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ScoringService.authHandler().handleValidateToken(token, new RequestCallback<String>() {
            @Override
            public void onSuccess(String responseObject, String message) {
                future.complete(Boolean.TRUE);
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(Boolean.FALSE);
            }
        });

        boolean valid;
        try {
            valid = future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reject(ctx, "Auth check interrupted.");
            return;
        } catch (ExecutionException e) {
            ILog.e(TAG, "validate failed", e.getMessage());
            reject(ctx, "Auth check failed.");
            return;
        }

        if (!valid) {
            reject(ctx, "Invalid or expired token.");
        }
    }

    private static String extractBearer(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.length() < 7) {
            return null;
        }
        String prefix = trimmed.substring(0, 7);
        if (!"bearer ".equalsIgnoreCase(prefix)) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static void reject(Context ctx, String message) {
        ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", message));
        ctx.skipRemainingHandlers();
    }

    /**
     * Utility retained from the legacy AuthApi: resolve whether the request
     * came from localhost (loopback or the server's own IP). Used by
     * {@code AuthApi#login} to permit the "local" admin shortcut.
     */
    public static boolean isLocalhost(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return true;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress().equals(remoteAddr);
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
