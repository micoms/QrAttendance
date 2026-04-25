package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.db.SecurityUtil;
import ppb.qrattend.email.ResendEmailClient;
import ppb.qrattend.model.AppDomain.EmailStatus;
import ppb.qrattend.model.AppDomain.ScheduleRequestStatus;
import ppb.qrattend.model.AppDomain.StudentProfile;
import ppb.qrattend.model.AppDomain.StudentRemovalRequest;

public final class StudentService {

    private static final String SELECT_STUDENTS_FOR_TEACHER_SQL = """
            SELECT
                sp.student_pk,
                sp.student_code,
                sp.section_name,
                sp.full_name,
                sp.email,
                sp.qr_status,
                sp.created_at
            FROM teacher_student_assignments tsa
            INNER JOIN student_profiles sp
                ON sp.student_pk = tsa.student_pk
            INNER JOIN users u
                ON u.user_id = tsa.teacher_user_id
            WHERE tsa.teacher_user_id = ?
              AND tsa.assignment_status = 'ACTIVE'
              AND sp.account_status = 'ACTIVE'
              AND u.role = 'TEACHER'
            ORDER BY sp.section_name ASC, sp.full_name ASC
            """;

    private static final String SELECT_ALL_STUDENTS_SQL = """
            SELECT
                sp.student_pk,
                sp.student_code,
                sp.section_name,
                sp.full_name,
                sp.email,
                sp.qr_status,
                sp.created_at,
                tsa.teacher_user_id
            FROM student_profiles sp
            LEFT JOIN teacher_student_assignments tsa
                ON tsa.student_pk = sp.student_pk
               AND tsa.assignment_status = 'ACTIVE'
            WHERE sp.account_status = 'ACTIVE'
            ORDER BY sp.section_name ASC, sp.full_name ASC
            """;

    private static final String SELECT_STUDENT_FOR_TEACHER_SQL = """
            SELECT
                sp.student_pk,
                sp.student_code,
                sp.section_name,
                sp.full_name,
                sp.email,
                sp.qr_status,
                sp.created_at
            FROM teacher_student_assignments tsa
            INNER JOIN student_profiles sp
                ON sp.student_pk = tsa.student_pk
            INNER JOIN users u
                ON u.user_id = tsa.teacher_user_id
            WHERE tsa.teacher_user_id = ?
              AND sp.student_code = ?
              AND tsa.assignment_status = 'ACTIVE'
              AND sp.account_status = 'ACTIVE'
              AND u.role = 'TEACHER'
            LIMIT 1
            """;

    private static final String SELECT_ACTIVE_QR_SQL = """
            SELECT
                sp.student_pk,
                sp.student_code,
                sp.section_name,
                sp.full_name,
                sp.email,
                sp.qr_status,
                sqt.qr_id
            FROM teacher_student_assignments tsa
            INNER JOIN student_profiles sp
                ON sp.student_pk = tsa.student_pk
            INNER JOIN users u
                ON u.user_id = tsa.teacher_user_id
            INNER JOIN student_qr_tokens sqt
                ON sqt.student_pk = sp.student_pk
               AND sqt.is_active = 1
               AND sqt.token_type = 'PERMANENT'
            WHERE tsa.teacher_user_id = ?
              AND sp.student_code = ?
              AND tsa.assignment_status = 'ACTIVE'
              AND sp.account_status = 'ACTIVE'
              AND u.role = 'TEACHER'
            ORDER BY sqt.qr_id DESC
            LIMIT 1
            """;

    private static final String SELECT_ACTIVE_TEACHER_SQL = """
            SELECT user_id, full_name, email, account_status
            FROM users
            WHERE user_id = ?
              AND role = 'TEACHER'
            LIMIT 1
            """;

    private static final String SELECT_ACTIVE_ADMIN_SQL = """
            SELECT user_id, full_name, email, account_status
            FROM users
            WHERE user_id = ?
              AND role = 'ADMIN'
            LIMIT 1
            """;

    private static final String CHECK_STUDENT_CODE_SQL = """
            SELECT 1
            FROM student_profiles
            WHERE student_code = ?
            LIMIT 1
            """;

    private static final String CHECK_STUDENT_EMAIL_SQL = """
            SELECT 1
            FROM student_profiles
            WHERE email = ?
            LIMIT 1
            """;

    private static final String INSERT_STUDENT_SQL = """
            INSERT INTO student_profiles
                (student_code, full_name, email, section_name, qr_status, account_status, created_by_teacher_id, managed_by_admin_id)
            VALUES (?, ?, ?, ?, 'QUEUED', 'ACTIVE', ?, ?)
            """;

    private static final String INSERT_ASSIGNMENT_SQL = """
            INSERT INTO teacher_student_assignments
                (teacher_user_id, student_pk, assignment_status)
            VALUES (?, ?, 'ACTIVE')
            """;

