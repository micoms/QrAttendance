package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.db.SecurityUtil;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.AppDomain.AttendanceSession;
import ppb.qrattend.model.AppDomain.AttendanceSource;
import ppb.qrattend.model.AppDomain.AttendanceStatus;
import ppb.qrattend.model.AppDomain.SessionState;

public final class AttendanceService {

    private static final String SELECT_TEACHER_SQL = """
            SELECT user_id, full_name, account_status
            FROM users
            WHERE user_id = ?
              AND role = 'TEACHER'
            LIMIT 1
            """;

    private static final String SELECT_OVERRIDE_SESSION_SQL = """
            SELECT session_id, teacher_user_id, subject_name, room_name, session_state, opened_at, override_reason
            FROM attendance_sessions
            WHERE teacher_user_id = ?
              AND session_mode = 'OVERRIDE'
              AND session_state = 'OVERRIDE_ACTIVE'
            ORDER BY opened_at DESC, session_id DESC
            LIMIT 1
            """;

    private static final String SELECT_ACTIVE_SCHEDULE_SQL = """
            SELECT schedule_id, teacher_user_id, subject_name, day_of_week, start_time, end_time, room_name
            FROM teacher_schedules
            WHERE teacher_user_id = ?
              AND day_of_week = ?
              AND schedule_status = 'APPROVED'
              AND start_time <= ?
              AND end_time >= ?
            ORDER BY start_time ASC
            LIMIT 1
            """;

    private static final String SELECT_ACTIVE_SCHEDULE_SESSION_SQL = """
            SELECT session_id, teacher_user_id, subject_name, room_name, session_state, opened_at, override_reason
            FROM attendance_sessions
            WHERE teacher_user_id = ?
              AND schedule_id = ?
              AND session_date = ?
              AND session_mode = 'SCHEDULE'
              AND session_state = 'ACTIVE'
            ORDER BY opened_at DESC, session_id DESC
            LIMIT 1
            """;

    private static final String INSERT_SCHEDULE_SESSION_SQL = """
            INSERT INTO attendance_sessions
                (schedule_id, teacher_user_id, subject_name, room_name, session_date, opened_at, session_state, session_mode)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE', 'SCHEDULE')
            """;

    private static final String INSERT_OVERRIDE_SESSION_SQL = """
            INSERT INTO attendance_sessions
                (schedule_id, teacher_user_id, subject_name, room_name, session_date, opened_at, session_state, session_mode, override_reason)
            VALUES (NULL, ?, ?, 'Override', ?, CURRENT_TIMESTAMP, 'OVERRIDE_ACTIVE', 'OVERRIDE', ?)
            """;

    private static final String CLOSE_OVERRIDE_SESSION_SQL = """
            UPDATE attendance_sessions
            SET session_state = 'CLOSED',
                closed_at = CURRENT_TIMESTAMP
            WHERE teacher_user_id = ?
              AND session_mode = 'OVERRIDE'
              AND session_state = 'OVERRIDE_ACTIVE'
            """;

    private static final String CLOSE_EXPIRED_SCHEDULE_SESSIONS_SQL = """
            UPDATE attendance_sessions
            SET session_state = 'CLOSED',
                closed_at = CURRENT_TIMESTAMP
            WHERE teacher_user_id = ?
              AND session_mode = 'SCHEDULE'
              AND session_state = 'ACTIVE'
              AND session_date = ?
              AND schedule_id NOT IN (
                  SELECT schedule_id
                  FROM teacher_schedules
                  WHERE teacher_user_id = ?
                    AND day_of_week = ?
                    AND schedule_status = 'APPROVED'
                    AND start_time <= ?
                    AND end_time >= ?
              )
            """;

    private static final String SELECT_STUDENT_FOR_QR_SQL = """
            SELECT
                sp.student_pk,
                sp.student_code,
                sp.full_name,
                sp.email,
                sqt.qr_id
            FROM teacher_student_assignments tsa
            INNER JOIN student_profiles sp
                ON sp.student_pk = tsa.student_pk
            LEFT JOIN student_qr_tokens sqt
                ON sqt.student_pk = sp.student_pk
               AND sqt.is_active = 1
            WHERE tsa.teacher_user_id = ?
              AND tsa.assignment_status = 'ACTIVE'
              AND sp.account_status = 'ACTIVE'
              AND (
                    UPPER(sp.student_code) = ?
                 OR LOWER(sp.email) = ?
                 OR COALESCE(sqt.token_hash, '') = ?
              )
            ORDER BY sqt.qr_id DESC
            LIMIT 1
            """;

