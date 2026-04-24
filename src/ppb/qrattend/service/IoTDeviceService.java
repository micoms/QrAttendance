package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;

public final class IoTDeviceService {

    public static final class IoTDeviceRecord {

        private final int deviceId;
        private final String deviceCode;
        private final String deviceName;
        private final String deviceType;
        private final String roomName;
        private final String deviceStatus;
        private final Integer assignedTeacherId;
        private final LocalDateTime lastSeenAt;

        public IoTDeviceRecord(int deviceId, String deviceCode, String deviceName, String deviceType,
                String roomName, String deviceStatus, Integer assignedTeacherId, LocalDateTime lastSeenAt) {
            this.deviceId = deviceId;
            this.deviceCode = deviceCode;
            this.deviceName = deviceName;
            this.deviceType = deviceType;
            this.roomName = roomName;
            this.deviceStatus = deviceStatus;
            this.assignedTeacherId = assignedTeacherId;
            this.lastSeenAt = lastSeenAt;
        }

        public int getDeviceId() {
            return deviceId;
        }

        public String getDeviceCode() {
            return deviceCode;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public String getRoomName() {
            return roomName;
        }

        public String getDeviceStatus() {
            return deviceStatus;
        }

        public Integer getAssignedTeacherId() {
            return assignedTeacherId;
        }

        public LocalDateTime getLastSeenAt() {
            return lastSeenAt;
        }
    }

    private final DatabaseManager databaseManager;

    public IoTDeviceService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public IoTDeviceService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<IoTDeviceRecord> registerDevice(String deviceCode, String deviceName, String deviceType,
            String roomName, Integer assignedTeacherId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (isBlank(deviceCode) || isBlank(deviceName) || isBlank(deviceType) || isBlank(roomName)) {
            return ServiceResult.failure("Complete the device details first.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (deviceCodeExists(connection, deviceCode)) {
                    connection.rollback();
                    return ServiceResult.failure("That device code is already in use.");
                }
                if (assignedTeacherId != null && !userExists(connection, assignedTeacherId, "TEACHER")) {
                    connection.rollback();
                    return ServiceResult.failure("Assigned teacher was not found.");
                }

                int deviceId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO iot_devices
                            (device_code, device_name, room_name, device_type, device_status, assigned_teacher_user_id)
                        VALUES (?, ?, ?, ?, 'OFFLINE', ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, deviceCode.trim());
                    statement.setString(2, deviceName.trim());
                    statement.setString(3, roomName.trim());
                    statement.setString(4, deviceType.trim().toUpperCase());
                    if (assignedTeacherId == null) {
                        statement.setNull(5, java.sql.Types.BIGINT);
                    } else {
                        statement.setInt(5, assignedTeacherId);
                    }
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        deviceId = keys.getInt(1);
                    }
                }

