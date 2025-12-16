package com. wso2.migration.checker. inspector.dialect;

import com. wso2.migration.checker. model.*;
import com.wso2.migration.checker.model. RoutineInfo. ParameterInfo;
import com.wso2.migration.checker.model.RoutineInfo.RoutineType;
import com.wso2.migration.checker.model.TriggerInfo. TriggerEvent;
import com.wso2.migration.checker.model.TriggerInfo.TriggerTiming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util. ArrayList;
import java.util. List;

/**
 * MySQL-specific dialect for schema extraction.
 */
public class MySqlDialect implements DatabaseDialect {

    private static final Logger LOG = LoggerFactory.getLogger(MySqlDialect.class);

    private static final String ROUTINES_QUERY = """
        SELECT 
            ROUTINE_NAME,
            ROUTINE_SCHEMA,
            ROUTINE_TYPE,
            DATA_TYPE,
            ROUTINE_DEFINITION,
            EXTERNAL_LANGUAGE
        FROM INFORMATION_SCHEMA. ROUTINES
        WHERE ROUTINE_SCHEMA = ? 
        ORDER BY ROUTINE_NAME
        """;

    private static final String ROUTINE_PARAMS_QUERY = """
        SELECT 
            PARAMETER_NAME,
            DATA_TYPE,
            PARAMETER_MODE,
            ORDINAL_POSITION
        FROM INFORMATION_SCHEMA. PARAMETERS
        WHERE SPECIFIC_SCHEMA = ?  AND SPECIFIC_NAME = ?
        ORDER BY ORDINAL_POSITION
        """;

    private static final String TRIGGERS_QUERY = """
        SELECT 
            TRIGGER_NAME,
            EVENT_OBJECT_TABLE,
            ACTION_TIMING,
            EVENT_MANIPULATION,
            ACTION_STATEMENT
        FROM INFORMATION_SCHEMA. TRIGGERS
        WHERE TRIGGER_SCHEMA = ?
        ORDER BY TRIGGER_NAME
        """;

    private static final String VIEWS_QUERY = """
        SELECT 
            TABLE_NAME,
            VIEW_DEFINITION,
            IS_UPDATABLE
        FROM INFORMATION_SCHEMA.VIEWS
        WHERE TABLE_SCHEMA = ?
        ORDER BY TABLE_NAME
        """;

    private static final String VIEW_COLUMNS_QUERY = """
        SELECT COLUMN_NAME
        FROM INFORMATION_SCHEMA. COLUMNS
        WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
        ORDER BY ORDINAL_POSITION
        """;

    @Override
    public List<RoutineInfo> extractRoutines(Connection connection, String schema) {
        List<RoutineInfo> routines = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(ROUTINES_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("ROUTINE_NAME");
                    String routineSchema = rs.getString("ROUTINE_SCHEMA");
                    String typeStr = rs.getString("ROUTINE_TYPE");
                    String returnType = rs.getString("DATA_TYPE");
                    String definition = rs.getString("ROUTINE_DEFINITION");
                    String language = rs.getString("EXTERNAL_LANGUAGE");

                    RoutineType type = "PROCEDURE".equalsIgnoreCase(typeStr)
                            ? RoutineType.PROCEDURE
                            :  RoutineType.FUNCTION;

                    List<ParameterInfo> params = extractRoutineParameters(connection, schema, name);

                    routines.add(new RoutineInfo(
                            name, routineSchema, type, returnType, params, definition, language
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract MySQL routines:  {}", e.getMessage());
        }

        return routines;
    }

    private List<ParameterInfo> extractRoutineParameters(Connection connection, String schema, String routineName) {
        List<ParameterInfo> params = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(ROUTINE_PARAMS_QUERY)) {
            stmt.setString(1, schema);
            stmt.setString(2, routineName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String paramName = rs.getString("PARAMETER_NAME");
                    if (paramName == null) continue; // Skip return parameter

                    String dataType = rs.getString("DATA_TYPE");
                    String modeStr = rs.getString("PARAMETER_MODE");
                    int position = rs.getInt("ORDINAL_POSITION");

                    ParameterInfo.ParameterMode mode = switch (modeStr) {
                        case "OUT" -> ParameterInfo. ParameterMode.OUT;
                        case "INOUT" -> ParameterInfo.ParameterMode.INOUT;
                        default -> ParameterInfo.ParameterMode.IN;
                    };

                    params.add(new ParameterInfo(paramName, dataType, mode, position));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract parameters for routine {}: {}", routineName, e.getMessage());
        }

        return params;
    }

    @Override
    public List<TriggerInfo> extractTriggers(Connection connection, String schema) {
        List<TriggerInfo> triggers = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(TRIGGERS_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TRIGGER_NAME");
                    String tableName = rs.getString("EVENT_OBJECT_TABLE");
                    String timingStr = rs.getString("ACTION_TIMING");
                    String eventStr = rs.getString("EVENT_MANIPULATION");
                    String definition = rs.getString("ACTION_STATEMENT");

                    TriggerTiming timing = switch (timingStr. toUpperCase()) {
                        case "BEFORE" -> TriggerTiming.BEFORE;
                        case "AFTER" -> TriggerTiming.AFTER;
                        default -> TriggerTiming. AFTER;
                    };

                    TriggerEvent event = parseTriggerEvent(eventStr);

                    triggers.add(new TriggerInfo(name, tableName, timing, event, definition, true));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract MySQL triggers: {}", e. getMessage());
        }

        return triggers;
    }

    private TriggerEvent parseTriggerEvent(String eventStr) {
        return switch (eventStr.toUpperCase()) {
            case "INSERT" -> TriggerEvent.INSERT;
            case "UPDATE" -> TriggerEvent.UPDATE;
            case "DELETE" -> TriggerEvent.DELETE;
            default -> TriggerEvent.INSERT;
        };
    }

    @Override
    public List<SequenceInfo> extractSequences(Connection connection, String schema) {
        // MySQL doesn't have sequences in versions < 8.0, using AUTO_INCREMENT instead
        // For MySQL 8.0+, we could query INFORMATION_SCHEMA, but it's rarely used
        return List.of();
    }

    @Override
    public List<ViewInfo> extractViews(Connection connection, String schema) {
        List<ViewInfo> views = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(VIEWS_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String definition = rs.getString("VIEW_DEFINITION");
                    boolean updatable = "YES".equalsIgnoreCase(rs.getString("IS_UPDATABLE"));

                    List<String> columns = extractViewColumns(connection, schema, name);

                    views.add(new ViewInfo(name, schema, columns, definition, updatable));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract MySQL views: {}", e.getMessage());
        }

        return views;
    }

    private List<String> extractViewColumns(Connection connection, String schema, String viewName) {
        List<String> columns = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(VIEW_COLUMNS_QUERY)) {
            stmt.setString(1, schema);
            stmt.setString(2, viewName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract columns for view {}: {}", viewName, e.getMessage());
        }

        return columns;
    }

    @Override
    public String getDefaultSchema(Connection connection) {
        try {
            return connection.getCatalog();
        } catch (SQLException e) {
            return "compliance_check";
        }
    }
}