package ppb.qrattend.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ResendConfig {

    private final boolean enabled;
    private final String provider;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final String replyTo;
    private final String apiBaseUrl;
    private final int timeoutSeconds;
    private final Path sourcePath;
    private final String statusMessage;

    private ResendConfig(boolean enabled, String provider, String apiKey, String fromEmail, String fromName,
            String replyTo, String apiBaseUrl, int timeoutSeconds, Path sourcePath, String statusMessage) {
        // Mini-code guide:
        // 1. Keep one immutable snapshot of the mail settings.
        // 2. This object can then be shared by the Resend client and teacher service safely.
        this.enabled = enabled;
        this.provider = provider;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.replyTo = replyTo;
        this.apiBaseUrl = apiBaseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.sourcePath = sourcePath;
        this.statusMessage = statusMessage;
    }

    public static ResendConfig loadDefault() {
        // Mini-code guide:
        // 1. Reuse config/database.properties as the shared settings file.
        return load(Path.of("config", "database.properties"));
    }

    public static ResendConfig load(Path path) {
        // Mini-code guide:
        // 1. If the file does not exist, keep email delivery disabled with a setup message.
        // 2. Load the config keys for the Resend provider.
        // 3. Validate provider/api key/from address and return a safe immutable config.
        if (!Files.exists(path)) {
            return new ResendConfig(false, "resend", "", "", "QR Attend", "",
                    "https://api.resend.com", 20, path,
                    "Resend email is disabled. Add mail.* settings to " + path + ".");
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ex) {
            return new ResendConfig(false, "resend", "", "", "QR Attend", "",
                    "https://api.resend.com", 20, path,
                    "Could not read " + path + " for Resend settings.");
        }

        boolean enabled = Boolean.parseBoolean(properties.getProperty("mail.enabled", "false"));
        String provider = properties.getProperty("mail.provider", "resend").trim().toLowerCase();
        String apiKey = properties.getProperty("mail.apiKey", "").trim();
        String fromEmail = properties.getProperty("mail.fromEmail", "").trim();
        String fromName = properties.getProperty("mail.fromName", "QR Attend").trim();
        String replyTo = properties.getProperty("mail.replyTo", "").trim();
        String apiBaseUrl = properties.getProperty("mail.apiBaseUrl", "https://api.resend.com").trim();
        int timeoutSeconds = parseInt(properties.getProperty("mail.timeoutSeconds"), 20);

        String statusMessage;
        if (!enabled) {
            statusMessage = "Resend email delivery is disabled in " + path + ".";
        } else if (!"resend".equals(provider)) {
            enabled = false;
            statusMessage = "Only Resend is supported in this build. Set mail.provider=resend in " + path + ".";
        } else if (apiKey.isBlank() || fromEmail.isBlank()) {
            enabled = false;
            statusMessage = "Mail is enabled, but mail.apiKey or mail.fromEmail is missing in " + path + ".";
        } else {
            statusMessage = "Resend email delivery is enabled through " + path + ".";
        }

        return new ResendConfig(enabled, provider, apiKey, fromEmail, fromName,
                replyTo, apiBaseUrl, timeoutSeconds, path, statusMessage);
    }

    private static int parseInt(String value, int defaultValue) {
        // Mini-code guide:
        // 1. Parse timeout values safely and fall back on invalid text.
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean isEnabled() {
        // Mini-code guide:
        // 1. Return whether Resend can be used for outbound email.
        return enabled;
    }

    public String getProvider() {
        // Mini-code guide:
        // 1. Return the normalized provider id.
        return provider;
    }

    public String getApiKey() {
        // Mini-code guide:
        // 1. Return the Resend API key; do not print it in logs/UI.
        return apiKey;
    }

    public String getFromEmail() {
        // Mini-code guide:
        // 1. Return the verified sender address for Resend.
        return fromEmail;
    }

    public String getFromName() {
        // Mini-code guide:
        // 1. Return the display name shown on outgoing email.
        return fromName;
    }

    public String getReplyTo() {
        // Mini-code guide:
        // 1. Return the optional reply-to address.
        return replyTo;
    }

    public String getApiBaseUrl() {
        // Mini-code guide:
        // 1. Return the Resend API base URL.
        return apiBaseUrl;
    }

    public int getTimeoutSeconds() {
        // Mini-code guide:
        // 1. Return the HTTP timeout for email delivery requests.
        return timeoutSeconds;
    }

    public Path getSourcePath() {
        // Mini-code guide:
        // 1. Return the config file path that supplied these settings.
        return sourcePath;
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return a friendly explanation of whether Resend is ready.
        return statusMessage;
    }
}