                insertAudit(connection, null, "IOT_DEVICE_REGISTER", "IOT_DEVICE", String.valueOf(deviceId),
                        "Device was added to the scanner registry.");
                connection.commit();
                return ServiceResult.success("Device registered.", loadDevice(connection, deviceId));
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not register the device.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not register the device.");
        }
    }

    public ServiceResult<Void> updateHeartbeat(int deviceId, String statusSnapshotJson) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (deviceId <= 0) {
            return ServiceResult.failure("Device is required.");
        }

        String snapshot = isBlank(statusSnapshotJson) ? "{\"status\":\"ONLINE\"}" : statusSnapshotJson.trim();
        String deviceStatus = snapshot.toUpperCase().contains("OFFLINE") ? "OFFLINE"
                : snapshot.toUpperCase().contains("ERROR") ? "MAINTENANCE"
                : "ONLINE";

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (loadDevice(connection, deviceId) == null) {
                    connection.rollback();
                    return ServiceResult.failure("Device not found.");
                }

                try (PreparedStatement heartbeat = connection.prepareStatement("""
                        INSERT INTO iot_device_heartbeats
                            (device_id, status_snapshot, received_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        """)) {
                    heartbeat.setInt(1, deviceId);
                    heartbeat.setString(2, snapshot);
                    heartbeat.executeUpdate();
                }

                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE iot_devices
                        SET device_status = ?,
                            last_seen_at = CURRENT_TIMESTAMP
                        WHERE device_id = ?
                        """)) {
                    update.setString(1, deviceStatus);
                    update.setInt(2, deviceId);
                    update.executeUpdate();
                }

                connection.commit();
                return ServiceResult.success("Device heartbeat saved.", null);
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not save the device heartbeat.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the device heartbeat.");
        }
    }

    public ServiceResult<List<IoTDeviceRecord>> getDevices() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        List<IoTDeviceRecord> devices = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT device_id, device_code, device_name, device_type, room_name,
                               device_status, assigned_teacher_user_id, last_seen_at
                        FROM iot_devices
                        ORDER BY device_name ASC, device_id ASC
                        """);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                devices.add(mapDevice(resultSet));
            }
            return ServiceResult.success("Devices loaded.", devices);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load devices.");
        }
    }

    public ServiceResult<IoTDeviceRecord> assignDeviceToTeacher(int deviceId, int teacherId, String roomName) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (deviceId <= 0 || teacherId <= 0 || isBlank(roomName)) {
            return ServiceResult.failure("Choose a device, teacher, and room.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (loadDevice(connection, deviceId) == null) {
                    connection.rollback();
                    return ServiceResult.failure("Device not found.");
                }
                if (!userExists(connection, teacherId, "TEACHER")) {
                    connection.rollback();
                    return ServiceResult.failure("Teacher not found.");
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE iot_devices
                        SET assigned_teacher_user_id = ?,
                            room_name = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE device_id = ?
                        """)) {
                    statement.setInt(1, teacherId);
                    statement.setString(2, roomName.trim());
                    statement.setInt(3, deviceId);
                    statement.executeUpdate();
                }

                insertAudit(connection, null, "IOT_DEVICE_ASSIGN", "IOT_DEVICE", String.valueOf(deviceId),
                        "Device assignment was updated.");
                connection.commit();
                return ServiceResult.success("Device assignment saved.", loadDevice(connection, deviceId));
            } catch (SQLException ex) {
                connection.rollback();
                return ServiceResult.failure("Could not save the device assignment.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the device assignment.");
        }
    }

    private boolean deviceCodeExists(Connection connection, String deviceCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM iot_devices
                WHERE device_code = ?
                LIMIT 1
                """)) {
            statement.setString(1, deviceCode.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean userExists(Connection connection, int userId, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM users
                WHERE user_id = ?
                  AND role = ?
                  AND account_status = 'ACTIVE'
                LIMIT 1
                """)) {
            statement.setInt(1, userId);
            statement.setString(2, role);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private IoTDeviceRecord loadDevice(Connection connection, int deviceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT device_id, device_code, device_name, device_type, room_name,
                       device_status, assigned_teacher_user_id, last_seen_at
                FROM iot_devices
                WHERE device_id = ?
                LIMIT 1
                """)) {
            statement.setInt(1, deviceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapDevice(resultSet);
            }
        }
    }

    private IoTDeviceRecord mapDevice(ResultSet resultSet) throws SQLException {
        return new IoTDeviceRecord(
                resultSet.getInt("device_id"),
                resultSet.getString("device_code"),
                resultSet.getString("device_name"),
                resultSet.getString("device_type"),
                resultSet.getString("room_name"),
                resultSet.getString("device_status"),
                resultSet.getObject("assigned_teacher_user_id") == null ? null : resultSet.getInt("assigned_teacher_user_id"),
                toLocalDateTime(resultSet.getTimestamp("last_seen_at"))
        );
    }

    private void insertAudit(Connection connection, Integer actorUserId, String actionType, String entityType, String entityId,
            String notes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_logs
                    (actor_user_id, action_type, entity_type, entity_id, old_values_json, new_values_json, notes, created_at)
                VALUES (?, ?, ?, ?, NULL, NULL, ?, CURRENT_TIMESTAMP)
                """)) {
            if (actorUserId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setInt(1, actorUserId);
            }
            statement.setString(2, actionType);
            statement.setString(3, entityType);
            statement.setString(4, entityId);
            statement.setString(5, notes);
            statement.executeUpdate();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
