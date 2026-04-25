package ppb.qrattend.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager {

    private final DatabaseConfig config;
    private final boolean driverAvailable;
    private final String driverStatus;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
        DriverLoadResult driverLoadResult = loadDriver(config);
        this.driverAvailable = driverLoadResult.available;
        this.driverStatus = driverLoadResult.message;
    }

    public static DatabaseManager fromDefaultConfig() {
        return new DatabaseManager(DatabaseConfig.loadDefault());
    }

    private DriverLoadResult loadDriver(DatabaseConfig config) {
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
        return config;
    }

    public boolean isReady() {
        return config.isEnabled() && driverAvailable;
    }

    public String getStatusMessage() {
        return driverStatus;
    }

    public Connection openConnection() throws SQLException {
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
