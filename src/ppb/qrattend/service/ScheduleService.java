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
import ppb.qrattend.model.AppDomain.ScheduleChangeRequest;
import ppb.qrattend.model.AppDomain.ScheduleRequestStatus;
import ppb.qrattend.model.AppDomain.ScheduleSlot;

public final class ScheduleService {

    private static final String SELECT_SCHEDULES_SQL = """
            SELECT schedule_id, teacher_user_id, subject_name, day_of_week, start_time, end_time, room_name, schedule_status
            FROM teacher_schedules
            ORDER BY day_of_week ASC, start_time ASC, schedule_id ASC
            """;

    private static final String SELECT_SCHEDULES_FOR_TEACHER_SQL = """
            SELECT schedule_id, teacher_user_id, subject_name, day_of_week, start_time, end_time, room_name, schedule_status
            FROM teacher_schedules
            WHERE teacher_user_id = ?
            ORDER BY day_of_week ASC, start_time ASC, schedule_id ASC
            """;

    private static final String SELECT_TODAY_SCHEDULES_SQL = """
            SELECT schedule_id, teacher_user_id, subject_name, day_of_week, start_time, end_time, room_name, schedule_status
            FROM teacher_schedules
            WHERE day_of_week = ?
              AND schedule_status = 'APPROVED'
            ORDER BY start_time ASC, schedule_id ASC
            """;

    private static final String SELECT_ACTIVE_SCHEDULE_SQL = """
            SELECT schedule_id, teacher_user_id, subject_name, day_of_week, start_time, end_time, room_name, schedule_status
            FROM teacher_schedules
            WHERE teacher_user_id = ?
              AND day_of_week = ?
              AND schedule_status = 'APPROVED'
              AND start_time <= ?
              AND end_time >= ?
            ORDER BY start_time ASC
            LIMIT 1
            """;

    private static final String SELECT_NEXT_SCHEDULE_SQL = """
            SELECT schedule_id, teacher_user_id, subject_name, day_of_week, start_time, end_time, room_name, schedule_status
            FROM teacher_schedules
            WHERE teacher_user_id = ?
              AND day_of_week = ?
              AND schedule_status = 'APPROVED'
              AND start_time > ?
            ORDER BY start_time ASC
            LIMIT 1
            """;

    private static final String SELECT_TEACHER_SQL = """
            SELECT user_id, full_name, role, account_status
            FROM users
            WHERE user_id = ?
              AND role = 'TEACHER'
            LIMIT 1
            """;

    private static final String SELECT_ADMIN_SQL = """
            SELECT user_id, full_name, role, account_status
            FROM users
            WHERE user_id = ?
              AND role = 'ADMIN'
            LIMIT 1
            """;

    private static final String SELECT_SCHEDULE_BY_TEACHER_SQL = """
            SELECT schedule_id, teacher_user_id, subject_code, subject_name, day_of_week, start_time, end_time, room_name, schedule_status
            FROM teacher_schedules
            WHERE schedule_id = ?
              AND teacher_user_id = ?
            LIMIT 1
            """;

    private static final String INSERT_SCHEDULE_SQL = """
            INSERT INTO teacher_schedules
                (teacher_user_id, subject_code, subject_name, day_of_week, start_time, end_time, room_name, schedule_status, created_by_user_id, updated_by_user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?)
            """;

    private static final String INSERT_REQUEST_SQL = """
            INSERT INTO schedule_change_requests
                (schedule_id, teacher_user_id, old_snapshot, requested_subject_name, requested_day_of_week,
                 requested_start_time, requested_end_time, requested_room_name, requested_snapshot, reason, request_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """;

    private static final String UPDATE_SCHEDULE_FROM_REQUEST_SQL = """
            UPDATE teacher_schedules
            SET subject_name = ?,
                day_of_week = ?,
                start_time = ?,
                end_time = ?,
                room_name = ?,
                updated_by_user_id = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE schedule_id = ?
            """;

    private static final String UPDATE_REQUEST_APPROVED_SQL = """
            UPDATE schedule_change_requests
            SET request_status = 'APPROVED',
                reviewed_by_user_id = ?,
                reviewed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE request_id = ?
            """;

