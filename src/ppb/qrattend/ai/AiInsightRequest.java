package ppb.qrattend.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiInsightRequest {

    private final String insightType;
    private final String targetType;
    private final String targetId;
    private final String title;
    private final List<String> contextLines;
    private final int maxWords;

    public AiInsightRequest(String insightType, String targetType, String targetId, String title,
            List<String> contextLines, int maxWords) {
        // Mini-code guide:
        // 1. Capture all request metadata that the AI layer needs for prompt building and caching.
        // 2. Copy contextLines into a new ArrayList so outside callers cannot mutate the request later.
        // 3. Wrap the copied list with Collections.unmodifiableList for immutability.
        this.insightType = insightType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.title = title;
        this.contextLines = Collections.unmodifiableList(new ArrayList<>(contextLines));
        this.maxWords = maxWords;
    }

    public String getInsightType() {
        // Mini-code guide:
        // 1. Return the insight category, such as TREND or ANOMALY.
        return insightType;
    }

    public String getTargetType() {
        // Mini-code guide:
        // 1. Return the scope type, such as SYSTEM, TEACHER, or SESSION.
        return targetType;
    }

    public String getTargetId() {
        // Mini-code guide:
        // 1. Return the cache/persistence key for the target entity.
        return targetId;
    }

    public String getTitle() {
        // Mini-code guide:
        // 1. Return the UI display title used in the prompt and in the response card.
        return title;
    }

    public List<String> getContextLines() {
        // Mini-code guide:
        // 1. Return the immutable fact list that will be sent to Gemini.
        return contextLines;
    }

    public int getMaxWords() {
        // Mini-code guide:
        // 1. Return the requested response length cap for the AI summary.
        return maxWords;
    }
}
