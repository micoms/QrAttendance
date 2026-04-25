package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.db.SecurityUtil;
import ppb.qrattend.model.CoreModels.AttendanceMethod;
import ppb.qrattend.model.CoreModels.AttendanceRecord;
import ppb.qrattend.model.CoreModels.AttendanceSession;
import ppb.qrattend.model.CoreModels.AttendanceStatus;
import ppb.qrattend.model.CoreModels.SessionStatus;
import ppb.qrattend.model.CoreModels.Student;
import ppb.qrattend.util.AppClock;

public final class AttendanceService {

    private static final String SELECT_OPEN_TEMP_SQL = """
            SELECT
                s.session_id,
                s.teacher_user_id,
                s.section_id,
                sec.section_name,
                s.subject_id,
                sub.subject_name,
                COALESCE(r.room_name, '') AS room_name,
                s.session_date,
                s.opened_at,
                s.is_temporary,
                COALESCE(s.reason, '') AS reason,
                s.status
            FROM attendance_sessions s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = s.subject_id
            LEFT JOIN rooms r
                ON r.room_id = s.room_id
            WHERE s.teacher_user_id = ?
              AND s.status = 'OPEN'
              AND s.is_temporary = 1
            ORDER BY s.session_id DESC
            LIMIT 1
            """;

    // Scheduled classes should stay open for the full saved end minute.
    // Example: a class that ends at 3:00 PM is still treated as open at 3:00 PM,
    // then closes after that minute has passed.
    private static final String SELECT_CURRENT_SCHEDULE_SQL = """
            SELECT
                sc.schedule_id,
                sc.teacher_user_id,
                sc.section_id,
                sec.section_name,
                sc.subject_id,
                sub.subject_name,
                sc.room_id,
                r.room_name,
                sc.day_of_week,
                sc.start_time,
                sc.end_time
            FROM schedules sc
            INNER JOIN sections sec
                ON sec.section_id = sc.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = sc.subject_id
            INNER JOIN rooms r
                ON r.room_id = sc.room_id
            WHERE sc.teacher_user_id = ?
              AND sc.is_active = 1
              AND sc.day_of_week = ?
              AND sc.start_time <= ?
              AND sc.end_time >= ?
            ORDER BY sc.start_time ASC
            LIMIT 1
            """;

    private static final String CLOSE_OLD_SCHEDULE_SESSIONS_SQL = """
            UPDATE attendance_sessions
            SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP
            WHERE teacher_user_id = ?
              AND status = 'OPEN'
              AND is_temporary = 0
              AND (session_date <> ? OR schedule_id <> ?)
            """;

    private static final String SELECT_OPEN_SCHEDULE_SESSION_SQL = """
            SELECT
                s.session_id,
                s.teacher_user_id,
                s.section_id,
                sec.section_name,
                s.subject_id,
                sub.subject_name,
                COALESCE(r.room_name, '') AS room_name,
                s.session_date,
                s.opened_at,
                s.is_temporary,
                COALESCE(s.reason, '') AS reason,
                s.status
            FROM attendance_sessions s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = s.subject_id
            LEFT JOIN rooms r
                ON r.room_id = s.room_id
            WHERE s.teacher_user_id = ?
              AND s.schedule_id = ?
              AND s.session_date = ?
              AND s.status = 'OPEN'
              AND s.is_temporary = 0
            LIMIT 1
            """;

    private static final String INSERT_SESSION_SQL = """
            INSERT INTO attendance_sessions
                (teacher_user_id, schedule_id, section_id, subject_id, room_id, session_date, is_temporary, reason, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
            """;

    private static final String CLOSE_TEMP_SQL = """
            UPDATE attendance_sessions
            SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP
            WHERE teacher_user_id = ?
              AND status = 'OPEN'
              AND is_temporary = 1
            """;

    private static final String SELECT_STUDENTS_FOR_SECTION_SQL = """
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
              AND s.is_active = 1
            ORDER BY s.full_name ASC
            """;

    private static final String SELECT_STUDENT_FOR_QR_SQL = """
            SELECT
                s.student_id,
                s.student_code,
                s.full_name,
                sec.section_name
            FROM students s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            WHERE s.section_id = ?
              AND s.is_active = 1
              AND (
                    s.qr_hash = ?
                 OR UPPER(s.student_code) = ?
                 OR LOWER(s.email) = ?
              )
            LIMIT 1
            """;