    private static final String INSERT_QR_TOKEN_SQL = """
            INSERT INTO student_qr_tokens
                (student_pk, token_hash, token_type, is_active)
            VALUES (?, ?, 'PERMANENT', 1)
            """;

    private static final String DEACTIVATE_QR_TOKENS_SQL = """
            UPDATE student_qr_tokens
            SET is_active = 0
            WHERE student_pk = ?
              AND is_active = 1
            """;

    private static final String UPDATE_STUDENT_QR_STATUS_SQL = """
            UPDATE student_profiles
            SET qr_status = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE student_pk = ?
            """;

    private static final String UPDATE_QR_EMAILED_AT_SQL = """
            UPDATE student_qr_tokens
            SET emailed_at = CURRENT_TIMESTAMP
            WHERE qr_id = ?
            """;

    private static final String CHECK_PENDING_REMOVAL_REQUEST_SQL = """
            SELECT 1
            FROM student_roster_change_requests
            WHERE teacher_user_id = ?
              AND student_pk = ?
              AND request_type = 'REMOVE_FROM_LIST'
              AND request_status = 'PENDING'
            LIMIT 1
            """;

    private static final String INSERT_REMOVAL_REQUEST_SQL = """
            INSERT INTO student_roster_change_requests
                (teacher_user_id, student_pk, request_type, request_status, reason)
            VALUES (?, ?, 'REMOVE_FROM_LIST', 'PENDING', ?)
            """;

    private static final String SELECT_REMOVAL_REQUESTS_SQL = """
            SELECT
                r.removal_request_id,
                r.teacher_user_id,
                t.full_name AS teacher_name,
                sp.student_pk,
                sp.student_code,
                sp.full_name AS student_name,
                sp.section_name,
                r.reason,
                r.request_status,
                reviewer.full_name AS reviewed_by_name,
                r.reviewed_at,
                r.created_at
            FROM student_roster_change_requests r
            INNER JOIN users t
                ON t.user_id = r.teacher_user_id
            INNER JOIN student_profiles sp
                ON sp.student_pk = r.student_pk
            LEFT JOIN users reviewer
                ON reviewer.user_id = r.reviewed_by_user_id
            ORDER BY
                CASE r.request_status
                    WHEN 'PENDING' THEN 0
                    WHEN 'APPROVED' THEN 1
                    ELSE 2
                END,
                r.created_at DESC,
                r.removal_request_id DESC
            """;

    private static final String SELECT_REMOVAL_REQUESTS_FOR_TEACHER_SQL = """
            SELECT
                r.removal_request_id,
                r.teacher_user_id,
                t.full_name AS teacher_name,
                sp.student_pk,
                sp.student_code,
                sp.full_name AS student_name,
                sp.section_name,
                r.reason,
                r.request_status,
                reviewer.full_name AS reviewed_by_name,
                r.reviewed_at,
                r.created_at
            FROM student_roster_change_requests r
            INNER JOIN users t
                ON t.user_id = r.teacher_user_id
            INNER JOIN student_profiles sp
                ON sp.student_pk = r.student_pk
            LEFT JOIN users reviewer
                ON reviewer.user_id = r.reviewed_by_user_id
            WHERE r.teacher_user_id = ?
            ORDER BY r.created_at DESC, r.removal_request_id DESC
            """;

    private static final String SELECT_REMOVAL_REQUEST_BY_ID_SQL = """
            SELECT
                r.removal_request_id,
                r.teacher_user_id,
                t.full_name AS teacher_name,
                sp.student_pk,
                sp.student_code,
                sp.full_name AS student_name,
                sp.section_name,
                r.reason,
                r.request_status,
                reviewer.full_name AS reviewed_by_name,
                r.reviewed_at,
                r.created_at
            FROM student_roster_change_requests r
            INNER JOIN users t
                ON t.user_id = r.teacher_user_id
            INNER JOIN student_profiles sp
                ON sp.student_pk = r.student_pk
            LEFT JOIN users reviewer
                ON reviewer.user_id = r.reviewed_by_user_id
            WHERE r.removal_request_id = ?
            LIMIT 1
            """;

    private static final String UPDATE_REMOVAL_REQUEST_SQL = """
            UPDATE student_roster_change_requests
            SET request_status = ?,
                reviewed_by_user_id = ?,
                reviewed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE removal_request_id = ?
            """;

    private static final String DEACTIVATE_ASSIGNMENT_SQL = """
            UPDATE teacher_student_assignments
            SET assignment_status = 'INACTIVE'
            WHERE teacher_user_id = ?
              AND student_pk = ?
              AND assignment_status = 'ACTIVE'
            """;

    private final DatabaseManager databaseManager;
    private final ResendEmailClient resendEmailClient;
    private final AuditLogService auditLogService;
    private final EmailDispatchService emailDispatchService;

    public StudentService() {
        this(DatabaseManager.fromDefaultConfig(), ResendEmailClient.createDefault());
    }