    private static final String SELECT_STUDENT_FOR_MANUAL_SQL = """
            SELECT
                sp.student_pk,
                sp.student_code,
                sp.full_name,
                sp.email
            FROM teacher_student_assignments tsa
            INNER JOIN student_profiles sp
                ON sp.student_pk = tsa.student_pk
            WHERE tsa.teacher_user_id = ?
              AND tsa.assignment_status = 'ACTIVE'
              AND sp.account_status = 'ACTIVE'
              AND UPPER(sp.student_code) = ?
            LIMIT 1
            """;

    private static final String INSERT_ATTENDANCE_SQL = """
            INSERT INTO attendance_records
                (session_id, student_pk, teacher_user_id, subject_name, attendance_source, attendance_status, note, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private static final String SELECT_DUPLICATE_SQL = """
            SELECT 1
            FROM attendance_records ar
            INNER JOIN student_profiles sp
                ON sp.student_pk = ar.student_pk
            WHERE ar.session_id = ?
              AND UPPER(sp.student_code) = ?
            LIMIT 1
            """;

    private static final String INSERT_SCAN_LOG_SQL = """
            INSERT INTO qr_scan_logs
                (session_id, student_pk, token_hash, payload_preview, scan_result, rejection_reason, scanned_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private static final String UPDATE_QR_LAST_USED_SQL = """
            UPDATE student_qr_tokens
            SET last_used_at = CURRENT_TIMESTAMP
            WHERE qr_id = ?
            """;

    private static final String INSERT_AUDIT_LOG_SQL = """
            INSERT INTO audit_logs
                (actor_user_id, action_type, entity_type, entity_id, old_values_json, new_values_json, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private static final String SELECT_ATTENDANCE_SQL = """
            SELECT
                ar.attendance_id,
                sp.student_code,
                ar.teacher_user_id,
                sp.full_name,
                ar.subject_name,
                ar.recorded_at,
                ar.attendance_source,
                ar.attendance_status,
                ar.note
            FROM attendance_records ar
            INNER JOIN student_profiles sp
                ON sp.student_pk = ar.student_pk
            ORDER BY ar.recorded_at DESC, ar.attendance_id DESC
            """;

    private static final String SELECT_ATTENDANCE_FOR_TEACHER_SQL = """
            SELECT
                ar.attendance_id,
                sp.student_code,
                ar.teacher_user_id,
                sp.full_name,
                ar.subject_name,
                ar.recorded_at,
                ar.attendance_source,
                ar.attendance_status,
                ar.note
            FROM attendance_records ar
            INNER JOIN student_profiles sp
                ON sp.student_pk = ar.student_pk
            WHERE ar.teacher_user_id = ?
            ORDER BY ar.recorded_at DESC, ar.attendance_id DESC
            """;

    private final DatabaseManager databaseManager;

    public AttendanceService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public AttendanceService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<AttendanceSession> getSessionForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            TeacherRow teacher = loadTeacher(connection, teacherId);
            if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.accountStatus)) {
                return ServiceResult.failure("Teacher account was not found or is inactive.");
            }

            AttendanceSession overrideSession = loadOverrideSession(connection, teacherId);
            if (overrideSession != null) {
                return ServiceResult.success("Loaded override attendance session.", overrideSession);
            }

            ScheduleWindowRow activeSchedule = loadActiveSchedule(connection, teacherId, LocalDate.now(), LocalTime.now().withNano(0));
            if (activeSchedule == null) {
                closeExpiredScheduleSessions(connection, teacherId);
                AttendanceSession locked = new AttendanceSession(0, teacherId, "No active class", SessionState.LOCKED,
                        LocalDateTime.now(), "-", "No approved schedule is live right now.", false);
                return ServiceResult.success("Teacher has no active schedule window.", locked);
            }

            AttendanceSession scheduleSession = ensureScheduleSession(connection, teacherId, activeSchedule);
            return ServiceResult.success("Loaded schedule attendance session.", scheduleSession);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not resolve the attendance session: " + ex.getMessage());
        }
    }

    public ServiceResult<AttendanceSession> openOverrideSession(int teacherId, String subjectName, String reason) {
        String normalizedSubject = normalize(subjectName);
        String normalizedReason = normalize(reason);
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (normalizedSubject.isBlank() || normalizedReason.isBlank()) {
            return ServiceResult.failure("Override subject and reason are required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                TeacherRow teacher = loadTeacher(connection, teacherId);
                if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.accountStatus)) {
                    connection.rollback();
                    return ServiceResult.failure("Teacher account was not found or is inactive.");
                }
                if (loadOverrideSession(connection, teacherId) != null) {
                    connection.rollback();
                    return ServiceResult.failure("An override session is already open.");
                }
                if (loadActiveSchedule(connection, teacherId, LocalDate.now(), LocalTime.now().withNano(0)) != null) {
                    connection.rollback();
                    return ServiceResult.failure("A scheduled class is already active, so an override is not needed.");
                }

                long sessionId = insertOverrideSession(connection, teacherId, normalizedSubject, normalizedReason);
                insertAuditLog(connection, teacherId, "ATTENDANCE_OVERRIDE_OPEN", "ATTENDANCE_SESSION", String.valueOf(sessionId),
                        null,
                        buildSessionJson(normalizedSubject, "OVERRIDE_ACTIVE", "Override", normalizedReason),
                        "Teacher opened an override attendance session.");
                connection.commit();
                AttendanceSession session = new AttendanceSession((int) sessionId, teacherId, normalizedSubject, SessionState.OVERRIDE_ACTIVE,
                        LocalDateTime.now(), "Override", normalizedReason, true);
                return ServiceResult.success("Override attendance session opened for " + normalizedSubject + ".", session);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not open the override session: " + ex.getMessage());
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not open the override session: " + ex.getMessage());
        }
    }

    public ServiceResult<Void> closeOverrideSession(int teacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                AttendanceSession overrideSession = loadOverrideSession(connection, teacherId);
                if (overrideSession == null) {
                    connection.rollback();
                    return ServiceResult.failure("No open override session found.");
                }
                try (PreparedStatement statement = connection.prepareStatement(CLOSE_OVERRIDE_SESSION_SQL)) {
                    statement.setInt(1, teacherId);
                    statement.executeUpdate();
                }
                insertAuditLog(connection, teacherId, "ATTENDANCE_OVERRIDE_CLOSE", "ATTENDANCE_SESSION", String.valueOf(overrideSession.getId()),
                        buildSessionJson(overrideSession.getSubjectName(), overrideSession.getState().name(), overrideSession.getRoom(), overrideSession.getNote()),
                        buildSessionJson(overrideSession.getSubjectName(), SessionState.CLOSED.name(), overrideSession.getRoom(), overrideSession.getNote()),
                        "Teacher closed an override attendance session.");
                connection.commit();
                return ServiceResult.success("Override session closed.", null);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not close the override session: " + ex.getMessage());
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not close the override session: " + ex.getMessage());
        }
    }

    public ServiceResult<AttendanceRecord> recordQrAttendance(int teacherId, String qrPayload) {
        String normalizedPayload = normalize(qrPayload);
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (normalizedPayload.isBlank()) {
            return ServiceResult.failure("Scan or enter a QR value first.");
        }
        String payloadHash = SecurityUtil.sha256Hex(normalizedPayload);

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                LiveSessionContext session = resolveLiveSession(connection, teacherId);
                if (session == null || session.session.getState() == SessionState.LOCKED) {
                    connection.rollback();
                    return ServiceResult.failure("No scheduled class is active. Open an override session to scan.");
                }

                StudentScanRow student = loadStudentForQr(connection, teacherId, normalizedPayload);
                if (student == null) {
                    insertScanLog(connection, session.session.getId(), null, payloadHash, "REJECTED", "Invalid or unassigned QR code.");
                    connection.commit();
                    return ServiceResult.failure("No student matched that QR token.");
                }
                if (isDuplicateAttendance(connection, session.session.getId(), student.studentCode)) {
                    insertScanLog(connection, session.session.getId(), student.studentPk, payloadHash, "DUPLICATE", "Student already recorded for this session.");
                    connection.commit();
                    return ServiceResult.warning(student.fullName + " is already marked for this session.", null);
                }

                AttendanceStatus status = deriveAttendanceStatus(session);
                String note = session.session.isOverrideSession()
                        ? "Captured from QR during override: " + session.session.getNote()
                        : "Captured from QR";
                long attendanceId = insertAttendanceRecord(connection, session.session.getId(), student.studentPk, teacherId,
                        session.session.getSubjectName(), AttendanceSource.QR, status, note);
                insertScanLog(connection, session.session.getId(), student.studentPk, payloadHash, "ACCEPTED", null);
                if (student.qrId != null) {
                    updateQrLastUsed(connection, student.qrId);
                }
                connection.commit();
                AttendanceRecord record = new AttendanceRecord((int) attendanceId, student.studentCode, teacherId, student.fullName,
                        session.session.getSubjectName(), LocalDateTime.now(), AttendanceSource.QR, status, note);
                return ServiceResult.success(student.fullName + " marked " + status.getLabel().toLowerCase(Locale.ENGLISH) + " via QR.", record);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not save the QR attendance.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the QR attendance.");
        }
    }

    public ServiceResult<AttendanceRecord> recordManualAttendance(int teacherId, String studentCode, String note) {
        String normalizedStudentCode = normalizeStudentCode(studentCode);
        String normalizedNote = normalize(note);
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (normalizedStudentCode.isBlank()) {
            return ServiceResult.failure("Student ID is required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                LiveSessionContext session = resolveLiveSession(connection, teacherId);
                if (session == null || session.session.getState() == SessionState.LOCKED) {
                    connection.rollback();
                    return ServiceResult.failure("No scheduled class is active. Open an override session first.");
                }

                StudentManualRow student = loadStudentForManual(connection, teacherId, normalizedStudentCode);
                if (student == null) {
                    connection.rollback();
                    return ServiceResult.failure("Select a student from the class list.");
                }
                if (isDuplicateAttendance(connection, session.session.getId(), student.studentCode)) {
                    connection.rollback();
                    return ServiceResult.warning(student.fullName + " is already marked for this session.", null);
                }

                AttendanceStatus status = deriveAttendanceStatus(session);
                String noteValue = normalizedNote.isBlank() ? "Manual backup entry." : normalizedNote;
                long attendanceId = insertAttendanceRecord(connection, session.session.getId(), student.studentPk, teacherId,
                        session.session.getSubjectName(), AttendanceSource.MANUAL, status, noteValue);
                insertAuditLog(connection, teacherId, "ATTENDANCE_MANUAL_MARK", "ATTENDANCE_RECORD", String.valueOf(attendanceId),
                        null,
                        buildAttendanceJson(student.studentCode, session.session.getSubjectName(), AttendanceSource.MANUAL, status, noteValue),
                        "Teacher marked attendance manually as backup.");
                connection.commit();
                AttendanceRecord record = new AttendanceRecord((int) attendanceId, student.studentCode, teacherId, student.fullName,
                        session.session.getSubjectName(), LocalDateTime.now(), AttendanceSource.MANUAL, status, noteValue);
                return ServiceResult.success(student.fullName + " added through backup attendance.", record);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not save the attendance record.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the attendance record.");
        }
    }

    public ServiceResult<Boolean> checkDuplicateAttendance(int sessionId, String studentCode) {
        String normalizedStudentCode = normalizeStudentCode(studentCode);
        if (sessionId <= 0) {
            return ServiceResult.failure("Attendance session is required.");
        }
        if (normalizedStudentCode.isBlank()) {
            return ServiceResult.failure("Student ID is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            return ServiceResult.success("Duplicate check completed.", isDuplicateAttendance(connection, sessionId, normalizedStudentCode));
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not check the attendance record.");
        }
    }

    public ServiceResult<List<AttendanceRecord>> getAttendanceRecords() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<AttendanceRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ATTENDANCE_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(mapAttendanceRecord(resultSet));
            }
            return ServiceResult.success("Loaded attendance records from MariaDB.", records);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load attendance records.");
        }
    }

    public ServiceResult<List<AttendanceRecord>> getAttendanceRecordsForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<AttendanceRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ATTENDANCE_FOR_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapAttendanceRecord(resultSet));
                }
            }
            return ServiceResult.success("Loaded teacher attendance records from MariaDB.", records);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load attendance records.");
        }
    }

    public ServiceResult<List<AttendanceRecord>> getRecentAttendanceRecords(int limit, Integer teacherId) {
        if (limit <= 0) {
            return ServiceResult.failure("Recent attendance limit must be greater than 0.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    ar.attendance_id,
                    sp.student_code,
                    ar.teacher_user_id,
                    sp.full_name,
                    ar.subject_name,
                    ar.recorded_at,
                    ar.attendance_source,
                    ar.attendance_status,
                    ar.note
                FROM attendance_records ar
                INNER JOIN student_profiles sp
                    ON sp.student_pk = ar.student_pk
                """);
        if (teacherId != null) {
            sql.append(" WHERE ar.teacher_user_id = ?");
        }
        sql.append(" ORDER BY ar.recorded_at DESC, ar.attendance_id DESC LIMIT ?");

