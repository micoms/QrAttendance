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
            this.success = success;
            this.providerMessageId = providerMessageId;
            this.message = message;
        }

        public static EmailSendResult success(String providerMessageId, String message) {
            return new EmailSendResult(true, providerMessageId, message);
        }

        public static EmailSendResult failure(String message) {
            return new EmailSendResult(false, "", message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getProviderMessageId() {
            return providerMessageId;
        }

        public String getMessage() {
            return message;
        }
    }

    private final ResendConfig config;
    private final HttpClient httpClient;

    public ResendEmailClient(ResendConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }

    public static ResendEmailClient createDefault() {
        return new ResendEmailClient(ResendConfig.loadDefault());
    }

    public boolean isAvailable() {
        return config.isEnabled() && !config.getApiKey().isBlank() && !config.getFromEmail().isBlank();
    }

    public String getStatusMessage() {
        return config.getStatusMessage();
    }

    public EmailSendResult sendTeacherPasswordEmail(String recipientEmail, String teacherName, String temporaryPassword,
            boolean resetMode) {
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
        String message = extractStringField(json, "\"message\"");
        return message.isBlank() ? "HTTP " + statusCode : message;
    }

    private String extractStringField(String json, String fieldName) {
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
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
