package ppb.qrattend.email;

import com.google.zxing.WriterException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import ppb.qrattend.qr.QrCodeService;

public final class ResendEmailClient {

    private static final String USER_AGENT = "qrattend-desktop/1.0";

    public static final class EmailSendResult {

        private final boolean success;
        private final String providerMessageId;
        private final String message;

        private EmailSendResult(boolean success, String providerMessageId, String message) {
            // Mini-code guide:
            // 1. Carry just enough information back to TeacherService to update the DB log.
            this.success = success;
            this.providerMessageId = providerMessageId;
            this.message = message;
        }

        public static EmailSendResult success(String providerMessageId, String message) {
            // Mini-code guide:
            // 1. Use this when Resend accepted the email.
            return new EmailSendResult(true, providerMessageId, message);
        }

        public static EmailSendResult failure(String message) {
            // Mini-code guide:
            // 1. Use this when Resend rejected the request or the network call failed.
            return new EmailSendResult(false, "", message);
        }

        public boolean isSuccess() {
            // Mini-code guide:
            // 1. Return whether Resend accepted the email.
            return success;
        }

        public String getProviderMessageId() {
            // Mini-code guide:
            // 1. Return the Resend email id when available.
            return providerMessageId;
        }

        public String getMessage() {
            // Mini-code guide:
            // 1. Return the status or error message.
            return message;
        }
    }

    private final ResendConfig config;
    private final HttpClient httpClient;

    public ResendEmailClient(ResendConfig config) {
        // Mini-code guide:
        // 1. Keep the immutable config and one reusable HTTP client.
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }

    public static ResendEmailClient createDefault() {
        // Mini-code guide:
        // 1. Load default mail settings and build a ready client.
        return new ResendEmailClient(ResendConfig.loadDefault());
    }

    public boolean isAvailable() {
        // Mini-code guide:
        // 1. Return true only when Resend is enabled and configured.
        return config.isEnabled() && !config.getApiKey().isBlank() && !config.getFromEmail().isBlank();
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return the configuration/readiness message for UI/service warnings.
        return config.getStatusMessage();
    }

    public EmailSendResult sendTeacherPasswordEmail(String recipientEmail, String teacherName, String temporaryPassword,
            boolean resetMode) {
        // Mini-code guide:
        // 1. Build the teacher onboarding/reset subject and body.
        // 2. Delegate the HTTP call to the shared sendPlainTextEmail helper.
        String subject = resetMode ? "Your teacher password was reset"
                : "Your teacher account is ready";
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(teacherName).append(",\n\n");
        body.append("Your teacher account is ready.\n");
        body.append("Temporary password: ").append(temporaryPassword).append("\n\n");
        body.append("Please sign in and change this password as soon as you can.\n\n");
        body.append("If you were not expecting this email, please contact the school admin.\n");
        return sendEmail(recipientEmail, subject, body.toString(), "", "", "Password email sent.");
    }

    public EmailSendResult sendStudentQrEmail(String recipientEmail, String studentName, String studentCode,
            String qrPayload, boolean resendMode) {
        // Mini-code guide:
        // 1. Build the student QR issuance/resend subject and body.
        // 2. Include the permanent QR payload so the team can later replace this with an attachment or image.
        String subject = resendMode ? "Your QR code was sent again"
                : "Your QR code is ready";
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(studentName).append(",\n\n");
        body.append("Your school QR code is ready.\n");
        body.append("Student ID: ").append(studentCode).append("\n");
        body.append("Your QR code is attached to this email.\n\n");
        body.append("Please keep it safe. You will use it when attendance is checked.\n");
        body.append("If you were not expecting this email, please contact your teacher.\n");
        try {
            String attachment = QrCodeService.generateQrBase64Png(qrPayload);
            String htmlBody = "<p>Hello " + escapeHtml(studentName) + ",</p>"
                    + "<p>Your school QR code is ready.</p>"
                    + "<p><strong>Student ID:</strong> " + escapeHtml(studentCode) + "</p>"
                    + "<p><img src=\"cid:student-qr\" alt=\"Student QR Code\"/></p>"
                    + "<p>Please keep it safe. You will use it when attendance is checked.</p>";
            String attachmentsJson = ",\"attachments\":[{"
                    + "\"content\":\"" + attachment + "\","
                    + "\"filename\":\"qrattend-" + escapeJson(studentCode.toLowerCase()) + ".png\","
                    + "\"content_type\":\"image/png\","
                    + "\"content_id\":\"student-qr\""
                    + "}]";
            return sendEmail(recipientEmail, subject, body.toString(), htmlBody, attachmentsJson, "QR code email sent.");
        } catch (WriterException | IOException ex) {
            return EmailSendResult.failure("Could not generate the QR image: " + ex.getMessage());
        }
    }