        List<AttendanceRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameterIndex = 1;
            if (teacherId != null) {
                statement.setInt(parameterIndex++, teacherId);
            }
            statement.setInt(parameterIndex, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapAttendanceRecord(resultSet));
                }
            }
            return ServiceResult.success("Loaded recent attendance records.", records);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load recent attendance records.");
        }
    }

    private TeacherRow loadTeacher(Connection connection, int teacherId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new TeacherRow(
                        resultSet.getInt("user_id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("account_status")
                );
            }
        }
    }

    private AttendanceSession loadOverrideSession(Connection connection, int teacherId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_OVERRIDE_SESSION_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapSession(resultSet, true);
            }
        }
    }

    private ScheduleWindowRow loadActiveSchedule(Connection connection, int teacherId, LocalDate date, LocalTime time) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_SCHEDULE_SQL)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, date.getDayOfWeek().getValue());
            statement.setTime(3, java.sql.Time.valueOf(time));
            statement.setTime(4, java.sql.Time.valueOf(time));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new ScheduleWindowRow(
                        resultSet.getInt("schedule_id"),
                        resultSet.getInt("teacher_user_id"),
                        resultSet.getString("subject_name"),
                        dayOfWeek(resultSet.getInt("day_of_week")),
                        resultSet.getTime("start_time").toLocalTime(),
                        resultSet.getTime("end_time").toLocalTime(),
                        resultSet.getString("room_name")
                );
            }
        }
    }

    private AttendanceSession ensureScheduleSession(Connection connection, int teacherId, ScheduleWindowRow schedule) throws SQLException {
        LocalDate today = LocalDate.now();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_SCHEDULE_SESSION_SQL)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, schedule.scheduleId);
            statement.setDate(3, java.sql.Date.valueOf(today));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapSession(resultSet, false);
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_SCHEDULE_SESSION_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, schedule.scheduleId);
            statement.setInt(2, teacherId);
            statement.setString(3, schedule.subjectName);
            statement.setString(4, schedule.roomName);
            statement.setDate(5, java.sql.Date.valueOf(today));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Attendance session insert did not return a generated key.");
                }
                int sessionId = keys.getInt(1);
                return new AttendanceSession(sessionId, teacherId, schedule.subjectName, SessionState.ACTIVE,
                        LocalDateTime.now(), schedule.roomName, "Session #" + sessionId + " - schedule window", false);
            }
        }
    }

    private void closeExpiredScheduleSessions(Connection connection, int teacherId) throws SQLException {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withNano(0);
        try (PreparedStatement statement = connection.prepareStatement(CLOSE_EXPIRED_SCHEDULE_SESSIONS_SQL)) {
            statement.setInt(1, teacherId);
            statement.setDate(2, java.sql.Date.valueOf(today));
            statement.setInt(3, teacherId);
            statement.setInt(4, today.getDayOfWeek().getValue());
            statement.setTime(5, java.sql.Time.valueOf(now));
            statement.setTime(6, java.sql.Time.valueOf(now));
            statement.executeUpdate();
        }
    }

    private LiveSessionContext resolveLiveSession(Connection connection, int teacherId) throws SQLException {
        AttendanceSession overrideSession = loadOverrideSession(connection, teacherId);
        if (overrideSession != null) {
            return new LiveSessionContext(overrideSession, null);
        }
        ScheduleWindowRow activeSchedule = loadActiveSchedule(connection, teacherId, LocalDate.now(), LocalTime.now().withNano(0));
        if (activeSchedule == null) {
            closeExpiredScheduleSessions(connection, teacherId);
            return null;
        }
        return new LiveSessionContext(ensureScheduleSession(connection, teacherId, activeSchedule), activeSchedule);
    }

    private long insertOverrideSession(Connection connection, int teacherId, String subjectName, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OVERRIDE_SESSION_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, teacherId);
            statement.setString(2, subjectName);
            statement.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
            statement.setString(4, reason);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Override session insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private StudentScanRow loadStudentForQr(Connection connection, int teacherId, String qrPayload) throws SQLException {
        String normalizedCode = normalizeStudentCode(qrPayload);
        String normalizedEmail = qrPayload.toLowerCase(Locale.ENGLISH);
        String normalizedPayloadHash = SecurityUtil.sha256Hex(normalize(qrPayload));
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STUDENT_FOR_QR_SQL)) {
            statement.setInt(1, teacherId);
            statement.setString(2, normalizedCode);
            statement.setString(3, normalizedEmail);
            statement.setString(4, normalizedPayloadHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Long qrId = resultSet.getObject("qr_id") == null ? null : resultSet.getLong("qr_id");
                return new StudentScanRow(
                        resultSet.getLong("student_pk"),
                        resultSet.getString("student_code"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        qrId
                );
            }
        }
    }

    private StudentManualRow loadStudentForManual(Connection connection, int teacherId, String studentCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STUDENT_FOR_MANUAL_SQL)) {
            statement.setInt(1, teacherId);
            statement.setString(2, studentCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new StudentManualRow(
                        resultSet.getLong("student_pk"),
                        resultSet.getString("student_code"),
                        resultSet.getString("full_name")
                );
            }
        }
    }

    private boolean isDuplicateAttendance(Connection connection, int sessionId, String studentCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_DUPLICATE_SQL)) {
            statement.setInt(1, sessionId);
            statement.setString(2, normalizeStudentCode(studentCode));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private long insertAttendanceRecord(Connection connection, int sessionId, long studentPk, int teacherId,
            String subjectName, AttendanceSource source, AttendanceStatus status, String note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ATTENDANCE_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, sessionId);
            statement.setLong(2, studentPk);
            statement.setInt(3, teacherId);
            statement.setString(4, subjectName);
            statement.setString(5, source.name());
            statement.setString(6, status.name());
            statement.setString(7, note);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Attendance insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertScanLog(Connection connection, Integer sessionId, Long studentPk, String tokenHash,
            String scanResult, String rejectionReason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SCAN_LOG_SQL)) {
            if (sessionId == null || sessionId <= 0) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setInt(1, sessionId);
            }
            if (studentPk == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, studentPk);
            }
            if (tokenHash == null || tokenHash.isBlank()) {
                statement.setNull(3, java.sql.Types.VARCHAR);
            } else {
                statement.setString(3, tokenHash);
            }
            statement.setString(4, "QR scan received.");
            statement.setString(5, scanResult);
            if (rejectionReason == null || rejectionReason.isBlank()) {
                statement.setNull(6, java.sql.Types.VARCHAR);
            } else {
                statement.setString(6, rejectionReason);
            }
            statement.executeUpdate();
        }
    }

    private void updateQrLastUsed(Connection connection, long qrId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_QR_LAST_USED_SQL)) {
            statement.setLong(1, qrId);
            statement.executeUpdate();
        }
    }

    private void insertAuditLog(Connection connection, int actorUserId, String actionType, String entityType,
            String entityId, String oldValuesJson, String newValuesJson, String notes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT_LOG_SQL)) {
            statement.setInt(1, actorUserId);
            statement.setString(2, actionType);
            statement.setString(3, entityType);
            statement.setString(4, entityId);
            statement.setString(5, oldValuesJson);
            statement.setString(6, newValuesJson);
            statement.setString(7, notes);
            statement.executeUpdate();
        }
    }

    private AttendanceStatus deriveAttendanceStatus(LiveSessionContext session) {
        if (session.schedule == null || session.session.isOverrideSession()) {
            return AttendanceStatus.PRESENT;
        }
        LocalTime lateCutoff = session.schedule.startTime.plusMinutes(15);
        return LocalTime.now().isAfter(lateCutoff) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
    }

    private AttendanceRecord mapAttendanceRecord(ResultSet resultSet) throws SQLException {
        return new AttendanceRecord(
                resultSet.getInt("attendance_id"),
                resultSet.getString("student_code"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getString("full_name"),
                resultSet.getString("subject_name"),
                resultSet.getTimestamp("recorded_at").toLocalDateTime(),
                mapSource(resultSet.getString("attendance_source")),
                mapStatus(resultSet.getString("attendance_status")),
                resultSet.getString("note")
        );
    }

    private AttendanceSession mapSession(ResultSet resultSet, boolean overrideSession) throws SQLException {
        SessionState state = mapSessionState(resultSet.getString("session_state"));
        String note = overrideSession ? safe(resultSet.getString("override_reason")) : "Session #" + resultSet.getInt("session_id") + " - schedule window";
        return new AttendanceSession(
                resultSet.getInt("session_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getString("subject_name"),
                state,
                toLocalDateTime(resultSet.getTimestamp("opened_at")),
                resultSet.getString("room_name"),
                note,
                overrideSession
        );
    }

    private SessionState mapSessionState(String value) {
        try {
            return SessionState.valueOf(value == null ? "LOCKED" : value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            return SessionState.LOCKED;
        }
    }

    private AttendanceSource mapSource(String value) {
        try {
            return AttendanceSource.valueOf(value == null ? "QR" : value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            return AttendanceSource.QR;
        }
    }

    private AttendanceStatus mapStatus(String value) {
        try {
            return AttendanceStatus.valueOf(value == null ? "PRESENT" : value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            return AttendanceStatus.PRESENT;
        }
    }

    private DayOfWeek dayOfWeek(int value) {
        int safeValue = Math.max(1, Math.min(7, value));
        return DayOfWeek.of(safeValue);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeStudentCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ENGLISH);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildSessionJson(String subjectName, String state, String room, String note) {
        return "{"
                + "\"subject_name\":\"" + escapeJson(subjectName) + "\","
                + "\"state\":\"" + escapeJson(state) + "\","
                + "\"room\":\"" + escapeJson(room) + "\","
                + "\"note\":\"" + escapeJson(note) + "\""
                + "}";
    }

    private String buildAttendanceJson(String studentCode, String subjectName, AttendanceSource source, AttendanceStatus status, String note) {
        return "{"
                + "\"student_code\":\"" + escapeJson(studentCode) + "\","
                + "\"subject_name\":\"" + escapeJson(subjectName) + "\","
                + "\"source\":\"" + source.name() + "\","
                + "\"status\":\"" + status.name() + "\","
                + "\"note\":\"" + escapeJson(note) + "\""
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
            // Ignore auto-commit restore failures; the connection closes right after this.
        }
    }

    private static final class TeacherRow {

        private final int userId;
        private final String fullName;
        private final String accountStatus;

        private TeacherRow(int userId, String fullName, String accountStatus) {
            this.userId = userId;
            this.fullName = fullName;
            this.accountStatus = accountStatus;
        }
    }

    private static final class ScheduleWindowRow {

        private final int scheduleId;
        private final int teacherId;
        private final String subjectName;
        private final DayOfWeek day;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String roomName;

        private ScheduleWindowRow(int scheduleId, int teacherId, String subjectName, DayOfWeek day,
                LocalTime startTime, LocalTime endTime, String roomName) {
            this.scheduleId = scheduleId;
            this.teacherId = teacherId;
            this.subjectName = subjectName;
            this.day = day;
            this.startTime = startTime;
            this.endTime = endTime;
            this.roomName = roomName;
        }
    }

    private static final class LiveSessionContext {

        private final AttendanceSession session;
        private final ScheduleWindowRow schedule;

        private LiveSessionContext(AttendanceSession session, ScheduleWindowRow schedule) {
            this.session = session;
            this.schedule = schedule;
        }
    }

    private static final class StudentScanRow {

        private final long studentPk;
        private final String studentCode;
        private final String fullName;
        private final String email;
        private final Long qrId;

        private StudentScanRow(long studentPk, String studentCode, String fullName, String email, Long qrId) {
            this.studentPk = studentPk;
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.email = email;
            this.qrId = qrId;
        }
    }

    private static final class StudentManualRow {

        private final long studentPk;
        private final String studentCode;
        private final String fullName;

        private StudentManualRow(long studentPk, String studentCode, String fullName) {
            this.studentPk = studentPk;
            this.studentCode = studentCode;
            this.fullName = fullName;
        }
    }
}
