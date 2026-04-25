package ppb.qrattend.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.db.DatabaseManager;
import ppb.qrattend.model.CoreModels.Section;

public final class SectionService {

    private static final String SELECT_ALL_SQL = """
            SELECT section_id, section_name
            FROM sections
            ORDER BY section_name ASC
            """;

    private static final String INSERT_SQL = """
            INSERT INTO sections (section_name)
            VALUES (?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE sections SET section_name = ? WHERE section_id = ?
            """;

    private static final String CHECK_STUDENTS_SQL = """
            SELECT 1 FROM students WHERE section_id = ? LIMIT 1
            """;

    private static final String CHECK_SCHEDULES_SQL = """
            SELECT 1 FROM schedules WHERE section_id = ? AND is_active = 1 LIMIT 1
            """;

    private static final String DELETE_SQL = """
            DELETE FROM sections WHERE section_id = ?
            """;

    private final DatabaseManager databaseManager;

    public SectionService() {
        this(DatabaseManager.fromDefaultConfig());
    }

    public SectionService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ServiceResult<List<Section>> getSections() {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        List<Section> sections = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                sections.add(new Section(resultSet.getInt("section_id"), resultSet.getString("section_name")));
            }
            return ServiceResult.success("Loaded sections.", sections);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not load sections.");
        }
    }

    public ServiceResult<Section> addSection(String sectionName) {
        String cleanName = safe(sectionName);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the section name.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, cleanName);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    return ServiceResult.failure("Could not save the section.");
                }
                return ServiceResult.success("Section saved.", new Section(keys.getInt(1), cleanName));
            }
        } catch (SQLException ex) {
            return ServiceResult.failure("That section already exists or could not be saved.");
        }
    }

    public ServiceResult<Section> renameSection(int sectionId, String newName) {
        String cleanName = safe(newName);
        if (cleanName.isBlank()) {
            return ServiceResult.failure("Enter the section name.");
        }
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setString(1, cleanName);
            statement.setInt(2, sectionId);
            statement.executeUpdate();
            return ServiceResult.success("Section renamed.", new Section(sectionId, cleanName));
        } catch (SQLException ex) {
            return ServiceResult.failure("That section name already exists or could not be saved.");
        }
    }

    public ServiceResult<Void> deleteSection(int sectionId) {
        if (!databaseManager.isReady()) {
            return ServiceResult.failure(databaseManager.getStatusMessage());
        }
        try (Connection connection = databaseManager.openConnection()) {
            try (PreparedStatement checkStudents = connection.prepareStatement(CHECK_STUDENTS_SQL)) {
                checkStudents.setInt(1, sectionId);
                try (ResultSet rs = checkStudents.executeQuery()) {
                    if (rs.next()) {
                        return ServiceResult.failure("This section is still used by students or schedules.");
                    }
                }
            }
            try (PreparedStatement checkSchedules = connection.prepareStatement(CHECK_SCHEDULES_SQL)) {
                checkSchedules.setInt(1, sectionId);
                try (ResultSet rs = checkSchedules.executeQuery()) {
                    if (rs.next()) {
                        return ServiceResult.failure("This section is still used by students or schedules.");
                    }
                }
            }
            try (PreparedStatement deleteStmt = connection.prepareStatement(DELETE_SQL)) {
                deleteStmt.setInt(1, sectionId);
                deleteStmt.executeUpdate();
            }
            return ServiceResult.success("Section deleted.", null);
        } catch (SQLException ex) {
            return ServiceResult.failure("Could not delete the section.");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
