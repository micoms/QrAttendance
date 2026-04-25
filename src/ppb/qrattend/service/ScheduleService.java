package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.model.CoreModels.RequestStatus;
import ppb.qrattend.model.CoreModels.Room;
import ppb.qrattend.model.CoreModels.Schedule;
import ppb.qrattend.model.CoreModels.ScheduleRequest;
import ppb.qrattend.model.CoreModels.Subject;
import ppb.qrattend.util.AppClock;

public final class ScheduleService {

    private static final String SELECT_SUBJECTS_SQL = """
            SELECT subject_id, subject_name
            FROM subjects
            ORDER BY subject_name ASC
            """;

    private static final String INSERT_SUBJECT_SQL = """
            INSERT INTO subjects (subject_name)
            VALUES (?)
            """;

    private static final String SELECT_ROOMS_SQL = """
            SELECT room_id, room_name
            FROM rooms
            ORDER BY room_name ASC
            """;

    private static final String INSERT_ROOM_SQL = """
            INSERT INTO rooms (room_name)
            VALUES (?)
            """;

    private static final String SELECT_SCHEDULES_SQL = """
            SELECT
                sc.schedule_id,
                sc.teacher_user_id,
                u.full_name AS teacher_name,
                sc.section_id,
                sec.section_name,
                sc.subject_id,
                sub.subject_name,
                sc.room_id,
                r.room_name,
                sc.day_of_week,
                sc.start_time,
                sc.end_time,
                sc.is_active
            FROM schedules sc
            INNER JOIN users u
                ON u.user_id = sc.teacher_user_id
            INNER JOIN sections sec
                ON sec.section_id = sc.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = sc.subject_id
            INNER JOIN rooms r
                ON r.room_id = sc.room_id
            WHERE sc.is_active = 1
            ORDER BY sc.day_of_week ASC, sc.start_time ASC, teacher_name ASC
            """;

    private static final String SELECT_TEACHER_SCHEDULES_SQL = """
            SELECT
                sc.schedule_id,
                sc.teacher_user_id,
                u.full_name AS teacher_name,
                sc.section_id,
                sec.section_name,
                sc.subject_id,
                sub.subject_name,
                sc.room_id,
                r.room_name,
                sc.day_of_week,
                sc.start_time,
                sc.end_time,
                sc.is_active
            FROM schedules sc
            INNER JOIN users u
                ON u.user_id = sc.teacher_user_id
            INNER JOIN sections sec
                ON sec.section_id = sc.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = sc.subject_id
            INNER JOIN rooms r
                ON r.room_id = sc.room_id
            WHERE sc.teacher_user_id = ?
              AND sc.is_active = 1
            ORDER BY sc.day_of_week ASC, sc.start_time ASC
            """;

    private static final String CHECK_CONFLICT_SQL = """
            SELECT 1
            FROM schedules
            WHERE teacher_user_id = ?
              AND day_of_week = ?
              AND is_active = 1
              AND (? < end_time AND ? > start_time)
            LIMIT 1
            """;

    private static final String FIND_CONFLICTING_SCHEDULE_SQL = """
            SELECT sc.schedule_id, sc.teacher_user_id, sc.section_id, sec.section_name,
                   sc.subject_id, sub.subject_name, sc.room_id, r.room_name,
                   sc.day_of_week, sc.start_time, sc.end_time, sc.is_active,
                   u.full_name AS teacher_name
            FROM schedules sc
            INNER JOIN subjects sub ON sub.subject_id = sc.subject_id
            INNER JOIN sections sec ON sec.section_id = sc.section_id
            INNER JOIN rooms r ON r.room_id = sc.room_id
            INNER JOIN users u ON u.user_id = sc.teacher_user_id
            WHERE sc.teacher_user_id = ?
              AND sc.day_of_week = ?
              AND sc.is_active = 1
              AND sc.schedule_id <> ?
              AND (? < sc.end_time AND ? > sc.start_time)
            LIMIT 1
            """;

    private static final String INSERT_SCHEDULE_SQL = """
            INSERT INTO schedules
                (teacher_user_id, section_id, subject_id, room_id, day_of_week, start_time, end_time, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1)
            """;

