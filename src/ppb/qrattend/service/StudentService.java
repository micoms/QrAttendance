package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.db.SecurityUtil;
import ppb.qrattend.email.ResendEmailClient;
import ppb.qrattend.email.ResendEmailClient.EmailSendResult;
import ppb.qrattend.model.CoreModels.EmailStatus;
import ppb.qrattend.model.CoreModels.RequestStatus;
import ppb.qrattend.model.CoreModels.Student;
import ppb.qrattend.model.CoreModels.StudentRemovalRequest;

public final class StudentService {

    private static final String SELECT_ALL_SQL = """
            SELECT
                s.student_id,
                s.student_code,
                s.full_name,
                s.email,
                s.section_id,
                sec.section_name,
                s.is_active,
                s.qr_status
            FROM students s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            ORDER BY sec.section_name ASC, s.full_name ASC
            """;

    private static final String SELECT_BY_SECTION_SQL = """
            SELECT
                s.student_id,
                s.student_code,
                s.full_name,
                s.email,
                s.section_id,
                sec.section_name,
                s.is_active,
                s.qr_status
            FROM students s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            WHERE s.section_id = ?
            ORDER BY s.full_name ASC
            """;

    private static final String SELECT_FOR_TEACHER_SQL = """
            SELECT DISTINCT
                s.student_id,
                s.student_code,
                s.full_name,
                s.email,
                s.section_id,
                sec.section_name,
                s.is_active,
                s.qr_status
            FROM schedules sc
            INNER JOIN students s
                ON s.section_id = sc.section_id
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            WHERE sc.teacher_user_id = ?
              AND sc.is_active = 1
              AND s.is_active = 1
            ORDER BY sec.section_name ASC, s.full_name ASC
            """;

    private static final String SELECT_ONE_SQL = """
            SELECT
                s.student_id,
                s.student_code,
                s.full_name,
                s.email,
                s.section_id,
                sec.section_name,
                s.is_active,
                s.qr_status
            FROM students s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            WHERE s.student_id = ?
            LIMIT 1
            """;

    private static final String SELECT_ONE_BY_CODE_SQL = """
            SELECT
                s.student_id,
                s.student_code,
                s.full_name,
                s.email,
                s.section_id,
                sec.section_name,
                s.is_active,
                s.qr_status
            FROM students s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            WHERE UPPER(s.student_code) = ?
            LIMIT 1
            """;

    private static final String INSERT_STUDENT_SQL = """
            INSERT INTO students
                (section_id, student_code, full_name, email, qr_hash, qr_status, is_active)
            VALUES (?, ?, ?, ?, ?, 'NOT_SENT', 1)
            """;

    private static final String UPDATE_QR_SQL = """
            UPDATE students
            SET qr_hash = ?, qr_status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE student_id = ?
            """;

    private static final String SELECT_TEACHER_SECTIONS_SQL = """
            SELECT DISTINCT sec.section_name
            FROM schedules sc
            INNER JOIN sections sec
                ON sec.section_id = sc.section_id
            WHERE sc.teacher_user_id = ?
              AND sc.is_active = 1
            ORDER BY sec.section_name ASC
            """;

    private static final String INSERT_REMOVAL_REQUEST_SQL = """
            INSERT INTO student_removal_requests
                (teacher_user_id, student_id, reason, status)
            VALUES (?, ?, ?, 'PENDING')
            """;

    private static final String SELECT_ALL_REMOVAL_REQUESTS_SQL = """
            SELECT
                r.request_id,
                r.teacher_user_id,
                t.full_name AS teacher_name,
                s.student_id,
                s.student_code,
                s.full_name AS student_name,
                sec.section_name,
                r.reason,
                r.status,
                reviewer.full_name AS reviewed_by,
                r.created_at,
                r.reviewed_at
            FROM student_removal_requests r
            INNER JOIN users t
                ON t.user_id = r.teacher_user_id
            INNER JOIN students s
                ON s.student_id = r.student_id
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = r.reviewed_by_user_id
            ORDER BY
                CASE r.status
                    WHEN 'PENDING' THEN 0
                    WHEN 'APPROVED' THEN 1
                    ELSE 2
                END,
                r.request_id DESC
            """;

