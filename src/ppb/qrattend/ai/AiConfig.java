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
        return load(Path.of("config", "database.properties"));
    }

    public static AiConfig load(Path path) {
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
        return enabled;
    }

    public String getProvider() {
        return provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