    private static final String INSERT_REQUEST_SQL = """
            INSERT INTO schedule_requests
                (schedule_id, teacher_user_id, section_id, subject_id, room_id, day_of_week, start_time, end_time, reason, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """;

    private static final String SELECT_REQUESTS_SQL = """
            SELECT
                sr.request_id,
                sr.schedule_id,
                sr.teacher_user_id,
                u.full_name AS teacher_name,
                sr.section_id,
                sec.section_name,
                sr.subject_id,
                sub.subject_name,
                sr.room_id,
                r.room_name,
                sr.day_of_week,
                sr.start_time,
                sr.end_time,
                sr.reason,
                sr.status,
                reviewer.full_name AS reviewed_by,
                sr.created_at,
                sr.reviewed_at
            FROM schedule_requests sr
            INNER JOIN users u
                ON u.user_id = sr.teacher_user_id
            INNER JOIN sections sec
                ON sec.section_id = sr.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = sr.subject_id
            INNER JOIN rooms r
                ON r.room_id = sr.room_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = sr.reviewed_by_user_id
            %s
            ORDER BY
                CASE sr.status
                    WHEN 'PENDING' THEN 0
                    WHEN 'APPROVED' THEN 1
                    ELSE 2
                END,
                sr.request_id DESC
            """;

    private static final String SELECT_REQUEST_SQL = """
            SELECT
                sr.request_id,
                sr.schedule_id,
                sr.teacher_user_id,
                u.full_name AS teacher_name,
                sr.section_id,
                sec.section_name,
                sr.subject_id,
                sub.subject_name,
                sr.room_id,
                r.room_name,
                sr.day_of_week,
                sr.start_time,
                sr.end_time,
                sr.reason,
                sr.status,
                reviewer.full_name AS reviewed_by,
                sr.created_at,
                sr.reviewed_at
            FROM schedule_requests sr
            INNER JOIN users u
                ON u.user_id = sr.teacher_user_id
            INNER JOIN sections sec
                ON sec.section_id = sr.section_id
            INNER JOIN subjects sub
                ON sub.subject_id = sr.subject_id
            INNER JOIN rooms r
                ON r.room_id = sr.room_id
            LEFT JOIN users reviewer
                ON reviewer.user_id = sr.reviewed_by_user_id
            WHERE sr.request_id = ?
            LIMIT 1
            """;

    private static final String UPDATE_REQUEST_SQL = """
            UPDATE schedule_requests
            SET status = ?, reviewed_by_user_id = ?, reviewed_at = CURRENT_TIMESTAMP
            WHERE request_id = ?
            """;

    private static final String UPDATE_SCHEDULE_SQL = """
            UPDATE schedules
            SET teacher_user_id = ?, section_id = ?, subject_id = ?, room_id = ?, day_of_week = ?, start_time = ?, end_time = ?, updated_at = CURRENT_TIMESTAMP
            WHERE schedule_id = ?
            """;

    private static final String DEACTIVATE_SCHEDULE_SQL =
            "UPDATE schedules SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE schedule_id = ?";

    private static final String RENAME_SUBJECT_SQL =
            "UPDATE subjects SET subject_name = ? WHERE subject_id = ?";

    private static final String CHECK_SUBJECT_IN_SCHEDULES_SQL =
            "SELECT 1 FROM schedules WHERE subject_id = ? AND is_active = 1 LIMIT 1";

    private static final String DELETE_SUBJECT_SQL =
            "DELETE FROM subjects WHERE subject_id = ?";

    private static final String RENAME_ROOM_SQL =
            "UPDATE rooms SET room_name = ? WHERE room_id = ?";

    private static final String CHECK_ROOM_IN_SCHEDULES_SQL =
            "SELECT 1 FROM schedules WHERE room_id = ? AND is_active = 1 LIMIT 1";

    private static final String DELETE_ROOM_SQL =
            "DELETE FROM rooms WHERE room_id = ?";

    private final DatabaseManager databaseManager;