    private static final String SELECT_TEACHER_REMOVAL_REQUESTS_SQL = """
            SELECT
                r.request_id,
                r.teacher_user_id,
                t.full_name AS teacher_name,
                s.student_id,
                s.student_code,
                s.full_name AS student_name,
                sec.section_name,
                r.reason,
                r.status,
                reviewer.full_name AS reviewed_by,
                r.created_at,
                r.reviewed_at
            FROM student_removal_requests r
            INNER JOIN users t
                ON t.user_id = r.teacher_user_id
            INNER JOIN students s
                ON s.student_id = r.student_id
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = r.reviewed_by_user_id
            WHERE r.teacher_user_id = ?
            ORDER BY r.request_id DESC
            """;

    private static final String SELECT_REMOVAL_REQUEST_SQL = """
            SELECT
                r.request_id,
                r.teacher_user_id,
                t.full_name AS teacher_name,
                s.student_id,
                s.student_code,
                s.full_name AS student_name,
                sec.section_name,
                r.reason,
                r.status,
                reviewer.full_name AS reviewed_by,
                r.created_at,
                r.reviewed_at
            FROM student_removal_requests r
            INNER JOIN users t
                ON t.user_id = r.teacher_user_id
            INNER JOIN students s
                ON s.student_id = r.student_id
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = r.reviewed_by_user_id
            WHERE r.request_id = ?
            LIMIT 1
            """;

    private static final String UPDATE_REMOVAL_REQUEST_SQL = """
            UPDATE student_removal_requests
            SET status = ?, reviewed_by_user_id = ?, reviewed_at = CURRENT_TIMESTAMP
            WHERE request_id = ?
            """;

    private static final String UPDATE_STUDENT_SQL = """
            UPDATE students
            SET section_id = ?, student_code = ?, full_name = ?, email = ?, updated_at = CURRENT_TIMESTAMP
            WHERE student_id = ?
            """;

    private static final String DEACTIVATE_STUDENT_SQL = """
            UPDATE students
            SET is_active = 0, updated_at = CURRENT_TIMESTAMP
            WHERE student_id = ?
            """;

    private final DatabaseManager databaseManager;
    private final ResendEmailClient resendEmailClient;
    private final EmailService emailService;

    public StudentService() {
        this(DatabaseManager.fromDefaultConfig(), ResendEmailClient.createDefault(), new EmailService());
    }

    public StudentService(DatabaseManager databaseManager, ResendEmailClient resendEmailClient, EmailService emailService) {
        this.databaseManager = databaseManager;
        this.resendEmailClient = resendEmailClient;
        this.emailService = emailService;
    }

    public ServiceResult<List<Student>> getAllStudents() {
        return loadStudents(SELECT_ALL_SQL, null);
    }

    public ServiceResult<List<Student>> getStudentsBySection(int sectionId) {
        return loadStudents(SELECT_BY_SECTION_SQL, sectionId);
    }

    public ServiceResult<List<Student>> getStudentsForTeacher(int teacherId) {
        return loadStudents(SELECT_FOR_TEACHER_SQL, teacherId);
    }

    public ServiceResult<Student> findStudent(int studentId) {
        return loadOneStudent(SELECT_ONE_SQL, studentId);
    }

