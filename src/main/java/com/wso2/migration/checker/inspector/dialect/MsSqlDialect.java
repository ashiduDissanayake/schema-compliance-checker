package com. wso2.migration.checker. inspector.dialect;

import com. wso2.migration.checker. model.*;
import com.wso2.migration.checker.model. RoutineInfo.ParameterInfo;
import com.wso2.migration.checker.model. RoutineInfo.RoutineType;
import com.wso2.migration.checker.model. TriggerInfo.TriggerEvent;
import com.wso2.migration.checker.model. TriggerInfo.TriggerTiming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Microsoft SQL Server-specific dialect for schema extraction.
 */
public class MsSqlDialect implements DatabaseDialect {

    private static final Logger LOG = LoggerFactory.getLogger(MsSqlDialect.class);

    private static final String ROUTINES_QUERY = """
        SELECT 
            r. ROUTINE_NAME,
            r.ROUTINE_SCHEMA,
            r.ROUTINE_TYPE,
            r.DATA_TYPE,
            m.definition
        FROM INFORMATION_SCHEMA. ROUTINES r
        LEFT JOIN sys.sql_modules m ON OBJECT_ID(r.ROUTINE_SCHEMA + '.' + r.ROUTINE_NAME) = m.object_id
        WHERE r.ROUTINE_SCHEMA = ? 
        ORDER BY r.ROUTINE_NAME
        """;

    private static final String ROUTINE_PARAMS_QUERY = """
        SELECT 
            PARAMETER_NAME,
            DATA_TYPE,
            PARAMETER_MODE,
            ORDINAL_POSITION
        FROM INFORMATION_SCHEMA. PARAMETERS
        WHERE SPECIFIC_SCHEMA = ? AND SPECIFIC_NAME = ?
        ORDER BY ORDINAL_POSITION
        """;

    private static final String TRIGGERS_QUERY = """
        SELECT 
            t.name AS TRIGGER_NAME,
            OBJECT_NAME(t.parent_id) AS TABLE_NAME,
            CASE WHEN t.is_instead_of_trigger = 1 THEN 'INSTEAD OF'
                 ELSE 'AFTER' END AS TRIGGER_TIMING,
            te.type_desc AS TRIGGER_EVENT,
            m.definition AS TRIGGER_BODY,
            CASE WHEN t. is_disabled = 0 THEN 'ENABLED' ELSE 'DISABLED' END AS STATUS
        FROM sys.triggers t
        JOIN sys. trigger_events te ON t.object_id = te.object_id
        LEFT JOIN sys.sql_modules m ON t.object_id = m.object_id
        WHERE SCHEMA_NAME(SCHEMA_ID(OBJECT_SCHEMA_NAME(t.parent_id))) = ?
        ORDER BY t.name
        """;

    private static final String SEQUENCES_QUERY = """
        SELECT 
            s.name AS SEQUENCE_NAME,
            SCHEMA_NAME(s.schema_id) AS SCHEMA_NAME,
            s.start_value,
            s.increment,
            s.minimum_value,
            s.maximum_value,
            s.is_cycling,
            s.cache_size
        FROM sys.sequences s
        WHERE SCHEMA_NAME(s.schema_id) = ?
        ORDER BY s.name
        """;

    private static final String VIEWS_QUERY = """
        SELECT 
            v.TABLE_NAME,
            v.TABLE_SCHEMA,
            v. VIEW_DEFINITION,
            v.IS_UPDATABLE
        FROM INFORMATION_SCHEMA. VIEWS v
        WHERE v.TABLE_SCHEMA = ? 
        ORDER BY v.TABLE_NAME
        """;

    private static final String VIEW_COLUMNS_QUERY = """
        SELECT COLUMN_NAME
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
        ORDER BY ORDINAL_POSITION
        """;

