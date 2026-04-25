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
            this.success = success;
            this.user = user;
            this.message = message;
        }

        public static AuthenticationResult success(ModelUser user, String message) {
            return new AuthenticationResult(true, user, message);
        }

        public static AuthenticationResult failure(String message) {
            return new AuthenticationResult(false, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public ModelUser getUser() {
            return user;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final String AUTH_SQL = """
            SELECT user_id, full_name, email, role, is_active, must_change_password
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
        return new DatabaseAuthenticationService(DatabaseManager.fromDefaultConfig());
    }

    public boolean isDatabaseLoginEnabled() {
        return databaseManager.getConfig().isEnabled();
    }

    public boolean isDatabaseReady() {
        return databaseManager.isReady();
    }

    public String getStatusMessage() {
        return databaseManager.getStatusMessage();
    }

    public AuthenticationResult authenticate(String email, String password, AppDomain.UserRole role) {
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
                if (!rs.getBoolean("is_active")) {
                    return AuthenticationResult.failure("This account is not active yet. Please ask the admin.");
                }

                ModelUser user = new ModelUser(
                        rs.getInt("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        AppDomain.UserRole.valueOf(rs.getString("role"))
                );
                user.setMustChangePassword(rs.getBoolean("must_change_password"));
                updateLastLogin(connection, user.getUserId());
                return AuthenticationResult.success(user, "Signed in.");
            }
        } catch (SQLException ex) {
            return AuthenticationResult.failure("We couldn't sign you in right now. Please try again.");
        }
    }

    private void updateLastLogin(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_LAST_LOGIN_SQL)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }
}
