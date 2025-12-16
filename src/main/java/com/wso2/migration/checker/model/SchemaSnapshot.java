package com.wso2.migration.checker.model;

import java.time. Instant;
import java.util. List;
import java.util. Map;
import java.util. stream.Collectors;

/**
 * Complete snapshot of a database schema at a point in time.
 */
public record SchemaSnapshot(
        String databaseName,
        String databaseType,
        String schemaName,
        Instant capturedAt,
        List<TableInfo> tables,
        List<ViewInfo> views,
        List<RoutineInfo> routines,
        List<TriggerInfo> triggers,
        List<SequenceInfo> sequences,
        Map<String, String> metadata
) {
    /**
     * Creates a lookup map for tables by name.
     */
    public Map<String, TableInfo> tableMap() {
        return tables.stream()
                .collect(Collectors.toMap(
                        t -> t.name().toUpperCase(),
                        t -> t,
                        (a, b) -> a
                ));
    }

    /**
     * Creates a lookup map for routines by name.
     */
    public Map<String, RoutineInfo> routineMap() {
        return routines.stream()
                .collect(Collectors. toMap(
                        r -> r.name().toUpperCase(),
                        r -> r,
                        (a, b) -> a
                ));
    }

    /**
     * Creates a lookup map for triggers by name.
     */
    public Map<String, TriggerInfo> triggerMap() {
        return triggers.stream()
                .collect(Collectors.toMap(
                        t -> t. name().toUpperCase(),
                        t -> t,
                        (a, b) -> a
                ));
    }

    /**
     * Creates a lookup map for sequences by name.
     */
    public Map<String, SequenceInfo> sequenceMap() {
        return sequences.stream()
                .collect(Collectors.toMap(
                        s -> s.name().toUpperCase(),
                        s -> s,
                        (a, b) -> a
                ));
    }

    /**
     * Summary statistics for the snapshot.
     */
    public String summary() {
        return String. format(
                "Database: %s (%s) | Tables: %d | Views: %d | Routines:  %d | Triggers: %d | Sequences: %d",
                databaseName, databaseType,
                tables.size(), views.size(), routines.size(), triggers.size(), sequences.size()
        );
    }
}