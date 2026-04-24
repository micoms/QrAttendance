package ppb.qrattend.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AiConfig {

    private final boolean enabled;
    private final String provider;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final int maxPromptChars;
    private final Path sourcePath;
    private final String statusMessage;

    private AiConfig(boolean enabled, String provider, String apiKey, String model,
            int timeoutSeconds, int maxPromptChars, Path sourcePath, String statusMessage) {
        this.enabled = enabled;
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.maxPromptChars = maxPromptChars;
        this.sourcePath = sourcePath;
        this.statusMessage = statusMessage;
    }

    public static AiConfig loadDefault() {
        // Mini-code guide:
        // 1. Use config/database.properties as the shared source for both DB and AI settings.
        // 2. Delegate to load(Path) so all validation rules stay in one place.
        return load(Path.of("config", "database.properties"));
    }

    public static AiConfig load(Path path) {
        // Mini-code guide:
        // 1. If the properties file does not exist, return a disabled config with a friendly setup message.
        // 2. Load the properties file with java.util.Properties.
        // 3. Read ai.enabled, ai.provider, ai.apiKey, ai.model, ai.timeoutSeconds, and ai.maxPromptChars.
        // 4. Normalize provider/model values and fall back to safe defaults.
        // 5. If provider is not gemini or apiKey is blank, force enabled = false and explain why in statusMessage.
        // 6. Return an immutable AiConfig object that the UI and AiClient can share safely.
        if (!Files.exists(path)) {
            return new AiConfig(false, "gemini", "", "gemini-2.5-flash", 20, 12000, path,
                    "Ask AI is not ready right now.");
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ex) {
            return new AiConfig(false, "gemini", "", "gemini-2.5-flash", 20, 12000, path,
                    "Ask AI is not ready right now.");
        }

        boolean enabled = Boolean.parseBoolean(properties.getProperty("ai.enabled", "false"));
        String provider = properties.getProperty("ai.provider", "gemini").trim().toLowerCase();
        String apiKey = properties.getProperty("ai.apiKey", "").trim();
        String model = properties.getProperty("ai.model", "gemini-2.5-flash").trim();
        int timeoutSeconds = parseInt(properties.getProperty("ai.timeoutSeconds"), 20);
        int maxPromptChars = parseInt(properties.getProperty("ai.maxPromptChars"), 12000);

        String statusMessage;
        if (!enabled) {
            statusMessage = "Ask AI is not ready right now.";
        } else if (!"gemini".equals(provider)) {
            enabled = false;
            statusMessage = "Ask AI is not ready right now.";
        } else if (apiKey.isBlank()) {
            enabled = false;
            statusMessage = "Ask AI is not ready right now.";
        } else {
            statusMessage = "Ask AI is ready.";
        }

        return new AiConfig(enabled, provider, apiKey, model, timeoutSeconds, maxPromptChars, path, statusMessage);
    }

    private static int parseInt(String value, int defaultValue) {
        // Mini-code guide:
        // 1. Return the fallback when the property is missing or blank.
        // 2. Parse the trimmed text into an int.
        // 3. If parsing fails, keep the default so startup never crashes on bad config text.
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
        // 1. Return whether AI generation is allowed after validation.
        return enabled;
    }

    public String getProvider() {
        // Mini-code guide:
        // 1. Return the normalized provider id, currently expected to be "gemini".
        return provider;
    }

    public String getApiKey() {
        // Mini-code guide:
        // 1. Return the configured external AI key.
        // 2. Never log this value in UI or debug output.
        return apiKey;
    }

    public String getModel() {
        // Mini-code guide:
        // 1. Return the configured Gemini model name used for requests.
        return model;
    }

    public int getTimeoutSeconds() {
        // Mini-code guide:
        // 1. Return the network timeout used by the HTTP client.
        return timeoutSeconds;
    }

    public int getMaxPromptChars() {
        // Mini-code guide:
        // 1. Return the maximum prompt length allowed before truncation.
        return maxPromptChars;
    }

    public Path getSourcePath() {
        // Mini-code guide:
        // 1. Return the config file path so status messages can point users to the right file.
        return sourcePath;
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return the latest readiness message for the UI.
        return statusMessage;
    }
}