    private EmailSendResult sendEmail(String recipientEmail, String subject, String textBody, String htmlBody,
            String attachmentsJson, String successMessage) {
        // Mini-code guide:
        // 1. Stop immediately when Resend is not configured.
        // 2. Build the JSON payload with from/to/subject/text/reply_to.
        // 3. POST it to {apiBaseUrl}/emails with the Bearer API key.
        // 4. Parse the returned email id on success.
        // 5. Parse a provider error message on failure.
        if (!isAvailable()) {
            return EmailSendResult.failure(getStatusMessage());
        }

        String requestBody = buildRequestBody(recipientEmail, subject, textBody, htmlBody, attachmentsJson);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(config.getApiBaseUrl()) + "/emails"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return EmailSendResult.failure("Resend rejected the email: " + extractErrorMessage(response.body(), response.statusCode()));
            }
            String id = extractStringField(response.body(), "\"id\"");
            return EmailSendResult.success(id, successMessage);
        } catch (IOException ex) {
            return EmailSendResult.failure("Could not reach Resend: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return EmailSendResult.failure("Email sending was interrupted: " + ex.getMessage());
        }
    }

    private String buildRequestBody(String recipientEmail, String subject, String textBody, String htmlBody, String attachmentsJson) {
        // Mini-code guide:
        // 1. Build a small JSON payload that matches the Resend /emails API.
        StringBuilder builder = new StringBuilder();
        builder.append('{')
                .append("\"from\":\"").append(escapeJson(config.getFromName() + " <" + config.getFromEmail() + ">")).append("\",")
                .append("\"to\":[\"").append(escapeJson(recipientEmail)).append("\"],")
                .append("\"subject\":\"").append(escapeJson(subject)).append("\",")
                .append("\"text\":\"").append(escapeJson(textBody)).append("\"");
        if (!htmlBody.isBlank()) {
            builder.append(",\"html\":\"").append(escapeJson(htmlBody)).append("\"");
        }
        if (!config.getReplyTo().isBlank()) {
            builder.append(",\"reply_to\":\"").append(escapeJson(config.getReplyTo())).append("\"");
        }
        if (!attachmentsJson.isBlank()) {
            builder.append(attachmentsJson);
        }
        builder.append('}');
        return builder.toString();
    }

    private String extractErrorMessage(String json, int statusCode) {
        // Mini-code guide:
        // 1. Try the common Resend "message" field first.
        // 2. Fall back to HTTP status text when the body does not match expectations.
        String message = extractStringField(json, "\"message\"");
        return message.isBlank() ? "HTTP " + statusCode : message;
    }

    private String extractStringField(String json, String fieldName) {
        // Mini-code guide:
        // 1. Find the requested JSON key and decode the following string value.
        int keyIndex = json.indexOf(fieldName);
        if (keyIndex < 0) {
            return "";
        }
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
        // 1. Escape special characters so plain text becomes safe JSON.
        StringBuilder builder = new StringBuilder(value.length() + 24);
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

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String trimTrailingSlash(String value) {
        // Mini-code guide:
        // 1. Prevent double slashes when composing the API URL.
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
