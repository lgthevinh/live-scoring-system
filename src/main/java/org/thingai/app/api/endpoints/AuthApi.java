package org.thingai.app.api.endpoints;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.thingai.app.api.AuthFilter;
import org.thingai.app.api.utils.ResponseEntityUtil.PendingResponse;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.dto.UserDto;
import org.thingai.app.scoringservice.entity.AccountRole;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.badRequest;
import static org.thingai.app.api.utils.ResponseEntityUtil.writeFuture;

/**
 * REST surface for authentication and account management
 * (base path {@code /api/auth}).
 *
 * <p>The three public endpoints ({@code /login}, {@code /refresh},
 * {@code /local-ip}) bypass {@link AuthFilter}; every other route requires
 * a valid Bearer token.
 */
public final class AuthApi {

    private AuthApi() {
    }

    public static void register(Javalin app) {
        app.post("/api/auth/login", AuthApi::login);
        app.post("/api/auth/refresh", AuthApi::refreshToken);
        app.get("/api/auth/local-ip", AuthApi::getLocalIp);
        app.post("/api/auth/create-account", AuthApi::createAccount);
        app.get("/api/auth/users", AuthApi::getAllUsers);
        app.get("/api/auth/accounts", AuthApi::getAllAccounts);
        app.put("/api/auth/accounts/{username}", AuthApi::updateAccount);
        app.delete("/api/auth/accounts/{username}", AuthApi::deleteAccount);
    }

    private static void login(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> request = ctx.bodyAsClass(Map.class);
        String username = request.get("username");
        String password = request.get("password");

        // Localhost shortcut: allow the single built-in "local" admin without a password
        // when the request actually originates from the host machine.
        if ("local".equalsIgnoreCase(username) && AuthFilter.isLocalhost(ctx.ip())) {
            String token = ScoringService.authHandler().generateTokenForLocalUser();
            ctx.json(Map.of("token", token, "message", "Local login successful."));
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.authHandler().handleAuthenticate(username, password, new RequestCallback<String>() {
            @Override
            public void onSuccess(String token, String message) {
                future.complete(PendingResponse.ok(Map.of("token", token, "message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.AUTHEN_INVALID_CREDENTIALS
                        ? HttpStatus.UNAUTHORIZED : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(PendingResponse.status(status, Map.of("error", errorMessage)));
            }
        });
        writeFuture(ctx, future);
    }

    private static void refreshToken(Context ctx) {
        String authHeader = ctx.header("Authorization");
        String token = (authHeader != null && authHeader.toLowerCase().startsWith("bearer "))
                ? authHeader.substring(7) : null;

        if (token == null) {
            ctx.status(HttpStatus.UNAUTHORIZED)
                    .json(Map.of("error", "Authorization header is missing or malformed."));
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.authHandler().handleRefreshToken(token, new RequestCallback<String>() {
            @Override
            public void onSuccess(String refreshedToken, String message) {
                future.complete(PendingResponse.ok(Map.of("token", refreshedToken, "message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(PendingResponse.status(HttpStatus.UNAUTHORIZED, Map.of("error", errorMessage)));
            }
        });
        writeFuture(ctx, future);
    }

    private static void getLocalIp(Context ctx) {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            ctx.json(Map.of("localIp", localIp));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(Map.of("error", "Unable to retrieve local IP address."));
        }
    }

    private static void createAccount(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> request = ctx.bodyAsClass(Map.class);
        String username = request.get("username");
        String password = request.get("password");
        int role;
        try {
            role = Integer.parseInt(request.get("role"));
        } catch (NumberFormatException e) {
            badRequest(ctx, "Invalid role format.");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.authHandler().handleCreateAuth(username, password, role, new RequestCallback<String>() {
            @Override
            public void onSuccess(String token, String message) {
                future.complete(PendingResponse.ok(Map.of("token", token, "message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.AUTHEN_USER_ALREADY_EXISTS
                        ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(PendingResponse.status(status, Map.of("error", errorMessage)));
            }
        });
        writeFuture(ctx, future);
    }

    private static void getAllUsers(Context ctx) {
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.authHandler().handleGetAllUsers(new RequestCallback<UserDto[]>() {
            @Override
            public void onSuccess(UserDto[] users, String message) {
                future.complete(PendingResponse.ok(users));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(PendingResponse.status(HttpStatus.INTERNAL_SERVER_ERROR,
                        Map.of("error", errorMessage)));
            }
        });
        writeFuture(ctx, future);
    }

    private static void getAllAccounts(Context ctx) {
        try {
            AccountRole[] accounts = ScoringService.authHandler().getAllAccounts();
            ctx.json(Map.of("accounts", accounts));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(Map.of("error", "Failed to retrieve accounts: " + e.getMessage()));
        }
    }

    private static void updateAccount(Context ctx) {
        String username = ctx.pathParam("username");
        @SuppressWarnings("unchecked")
        Map<String, String> request = ctx.bodyAsClass(Map.class);
        String password = request.get("password");
        String roleStr = request.get("role");

        if (roleStr == null || roleStr.isEmpty()) {
            badRequest(ctx, "Role is required");
            return;
        }

        int role;
        try {
            role = Integer.parseInt(roleStr);
        } catch (NumberFormatException e) {
            badRequest(ctx, "Invalid role format");
            return;
        }

        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.authHandler().handleUpdateAccount(username, password, role, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void result, String message) {
                future.complete(PendingResponse.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.DAO_NOT_FOUND
                        ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(PendingResponse.status(status, Map.of("error", errorMessage)));
            }
        });
        writeFuture(ctx, future);
    }

    private static void deleteAccount(Context ctx) {
        String username = ctx.pathParam("username");
        CompletableFuture<PendingResponse> future = new CompletableFuture<>();
        ScoringService.authHandler().handleDeleteAccount(username, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void result, String message) {
                future.complete(PendingResponse.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.DAO_NOT_FOUND
                        ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(PendingResponse.status(status, Map.of("error", errorMessage)));
            }
        });
        writeFuture(ctx, future);
    }

    // Keep a reference so IntelliJ won't flag this import as unused.
}
