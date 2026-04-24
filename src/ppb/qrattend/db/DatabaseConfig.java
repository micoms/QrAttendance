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
        // Mini-code guide:
        // 1. Use config/database.properties as the default MariaDB/XAMPP settings file.
        // 2. Delegate to load(Path) so validation rules live in one method.
        return load(Path.of("config", "database.properties"));
    }

    public static DatabaseConfig load(Path path) {
        // Mini-code guide:
        // 1. If the file is missing, return a disabled config plus a message explaining how to create it.
        // 2. Load properties with java.util.Properties.
        // 3. Read db.enabled, db.driverClass, db.url, db.username, and db.password.
        // 4. Trim the values so accidental spaces do not break login.
        // 5. If required fields are missing, force enabled = false and explain the problem in statusMessage.
        // 6. Return the immutable DatabaseConfig for the rest of the app to reuse.
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
        // Mini-code guide:
        // 1. Return whether DB-backed login/features are allowed after config validation.
        return enabled;
    }

    public String getDriverClass() {
        // Mini-code guide:
        // 1. Return the JDBC driver class name that DatabaseManager should load.
        return driverClass;
    }

    public String getUrl() {
        // Mini-code guide:
        // 1. Return the JDBC URL for MariaDB/MySQL connection creation.
        return url;
    }

    public String getUsername() {
        // Mini-code guide:
        // 1. Return the DB username used by DriverManager.getConnection(...).
        return username;
    }

    public String getPassword() {
        // Mini-code guide:
        // 1. Return the DB password.
        // 2. Avoid logging this value anywhere in the UI or console.
        return password;
    }

    public Path getSourcePath() {
        // Mini-code guide:
        // 1. Return the config file path so setup/status messages can point to the right file.
        return sourcePath;
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return the last readiness/setup message for login and diagnostics.
        return statusMessage;
    }
}
