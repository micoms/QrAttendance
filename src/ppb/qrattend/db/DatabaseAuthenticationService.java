package ppb.qrattend.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.ModelUser;

public final class DatabaseAuthenticationService {

    public static final class AuthenticationResult {

        private final boolean success;
        private final ModelUser user;
        private final String message;

        private AuthenticationResult(boolean success, ModelUser user, String message) {
            // Mini-code guide:
            // 1. Capture whether authentication succeeded.
            // 2. Store the authenticated ModelUser on success, otherwise keep it null.
            // 3. Keep message ready for direct UI display.
            this.success = success;
            this.user = user;
            this.message = message;
        }

        public static AuthenticationResult success(ModelUser user, String message) {
            // Mini-code guide:
            // 1. Build a success result when DB credentials are valid and role/account checks passed.
            return new AuthenticationResult(true, user, message);
        }

        public static AuthenticationResult failure(String message) {
            // Mini-code guide:
            // 1. Build a failure result with no authenticated user attached.
            return new AuthenticationResult(false, null, message);
        }

        public boolean isSuccess() {
            // Mini-code guide:
            // 1. Return whether authentication completed successfully.
            return success;
        }

        public ModelUser getUser() {
            // Mini-code guide:
            // 1. Return the mapped user for successful login flows.
            return user;
        }

        public String getMessage() {
            // Mini-code guide:
            // 1. Return the login status text for the Swing form.
            return message;
        }
    }

    private static final String AUTH_SQL = """
            SELECT user_id, full_name, email, role, account_status
            FROM users
            WHERE email = ?
              AND role = ?
              AND password_hash = ?
            LIMIT 1
            """;

    private static final String UPDATE_LAST_LOGIN_SQL = """
            UPDATE users
            SET last_login_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
            """;

    private final DatabaseManager databaseManager;

    public DatabaseAuthenticationService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public static DatabaseAuthenticationService fromDefaultConfig() {
        // Mini-code guide:
        // 1. Build the auth service from the default config/database.properties file.
        return new DatabaseAuthenticationService(DatabaseManager.fromDefaultConfig());
    }

    public boolean isDatabaseLoginEnabled() {
        // Mini-code guide:
        // 1. Return whether DB-backed login is enabled in config, regardless of readiness.
        return databaseManager.getConfig().isEnabled();
    }

    public boolean isDatabaseReady() {
        // Mini-code guide:
        // 1. Return whether login can actually connect because config + driver are both ready.
        return databaseManager.isReady();
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return the DB readiness message for login diagnostics.
        return databaseManager.getStatusMessage();
    }

    public AuthenticationResult authenticate(String email, String password, AppDomain.UserRole role) {
        // Mini-code guide:
        // 1. Reject immediately when DB login is disabled or the JDBC layer is not ready.
        // 2. Normalize email to lowercase and trim the password.
        // 3. Reject blank credentials before touching MariaDB.
        // 4. Hash the plain password with PasswordUtil.hashPassword(...) so the query compares stored hashes.
        // 5. SELECT user_id, full_name, email, role, account_status FROM users
        //    WHERE email = ? AND role = ? AND password_hash = ? LIMIT 1.
        // 6. If no row matches, return failure for invalid credentials.
        // 7. Reject non-ACTIVE accounts.
        // 8. Map the row into ModelUser, call updateLastLogin(...), and return success.
        if (!databaseManager.getConfig().isEnabled()) {
            return AuthenticationResult.failure(databaseManager.getStatusMessage());
        }
        if (!databaseManager.isReady()) {
            return AuthenticationResult.failure(databaseManager.getStatusMessage());
        }

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String plainPassword = password == null ? "" : password.trim();
        if (normalizedEmail.isBlank() || plainPassword.isBlank()) {
            return AuthenticationResult.failure("Enter your email and password.");
        }

        String passwordHash = PasswordUtil.hashPassword(plainPassword);
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(AUTH_SQL)) {
            statement.setString(1, normalizedEmail);
            statement.setString(2, role.name());
            statement.setString(3, passwordHash);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return AuthenticationResult.failure("Incorrect email, password, or account type.");
                }
                String accountStatus = rs.getString("account_status");
                if (!"ACTIVE".equalsIgnoreCase(accountStatus)) {
                    return AuthenticationResult.failure("This account is not active yet. Please ask the admin.");
                }

                ModelUser user = new ModelUser(
                        rs.getInt("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        AppDomain.UserRole.valueOf(rs.getString("role"))
                );
                updateLastLogin(connection, user.getUserId());
                return AuthenticationResult.success(user, "Signed in.");
            }
        } catch (SQLException ex) {
            return AuthenticationResult.failure("We couldn't sign you in right now. Please try again.");
        }
    }

    private void updateLastLogin(Connection connection, int userId) throws SQLException {
        // Mini-code guide:
        // 1. Reuse the caller's open connection so login + last_login update stay in one DB session.
        // 2. Execute UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE user_id = ?.
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_LAST_LOGIN_SQL)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }
}
