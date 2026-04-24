package ppb.qrattend.service;

import java.security.SecureRandom;
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
import ppb.qrattend.db.PasswordUtil;
import ppb.qrattend.db.SecurityUtil;
import ppb.qrattend.email.ResendEmailClient;
import ppb.qrattend.model.AppDomain.EmailStatus;
import ppb.qrattend.model.AppDomain.TeacherProfile;

public final class TeacherService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private static final String SELECT_TEACHERS_SQL = """
            SELECT
                u.user_id,
                u.full_name,
                u.email,
                u.account_status,
                u.created_at,
                COALESCE((
                    SELECT ed.delivery_status
                    FROM email_dispatch_logs ed
                    WHERE ed.related_user_id = u.user_id
                      AND ed.email_type IN ('TEACHER_PASSWORD', 'PASSWORD_RESET')
                    ORDER BY ed.email_id DESC
                    LIMIT 1
                ), 'QUEUED') AS last_email_status
            FROM users u
            INNER JOIN teacher_profiles tp
                ON tp.teacher_user_id = u.user_id
            WHERE u.role = 'TEACHER'
            ORDER BY u.full_name ASC
            """;

    private static final String SELECT_TEACHER_BY_ID_SQL = """
            SELECT
                u.user_id,
                u.full_name,
                u.email,
                u.account_status,
                u.created_at,
                COALESCE((
                    SELECT ed.delivery_status
                    FROM email_dispatch_logs ed
                    WHERE ed.related_user_id = u.user_id
                      AND ed.email_type IN ('TEACHER_PASSWORD', 'PASSWORD_RESET')
                    ORDER BY ed.email_id DESC
                    LIMIT 1
                ), 'QUEUED') AS last_email_status
            FROM users u
            INNER JOIN teacher_profiles tp
                ON tp.teacher_user_id = u.user_id
            WHERE u.user_id = ?
              AND u.role = 'TEACHER'
            LIMIT 1
            """;

    private static final String SELECT_TEACHER_ACCOUNT_SQL = """
            SELECT user_id, full_name, email, account_status
            FROM users
            WHERE user_id = ?
              AND role = 'TEACHER'
            LIMIT 1
            """;

    private static final String CHECK_EMAIL_SQL = """
            SELECT 1
            FROM users
            WHERE email = ?
            LIMIT 1
            """;

    private static final String INSERT_USER_SQL = """
            INSERT INTO users (full_name, email, password_hash, role, account_status, must_change_password)
            VALUES (?, ?, ?, 'TEACHER', 'ACTIVE', 1)
            """;

    private static final String INSERT_TEACHER_PROFILE_SQL = """
            INSERT INTO teacher_profiles (teacher_user_id, employee_code, schedule_edit_mode, notes)
            VALUES (?, NULL, 'APPROVAL_REQUIRED', ?)
            """;

    private static final String INSERT_EMAIL_LOG_SQL = """
            INSERT INTO email_dispatch_logs
                (recipient_email, email_type, related_user_id, subject_line, message_preview, delivery_status)
            VALUES (?, ?, ?, ?, ?, 'QUEUED')
            """;

    private static final String INSERT_AUDIT_LOG_SQL = """
            INSERT INTO audit_logs
                (actor_user_id, action_type, entity_type, entity_id, old_values_json, new_values_json, notes, created_at)
            VALUES (?, ?, 'TEACHER', ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private static final String UPDATE_PASSWORD_SQL = """
            UPDATE users
            SET password_hash = ?, must_change_password = 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
              AND role = 'TEACHER'
            """;

    private static final String UPDATE_EMAIL_SENT_SQL = """
            UPDATE email_dispatch_logs
            SET delivery_status = 'SENT',
                provider_message_id = ?,
                error_message = NULL,
                sent_at = CURRENT_TIMESTAMP
            WHERE email_id = ?
            """;

    private static final String UPDATE_EMAIL_FAILED_SQL = """
            UPDATE email_dispatch_logs
            SET delivery_status = 'FAILED',
                error_message = ?,
                sent_at = NULL
            WHERE email_id = ?
            """;

    private final DatabaseManager databaseManager;
    private final ResendEmailClient resendEmailClient;

    public TeacherService() {
        // Mini-code guide:
        // 1. Wire the teacher module to the shared MariaDB config and Resend config.
        this(DatabaseManager.fromDefaultConfig(), ResendEmailClient.createDefault());
    }

    public TeacherService(DatabaseManager databaseManager, ResendEmailClient resendEmailClient) {
        // Mini-code guide:
        // 1. Allow constructor injection so the service can be tested independently later.
        this.databaseManager = databaseManager;
        this.resendEmailClient = resendEmailClient;
    }

    public boolean isReady() {
        // Mini-code guide:
        // 1. Teacher DB flows can run when the MariaDB layer is ready.
        return databaseManager.isReady();
    }

    public ServiceResult<List<TeacherProfile>> getTeachers() {
        /*
         * Purpose:
         * Load the teacher list for the admin dashboard and teacher-management screen.
         *
         * Tables touched:
         * - users
         * - teacher_profiles
         * - email_dispatch_logs (optional last-email summary)
         *
         * Validation:
         * - Only admins should call this in the real implementation.
         *
         * Transaction scope:
         * - Read-only query.
         *
         * Success result:
         * - Return teachers sorted by full_name with account/email status ready for UI binding.
         *
         * Failure cases:
         * - Database connection failure.
         * - Unauthorized caller.
         */
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        List<TeacherProfile> teachers = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_TEACHERS_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                teachers.add(mapTeacherProfile(resultSet));
            }
            return ServiceResult.success("Loaded teachers from MariaDB.", teachers);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load teachers: " + ex.getMessage());
        }
    }

    public ServiceResult<TeacherProfile> findTeacher(int teacherId) {
        /*
         * Purpose:
         * Load one teacher profile by primary id.
         *
         * Tables touched:
         * - users
         * - teacher_profiles
         *
         * Validation:
         * - teacherId must be greater than 0.
         *
         * Transaction scope:
         * - Read-only query.
         *
         * Success result:
         * - Return the matching TeacherProfile.
         *
         * Failure cases:
         * - Invalid teacher id.
         * - Teacher not found.
         * - Database connection failure.
         */
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher id must be greater than 0.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_TEACHER_BY_ID_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ServiceResult.failure("Teacher not found.");
                }
                return ServiceResult.success("Loaded teacher from MariaDB.", mapTeacherProfile(resultSet));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load teacher: " + ex.getMessage());
        }
    }

    public ServiceResult<TeacherProfile> createTeacherAccount(int actorUserId, String fullName, String email) {
        /*
         * Purpose:
         * Create the login account and profile row for a new teacher.
         *
         * Tables touched:
         * - users
         * - teacher_profiles
         * - email_dispatch_logs
         * - audit_logs
         *
         * Validation:
         * - fullName and email are required.
         * - email must be unique and valid.
         * - role must always be TEACHER for this flow.
         *
         * Transaction scope:
         * - Single JDBC transaction covering user insert, profile insert, audit insert, and email queue insert.
         *
         * Success result:
         * - Return the created TeacherProfile and queue the temporary password email.
         *
         * Failure cases:
         * - Duplicate email.
         * - Password generation/hash failure.
         * - Any insert failure that should roll back the whole transaction.
         */
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (actorUserId <= 0) {
            return ServiceResult.failure("A valid admin account is required to create a teacher.");
        }

        String normalizedName = normalizeName(fullName);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedName.isBlank() || normalizedEmail.isBlank()) {
            return ServiceResult.failure("Teacher name and email are required.");
        }
        if (!looksLikeEmail(normalizedEmail)) {
            return ServiceResult.failure("Enter a valid teacher email address.");
        }

        String temporaryPassword = generateTemporaryPassword();
        String passwordHash = PasswordUtil.hashPassword(temporaryPassword);
        long teacherUserId;
        long emailLogId;

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (emailExists(connection, normalizedEmail)) {
                    connection.rollback();
                    return ServiceResult.failure("A teacher already uses that email address.");
                }

                teacherUserId = insertUser(connection, normalizedName, normalizedEmail, passwordHash);
                insertTeacherProfile(connection, teacherUserId, actorUserId);
                emailLogId = insertTeacherEmailLog(connection, teacherUserId, normalizedEmail, temporaryPassword, false);
                insertAuditLog(connection, actorUserId, "TEACHER_CREATE", String.valueOf(teacherUserId),
                        null,
                        buildTeacherJson(normalizedName, normalizedEmail, "ACTIVE"),
                        "Teacher account created by admin.");
                connection.commit();
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not create the teacher account.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not create the teacher account.");
        }

        ResendEmailClient.EmailSendResult emailResult = resendEmailClient.sendTeacherPasswordEmail(
                normalizedEmail, normalizedName, temporaryPassword, false);
        TeacherProfile teacherProfile = loadTeacherProfileOrFallback((int) teacherUserId, normalizedName, normalizedEmail);

        if (emailResult.isSuccess()) {
            updateEmailLogSent(emailLogId, emailResult.getProviderMessageId());
            teacherProfile.setEmailStatus(EmailStatus.SENT);
            return ServiceResult.success(
                    "Teacher account created and password email sent to " + normalizedName + ".",
                    loadTeacherProfileOrFallback((int) teacherUserId, normalizedName, normalizedEmail)
            );
        }

        updateEmailLogFailed(emailLogId, emailResult.getMessage());
        teacherProfile.setEmailStatus(EmailStatus.FAILED);
        return ServiceResult.warning(
                "Teacher account created, but the password email could not be sent yet.",
                teacherProfile
        );
    }

    public ServiceResult<Void> resendTeacherPassword(int actorUserId, int teacherId) {
        /*
         * Purpose:
         * Re-send the current temporary or reset password email to a teacher.
         *
         * Tables touched:
         * - users
         * - email_dispatch_logs
         * - audit_logs
         *
         * Validation:
         * - teacherId must exist and belong to a TEACHER account.
         * - The account should be ACTIVE before sending email.
         *
         * Transaction scope:
         * - Small transaction for audit + email queue.
         *
         * Success result:
         * - Add a new email_dispatch_logs row with delivery_status QUEUED or SENT.
         *
         * Failure cases:
         * - Teacher not found.
         * - Email provider handoff fails.
         */
        return rotateTeacherPassword(actorUserId, teacherId,
                "TEACHER_PASSWORD_RESEND",
                "Teacher password resend requested by admin.",
                "A new temporary password was sent to ");
    }

    public ServiceResult<Void> resetTeacherPassword(int actorUserId, int teacherId) {
        /*
         * Purpose:
         * Generate a new temporary password and force the teacher to change it on next login.
         *
         * Tables touched:
         * - users
         * - email_dispatch_logs
         * - audit_logs
         *
         * Validation:
         * - teacherId must exist.
         * - Only admins should trigger this.
         *
         * Transaction scope:
         * - Single transaction for password update, must_change_password flag, audit insert, and email queue.
         *
         * Success result:
         * - Update password_hash and must_change_password, then queue a reset email.
         *
         * Failure cases:
         * - Teacher not found.
         * - Update failure or email queue failure requiring rollback.
         */
        return rotateTeacherPassword(actorUserId, teacherId,
                "TEACHER_PASSWORD_RESET",
                "Teacher password reset by admin.",
                "Password reset email sent to ");
    }

    private ServiceResult<Void> rotateTeacherPassword(int actorUserId, int teacherId, String auditActionType,
            String auditNote, String successMessagePrefix) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (actorUserId <= 0) {
            return ServiceResult.failure("A valid admin account is required for this action.");
        }
        if (teacherId <= 0) {
            return ServiceResult.failure("Select a teacher first.");
        }

        TeacherAccountRow teacherRow;
        String temporaryPassword = generateTemporaryPassword();
        String passwordHash = PasswordUtil.hashPassword(temporaryPassword);
        long emailLogId;

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                teacherRow = loadTeacherAccount(connection, teacherId);
                if (teacherRow == null) {
                    connection.rollback();
                    return ServiceResult.failure("Teacher not found.");
                }
                if (!"ACTIVE".equalsIgnoreCase(teacherRow.accountStatus)) {
                    connection.rollback();
                    return ServiceResult.failure("This teacher account is not active.");
                }

                try (PreparedStatement update = connection.prepareStatement(UPDATE_PASSWORD_SQL)) {
                    update.setString(1, passwordHash);
                    update.setInt(2, teacherId);
                    if (update.executeUpdate() == 0) {
                        connection.rollback();
                        return ServiceResult.failure("Teacher password could not be updated.");
                    }
                }

                emailLogId = insertTeacherEmailLog(connection, teacherId, teacherRow.email, temporaryPassword, true);
                insertAuditLog(connection, actorUserId, auditActionType, String.valueOf(teacherId),
                        null,
                        buildTeacherJson(teacherRow.fullName, teacherRow.email, teacherRow.accountStatus),
                        auditNote);
                connection.commit();
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not update the teacher password.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the teacher password.");
        }

        ResendEmailClient.EmailSendResult emailResult = resendEmailClient.sendTeacherPasswordEmail(
                teacherRow.email, teacherRow.fullName, temporaryPassword, true);

        if (emailResult.isSuccess()) {
            updateEmailLogSent(emailLogId, emailResult.getProviderMessageId());
            return ServiceResult.success(successMessagePrefix + teacherRow.fullName + ".", null);
        }

        updateEmailLogFailed(emailLogId, emailResult.getMessage());
        return ServiceResult.warning(
                "Password was changed for " + teacherRow.fullName + ", but the email could not be sent yet.",
                null
        );
    }

    private TeacherProfile loadTeacherProfileOrFallback(int teacherId, String fullName, String email) {
        ServiceResult<TeacherProfile> profileResult = findTeacher(teacherId);
        if (profileResult.isSuccess() && profileResult.getData() != null) {
            return profileResult.getData();
        }
        return new TeacherProfile(teacherId, fullName, email, EmailStatus.QUEUED, "ACTIVE", LocalDateTime.now());
    }

    private TeacherProfile mapTeacherProfile(ResultSet resultSet) throws SQLException {
        return new TeacherProfile(
                resultSet.getInt("user_id"),
                resultSet.getString("full_name"),
                resultSet.getString("email"),
                mapEmailStatus(resultSet.getString("last_email_status")),
                resultSet.getString("account_status"),
                toLocalDateTime(resultSet.getTimestamp("created_at"))
        );
    }

    private TeacherAccountRow loadTeacherAccount(Connection connection, int teacherId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_TEACHER_ACCOUNT_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new TeacherAccountRow(
                        resultSet.getInt("user_id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        resultSet.getString("account_status")
                );
            }
        }
    }

    private boolean emailExists(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHECK_EMAIL_SQL)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private long insertUser(Connection connection, String fullName, String email, String passwordHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_USER_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("User insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertTeacherProfile(Connection connection, long teacherUserId, int actorUserId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TEACHER_PROFILE_SQL)) {
            statement.setLong(1, teacherUserId);
            statement.setString(2, "Created by admin #" + actorUserId + ".");
            statement.executeUpdate();
        }
    }

    private long insertTeacherEmailLog(Connection connection, long teacherUserId, String recipientEmail,
            String temporaryPassword, boolean resetMode) throws SQLException {
        String subject = resetMode ? "Teacher password reset" : "Your QR Attend teacher account";
        String preview = SecurityUtil.safePreview(resetMode
                ? "Teacher password reset email queued."
                : "Teacher welcome email queued.");

        try (PreparedStatement statement = connection.prepareStatement(INSERT_EMAIL_LOG_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, recipientEmail);
            statement.setString(2, resetMode ? "PASSWORD_RESET" : "TEACHER_PASSWORD");
            statement.setLong(3, teacherUserId);
            statement.setString(4, subject);
            statement.setString(5, preview);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Email log insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertAuditLog(Connection connection, int actorUserId, String actionType, String entityId,
            String oldValuesJson, String newValuesJson, String notes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT_LOG_SQL)) {
            statement.setInt(1, actorUserId);
            statement.setString(2, actionType);
            statement.setString(3, entityId);
            statement.setString(4, oldValuesJson);
            statement.setString(5, newValuesJson);
            statement.setString(6, notes);
            statement.executeUpdate();
        }
    }

    private void updateEmailLogSent(long emailLogId, String providerMessageId) {
        if (!databaseManager.isReady()) {
            return;
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_EMAIL_SENT_SQL)) {
            statement.setString(1, providerMessageId == null ? "" : providerMessageId);
            statement.setLong(2, emailLogId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            // Keep the teacher creation/reset committed even if the follow-up log update fails.
        }
    }

    private void updateEmailLogFailed(long emailLogId, String errorMessage) {
        if (!databaseManager.isReady()) {
            return;
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_EMAIL_FAILED_SQL)) {
            statement.setString(1, truncate(errorMessage, 1000));
            statement.setLong(2, emailLogId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            // Keep the teacher creation/reset committed even if the follow-up log update fails.
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            // Ignore rollback exceptions because the original failure is already being handled.
        }
    }

    private void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ex) {
            // Ignore auto-commit restore failures; the connection is closing right after this.
        }
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

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private boolean looksLikeEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    private String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            builder.append(PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return builder.toString();
    }

    private String buildTeacherJson(String fullName, String email, String status) {
        return "{"
                + "\"full_name\":\"" + escapeJson(fullName) + "\","
                + "\"email\":\"" + escapeJson(email) + "\","
                + "\"status\":\"" + escapeJson(status) + "\""
                + "}";
    }

    private String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 24);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static final class TeacherAccountRow {

        private final int userId;
        private final String fullName;
        private final String email;
        private final String accountStatus;

        private TeacherAccountRow(int userId, String fullName, String email, String accountStatus) {
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
            this.accountStatus = accountStatus;
        }
    }
}
