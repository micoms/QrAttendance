package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.model.AppDomain;

public final class ReportService {

    public static final class AdminDashboardMetrics {

        private final int teacherCount;
        private final int activeClassCount;
        private final int pendingRequestCount;
        private final int failedEmailCount;
        private final int attendanceRecordCount;

        public AdminDashboardMetrics(int teacherCount, int activeClassCount, int pendingRequestCount,
                int failedEmailCount, int attendanceRecordCount) {
            this.teacherCount = teacherCount;
            this.activeClassCount = activeClassCount;
            this.pendingRequestCount = pendingRequestCount;
            this.failedEmailCount = failedEmailCount;
            this.attendanceRecordCount = attendanceRecordCount;
        }

        public int getTeacherCount() {
            return teacherCount;
        }

        public int getActiveClassCount() {
            return activeClassCount;
        }

        public int getPendingRequestCount() {
            return pendingRequestCount;
        }

        public int getFailedEmailCount() {
            return failedEmailCount;
        }

        public int getAttendanceRecordCount() {
            return attendanceRecordCount;
        }
    }

    public static final class TeacherDashboardMetrics {

        private final int studentCount;
        private final int pendingRequestCount;
        private final int recentAttendanceCount;
        private final int todayClassCount;

        public TeacherDashboardMetrics(int studentCount, int pendingRequestCount, int recentAttendanceCount, int todayClassCount) {
            this.studentCount = studentCount;
            this.pendingRequestCount = pendingRequestCount;
            this.recentAttendanceCount = recentAttendanceCount;
            this.todayClassCount = todayClassCount;
        }

        public int getStudentCount() {
            return studentCount;
        }

        public int getPendingRequestCount() {
            return pendingRequestCount;
        }

        public int getRecentAttendanceCount() {
            return recentAttendanceCount;
        }

        public int getTodayClassCount() {
            return todayClassCount;
        }
    }

    private static final String SUBJECTS_SQL = """
            SELECT subject_name
            FROM (
                SELECT DISTINCT subject_name
                FROM teacher_schedules
                WHERE schedule_status = 'APPROVED'
                %s
                UNION
                SELECT DISTINCT subject_name
                FROM attendance_records
                %s
            ) subject_list
            WHERE subject_name IS NOT NULL AND subject_name <> ''
            ORDER BY subject_name ASC
            """;

    private static final String REPORT_SQL = """
            SELECT
                sp.student_code,
                sp.full_name,
                ar.subject_name,
                ar.recorded_at,
                ar.attendance_source,
                ar.attendance_status,
                COALESCE(ar.note, '') AS note
            FROM attendance_records ar
            INNER JOIN student_profiles sp
                ON sp.student_pk = ar.student_pk
            %s
            ORDER BY ar.recorded_at DESC, ar.attendance_id DESC
            """;

    private final DatabaseManager databaseManager;

