import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thingai.app.scoringservice.callback.RequestCallback;
import org.thingai.app.scoringservice.dto.UserDto;
import org.thingai.base.dao.Dao;
import org.thingai.base.dao.DaoSqlite;
import org.thingai.app.scoringservice.entity.config.AccountRole;
import org.thingai.app.scoringservice.entity.config.AuthData;
import org.thingai.app.scoringservice.handler.entityhandler.AuthHandler;

public class TestAuthHandler {
    private static AuthHandler authHandler;

    @BeforeAll
    public static void setup() {
        // Set up the DAO factory with SQLite configuration
        String url = "src/test/resources/test.db";
        Dao dao = new DaoSqlite(url);
        dao.initDao(new Class[] {
                AuthData.class,
                AccountRole.class
        }); // Ensure the DAO is ready for use
        authHandler = new AuthHandler(dao);
    }

    @Test
    public void testHandleCreateAuth() {
        String username = "newUser";
        String password = "newPassword";

        authHandler.handleCreateAuth(username, password, 1, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                System.out.println("User created successfully: " + successMessage);
                System.out.println("Token: " + token);
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println("User creation failed: " + errorMessage);
            }
        });
    }

    @Test
    public void testHandleAuthenticate() {
        String username = "newUser";
        String password = "newPassword";

        authHandler.handleAuthenticate(username, password, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                System.out.println("Authentication successful: " + successMessage);
                System.out.println("Token: " + token);
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println(errorMessage);
            }
        });
    }

    @Test
    public void testHandleAuthenticateWithWrongPassword() {
        String username = "newUser";
        String password = "wrongPassword";

        authHandler.handleAuthenticate(username, password, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                System.out.println("Authentication should not succeed with wrong password.");
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println("Expected failure: " + errorMessage);
            }
        });
    }

    @Test
    public void testHandleValidateToken() {
        String username = "newUser";
        String password = "newPassword";

        final String[] newToken = new String[1];

        authHandler.handleAuthenticate(username, password, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                System.out.println(successMessage);
                System.out.println("Token: " + token);
                newToken[0] = token; // Store the token for validation
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println(errorMessage);
            }
        });

        // Now validate the token
        authHandler.handleValidateToken(newToken[0], new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String validToken, String successMessage) {
                System.out.println("Token validation successful: " + successMessage);
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println("Token validation failed: " + errorMessage);
            }
        });
    }

    @Test
    public void testHandleValidateTokenWithInvalidToken() {
        String invalidToken = "invalid:token";
        authHandler.handleValidateToken(invalidToken, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String validToken, String successMessage) {
                System.out.println("This should not succeed with an invalid token.");
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println("Expected failure: " + errorMessage);
            }
        });
    }

    @Test
    public void testRefreshToken() {
        String username = "newUser";
        String password = "newPassword";

        final String[] newToken = new String[1];

        authHandler.handleAuthenticate(username, password, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                System.out.println(successMessage);
                System.out.println("Token: " + token);
                newToken[0] = token; // Store the token for refreshing
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println(errorMessage);
            }
        });

        // Now refresh the token
        authHandler.handleRefreshToken(newToken[0], new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String refreshedToken, String successMessage) {
                System.out.println("Token refresh successful: " + successMessage);
                System.out.println("Refreshed Token: " + refreshedToken);
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println("Token refresh failed: " + errorMessage);
            }
        });

        // Validate the refreshed token
        authHandler.handleValidateToken(newToken[0], new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String validToken, String successMessage) {
                System.out.println("Refreshed token validation successful: " + successMessage);
            }

            @Override
            public void onFailure(String errorMessage) {
                System.err.println("Refreshed token validation failed: " + errorMessage);
            }
        });
    }

    @Test
    public void testGetAllUsers() {
        // First, ensure at least one user exists
        authHandler.handleCreateAuth("listUser", "listPassword", 2, new AuthHandler.AuthHandlerCallback() {
            @Override
            public void onSuccess(String token, String successMessage) {
                System.out.println("Setup user created: " + successMessage);
            }

            @Override
            public void onFailure(String errorMessage) {
                // User may already exist from a previous run, that is fine
                System.out.println("Setup user note: " + errorMessage);
            }
        });

        // Now retrieve all users
        authHandler.handleGetAllUsers(new RequestCallback<UserDto[]>() {
            @Override
            public void onSuccess(UserDto[] users, String message) {
                System.out.println("Get all users success: " + message);
                for (UserDto user : users) {
                    System.out.println("  username=" + user.getUsername() + ", role=" + user.getRole());
                }
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                System.err.println("Get all users failed [" + errorCode + "]: " + errorMessage);
            }
        });
    }

}