    public ScheduleService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public ScheduleService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ServiceResult<List<Subject>> getSubjects() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<Subject> subjects = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SUBJECTS_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                subjects.add(new Subject(resultSet.getInt("subject_id"), resultSet.getString("subject_name")));
            }
            return ServiceResult.success("Subjects loaded.", subjects);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load subjects.");
        }
    }

    public ServiceResult<Subject> addSubject(String subjectName) {
        String cleanName = safe(subjectName);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the subject name.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SUBJECT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, cleanName);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    return ServiceResult.failure("Could not save the subject.");
                }
                return ServiceResult.success("Subject saved.", new Subject(keys.getInt(1), cleanName));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("That subject already exists or could not be saved.");
        }
    }

    public ServiceResult<List<Room>> getRooms() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<Room> rooms = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ROOMS_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rooms.add(new Room(resultSet.getInt("room_id"), resultSet.getString("room_name")));
            }
            return ServiceResult.success("Rooms loaded.", rooms);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load rooms.");
        }
    }

    public ServiceResult<Room> addRoom(String roomName) {
        String cleanName = safe(roomName);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the room name.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_ROOM_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, cleanName);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    return ServiceResult.failure("Could not save the room.");
                }
                return ServiceResult.success("Room saved.", new Room(keys.getInt(1), cleanName));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("That room already exists or could not be saved.");
        }
    }

    public ServiceResult<List<Schedule>> getSchedules() {
        return loadSchedules(SELECT_SCHEDULES_SQL, null);
    }

    public ServiceResult<List<Schedule>> getSchedulesForTeacher(int teacherId) {
        return loadSchedules(SELECT_TEACHER_SCHEDULES_SQL, teacherId);
    }

    public ServiceResult<Schedule> getCurrentScheduleForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher not found.");
        }
        ServiceResult<List<Schedule>> schedules = getSchedulesForTeacher(teacherId);
        if (!schedules.isSuccess()) {
            return ServiceResult.failure(schedules.getMessage());
        }
        LocalDate today = AppClock.today();
        LocalTime now = AppClock.nowTime();
        for (Schedule schedule : schedules.getData()) {
            if (isCurrentClass(schedule, today, now)) {
                return ServiceResult.success("Current class loaded.", schedule);
            }
        }
        return ServiceResult.failure("No class is open right now.");
    }

    public ServiceResult<Schedule> getNextScheduleForTeacher(int teacherId) {
        if (teacherId <= 0) {
            return ServiceResult.failure("Teacher not found.");
        }
        ServiceResult<List<Schedule>> schedules = getSchedulesForTeacher(teacherId);
        if (!schedules.isSuccess()) {
            return ServiceResult.failure(schedules.getMessage());
        }
        LocalDate today = AppClock.today();
        LocalTime now = AppClock.nowTime();
        Schedule next = null;
        for (Schedule schedule : schedules.getData()) {
            if (schedule.day().getValue() != today.getDayOfWeek().getValue()) {
                continue;
            }
            if (schedule.startTime().isAfter(now) && (next == null || schedule.startTime().isBefore(next.startTime()))) {
                next = schedule;
            }
        }
        if (next == null) {
            return ServiceResult.failure("No next class.");
        }
        return ServiceResult.success("Next class loaded.", next);
    }

    public ServiceResult<Schedule> addSchedule(int teacherId, int sectionId, int subjectId, int roomId,
            DayOfWeek day, LocalTime startTime, LocalTime endTime) {
        if (teacherId <= 0 || sectionId <= 0 || subjectId <= 0 || roomId <= 0 || day == null || startTime == null || endTime == null) {
            return ServiceResult.failure("Complete the schedule form first.");
        }
        if (!startTime.isBefore(endTime)) {
            return ServiceResult.failure("End time must be after the start time.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            Schedule conflicting = findConflictingSchedule(connection, teacherId, day, startTime, endTime, -1);
            if (conflicting != null) {
                return ServiceResult.failure("This time overlaps with " + conflicting.subjectName() + " (" + conflicting.getTimeLabel() + ").");
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SCHEDULE_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, teacherId);
                statement.setInt(2, sectionId);
                statement.setInt(3, subjectId);
                statement.setInt(4, roomId);
                statement.setInt(5, day.getValue());
                statement.setTime(6, java.sql.Time.valueOf(startTime));
                statement.setTime(7, java.sql.Time.valueOf(endTime));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        return ServiceResult.failure("Could not save the class.");
                    }
                }
            }
            return ServiceResult.success("Class saved.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the class.");
        }
    }

    public ServiceResult<Schedule> updateSchedule(int scheduleId, int teacherId, int sectionId, int subjectId, int roomId,
            DayOfWeek day, LocalTime startTime, LocalTime endTime) {
        if (scheduleId <= 0 || teacherId <= 0 || sectionId <= 0 || subjectId <= 0 || roomId <= 0 || day == null || startTime == null || endTime == null) {
            return ServiceResult.failure("Complete the schedule form first.");
        }
        if (!startTime.isBefore(endTime)) {
            return ServiceResult.failure("End time must be after the start time.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection()) {
            Schedule conflicting = findConflictingSchedule(connection, teacherId, day, startTime, endTime, scheduleId);
            if (conflicting != null) {
                return ServiceResult.failure("This time overlaps with " + conflicting.subjectName() + " (" + conflicting.getTimeLabel() + ").");
            }
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_SCHEDULE_SQL)) {
                statement.setInt(1, teacherId);
                statement.setInt(2, sectionId);
                statement.setInt(3, subjectId);
                statement.setInt(4, roomId);
                statement.setInt(5, day.getValue());
                statement.setTime(6, java.sql.Time.valueOf(startTime));
                statement.setTime(7, java.sql.Time.valueOf(endTime));
                statement.setInt(8, scheduleId);
                statement.executeUpdate();
            }
            return ServiceResult.success("Schedule updated.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the schedule.");
        }
    }

    public ServiceResult<Void> deactivateSchedule(int scheduleId) {
        if (scheduleId <= 0) {
            return ServiceResult.failure("Schedule not found.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(DEACTIVATE_SCHEDULE_SQL)) {
            statement.setInt(1, scheduleId);
            statement.executeUpdate();
            return ServiceResult.success("Schedule deactivated.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not deactivate the schedule.");
        }
    }

    public ServiceResult<Void> submitScheduleRequest(int teacherId, int scheduleId, int sectionId, int subjectId, int roomId,
            DayOfWeek day, LocalTime startTime, LocalTime endTime, String reason) {
        String cleanReason = safe(reason);
        if (teacherId <= 0 || scheduleId <= 0 || sectionId <= 0 || subjectId <= 0 || roomId <= 0 || day == null || startTime == null || endTime == null) {
            return ServiceResult.failure("Complete the schedule request first.");
        }
        if (cleanReason.isBlank()) {
            return ServiceResult.failure("Enter a short reason.");
        }
        if (!startTime.isBefore(endTime)) {
            return ServiceResult.failure("End time must be after the start time.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_REQUEST_SQL)) {
            statement.setInt(1, scheduleId);
            statement.setInt(2, teacherId);
            statement.setInt(3, sectionId);
            statement.setInt(4, subjectId);
            statement.setInt(5, roomId);
            statement.setInt(6, day.getValue());
            statement.setTime(7, java.sql.Time.valueOf(startTime));
            statement.setTime(8, java.sql.Time.valueOf(endTime));
            statement.setString(9, cleanReason);
            statement.executeUpdate();
            return ServiceResult.success("Schedule request sent to the admin.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not send the schedule request.");
        }
    }

    public ServiceResult<List<ScheduleRequest>> getScheduleRequests() {
        return loadRequests(null);
    }

    public ServiceResult<List<ScheduleRequest>> getScheduleRequestsForTeacher(int teacherId) {
        return loadRequests(teacherId);
    }

    public ServiceResult<Void> reviewScheduleRequest(int reviewerId, int requestId, boolean approve) {
        if (reviewerId <= 0 || requestId <= 0) {
            return ServiceResult.failure("Choose a request first.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(SELECT_REQUEST_SQL)) {
                select.setInt(1, requestId);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        return ServiceResult.failure("Request not found.");
                    }
                    ScheduleRequest request = mapRequest(resultSet);
                    if (request.status() != RequestStatus.PENDING) {
                        connection.rollback();
                        return ServiceResult.failure("This request was already reviewed.");
                    }
                    if (approve) {
                        Schedule conflicting = findConflictingSchedule(connection, request.teacherId(), request.day(), request.startTime(), request.endTime(), request.scheduleId());
                        if (conflicting != null) {
                            connection.rollback();
                            return ServiceResult.failure("This time overlaps with " + conflicting.subjectName() + " (" + conflicting.getTimeLabel() + ").");
                        }
                    }
                    try (PreparedStatement updateRequest = connection.prepareStatement(UPDATE_REQUEST_SQL)) {
                        updateRequest.setString(1, approve ? "APPROVED" : "REJECTED");
                        updateRequest.setInt(2, reviewerId);
                        updateRequest.setInt(3, requestId);
                        updateRequest.executeUpdate();
                    }
                    if (approve) {
                        try (PreparedStatement updateSchedule = connection.prepareStatement(UPDATE_SCHEDULE_SQL)) {
                            updateSchedule.setInt(1, request.teacherId());
                            updateSchedule.setInt(2, request.sectionId());
                            updateSchedule.setInt(3, request.subjectId());
                            updateSchedule.setInt(4, request.roomId());
                            updateSchedule.setInt(5, request.day().getValue());
                            updateSchedule.setTime(6, java.sql.Time.valueOf(request.startTime()));
                            updateSchedule.setTime(7, java.sql.Time.valueOf(request.endTime()));
                            updateSchedule.setInt(8, request.scheduleId());
                            updateSchedule.executeUpdate();
                        }
                    }
                    connection.commit();
                    return ServiceResult.success(approve ? "Schedule request approved." : "Schedule request rejected.", null);
                }
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not review the schedule request.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not review the schedule request.");
        }
    }

    private ServiceResult<List<Schedule>> loadSchedules(String sql, Integer teacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<Schedule> schedules = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (teacherId != null) {
                statement.setInt(1, teacherId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    schedules.add(mapSchedule(resultSet));
                }
            }
            return ServiceResult.success("Schedules loaded.", schedules);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load schedules.");
        }
    }

    private ServiceResult<List<ScheduleRequest>> loadRequests(Integer teacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        String filter = teacherId == null ? "" : "WHERE sr.teacher_user_id = ?";
        List<ScheduleRequest> requests = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_REQUESTS_SQL.formatted(filter))) {
            if (teacherId != null) {
                statement.setInt(1, teacherId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(mapRequest(resultSet));
                }
            }
            return ServiceResult.success("Schedule requests loaded.", requests);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load schedule requests.");
        }
    }

    private boolean hasTeacherConflict(Connection connection, int teacherId, DayOfWeek day, LocalTime startTime, LocalTime endTime) throws SQLException {
        return hasTeacherConflict(connection, teacherId, day, startTime, endTime, -1);
    }

    private boolean hasTeacherConflict(Connection connection, int teacherId, DayOfWeek day, LocalTime startTime, LocalTime endTime, int ignoreScheduleId)
            throws SQLException {
        String sql = CHECK_CONFLICT_SQL;
        if (ignoreScheduleId > 0) {
            sql = """
                    SELECT 1
                    FROM schedules
                    WHERE teacher_user_id = ?
                      AND day_of_week = ?
                      AND is_active = 1
                      AND schedule_id <> ?
                      AND (? < end_time AND ? > start_time)
                    LIMIT 1
                    """;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, day.getValue());
            int timeIndex = 3;
            if (ignoreScheduleId > 0) {
                statement.setInt(3, ignoreScheduleId);
                timeIndex = 4;
            }
            statement.setTime(timeIndex, java.sql.Time.valueOf(startTime));
            statement.setTime(timeIndex + 1, java.sql.Time.valueOf(endTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Schedule findConflictingSchedule(Connection connection, int teacherId,
            DayOfWeek day, LocalTime startTime, LocalTime endTime, int ignoreScheduleId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_CONFLICTING_SCHEDULE_SQL)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, day.getValue());
            statement.setInt(3, ignoreScheduleId);
            statement.setTime(4, java.sql.Time.valueOf(startTime));
            statement.setTime(5, java.sql.Time.valueOf(endTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapSchedule(resultSet);
                }
                return null;
            }
        }
    }

    private Schedule mapSchedule(ResultSet resultSet) throws SQLException {
        return new Schedule(
                resultSet.getInt("schedule_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getString("teacher_name"),
                resultSet.getInt("section_id"),
                resultSet.getString("section_name"),
                resultSet.getInt("subject_id"),
                resultSet.getString("subject_name"),
                resultSet.getInt("room_id"),
                resultSet.getString("room_name"),
                DayOfWeek.of(resultSet.getInt("day_of_week")),
                resultSet.getTime("start_time").toLocalTime(),
                resultSet.getTime("end_time").toLocalTime(),
                resultSet.getBoolean("is_active")
        );
    }

    private ScheduleRequest mapRequest(ResultSet resultSet) throws SQLException {
        return new ScheduleRequest(
                resultSet.getInt("request_id"),
                resultSet.getInt("schedule_id"),
                resultSet.getInt("teacher_user_id"),
                resultSet.getString("teacher_name"),
                resultSet.getInt("section_id"),
                resultSet.getString("section_name"),
                resultSet.getInt("subject_id"),
                resultSet.getString("subject_name"),
                resultSet.getInt("room_id"),
                resultSet.getString("room_name"),
                DayOfWeek.of(resultSet.getInt("day_of_week")),
                resultSet.getTime("start_time").toLocalTime(),
                resultSet.getTime("end_time").toLocalTime(),
                resultSet.getString("reason"),
                mapStatus(resultSet.getString("status")),
                safe(resultSet.getString("reviewed_by")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("reviewed_at") == null ? null : resultSet.getTimestamp("reviewed_at").toLocalDateTime()
        );
    }

    private boolean isCurrentClass(Schedule schedule, LocalDate today, LocalTime now) {
        return schedule.day().getValue() == today.getDayOfWeek().getValue()
                && !now.isBefore(schedule.startTime())
                && !now.isAfter(schedule.endTime());
    }

    private RequestStatus mapStatus(String raw) {
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public ServiceResult<Subject> renameSubject(int subjectId, String newName) {
        String cleanName = safe(newName);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the subject name.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(RENAME_SUBJECT_SQL)) {
            statement.setString(1, cleanName);
            statement.setInt(2, subjectId);
            statement.executeUpdate();
            return ServiceResult.success("Subject renamed.", new Subject(subjectId, cleanName));
        } catch (SQLException ex) {
            return ServiceResult.failure("That subject name already exists or could not be saved.");
        }
    }

    public ServiceResult<Void> deleteSubject(int subjectId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection()) {
            try (PreparedStatement check = connection.prepareStatement(CHECK_SUBJECT_IN_SCHEDULES_SQL)) {
                check.setInt(1, subjectId);
                try (ResultSet resultSet = check.executeQuery()) {
                    if (resultSet.next()) {
                        return ServiceResult.failure("This subject is still used by active schedules.");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(DELETE_SUBJECT_SQL)) {
                statement.setInt(1, subjectId);
                statement.executeUpdate();
            }
            return ServiceResult.success("Subject deleted.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not delete the subject.");
        }
    }

    public ServiceResult<Room> renameRoom(int roomId, String newName) {
        String cleanName = safe(newName);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the room name.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(RENAME_ROOM_SQL)) {
            statement.setString(1, cleanName);
            statement.setInt(2, roomId);
            statement.executeUpdate();
            return ServiceResult.success("Room renamed.", new Room(roomId, cleanName));
        } catch (SQLException ex) {
            return ServiceResult.failure("That room name already exists or could not be saved.");
        }
    }

    public ServiceResult<Void> deleteRoom(int roomId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection()) {
            try (PreparedStatement check = connection.prepareStatement(CHECK_ROOM_IN_SCHEDULES_SQL)) {
                check.setInt(1, roomId);
                try (ResultSet resultSet = check.executeQuery()) {
                    if (resultSet.next()) {
                        return ServiceResult.failure("This room is still used by active schedules.");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(DELETE_ROOM_SQL)) {
                statement.setInt(1, roomId);
                statement.executeUpdate();
            }
            return ServiceResult.success("Room deleted.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not delete the room.");
        }
    }
}