    private static final String UPDATE_REQUEST_REJECTED_SQL = """
            UPDATE schedule_change_requests
            SET request_status = 'REJECTED',
                reviewed_by_user_id = ?,
                reviewed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE request_id = ?
            """;

    private static final String INSERT_AUDIT_LOG_SQL = """
            INSERT INTO audit_logs
                (actor_user_id, action_type, entity_type, entity_id, old_values_json, new_values_json, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private static final String SELECT_REQUESTS_SQL = """
            SELECT
                scr.request_id,
                scr.schedule_id,
                scr.teacher_user_id,
                requester.full_name AS requester_name,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.subject_name')) AS old_subject_name,
                CAST(JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.day_of_week')) AS UNSIGNED) AS old_day_of_week,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.start_time')) AS old_start_time,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.end_time')) AS old_end_time,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.room_name')) AS old_room_name,
                scr.requested_subject_name,
                scr.requested_day_of_week,
                scr.requested_start_time,
                scr.requested_end_time,
                scr.requested_room_name,
                scr.reason,
                scr.request_status,
                reviewer.full_name AS reviewed_by_name,
                scr.reviewed_at,
                scr.created_at
            FROM schedule_change_requests scr
            INNER JOIN users requester
                ON requester.user_id = scr.teacher_user_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = scr.reviewed_by_user_id
            ORDER BY scr.created_at DESC, scr.request_id DESC
            """;

    private static final String SELECT_REQUESTS_FOR_TEACHER_SQL = """
            SELECT
                scr.request_id,
                scr.schedule_id,
                scr.teacher_user_id,
                requester.full_name AS requester_name,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.subject_name')) AS old_subject_name,
                CAST(JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.day_of_week')) AS UNSIGNED) AS old_day_of_week,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.start_time')) AS old_start_time,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.end_time')) AS old_end_time,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.room_name')) AS old_room_name,
                scr.requested_subject_name,
                scr.requested_day_of_week,
                scr.requested_start_time,
                scr.requested_end_time,
                scr.requested_room_name,
                scr.reason,
                scr.request_status,
                reviewer.full_name AS reviewed_by_name,
                scr.reviewed_at,
                scr.created_at
            FROM schedule_change_requests scr
            INNER JOIN users requester
                ON requester.user_id = scr.teacher_user_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = scr.reviewed_by_user_id
            WHERE scr.teacher_user_id = ?
            ORDER BY scr.created_at DESC, scr.request_id DESC
            """;

    private static final String SELECT_REQUEST_FOR_UPDATE_SQL = """
            SELECT
                scr.request_id,
                scr.schedule_id,
                scr.teacher_user_id,
                requester.full_name AS requester_name,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.subject_name')) AS old_subject_name,
                CAST(JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.day_of_week')) AS UNSIGNED) AS old_day_of_week,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.start_time')) AS old_start_time,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.end_time')) AS old_end_time,
                JSON_UNQUOTE(JSON_EXTRACT(scr.old_snapshot, '$.room_name')) AS old_room_name,
                scr.requested_subject_name,
                scr.requested_day_of_week,
                scr.requested_start_time,
                scr.requested_end_time,
                scr.requested_room_name,
                scr.reason,
                scr.request_status,
                reviewer.full_name AS reviewed_by_name,
                scr.reviewed_at,
                scr.created_at
            FROM schedule_change_requests scr
            INNER JOIN users requester
                ON requester.user_id = scr.teacher_user_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = scr.reviewed_by_user_id
            WHERE scr.request_id = ?
            FOR UPDATE
            """;

    private final DatabaseManager databaseManager;

    public ScheduleService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public ScheduleService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<List<ScheduleSlot>> getSchedules() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<ScheduleSlot> schedules = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SCHEDULES_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                schedules.add(mapScheduleSlot(resultSet));
            }
            return ServiceResult.success("Loaded schedules from MariaDB.", schedules);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load schedules: " + ex.getMessage());
        }
    }

    public ServiceResult<List<ScheduleSlot>> getSchedulesForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<ScheduleSlot> schedules = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SCHEDULES_FOR_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    schedules.add(mapScheduleSlot(resultSet));
                }
            }
            return ServiceResult.success("Loaded teacher schedule from MariaDB.", schedules);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load teacher schedule: " + ex.getMessage());
        }
    }

    public ServiceResult<List<ScheduleSlot>> getTodaySchedules() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<ScheduleSlot> schedules = new ArrayList<>();
        int todayValue = LocalDate.now().getDayOfWeek().getValue();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_TODAY_SCHEDULES_SQL)) {
            statement.setInt(1, todayValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    schedules.add(mapScheduleSlot(resultSet));
                }
            }
            return ServiceResult.success("Loaded today's approved schedules.", schedules);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load today's schedules: " + ex.getMessage());
        }
    }

    public ServiceResult<ScheduleSlot> getActiveScheduleForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        LocalTime now = LocalTime.now().withNano(0);
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_SCHEDULE_SQL)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, today.getValue());
            statement.setTime(3, java.sql.Time.valueOf(now));
            statement.setTime(4, java.sql.Time.valueOf(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ServiceResult.failure("No active approved schedule.");
                }
                return ServiceResult.success("Loaded active schedule.", mapScheduleSlot(resultSet));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not resolve the active schedule: " + ex.getMessage());
        }
    }

    public ServiceResult<ScheduleSlot> getNextScheduleForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        LocalTime now = LocalTime.now().withNano(0);
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_NEXT_SCHEDULE_SQL)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, today.getValue());
            statement.setTime(3, java.sql.Time.valueOf(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ServiceResult.failure("No upcoming schedule found for today.");
                }
                return ServiceResult.success("Loaded next schedule.", mapScheduleSlot(resultSet));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the next schedule: " + ex.getMessage());
        }
    }

    public ServiceResult<ScheduleSlot> createApprovedScheduleSlot(int actorUserId, int teacherId, String subjectCode,
            String subjectName, DayOfWeek day, LocalTime start, LocalTime end, String room) {
        String normalizedSubjectCode = normalize(subjectCode);
        String normalizedSubjectName = normalize(subjectName);
        String normalizedRoom = normalize(room);
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (actorUserId <= 0) {
            return ServiceResult.failure("A valid admin account is required to save schedules.");
        }
        if (teacherId <= 0 || day == null || start == null || end == null || normalizedSubjectName.isBlank() || normalizedRoom.isBlank()) {
            return ServiceResult.failure("Teacher, subject, room, day, and time are required.");
        }
        if (!start.isBefore(end)) {
            return ServiceResult.failure("The class end time must be after the start time.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserRow admin = loadAdmin(connection, actorUserId);
                if (admin == null || !"ACTIVE".equalsIgnoreCase(admin.accountStatus)) {
                    connection.rollback();
                    return ServiceResult.failure("Only an active admin account can save schedules.");
                }
                UserRow teacher = loadTeacher(connection, teacherId);
                if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.accountStatus)) {
                    connection.rollback();
                    return ServiceResult.failure("Teacher account was not found or is inactive.");
                }
                if (hasScheduleConflict(connection, teacherId, null, day, start, end)) {
                    connection.rollback();
                    return ServiceResult.failure("This schedule overlaps another approved class for the same teacher.");
                }

                long scheduleId = insertSchedule(connection, actorUserId, teacherId, normalizedSubjectCode, normalizedSubjectName, day, start, end, normalizedRoom);
                ScheduleSlot created = new ScheduleSlot((int) scheduleId, teacherId, normalizedSubjectName, day, start, end, normalizedRoom, "APPROVED");
                insertAuditLog(connection, actorUserId, "SCHEDULE_CREATE", "SCHEDULE", String.valueOf(scheduleId),
                        null,
                        buildScheduleJson(created),
                        "Approved schedule created from admin schedule builder.");
                connection.commit();
                return ServiceResult.success("Schedule saved for " + normalizedSubjectName + ".", created);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not save the schedule: " + ex.getMessage());
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the schedule: " + ex.getMessage());
        }
    }

    public ServiceResult<Boolean> validateScheduleConflict(int teacherId, Integer existingScheduleId, DayOfWeek day,
            LocalTime start, LocalTime end) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (day == null || start == null || end == null) {
            return ServiceResult.failure("Day and time are required.");
        }
        if (!start.isBefore(end)) {
            return ServiceResult.failure("The class end time must be after the start time.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection()) {
            return ServiceResult.success("Schedule conflict check completed.",
                    hasScheduleConflict(connection, teacherId, existingScheduleId, day, start, end));
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not validate schedule conflict: " + ex.getMessage());
        }
    }

    public ServiceResult<List<ScheduleChangeRequest>> getScheduleRequests() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<ScheduleChangeRequest> requests = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_REQUESTS_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                requests.add(mapScheduleRequest(resultSet));
            }
            return ServiceResult.success("Loaded schedule requests from MariaDB.", requests);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load schedule requests: " + ex.getMessage());
        }
    }

    public ServiceResult<List<ScheduleChangeRequest>> getScheduleRequestsForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher account is required.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<ScheduleChangeRequest> requests = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_REQUESTS_FOR_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(mapScheduleRequest(resultSet));
                }
            }
            return ServiceResult.success("Loaded teacher schedule requests from MariaDB.", requests);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load teacher schedule requests: " + ex.getMessage());
        }
    }

    public ServiceResult<ScheduleChangeRequest> submitScheduleCorrectionRequest(int teacherId, int scheduleId,
            String requestedSubjectName, DayOfWeek requestedDay, LocalTime requestedStartTime,
            LocalTime requestedEndTime, String requestedRoom, String reason) {
        String normalizedSubjectName = normalize(requestedSubjectName);
        String normalizedRoom = normalize(requestedRoom);
        String normalizedReason = normalize(reason);
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (teacherId <= 0 || scheduleId <= 0 || requestedDay == null || requestedStartTime == null || requestedEndTime == null
                || normalizedSubjectName.isBlank() || normalizedRoom.isBlank() || normalizedReason.isBlank()) {
            return ServiceResult.failure("Requested subject, room, day, time, and reason are required.");
        }
        if (!requestedStartTime.isBefore(requestedEndTime)) {
            return ServiceResult.failure("The requested end time must be after the start time.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserRow teacher = loadTeacher(connection, teacherId);
                if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.accountStatus)) {
                    connection.rollback();
                    return ServiceResult.failure("Teacher account was not found or is inactive.");
                }
                ScheduleRow sourceSchedule = loadScheduleForTeacher(connection, teacherId, scheduleId);
                if (sourceSchedule == null) {
                    connection.rollback();
                    return ServiceResult.failure("Select one of your schedule rows first.");
                }

                String oldSnapshot = buildScheduleSnapshotJson(sourceSchedule.subjectName, sourceSchedule.day, sourceSchedule.startTime,
                        sourceSchedule.endTime, sourceSchedule.room);
                String requestedSnapshot = buildScheduleSnapshotJson(normalizedSubjectName, requestedDay, requestedStartTime,
                        requestedEndTime, normalizedRoom);
                long requestId = insertScheduleRequest(connection, scheduleId, teacherId, oldSnapshot, normalizedSubjectName,
                        requestedDay, requestedStartTime, requestedEndTime, normalizedRoom, requestedSnapshot, normalizedReason);

                ScheduleChangeRequest created = new ScheduleChangeRequest((int) requestId, teacherId, scheduleId, teacher.fullName,
                        describeSchedule(sourceSchedule.subjectName, sourceSchedule.day, sourceSchedule.startTime, sourceSchedule.endTime, sourceSchedule.room),
                        describeSchedule(normalizedSubjectName, requestedDay, requestedStartTime, requestedEndTime, normalizedRoom),
                        normalizedSubjectName, requestedDay, requestedStartTime, requestedEndTime, normalizedRoom, normalizedReason,
                        ScheduleRequestStatus.PENDING);

                insertAuditLog(connection, teacherId, "SCHEDULE_REQUEST_CREATE", "SCHEDULE_REQUEST", String.valueOf(requestId),
                        oldSnapshot,
                        requestedSnapshot,
                        "Teacher submitted a schedule correction request.");
                connection.commit();
                return ServiceResult.success("Schedule correction submitted for admin approval.", created);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not submit the schedule correction: " + ex.getMessage());
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not submit the schedule correction: " + ex.getMessage());
        }
    }

    public ServiceResult<ScheduleChangeRequest> approveScheduleRequest(int reviewerUserId, int requestId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (reviewerUserId <= 0 || requestId <= 0) {
            return ServiceResult.failure("A valid admin account and request id are required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserRow reviewer = loadAdmin(connection, reviewerUserId);
                if (reviewer == null || !"ACTIVE".equalsIgnoreCase(reviewer.accountStatus)) {
                    connection.rollback();
                    return ServiceResult.failure("Only an active admin can approve schedule requests.");
                }
                ScheduleRequestRow requestRow = loadRequestForUpdate(connection, requestId);
                if (requestRow == null) {
                    connection.rollback();
                    return ServiceResult.failure("Schedule request not found.");
                }
                if (requestRow.status != ScheduleRequestStatus.PENDING) {
                    connection.rollback();
                    return ServiceResult.failure("Only pending requests can be approved.");
                }
                if (hasScheduleConflict(connection, requestRow.teacherId, requestRow.sourceSlotId,
                        requestRow.requestedDay, requestRow.requestedStartTime, requestRow.requestedEndTime)) {
                    connection.rollback();
                    return ServiceResult.failure("Cannot approve because the requested slot conflicts with another approved class.");
                }

                try (PreparedStatement update = connection.prepareStatement(UPDATE_SCHEDULE_FROM_REQUEST_SQL)) {
                    update.setString(1, requestRow.requestedSubjectName);
                    update.setInt(2, requestRow.requestedDay.getValue());
                    update.setTime(3, java.sql.Time.valueOf(requestRow.requestedStartTime));
                    update.setTime(4, java.sql.Time.valueOf(requestRow.requestedEndTime));
                    update.setString(5, requestRow.requestedRoom);
                    update.setInt(6, reviewerUserId);
                    update.setInt(7, requestRow.sourceSlotId);
                    update.executeUpdate();
                }

                try (PreparedStatement update = connection.prepareStatement(UPDATE_REQUEST_APPROVED_SQL)) {
                    update.setInt(1, reviewerUserId);
                    update.setInt(2, requestId);
                    update.executeUpdate();
                }

                insertAuditLog(connection, reviewerUserId, "SCHEDULE_REQUEST_APPROVE", "SCHEDULE_REQUEST", String.valueOf(requestId),
                        requestRow.oldSnapshotJson,
                        requestRow.requestedSnapshotJson,
                        "Schedule request approved and applied to the live schedule.");
                connection.commit();

                ScheduleChangeRequest mapped = requestRow.toDomainRequest();
                mapped.setStatus(ScheduleRequestStatus.APPROVED);
                mapped.setReviewedBy(reviewer.fullName);
                mapped.setReviewedAt(LocalDateTime.now());
                return ServiceResult.success("Schedule request approved.", mapped);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not approve the schedule request: " + ex.getMessage());
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not approve the schedule request: " + ex.getMessage());
        }
    }

    public ServiceResult<ScheduleChangeRequest> rejectScheduleRequest(int reviewerUserId, int requestId, String rejectionNote) {
        String normalizedNote = normalize(rejectionNote);
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (reviewerUserId <= 0 || requestId <= 0) {
            return ServiceResult.failure("A valid admin account and request id are required.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                UserRow reviewer = loadAdmin(connection, reviewerUserId);
                if (reviewer == null || !"ACTIVE".equalsIgnoreCase(reviewer.accountStatus)) {
                    connection.rollback();
                    return ServiceResult.failure("Only an active admin can reject schedule requests.");
                }
                ScheduleRequestRow requestRow = loadRequestForUpdate(connection, requestId);
                if (requestRow == null) {
                    connection.rollback();
                    return ServiceResult.failure("Schedule request not found.");
                }
                if (requestRow.status != ScheduleRequestStatus.PENDING) {
                    connection.rollback();
                    return ServiceResult.failure("Only pending requests can be rejected.");
                }

                try (PreparedStatement update = connection.prepareStatement(UPDATE_REQUEST_REJECTED_SQL)) {
                    update.setInt(1, reviewerUserId);
                    update.setInt(2, requestId);
                    update.executeUpdate();
                }

                insertAuditLog(connection, reviewerUserId, "SCHEDULE_REQUEST_REJECT", "SCHEDULE_REQUEST", String.valueOf(requestId),
                        requestRow.oldSnapshotJson,
                        requestRow.requestedSnapshotJson,
                        normalizedNote.isBlank() ? "Schedule request rejected from admin queue." : normalizedNote);
                connection.commit();

                ScheduleChangeRequest mapped = requestRow.toDomainRequest();
                mapped.setStatus(ScheduleRequestStatus.REJECTED);
                mapped.setReviewedBy(reviewer.fullName);
                mapped.setReviewedAt(LocalDateTime.now());
                return ServiceResult.success("Schedule request rejected.", mapped);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not reject the schedule request: " + ex.getMessage());
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not reject the schedule request: " + ex.getMessage());
        }
    }

    private UserRow loadTeacher(Connection connection, int teacherId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_TEACHER_SQL)) {
            statement.setInt(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserRow(
                        resultSet.getInt("user_id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("role"),
                        resultSet.getString("account_status")
                );
            }
        }
    }

    private UserRow loadAdmin(Connection connection, int adminId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ADMIN_SQL)) {
            statement.setInt(1, adminId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserRow(
                        resultSet.getInt("user_id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("role"),
                        resultSet.getString("account_status")
                );
            }
        }
    }

    private ScheduleRow loadScheduleForTeacher(Connection connection, int teacherId, int scheduleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SCHEDULE_BY_TEACHER_SQL)) {
            statement.setInt(1, scheduleId);
            statement.setInt(2, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new ScheduleRow(
                        resultSet.getInt("schedule_id"),
                        resultSet.getInt("teacher_user_id"),
                        resultSet.getString("subject_code"),
                        resultSet.getString("subject_name"),
                        dayOfWeek(resultSet.getInt("day_of_week")),
                        resultSet.getTime("start_time").toLocalTime(),
                        resultSet.getTime("end_time").toLocalTime(),
                        resultSet.getString("room_name"),
                        resultSet.getString("schedule_status")
                );
            }
        }
    }

    private long insertSchedule(Connection connection, int actorUserId, int teacherId, String subjectCode,
            String subjectName, DayOfWeek day, LocalTime start, LocalTime end, String room) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SCHEDULE_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, teacherId);
            if (subjectCode.isBlank()) {
                statement.setNull(2, java.sql.Types.VARCHAR);
            } else {
                statement.setString(2, subjectCode);
            }
            statement.setString(3, subjectName);
            statement.setInt(4, day.getValue());
            statement.setTime(5, java.sql.Time.valueOf(start));
            statement.setTime(6, java.sql.Time.valueOf(end));
            statement.setString(7, room);
            statement.setInt(8, actorUserId);
            statement.setInt(9, actorUserId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Schedule insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
        }
    }

    private long insertScheduleRequest(Connection connection, int scheduleId, int teacherId, String oldSnapshot,
            String requestedSubjectName, DayOfWeek requestedDay, LocalTime requestedStartTime,
            LocalTime requestedEndTime, String requestedRoom, String requestedSnapshot, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_REQUEST_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, scheduleId);
            statement.setInt(2, teacherId);
            statement.setString(3, oldSnapshot);
            statement.setString(4, requestedSubjectName);
            statement.setInt(5, requestedDay.getValue());
            statement.setTime(6, java.sql.Time.valueOf(requestedStartTime));
            statement.setTime(7, java.sql.Time.valueOf(requestedEndTime));
            statement.setString(8, requestedRoom);
            statement.setString(9, requestedSnapshot);
            statement.setString(10, reason);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Schedule request insert did not return a generated key.");
                }
                return keys.getLong(1);
            }
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

    private boolean hasScheduleConflict(Connection connection, int teacherId, Integer existingScheduleId, DayOfWeek day,
            LocalTime start, LocalTime end) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT schedule_id
                FROM teacher_schedules
                WHERE teacher_user_id = ?
                  AND day_of_week = ?
                  AND schedule_status = 'APPROVED'
                  AND start_time < ?
                  AND end_time > ?
                """);
        if (existingScheduleId != null) {
            sql.append(" AND schedule_id <> ?");
        }
        sql.append(" LIMIT 1");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setInt(1, teacherId);
            statement.setInt(2, day.getValue());
            statement.setTime(3, java.sql.Time.valueOf(end));
            statement.setTime(4, java.sql.Time.valueOf(start));
            if (existingScheduleId != null) {
                statement.setInt(5, existingScheduleId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private ScheduleRequestRow loadRequestForUpdate(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_REQUEST_FOR_UPDATE_SQL)) {
            statement.setInt(1, requestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapScheduleRequestRow(resultSet);
            }
        }
    }

    private ScheduleSlot mapScheduleSlot(ResultSet resultSet) throws SQLException {
        return new ScheduleSlot(
                resultSet.getInt("schedule_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getString("subject_name"),
                dayOfWeek(resultSet.getInt("day_of_week")),
                resultSet.getTime("start_time").toLocalTime(),
                resultSet.getTime("end_time").toLocalTime(),
                resultSet.getString("room_name"),
                resultSet.getString("schedule_status")
        );
    }

    private ScheduleChangeRequest mapScheduleRequest(ResultSet resultSet) throws SQLException {
        return mapScheduleRequestRow(resultSet).toDomainRequest();
    }

    private ScheduleRequestRow mapScheduleRequestRow(ResultSet resultSet) throws SQLException {
        String oldSubjectName = safe(resultSet.getString("old_subject_name"));
        DayOfWeek oldDay = dayOfWeek(resultSet.getInt("old_day_of_week"));
        LocalTime oldStart = parseTime(resultSet.getString("old_start_time"));
        LocalTime oldEnd = parseTime(resultSet.getString("old_end_time"));
        String oldRoom = safe(resultSet.getString("old_room_name"));

        String requestedSubjectName = resultSet.getString("requested_subject_name");
        DayOfWeek requestedDay = dayOfWeek(resultSet.getInt("requested_day_of_week"));
        LocalTime requestedStart = resultSet.getTime("requested_start_time").toLocalTime();
        LocalTime requestedEnd = resultSet.getTime("requested_end_time").toLocalTime();
        String requestedRoom = resultSet.getString("requested_room_name");
        String reason = resultSet.getString("reason");
        ScheduleRequestStatus status = mapRequestStatus(resultSet.getString("request_status"));
        String oldSnapshotJson = buildScheduleSnapshotJson(oldSubjectName, oldDay, oldStart, oldEnd, oldRoom);
        String requestedSnapshotJson = buildScheduleSnapshotJson(requestedSubjectName, requestedDay, requestedStart, requestedEnd, requestedRoom);

        return new ScheduleRequestRow(
                resultSet.getInt("request_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getInt("schedule_id"),
                resultSet.getString("requester_name"),
                oldSubjectName,
                oldDay,
                oldStart,
                oldEnd,
                oldRoom,
                requestedSubjectName,
                requestedDay,
                requestedStart,
                requestedEnd,
                requestedRoom,
                reason,
                status,
                resultSet.getString("reviewed_by_name"),
                toLocalDateTime(resultSet.getTimestamp("reviewed_at")),
                oldSnapshotJson,
                requestedSnapshotJson
        );
    }

    private ScheduleRequestStatus mapRequestStatus(String value) {
        try {
            return ScheduleRequestStatus.valueOf(value == null ? "PENDING" : value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            return ScheduleRequestStatus.PENDING;
        }
    }

    private DayOfWeek dayOfWeek(int value) {
        int safeValue = Math.max(1, Math.min(7, value));
        return DayOfWeek.of(safeValue);
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.of(0, 0);
        }
        return LocalTime.parse(value);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String buildScheduleSnapshotJson(String subjectName, DayOfWeek day, LocalTime start, LocalTime end, String room) {
        return "{"
                + "\"subject_name\":\"" + escapeJson(subjectName) + "\","
                + "\"day_of_week\":" + day.getValue() + ","
                + "\"start_time\":\"" + start + "\","
                + "\"end_time\":\"" + end + "\","
                + "\"room_name\":\"" + escapeJson(room) + "\""
                + "}";
    }

    private String buildScheduleJson(ScheduleSlot slot) {
        return buildScheduleSnapshotJson(slot.getSubjectName(), slot.getDay(), slot.getStartTime(), slot.getEndTime(), slot.getRoom());
    }

    private String describeSchedule(String subjectName, DayOfWeek day, LocalTime start, LocalTime end, String room) {
        return subjectName + " - " + titleCase(day.name()) + " - " + start + " to " + end + " - " + room;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
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

    private static final class UserRow {

        private final int userId;
        private final String fullName;
        private final String role;
        private final String accountStatus;

        private UserRow(int userId, String fullName, String role, String accountStatus) {
            this.userId = userId;
            this.fullName = fullName;
            this.role = role;
            this.accountStatus = accountStatus;
        }
    }

    private static final class ScheduleRow {

        private final int scheduleId;
        private final int teacherId;
        private final String subjectCode;
        private final String subjectName;
        private final DayOfWeek day;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String room;
        private final String status;

        private ScheduleRow(int scheduleId, int teacherId, String subjectCode, String subjectName,
                DayOfWeek day, LocalTime startTime, LocalTime endTime, String room, String status) {
            this.scheduleId = scheduleId;
            this.teacherId = teacherId;
            this.subjectCode = subjectCode;
            this.subjectName = subjectName;
            this.day = day;
            this.startTime = startTime;
            this.endTime = endTime;
            this.room = room;
            this.status = status;
        }
    }

    private static final class ScheduleRequestRow {

        private final int requestId;
        private final int teacherId;
        private final int sourceSlotId;
        private final String requesterName;
        private final String oldSubjectName;
        private final DayOfWeek oldDay;
        private final LocalTime oldStartTime;
        private final LocalTime oldEndTime;
        private final String oldRoom;
        private final String requestedSubjectName;
        private final DayOfWeek requestedDay;
        private final LocalTime requestedStartTime;
        private final LocalTime requestedEndTime;
        private final String requestedRoom;
        private final String reason;
        private final ScheduleRequestStatus status;
        private final String reviewedByName;
        private final LocalDateTime reviewedAt;
        private final String oldSnapshotJson;
        private final String requestedSnapshotJson;

        private ScheduleRequestRow(int requestId, int teacherId, int sourceSlotId, String requesterName,
                String oldSubjectName, DayOfWeek oldDay, LocalTime oldStartTime, LocalTime oldEndTime, String oldRoom,
                String requestedSubjectName, DayOfWeek requestedDay, LocalTime requestedStartTime, LocalTime requestedEndTime,
                String requestedRoom, String reason, ScheduleRequestStatus status, String reviewedByName,
                LocalDateTime reviewedAt, String oldSnapshotJson, String requestedSnapshotJson) {
            this.requestId = requestId;
            this.teacherId = teacherId;
            this.sourceSlotId = sourceSlotId;
            this.requesterName = requesterName;
            this.oldSubjectName = oldSubjectName;
            this.oldDay = oldDay;
            this.oldStartTime = oldStartTime;
            this.oldEndTime = oldEndTime;
            this.oldRoom = oldRoom;
            this.requestedSubjectName = requestedSubjectName;
            this.requestedDay = requestedDay;
            this.requestedStartTime = requestedStartTime;
            this.requestedEndTime = requestedEndTime;
            this.requestedRoom = requestedRoom;
            this.reason = reason;
            this.status = status;
            this.reviewedByName = reviewedByName;
            this.reviewedAt = reviewedAt;
            this.oldSnapshotJson = oldSnapshotJson;
            this.requestedSnapshotJson = requestedSnapshotJson;
        }

        private ScheduleChangeRequest toDomainRequest() {
            ScheduleChangeRequest request = new ScheduleChangeRequest(
                    requestId,
                    teacherId,
                    sourceSlotId,
                    requesterName,
                    oldSubjectName.isBlank() ? oldSnapshotJson : oldSubjectName + " - " + capitalizeDay(oldDay) + " - " + oldStartTime + " to " + oldEndTime + " - " + oldRoom,
                    requestedSubjectName + " - " + capitalizeDay(requestedDay) + " - " + requestedStartTime + " to " + requestedEndTime + " - " + requestedRoom,
                    requestedSubjectName,
                    requestedDay,
                    requestedStartTime,
                    requestedEndTime,
                    requestedRoom,
                    reason,
                    status
            );
            request.setReviewedBy(reviewedByName);
            request.setReviewedAt(reviewedAt);
            return request;
        }

        private String capitalizeDay(DayOfWeek day) {
            String lower = day.name().toLowerCase(Locale.ENGLISH);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }
}
