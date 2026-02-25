package org.thingai.app.scoringservice.handler.entityhandler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.dto.UserDto;
import org.thingai.app.scoringservice.entity.config.AccountRole;
import org.thingai.base.dao.Dao;
import org.thingai.app.scoringservice.entity.config.AuthData;
import org.thingai.base.log.ILog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static org.thingai.app.scoringservice.util.ByteUtil.bytesToHex;
import static org.thingai.app.scoringservice.util.ByteUtil.hexToBytes;

public class AuthHandler {
    private static final String SECRET_KEY = "secret_key";
    private static final int TOKEN_EXPIRATION_TIME = 3600 * 1000 * 24; // 1 day in milliseconds

    private Dao dao;

    public AuthHandler(Dao dao) {
        this.dao = dao;
    }

    public interface AuthHandlerCallback {
        void onSuccess(String token, String successMessage);

        void onFailure(String errorMessage);
    }

    public void handleAuthenticate(String username, String password, AuthHandlerCallback callback) {
        try {
            AuthData[] authDataList = dao.query(AuthData.class, new String[] { "username" }, new String[] { username });

            if (authDataList.length == 0) {
                callback.onFailure("Authentication failed: User not found.");
                return;
            }

            AuthData authData = authDataList[0];

            // Hash the provided password with the stored salt
            byte[] salt = hexToBytes(authData.getSalt());
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));

            // Compare the hashed password with the stored password
            if (authData.getPassword().equals(bytesToHex(hashedPassword))) {
                String token = generateToken(username);
                callback.onSuccess(token, "Authentication successful.");
            } else {
                callback.onFailure("Authentication failed: Incorrect password.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure("Authentication failed: " + e.getMessage());
        }
    }

    public void handleCreateAuth(String username, String password, int role, AuthHandlerCallback callback) {
        // Check if the user already exists
        AuthData[] existingUsers = dao.query(AuthData.class, new String[] { "username" }, new String[] { username });
        if (!(existingUsers.length == 0)) {
            callback.onFailure("User already exists.");
            return;
        }

        // Hash password with salt
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md.update(salt);
        byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));

        // Create AuthData object
        AuthData authData = new AuthData();
        authData.setUsername(username);
        authData.setPassword(bytesToHex(hashedPassword));
        authData.setSalt(bytesToHex(salt));

        // Create AccountRole object
        AccountRole accountRole = new AccountRole();
        accountRole.setUsername(username);
        accountRole.setRole(role);

        try {
            dao.insertOrUpdate(authData);
            dao.insertOrUpdate(accountRole);
            String token = generateToken(username);
            callback.onSuccess(token, "Authentication created successfully.");
        } catch (Exception e) {
            callback.onFailure("Failed to create authentication: " + e.getMessage());
        }
    }

    public void handleValidateToken(String token, AuthHandlerCallback callback) {
        if (validateToken(token)) {
            callback.onSuccess(token, "Token is valid.");
        } else {
            callback.onFailure("Invalid or expired token.");
        }
    }

    public void handleRefreshToken(String token, AuthHandlerCallback callback) {
        if (validateToken(token)) {
            String[] parts = token.split(":");
            String username = parts[0];
            String newToken = generateToken(username);
            callback.onSuccess(newToken, "Token refreshed successfully.");
        } else {
            callback.onFailure("Invalid or expired token.");
        }
    }

    public String generateTokenForLocalUser() {
        return generateToken("local");
    }

    private String generateToken(String username) {
        long timestamp = System.currentTimeMillis();
        String tokenData = username + ":" + timestamp + ":" + SECRET_KEY;
        // encode the token data using SHA-256
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
    }

    private boolean validateToken(String token) {
        try {
            // Decode the token
            Base64.Decoder decoder = Base64.getDecoder();
            String decodedToken = new String(decoder.decode(token), StandardCharsets.UTF_8);

            String[] parts = decodedToken.split(":");

            if (parts.length != 3) {
                return false;
            }
            // String username = parts[0]; // Not used in validation, but could be used for
            // logging or further checks
            long timestamp = Long.parseLong(parts[1]);
            String secretKey = parts[2];

            // Check if the token is expired
            if (System.currentTimeMillis() - timestamp > TOKEN_EXPIRATION_TIME) {
                return false;
            }

            // Validate the secret key
            return SECRET_KEY.equals(secretKey);
        } catch (Exception e) {
            return false;
        }
    }

    public void handleGetAllUsers(RequestCallback<UserDto[]> callback) {
        try {
            AuthData[] authDataList = dao.query(AuthData.class, new String[] {}, new String[] {});

            UserDto[] users = new UserDto[authDataList.length];
            for (int i = 0; i < authDataList.length; i++) {
                String username = authDataList[i].getUsername();
                int role = 0;

                AccountRole[] roles = dao.query(AccountRole.class, new String[] { "username" },
                        new String[] { username });
                if (roles.length > 0) {
                    role = roles[0].getRole();
                }

                users[i] = new UserDto(username, role);
            }

            callback.onSuccess(users, "Users retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(500, "Failed to retrieve users: " + e.getMessage());
        }
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }
}
