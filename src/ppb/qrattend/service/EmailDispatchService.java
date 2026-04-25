package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.db.SecurityUtil;
import ppb.qrattend.model.AppDomain.EmailDispatch;
import ppb.qrattend.model.AppDomain.EmailStatus;

public final class EmailDispatchService {

    private static final String SAFE_PASSWORD_PREVIEW = "Teacher password email queued.";
    private static final String SAFE_QR_PREVIEW = "Student QR code email queued.";

    private final DatabaseManager databaseManager;

    public EmailDispatchService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public EmailDispatchService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<List<EmailDispatch>> getEmailDispatches() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        List<EmailDispatch> dispatches = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT email_id, recipient_email, subject_line, message_preview, delivery_status,
                               COALESCE(sent_at, created_at) AS display_time
                        FROM email_dispatch_logs
                        ORDER BY email_id DESC
                        """);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                dispatches.add(mapEmailDispatch(resultSet));
            }
            return ServiceResult.success("Email history loaded.", dispatches);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load email history.");
        }
    }

    public ServiceResult<List<EmailDispatch>> getRecentEmailDispatches(int limit) {
        if (limit <= 0) {
            return ServiceResult.failure("Recent email limit must be greater than 0.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        List<EmailDispatch> dispatches = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT email_id, recipient_email, subject_line, message_preview, delivery_status,
                               COALESCE(sent_at, created_at) AS display_time
                        FROM email_dispatch_logs
                        ORDER BY email_id DESC
                        LIMIT ?
                        """)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    dispatches.add(mapEmailDispatch(resultSet));
                }
            }
            return ServiceResult.success("Recent email history loaded.", dispatches);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load recent email history.");
        }
    }

    public ServiceResult<Integer> getFailedEmailCount() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*) AS failed_count
                        FROM email_dispatch_logs
                        WHERE delivery_status = 'FAILED'
                        """);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return ServiceResult.success("Failed email count loaded.", resultSet.getInt("failed_count"));
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load failed email count.");
        }
    }

    public ServiceResult<EmailDispatch> queueTeacherPasswordEmail(int teacherId, String recipientEmail, String temporaryPassword) {
        return queueTeacherPasswordEmail(null, teacherId, recipientEmail, false);
    }

    public ServiceResult<EmailDispatch> queueTeacherPasswordEmail(Connection connection, int teacherId,
            String recipientEmail, boolean resetMode) {
        return insertEmailLog(
                connection,
                recipientEmail,
                resetMode ? "PASSWORD_RESET" : "TEACHER_PASSWORD",
                teacherId,
                null,
                resetMode ? "Your password was reset" : "Your teacher account is ready",
                SAFE_PASSWORD_PREVIEW
        );
    }

    public ServiceResult<EmailDispatch> queueStudentQrEmail(int studentPrimaryKey, String recipientEmail, String qrPayload, boolean resend) {
        return queueStudentQrEmail(null, studentPrimaryKey, recipientEmail, resend);
    }

    public ServiceResult<EmailDispatch> queueStudentQrEmail(Connection connection, int studentPrimaryKey,
            String recipientEmail, boolean resend) {
        return insertEmailLog(
                connection,
                recipientEmail,
                resend ? "QR_RESEND" : "STUDENT_QR",
                null,
                studentPrimaryKey,
                resend ? "Your QR code was sent again" : "Your QR code is ready",
                SAFE_QR_PREVIEW
        );
    }

    public ServiceResult<Void> markEmailSent(int emailDispatchId, String providerMessageId) {
        if (emailDispatchId <= 0) {
            return ServiceResult.failure("Email log is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            return markEmailSent(connection, emailDispatchId, providerMessageId);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the email log.");
        }
    }

    public ServiceResult<Void> markEmailFailed(int emailDispatchId, String errorMessage) {
        if (emailDispatchId <= 0) {
            return ServiceResult.failure("Email log is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            return markEmailFailed(connection, emailDispatchId, errorMessage);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the email log.");
        }
    }

    public ServiceResult<Void> markEmailSent(Connection connection, int emailDispatchId, String providerMessageId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE email_dispatch_logs
                SET delivery_status = 'SENT',
                    provider_message_id = ?,
                    error_message = NULL,
                    sent_at = CURRENT_TIMESTAMP
                WHERE email_id = ?
                """)) {
            statement.setString(1, safe(providerMessageId));
            statement.setInt(2, emailDispatchId);
            if (statement.executeUpdate() == 0) {
                return ServiceResult.failure("Email log not found.");
            }
            return ServiceResult.success("Email marked as sent.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the email log.");
        }
    }

    public ServiceResult<Void> markEmailFailed(Connection connection, int emailDispatchId, String errorMessage) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE email_dispatch_logs
                SET delivery_status = 'FAILED',
                    error_message = ?,
                    sent_at = NULL
                WHERE email_id = ?
                """)) {
            statement.setString(1, truncate(errorMessage, 1000));
            statement.setInt(2, emailDispatchId);
            if (statement.executeUpdate() == 0) {
                return ServiceResult.failure("Email log not found.");
            }
            return ServiceResult.success("Email marked as failed.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the email log.");
        }
    }

    public void markEmailSentQuietly(int emailDispatchId, String providerMessageId) {
        if (!databaseManager.isReady() || emailDispatchId <= 0) {
            return;
        }
        try (Connection connection = databaseManager.openConnection()) {
            markEmailSent(connection, emailDispatchId, providerMessageId);
        } catch (SQLException ex) {
            // Keep the main business action committed even if this follow-up update fails.
        }
    }

    public void markEmailFailedQuietly(int emailDispatchId, String errorMessage) {
        if (!databaseManager.isReady() || emailDispatchId <= 0) {
            return;
        }
        try (Connection connection = databaseManager.openConnection()) {
            markEmailFailed(connection, emailDispatchId, errorMessage);
        } catch (SQLException ex) {
            // Keep the main business action committed even if this follow-up update fails.
        }
    }

    private ServiceResult<EmailDispatch> insertEmailLog(Connection connection, String recipientEmail, String emailType, Integer relatedUserId,
            Integer relatedStudentPk, String subjectLine, String preview) {
        if (isBlank(recipientEmail) || !looksLikeEmail(recipientEmail)) {
            return ServiceResult.failure("Enter a valid email first.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        boolean openedHere = false;
        try {
            if (connection == null) {
                connection = databaseManager.openConnection();
                openedHere = true;
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO email_dispatch_logs
                                (recipient_email, email_type, related_user_id, related_student_pk, subject_line, message_preview, delivery_status)
                            VALUES (?, ?, ?, ?, ?, ?, 'QUEUED')
                            """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, recipientEmail.trim().toLowerCase(Locale.ENGLISH));
                statement.setString(2, emailType);
                if (relatedUserId == null) {
                    statement.setNull(3, java.sql.Types.BIGINT);
                } else {
                    statement.setInt(3, relatedUserId);
                }
                if (relatedStudentPk == null) {
                    statement.setNull(4, java.sql.Types.BIGINT);
                } else {
                    statement.setInt(4, relatedStudentPk);
                }
                statement.setString(5, SecurityUtil.safePreview(subjectLine));
                statement.setString(6, SecurityUtil.safePreview(preview));
                statement.executeUpdate();

                int emailId;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        return ServiceResult.failure("Could not queue the email.");
                    }
                    emailId = keys.getInt(1);
                }

                EmailDispatch dispatch = new EmailDispatch(
                        emailId,
                        recipientEmail.trim().toLowerCase(Locale.ENGLISH),
                        SecurityUtil.safePreview(subjectLine),
                        SecurityUtil.safePreview(preview),
                        EmailStatus.QUEUED,
                        LocalDateTime.now()
                );
                return ServiceResult.success("Email queued.", dispatch);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not queue the email.");
        } finally {
            if (openedHere && connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    // Nothing else to do here.
                }
            }
        }
    }

    private EmailDispatch mapEmailDispatch(ResultSet resultSet) throws SQLException {
        return new EmailDispatch(
                resultSet.getInt("email_id"),
                resultSet.getString("recipient_email"),
                resultSet.getString("subject_line"),
                resultSet.getString("message_preview"),
                mapEmailStatus(resultSet.getString("delivery_status")),
                toLocalDateTime(resultSet.getTimestamp("display_time"))
        );
    }

    private EmailStatus mapEmailStatus(String value) {
        try {
            return EmailStatus.valueOf(value == null ? "QUEUED" : value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            return EmailStatus.QUEUED;
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private boolean looksLikeEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        String safe = safe(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