    public StudentService(DatabaseManager databaseManager, ResendEmailClient resendEmailClient) {
        this(databaseManager, resendEmailClient, new AuditLogService(databaseManager), new EmailDispatchService(databaseManager));
    }

    public StudentService(DatabaseManager databaseManager, ResendEmailClient resendEmailClient,
            AuditLogService auditLogService, EmailDispatchService emailDispatchService) {
        this.databaseManager = databaseManager;
        this.resendEmailClient = resendEmailClient;
        this.auditLogService = auditLogService;
        this.emailDispatchService = emailDispatchService;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<List<StudentProfile>> getStudentsForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        List<StudentProfile> students = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_STUDENTS_FOR_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    students.add(mapStudentProfile(resultSet, teacherId));
                }
            }
            return ServiceResult.success("Loaded the teacher roster from MariaDB.", students);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load students: " + ex.getMessage());
        }
    }

    public ServiceResult<List<StudentProfile>> getAllStudents() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        List<StudentProfile> students = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ALL_STUDENTS_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                students.add(mapStudentProfile(resultSet, resultSet.getInt("teacher_user_id")));
            }
            return ServiceResult.success("Loaded all active students from MariaDB.", students);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load all students: " + ex.getMessage());
        }
    }

    public ServiceResult<StudentProfile> findStudentForTeacher(int teacherId, String studentCode) {
        String normalizedStudentCode = normalizeStudentCode(studentCode);
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (normalizedStudentCode.isBlank()) {
            return ServiceResult.failure("Student ID is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_STUDENT_FOR_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            statement.setString(2, normalizedStudentCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ServiceResult.failure("Student not found for this teacher.");
                }
                return ServiceResult.success("Loaded student from MariaDB.", mapStudentProfile(resultSet, teacherId));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load student: " + ex.getMessage());
        }
    }

    public ServiceResult<StudentProfile> createStudentProfile(int teacherId, String studentCode, String fullName, String email) {
        return ServiceResult.failure("Student accounts are now managed by admin by section.");
    }

    public ServiceResult<StudentProfile> createStudentProfileByAdmin(int actorUserId, int teacherId, String sectionName,
            String studentCode, String fullName, String email) {
        ServiceResult<StudentCreateDraft> draftResult = prepareStudentCreateDraft(
                actorUserId, teacherId, sectionName, studentCode, fullName, email);
        if (!draftResult.isSuccess() || draftResult.getData() == null) {
            return ServiceResult.failure(draftResult.getMessage());
        }

        StudentCreateDraft draft = draftResult.getData();
        ServiceResult<StudentCreateResult> saveResult = saveStudentProfileByAdmin(draft);
        if (!saveResult.isSuccess() || saveResult.getData() == null) {
            return ServiceResult.failure(saveResult.getMessage());
        }

        return sendStudentQrEmail(saveResult.getData(), draft.qrToken, false,
                "Student added to " + draft.sectionName + " and QR email sent.",
                "Student was added, but the QR email could not be sent yet.");
    }

    public ServiceResult<Void> resendStudentQr(int teacherId, String studentCode) {
        ServiceResult<StudentQrRefreshResult> refreshResult = saveStudentQrRefresh(teacherId, studentCode);
        if (!refreshResult.isSuccess() || refreshResult.getData() == null) {
            return ServiceResult.failure(refreshResult.getMessage());
        }

        StudentQrRefreshResult refresh = refreshResult.getData();
        ResendEmailClient.EmailSendResult emailResult = resendEmailClient.sendStudentQrEmail(
                refresh.email, refresh.fullName, refresh.studentCode, refresh.qrToken, true);

        if (emailResult.isSuccess()) {
            emailDispatchService.markEmailSentQuietly(refresh.emailLogId, emailResult.getProviderMessageId());
            updateStudentQrStatus(refresh.studentPk, EmailStatus.SENT);
            updateQrTokenEmailedAt(refresh.qrId);
            return ServiceResult.success("QR email re-sent to " + refresh.fullName + ".", null);
        }

        emailDispatchService.markEmailFailedQuietly(refresh.emailLogId, emailResult.getMessage());
        updateStudentQrStatus(refresh.studentPk, EmailStatus.FAILED);
        return ServiceResult.warning("The QR code was updated, but the email could not be sent yet.", null);
    }

    public ServiceResult<String> generatePermanentQrToken(String studentCode) {
        String normalizedStudentCode = normalizeStudentCode(studentCode);
        if (normalizedStudentCode.isBlank()) {
            return ServiceResult.failure("Student ID is required.");
        }
        return ServiceResult.success("QR code created.", SecurityUtil.generateOpaqueToken());
    }

    private ServiceResult<StudentCreateDraft> prepareStudentCreateDraft(int actorUserId, int teacherId, String sectionName,
            String studentCode, String fullName, String email) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String normalizedSection = normalizeSection(sectionName);
        String normalizedStudentCode = normalizeStudentCode(studentCode);
        String normalizedName = normalizeName(fullName);
        String normalizedEmail = normalizeEmail(email);

        if (actorUserId <= 0) {
            return ServiceResult.failure("Admin account is required.");
        }
        if (teacherId <= 0) {
            return ServiceResult.failure("Assigned teacher is required.");
        }
        if (normalizedSection.isBlank() || normalizedStudentCode.isBlank() || normalizedName.isBlank() || normalizedEmail.isBlank()) {
            return ServiceResult.failure("Section, student ID, full name, and email are required.");
        }
        if (!looksLikeEmail(normalizedEmail)) {
            return ServiceResult.failure("Enter a valid student email address.");
        }

        ServiceResult<String> qrTokenResult = generatePermanentQrToken(normalizedStudentCode);
        if (!qrTokenResult.isSuccess() || qrTokenResult.getData() == null) {
            return ServiceResult.failure(qrTokenResult.getMessage());
        }

        return ServiceResult.success("Student details are ready.",
                new StudentCreateDraft(actorUserId, teacherId, normalizedSection, normalizedStudentCode,
                        normalizedName, normalizedEmail, qrTokenResult.getData()));
    }

    private ServiceResult<StudentCreateResult> saveStudentProfileByAdmin(StudentCreateDraft draft) {
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                ServiceResult<Void> actorCheck = validateStudentActors(connection, draft.actorUserId, draft.teacherId);
                if (!actorCheck.isSuccess()) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure(actorCheck.getMessage());
                }

                if (studentCodeExists(connection, draft.studentCode)) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("That student ID already exists.");
                }
                if (studentEmailExists(connection, draft.email)) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("That student email is already registered.");
                }

                long studentPk = insertStudentProfile(connection, draft.teacherId, draft.actorUserId,
                        draft.studentCode, draft.sectionName, draft.fullName, draft.email);
                insertTeacherAssignment(connection, draft.teacherId, studentPk);
                long qrId = insertQrToken(connection, studentPk, SecurityUtil.sha256Hex(draft.qrToken));

                ServiceResult<Integer> emailLogResult = queueStudentQrEmail(connection, (int) studentPk, draft.email, false);
                if (!emailLogResult.isSuccess() || emailLogResult.getData() == null) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not create the student record.");
                }

                ServiceResult<Void> auditResult = auditLogService.logAction(connection, draft.actorUserId,
                        "STUDENT_CREATE", "STUDENT", String.valueOf(studentPk),
                        null,
                        buildStudentJson(draft.studentCode, draft.sectionName, draft.fullName, draft.email, "QUEUED", draft.teacherId),
                        "Student profile created by admin and assigned to teacher section.");
                if (!auditResult.isSuccess()) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not create the student record.");
                }

                connection.commit();
                return ServiceResult.success("Student record saved.",
                        new StudentCreateResult((int) studentPk, draft.teacherId, draft.sectionName, draft.studentCode,
                                draft.fullName, draft.email, emailLogResult.getData(), qrId));
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not create the student record.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not create the student record.");
        }
    }

    private ServiceResult<StudentProfile> sendStudentQrEmail(StudentCreateResult created, String qrToken, boolean resend,
            String successMessage, String warningMessage) {
        ResendEmailClient.EmailSendResult emailResult = resendEmailClient.sendStudentQrEmail(
                created.email, created.fullName, created.studentCode, qrToken, resend);

        if (emailResult.isSuccess()) {
            emailDispatchService.markEmailSentQuietly(created.emailLogId, emailResult.getProviderMessageId());
            updateStudentQrStatus(created.studentPk, EmailStatus.SENT);
            updateQrTokenEmailedAt(created.qrId);
            StudentProfile profile = loadStudentProfileOrFallback(created.teacherId, created.studentCode,
                    created.sectionName, created.fullName, created.email, EmailStatus.SENT);
            return ServiceResult.success(successMessage, profile);
        }

        emailDispatchService.markEmailFailedQuietly(created.emailLogId, emailResult.getMessage());
        updateStudentQrStatus(created.studentPk, EmailStatus.FAILED);
        StudentProfile profile = loadStudentProfileOrFallback(created.teacherId, created.studentCode,
                created.sectionName, created.fullName, created.email, EmailStatus.FAILED);
        return ServiceResult.warning(warningMessage, profile);
    }

    private ServiceResult<StudentQrRefreshResult> saveStudentQrRefresh(int teacherId, String studentCode) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String normalizedStudentCode = normalizeStudentCode(studentCode);
        if (teacherId <= 0) {
            return ServiceResult.failure("Assigned teacher account is required.");
        }
        if (normalizedStudentCode.isBlank()) {
            return ServiceResult.failure("Student ID is required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserAccountRow teacher = loadActiveUser(connection, teacherId, "TEACHER", "Assigned teacher");
                if (teacher == null) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Assigned teacher account was not found.");
                }

                StudentTokenRow studentRow = loadStudentToken(connection, teacherId, normalizedStudentCode);
                if (studentRow == null) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Student not found for this teacher list.");
                }

                String qrToken = SecurityUtil.generateOpaqueToken();
                deactivateActiveQrTokens(connection, studentRow.studentPk);
                long qrId = insertQrToken(connection, studentRow.studentPk, SecurityUtil.sha256Hex(qrToken));
                updateStudentQrStatus(connection, studentRow.studentPk, EmailStatus.QUEUED);

                ServiceResult<Integer> emailLogResult = queueStudentQrEmail(connection, (int) studentRow.studentPk, studentRow.email, true);
                if (!emailLogResult.isSuccess() || emailLogResult.getData() == null) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not send the QR code again.");
                }

                ServiceResult<Void> auditResult = auditLogService.logAction(connection, teacherId,
                        "STUDENT_QR_RESEND", "STUDENT", String.valueOf(studentRow.studentPk),
                        null,
                        buildStudentJson(studentRow.studentCode, studentRow.sectionName, studentRow.fullName,
                                studentRow.email, "QUEUED", teacherId),
                        "Student QR was re-sent from the current teacher list.");
                if (!auditResult.isSuccess()) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not send the QR code again.");
                }

                connection.commit();
                return ServiceResult.success("Student QR updated.",
                        new StudentQrRefreshResult(studentRow.studentPk, studentRow.studentCode, studentRow.fullName,
                                studentRow.email, emailLogResult.getData(), qrId, qrToken));
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not send the QR code again.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not send the QR code again.");
        }
    }

    public ServiceResult<Void> submitStudentRemovalRequest(int teacherId, String studentCode, String reason) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String normalizedStudentCode = normalizeStudentCode(studentCode);
        String normalizedReason = normalizeReason(reason);
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (normalizedStudentCode.isBlank()) {
            return ServiceResult.failure("Student ID is required.");
        }
        if (normalizedReason.isBlank()) {
            return ServiceResult.failure("A removal reason is required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserAccountRow teacher = loadUser(connection, teacherId, "TEACHER");
                if (teacher == null) {
                    connection.rollback();
                    return ServiceResult.failure("Teacher account was not found.");
                }

                StudentTokenRow studentRow = loadStudentToken(connection, teacherId, normalizedStudentCode);
                if (studentRow == null) {
                    connection.rollback();
                    return ServiceResult.failure("Student not found in your list.");
                }
                if (hasPendingRemovalRequest(connection, teacherId, studentRow.studentPk)) {
                    connection.rollback();
                    return ServiceResult.failure("There is already a pending removal request for this student.");
                }

                long requestId = insertRemovalRequest(connection, teacherId, studentRow.studentPk, normalizedReason);
                ServiceResult<Void> auditResult = auditLogService.logAction(connection, teacherId,
                        "STUDENT_REMOVAL_REQUEST", "STUDENT_REMOVAL_REQUEST", String.valueOf(requestId),
                        null,
                        "{\"student_code\":\"" + escapeJson(studentRow.studentCode) + "\",\"section_name\":\"" + escapeJson(studentRow.sectionName)
                        + "\",\"reason\":\"" + escapeJson(normalizedReason) + "\"}",
                        "Teacher requested removal of the student from the roster.");
                if (!auditResult.isSuccess()) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not submit the removal request.");
                }
                connection.commit();
                return ServiceResult.success("Removal request sent to admin for approval.", null);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not submit the removal request.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not submit the removal request.");
        }
    }

    public ServiceResult<List<StudentRemovalRequest>> getStudentRemovalRequests() {
        return loadRemovalRequests(SELECT_REMOVAL_REQUESTS_SQL, null);
    }

    public ServiceResult<List<StudentRemovalRequest>> getStudentRemovalRequestsForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        return loadRemovalRequests(SELECT_REMOVAL_REQUESTS_FOR_TEACHER_SQL, teacherId);
    }

    public ServiceResult<Void> reviewStudentRemovalRequest(int reviewerUserId, int requestId, boolean approve, String reviewerName) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (reviewerUserId <= 0) {
            return ServiceResult.failure("Admin account is required.");
        }
        if (requestId <= 0) {
            return ServiceResult.failure("Removal request selection is required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserAccountRow admin = loadUser(connection, reviewerUserId, "ADMIN");
                if (admin == null) {
                    connection.rollback();
                    return ServiceResult.failure("Admin account was not found.");
                }

                RemovalRequestRow requestRow = loadRemovalRequest(connection, requestId);
                if (requestRow == null) {
                    connection.rollback();
                    return ServiceResult.failure("Removal request not found.");
                }
                if (requestRow.status != ScheduleRequestStatus.PENDING) {
                    connection.rollback();
                    return ServiceResult.failure("Only pending removal requests can be reviewed.");
                }

                if (approve) {
                    deactivateAssignment(connection, requestRow.teacherId, requestRow.studentPk);
                }
                updateRemovalRequestStatus(connection, requestId, approve ? "APPROVED" : "REJECTED", reviewerUserId);
                ServiceResult<Void> auditResult = auditLogService.logAction(connection, reviewerUserId,
                        approve ? "STUDENT_REMOVAL_APPROVE" : "STUDENT_REMOVAL_REJECT",
                        "STUDENT_REMOVAL_REQUEST",
                        String.valueOf(requestId),
                        "{\"status\":\"PENDING\"}",
                        "{\"status\":\"" + (approve ? "APPROVED" : "REJECTED") + "\",\"reviewed_by\":\"" + escapeJson(safe(reviewerName)) + "\"}",
                        approve
                                ? "Admin approved the teacher request to remove the student from the roster."
                                : "Admin rejected the teacher request to remove the student from the roster.");
                if (!auditResult.isSuccess()) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure("Could not review the removal request.");
                }
                connection.commit();
                return ServiceResult.success(approve ? "Student removal approved." : "Student removal request rejected.", null);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not review the removal request.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not review the removal request.");
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
            return ServiceResult.success("Loaded roster removal requests from MariaDB.", requests);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load removal requests: " + ex.getMessage());
        }
    }

    private UserAccountRow loadUser(Connection connection, int userId, String role) throws SQLException {
        String sql = "ADMIN".equalsIgnoreCase(role) ? SELECT_ACTIVE_ADMIN_SQL : SELECT_ACTIVE_TEACHER_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserAccountRow(
                        resultSet.getInt("user_id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        resultSet.getString("account_status")
                );
            }
        }
    }

    private UserAccountRow loadActiveUser(Connection connection, int userId, String role, String label) throws SQLException {
        UserAccountRow user = loadUser(connection, userId, role);
        if (user == null) {
            return null;
        }
        if (!"ACTIVE".equalsIgnoreCase(user.accountStatus)) {
            throw new SQLException(label + " account is not active.");
        }
        return user;
    }

    private ServiceResult<Void> validateStudentActors(Connection connection, int adminUserId, int teacherId) {
        try {
            UserAccountRow admin = loadActiveUser(connection, adminUserId, "ADMIN", "Admin");
            if (admin == null) {
                return ServiceResult.failure("Admin account was not found.");
            }
            UserAccountRow teacher = loadActiveUser(connection, teacherId, "TEACHER", "Assigned teacher");
            if (teacher == null) {
                return ServiceResult.failure("Assigned teacher account was not found.");
            }
            return ServiceResult.success("Student actors are valid.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure(ex.getMessage());
        }
    }

    private boolean studentCodeExists(Connection connection, String studentCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHECK_STUDENT_CODE_SQL)) {
            statement.setString(1, studentCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean studentEmailExists(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHECK_STUDENT_EMAIL_SQL)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private long insertStudentProfile(Connection connection, int teacherId, int adminId, String studentCode, String sectionName,
            String fullName, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_STUDENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, studentCode);
            statement.setString(2, fullName);
            statement.setString(3, email);
            statement.setString(4, sectionName);
            statement.setInt(5, teacherId);
            statement.setInt(6, adminId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Student insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertTeacherAssignment(Connection connection, int teacherId, long studentPk) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ASSIGNMENT_SQL)) {
            statement.setInt(1, teacherId);
            statement.setLong(2, studentPk);
            statement.executeUpdate();
        }
    }

    private long insertQrToken(Connection connection, long studentPk, String tokenHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_QR_TOKEN_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, studentPk);
            statement.setString(2, tokenHash);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("QR token insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void deactivateActiveQrTokens(Connection connection, long studentPk) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DEACTIVATE_QR_TOKENS_SQL)) {
            statement.setLong(1, studentPk);
            statement.executeUpdate();
        }
    }

    private ServiceResult<Integer> queueStudentQrEmail(Connection connection, int studentPk, String recipientEmail,
            boolean resendMode) {
        ServiceResult<ppb.qrattend.model.AppDomain.EmailDispatch> emailLogResult =
                emailDispatchService.queueStudentQrEmail(connection, studentPk, recipientEmail, resendMode);
        if (!emailLogResult.isSuccess() || emailLogResult.getData() == null) {
            return ServiceResult.failure("Could not queue the student email.");
        }
        return ServiceResult.success("Student email queued.", emailLogResult.getData().getId());
    }

    private StudentTokenRow loadStudentToken(Connection connection, int teacherId, String studentCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_QR_SQL)) {
            statement.setInt(1, teacherId);
            statement.setString(2, studentCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new StudentTokenRow(
                        resultSet.getLong("student_pk"),
                        resultSet.getString("student_code"),
                        resultSet.getString("section_name"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        mapEmailStatus(resultSet.getString("qr_status")),
                        resultSet.getLong("qr_id")
                );
            }
        }
    }

    private boolean hasPendingRemovalRequest(Connection connection, int teacherId, long studentPk) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHECK_PENDING_REMOVAL_REQUEST_SQL)) {
            statement.setInt(1, teacherId);
            statement.setLong(2, studentPk);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private long insertRemovalRequest(Connection connection, int teacherId, long studentPk, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_REMOVAL_REQUEST_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, teacherId);
            statement.setLong(2, studentPk);
            statement.setString(3, reason);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Removal request insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private RemovalRequestRow loadRemovalRequest(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_REMOVAL_REQUEST_BY_ID_SQL)) {
            statement.setInt(1, requestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapRemovalRequestRow(resultSet);
            }
        }
    }

    private void updateRemovalRequestStatus(Connection connection, int requestId, String status, int reviewerUserId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_REMOVAL_REQUEST_SQL)) {
            statement.setString(1, status);
            statement.setInt(2, reviewerUserId);
            statement.setInt(3, requestId);
            statement.executeUpdate();
        }
    }

    private void deactivateAssignment(Connection connection, int teacherId, long studentPk) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DEACTIVATE_ASSIGNMENT_SQL)) {
            statement.setInt(1, teacherId);
            statement.setLong(2, studentPk);
            statement.executeUpdate();
        }
    }

    private void updateStudentQrStatus(long studentPk, EmailStatus status) {
        try (Connection connection = databaseManager.openConnection()) {
            updateStudentQrStatus(connection, studentPk, status);
        } catch (SQLException ignored) {
        }
    }

    private void updateStudentQrStatus(Connection connection, long studentPk, EmailStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_STUDENT_QR_STATUS_SQL)) {
            statement.setString(1, status.name());
            statement.setLong(2, studentPk);
            statement.executeUpdate();
        }
    }

    private void updateQrTokenEmailedAt(long qrId) {
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_QR_EMAILED_AT_SQL)) {
            statement.setLong(1, qrId);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private StudentProfile loadStudentProfileOrFallback(int teacherId, String studentCode, String sectionName,
            String fullName, String email, EmailStatus qrStatus) {
        ServiceResult<StudentProfile> profileResult = findStudentForTeacher(teacherId, studentCode);
        if (profileResult.isSuccess() && profileResult.getData() != null) {
            return profileResult.getData();
        }
        return new StudentProfile(studentCode, teacherId, sectionName, fullName, email, qrStatus, LocalDateTime.now());
    }

    private StudentProfile mapStudentProfile(ResultSet resultSet, int teacherId) throws SQLException {
        return new StudentProfile(
                resultSet.getString("student_code"),
                teacherId,
                safe(resultSet.getString("section_name")),
                resultSet.getString("full_name"),
                resultSet.getString("email"),
                mapEmailStatus(resultSet.getString("qr_status")),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private RemovalRequestRow mapRemovalRequestRow(ResultSet resultSet) throws SQLException {
        return new RemovalRequestRow(
                resultSet.getInt("removal_request_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getString("teacher_name"),
                resultSet.getLong("student_pk"),
                resultSet.getString("student_code"),
                resultSet.getString("student_name"),
                safe(resultSet.getString("section_name")),
                safe(resultSet.getString("reason")),
                mapRequestStatus(resultSet.getString("request_status")),
                safe(resultSet.getString("reviewed_by_name")),
                timestampToLocalDateTime(resultSet.getTimestamp("reviewed_at")),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private StudentRemovalRequest mapRemovalRequest(ResultSet resultSet) throws SQLException {
        return mapRemovalRequestRow(resultSet).toDomain();
    }

    private ScheduleRequestStatus mapRequestStatus(String rawStatus) {
        if (rawStatus == null) {
            return ScheduleRequestStatus.PENDING;
        }
        return switch (rawStatus.trim().toUpperCase(Locale.ENGLISH)) {
            case "APPROVED" -> ScheduleRequestStatus.APPROVED;
            case "REJECTED" -> ScheduleRequestStatus.REJECTED;
            default -> ScheduleRequestStatus.PENDING;
        };
    }

    private EmailStatus mapEmailStatus(String rawStatus) {
        if (rawStatus == null) {
            return EmailStatus.QUEUED;
        }
        return switch (rawStatus.trim().toUpperCase(Locale.ENGLISH)) {
            case "SENT" -> EmailStatus.SENT;
            case "FAILED" -> EmailStatus.FAILED;
            default -> EmailStatus.QUEUED;
        };
    }

    private String normalizeStudentCode(String studentCode) {
        return safe(studentCode).toUpperCase(Locale.ENGLISH);
    }

    private String normalizeSection(String sectionName) {
        return safe(sectionName);
    }

    private String normalizeName(String fullName) {
        return safe(fullName);
    }

    private String normalizeEmail(String email) {
        return safe(email).toLowerCase(Locale.ENGLISH);
    }

    private String normalizeReason(String reason) {
        return safe(reason);
    }

    private boolean looksLikeEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    private String buildStudentJson(String studentCode, String sectionName, String fullName, String email, String qrStatus, int teacherId) {
        return "{"
                + "\"student_code\":\"" + escapeJson(studentCode) + "\","
                + "\"section_name\":\"" + escapeJson(sectionName) + "\","
                + "\"full_name\":\"" + escapeJson(fullName) + "\","
                + "\"email\":\"" + escapeJson(email) + "\","
                + "\"qr_status\":\"" + escapeJson(qrStatus) + "\","
                + "\"teacher_user_id\":" + teacherId
                + "}";
    }

    private LocalDateTime timestampToLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    private String escapeJson(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class StudentCreateDraft {

        private final int actorUserId;
        private final int teacherId;
        private final String sectionName;
        private final String studentCode;
        private final String fullName;
        private final String email;
        private final String qrToken;

        private StudentCreateDraft(int actorUserId, int teacherId, String sectionName, String studentCode,
                String fullName, String email, String qrToken) {
            this.actorUserId = actorUserId;
            this.teacherId = teacherId;
            this.sectionName = sectionName;
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.email = email;
            this.qrToken = qrToken;
        }
    }

    private static final class StudentCreateResult {

        private final long studentPk;
        private final int teacherId;
        private final String sectionName;
        private final String studentCode;
        private final String fullName;
        private final String email;
        private final int emailLogId;
        private final long qrId;

        private StudentCreateResult(long studentPk, int teacherId, String sectionName, String studentCode,
                String fullName, String email, int emailLogId, long qrId) {
            this.studentPk = studentPk;
            this.teacherId = teacherId;
            this.sectionName = sectionName;
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.email = email;
            this.emailLogId = emailLogId;
            this.qrId = qrId;
        }
    }

    private static final class StudentQrRefreshResult {

        private final long studentPk;
        private final String studentCode;
        private final String fullName;
        private final String email;
        private final int emailLogId;
        private final long qrId;
        private final String qrToken;

        private StudentQrRefreshResult(long studentPk, String studentCode, String fullName, String email,
                int emailLogId, long qrId, String qrToken) {
            this.studentPk = studentPk;
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.email = email;
            this.emailLogId = emailLogId;
            this.qrId = qrId;
            this.qrToken = qrToken;
        }
    }

    private static final class UserAccountRow {

        private final int userId;
        private final String fullName;
        private final String email;
        private final String accountStatus;

        private UserAccountRow(int userId, String fullName, String email, String accountStatus) {
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
            this.accountStatus = accountStatus;
        }
    }

    private static final class StudentTokenRow {

        private final long studentPk;
        private final String studentCode;
        private final String sectionName;
        private final String fullName;
        private final String email;
        private final EmailStatus qrStatus;
        private final long qrId;

        private StudentTokenRow(long studentPk, String studentCode, String sectionName, String fullName,
                String email, EmailStatus qrStatus, long qrId) {
            this.studentPk = studentPk;
            this.studentCode = studentCode;
            this.sectionName = sectionName;
            this.fullName = fullName;
            this.email = email;
            this.qrStatus = qrStatus;
            this.qrId = qrId;
        }
    }

    private static final class RemovalRequestRow {

        private final int id;
        private final int teacherId;
        private final String teacherName;
        private final long studentPk;
        private final String studentCode;
        private final String studentName;
        private final String sectionName;
        private final String reason;
        private final ScheduleRequestStatus status;
        private final String reviewedBy;
        private final LocalDateTime reviewedAt;
        private final LocalDateTime createdAt;

        private RemovalRequestRow(int id, int teacherId, String teacherName, long studentPk, String studentCode,
                String studentName, String sectionName, String reason, ScheduleRequestStatus status,
                String reviewedBy, LocalDateTime reviewedAt, LocalDateTime createdAt) {
            this.id = id;
            this.teacherId = teacherId;
            this.teacherName = teacherName;
            this.studentPk = studentPk;
            this.studentCode = studentCode;
            this.studentName = studentName;
            this.sectionName = sectionName;
            this.reason = reason;
            this.status = status;
            this.reviewedBy = reviewedBy;
            this.reviewedAt = reviewedAt;
            this.createdAt = createdAt;
        }

        private StudentRemovalRequest toDomain() {
            StudentRemovalRequest request = new StudentRemovalRequest(
                    id,
                    teacherId,
                    teacherName,
                    studentCode,
                    studentName,
                    sectionName,
                    reason,
                    status,
                    createdAt
            );
            request.setReviewedBy(reviewedBy);
            request.setReviewedAt(reviewedAt);
            return request;
        }
    }
}
