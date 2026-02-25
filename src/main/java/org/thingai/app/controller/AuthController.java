package org.thingai.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thingai.app.scoringservice.ScoringService;
import org.thingai.app.scoringservice.entity.config.AccountRole;
import org.thingai.app.scoringservice.entity.event.Event;
import org.thingai.app.scoringservice.handler.entityhandler.AuthHandler;
import org.thingai.base.log.ILog;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.thingai.app.controller.utils.ResponseEntityUtil.getObjectResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        String username = request.get("username");
        String password = request.get("password");

        // --- LOCAL LOGIN IMPLEMENTATION ---
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
            // Bypass normal authentication for local development.
            // This is a convenient backdoor for testing on a local machine.
            String token = ScoringService.authHandler().generateTokenForLocalUser();
            return ResponseEntity.ok(Map.of("token", token, "message", "Local login successful."));
        }
        // --- END LOCAL LOGIN IMPLEMENTATION ---

        // Standard authentication flow, now made thread-safe.
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleAuthenticate(username, password, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                future.complete(ResponseEntity.ok(Map.of("token", token, "message", successMessage)));
            }

            @Override
            public void onFailure(String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(@RequestHeader Map<String, String> requestHeaders) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();
        String authHeader = requestHeaders.get("authorization"); // Headers are converted to lowercase

        // Expecting the format "Bearer <token>"
        String token = (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) ? authHeader.substring(7) : null;

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authorization header is missing or malformed."));
        }

        ScoringService.authHandler().handleRefreshToken(token, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String refreshedToken, String successMessage) {
                future.complete(ResponseEntity.ok(Map.of("token", refreshedToken, "message", successMessage)));
            }

            @Override
            public void onFailure(String errorMessage) {
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Unable to retrieve local IP address."));
        }
    }

    @PostMapping("/create-account")
    public ResponseEntity<Object> createAccount(@RequestBody Map<String, String> request) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        String username = request.get("username");
        String password = request.get("password");
        int role = Integer.parseInt(request.get("role"));

        ScoringService.authHandler().handleCreateAuth(username, password, role, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                future.complete(ResponseEntity.ok(Map.of("token", token, "message", successMessage)));
            }

            @Override
            public void onFailure(String errorMessage) {
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
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

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

        ScoringService.authHandler().handleUpdateAccount(username, password, role, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                future.complete(ResponseEntity.ok(Map.of("message", successMessage)));
            }

            @Override
            public void onFailure(String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }

    @DeleteMapping("/accounts/{username}")
    public ResponseEntity<Object> deleteAccount(@PathVariable String username) {
        CompletableFuture<ResponseEntity<Object>> future = new CompletableFuture<>();

        ScoringService.authHandler().handleDeleteAccount(username, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                future.complete(ResponseEntity.ok(Map.of("message", successMessage)));
            }

            @Override
            public void onFailure(String errorMessage) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", errorMessage)));
            }
        });

        return getObjectResponse(future);
    }
}