    @Override
    public List<RoutineInfo> extractRoutines(Connection connection, String schema) {
        List<RoutineInfo> routines = new ArrayList<>();

        try (PreparedStatement stmt = connection. prepareStatement(ROUTINES_QUERY)) {
            stmt. setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs. getString("ROUTINE_NAME");
                    String routineSchema = rs.getString("ROUTINE_SCHEMA");
                    String typeStr = rs.getString("ROUTINE_TYPE");
                    String returnType = rs.getString("DATA_TYPE");
                    String definition = rs. getString("definition");

                    RoutineType type = "PROCEDURE".equalsIgnoreCase(typeStr)
                            ? RoutineType.PROCEDURE
                            :  RoutineType. FUNCTION;

                    List<ParameterInfo> params = extractRoutineParameters(connection, schema, name);

                    routines.add(new RoutineInfo(
                            name, routineSchema, type, returnType, params, definition, "T-SQL"
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract MSSQL routines: {}", e.getMessage());
        }

        return routines;
    }

    private List<ParameterInfo> extractRoutineParameters(Connection connection, String schema, String routineName) {
        List<ParameterInfo> params = new ArrayList<>();

        try (PreparedStatement stmt = connection. prepareStatement(ROUTINE_PARAMS_QUERY)) {
            stmt.setString(1, schema);
            stmt.setString(2, routineName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String paramName = rs. getString("PARAMETER_NAME");
                    if (paramName == null || paramName.isEmpty()) continue;

                    // Remove @ prefix from parameter names
                    if (paramName.startsWith("@")) {
                        paramName = paramName.substring(1);
                    }

                    String dataType = rs.getString("DATA_TYPE");
                    String modeStr = rs.getString("PARAMETER_MODE");
                    int position = rs.getInt("ORDINAL_POSITION");

                    ParameterInfo. ParameterMode mode = switch (modeStr != null ? modeStr.toUpperCase() : "IN") {
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
            stmt. setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs. getString("TRIGGER_NAME");
                    String tableName = rs.getString("TABLE_NAME");
                    String timingStr = rs.getString("TRIGGER_TIMING");
                    String eventStr = rs.getString("TRIGGER_EVENT");
                    String body = rs.getString("TRIGGER_BODY");
                    String status = rs.getString("STATUS");

                    TriggerTiming timing = parseTriggerTiming(timingStr);
                    TriggerEvent event = parseTriggerEvent(eventStr);
                    boolean enabled = "ENABLED".equalsIgnoreCase(status);

                    triggers.add(new TriggerInfo(name, tableName, timing, event, body, enabled));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract MSSQL triggers: {}", e.getMessage());
        }

        return triggers;
    }

    private TriggerTiming parseTriggerTiming(String timingStr) {
        if (timingStr == null) return TriggerTiming. AFTER;
        return switch (timingStr.toUpperCase()) {
            case "INSTEAD OF" -> TriggerTiming.INSTEAD_OF;
            case "BEFORE" -> TriggerTiming. BEFORE;
            default -> TriggerTiming. AFTER;
        };
    }

    private TriggerEvent parseTriggerEvent(String eventStr) {
        if (eventStr == null) return TriggerEvent.INSERT;
        return switch (eventStr.toUpperCase()) {
            case "INSERT" -> TriggerEvent.INSERT;
            case "UPDATE" -> TriggerEvent. UPDATE;
            case "DELETE" -> TriggerEvent.DELETE;
            default -> TriggerEvent.INSERT;
        };
    }

    @Override
    public List<SequenceInfo> extractSequences(Connection connection, String schema) {
        List<SequenceInfo> sequences = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(SEQUENCES_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("SEQUENCE_NAME");
                    String seqSchema = rs.getString("SCHEMA_NAME");
                    long startValue = rs.getLong("start_value");
                    long increment = rs.getLong("increment");
                    long minValue = rs.getLong("minimum_value");
                    long maxValue = rs.getLong("maximum_value");
                    boolean cycling = rs.getBoolean("is_cycling");
                    int cacheSize = rs.getInt("cache_size");

                    sequences.add(new SequenceInfo(
                            name, seqSchema, startValue, increment, minValue, maxValue, cycling, cacheSize
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract MSSQL sequences: {}", e.getMessage());
        }

        return sequences;
    }

    @Override
    public List<ViewInfo> extractViews(Connection connection, String schema) {
        List<ViewInfo> views = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(VIEWS_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt. executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String viewSchema = rs.getString("TABLE_SCHEMA");
                    String definition = rs.getString("VIEW_DEFINITION");
                    boolean updatable = "YES".equalsIgnoreCase(rs.getString("IS_UPDATABLE"));

                    List<String> columns = extractViewColumns(connection, schema, name);

                    views.add(new ViewInfo(name, viewSchema, columns, definition, updatable));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract MSSQL views: {}", e.getMessage());
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
            return connection.getSchema();
        } catch (SQLException e) {
            return "dbo";
        }
    }
}