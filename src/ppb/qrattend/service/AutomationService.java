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
import java.util.Locale;
import ppb.qrattend.db.DatabaseManager;

public final class AutomationService {

    public static final class AutomationRuleRecord {

        private final int automationId;
        private final String ruleName;
        private final String triggerType;
        private final String scheduleExpression;
        private final String configJson;
        private final boolean active;
        private final LocalDateTime lastRunAt;
        private final LocalDateTime nextRunAt;

        public AutomationRuleRecord(int automationId, String ruleName, String triggerType, String scheduleExpression,
                String configJson, boolean active, LocalDateTime lastRunAt, LocalDateTime nextRunAt) {
            this.automationId = automationId;
            this.ruleName = ruleName;
            this.triggerType = triggerType;
            this.scheduleExpression = scheduleExpression;
            this.configJson = configJson;
            this.active = active;
            this.lastRunAt = lastRunAt;
            this.nextRunAt = nextRunAt;
        }

        public int getAutomationId() {
            return automationId;
        }

        public String getRuleName() {
            return ruleName;
        }

        public String getTriggerType() {
            return triggerType;
        }

        public String getScheduleExpression() {
            return scheduleExpression;
        }

        public String getConfigJson() {
            return configJson;
        }

        public boolean isActive() {
            return active;
        }

        public LocalDateTime getLastRunAt() {
            return lastRunAt;
        }

        public LocalDateTime getNextRunAt() {
            return nextRunAt;
        }
    }

    public static final class AutomationRunRecord {

        private final int runId;
        private final int automationId;
        private final String runStatus;
        private final String outputSummary;
        private final LocalDateTime startedAt;
        private final LocalDateTime finishedAt;

        public AutomationRunRecord(int runId, int automationId, String runStatus, String outputSummary,
                LocalDateTime startedAt, LocalDateTime finishedAt) {
            this.runId = runId;
            this.automationId = automationId;
            this.runStatus = runStatus;
            this.outputSummary = outputSummary;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }

        public int getRunId() {
            return runId;
        }

        public int getAutomationId() {
            return automationId;
        }

        public String getRunStatus() {
            return runStatus;
        }

        public String getOutputSummary() {
            return outputSummary;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public LocalDateTime getFinishedAt() {
            return finishedAt;
        }
    }

    private static final List<String> ALLOWED_TRIGGERS = List.of(
            "DAILY_SUMMARY",
            "LOW_ATTENDANCE",
            "FAILED_EMAIL",
            "DEVICE_OFFLINE",
            "MANUAL"
    );

    private static final String INSERT_RULE_SQL = """
            INSERT INTO automation_rules
                (rule_name, trigger_type, schedule_expression, config_json, is_active, created_by_user_id, next_run_at)
            VALUES (?, ?, ?, ?, 1, ?, ?)
            """;

    private static final String SELECT_RULES_SQL = """
            SELECT automation_id, rule_name, trigger_type, schedule_expression, config_json,
                   is_active, last_run_at, next_run_at
            FROM automation_rules
            ORDER BY rule_name ASC, automation_id ASC
            """;

    private static final String SELECT_RULE_BY_ID_SQL = """
            SELECT automation_id, rule_name, trigger_type, schedule_expression, config_json,
                   is_active, last_run_at, next_run_at
            FROM automation_rules
            WHERE automation_id = ?
            LIMIT 1
            """;

    private static final String INSERT_RUN_SQL = """
            INSERT INTO automation_runs
                (automation_id, run_status, output_summary, started_at)
            VALUES (?, 'RUNNING', 'Automation started.', CURRENT_TIMESTAMP)
            """;

    private static final String UPDATE_RUN_SQL = """
            UPDATE automation_runs
            SET run_status = ?,
                output_summary = ?,
                finished_at = CURRENT_TIMESTAMP
            WHERE run_id = ?
            """;

    private static final String UPDATE_RULE_AFTER_RUN_SQL = """
            UPDATE automation_rules
            SET last_run_at = CURRENT_TIMESTAMP,
                next_run_at = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE automation_id = ?
            """;

    private static final String DISABLE_RULE_SQL = """
            UPDATE automation_rules
            SET is_active = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE automation_id = ?
            """;

