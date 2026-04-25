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
        this.insightType = insightType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.title = title;
        this.contextLines = Collections.unmodifiableList(new ArrayList<>(contextLines));
        this.maxWords = maxWords;
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

    public List<String> getContextLines() {
        return contextLines;
    }

    public int getMaxWords() {
        return maxWords;
    }
}
