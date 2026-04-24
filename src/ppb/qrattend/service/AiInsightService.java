package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import ppb.qrattend.ai.AiClient;
import ppb.qrattend.ai.AiConfig;
import ppb.qrattend.ai.AiInsightRequest;
import ppb.qrattend.ai.AiInsightResponse;
import ppb.qrattend.ai.GeminiAiClient;
import ppb.qrattend.db.DatabaseManager;

public final class AiInsightService {

    private static final String INSERT_INSIGHT_SQL = """
            INSERT INTO ai_insights (insight_type, target_type, target_id, score, summary_text)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_LATEST_INSIGHT_SQL = """
            SELECT insight_id, summary_text, score, generated_at
            FROM ai_insights
            WHERE insight_type = ?
              AND target_type = ?
              AND target_id = ?
            ORDER BY generated_at DESC, insight_id DESC
            LIMIT 1
            """;

    private final DatabaseManager databaseManager;
    private final AiConfig aiConfig;
    private final AiClient aiClient;

    public AiInsightService(DatabaseManager databaseManager, AiConfig aiConfig, AiClient aiClient) {
        this.databaseManager = databaseManager;
        this.aiConfig = aiConfig;
        this.aiClient = aiClient;
    }

    public static AiInsightService createDefault() {
        // Mini-code guide:
        // 1. Load AI settings from config/database.properties through AiConfig.loadDefault().
        // 2. Create a MariaDB-aware DatabaseManager from the default DB config.
        // 3. Create the GeminiAiClient using the same AI config so network calls respect timeout/model settings.
        // 4. Return a fully wired AiInsightService instance for AppDataStore.
        AiConfig config = AiConfig.loadDefault();
        return new AiInsightService(DatabaseManager.fromDefaultConfig(), config, new GeminiAiClient(config));
    }

    public boolean isAiAvailable() {
        // Mini-code guide:
        // 1. Delegate to aiClient.isAvailable().
        // 2. Keep this side-effect free so the UI can poll safely without triggering any API call.
        return aiClient.isAvailable();
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Delegate to aiClient.getStatusMessage().
        // 2. Return a human-readable reason when AI is disabled, misconfigured, or unavailable.
        return aiClient.getStatusMessage();
    }

    public ServiceResult<AiInsightResponse> generateDashboardInsights(AiInsightRequest request) {
        // Mini-code guide:
        // 1. Use this for admin/system dashboards only.
        // 2. Validate the request upstream, then forward to generateAndPersist(request).
        // 3. Persist the generated summary into ai_insights for later reuse.
        return generateAndPersist(request);
    }

    public ServiceResult<AiInsightResponse> generateTeacherInsights(AiInsightRequest request) {
        // Mini-code guide:
        // 1. Use this path for teacher-scoped recommendations and risk summaries.
        // 2. Keep the same on-demand generation flow by delegating to generateAndPersist(request).
        return generateAndPersist(request);
    }

    public ServiceResult<AiInsightResponse> generateReportSummary(AiInsightRequest request) {
        // Mini-code guide:
        // 1. Use this only when the user clicks Generate AI Summary on the reports screen.
        // 2. Reuse the shared generateAndPersist(request) flow so prompt building and caching stay consistent.
        return generateAndPersist(request);
    }

    public ServiceResult<AiInsightResponse> detectAttendanceAnomalies(AiInsightRequest request) {
        // Mini-code guide:
        // 1. Call this when the UI wants duplicate-scan or suspicious attendance insight text.
        // 2. Delegate to generateAndPersist(request) so anomaly output is cached in ai_insights too.
        return generateAndPersist(request);
    }

    public ServiceResult<AiInsightResponse> getLatestInsight(String insightType, String targetType, String targetId, String title) {
        // Mini-code guide:
        // 1. Reject immediately if the DB layer is not ready because cached insights live in ai_insights.
        // 2. Open one connection and prepare SELECT_LATEST_INSIGHT_SQL.
        // 3. Bind insightType, targetType, and targetId.
        // 4. If no row is found, return failure("No cached AI insight has been saved yet.").
        // 5. When a row exists, rebuild a lightweight AiInsightRequest so the response keeps the same metadata contract.
        // 6. Map summary_text + generated_at into AiInsightResponse.generated(..., fromCache = true).
        // 7. Return the cached summary without calling Gemini again.
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(aiConfig.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_LATEST_INSIGHT_SQL)) {
            statement.setString(1, insightType);
            statement.setString(2, targetType);
            statement.setString(3, targetId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return ServiceResult.failure("No saved AI answer yet.");
                }
                AiInsightRequest request = new AiInsightRequest(insightType, targetType, targetId, title, java.util.List.of(), 0);
                AiInsightResponse response = AiInsightResponse.generated(
                        request,
                        "Gemini",
                        aiConfig.getModel(),
                        rs.getString("summary_text"),
                        rs.getTimestamp("generated_at").toLocalDateTime(),
                        true
                );
                return ServiceResult.success("Saved AI answer loaded.", response);
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("Ask AI is not ready right now.");
        }
    }

    private ServiceResult<AiInsightResponse> generateAndPersist(AiInsightRequest request) {
        // Mini-code guide:
        // 1. Ask the configured AiClient to generate the summary now.
        // 2. If generation fails, bubble that failure straight back to the UI.
        // 3. If MariaDB is not ready, still return the generated result because live AI text is better than nothing.
        // 4. When DB is ready, INSERT INTO ai_insights:
        //    (insight_type, target_type, target_id, score, summary_text)
        //    VALUES (?, ?, ?, ?, ?)
        // 5. Leave score as null for now until your scoring algorithm is implemented.
        // 6. If caching fails, keep the AI response visible and only downgrade the message to mention that save failed.
        ServiceResult<AiInsightResponse> generated = aiClient.generateInsight(request);
        if (!generated.isSuccess() || generated.getData() == null) {
            return generated;
        }

        if (!databaseManager.isReady()) {
            return generated;
        }

        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_INSIGHT_SQL)) {
            statement.setString(1, request.getInsightType());
            statement.setString(2, request.getTargetType());
            statement.setString(3, request.getTargetId());
            statement.setObject(4, null);
            statement.setString(5, generated.getData().getSummaryText());
            statement.executeUpdate();
            return generated;
        } catch (SQLException ex) {
            return ServiceResult.success(
                    "AI answered, but it could not be saved for later.",
                    generated.getData()
            );
        }
    }
}