    private static final String SELECT_STUDENT_IN_SECTION_SQL = """
            SELECT
                s.student_id,
                s.student_code,
                s.full_name,
                sec.section_name
            FROM students s
            INNER JOIN sections sec
                ON sec.section_id = s.section_id
            WHERE s.student_id = ?
              AND s.section_id = ?
              AND s.is_active = 1
            LIMIT 1
            """;

    private static final String CHECK_DUPLICATE_SQL = """
            SELECT 1
            FROM attendance_records
            WHERE session_id = ?
              AND student_id = ?
            LIMIT 1
            """;

    private static final String INSERT_RECORD_SQL = """
            INSERT INTO attendance_records
                (session_id, student_id, attendance_method, attendance_status, note)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String INSERT_ABSENT_SQL =
            "INSERT INTO attendance_records " +
            "    (session_id, student_id, attendance_method, attendance_status, note) " +
            "VALUES (?, ?, 'MANUAL', 'ABSENT', '')";

    private static final String SELECT_RECENT_SQL = """
            SELECT
                ar.record_id,
                st.student_code,
                st.full_name AS student_name,
                sec.section_name,
                sub.subject_name,
                ar.recorded_at,
                ar.attendance_method,
                ar.attendance_status,
                ar.note
            FROM attendance_records ar
            INNER JOIN attendance_sessions sess
                ON sess.session_id = ar.session_id
            INNER JOIN students st
                ON st.student_id = ar.student_id
            INNER JOIN sections sec
                ON sec.section_id = st.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = sess.subject_id
            %s
            ORDER BY ar.record_id DESC
            LIMIT ?
            """;

    private final DatabaseManager databaseManager;

    public AttendanceService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public AttendanceService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ServiceResult<AttendanceSession> getCurrentSessionForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher not found.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            AttendanceSession tempSession = loadTemporarySession(connection, teacherId);
            if (tempSession != null) {
                return ServiceResult.success("Temporary class loaded.", tempSession);
            }

            ScheduleRow currentSchedule = loadCurrentSchedule(connection, teacherId);
            if (currentSchedule == null) {
                closeOpenScheduleSessions(connection, teacherId, -1);
                AttendanceSession none = new AttendanceSession(
                        0, teacherId, 0, "No class", 0, "No class", "", AppClock.today(),
                        AppClock.nowDateTime(), false, "", SessionStatus.NONE
                );
                return ServiceResult.success("No class is open.", none);
            }

            closeOpenScheduleSessions(connection, teacherId, currentSchedule.scheduleId);
            AttendanceSession session = loadOrCreateScheduleSession(connection, currentSchedule);
            return ServiceResult.success("Current class loaded.", session);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the class.");
        }
    }

    public ServiceResult<AttendanceSession> startTemporaryClass(int teacherId, int sectionId, int subjectId, String reason) {
        if (teacherId <= 0 || sectionId <= 0 || subjectId <= 0) {
            return ServiceResult.failure("Choose the section and subject first.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                AttendanceSession tempSession = loadTemporarySession(connection, teacherId);
                if (tempSession != null) {
                    connection.rollback();
                    return ServiceResult.failure("A temporary class is already open.");
                }
                ScheduleRow currentSchedule = loadCurrentSchedule(connection, teacherId);
                if (currentSchedule != null) {
                    connection.rollback();
                    return ServiceResult.failure("You have a scheduled class right now. Use that instead of opening a temporary one.");
                }
                int sessionId = insertSession(connection, teacherId, null, sectionId, subjectId, null, true, safe(reason));
                connection.commit();
                return ServiceResult.success("Temporary class started.", loadSessionById(connection, sessionId));
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not start the temporary class.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not start the temporary class.");
        }
    }

    public ServiceResult<Void> endTemporaryClass(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher not found.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(CLOSE_TEMP_SQL)) {
            statement.setInt(1, teacherId);
            int count = statement.executeUpdate();
            if (count == 0) {
                return ServiceResult.failure("No temporary class is open.");
            }
            return ServiceResult.success("Temporary class ended.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not end the temporary class.");
        }
    }

    public ServiceResult<List<Student>> getCurrentClassStudents(int teacherId) {
        ServiceResult<AttendanceSession> sessionResult = getCurrentSessionForTeacher(teacherId);
        if (!sessionResult.isSuccess() || sessionResult.getData() == null) {
            return ServiceResult.failure("Could not load the class.");
        }
        AttendanceSession session = sessionResult.getData();
        if (session.status() == SessionStatus.NONE || session.sectionId() <= 0) {
            return ServiceResult.success("No class is open.", new ArrayList<>());
        }
        return loadStudentsForSection(session.sectionId());
    }

    public ServiceResult<AttendanceRecord> markAttendanceFromQr(int teacherId, String qrValue) {
        String cleanValue = safe(qrValue);
        if (cleanValue.isBlank()) {
            return ServiceResult.failure("Scan a student QR code first.");
        }
        ServiceResult<AttendanceSession> sessionResult = getCurrentSessionForTeacher(teacherId);
        if (!sessionResult.isSuccess() || sessionResult.getData() == null) {
            return ServiceResult.failure("Could not load the class.");
        }
        AttendanceSession session = sessionResult.getData();
        if (session.status() == SessionStatus.NONE) {
            return ServiceResult.failure("No class is open.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            StudentRow student = loadStudentForQr(connection, session.sectionId(), cleanValue);
            if (student == null) {
                return ServiceResult.failure("That QR code does not match a student in this class.");
            }
            if (hasAttendance(connection, session.id(), student.studentId)) {
                return ServiceResult.warning(student.fullName + " is already marked.", null);
            }
            AttendanceRecord record = insertAttendance(
                    connection,
                    session.id(),
                    session.subjectName(),
                    student.studentId,
                    student.studentCode,
                    student.fullName,
                    student.sectionName,
                    AttendanceMethod.QR,
                    AttendanceStatus.PRESENT,
                    ""
            );
            return ServiceResult.success(student.fullName + " marked present.", record);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save attendance.");
        }
    }

    public ServiceResult<AttendanceRecord> markManualAttendance(int teacherId, int studentId, String note) {
        ServiceResult<AttendanceSession> sessionResult = getCurrentSessionForTeacher(teacherId);
        if (!sessionResult.isSuccess() || sessionResult.getData() == null) {
            return ServiceResult.failure("Could not load the class.");
        }
        AttendanceSession session = sessionResult.getData();
        if (session.status() == SessionStatus.NONE) {
            return ServiceResult.failure("No class is open.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            StudentRow student = loadStudentInSection(connection, studentId, session.sectionId());
            if (student == null) {
                return ServiceResult.failure("Choose a student from this class.");
            }
            if (hasAttendance(connection, session.id(), student.studentId)) {
                return ServiceResult.warning(student.fullName + " is already marked.", null);
            }
            AttendanceRecord record = insertAttendance(
                    connection,
                    session.id(),
                    session.subjectName(),
                    student.studentId,
                    student.studentCode,
                    student.fullName,
                    student.sectionName,
                    AttendanceMethod.MANUAL,
                    AttendanceStatus.PRESENT,
                    safe(note)
            );
            return ServiceResult.success(student.fullName + " marked present.", record);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save attendance.");
        }
    }

    public ServiceResult<List<AttendanceRecord>> getRecentRecords(Integer teacherId, int limit) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<AttendanceRecord> records = new ArrayList<>();
        String filter = teacherId == null ? "" : "WHERE sess.teacher_user_id = ?";
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_RECENT_SQL.formatted(filter))) {
            int index = 1;
            if (teacherId != null) {
                statement.setInt(index++, teacherId);
            }
            statement.setInt(index, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new AttendanceRecord(
                            resultSet.getInt("record_id"),
                            resultSet.getString("student_code"),
                            resultSet.getString("student_name"),
                            resultSet.getString("section_name"),
                            resultSet.getString("subject_name"),
                            resultSet.getTimestamp("recorded_at").toLocalDateTime(),
                            AttendanceMethod.valueOf(resultSet.getString("attendance_method")),
                            AttendanceStatus.valueOf(resultSet.getString("attendance_status")),
                            resultSet.getString("note")
                    ));
                }
            }
            return ServiceResult.success("Attendance records loaded.", records);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load attendance records.");
        }
    }

    public ServiceResult<Integer> markAllAbsent(int teacherId) {
        ServiceResult<AttendanceSession> sessionResult = getCurrentSessionForTeacher(teacherId);
        if (!sessionResult.isSuccess() || sessionResult.getData() == null) {
            return ServiceResult.failure("Could not load the class.");
        }
        AttendanceSession session = sessionResult.getData();
        if (session.status() == SessionStatus.NONE) {
            return ServiceResult.failure("No class is open.");
        }

        ServiceResult<List<Student>> studentsResult = loadStudentsForSection(session.sectionId());
        if (!studentsResult.isSuccess() || studentsResult.getData() == null) {
            return ServiceResult.failure("Could not load students for this class.");
        }
        List<Student> students = studentsResult.getData();

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int count = 0;
                for (int i = 0; i < students.size(); i++) {
                    Student student = students.get(i);
                    if (!hasAttendance(connection, session.id(), student.id())) {
                        try (PreparedStatement statement = connection.prepareStatement(INSERT_ABSENT_SQL)) {
                            statement.setInt(1, session.id());
                            statement.setInt(2, student.id());
                            statement.executeUpdate();
                        }
                        count++;
                    }
                }
                connection.commit();
                if (count == 0) {
                    return ServiceResult.success("All students are already marked.", 0);
                }
                return ServiceResult.success("Marked " + count + " students absent.", count);
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not mark students absent.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not mark students absent.");
        }
    }

    private ServiceResult<List<Student>> loadStudentsForSection(int sectionId) {
        List<Student> students = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_STUDENTS_FOR_SECTION_SQL)) {
            statement.setInt(1, sectionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    students.add(new Student(
                            resultSet.getInt("student_id"),
                            resultSet.getString("student_code"),
                            resultSet.getString("full_name"),
                            resultSet.getString("email"),
                            resultSet.getInt("section_id"),
                            resultSet.getString("section_name"),
                            resultSet.getBoolean("is_active"),
                            mapEmailStatus(resultSet.getString("qr_status"))
                    ));
                }
            }
            return ServiceResult.success("Students loaded.", students);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load students.");
        }
    }

    private AttendanceSession loadTemporarySession(Connection connection, int teacherId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_OPEN_TEMP_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapSession(resultSet);
            }
        }
    }

    private ScheduleRow loadCurrentSchedule(Connection connection, int teacherId) throws SQLException {
        LocalDate today = AppClock.today();
        LocalTime now = AppClock.nowTime();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CURRENT_SCHEDULE_SQL)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, today.getDayOfWeek().getValue());
            statement.setTime(3, java.sql.Time.valueOf(now));
            statement.setTime(4, java.sql.Time.valueOf(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new ScheduleRow(
                        resultSet.getInt("schedule_id"),
                        teacherId,
                        resultSet.getInt("section_id"),
                        resultSet.getString("section_name"),
                        resultSet.getInt("subject_id"),
                        resultSet.getString("subject_name"),
                        resultSet.getInt("room_id"),
                        resultSet.getString("room_name")
                );
            }
        }
    }

    private void closeOpenScheduleSessions(Connection connection, int teacherId, int currentScheduleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CLOSE_OLD_SCHEDULE_SESSIONS_SQL)) {
            statement.setInt(1, teacherId);
            statement.setDate(2, java.sql.Date.valueOf(AppClock.today()));
            statement.setInt(3, currentScheduleId);
            statement.executeUpdate();
        }
    }

    private AttendanceSession loadOrCreateScheduleSession(Connection connection, ScheduleRow schedule) throws SQLException {
        // First check if a session already exists for this schedule today
        try (PreparedStatement statement = connection.prepareStatement(SELECT_OPEN_SCHEDULE_SESSION_SQL)) {
            statement.setInt(1, schedule.teacherId);
            statement.setInt(2, schedule.scheduleId);
            statement.setDate(3, java.sql.Date.valueOf(AppClock.today()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapSession(resultSet);
                }
            }
        }

        // No session yet — create one inside a transaction so a failed load
        // does not leave an orphaned open session row in the database
        connection.setAutoCommit(false);
        try {
            int sessionId = insertSession(connection, schedule.teacherId, schedule.scheduleId, schedule.sectionId,
                    schedule.subjectId, schedule.roomId, false, "");
            AttendanceSession session = loadSessionById(connection, sessionId);
            connection.commit();
            return session;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private int insertSession(Connection connection, int teacherId, Integer scheduleId, int sectionId, int subjectId,
            Integer roomId, boolean temporary, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SESSION_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, teacherId);
            if (scheduleId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, scheduleId);
            }
            statement.setInt(3, sectionId);
            statement.setInt(4, subjectId);
            if (roomId == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, roomId);
            }
            statement.setDate(6, java.sql.Date.valueOf(AppClock.today()));
            statement.setBoolean(7, temporary);
            statement.setString(8, safe(reason));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Session key was not returned.");
                }
                return keys.getInt(1);
            }
        }
    }

    private AttendanceSession loadSessionById(Connection connection, int sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    s.session_id,
                    s.teacher_user_id,
                    s.section_id,
                    sec.section_name,
                    s.subject_id,
                    sub.subject_name,
                    COALESCE(r.room_name, '') AS room_name,
                    s.session_date,
                    s.opened_at,
                    s.is_temporary,
                    COALESCE(s.reason, '') AS reason,
                    s.status
                FROM attendance_sessions s
                INNER JOIN sections sec ON sec.section_id = s.section_id
                INNER JOIN subjects sub ON sub.subject_id = s.subject_id
                LEFT JOIN rooms r ON r.room_id = s.room_id
                WHERE s.session_id = ?
                LIMIT 1
                """)) {
            statement.setInt(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Session not found.");
                }
                return mapSession(resultSet);
            }
        }
    }

    private StudentRow loadStudentForQr(Connection connection, int sectionId, String qrValue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STUDENT_FOR_QR_SQL)) {
            statement.setInt(1, sectionId);
            statement.setString(2, SecurityUtil.sha256Hex(qrValue));
            statement.setString(3, qrValue.trim().toUpperCase());
            statement.setString(4, qrValue.trim().toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new StudentRow(
                        resultSet.getInt("student_id"),
                        resultSet.getString("student_code"),
                        resultSet.getString("full_name"),
                        resultSet.getString("section_name")
                );
            }
        }
    }

    private StudentRow loadStudentInSection(Connection connection, int studentId, int sectionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STUDENT_IN_SECTION_SQL)) {
            statement.setInt(1, studentId);
            statement.setInt(2, sectionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new StudentRow(
                        resultSet.getInt("student_id"),
                        resultSet.getString("student_code"),
                        resultSet.getString("full_name"),
                        resultSet.getString("section_name")
                );
            }
        }
    }

    private boolean hasAttendance(Connection connection, int sessionId, int studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHECK_DUPLICATE_SQL)) {
            statement.setInt(1, sessionId);
            statement.setInt(2, studentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private AttendanceRecord insertAttendance(Connection connection, int sessionId, String subjectName, int studentId,
            String studentCode, String studentName, String sectionName, AttendanceMethod method,
            AttendanceStatus status, String note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RECORD_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, sessionId);
            statement.setInt(2, studentId);
            statement.setString(3, method.name());
            statement.setString(4, status.name());
            statement.setString(5, safe(note));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Attendance key was not returned.");
                }
                return new AttendanceRecord(
                        keys.getInt(1),
                        studentCode,
                        studentName,
                        sectionName,
                        subjectName,
                        AppClock.nowDateTime(),
                        method,
                        status,
                        safe(note)
                );
            }
        }
    }

    private AttendanceSession mapSession(ResultSet resultSet) throws SQLException {
        String rawStatus = resultSet.getString("status");
        SessionStatus status = "OPEN".equalsIgnoreCase(rawStatus) ? SessionStatus.OPEN : SessionStatus.CLOSED;
        return new AttendanceSession(
                resultSet.getInt("session_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getInt("section_id"),
                resultSet.getString("section_name"),
                resultSet.getInt("subject_id"),
                resultSet.getString("subject_name"),
                resultSet.getString("room_name"),
                resultSet.getDate("session_date").toLocalDate(),
                resultSet.getTimestamp("opened_at").toLocalDateTime(),
                resultSet.getBoolean("is_temporary"),
                safe(resultSet.getString("reason")),
                status
        );
    }

    private ppb.qrattend.model.CoreModels.EmailStatus mapEmailStatus(String raw) {
        if (raw == null) {
            return ppb.qrattend.model.CoreModels.EmailStatus.NOT_SENT;
        }
        return switch (raw.trim().toUpperCase()) {
            case "SENT" -> ppb.qrattend.model.CoreModels.EmailStatus.SENT;
            case "FAILED" -> ppb.qrattend.model.CoreModels.EmailStatus.FAILED;
            default -> ppb.qrattend.model.CoreModels.EmailStatus.NOT_SENT;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ScheduleRow(int scheduleId, int teacherId, int sectionId, String sectionName,
            int subjectId, String subjectName, int roomId, String roomName) {
    }

    private record StudentRow(int studentId, String studentCode, String fullName, String sectionName) {
    }
}
