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

    private static final String UPDATE_PASSWORD_SQL = """
            UPDATE users
            SET password_hash = ?, must_change_password = 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
              AND role = 'TEACHER'
            """;

    private final DatabaseManager databaseManager;
    private final ResendEmailClient resendEmailClient;
    private final AuditLogService auditLogService;
    private final EmailDispatchService emailDispatchService;

    public TeacherService() {
        // Mini-code guide:
        // 1. Wire the teacher module to the shared MariaDB config and Resend config.
        this(DatabaseManager.fromDefaultConfig(), ResendEmailClient.createDefault());
    }

    public TeacherService(DatabaseManager databaseManager, ResendEmailClient resendEmailClient) {
        this(databaseManager, resendEmailClient, new AuditLogService(databaseManager), new EmailDispatchService(databaseManager));
    }

    public TeacherService(DatabaseManager databaseManager, ResendEmailClient resendEmailClient,
            AuditLogService auditLogService, EmailDispatchService emailDispatchService) {
        // Mini-code guide:
        // 1. Allow constructor injection so the service can be tested independently later.
        this.databaseManager = databaseManager;
        this.resendEmailClient = resendEmailClient;
        this.auditLogService = auditLogService;
        this.emailDispatchService = emailDispatchService;
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
        ServiceResult<TeacherDraft> draftResult = prepareTeacherDraft(actorUserId, fullName, email);
        if (!draftResult.isSuccess() || draftResult.getData() == null) {
            return ServiceResult.failure(draftResult.getMessage());
        }

        TeacherDraft draft = draftResult.getData();
        ServiceResult<TeacherCreateResult> saveResult = saveTeacherAccount(actorUserId, draft);
        if (!saveResult.isSuccess() || saveResult.getData() == null) {
            return ServiceResult.failure(saveResult.getMessage());
        }

        return sendTeacherPasswordEmail(saveResult.getData(), draft.temporaryPassword, false,
                "Teacher account created and password email sent to ",
                "Teacher account created, but the password email could not be sent yet.");
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
        ServiceResult<TeacherPasswordChangeResult> changeResult = saveTeacherPasswordChange(
                actorUserId, teacherId, auditActionType, auditNote);
        if (!changeResult.isSuccess() || changeResult.getData() == null) {
            return ServiceResult.failure(changeResult.getMessage());
        }

        TeacherPasswordChangeResult change = changeResult.getData();
        ResendEmailClient.EmailSendResult emailResult = resendEmailClient.sendTeacherPasswordEmail(
                change.teacher.email, change.teacher.fullName, change.temporaryPassword, true);

        if (emailResult.isSuccess()) {
            emailDispatchService.markEmailSentQuietly(change.emailLogId, emailResult.getProviderMessageId());
            return ServiceResult.success(successMessagePrefix + change.teacher.fullName + ".", null);
        }

        emailDispatchService.markEmailFailedQuietly(change.emailLogId, emailResult.getMessage());
        return ServiceResult.warning(
                "Password was changed for " + change.teacher.fullName + ", but the email could not be sent yet.",
                null
        );
    }

    private ServiceResult<TeacherDraft> prepareTeacherDraft(int actorUserId, String fullName, String email) {
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
        return ServiceResult.success("Teacher details are ready.",
                new TeacherDraft(normalizedName, normalizedEmail, temporaryPassword, PasswordUtil.hashPassword(temporaryPassword)));
    }

    private ServiceResult<TeacherCreateResult> saveTeacherAccount(int actorUserId, TeacherDraft draft) {
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (emailExists(connection, draft.email)) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("A teacher already uses that email address.");
                }

                long teacherUserId = insertUser(connection, draft.fullName, draft.email, draft.passwordHash);
                insertTeacherProfile(connection, teacherUserId, actorUserId);

                ServiceResult<Integer> emailLogResult = queueTeacherPasswordEmail(connection, (int) teacherUserId, draft.email, false);
                if (!emailLogResult.isSuccess() || emailLogResult.getData() == null) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not create the teacher account.");
                }

                ServiceResult<Void> auditResult = auditLogService.logAction(connection, actorUserId,
                        "TEACHER_CREATE", "TEACHER", String.valueOf(teacherUserId),
                        null, buildTeacherJson(draft.fullName, draft.email, "ACTIVE"),
                        "Teacher account created by admin.");
                if (!auditResult.isSuccess()) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not create the teacher account.");
                }

                connection.commit();
                return ServiceResult.success("Teacher account saved.",
                        new TeacherCreateResult((int) teacherUserId, draft.fullName, draft.email, emailLogResult.getData()));
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not create the teacher account.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not create the teacher account.");
        }
    }

    private ServiceResult<TeacherProfile> sendTeacherPasswordEmail(TeacherCreateResult created, String temporaryPassword,
            boolean resetMode, String successPrefix, String warningMessage) {
        ResendEmailClient.EmailSendResult emailResult = resendEmailClient.sendTeacherPasswordEmail(
                created.email, created.fullName, temporaryPassword, resetMode);

        TeacherProfile teacherProfile = loadTeacherProfileOrFallback(created.teacherId, created.fullName, created.email);
        if (emailResult.isSuccess()) {
            emailDispatchService.markEmailSentQuietly(created.emailLogId, emailResult.getProviderMessageId());
            teacherProfile.setEmailStatus(EmailStatus.SENT);
            return ServiceResult.success(successPrefix + created.fullName + ".",
                    loadTeacherProfileOrFallback(created.teacherId, created.fullName, created.email));
        }

        emailDispatchService.markEmailFailedQuietly(created.emailLogId, emailResult.getMessage());
        teacherProfile.setEmailStatus(EmailStatus.FAILED);
        return ServiceResult.warning(warningMessage, teacherProfile);
    }

    private ServiceResult<TeacherPasswordChangeResult> saveTeacherPasswordChange(int actorUserId, int teacherId,
            String auditActionType, String auditNote) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (actorUserId <= 0) {
            return ServiceResult.failure("A valid admin account is required for this action.");
        }
        if (teacherId <= 0) {
            return ServiceResult.failure("Select a teacher first.");
        }

        String temporaryPassword = generateTemporaryPassword();
        String passwordHash = PasswordUtil.hashPassword(temporaryPassword);

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                TeacherAccountRow teacherRow = loadTeacherAccount(connection, teacherId);
                if (teacherRow == null) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Teacher not found.");
                }
                if (!"ACTIVE".equalsIgnoreCase(teacherRow.accountStatus)) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("This teacher account is not active.");
                }

                if (!updateTeacherPassword(connection, teacherId, passwordHash)) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Teacher password could not be updated.");
                }

                ServiceResult<Integer> emailLogResult = queueTeacherPasswordEmail(connection, teacherId, teacherRow.email, true);
                if (!emailLogResult.isSuccess() || emailLogResult.getData() == null) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not update the teacher password.");
                }

                ServiceResult<Void> auditResult = auditLogService.logAction(connection, actorUserId,
                        auditActionType, "TEACHER", String.valueOf(teacherId),
                        null, buildTeacherJson(teacherRow.fullName, teacherRow.email, teacherRow.accountStatus), auditNote);
                if (!auditResult.isSuccess()) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not update the teacher password.");
                }

                connection.commit();
                return ServiceResult.success("Teacher password saved.",
                        new TeacherPasswordChangeResult(teacherRow, temporaryPassword, emailLogResult.getData()));
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not update the teacher password.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the teacher password.");
        }
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

    private boolean updateTeacherPassword(Connection connection, int teacherId, String passwordHash) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(UPDATE_PASSWORD_SQL)) {
            update.setString(1, passwordHash);
            update.setInt(2, teacherId);
            return update.executeUpdate() > 0;
        }
    }

    private ServiceResult<Integer> queueTeacherPasswordEmail(Connection connection, int teacherId, String recipientEmail,
            boolean resetMode) {
        ServiceResult<ppb.qrattend.model.AppDomain.EmailDispatch> emailLogResult
                = emailDispatchService.queueTeacherPasswordEmail(connection, teacherId, recipientEmail, resetMode);
        if (!emailLogResult.isSuccess() || emailLogResult.getData() == null) {
            return ServiceResult.failure("Could not queue the teacher email.");
        }
        return ServiceResult.success("Teacher email queued.", emailLogResult.getData().getId());
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

    private static final class TeacherDraft {

        private final String fullName;
        private final String email;
        private final String temporaryPassword;
        private final String passwordHash;

        private TeacherDraft(String fullName, String email, String temporaryPassword, String passwordHash) {
            this.fullName = fullName;
            this.email = email;
            this.temporaryPassword = temporaryPassword;
            this.passwordHash = passwordHash;
        }
    }

    private static final class TeacherCreateResult {

        private final int teacherId;
        private final String fullName;
        private final String email;
        private final int emailLogId;

        private TeacherCreateResult(int teacherId, String fullName, String email, int emailLogId) {
            this.teacherId = teacherId;
            this.fullName = fullName;
            this.email = email;
            this.emailLogId = emailLogId;
        }
    }

    private static final class TeacherPasswordChangeResult {

        private final TeacherAccountRow teacher;
        private final String temporaryPassword;
        private final int emailLogId;

        private TeacherPasswordChangeResult(TeacherAccountRow teacher, String temporaryPassword, int emailLogId) {
            this.teacher = teacher;
            this.temporaryPassword = temporaryPassword;
            this.emailLogId = emailLogId;
        }
    }
}
