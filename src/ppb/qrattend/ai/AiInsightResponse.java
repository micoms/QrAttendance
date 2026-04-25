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
        return new AiInsightResponse(insightType, targetType, targetId, title, "", "",
                "", statusMessage, null, false);
    }

    public String getInsightType() {
        return insightType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTitle() {
        return title;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public boolean hasSummary() {
        return !summaryText.isBlank();
    }

    public String getDisplayText() {
        return hasSummary() ? summaryText : statusMessage;
    }
}
