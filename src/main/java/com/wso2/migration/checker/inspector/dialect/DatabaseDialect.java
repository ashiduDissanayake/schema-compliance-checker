package com. wso2.migration.checker. inspector.dialect;

import com.wso2.migration. checker.model.*;

import java.sql. Connection;
import java.util.List;

/**
 * Database-specific dialect for extracting schema objects.
 * Each database has different system catalogs and syntax for stored logic.
 */
public interface DatabaseDialect {

    /**
     * Extracts all stored procedures and functions.
     */
    List<RoutineInfo> extractRoutines(Connection connection, String schema);

    /**
     * Extracts all triggers.
     */
    List<TriggerInfo> extractTriggers(Connection connection, String schema);

    /**
     * Extracts all sequences (if supported).
     */
    List<SequenceInfo> extractSequences(Connection connection, String schema);

    /**
     * Extracts all views with their definitions.
     */
    List<ViewInfo> extractViews(Connection connection, String schema);

    /**
     * Gets the default schema name for this database type.
     */
    String getDefaultSchema(Connection connection);

    /**
     * Normalizes object names according to database conventions.
     */
    default String normalizeObjectName(String name) {
        return name != null ? name.toUpperCase().trim() : null;
    }
}