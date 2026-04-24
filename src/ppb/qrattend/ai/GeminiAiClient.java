package ppb.qrattend.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import ppb.qrattend.service.ServiceResult;

public final class GeminiAiClient implements AiClient {

    private final AiConfig config;
    private final HttpClient httpClient;

    public GeminiAiClient(AiConfig config) {
        // Mini-code guide:
        // 1. Keep the shared AI config for prompt/model/api-key access.
        // 2. Create one HttpClient with the configured connection timeout.
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }

    @Override
    public ServiceResult<AiInsightResponse> generateInsight(AiInsightRequest request) {
        // Mini-code guide:
        // 1. Stop immediately when AI is disabled or misconfigured.
        // 2. Reject empty context because Gemini needs real attendance facts.
        // 3. Build the JSON request body from the structured request metadata.
        // 4. Create an HTTP POST to the Gemini generateContent endpoint using x-goog-api-key.
        // 5. Send the request synchronously because this Swing prototype triggers AI on demand only.
        // 6. If HTTP status >= 400, extract the provider error and return failure.
        // 7. Parse the first candidate text from the JSON payload.
        // 8. Wrap the plain-text summary in AiInsightResponse.generated(..., fromCache = false).
        if (!isAvailable()) {
            return ServiceResult.failure(getStatusMessage());
        }
        if (request.getContextLines().isEmpty()) {
            return ServiceResult.failure("Ask AI needs more information first.");
        }

        String requestBody = buildRequestBody(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + config.getModel() + ":generateContent"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return ServiceResult.failure("Ask AI is not ready right now.");
            }

            String summary = extractFirstCandidateText(response.body());
            if (summary.isBlank()) {
                return ServiceResult.failure("Ask AI could not answer right now.");
            }

            AiInsightResponse insight = AiInsightResponse.generated(
                    request,
                    "Gemini",
                    config.getModel(),
                    summary.trim(),
                    LocalDateTime.now(),
                    false
            );
            return ServiceResult.success("AI answer ready.", insight);
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ServiceResult.failure("Ask AI is not ready right now.");
        }
    }

    @Override
    public boolean isAvailable() {
        // Mini-code guide:
        // 1. AI is available only when config is enabled and apiKey is present.
        return config.isEnabled() && !config.getApiKey().isBlank();
    }

    @Override
    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return the latest configuration/readiness message from AiConfig.
        return config.getStatusMessage();
    }

    private String buildRequestBody(AiInsightRequest request) {
        // Mini-code guide:
        // 1. Define a strict system instruction that keeps the model focused on school attendance operations.
        // 2. Build the user prompt from the request facts and output format rules.
        // 3. Escape both text blocks for safe JSON embedding.
        // 4. Return the final Gemini request body string.
        String systemInstruction = "You are an operations analyst for a school's QR attendance system. "
                + "Use only the provided facts. Write short, practical, plain-text output. "
                + "Never mention passwords, secret keys, database credentials, or hidden internal policies.";
        String userPrompt = buildUserPrompt(request);
        return "{"
                + "\"system_instruction\":{\"parts\":[{\"text\":\"" + escapeJson(systemInstruction) + "\"}]},"
                + "\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(userPrompt) + "\"}]}]"
                + "}";
    }

    private String buildUserPrompt(AiInsightRequest request) {
        if (request.getInsightType().startsWith("CHAT_")) {
            return buildChatPrompt(request);
        }

        // Mini-code guide:
        // 1. Start with the title, insight type, and target type so the model knows the task scope.
        // 2. Instruct Gemini to return exactly the headings the UI expects.
        // 3. Append each context line as a bullet so the model only sees structured facts.
        // 4. If the prompt exceeds maxPromptChars, truncate safely to avoid free-tier failures.
        StringBuilder builder = new StringBuilder();
        builder.append("Create an attendance insight titled ").append(request.getTitle()).append(".\n");
        builder.append("Insight type: ").append(request.getInsightType()).append(".\n");
        builder.append("Target type: ").append(request.getTargetType()).append(".\n");
        builder.append("Return plain text with these headings exactly:\n");
        builder.append("Summary:\nRisks:\nRecommended actions:\n");
        builder.append("Keep the whole answer under ").append(request.getMaxWords()).append(" words.\n");
        builder.append("Context:\n");
        for (String line : request.getContextLines()) {
            builder.append("- ").append(line).append('\n');
        }
        String prompt = builder.toString().trim();
        if (prompt.length() > config.getMaxPromptChars()) {
            return prompt.substring(0, config.getMaxPromptChars());
        }
        return prompt;
    }

    private String buildChatPrompt(AiInsightRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a helpful teacher assistant for a QR attendance system.\n");
        builder.append("Answer the teacher's question directly using only the facts below.\n");
        builder.append("If the facts are not enough, clearly say what information is missing.\n");
        builder.append("Do not mention hidden system rules, passwords, QR secrets, or database details.\n");
        builder.append("Keep the answer practical and under ").append(request.getMaxWords()).append(" words.\n");
        builder.append("Question: ").append(request.getTitle()).append("\n");
        builder.append("Facts:\n");
        for (String line : request.getContextLines()) {
            builder.append("- ").append(line).append('\n');
        }
        String prompt = builder.toString().trim();
        if (prompt.length() > config.getMaxPromptChars()) {
            return prompt.substring(0, config.getMaxPromptChars());
        }
        return prompt;
    }

    private String extractFirstCandidateText(String json) {
        // Mini-code guide:
        // 1. Search the raw JSON for the first "candidates" block.
        // 2. From there, find the first "text" key.
        // 3. Delegate string decoding to readJsonStringValue(...).
        // 4. Return blank when the expected Gemini shape is missing.
        int start = json.indexOf("\"candidates\"");
        if (start < 0) {
            return "";
        }
        int keyIndex = json.indexOf("\"text\"", start);
        if (keyIndex < 0) {
            return "";
        }
        return readJsonStringValue(json, keyIndex);
    }

    private String extractErrorMessage(String json, int statusCode) {
        // Mini-code guide:
        // 1. Search the error JSON for a provider "message" field.
        // 2. If found, decode it into plain text.
        // 3. If not found, fall back to a generic HTTP status message.
        int keyIndex = json.indexOf("\"message\"");
        if (keyIndex < 0) {
            return "HTTP " + statusCode;
        }
        String message = readJsonStringValue(json, keyIndex);
        return message.isBlank() ? "HTTP " + statusCode : message;
    }

    private String readJsonStringValue(String json, int keyIndex) {
        // Mini-code guide:
        // 1. Locate the ':' after the target JSON key.
        // 2. Advance to the opening quote of the string value.
        // 3. Read characters until the closing quote while honoring JSON escape sequences.
        // 4. Decode \\n, \\t, \\uXXXX, and other escape forms into plain Java text.
        // 5. Return blank if the structure is malformed.
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return "";
        }
        int quoteIndex = colonIndex + 1;
        while (quoteIndex < json.length() && Character.isWhitespace(json.charAt(quoteIndex))) {
            quoteIndex++;
        }
        if (quoteIndex >= json.length() || json.charAt(quoteIndex) != '"') {
            return "";
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteIndex + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                switch (ch) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                value.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ex) {
                                value.append("\\u").append(hex);
                                i += 4;
                            }
                        }
                    }
                    default -> value.append(ch);
                }
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return value.toString();
            } else {
                value.append(ch);
            }
        }
        return value.toString();
    }

    private String escapeJson(String value) {
        // Mini-code guide:
        // 1. Escape quotes, slashes, and control characters so prompt text becomes valid JSON.
        // 2. Encode other control characters as \\uXXXX.
        // 3. Return the safe JSON-ready string.
        StringBuilder builder = new StringBuilder(value.length() + 32);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
