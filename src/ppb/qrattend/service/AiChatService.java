package ppb.qrattend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ppb.qrattend.ai.AiClient;
import ppb.qrattend.ai.AiConfig;
import ppb.qrattend.ai.AiInsightRequest;
import ppb.qrattend.ai.AiInsightResponse;
import ppb.qrattend.ai.GeminiAiClient;

public final class AiChatService {

    private final AiClient aiClient;
    private final Map<String, StringBuilder> conversations = new HashMap<>();

    public AiChatService() {
        this(new GeminiAiClient(AiConfig.loadDefault()));
    }

    public AiChatService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public String getConversation(int teacherId, String scopeKey) {
        return conversations.getOrDefault(buildKey(teacherId, scopeKey), new StringBuilder()).toString();
    }

    public ServiceResult<String> ask(int teacherId, String scopeKey, String question, List<String> contextLines) {
        String cleanQuestion = safe(question);
        if (cleanQuestion.isBlank()) {
            return ServiceResult.failure("Type your question first.");
        }

        List<String> safeContext = new ArrayList<>(contextLines);
        AiInsightRequest request = new AiInsightRequest(
                "CHAT_PAGE",
                "TEACHER",
                String.valueOf(teacherId),
                cleanQuestion,
                safeContext,
                180
        );
        ServiceResult<AiInsightResponse> result = aiClient.generateInsight(request);
        if (!result.isSuccess() || result.getData() == null) {
            return ServiceResult.failure(result.getMessage());
        }

        StringBuilder conversation = conversations.computeIfAbsent(buildKey(teacherId, scopeKey), key -> new StringBuilder());
        if (!conversation.isEmpty()) {
            conversation.append(System.lineSeparator()).append(System.lineSeparator());
        }
        conversation.append("You: ").append(cleanQuestion).append(System.lineSeparator())
                .append("Ask AI: ").append(result.getData().getDisplayText());
        return ServiceResult.success("AI answer ready.", conversation.toString());
    }

    public ServiceResult<Void> clear(int teacherId, String scopeKey) {
        conversations.remove(buildKey(teacherId, scopeKey));
        return ServiceResult.success("AI chat cleared.", null);
    }

    private String buildKey(int teacherId, String scopeKey) {
        return teacherId + "|" + safe(scopeKey);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
