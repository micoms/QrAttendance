package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;

public final class AuditLogService {

    public static final class AuditTrailEntry {

        private final int id;
        private final Integer actorUserId;
        private final String actionType;
        private final String entityType;
        private final String entityId;
        private final String oldValuesJson;
        private final String newValuesJson;
        private final String notes;
        private final LocalDateTime createdAt;

        public AuditTrailEntry(int id, Integer actorUserId, String actionType, String entityType, String entityId,
                String oldValuesJson, String newValuesJson, String notes, LocalDateTime createdAt) {
            this.id = id;
            this.actorUserId = actorUserId;
            this.actionType = actionType;
            this.entityType = entityType;
            this.entityId = entityId;
            this.oldValuesJson = oldValuesJson;
            this.newValuesJson = newValuesJson;
            this.notes = notes;
            this.createdAt = createdAt;
        }

        public int getId() {
            return id;
        }

        public Integer getActorUserId() {
            return actorUserId;
        }

        public String getActionType() {
            return actionType;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getEntityId() {
            return entityId;
        }

        public String getOldValuesJson() {
            return oldValuesJson;
        }

        public String getNewValuesJson() {
            return newValuesJson;
        }

        public String getNotes() {
            return notes;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    private final DatabaseManager databaseManager;

    public AuditLogService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public AuditLogService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<Void> logAction(Integer actorUserId, String actionType, String entityType, String entityId,
            String oldValuesJson, String newValuesJson, String notes) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (isBlank(actionType) || isBlank(entityType) || isBlank(entityId)) {
            return ServiceResult.failure("Audit action details are incomplete.");
        }

        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO audit_logs
                            (actor_user_id, action_type, entity_type, entity_id, old_values_json, new_values_json, notes, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """)) {
            if (actorUserId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setInt(1, actorUserId);
            }
            statement.setString(2, actionType.trim());
            statement.setString(3, entityType.trim());
            statement.setString(4, entityId.trim());
            statement.setString(5, emptyToNull(oldValuesJson));
            statement.setString(6, emptyToNull(newValuesJson));
            statement.setString(7, emptyToNull(notes));
            statement.executeUpdate();
            return ServiceResult.success("Audit log saved.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the audit log.");
        }
    }

    public ServiceResult<List<AuditTrailEntry>> getAuditTrail(String entityType, String entityId, int limit) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (isBlank(entityType) || isBlank(entityId) || limit <= 0) {
            return ServiceResult.failure("Audit trail filters are incomplete.");
        }

        List<AuditTrailEntry> entries = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT audit_id, actor_user_id, action_type, entity_type, entity_id,
                               old_values_json, new_values_json, notes, created_at
                        FROM audit_logs
                        WHERE entity_type = ?
                          AND entity_id = ?
                        ORDER BY created_at DESC, audit_id DESC
                        LIMIT ?
                        """)) {
            statement.setString(1, entityType.trim());
            statement.setString(2, entityId.trim());
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new AuditTrailEntry(
                            resultSet.getInt("audit_id"),
                            resultSet.getObject("actor_user_id") == null ? null : resultSet.getInt("actor_user_id"),
                            resultSet.getString("action_type"),
                            resultSet.getString("entity_type"),
                            resultSet.getString("entity_id"),
                            resultSet.getString("old_values_json"),
                            resultSet.getString("new_values_json"),
                            resultSet.getString("notes"),
                            toLocalDateTime(resultSet.getTimestamp("created_at"))
                    ));
                }
            }
            return ServiceResult.success("Audit trail loaded.", entries);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load the audit trail.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
    }
}
