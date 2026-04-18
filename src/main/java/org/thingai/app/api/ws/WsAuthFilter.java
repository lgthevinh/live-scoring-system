package org.thingai.app.api.ws;

import io.javalin.websocket.WsConnectContext;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.AccountRole;
import org.thingai.base.log.ILog;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Authorization helper for WebSocket endpoints that require a logged-in user.
 *
 * <p>Why this isn't a Javalin {@code before}/{@code beforeUpgrade} HTTP
 * handler: when an HTTP handler aborts the WS upgrade, the browser only sees
 * a generic "1006 abnormal closure" close code &mdash; the custom 4xxx code
 * we want to surface is lost. The reliable way to send a 4401/4403 frame is
 * to let the upgrade succeed and close the session inside {@code onConnect}
 * via {@link WsConnectContext#closeSession(int, String)}.
 *
 * <p>Usage from an endpoint:
 * <pre>
 *   ws.onConnect(ctx -&gt; {
 *       AuthResult auth = WsAuthFilter.authorize(ctx, AccountRole.REFEREE);
 *       if (!auth.ok()) return;            // closeSession already called
 *       // ... register and send snapshot
 *   });
 * </pre>
 *
 * <p>Token transport: query parameter {@code ?token=...}. Browsers can't add
 * an {@code Authorization} header to the WS upgrade request, so the token
 * rides in the URL. This is acceptable for a LAN deployment with short-lived
 * tokens.
 *
 * <p>Role check: callers pass a numeric ceiling. A user passes if
 * {@code accountRole.role <= ceiling}. The role enum
 * ({@link AccountRole#EVENT_ADMIN}=1, ..., {@link AccountRole#REFEREE}=21)
 * uses lower numbers for higher privilege, so passing
 * {@code AccountRole.REFEREE} as the ceiling lets referees, head referees,
 * scorekeepers, and event admins all in.
 */
public final class WsAuthFilter {

    private static final String TAG = "WsAuthFilter";

    private WsAuthFilter() {
    }

    /**
     * Validate the {@code token} query param and (if {@code requiredRole} is
     * non-negative) the user's role. On failure this method calls
     * {@link WsConnectContext#closeSession(int, String)} with the appropriate
     * 4xxx code and returns a failed {@link AuthResult}.
     *
     * @param ctx          the connect context
     * @param requiredRole the role ceiling (one of {@link AccountRole}); pass
     *                     a negative value to skip the role check
     * @return result carrying {@code username}/{@code role} on success
     */
    public static AuthResult authorize(WsConnectContext ctx, int requiredRole) {
        String token = ctx.queryParam("token");
        if (token == null || token.isBlank()) {
            close(ctx, WsCloseCode.UNAUTHORIZED, "Missing token");
            return AuthResult.fail();
        }

        if (!validateTokenSync(token)) {
            close(ctx, WsCloseCode.UNAUTHORIZED, "Invalid or expired token");
            return AuthResult.fail();
        }

        String username = decodeUsername(token);
        if (username == null) {
            close(ctx, WsCloseCode.UNAUTHORIZED, "Malformed token");
            return AuthResult.fail();
        }

        if (requiredRole < 0) {
            return AuthResult.ok(username, -1);
        }

        int role = lookupRole(username);
        if (role < 0) {
            close(ctx, WsCloseCode.FORBIDDEN, "User has no role");
            return AuthResult.fail();
        }
        // Lower number == higher privilege in this enum.
        if (role > requiredRole) {
            close(ctx, WsCloseCode.FORBIDDEN, "Insufficient role");
            return AuthResult.fail();
        }

        return AuthResult.ok(username, role);
    }

    /**
     * Bridge {@link org.thingai.app.scoringservice.handler.AuthHandler#handleValidateToken}
     * to a synchronous boolean. The underlying handler invokes the callback
     * inline, so this never blocks in practice.
     */
    private static boolean validateTokenSync(String token) {
        boolean[] ok = {false};
        try {
            ScoringService.authHandler().handleValidateToken(token, new RequestCallback<String>() {
                @Override
                public void onSuccess(String responseObject, String message) {
                    ok[0] = true;
                }

                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    ok[0] = false;
                }
            });
        } catch (Exception e) {
            ILog.e(TAG, "validate threw", e.getMessage());
            return false;
        }
        return ok[0];
    }

    /**
     * Tokens are produced by {@code AuthHandler.generateToken} as
     * {@code Base64( username:timestamp:secret )}. We only need the first
     * field; full validation already happened above.
     */
    private static String decodeUsername(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon > 0 ? decoded.substring(0, colon) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int lookupRole(String username) {
        try {
            // AuthHandler doesn't expose a getRoleByUsername, so we query the
            // DAO directly. If the user has no AccountRole row, we treat that
            // as role -1 (denied). Checking auth-data existence separately
            // would be redundant -- a role row can't exist without an auth row.
            org.thingai.app.scoringservice.entity.AccountRole accountRole =
                    org.thingai.app.scoringservice.repository.LocalRepository.authDao()
                            .getAccountRoleById(username);
            return accountRole == null ? -1 : accountRole.getRole();
        } catch (Exception e) {
            ILog.w(TAG, "role lookup failed", username, e.getMessage());
            return -1;
        }
    }

    private static void close(WsConnectContext ctx, int code, String reason) {
        try {
            ctx.closeSession(code, reason);
        } catch (Exception e) {
            ILog.w(TAG, "closeSession failed", e.getMessage());
        }
    }

    /** Result of an authorization check. Immutable. */
    public static final class AuthResult {
        private final boolean ok;
        private final String username;
        private final int role;

        private AuthResult(boolean ok, String username, int role) {
            this.ok = ok;
            this.username = username;
            this.role = role;
        }

        static AuthResult ok(String username, int role) {
            return new AuthResult(true, username, role);
        }

        static AuthResult fail() {
            return new AuthResult(false, null, -1);
        }

        public boolean ok() {
            return ok;
        }

        public String username() {
            return username;
        }

        public int role() {
            return role;
        }
    }
}
