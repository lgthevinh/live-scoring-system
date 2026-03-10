package org.thingai.app.scoringservice.handler;

import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.define.ErrorCode;
import org.thingai.app.scoringservice.dto.UserDto;
import org.thingai.app.scoringservice.entity.AccountRole;
import org.thingai.app.scoringservice.entity.AuthData;
import org.thingai.app.scoringservice.repository.LocalRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.thingai.app.scoringservice.util.ByteUtil.bytesToHex;
import static org.thingai.app.scoringservice.util.ByteUtil.hexToBytes;

public class AuthHandler {
    private static final String SECRET_KEY = "secret_key";
    private static final int TOKEN_EXPIRATION_TIME = 3600 * 1000 * 24; // 1 day in milliseconds

    public AuthHandler() {
        // Default constructor - initialization handled by repository
    }

    public void handleAuthenticate(String username, String password, RequestCallback<String> callback) {
        try {
            AuthData authData = LocalRepository.authDao().getAuthDataById(username);

            if (authData == null) {
                callback.onFailure(ErrorCode.AUTHEN_INVALID_CREDENTIALS, "Authentication failed: Invalid credentials.");
                return;
            }

            byte[] salt = hexToBytes(authData.getSalt());
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));

            if (authData.getPassword().equals(bytesToHex(hashedPassword))) {
                String token = generateToken(username);
                callback.onSuccess(token, "Authentication successful.");
            } else {
                callback.onFailure(ErrorCode.AUTHEN_INVALID_CREDENTIALS, "Authentication failed: Invalid credentials.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(ErrorCode.AUTHEN_UNAUTHORIZED, "Authentication failed: " + e.getMessage());
        }
    }

    public void handleCreateAuth(String username, String password, int role, RequestCallback<String> callback) {
        try {
            if (LocalRepository.authDao().authDataExists(username)) {
                callback.onFailure(ErrorCode.AUTHEN_USER_ALREADY_EXISTS, "User already exists.");
                return;
            }

            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));

            AuthData authData = new AuthData();
            authData.setUsername(username);
            authData.setPassword(bytesToHex(hashedPassword));
            authData.setSalt(bytesToHex(salt));

            AccountRole accountRole = new AccountRole();
            accountRole.setUsername(username);
            accountRole.setRole(role);

            LocalRepository.authDao().insertAuthData(authData);
            LocalRepository.authDao().insertAccountRole(accountRole);

            String token = generateToken(username);
            callback.onSuccess(token, "Account created successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_CREATE_FAILED, "Failed to create account: " + e.getMessage());
        }
    }

    public void handleValidateToken(String token, RequestCallback<String> callback) {
        if (validateToken(token)) {
            callback.onSuccess(token, "Token is valid.");
        } else {
            callback.onFailure(ErrorCode.AUTHEN_INVALID_TOKEN, "Invalid or expired token.");
        }
    }

    public void handleRefreshToken(String token, RequestCallback<String> callback) {
        if (validateToken(token)) {
            String[] parts = token.split(":");
            String username = parts[0];
            String newToken = generateToken(username);
            callback.onSuccess(newToken, "Token refreshed successfully.");
        } else {
            callback.onFailure(ErrorCode.AUTHEN_INVALID_TOKEN, "Invalid or expired token.");
        }
    }

    public String generateTokenForLocalUser() {
        return generateToken("local");
    }

    private String generateToken(String username) {
        long timestamp = System.currentTimeMillis();
        String tokenData = username + ":" + timestamp + ":" + SECRET_KEY;
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
    }

    public void handleGetAllUsers(RequestCallback<UserDto[]> callback) {
        try {
            AuthData[] authDataList = LocalRepository.authDao().listAuthData();

            UserDto[] users = new UserDto[authDataList.length];
            for (int i = 0; i < authDataList.length; i++) {
                String username = authDataList[i].getUsername();
                int role = 0;

                AccountRole accountRole = LocalRepository.authDao().getAccountRoleById(username);
                if (accountRole != null) {
                    role = accountRole.getRole();
                }

                users[i] = new UserDto(username, role);
            }

            callback.onSuccess(users, "Users retrieved successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_RETRIEVE_FAILED, "Failed to retrieve users: " + e.getMessage());
        }
    }

    public void handleUpdateAccount(String username, String newPassword, int newRole, RequestCallback<Void> callback) {
        try {
            AuthData existingAuth = LocalRepository.authDao().getAuthDataById(username);
            if (existingAuth == null) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "User not found.");
                return;
            }

            if (newPassword != null && !newPassword.isEmpty()) {
                SecureRandom random = new SecureRandom();
                byte[] salt = new byte[16];
                random.nextBytes(salt);

                MessageDigest md = MessageDigest.getInstance("SHA-512");
                md.update(salt);
                byte[] hashedPassword = md.digest(newPassword.getBytes(StandardCharsets.UTF_8));

                existingAuth.setPassword(bytesToHex(hashedPassword));
                existingAuth.setSalt(bytesToHex(salt));
            }

            AccountRole accountRole = new AccountRole();
            accountRole.setUsername(username);
            accountRole.setRole(newRole);

            LocalRepository.authDao().updateAuthData(existingAuth);
            LocalRepository.authDao().updateAccountRole(accountRole);

            callback.onSuccess(null, "Account updated successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_UPDATE_FAILED, "Failed to update account: " + e.getMessage());
        }
    }

    public void handleDeleteAccount(String username, RequestCallback<Void> callback) {
        try {
            if (!LocalRepository.authDao().authDataExists(username)) {
                callback.onFailure(ErrorCode.DAO_NOT_FOUND, "User not found: " + username);
                return;
            }

            LocalRepository.authDao().deleteAccountRole(username);
            LocalRepository.authDao().deleteAuthData(username);

            callback.onSuccess(null, "Account '" + username + "' deleted successfully.");
        } catch (Exception e) {
            callback.onFailure(ErrorCode.DAO_DELETE_FAILED, "Failed to delete account: " + e.getMessage());
        }
    }

    private boolean validateToken(String token) {
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            String decodedToken = new String(decoder.decode(token), StandardCharsets.UTF_8);

            String[] parts = decodedToken.split(":");

            if (parts.length != 3) {
                return false;
            }
            long timestamp = Long.parseLong(parts[1]);
            String secretKey = parts[2];

            if (System.currentTimeMillis() - timestamp > TOKEN_EXPIRATION_TIME) {
                return false;
            }

            return SECRET_KEY.equals(secretKey);
        } catch (Exception e) {
            return false;
        }
    }

    public AccountRole[] getAllAccounts() {
        try {
            AccountRole[] accountRoles = LocalRepository.authDao().listAccountRoles();

            if (accountRoles.length == 0) {
                AuthData[] authDataList = LocalRepository.authDao().listAuthData();
                accountRoles = new AccountRole[authDataList.length];
                for (int i = 0; i < authDataList.length; i++) {
                    AccountRole role = new AccountRole();
                    role.setUsername(authDataList[i].getUsername());
                    role.setRole(0);
                    accountRoles[i] = role;
                }
            }

            return accountRoles;
        } catch (Exception e) {
            return new AccountRole[0];
        }
    }

    public AuthData getAuthDataByUsername(String username) {
        try {
            return LocalRepository.authDao().getAuthDataById(username);
        } catch (Exception e) {
            return null;
        }
    }
}