    public ServiceResult<Student> findStudentByCode(String studentCode) {
        String cleanCode = normalizeCode(studentCode);
        if (cleanCode.isBlank()) {
            return ServiceResult.failure("Student not found.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ONE_BY_CODE_SQL)) {
            statement.setString(1, cleanCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ServiceResult.failure("Student not found.");
                }
                return ServiceResult.success("Student loaded.", mapStudent(resultSet));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the student.");
        }
    }

    public ServiceResult<Student> addStudent(int sectionId, String studentCode, String fullName, String email) {
        String cleanCode = normalizeCode(studentCode);
        String cleanName = safe(fullName);
        String cleanEmail = normalizeEmail(email);
        if (sectionId <= 0) {
            return ServiceResult.failure("Choose the section first.");
        }
        if (cleanCode.isBlank() || cleanName.isBlank() || cleanEmail.isBlank()) {
            return ServiceResult.failure("Complete the student ID, name, and email.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String qrToken = SecurityUtil.generateOpaqueToken();
        String qrHash = SecurityUtil.sha256Hex(qrToken);
        int studentId;

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_STUDENT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, sectionId);
                statement.setString(2, cleanCode);
                statement.setString(3, cleanName);
                statement.setString(4, cleanEmail);
                statement.setString(5, qrHash);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        connection.rollback();
                        return ServiceResult.failure("Could not save the student.");
                    }
                    studentId = keys.getInt(1);
                }
                int emailLogId = emailService.createQueuedEmail(
                        connection,
                        "STUDENT_QR",
                        null,
                        studentId,
                        cleanEmail,
                        "Your school QR code is ready",
                        "Student QR email queued."
                );
                connection.commit();

                EmailSendResult emailResult = resendEmailClient.sendStudentQrEmail(cleanEmail, cleanName, cleanCode, qrToken, false);
                updateStudentEmailResult(studentId, emailLogId, emailResult);
                ServiceResult<Student> studentResult = findStudent(studentId);
                if (emailResult.isSuccess()) {
                    return studentResult.isSuccess()
                            ? ServiceResult.success("Student saved and QR sent.", studentResult.getData())
                            : ServiceResult.success("Student saved and QR sent.", null);
                }
                return studentResult.isSuccess()
                        ? ServiceResult.warning("Student saved, but the QR email could not be sent.", studentResult.getData())
                        : ServiceResult.warning("Student saved, but the QR email could not be sent.", null);
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not save the student. The ID or email may already be in use.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the student.");
        }
    }

    public ServiceResult<String> importStudents(int sectionId, String pastedRows) {
        String cleanRows = safe(pastedRows);
        if (sectionId <= 0) {
            return ServiceResult.failure("Choose the section first.");
        }
        if (cleanRows.isBlank()) {
            return ServiceResult.failure("Paste at least one student row.");
        }

        String[] lines = cleanRows.split("\\r?\\n");
        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        for (String line : lines) {
            if (safe(line).isBlank()) {
                continue;
            }
            String[] parts = splitStudentLine(line);
            if (parts.length < 3) {
                failedCount++;
                errors.add("Skip: " + line);
                continue;
            }
            ServiceResult<Student> result = addStudent(sectionId, parts[0], parts[1], parts[2]);
            if (result.isSuccess() || result.isWarning()) {
                successCount++;
            } else {
                failedCount++;
                errors.add(parts[0] + ": " + result.getMessage());
            }
        }

        if (successCount == 0) {
            return ServiceResult.failure("No students were imported.");
        }

        StringBuilder message = new StringBuilder();
        message.append("Imported ").append(successCount).append(" student");
        if (successCount != 1) {
            message.append('s');
        }
        if (failedCount > 0) {
            message.append(". ").append(failedCount).append(" row");
            if (failedCount != 1) {
                message.append('s');
            }
            message.append(" were skipped.");
            return ServiceResult.warning(message.toString(), String.join(System.lineSeparator(), errors));
        }
        return ServiceResult.success(message.toString() + ".", "");
    }

    public ServiceResult<Void> resendStudentQr(int studentId) {
        ServiceResult<Student> studentResult = findStudent(studentId);
        if (!studentResult.isSuccess() || studentResult.getData() == null) {
            return ServiceResult.failure("Student not found.");
        }
        Student student = studentResult.getData();
        String qrToken = SecurityUtil.generateOpaqueToken();
        String qrHash = SecurityUtil.sha256Hex(qrToken);

        int emailLogId;
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_QR_SQL)) {
                    statement.setString(1, qrHash);
                    statement.setString(2, "NOT_SENT");
                    statement.setInt(3, student.id());
                    statement.executeUpdate();
                }
                emailLogId = emailService.createQueuedEmail(
                        connection,
                        "STUDENT_QR",
                        null,
                        student.id(),
                        student.email(),
                        "Your school QR code was sent again",
                        "Student QR email sent again."
                );
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not send the QR code again.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not send the QR code again.");
        }

        EmailSendResult emailResult = resendEmailClient.sendStudentQrEmail(
                student.email(),
                student.fullName(),
                student.studentCode(),
                qrToken,
                true
        );
        updateStudentEmailResult(student.id(), emailLogId, emailResult);
        if (emailResult.isSuccess()) {
            return ServiceResult.success("QR code sent again.", null);
        }
        return ServiceResult.warning("The QR code was updated, but the email could not be sent.", null);
    }

    public ServiceResult<Student> updateStudent(int studentId, int sectionId, String studentCode, String fullName, String email) {
        String cleanCode = normalizeCode(studentCode);
        String cleanName = safe(fullName);
        String cleanEmail = normalizeEmail(email);
        if (studentId <= 0) {
            return ServiceResult.failure("Student not found.");
        }
        if (sectionId <= 0) {
            return ServiceResult.failure("Choose the section first.");
        }
        if (cleanCode.isBlank() || cleanName.isBlank() || cleanEmail.isBlank()) {
            return ServiceResult.failure("Complete the student ID, name, and email.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_STUDENT_SQL)) {
            statement.setInt(1, sectionId);
            statement.setString(2, cleanCode);
            statement.setString(3, cleanName);
            statement.setString(4, cleanEmail);
            statement.setInt(5, studentId);
            statement.executeUpdate();
            ServiceResult<Student> studentResult = findStudent(studentId);
            return studentResult.isSuccess()
                    ? ServiceResult.success("Student updated.", studentResult.getData())
                    : ServiceResult.success("Student updated.", null);
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            String state = ex.getSQLState();
            if ((msg != null && (msg.contains("Duplicate") || msg.contains("duplicate")))
                    || (state != null && state.startsWith("23"))) {
                return ServiceResult.failure("That student ID or email is already in use.");
            }
            return ServiceResult.failure("Could not update the student.");
        }
    }

    public ServiceResult<Void> deactivateStudent(int studentId) {
        if (studentId <= 0) {
            return ServiceResult.failure("Student not found.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(DEACTIVATE_STUDENT_SQL)) {
            statement.setInt(1, studentId);
            statement.executeUpdate();
            return ServiceResult.success("Student deactivated.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not deactivate the student.");
        }
    }

    public ServiceResult<List<String>> getTeacherSectionNames(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher not found.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        Set<String> names = new LinkedHashSet<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_TEACHER_SECTIONS_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString("section_name"));
                }
            }
            return ServiceResult.success("Teacher sections loaded.", new ArrayList<>(names));
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load class sections.");
        }
    }

    public ServiceResult<Void> submitStudentRemovalRequest(int teacherId, int studentId, String reason) {
        String cleanReason = safe(reason);
        if (teacherId <= 0 || studentId <= 0) {
            return ServiceResult.failure("Choose the student first.");
        }
        if (cleanReason.isBlank()) {
            return ServiceResult.failure("Enter a short reason.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_REMOVAL_REQUEST_SQL)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, studentId);
            statement.setString(3, cleanReason);
            statement.executeUpdate();
            return ServiceResult.success("Removal request sent to the admin.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not send the removal request.");
        }
    }

    public ServiceResult<List<StudentRemovalRequest>> getStudentRemovalRequests() {
        return loadRemovalRequests(SELECT_ALL_REMOVAL_REQUESTS_SQL, null);
    }

    public ServiceResult<List<StudentRemovalRequest>> getStudentRemovalRequestsForTeacher(int teacherId) {
        return loadRemovalRequests(SELECT_TEACHER_REMOVAL_REQUESTS_SQL, teacherId);
    }

    public ServiceResult<Void> reviewStudentRemovalRequest(int reviewerId, int requestId, boolean approve) {
        if (reviewerId <= 0 || requestId <= 0) {
            return ServiceResult.failure("Choose a request first.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(SELECT_REMOVAL_REQUEST_SQL)) {
                select.setInt(1, requestId);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        return ServiceResult.failure("Request not found.");
                    }
                    StudentRemovalRequest request = mapRemovalRequest(resultSet);
                    if (request.status() != RequestStatus.PENDING) {
                        connection.rollback();
                        return ServiceResult.failure("This request was already reviewed.");
                    }
                    try (PreparedStatement update = connection.prepareStatement(UPDATE_REMOVAL_REQUEST_SQL)) {
                        update.setString(1, approve ? "APPROVED" : "REJECTED");
                        update.setInt(2, reviewerId);
                        update.setInt(3, requestId);
                        update.executeUpdate();
                    }
                    if (approve) {
                        try (PreparedStatement deactivate = connection.prepareStatement(DEACTIVATE_STUDENT_SQL)) {
                            deactivate.setInt(1, request.studentId());
                            deactivate.executeUpdate();
                        }
                    }
                    connection.commit();
                    return ServiceResult.success(approve ? "Student removed from the class list." : "Request rejected.", null);
                }
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not review the request.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not review the request.");
        }
    }

    private ServiceResult<List<Student>> loadStudents(String sql, Integer value) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<Student> students = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value != null) {
                statement.setInt(1, value);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    students.add(mapStudent(resultSet));
                }
            }
            return ServiceResult.success("Students loaded.", students);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load students.");
        }
    }

    private ServiceResult<Student> loadOneStudent(String sql, int value) {
        if (value <= 0) {
            return ServiceResult.failure("Student not found.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ServiceResult.failure("Student not found.");
                }
                return ServiceResult.success("Student loaded.", mapStudent(resultSet));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the student.");
        }
    }

    private ServiceResult<List<StudentRemovalRequest>> loadRemovalRequests(String sql, Integer teacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<StudentRemovalRequest> requests = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (teacherId != null) {
                statement.setInt(1, teacherId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(mapRemovalRequest(resultSet));
                }
            }
            return ServiceResult.success("Removal requests loaded.", requests);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load removal requests.");
        }
    }

    private void updateStudentEmailResult(int studentId, int emailLogId, EmailSendResult emailResult) {
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE students
                    SET qr_status = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE student_id = ?
                    """)) {
                statement.setString(1, emailResult.isSuccess() ? "SENT" : "FAILED");
                statement.setInt(2, studentId);
                statement.executeUpdate();
                if (emailResult.isSuccess()) {
                    emailService.markSent(connection, emailLogId, emailResult.getProviderMessageId());
                } else {
                    emailService.markFailed(connection, emailLogId, emailResult.getMessage());
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ignored) {
        }
    }

    private Student mapStudent(ResultSet resultSet) throws SQLException {
        return new Student(
                resultSet.getInt("student_id"),
                resultSet.getString("student_code"),
                resultSet.getString("full_name"),
                resultSet.getString("email"),
                resultSet.getInt("section_id"),
                resultSet.getString("section_name"),
                resultSet.getBoolean("is_active"),
                mapEmailStatus(resultSet.getString("qr_status"))
        );
    }

    private StudentRemovalRequest mapRemovalRequest(ResultSet resultSet) throws SQLException {
        return new StudentRemovalRequest(
                resultSet.getInt("request_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getString("teacher_name"),
                resultSet.getInt("student_id"),
                resultSet.getString("student_code"),
                resultSet.getString("student_name"),
                resultSet.getString("section_name"),
                resultSet.getString("reason"),
                mapRequestStatus(resultSet.getString("status")),
                safe(resultSet.getString("reviewed_by")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("reviewed_at") == null ? null : resultSet.getTimestamp("reviewed_at").toLocalDateTime()
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

    private RequestStatus mapRequestStatus(String raw) {
        if (raw == null) {
            return RequestStatus.PENDING;
        }
        String upper = raw.trim().toUpperCase();
        if ("APPROVED".equals(upper)) {
            return RequestStatus.APPROVED;
        } else if ("REJECTED".equals(upper)) {
            return RequestStatus.REJECTED;
        } else {
            return RequestStatus.PENDING;
        }
    }

    private String[] splitStudentLine(String line) {
        String clean = safe(line);
        if (clean.contains("\t")) {
            String[] parts = clean.split("\\t");
            return normalizeParts(parts);
        }
        String[] parts = clean.split(",", 3);
        return normalizeParts(parts);
    }

    private String[] normalizeParts(String[] parts) {
        if (parts.length < 3) {
            return parts;
        }
        return new String[]{safe(parts[0]), safe(parts[1]), normalizeEmail(parts[2])};
    }

    private String normalizeCode(String value) {
        return safe(value).toUpperCase();
    }

    private String normalizeEmail(String value) {
        return safe(value).toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
