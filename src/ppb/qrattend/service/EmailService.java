package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.model.CoreModels.EmailLog;
import ppb.qrattend.model.CoreModels.EmailStatus;
import ppb.qrattend.util.AppClock;

public final class EmailService {

    private static final String INSERT_EMAIL_SQL = """
            INSERT INTO email_logs
                (email_type, related_user_id, related_student_id, recipient_email, subject_line, info_text, status)
            VALUES (?, ?, ?, ?, ?, ?, 'QUEUED')
            """;

    private static final String UPDATE_EMAIL_SQL = """
            UPDATE email_logs
            SET status = ?, provider_message_id = ?, error_text = ?, sent_at = ?
            WHERE email_id = ?
            """;

    private static final String SELECT_RECENT_SQL = """
            SELECT email_id, email_type, recipient_email, subject_line, info_text, status, created_at
            FROM email_logs
            ORDER BY email_id DESC
            LIMIT ?
            """;

    private final DatabaseManager databaseManager;

    public EmailService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public EmailService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public int createQueuedEmail(Connection connection, String emailType, Integer userId, Integer studentId,
            String recipientEmail, String subjectLine, String infoText) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EMAIL_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, emailType);
            if (userId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, userId);
            }
            if (studentId == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setInt(3, studentId);
            }
            statement.setString(4, recipientEmail);
            statement.setString(5, subjectLine);
            statement.setString(6, infoText);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Email log key was not returned.");
                }
                return keys.getInt(1);
            }
        }
    }

    public void markSent(Connection connection, int emailId, String providerMessageId) throws SQLException {
        updateStatus(connection, emailId, "SENT", providerMessageId, "", AppClock.nowDateTime());
    }

    public void markFailed(Connection connection, int emailId, String errorText) throws SQLException {
        updateStatus(connection, emailId, "FAILED", "", safe(errorText), null);
    }

    public ServiceResult<List<EmailLog>> getRecentLogs(int limit) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<EmailLog> logs = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_RECENT_SQL)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    logs.add(new EmailLog(
                            resultSet.getInt("email_id"),
                            resultSet.getString("email_type"),
                            resultSet.getString("recipient_email"),
                            resultSet.getString("subject_line"),
                            resultSet.getString("info_text"),
                            mapStatus(resultSet.getString("status")),
                            resultSet.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
            return ServiceResult.success("Loaded email logs.", logs);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load email logs.");
        }
    }

    private void updateStatus(Connection connection, int emailId, String status, String providerMessageId,
            String errorText, java.time.LocalDateTime sentAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_EMAIL_SQL)) {
            statement.setString(1, status);
            statement.setString(2, safe(providerMessageId));
            statement.setString(3, safe(errorText));
            if (sentAt == null) {
                statement.setNull(4, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(4, java.sql.Timestamp.valueOf(sentAt));
            }
            statement.setInt(5, emailId);
            statement.executeUpdate();
        }
    }

    private EmailStatus mapStatus(String raw) {
        if (raw == null) {
            return EmailStatus.NOT_SENT;
        }
        return switch (raw.trim().toUpperCase()) {
            case "SENT" -> EmailStatus.SENT;
            case "FAILED" -> EmailStatus.FAILED;
            default -> EmailStatus.NOT_SENT;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
