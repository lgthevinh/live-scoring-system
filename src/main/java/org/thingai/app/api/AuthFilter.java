package org.thingai.app.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.entity.AccountRole;
import org.thingai.app.scoringservice.repository.LocalRepository;
import org.thingai.base.log.ILog;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
 * <p>On success the filter stashes the caller's {@code username} and
 * numeric {@code role} on the request via {@link Context#attribute}, keys
 * {@link #ATTR_USERNAME} and {@link #ATTR_ROLE}. Downstream handlers and
 * {@link #requireRole} read them back without hitting the DAO twice.
 *
 * <p>Role gating uses {@link #requireRole}. The role enum (see
 * {@code org.thingai.app.scoringservice.define.AccountRole}) maps lower
 * numbers to higher privilege &mdash; so a user with role {@code role}
 * is accepted iff {@code role <= minRole}.
 */
public final class AuthFilter {

    private static final String TAG = "AuthFilter";

    /** Context attribute: username of the authenticated caller. */
    public static final String ATTR_USERNAME = "authUsername";
    /** Context attribute: numeric role of the authenticated caller, or -1 if unknown. */
    public static final String ATTR_ROLE = "authRole";

    /** Paths that bypass the Bearer-token check. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/local-ip"
    );

    /**
     * Prefixes that bypass the Bearer-token check for READ operations
     * (GET/HEAD only). These power the schedule/ranking/display pages that
     * spectators and unattended field displays need to reach without
     * logging in. A matching write request still requires a token AND
     * whatever role gate {@link #requireRole} has registered for that path.
     */
    private static final Set<String> PUBLIC_READ_PREFIXES = Set.of(
            "/api/match/",           // GET schedule, list, details
            "/api/rank/",            // GET ranking status
            "/api/match-control/state", // GET current loaded/active match for displays
            "/api/scores/",          // GET committed scores (read-only lookups)
            "/api/event/"            // GET current event + public event metadata
    );

    private AuthFilter() {
    }

    public static void register(Javalin app) {
        app.before("/api/*", AuthFilter::handle);
    }

    /**
     * Register a role-gate on the given path (supports Javalin glob syntax,
     * e.g. {@code "/api/event/*"}). Must be called AFTER {@link #register}
     * so the token filter runs first and populates {@link #ATTR_ROLE}.
     *
     * <p>The check is <i>read-or-deny</i>: if for any reason the role
     * attribute wasn't set (auth bypass path, filter ordering bug), the
     * request is rejected. A 403 never leaks past this method by accident.
     */
    public static void requireRole(Javalin app, String pathGlob, int minRole) {
        app.before(pathGlob, ctx -> {
            if ("OPTIONS".equalsIgnoreCase(ctx.method().name())) return;
            Integer role = ctx.attribute(ATTR_ROLE);
            if (role == null || role < 0 || role > minRole) {
                ctx.status(HttpStatus.FORBIDDEN)
                        .json(Map.of("error", "Insufficient role."));
                ctx.skipRemainingHandlers();
            }
        });
    }

    private static void handle(Context ctx) {
        if ("OPTIONS".equalsIgnoreCase(ctx.method().name())) {
            return;
        }

        String path = ctx.path();
        if (PUBLIC_PATHS.contains(path)) {
            return;
        }

        // Public read-only surfaces (schedule / rankings / live display).
        // GET and HEAD only; POST/PUT/DELETE on the same prefix still
        // require a bearer token and whatever role gate is registered.
        // Role gates registered in ApiServer run after this filter, so
        // mutations stay protected even when their path shares a prefix
        // with a public read endpoint.
        String method = ctx.method().name();
        if (("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                && isPublicReadPath(path)) {
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
            return;
        }

        // Token is valid. Decode the caller and stash identity + role for
        // downstream handlers and requireRole(). Missing role is stored as
        // -1 so requireRole can deny uniformly.
        String username = decodeUsername(token);
        ctx.attribute(ATTR_USERNAME, username);
        ctx.attribute(ATTR_ROLE, lookupRole(username));
    }

    private static boolean isPublicReadPath(String path) {
        if (path == null) return false;
        // Exact: /api/match-control/state is public-read; everything else
        // under /api/match-control/ is a control action and needs auth.
        if ("/api/match-control/state".equals(path)) return true;
        for (String prefix : PUBLIC_READ_PREFIXES) {
            if (prefix.endsWith("/") && path.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Tokens are {@code Base64( username:timestamp:secret )}. Full
     * validation already ran; we only need the username here.
     */
    private static String decodeUsername(String token) {
        if (token == null) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon > 0 ? decoded.substring(0, colon) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int lookupRole(String username) {
        if (username == null) return -1;
        // The built-in "local" loopback admin (see AuthApi#login) has no
        // account_role row. Treat it as EVENT_ADMIN so the machine hosting
        // the server is always usable for setup / recovery.
        if ("local".equalsIgnoreCase(username)) {
            return org.thingai.app.scoringservice.define.AccountRole.EVENT_ADMIN;
        }
        try {
            AccountRole ar = LocalRepository.authDao().getAccountRoleById(username);
            return ar == null ? -1 : ar.getRole();
        } catch (Exception e) {
            ILog.w(TAG, "role lookup failed", username, e.getMessage());
            return -1;
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
