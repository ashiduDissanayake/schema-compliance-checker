package com.wso2.migration. checker.core;

import com. wso2.migration.checker. model.*;
import com.wso2.migration.checker.report.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Core engine for detecting schema differences between standard and user snapshots.
 */
public class DiffEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DiffEngine.class);

    /**
     * Compares two schema snapshots and generates a compliance report.
     */
    public ComplianceReport compare(SchemaSnapshot standard, SchemaSnapshot user) {
        LOG.info("⚖️  Starting schema comparison...");

        ComplianceReport report = new ComplianceReport(standard, user);

        // Compare all schema objects
        report.addAllDrifts(compareTables(standard, user));
        report.addAllDrifts(compareRoutines(standard, user));
        report.addAllDrifts(compareTriggers(standard, user));
        report.addAllDrifts(compareSequences(standard, user));
        report.addAllDrifts(compareViews(standard, user));

        report.calculateSummary();

        LOG.info("   ✓ Comparison complete.  Found {} drift items", report.getDriftItems().size());

        return report;
    }

    /**
     * Compares tables between standard and user schemas.
     */
    private List<DriftItem> compareTables(SchemaSnapshot standard, SchemaSnapshot user) {
        List<DriftItem> drifts = new ArrayList<>();

        Map<String, TableInfo> stdTables = standard.tableMap();
        Map<String, TableInfo> userTables = user. tableMap();

        // Find missing tables in user schema
        for (String tableName : stdTables.keySet()) {
            if (!userTables.containsKey(tableName)) {
                drifts.add(DriftItem.missing("Tables", "Table", tableName, DriftSeverity.CRITICAL));
            } else {
                // Table exists - compare structure
                drifts.addAll(compareTableStructure(stdTables.get(tableName), userTables.get(tableName)));
            }
        }

        // Find extra tables in user schema
        for (String tableName : userTables. keySet()) {
            if (! stdTables.containsKey(tableName)) {
                drifts.add(DriftItem. extra("Tables", "Table", tableName, DriftSeverity. LOW));
            }
        }

        return drifts;
    }

    /**
     * Compares structure of two tables.
     */
    private List<DriftItem> compareTableStructure(TableInfo stdTable, TableInfo userTable) {
        List<DriftItem> drifts = new ArrayList<>();
        String tableName = stdTable.name();

        Map<String, ColumnInfo> stdColumns = stdTable.columnMap();
        Map<String, ColumnInfo> userColumns = userTable.columnMap();

        // Compare columns
        for (String colName : stdColumns.keySet()) {
            ColumnInfo stdCol = stdColumns. get(colName);
            ColumnInfo userCol = userColumns. get(colName);

            if (userCol == null) {
                drifts.add(DriftItem.missing("Columns", "Column",
                        tableName + "." + colName, DriftSeverity.CRITICAL));
            } else {
                // Compare column properties
                drifts.addAll(compareColumnProperties(tableName, stdCol, userCol));
            }
        }

        // Find extra columns
        for (String colName : userColumns.keySet()) {
            if (!stdColumns.containsKey(colName)) {
                drifts.add(DriftItem.extra("Columns", "Column",
                        tableName + "." + colName, DriftSeverity. MEDIUM));
            }
        }

        // Compare indexes
        drifts.addAll(compareIndexes(tableName, stdTable.indexes(), userTable.indexes()));

        // Compare constraints
        drifts.addAll(compareConstraints(tableName, stdTable.constraints(), userTable.constraints()));

        return drifts;
    }

    /**
     * Compares properties of two columns.
     */
    private List<DriftItem> compareColumnProperties(String tableName, ColumnInfo stdCol, ColumnInfo userCol) {
        List<DriftItem> drifts = new ArrayList<>();
        String colPath = tableName + "." + stdCol.name();

        // Data type comparison (normalized)
        if (!stdCol. normalizedSignature().equals(userCol.normalizedSignature())) {
            // Check specific differences
            if (! normalizeDataType(stdCol.dataType()).equals(normalizeDataType(userCol.dataType()))) {
                drifts.add(DriftItem.modified("Columns", "Column Data Type", colPath,
                        DriftSeverity.HIGH, stdCol.dataType(), userCol.dataType(),
                        String.format("Data type mismatch:  expected '%s' but found '%s'",
                                stdCol.dataType(), userCol.dataType())));
            }

            // Nullability
            if (stdCol.nullable() != userCol.nullable()) {
                drifts.add(DriftItem.modified("Columns", "Column Nullability", colPath,
                        DriftSeverity. MEDIUM,
                        stdCol.nullable() ? "NULLABLE" : "NOT NULL",
                        userCol.nullable() ? "NULLABLE" : "NOT NULL",
                        "Nullability constraint differs"));
            }

            // Primary key status
            if (stdCol.isPrimaryKey() != userCol.isPrimaryKey()) {
                drifts.add(DriftItem. modified("Columns", "Primary Key", colPath,
                        DriftSeverity.CRITICAL,
                        stdCol.isPrimaryKey() ? "PK" : "Not PK",
                        userCol.isPrimaryKey() ? "PK" : "Not PK",
                        "Primary key status differs"));
            }

            // Size difference (only for variable types)
            if (stdCol. size() != userCol.size() && stdCol.size() > 0) {
                DriftSeverity severity = userCol.size() < stdCol.size()
                        ? DriftSeverity.HIGH : DriftSeverity.LOW;
                drifts.add(DriftItem.modified("Columns", "Column Size", colPath,
                        severity, String.valueOf(stdCol.size()), String.valueOf(userCol. size()),
                        String.format("Column size:  expected %d but found %d", stdCol.size(), userCol.size())));
            }
        }

        return drifts;
    }

    private String normalizeDataType(String type) {
        if (type == null) return "UNKNOWN";
        String normalized = type.toUpperCase().trim();
        return switch (normalized) {
            case "INT", "INTEGER", "INT4" -> "INTEGER";
            case "BIGINT", "INT8" -> "BIGINT";
            case "VARCHAR2" -> "VARCHAR";
            case "NUMBER" -> "NUMERIC";
            case "DATETIME2", "TIMESTAMP" -> "TIMESTAMP";
            default -> normalized;
        };
    }

    /**
     * Compares indexes between standard and user tables.
     */
    private List<DriftItem> compareIndexes(String tableName, List<IndexInfo> stdIndexes, List<IndexInfo> userIndexes) {
        List<DriftItem> drifts = new ArrayList<>();

        Map<String, IndexInfo> stdMap = stdIndexes.stream()
                .collect(java.util.stream.Collectors. toMap(
                        IndexInfo::normalizedSignature, i -> i, (a, b) -> a));

        Map<String, IndexInfo> userMap = userIndexes.stream()
                .collect(java. util.stream.Collectors.toMap(
                        IndexInfo::normalizedSignature, i -> i, (a, b) -> a));

        for (String sig : stdMap.keySet()) {
            if (!userMap.containsKey(sig)) {
                IndexInfo idx = stdMap.get(sig);
                drifts.add(DriftItem.missing("Indexes", "Index",
                        tableName + "." + idx.name(), DriftSeverity.MEDIUM));
            }
        }

        for (String sig : userMap.keySet()) {
            if (!stdMap. containsKey(sig)) {
                IndexInfo idx = userMap. get(sig);
                drifts.add(DriftItem. extra("Indexes", "Index",
                        tableName + "." + idx.name(), DriftSeverity.LOW));
            }
        }

        return drifts;
    }

    /**
     * Compares constraints between standard and user tables.
     */
    private List<DriftItem> compareConstraints(String tableName,
                                               List<ConstraintInfo> stdConstraints,
                                               List<ConstraintInfo> userConstraints) {
        List<DriftItem> drifts = new ArrayList<>();

        Map<String, ConstraintInfo> stdMap = stdConstraints.stream()
                .collect(java. util.stream.Collectors.toMap(
                        ConstraintInfo::normalizedSignature, c -> c, (a, b) -> a));

        Map<String, ConstraintInfo> userMap = userConstraints.stream()
                .collect(java. util.stream.Collectors.toMap(
                        ConstraintInfo::normalizedSignature, c -> c, (a, b) -> a));

        for (String sig : stdMap.keySet()) {
            if (!userMap.containsKey(sig)) {
                ConstraintInfo con = stdMap.get(sig);
                DriftSeverity severity = con. type() == ConstraintInfo. ConstraintType.FOREIGN_KEY
                        ? DriftSeverity.HIGH : DriftSeverity. CRITICAL;
                drifts.add(DriftItem.missing("Constraints", con.type().name(),
                        tableName + "." + con.name(), severity));
            }
        }

        return drifts;
    }

    /**
     * Compares stored routines (procedures, functions, packages).
     */
    private List<DriftItem> compareRoutines(SchemaSnapshot standard, SchemaSnapshot user) {
        List<DriftItem> drifts = new ArrayList<>();

        Map<String, RoutineInfo> stdRoutines = standard.routineMap();
        Map<String, RoutineInfo> userRoutines = user. routineMap();

        for (String name : stdRoutines.keySet()) {
            RoutineInfo stdRoutine = stdRoutines.get(name);
            RoutineInfo userRoutine = userRoutines. get(name);

            if (userRoutine == null) {
                drifts.add(DriftItem.missing("Routines", stdRoutine.type().name(),
                        name, DriftSeverity.CRITICAL));
            } else {
                // Compare signature
                if (!stdRoutine. normalizedSignature().equals(userRoutine.normalizedSignature())) {
                    drifts. add(DriftItem.modified("Routines", stdRoutine. type().name(), name,
                            DriftSeverity.HIGH,
                            stdRoutine.normalizedSignature(),
                            userRoutine.normalizedSignature(),
                            "Routine signature differs (parameters or return type)"));
                }

                // Compare definition (if available)
                if (stdRoutine.definition() != null && userRoutine.definition() != null) {
                    if (!stdRoutine.normalizedDefinition().equals(userRoutine.normalizedDefinition())) {
                        drifts. add(DriftItem.modified("Routines", stdRoutine. type().name() + " Body", name,
                                DriftSeverity. MEDIUM,
                                "See standard definition",
                                "See user definition",
                                "Routine implementation differs"));
                    }
                }
            }
        }

        for (String name : userRoutines. keySet()) {
            if (! stdRoutines.containsKey(name)) {
                RoutineInfo routine = userRoutines.get(name);
                drifts.add(DriftItem.extra("Routines", routine.type().name(),
                        name, DriftSeverity.LOW));
            }
        }

        return drifts;
    }

    /**
     * Compares triggers.
     */
    private List<DriftItem> compareTriggers(SchemaSnapshot standard, SchemaSnapshot user) {
        List<DriftItem> drifts = new ArrayList<>();

        Map<String, TriggerInfo> stdTriggers = standard.triggerMap();
        Map<String, TriggerInfo> userTriggers = user.triggerMap();

        for (String name : stdTriggers.keySet()) {
            TriggerInfo stdTrigger = stdTriggers.get(name);
            TriggerInfo userTrigger = userTriggers.get(name);

            if (userTrigger == null) {
                drifts.add(DriftItem.missing("Triggers", "Trigger", name, DriftSeverity. HIGH));
            } else {
                if (!stdTrigger.normalizedSignature().equals(userTrigger.normalizedSignature())) {
                    drifts. add(DriftItem.modified("Triggers", "Trigger", name,
                            DriftSeverity.HIGH,
                            stdTrigger. normalizedSignature(),
                            userTrigger.normalizedSignature(),
                            "Trigger configuration differs (timing or event)"));
                }

                if (! stdTrigger.normalizedDefinition().equals(userTrigger.normalizedDefinition())) {
                    drifts.add(DriftItem.modified("Triggers", "Trigger Body", name,
                            DriftSeverity.MEDIUM,
                            "See standard definition",
                            "See user definition",
                            "Trigger implementation differs"));
                }
            }
        }

        for (String name :  userTriggers.keySet()) {
            if (!stdTriggers.containsKey(name)) {
                drifts.add(DriftItem.extra("Triggers", "Trigger", name, DriftSeverity.LOW));
            }
        }

        return drifts;
    }

    /**
     * Compares sequences.
     */
    private List<DriftItem> compareSequences(SchemaSnapshot standard, SchemaSnapshot user) {
        List<DriftItem> drifts = new ArrayList<>();

        Map<String, SequenceInfo> stdSequences = standard.sequenceMap();
        Map<String, SequenceInfo> userSequences = user.sequenceMap();

        for (String name : stdSequences.keySet()) {
            SequenceInfo stdSeq = stdSequences.get(name);

            if (! userSequences.containsKey(name)) {
                drifts.add(DriftItem.missing("Sequences", "Sequence", name, DriftSeverity. HIGH));
            } else {
                SequenceInfo userSeq = userSequences.get(name);
                if (!stdSeq.normalizedSignature().equals(userSeq.normalizedSignature())) {
                    drifts.add(DriftItem.modified("Sequences", "Sequence", name,
                            DriftSeverity. MEDIUM,
                            stdSeq.normalizedSignature(),
                            userSeq.normalizedSignature(),
                            "Sequence configuration differs"));
                }
            }
        }

        for (String name :  userSequences.keySet()) {
            if (!stdSequences. containsKey(name)) {
                drifts.add(DriftItem.extra("Sequences", "Sequence", name, DriftSeverity.LOW));
            }
        }

        return drifts;
    }

    /**
     * Compares views.
     */
    private List<DriftItem> compareViews(SchemaSnapshot standard, SchemaSnapshot user) {
        List<DriftItem> drifts = new ArrayList<>();

        Map<String, ViewInfo> stdViews = standard. views().stream()
                .collect(java.util.stream.Collectors. toMap(
                        v -> v.name().toUpperCase(), v -> v, (a, b) -> a));

        Map<String, ViewInfo> userViews = user.views().stream()
                .collect(java. util.stream.Collectors.toMap(
                        v -> v. name().toUpperCase(), v -> v, (a, b) -> a));

        for (String name : stdViews.keySet()) {
            ViewInfo stdView = stdViews.get(name);

            if (!userViews.containsKey(name)) {
                drifts.add(DriftItem.missing("Views", "View", name, DriftSeverity. MEDIUM));
            } else {
                ViewInfo userView = userViews.get(name);
                if (!stdView.normalizedDefinition().equals(userView.normalizedDefinition())) {
                    drifts.add(DriftItem.modified("Views", "View Definition", name,
                            DriftSeverity.MEDIUM,
                            "See standard definition",
                            "See user definition",
                            "View definition differs"));
                }
            }
        }

        for (String name : userViews.keySet()) {
            if (!stdViews.containsKey(name)) {
                drifts.add(DriftItem.extra("Views", "View", name, DriftSeverity.LOW));
            }
        }

        return drifts;
    }
}