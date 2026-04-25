package ppb.qrattend.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.model.CoreModels;
import ppb.qrattend.model.CoreModels.AttendanceMethod;
import ppb.qrattend.model.CoreModels.AttendanceRecord;
import ppb.qrattend.model.CoreModels.AttendanceStatus;
import ppb.qrattend.model.CoreModels.ReportSummary;

public final class ReportService {

    private static final String BASE_SELECT = """
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
            ORDER BY ar.recorded_at DESC, ar.record_id DESC
            """;

    private final DatabaseManager databaseManager;

    public ReportService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public ReportService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ServiceResult<List<AttendanceRecord>> getRecords(Integer teacherId, Integer sectionId, Integer subjectId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        String whereClause = buildWhereClause(teacherId, sectionId, subjectId);
        List<AttendanceRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(BASE_SELECT.formatted(whereClause))) {
            fillFilters(statement, teacherId, sectionId, subjectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
            return ServiceResult.success("Report records loaded.", records);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the report.");
        }
    }

    public ServiceResult<ReportSummary> getSummary(Integer teacherId, Integer sectionId, Integer subjectId) {
        ServiceResult<List<AttendanceRecord>> recordsResult = getRecords(teacherId, sectionId, subjectId);
        if (!recordsResult.isSuccess()) {
            return ServiceResult.failure(recordsResult.getMessage());
        }
        List<AttendanceRecord> records = recordsResult.getData();
        int present = 0;
        int late = 0;
        java.util.LinkedHashSet<String> studentCodes = new java.util.LinkedHashSet<>();
        for (AttendanceRecord record : records) {
            studentCodes.add(record.studentCode());
            if (record.status() == AttendanceStatus.LATE) {
                late++;
            } else {
                present++;
            }
        }
        ReportSummary summary = new ReportSummary(studentCodes.size(), present, late, records.size());
        return ServiceResult.success("Report summary loaded.", summary);
    }

    public ServiceResult<List<String>> getSubjectsForReport(Integer teacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        String sql = """
                SELECT DISTINCT sub.subject_name
                FROM schedules sc
                INNER JOIN subjects sub
                    ON sub.subject_id = sc.subject_id
                %s
                ORDER BY sub.subject_name ASC
                """.formatted(teacherId == null ? "" : "WHERE sc.teacher_user_id = ?");
        List<String> names = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (teacherId != null) {
                statement.setInt(1, teacherId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString("subject_name"));
                }
            }
            return ServiceResult.success("Subjects loaded.", names);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load subjects.");
        }
    }

    private String buildWhereClause(Integer teacherId, Integer sectionId, Integer subjectId) {
        List<String> parts = new ArrayList<>();
        if (teacherId != null) {
            parts.add("sess.teacher_user_id = ?");
        }
        if (sectionId != null) {
            parts.add("sess.section_id = ?");
        }
        if (subjectId != null) {
            parts.add("sess.subject_id = ?");
        }
        return parts.isEmpty() ? "" : "WHERE " + String.join(" AND ", parts);
    }

    private void fillFilters(PreparedStatement statement, Integer teacherId, Integer sectionId, Integer subjectId) throws SQLException {
        int index = 1;
        if (teacherId != null) {
            statement.setInt(index++, teacherId);
        }
        if (sectionId != null) {
            statement.setInt(index++, sectionId);
        }
        if (subjectId != null) {
            statement.setInt(index, subjectId);
        }
    }

    private AttendanceRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new AttendanceRecord(
                resultSet.getInt("record_id"),
                resultSet.getString("student_code"),
                resultSet.getString("student_name"),
                resultSet.getString("section_name"),
                resultSet.getString("subject_name"),
                resultSet.getTimestamp("recorded_at").toLocalDateTime(),
                AttendanceMethod.valueOf(resultSet.getString("attendance_method")),
                AttendanceStatus.valueOf(resultSet.getString("attendance_status")),
                resultSet.getString("note")
        );
    }

    public ServiceResult<Integer> exportCsv(List<AttendanceRecord> records, java.io.File file) {
        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.println("Student ID,Student Name,Section,Subject,Date/Time,Method,Status,Note");
            int count = 0;
            for (AttendanceRecord record : records) {
                String row = escapeCsv(record.studentCode())
                        + "," + escapeCsv(record.studentName())
                        + "," + escapeCsv(record.sectionName())
                        + "," + escapeCsv(record.subjectName())
                        + "," + escapeCsv(record.recordedAt().format(CoreModels.DATE_TIME_FORMAT))
                        + "," + escapeCsv(record.method().getLabel())
                        + "," + escapeCsv(record.status().getLabel())
                        + "," + escapeCsv(record.note());
                writer.println(row);
                count++;
            }
            return ServiceResult.success("Exported " + count + " rows.", count);
        } catch (IOException ex) {
            return ServiceResult.failure("Could not write the file: " + ex.getMessage());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
