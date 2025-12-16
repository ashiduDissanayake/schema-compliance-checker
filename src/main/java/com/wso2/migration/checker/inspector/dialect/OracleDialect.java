package com.wso2.migration. checker.inspector.dialect;

import com.wso2.migration. checker.model.*;
import com. wso2.migration.checker. model.RoutineInfo. ParameterInfo;
import com. wso2.migration.checker. model.RoutineInfo. RoutineType;
import com. wso2.migration.checker. model.TriggerInfo.TriggerEvent;
import com. wso2.migration.checker. model.TriggerInfo.TriggerTiming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Oracle-specific dialect for schema extraction.
 * Handles PL/SQL procedures, functions, packages, triggers, and sequences.
 */
public class OracleDialect implements DatabaseDialect {

    private static final Logger LOG = LoggerFactory.getLogger(OracleDialect.class);

    private static final String ROUTINES_QUERY = """
        SELECT 
            OBJECT_NAME,
            OWNER,
            OBJECT_TYPE
        FROM ALL_OBJECTS
        WHERE OWNER = UPPER(?)
        AND OBJECT_TYPE IN ('PROCEDURE', 'FUNCTION', 'PACKAGE', 'PACKAGE BODY')
        ORDER BY OBJECT_NAME
        """;

    private static final String ROUTINE_SOURCE_QUERY = """
        SELECT TEXT
        FROM ALL_SOURCE
        WHERE OWNER = UPPER(?) AND NAME = ?  AND TYPE = ? 
        ORDER BY LINE
        """;

    private static final String ROUTINE_PARAMS_QUERY = """
        SELECT 
            ARGUMENT_NAME,
            DATA_TYPE,
            IN_OUT,
            POSITION
        FROM ALL_ARGUMENTS
        WHERE OWNER = UPPER(?) AND OBJECT_NAME = ?
        AND ARGUMENT_NAME IS NOT NULL
        ORDER BY POSITION
        """;

    private static final String TRIGGERS_QUERY = """
        SELECT 
            TRIGGER_NAME,
            TABLE_NAME,
            TRIGGER_TYPE,
            TRIGGERING_EVENT,
            TRIGGER_BODY,
            STATUS
        FROM ALL_TRIGGERS
        WHERE OWNER = UPPER(?)
        ORDER BY TRIGGER_NAME
        """;

    private static final String SEQUENCES_QUERY = """
        SELECT 
            SEQUENCE_NAME,
            SEQUENCE_OWNER,
            MIN_VALUE,
            MAX_VALUE,
            INCREMENT_BY,
            CYCLE_FLAG,
            CACHE_SIZE,
            LAST_NUMBER
        FROM ALL_SEQUENCES
        WHERE SEQUENCE_OWNER = UPPER(?)
        ORDER BY SEQUENCE_NAME
        """;

    private static final String VIEWS_QUERY = """
        SELECT 
            VIEW_NAME,
            OWNER,
            TEXT
        FROM ALL_VIEWS
        WHERE OWNER = UPPER(?)
        ORDER BY VIEW_NAME
        """;

    private static final String VIEW_COLUMNS_QUERY = """
        SELECT COLUMN_NAME
        FROM ALL_TAB_COLUMNS
        WHERE OWNER = UPPER(?) AND TABLE_NAME = ?
        ORDER BY COLUMN_ID
        """;