    private static final String INSERT_AUDIT_SQL = """
            INSERT INTO audit_logs
                (actor_user_id, action_type, entity_type, entity_id, old_values_json, new_values_json, notes, created_at)
            VALUES (?, ?, 'AUTOMATION_RULE', ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private final DatabaseManager databaseManager;

    public AutomationService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public AutomationService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean isReady() {
        return databaseManager.isReady();
    }

    public ServiceResult<AutomationRuleRecord> createAutomationRule(String ruleName, String triggerType,
            String scheduleExpression, String configJson, int createdByUserId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        String safeName = clean(ruleName);
        String safeTrigger = clean(triggerType).toUpperCase(Locale.ENGLISH);
        String safeSchedule = clean(scheduleExpression);
        String safeConfig = clean(configJson).isBlank() ? "{}" : clean(configJson);

        if (safeName.isBlank() || safeTrigger.isBlank() || safeSchedule.isBlank()) {
            return ServiceResult.failure("Complete the automation details first.");
        }
        if (createdByUserId <= 0 || !ALLOWED_TRIGGERS.contains(safeTrigger)) {
            return ServiceResult.failure("Check the automation details and try again.");
        }

        LocalDateTime nextRunAt = computeNextRun(safeSchedule, LocalDateTime.now());

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!userExists(connection, createdByUserId, "ADMIN")) {
                    connection.rollback();
                    return ServiceResult.failure("Admin account was not found.");
                }

                int automationId;
                try (PreparedStatement statement = connection.prepareStatement(INSERT_RULE_SQL, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, safeName);
                    statement.setString(2, safeTrigger);
                    statement.setString(3, safeSchedule);
                    statement.setString(4, safeConfig);
                    statement.setInt(5, createdByUserId);
                    statement.setTimestamp(6, Timestamp.valueOf(nextRunAt));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Automation rule insert did not return a generated key.");
                        }
                        automationId = keys.getInt(1);
                    }
                }

                insertAudit(connection, createdByUserId, "AUTOMATION_CREATE", automationId,
                        null, buildRuleJson(safeName, safeTrigger, safeSchedule, safeConfig, true),
                        "Automation rule was created.");
                connection.commit();
                return ServiceResult.success("Automation rule saved.", loadRule(connection, automationId));
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not save the automation rule.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not save the automation rule.");
        }
    }

    public ServiceResult<List<AutomationRuleRecord>> getAutomationRules() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }

        List<AutomationRuleRecord> rules = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_RULES_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rules.add(mapRule(resultSet));
            }
            return ServiceResult.success("Automation rules loaded.", rules);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load automation rules.");
        }
    }

    public ServiceResult<AutomationRunRecord> runAutomationRule(int automationId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (automationId <= 0) {
            return ServiceResult.failure("Choose an automation rule first.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                AutomationRuleRecord rule = loadRule(connection, automationId);
                if (rule == null) {
                    connection.rollback();
                    return ServiceResult.failure("Automation rule not found.");
                }
                if (!rule.isActive()) {
                    connection.rollback();
                    return ServiceResult.failure("This automation rule is turned off.");
                }

                int runId;
                try (PreparedStatement statement = connection.prepareStatement(INSERT_RUN_SQL, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, automationId);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Automation run insert did not return a generated key.");
                        }
                        runId = keys.getInt(1);
                    }
                }

                String summary = buildRunSummary(connection, rule);
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_RUN_SQL)) {
                    statement.setString(1, "SUCCESS");
                    statement.setString(2, summary);
                    statement.setInt(3, runId);
                    statement.executeUpdate();
                }

                LocalDateTime nextRunAt = computeNextRun(rule.getScheduleExpression(), LocalDateTime.now());
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_RULE_AFTER_RUN_SQL)) {
                    statement.setTimestamp(1, Timestamp.valueOf(nextRunAt));
                    statement.setInt(2, automationId);
                    statement.executeUpdate();
                }

                connection.commit();
                return ServiceResult.success("Automation rule ran successfully.",
                        new AutomationRunRecord(runId, automationId, "SUCCESS", summary, LocalDateTime.now(), LocalDateTime.now()));
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not run the automation rule.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not run the automation rule.");
        }
    }

    public ServiceResult<Void> disableAutomationRule(int automationId, int actorUserId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        if (automationId <= 0 || actorUserId <= 0) {
            return ServiceResult.failure("Choose an automation rule first.");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                AutomationRuleRecord existing = loadRule(connection, automationId);
                if (existing == null) {
                    connection.rollback();
                    return ServiceResult.failure("Automation rule not found.");
                }
                if (!userExists(connection, actorUserId, "ADMIN")) {
                    connection.rollback();
                    return ServiceResult.failure("Admin account was not found.");
                }

                try (PreparedStatement statement = connection.prepareStatement(DISABLE_RULE_SQL)) {
                    statement.setInt(1, automationId);
                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        return ServiceResult.failure("Automation rule not found.");
                    }
                }

                insertAudit(connection, actorUserId, "AUTOMATION_DISABLE", automationId,
                        buildRuleJson(existing.getRuleName(), existing.getTriggerType(), existing.getScheduleExpression(), existing.getConfigJson(), existing.isActive()),
                        buildRuleJson(existing.getRuleName(), existing.getTriggerType(), existing.getScheduleExpression(), existing.getConfigJson(), false),
                        "Automation rule was turned off.");
                connection.commit();
                return ServiceResult.success("Automation rule turned off.", null);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                return ServiceResult.failure("Could not update the automation rule.");
            } finally {
                restoreAutoCommit(connection);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not update the automation rule.");
        }
    }

    private AutomationRuleRecord loadRule(Connection connection, int automationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_RULE_BY_ID_SQL)) {
            statement.setInt(1, automationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapRule(resultSet);
            }
        }
    }

    private AutomationRuleRecord mapRule(ResultSet resultSet) throws SQLException {
        return new AutomationRuleRecord(
                resultSet.getInt("automation_id"),
                resultSet.getString("rule_name"),
                resultSet.getString("trigger_type"),
                resultSet.getString("schedule_expression"),
                resultSet.getString("config_json"),
                resultSet.getBoolean("is_active"),
                toLocalDateTime(resultSet.getTimestamp("last_run_at")),
                toLocalDateTime(resultSet.getTimestamp("next_run_at"))
        );
    }

    private String buildRunSummary(Connection connection, AutomationRuleRecord rule) throws SQLException {
        return switch (rule.getTriggerType()) {
            case "DAILY_SUMMARY" -> "Daily summary checked " + count(connection,
                "SELECT COUNT(*) FROM attendance_records WHERE DATE(recorded_at) = CURRENT_DATE") + " attendance records today.";
            case "LOW_ATTENDANCE" -> "Low attendance check found " + count(connection,
                """
                SELECT COUNT(*)
                FROM (
                    SELECT teacher_user_id, subject_name, DATE(recorded_at) AS day_key, COUNT(*) AS record_count
                    FROM attendance_records
                    GROUP BY teacher_user_id, subject_name, DATE(recorded_at)
                    HAVING COUNT(*) < 5
                ) flagged
                """) + " low-count class summaries.";
            case "FAILED_EMAIL" -> "Email check found " + count(connection,
                "SELECT COUNT(*) FROM email_dispatch_logs WHERE delivery_status = 'FAILED'") + " failed email entries.";
            case "DEVICE_OFFLINE" -> "Device check found " + count(connection,
                "SELECT COUNT(*) FROM iot_devices WHERE device_status = 'OFFLINE'") + " offline scanner devices.";
            default -> "Manual automation run completed successfully.";
        };
    }

    private boolean userExists(Connection connection, int userId, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM users
                WHERE user_id = ?
                  AND role = ?
                LIMIT 1
                """)) {
            statement.setInt(1, userId);
            statement.setString(2, role);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private LocalDateTime computeNextRun(String scheduleExpression, LocalDateTime now) {
        String value = clean(scheduleExpression).toUpperCase(Locale.ENGLISH);
        if (value.contains("WEEK")) {
            return now.plusWeeks(1);
        }
        if (value.contains("HOUR")) {
            return now.plusHours(1);
        }
        return now.plusDays(1);
    }

    private void insertAudit(Connection connection, Integer actorUserId, String actionType, int automationId,
            String oldValuesJson, String newValuesJson, String notes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT_SQL)) {
            if (actorUserId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setInt(1, actorUserId);
            }
            statement.setString(2, actionType);
            statement.setString(3, String.valueOf(automationId));
            statement.setString(4, oldValuesJson);
            statement.setString(5, newValuesJson);
            statement.setString(6, notes);
            statement.executeUpdate();
        }
    }

    private String buildRuleJson(String ruleName, String triggerType, String scheduleExpression, String configJson, boolean active) {
        return "{"
                + "\"rule_name\":\"" + escapeJson(ruleName) + "\","
                + "\"trigger_type\":\"" + escapeJson(triggerType) + "\","
                + "\"schedule_expression\":\"" + escapeJson(scheduleExpression) + "\","
                + "\"config_json\":\"" + escapeJson(configJson == null ? "{}" : configJson) + "\","
                + "\"active\":" + active
                + "}";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
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
}
