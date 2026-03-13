package org.thingai.app.api.v2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.dto.UserDto;
import org.thingai.app.scoringservice.entity.AccountRole;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.api.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthApi {

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        String username = request.get("username");
        String password = request.get("password");

        String remoteAddr = servletRequest.getRemoteAddr();
        boolean isLocalhost;
        try {
            isLocalhost = "127.0.0.1".equals(remoteAddr) ||
                    "0:0:0:0:0:0:0:1".equals(remoteAddr) ||
                    InetAddress.getLocalHost().getHostAddress().equals(remoteAddr);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        if ("local".equalsIgnoreCase(username) && isLocalhost) {
            String token = ScoringService.authHandler().generateTokenForLocalUser();
            return ResponseEntity.ok(Map.of("token", token, "message", "Local login successful."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleAuthenticate(username, password, new RequestCallback<String>() {
            @Override
            public void onSuccess(String token, String message) {
                future.complete(ResponseEntity.ok(Map.of("token", token, "message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.AUTHEN_INVALID_CREDENTIALS
                        ? HttpStatus.UNAUTHORIZED : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(ResponseEntity.status(status).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(@RequestHeader Map<String, String> requestHeaders) {
        String authHeader = requestHeaders.get("authorization");

        String token = (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) ? authHeader.substring(7)
                : null;

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authorization header is missing or malformed."));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleRefreshToken(token, new RequestCallback<String>() {
            @Override
            public void onSuccess(String refreshedToken, String message) {
                future.complete(ResponseEntity.ok(Map.of("token", refreshedToken, "message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("/local-ip")
    public ResponseEntity<Object> getLocalIp() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String localIp = localHost.getHostAddress();
            return ResponseEntity.ok(Map.of("localIp", localIp));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to retrieve local IP address."));
        }
    }

    @PostMapping("/create-account")
    public ResponseEntity<Object> createAccount(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        int role = Integer.parseInt(request.get("role"));

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleCreateAuth(username, password, role, new RequestCallback<String>() {
            @Override
            public void onSuccess(String token, String message) {
                future.complete(ResponseEntity.ok(Map.of("token", token, "message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.AUTHEN_USER_ALREADY_EXISTS
                        ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(ResponseEntity.status(status).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("/users")
    public ResponseEntity<Object> getAllUsers() {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleGetAllUsers(new RequestCallback<UserDto[]>() {
            @Override
            public void onSuccess(UserDto[] users, String message) {
                future.complete(ResponseEntity.ok(users));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @GetMapping("/accounts")
    public ResponseEntity<Object> getAllAccounts() {
        try {
            AccountRole[] accounts = ScoringService.authHandler().getAllAccounts();
            return ResponseEntity.ok(Map.of("accounts", accounts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to retrieve accounts: " + e.getMessage()));
        }
    }

    @PutMapping("/accounts/{username}")
    public ResponseEntity<Object> updateAccount(@PathVariable String username, @RequestBody Map<String, String> request) {
        String password = request.get("password");
        String roleStr = request.get("role");

        if (roleStr == null || roleStr.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Role is required"));
        }

        int role;
        try {
            role = Integer.parseInt(roleStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role format"));
        }

        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleUpdateAccount(username, password, role, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void result, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.DAO_NOT_FOUND
                        ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(ResponseEntity.status(status).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @DeleteMapping("/accounts/{username}")
    public ResponseEntity<Object> deleteAccount(@PathVariable String username) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleDeleteAccount(username, new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void result, String message) {
                future.complete(ResponseEntity.ok(Map.of("message", message)));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                HttpStatus status = errorCode == ErrorCode.DAO_NOT_FOUND
                        ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
                future.complete(ResponseEntity.status(status).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }
}
