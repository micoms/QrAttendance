package ppb.qrattend.ai;

import ppb.qrattend.service.ServiceResult;

public interface AiClient {

    ServiceResult<AiInsightResponse> generateInsight(AiInsightRequest request);

    boolean isAvailable();

    String getStatusMessage();
}
