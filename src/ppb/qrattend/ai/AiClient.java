package ppb.qrattend.ai;

import ppb.qrattend.service.ServiceResult;

public interface AiClient {

    /*
     * Mini-code guide:
     * 1. Validate the request metadata and context lines.
     * 2. Build the provider-specific prompt/body.
     * 3. Call the external AI API.
     * 4. Parse the text response into AiInsightResponse.
     * 5. Wrap the outcome in ServiceResult.
     */
    ServiceResult<AiInsightResponse> generateInsight(AiInsightRequest request);

    /*
     * Mini-code guide:
     * 1. Return true only when provider config is enabled and the required API key is present.
     * 2. Do not trigger a network request here.
     */
    boolean isAvailable();

    /*
     * Mini-code guide:
     * 1. Return a human-readable status explaining whether the provider is ready, disabled, or misconfigured.
     */
    String getStatusMessage();
}