    @Override
    public List<RoutineInfo> extractRoutines(Connection connection, String schema) {
        List<RoutineInfo> routines = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(ROUTINES_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("OBJECT_NAME");
                    String owner = rs.getString("OWNER");
                    String typeStr = rs.getString("OBJECT_TYPE");

                    RoutineType type = parseRoutineType(typeStr);
                    String definition = getRoutineSource(connection, owner, name, typeStr);
                    List<ParameterInfo> params = extractRoutineParameters(connection, owner, name);

                    routines.add(new RoutineInfo(
                            name, owner, type, null, params, definition, "PL/SQL"
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract Oracle routines: {}", e.getMessage());
        }

        return routines;
    }

    private RoutineType parseRoutineType(String typeStr) {
        return switch (typeStr.toUpperCase()) {
            case "PROCEDURE" -> RoutineType. PROCEDURE;
            case "FUNCTION" -> RoutineType. FUNCTION;
            case "PACKAGE" -> RoutineType. PACKAGE;
            case "PACKAGE BODY" -> RoutineType.PACKAGE_BODY;
            default -> RoutineType. PROCEDURE;
        };
    }

    private String getRoutineSource(Connection connection, String owner, String name, String type) {
        StringBuilder source = new StringBuilder();

        try (PreparedStatement stmt = connection.prepareStatement(ROUTINE_SOURCE_QUERY)) {
            stmt.setString(1, owner);
            stmt.setString(2, name);
            stmt.setString(3, type);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    source.append(rs.getString("TEXT"));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to get source for {}.{}:  {}", owner, name, e. getMessage());
        }

        return source.toString();
    }

    private List<ParameterInfo> extractRoutineParameters(Connection connection, String owner, String routineName) {
        List<ParameterInfo> params = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(ROUTINE_PARAMS_QUERY)) {
            stmt.setString(1, owner);
            stmt.setString(2, routineName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String paramName = rs.getString("ARGUMENT_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    String inOut = rs.getString("IN_OUT");
                    int position = rs.getInt("POSITION");

                    ParameterInfo. ParameterMode mode = switch (inOut) {
                        case "OUT" -> ParameterInfo. ParameterMode.OUT;
                        case "IN/OUT" -> ParameterInfo. ParameterMode.INOUT;
                        default -> ParameterInfo.ParameterMode. IN;
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

            try (ResultSet rs = stmt. executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TRIGGER_NAME");
                    String tableName = rs.getString("TABLE_NAME");
                    String triggerType = rs.getString("TRIGGER_TYPE");
                    String eventStr = rs.getString("TRIGGERING_EVENT");
                    String body = rs.getString("TRIGGER_BODY");
                    String status = rs.getString("STATUS");

                    TriggerTiming timing = parseTriggerTiming(triggerType);
                    TriggerEvent event = parseTriggerEvent(eventStr);
                    boolean enabled = "ENABLED".equalsIgnoreCase(status);

                    triggers.add(new TriggerInfo(name, tableName, timing, event, body, enabled));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract Oracle triggers: {}", e.getMessage());
        }

        return triggers;
    }

    private TriggerTiming parseTriggerTiming(String triggerType) {
        if (triggerType == null) return TriggerTiming.AFTER;
        String upper = triggerType.toUpperCase();
        if (upper.contains("BEFORE")) return TriggerTiming. BEFORE;
        if (upper. contains("INSTEAD OF")) return TriggerTiming. INSTEAD_OF;
        return TriggerTiming.AFTER;
    }

    private TriggerEvent parseTriggerEvent(String eventStr) {
        if (eventStr == null) return TriggerEvent.INSERT;
        String upper = eventStr.toUpperCase();

        boolean hasInsert = upper.contains("INSERT");
        boolean hasUpdate = upper.contains("UPDATE");
        boolean hasDelete = upper.contains("DELETE");

        if (hasInsert && hasUpdate && hasDelete) return TriggerEvent.INSERT_UPDATE_DELETE;
        if (hasInsert && hasUpdate) return TriggerEvent.INSERT_UPDATE;
        if (hasInsert && hasDelete) return TriggerEvent.INSERT_DELETE;
        if (hasUpdate && hasDelete) return TriggerEvent.UPDATE_DELETE;
        if (hasInsert) return TriggerEvent.INSERT;
        if (hasUpdate) return TriggerEvent.UPDATE;
        if (hasDelete) return TriggerEvent.DELETE;

        return TriggerEvent.INSERT;
    }

    @Override
    public List<SequenceInfo> extractSequences(Connection connection, String schema) {
        List<SequenceInfo> sequences = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(SEQUENCES_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("SEQUENCE_NAME");
                    String owner = rs. getString("SEQUENCE_OWNER");
                    long minValue = rs.getLong("MIN_VALUE");
                    long maxValue = rs.getLong("MAX_VALUE");
                    long incrementBy = rs.getLong("INCREMENT_BY");
                    boolean cycling = "Y".equals(rs.getString("CYCLE_FLAG"));
                    int cacheSize = rs.getInt("CACHE_SIZE");

                    sequences.add(new SequenceInfo(
                            name, owner, 1, incrementBy, minValue, maxValue, cycling, cacheSize
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract Oracle sequences: {}", e.getMessage());
        }

        return sequences;
    }

    @Override
    public List<ViewInfo> extractViews(Connection connection, String schema) {
        List<ViewInfo> views = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(VIEWS_QUERY)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("VIEW_NAME");
                    String owner = rs.getString("OWNER");
                    String definition = rs.getString("TEXT");

                    List<String> columns = extractViewColumns(connection, owner, name);

                    views.add(new ViewInfo(name, owner, columns, definition, false));
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to extract Oracle views: {}", e. getMessage());
        }

        return views;
    }

    private List<String> extractViewColumns(Connection connection, String owner, String viewName) {
        List<String> columns = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(VIEW_COLUMNS_QUERY)) {
            stmt.setString(1, owner);
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
            return "CHECKER";
        }
    }
}