    public ReportService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public ReportService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<List<String>> getSubjectOptions(Integer teacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String scheduleFilter = teacherId == null ? "" : "AND teacher_user_id = ?";
        String attendanceFilter = teacherId == null ? "" : "WHERE teacher_user_id = ?";
        String sql = SUBJECTS_SQL.formatted(scheduleFilter, attendanceFilter);

        List<String> subjects = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (teacherId != null) {
                statement.setInt(1, teacherId);
                statement.setInt(2, teacherId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    subjects.add(resultSet.getString("subject_name"));
                }
            }
            return ServiceResult.success("Loaded report subjects.", subjects);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load report subjects.");
        }
    }

    public ServiceResult<String> exportAttendanceSummary(Integer teacherId, String subjectFilter) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String normalizedSubject = normalizeSubjectFilter(subjectFilter);
        List<String> conditions = new ArrayList<>();
        if (teacherId != null) {
            conditions.add("ar.teacher_user_id = ?");
        }
        if (!normalizedSubject.isBlank()) {
            conditions.add("ar.subject_name = ?");
        }
        String whereClause = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        String sql = REPORT_SQL.formatted(whereClause);

        StringBuilder builder = new StringBuilder();
        builder.append("student_id,student_name,subject,timestamp,source,status,note").append(System.lineSeparator());

        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            if (teacherId != null) {
                statement.setInt(parameterIndex++, teacherId);
            }
            if (!normalizedSubject.isBlank()) {
                statement.setString(parameterIndex, normalizedSubject);
            }
            int rows = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    builder.append(csv(resultSet.getString("student_code"))).append(',')
                            .append(csv(resultSet.getString("full_name"))).append(',')
                            .append(csv(resultSet.getString("subject_name"))).append(',')
                            .append(csv(resultSet.getTimestamp("recorded_at").toLocalDateTime().format(AppDomain.DATE_TIME_FORMAT))).append(',')
                            .append(csv(resultSet.getString("attendance_source"))).append(',')
                            .append(csv(resultSet.getString("attendance_status"))).append(',')
                            .append(csv(resultSet.getString("note")))
                            .append(System.lineSeparator());
                    rows++;
                }
            }
            if (rows == 0) {
                builder.append("No matching records found.");
            }
            return ServiceResult.success("Attendance report loaded.", builder.toString());
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the attendance report.");
        }
    }

    public ServiceResult<AdminDashboardMetrics> getAdminDashboardMetrics() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            int teacherCount = count(connection, "SELECT COUNT(*) FROM users WHERE role = 'TEACHER' AND account_status = 'ACTIVE'");
            int pendingSchedule = count(connection, "SELECT COUNT(*) FROM schedule_change_requests WHERE request_status = 'PENDING'");
            int pendingRoster = count(connection, "SELECT COUNT(*) FROM student_roster_change_requests WHERE request_status = 'PENDING'");
            int failedEmailCount = count(connection, "SELECT COUNT(*) FROM email_dispatch_logs WHERE delivery_status = 'FAILED'");
            int attendanceCount = count(connection, "SELECT COUNT(*) FROM attendance_records");

            int activeClassCount;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM teacher_schedules
                    WHERE schedule_status = 'APPROVED'
                      AND day_of_week = ?
                      AND start_time <= CURRENT_TIME
                      AND end_time >= CURRENT_TIME
                    """)) {
                statement.setInt(1, LocalDate.now().getDayOfWeek().getValue());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    activeClassCount = resultSet.getInt(1);
                }
            }

            AdminDashboardMetrics metrics = new AdminDashboardMetrics(
                    teacherCount,
                    activeClassCount,
                    pendingSchedule + pendingRoster,
                    failedEmailCount,
                    attendanceCount
            );
            return ServiceResult.success("Dashboard numbers loaded.", metrics);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the dashboard numbers.");
        }
    }

    public ServiceResult<TeacherDashboardMetrics> getTeacherDashboardMetrics(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            int studentCount;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM teacher_student_assignments
                    WHERE teacher_user_id = ?
                      AND assignment_status = 'ACTIVE'
                    """)) {
                statement.setInt(1, teacherId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    studentCount = resultSet.getInt(1);
                }
            }

            int pendingSchedule;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM schedule_change_requests
                    WHERE teacher_user_id = ?
                      AND request_status = 'PENDING'
                    """)) {
                statement.setInt(1, teacherId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    pendingSchedule = resultSet.getInt(1);
                }
            }

            int pendingRoster;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM student_roster_change_requests
                    WHERE teacher_user_id = ?
                      AND request_status = 'PENDING'
                    """)) {
                statement.setInt(1, teacherId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    pendingRoster = resultSet.getInt(1);
                }
            }

            int recentAttendance;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM attendance_records
                    WHERE teacher_user_id = ?
                      AND DATE(recorded_at) = CURRENT_DATE
                    """)) {
                statement.setInt(1, teacherId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    recentAttendance = resultSet.getInt(1);
                }
            }

            int todayClasses;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM teacher_schedules
                    WHERE teacher_user_id = ?
                      AND day_of_week = ?
                      AND schedule_status = 'APPROVED'
                    """)) {
                statement.setInt(1, teacherId);
                statement.setInt(2, LocalDate.now().getDayOfWeek().getValue());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    todayClasses = resultSet.getInt(1);
                }
            }

            TeacherDashboardMetrics metrics = new TeacherDashboardMetrics(
                    studentCount,
                    pendingSchedule + pendingRoster,
                    recentAttendance,
                    todayClasses
            );
            return ServiceResult.success("Teacher dashboard numbers loaded.", metrics);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the teacher dashboard.");
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String normalizeSubjectFilter(String subjectFilter) {
        String value = subjectFilter == null ? "" : subjectFilter.trim();
        if (value.isBlank() || "All Subjects".equalsIgnoreCase(value)) {
            return "";
        }
        return value;
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        String escaped = safe.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
