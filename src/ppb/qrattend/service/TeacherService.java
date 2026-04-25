package ppb.qrattend.service;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.db.PasswordUtil;
import ppb.qrattend.email.ResendEmailClient;
import ppb.qrattend.email.ResendEmailClient.EmailSendResult;
import ppb.qrattend.model.CoreModels.EmailStatus;
import ppb.qrattend.model.CoreModels.Teacher;

public final class TeacherService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private static final String SELECT_ALL_SQL = """
            SELECT
                u.user_id,
                u.full_name,
                u.email,
                u.is_active,
                COALESCE((
                    SELECT el.status
                    FROM email_logs el
                    WHERE el.related_user_id = u.user_id
                      AND el.email_type = 'TEACHER_PASSWORD'
                    ORDER BY el.email_id DESC
                    LIMIT 1
                ), 'NOT_SENT') AS email_status
            FROM users u
            WHERE u.role = 'TEACHER'
            ORDER BY u.full_name ASC
            """;

    private static final String SELECT_ONE_SQL = """
            SELECT
                u.user_id,
                u.full_name,
                u.email,
                u.is_active,
                COALESCE((
                    SELECT el.status
                    FROM email_logs el
                    WHERE el.related_user_id = u.user_id
                      AND el.email_type = 'TEACHER_PASSWORD'
                    ORDER BY el.email_id DESC
                    LIMIT 1
                ), 'NOT_SENT') AS email_status
            FROM users u
            WHERE u.user_id = ?
              AND u.role = 'TEACHER'
            LIMIT 1
            """;

    private static final String INSERT_SQL = """
            INSERT INTO users (full_name, email, password_hash, role, is_active, must_change_password)
            VALUES (?, ?, ?, 'TEACHER', 1, 1)
            """;

    private static final String UPDATE_PASSWORD_SQL = """
            UPDATE users
            SET password_hash = ?, must_change_password = 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
              AND role = 'TEACHER'
            """;

    private static final String UPDATE_TEACHER_SQL = """
            UPDATE users
            SET full_name = ?, email = ?, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
              AND role = 'TEACHER'
            """;

    private static final String DEACTIVATE_TEACHER_SQL = """
            UPDATE users
            SET is_active = 0, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
              AND role = 'TEACHER'
            """;

    private static final String CHANGE_PASSWORD_SQL =
            "UPDATE users SET password_hash = ?, must_change_password = 0, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";

    private final DatabaseManager databaseManager;
    private final ResendEmailClient resendEmailClient;
    private final EmailService emailService;

    public TeacherService() {
        this(DatabaseManager.fromDefaultConfig(), ResendEmailClient.createDefault(), new EmailService());
    }

    public TeacherService(DatabaseManager databaseManager, ResendEmailClient resendEmailClient, EmailService emailService) {
        this.databaseManager = databaseManager;
        this.resendEmailClient = resendEmailClient;
        this.emailService = emailService;
    }

    public ServiceResult<List<Teacher>> getTeachers() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<Teacher> teachers = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                teachers.add(mapTeacher(resultSet));
            }
            return ServiceResult.success("Teachers loaded.", teachers);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load teachers.");
        }
    }

    public ServiceResult<Teacher> findTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Choose a teacher first.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ONE_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ServiceResult.failure("Teacher not found.");
                }
                return ServiceResult.success("Teacher loaded.", mapTeacher(resultSet));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the teacher.");
        }
    }

    public ServiceResult<Teacher> createTeacherAccount(int actorUserId, String fullName, String email) {
        String cleanName = safe(fullName);
        String cleanEmail = normalizeEmail(email);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the teacher name.");
        }
        if (cleanEmail.isBlank()) {
            return ServiceResult.failure("Enter the teacher email.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String temporaryPassword = createTemporaryPassword();
        int teacherId;
        int emailLogId;

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                teacherId = insertTeacher(connection, cleanName, cleanEmail, temporaryPassword);
                emailLogId = emailService.createQueuedEmail(
                        connection,
                        "TEACHER_PASSWORD",
                        teacherId,
                        null,
                        cleanEmail,
                        "Your teacher account is ready",
                        "Teacher account created."
                );
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not save the teacher. The email may already be in use.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the teacher.");
        }

        ServiceResult<Teacher> teacherResult = findTeacher(teacherId);
        EmailSendResult emailResult = resendEmailClient.sendTeacherPasswordEmail(cleanEmail, cleanName, temporaryPassword, false);
        updateEmailLog(emailLogId, emailResult);

        if (emailResult.isSuccess()) {
            return teacherResult.isSuccess()
                    ? ServiceResult.success("Teacher added and password sent.", teacherResult.getData())
                    : ServiceResult.success("Teacher added and password sent.", null);
        }
        return teacherResult.isSuccess()
                ? ServiceResult.warning("Teacher added, but the password email could not be sent.", teacherResult.getData())
                : ServiceResult.warning("Teacher added, but the password email could not be sent.", null);
    }

    public ServiceResult<Teacher> updateTeacher(int teacherId, String fullName, String email) {
        String cleanName = safe(fullName);
        String cleanEmail = normalizeEmail(email);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the teacher name.");
        }
        if (cleanEmail.isBlank()) {
            return ServiceResult.failure("Enter the teacher email.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_TEACHER_SQL)) {
            statement.setString(1, cleanName);
            statement.setString(2, cleanEmail);
            statement.setInt(3, teacherId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            String state = ex.getSQLState();
            if ((msg != null && (msg.contains("Duplicate") || msg.contains("duplicate")))
                    || (state != null && state.startsWith("23"))) {
                return ServiceResult.failure("That email is already in use.");
            }
            return ServiceResult.failure("Could not update the teacher.");
        }
        return findTeacher(teacherId);
    }

    public ServiceResult<Void> deactivateTeacher(int teacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(DEACTIVATE_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            statement.executeUpdate();
            return ServiceResult.success("Teacher deactivated.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not deactivate the teacher.");
        }
    }

    public ServiceResult<Void> changePassword(int teacherId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            return ServiceResult.failure("Password must be at least 8 characters.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(CHANGE_PASSWORD_SQL)) {
            statement.setString(1, PasswordUtil.hashPassword(newPassword));
            statement.setInt(2, teacherId);
            statement.executeUpdate();
            return ServiceResult.success("Password changed.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not change the password.");
        }
    }

    public ServiceResult<Void> resendTeacherPassword(int actorUserId, int teacherId) {
        return rotateTeacherPassword(teacherId, true);
    }

    public ServiceResult<Void> resetTeacherPassword(int actorUserId, int teacherId) {
        return rotateTeacherPassword(teacherId, false);
    }

    private ServiceResult<Void> rotateTeacherPassword(int teacherId, boolean resendMode) {
        ServiceResult<Teacher> teacherResult = findTeacher(teacherId);
        if (!teacherResult.isSuccess() || teacherResult.getData() == null) {
            return ServiceResult.failure("Teacher not found.");
        }

        Teacher teacher = teacherResult.getData();
        String temporaryPassword = createTemporaryPassword();
        int emailLogId;

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD_SQL)) {
                    statement.setString(1, PasswordUtil.hashPassword(temporaryPassword));
                    statement.setInt(2, teacherId);
                    statement.executeUpdate();
                }
                emailLogId = emailService.createQueuedEmail(
                        connection,
                        "TEACHER_PASSWORD",
                        teacherId,
                        null,
                        teacher.email(),
                        resendMode ? "Your teacher password was sent again" : "Your teacher password was reset",
                        resendMode ? "Password sent again." : "Password reset."
                );
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not update the teacher password.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the teacher password.");
        }

        EmailSendResult emailResult = resendEmailClient.sendTeacherPasswordEmail(
                teacher.email(),
                teacher.fullName(),
                temporaryPassword,
                true
        );
        updateEmailLog(emailLogId, emailResult);
        if (emailResult.isSuccess()) {
            return ServiceResult.success("Password email sent.", null);
        }
        return ServiceResult.warning("Password updated, but the email could not be sent.", null);
    }

    private int insertTeacher(Connection connection, String fullName, String email, String temporaryPassword) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.setString(3, PasswordUtil.hashPassword(temporaryPassword));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Teacher key was not returned.");
                }
                return keys.getInt(1);
            }
        }
    }

    private void updateEmailLog(int emailLogId, EmailSendResult emailResult) {
        try (Connection connection = databaseManager.openConnection()) {
            if (emailResult.isSuccess()) {
                emailService.markSent(connection, emailLogId, emailResult.getProviderMessageId());
            } else {
                emailService.markFailed(connection, emailLogId, emailResult.getMessage());
            }
        } catch (SQLException ignored) {
        }
    }

    private Teacher mapTeacher(ResultSet resultSet) throws SQLException {
        return new Teacher(
                resultSet.getInt("user_id"),
                resultSet.getString("full_name"),
                resultSet.getString("email"),
                resultSet.getBoolean("is_active"),
                mapEmailStatus(resultSet.getString("email_status"))
        );
    }

    private EmailStatus mapEmailStatus(String raw) {
        if (raw == null) {
            return EmailStatus.NOT_SENT;
        }
        String upper = raw.trim().toUpperCase();
        if ("SENT".equals(upper)) {
            return EmailStatus.SENT;
        } else if ("FAILED".equals(upper)) {
            return EmailStatus.FAILED;
        } else {
            return EmailStatus.NOT_SENT;
        }
    }

    private String createTemporaryPassword() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            builder.append(PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return builder.toString();
    }

    private String normalizeEmail(String value) {
        return safe(value).toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
