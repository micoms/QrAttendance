package ppb.qrattend.ai;

import java.time.LocalDateTime;

public final class AiInsightResponse {

    private final String insightType;
    private final String targetType;
    private final String targetId;
    private final String title;
    private final String provider;
    private final String model;
    private final String summaryText;
    private final String statusMessage;
    private final LocalDateTime generatedAt;
    private final boolean fromCache;

    private AiInsightResponse(String insightType, String targetType, String targetId, String title,
            String provider, String model, String summaryText, String statusMessage,
            LocalDateTime generatedAt, boolean fromCache) {
        this.insightType = insightType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.title = title;
        this.provider = provider;
        this.model = model;
        this.summaryText = summaryText;
        this.statusMessage = statusMessage;
        this.generatedAt = generatedAt;
        this.fromCache = fromCache;
    }

    public static AiInsightResponse generated(AiInsightRequest request, String provider, String model,
            String summaryText, LocalDateTime generatedAt, boolean fromCache) {
        // Mini-code guide:
        // 1. Copy the request metadata into the response so cached and live results share the same shape.
        // 2. Store provider/model information for UI transparency.
        // 3. Keep statusMessage blank because this path represents a successful generated summary.
        return new AiInsightResponse(
                request.getInsightType(),
                request.getTargetType(),
                request.getTargetId(),
                request.getTitle(),
                provider,
                model,
                summaryText,
                "",
                generatedAt,
                fromCache
        );
    }

    public static AiInsightResponse placeholder(String insightType, String targetType, String targetId,
            String title, String statusMessage) {
        // Mini-code guide:
        // 1. Use this when AI is disabled, not configured, or no cached result exists yet.
        // 2. Leave summary/provider/model empty and surface the friendly statusMessage instead.
        return new AiInsightResponse(insightType, targetType, targetId, title, "", "",
                "", statusMessage, null, false);
    }

    public String getInsightType() {
        // Mini-code guide:
        // 1. Return the insight category for UI labeling and cache lookup.
        return insightType;
    }

    public String getTargetType() {
        // Mini-code guide:
        // 1. Return the scope type this insight belongs to.
        return targetType;
    }

    public String getTargetId() {
        // Mini-code guide:
        // 1. Return the unique target key used for cache/persistence matching.
        return targetId;
    }

    public String getTitle() {
        // Mini-code guide:
        // 1. Return the card title shown in the Swing UI.
        return title;
    }

    public String getProvider() {
        // Mini-code guide:
        // 1. Return the provider name, such as Gemini.
        return provider;
    }

    public String getModel() {
        // Mini-code guide:
        // 1. Return the specific AI model used to generate the summary.
        return model;
    }

    public String getSummaryText() {
        // Mini-code guide:
        // 1. Return the generated plain-text insight body.
        return summaryText;
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return the placeholder/error text when no summary is available.
        return statusMessage;
    }

    public LocalDateTime getGeneratedAt() {
        // Mini-code guide:
        // 1. Return when the insight was generated or loaded from cache.
        return generatedAt;
    }

    public boolean isFromCache() {
        // Mini-code guide:
        // 1. Return whether this response came from ai_insights instead of a live API call.
        return fromCache;
    }

    public boolean hasSummary() {
        // Mini-code guide:
        // 1. Return true only when summaryText contains usable AI output.
        return !summaryText.isBlank();
    }

    public String getDisplayText() {
        // Mini-code guide:
        // 1. Prefer summaryText when available.
        // 2. Otherwise show the fallback status message so the UI still has meaningful content.
        return hasSummary() ? summaryText : statusMessage;
    }
}
