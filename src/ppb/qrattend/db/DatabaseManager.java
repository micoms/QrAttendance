package ppb.qrattend.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager {

    private final DatabaseConfig config;
    private final boolean driverAvailable;
    private final String driverStatus;

    public DatabaseManager(DatabaseConfig config) {
        // Mini-code guide:
        // 1. Keep the shared DatabaseConfig.
        // 2. Try to load the JDBC driver once during construction.
        // 3. Cache the driver availability/status so later UI checks stay cheap.
        this.config = config;
        DriverLoadResult driverLoadResult = loadDriver(config);
        this.driverAvailable = driverLoadResult.available;
        this.driverStatus = driverLoadResult.message;
    }

    public static DatabaseManager fromDefaultConfig() {
        // Mini-code guide:
        // 1. Load the default config file.
        // 2. Build a DatabaseManager around it.
        return new DatabaseManager(DatabaseConfig.loadDefault());
    }

    private DriverLoadResult loadDriver(DatabaseConfig config) {
        // Mini-code guide:
        // 1. Skip driver loading entirely when DB is disabled.
        // 2. Otherwise call Class.forName(driverClass) to register the JDBC driver.
        // 3. Cache a helpful message if the jar/class is missing.
        if (!config.isEnabled()) {
            return new DriverLoadResult(false, config.getStatusMessage());
        }
        try {
            Class.forName(config.getDriverClass());
            return new DriverLoadResult(true, config.getStatusMessage());
        } catch (ClassNotFoundException ex) {
            return new DriverLoadResult(false, "Sign-in is not ready yet. Please ask the admin.");
        }
    }

    public DatabaseConfig getConfig() {
        // Mini-code guide:
        // 1. Return the shared configuration object for callers that need file/status details.
        return config;
    }

    public boolean isReady() {
        // Mini-code guide:
        // 1. DB is ready only when config is enabled and the JDBC driver loaded successfully.
        return config.isEnabled() && driverAvailable;
    }

    public String getStatusMessage() {
        // Mini-code guide:
        // 1. Return the current connection-readiness message for the login UI and diagnostics.
        return driverStatus;
    }

    public Connection openConnection() throws SQLException {
        // Mini-code guide:
        // 1. Reject opening a connection when config is disabled.
        // 2. Reject when the driver is unavailable.
        // 3. Otherwise call DriverManager.getConnection(url, username, password).
        // 4. Let SQLException bubble so callers can decide whether to retry, fail login, or show a UI message.
        if (!config.isEnabled()) {
            throw new SQLException(config.getStatusMessage());
        }
        if (!isReady()) {
            throw new SQLException(driverStatus);
        }
        return DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
    }

    private static final class DriverLoadResult {

        private final boolean available;
        private final String message;

        private DriverLoadResult(boolean available, String message) {
            this.available = available;
            this.message = message;
        }
    }
}
