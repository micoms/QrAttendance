package ppb.qrattend.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DatabaseConfig {

    private final boolean enabled;
    private final String driverClass;
    private final String url;
    private final String username;
    private final String password;
    private final Path sourcePath;
    private final String statusMessage;

    private DatabaseConfig(boolean enabled, String driverClass, String url, String username, String password, Path sourcePath, String statusMessage) {
        this.enabled = enabled;
        this.driverClass = driverClass;
        this.url = url;
        this.username = username;
        this.password = password;
        this.sourcePath = sourcePath;
        this.statusMessage = statusMessage;
    }

    public static DatabaseConfig loadDefault() {
        return load(Path.of("config", "database.properties"));
    }

    public static DatabaseConfig load(Path path) {
        if (!Files.exists(path)) {
            return new DatabaseConfig(
                    false,
                    "com.mysql.cj.jdbc.Driver",
                    "",
                    "",
                    "",
                    path,
                    "Sign-in is not ready yet. Please ask the admin."
            );
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ex) {
            return new DatabaseConfig(
                    false,
                    "com.mysql.cj.jdbc.Driver",
                    "",
                    "",
                    "",
                    path,
                    "Sign-in is not ready yet. Please ask the admin."
            );
        }

        boolean enabled = Boolean.parseBoolean(properties.getProperty("db.enabled", "true"));
        String driverClass = properties.getProperty("db.driverClass", "com.mysql.cj.jdbc.Driver").trim();
        String url = properties.getProperty("db.url", "").trim();
        String username = properties.getProperty("db.username", "").trim();
        String password = properties.getProperty("db.password", "").trim();

        String statusMessage;
        if (!enabled) {
            statusMessage = "Sign-in is not ready yet. Please ask the admin.";
        } else if (url.isBlank() || username.isBlank()) {
            statusMessage = "Sign-in is not ready yet. Please ask the admin.";
            enabled = false;
        } else {
            statusMessage = "Sign-in is ready.";
        }

        return new DatabaseConfig(enabled, driverClass, url, username, password, path, statusMessage);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
