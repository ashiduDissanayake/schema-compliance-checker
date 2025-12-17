package com.wso2.migration.checker.assertion;

import com.wso2.migration. checker.report.ComplianceReport;
import com.wso2.migration.checker.report.DriftItem;
import com.wso2.migration.checker.report.DriftSeverity;
import org.assertj.core. api.AbstractAssert;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream. Collectors;

/**
 * Custom AssertJ assertions for ComplianceReport validation.
 */
public class DriftAssertions extends AbstractAssert<DriftAssertions, ComplianceReport> {

    private DriftAssertions(ComplianceReport report) {
        super(report, DriftAssertions.class);
    }

    public static DriftAssertions assertThat(ComplianceReport report) {
        return new DriftAssertions(report);
    }

    // ========================================
    // Migration Readiness Assertions
    // ========================================

    public DriftAssertions isMigrationReady() {
        isNotNull();
        if (!actual.isMigrationReady()) {
            failWithMessage("Expected schema to be migration-ready but it has blocking issues:\n%s",
                    formatBlockingDrifts());
        }
        return this;
    }

    public DriftAssertions isNotMigrationReady() {
        isNotNull();
        if (actual. isMigrationReady()) {
            failWithMessage("Expected schema to NOT be migration-ready but no blocking issues found");
        }
        return this;
    }

    // ========================================
    // Drift Count Assertions
    // ========================================

    public DriftAssertions hasNoDrifts() {
        isNotNull();
        if (!actual.getDriftItems().isEmpty()) {
            failWithMessage("Expected no drifts but found %d:\n%s",
                    actual.getDriftItems().size(), formatDrifts(actual. getDriftItems()));
        }
        return this;
    }

    public DriftAssertions hasDrifts() {
        isNotNull();
        if (actual.getDriftItems().isEmpty()) {
            failWithMessage("Expected drifts to be detected but found none");
        }
        return this;
    }

    public DriftAssertions hasDriftCount(int expectedCount) {
        isNotNull();
        int actualCount = actual.getDriftItems().size();
        if (actualCount != expectedCount) {
            failWithMessage("Expected %d drifts but found %d:\n%s",
                    expectedCount, actualCount, formatDrifts(actual.getDriftItems()));
        }
        return this;
    }

    public DriftAssertions hasMinDriftCount(int minCount) {
        isNotNull();
        int actualCount = actual.getDriftItems().size();
        if (actualCount < minCount) {
            failWithMessage("Expected at least %d drifts but found %d", minCount, actualCount);
        }
        return this;
    }

    public DriftAssertions hasMaxDriftCount(int maxCount) {
        isNotNull();
        int actualCount = actual. getDriftItems().size();
        if (actualCount > maxCount) {
            failWithMessage("Expected at most %d drifts but found %d:\n%s",
                    maxCount, actualCount, formatDrifts(actual. getDriftItems()));
        }
        return this;
    }

    // ========================================
    // Severity-based Assertions
    // ========================================

    public DriftAssertions hasCriticalDrifts(int count) {
        return hasDriftsBySeverity(DriftSeverity.CRITICAL, count);
    }

    public DriftAssertions hasHighDrifts(int count) {
        return hasDriftsBySeverity(DriftSeverity.HIGH, count);
    }

    public DriftAssertions hasMediumDrifts(int count) {
        return hasDriftsBySeverity(DriftSeverity. MEDIUM, count);
    }

    public DriftAssertions hasLowDrifts(int count) {
        return hasDriftsBySeverity(DriftSeverity.LOW, count);
    }

    public DriftAssertions hasDriftsBySeverity(DriftSeverity severity, int expectedCount) {
        isNotNull();
        long actualCount = actual.getDriftsBySeverity(severity).size();
        if (actualCount != expectedCount) {
            failWithMessage("Expected %d %s drifts but found %d:\n%s",
                    expectedCount, severity, actualCount,
                    formatDrifts(actual.getDriftsBySeverity(severity)));
        }
        return this;
    }

    public DriftAssertions hasNoCriticalDrifts() {
        return hasCriticalDrifts(0);
    }

    public DriftAssertions hasNoHighDrifts() {
        return hasHighDrifts(0);
    }

    public DriftAssertions hasAtLeastCriticalDrifts(int minCount) {
        isNotNull();
        long actualCount = actual.getDriftsBySeverity(DriftSeverity.CRITICAL).size();
        if (actualCount < minCount) {
            failWithMessage("Expected at least %d CRITICAL drifts but found %d", minCount, actualCount);
        }
        return this;
    }

    public DriftAssertions hasAtLeastHighDrifts(int minCount) {
        isNotNull();
        long actualCount = actual.getDriftsBySeverity(DriftSeverity.HIGH).size();
        if (actualCount < minCount) {
            failWithMessage("Expected at least %d HIGH drifts but found %d", minCount, actualCount);
        }
        return this;
    }

    // ========================================
    // Specific Drift Assertions
    // ========================================

    public DriftAssertions containsMissingTable(String tableName) {
        return containsDrift("Table", tableName, DriftItem.DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsExtraTable(String tableName) {
        return containsDrift("Table", tableName, DriftItem. DriftType.MISSING_IN_STANDARD);
    }

    public DriftAssertions containsMissingColumn(String columnPath) {
        return containsDrift("Column", columnPath, DriftItem. DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsExtraColumn(String columnPath) {
        return containsDrift("Column", columnPath, DriftItem.DriftType.MISSING_IN_STANDARD);
    }

    public DriftAssertions containsModifiedColumn(String columnPath) {
        return containsDrift("Column", columnPath, DriftItem. DriftType.MODIFIED);
    }

    public DriftAssertions containsMissingIndex(String indexName) {
        return containsDrift("Index", indexName, DriftItem.DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsMissingConstraint(String constraintType, String name) {
        return containsDrift(constraintType, name, DriftItem.DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsMissingPrimaryKey(String tableName) {
        return containsDrift("PRIMARY_KEY", tableName, DriftItem.DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsMissingForeignKey(String fkName) {
        return containsDrift("FOREIGN_KEY", fkName, DriftItem. DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsMissingProcedure(String procName) {
        return containsDrift("PROCEDURE", procName, DriftItem.DriftType. MISSING_IN_USER);
    }

    public DriftAssertions containsMissingFunction(String funcName) {
        return containsDrift("FUNCTION", funcName, DriftItem.DriftType. MISSING_IN_USER);
    }

    public DriftAssertions containsMissingTrigger(String triggerName) {
        return containsDrift("Trigger", triggerName, DriftItem.DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsMissingView(String viewName) {
        return containsDrift("View", viewName, DriftItem. DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsMissingEvent(String eventName) {
        return containsDrift("Event", eventName, DriftItem.DriftType.MISSING_IN_USER);
    }

    public DriftAssertions containsModifiedProcedure(String procName) {
        return containsDrift("PROCEDURE", procName, DriftItem.DriftType.MODIFIED);
    }

    public DriftAssertions containsModifiedTrigger(String triggerName) {
        return containsDrift("Trigger", triggerName, DriftItem.DriftType. MODIFIED);
    }

    public DriftAssertions containsTypeMismatch(String columnPath) {
        isNotNull();
        boolean found = actual.getDriftItems().stream()
                .anyMatch(d -> d.objectName().toUpperCase().contains(columnPath. toUpperCase()) &&
                        (d. driftType() == DriftItem.DriftType.MODIFIED ||
                                d.driftType() == DriftItem.DriftType.TYPE_MISMATCH));
        if (!found) {
            failWithMessage("Expected type mismatch for '%s' but not found.\nActual drifts:\n%s",
                    columnPath, formatDrifts(actual.getDriftItems()));
        }
        return this;
    }

    public DriftAssertions containsEngineMismatch(String tableName) {
        isNotNull();
        boolean found = actual. getDriftItems().stream()
                .anyMatch(d -> d. objectName().toUpperCase().contains(tableName.toUpperCase()) &&
                        d.objectType().toUpperCase().contains("ENGINE"));
        if (!found) {
            failWithMessage("Expected engine mismatch for table '%s' but not found", tableName);
        }
        return this;
    }

    public DriftAssertions containsAutoIncrementMismatch(String columnPath) {
        isNotNull();
        boolean found = actual. getDriftItems().stream()
                .anyMatch(d -> d. objectName().toUpperCase().contains(columnPath.toUpperCase()) &&
                        (d.description().toUpperCase().contains("AUTO_INCREMENT") ||
                                d.description().toUpperCase().contains("AUTO INCREMENT")));
        if (!found) {
            failWithMessage("Expected AUTO_INCREMENT mismatch for '%s' but not found", columnPath);
        }
        return this;
    }

    public DriftAssertions containsNullabilityMismatch(String columnPath) {
        isNotNull();
        boolean found = actual.getDriftItems().stream()
                .anyMatch(d -> d.objectName().toUpperCase().contains(columnPath.toUpperCase()) &&
                        (d.objectType().toUpperCase().contains("NULLABILITY") ||
                                d. description().toUpperCase().contains("NULL")));
        if (!found) {
            failWithMessage("Expected nullability mismatch for '%s' but not found", columnPath);
        }
        return this;
    }

    public DriftAssertions containsGeneratedColumnMismatch(String columnPath) {
        isNotNull();
        boolean found = actual.getDriftItems().stream()
                .anyMatch(d -> d.objectName().toUpperCase().contains(columnPath.toUpperCase()) &&
                        (d.description().toUpperCase().contains("GENERATED") ||
                                d. description().toUpperCase().contains("COMPUTED")));
        if (!found) {
            failWithMessage("Expected generated column mismatch for '%s' but not found", columnPath);
        }
        return this;
    }

    public DriftAssertions containsUnsignedMismatch(String columnPath) {
        isNotNull();
        boolean found = actual.getDriftItems().stream()
                .anyMatch(d -> d.objectName().toUpperCase().contains(columnPath.toUpperCase()) &&
                        d.description().toUpperCase().contains("UNSIGNED"));
        if (!found) {
            failWithMessage("Expected UNSIGNED mismatch for '%s' but not found", columnPath);
        }
        return this;
    }

    // ========================================
    // Generic Drift Assertion
    // ========================================

    public DriftAssertions containsDrift(String objectType, String objectNamePattern, DriftItem.DriftType driftType) {
        isNotNull();
        boolean found = actual.getDriftItems().stream()
                .anyMatch(d ->
                        d.objectType().toUpperCase().contains(objectType.toUpperCase()) &&
                                d. objectName().toUpperCase().contains(objectNamePattern.toUpperCase()) &&
                                d. driftType() == driftType);
        if (!found) {
            failWithMessage("Expected %s drift for %s '%s' but not found.\nActual drifts:\n%s",
                    driftType, objectType, objectNamePattern, formatDrifts(actual. getDriftItems()));
        }
        return this;
    }

    public DriftAssertions containsDriftMatching(Predicate<DriftItem> predicate, String description) {
        isNotNull();
        boolean found = actual.getDriftItems().stream().anyMatch(predicate);
        if (!found) {
            failWithMessage("Expected drift matching '%s' but not found.\nActual drifts:\n%s",
                    description, formatDrifts(actual.getDriftItems()));
        }
        return this;
    }

    public DriftAssertions doesNotContainDrift(String objectType, String objectNamePattern) {
        isNotNull();
        boolean found = actual.getDriftItems().stream()
                .anyMatch(d ->
                        d.objectType().toUpperCase().contains(objectType.toUpperCase()) &&
                                d.objectName().toUpperCase().contains(objectNamePattern. toUpperCase()));
        if (found) {
            List<DriftItem> matches = actual.getDriftItems().stream()
                    .filter(d -> d.objectType().toUpperCase().contains(objectType.toUpperCase()) &&
                            d.objectName().toUpperCase().contains(objectNamePattern.toUpperCase()))
                    .collect(Collectors.toList());
            failWithMessage("Expected no drift for %s '%s' but found:\n%s",
                    objectType, objectNamePattern, formatDrifts(matches));
        }
        return this;
    }

    // ========================================
    // Compliance Score Assertions
    // ========================================

    public DriftAssertions hasComplianceScoreAbove(double minScore) {
        isNotNull();
        double actualScore = (double) actual.getSummary().get("complianceScore");
        if (actualScore < minScore) {
            failWithMessage("Expected compliance score above %. 1f but was %.1f", minScore, actualScore);
        }
        return this;
    }

    public DriftAssertions hasComplianceScoreBelow(double maxScore) {
        isNotNull();
        double actualScore = (double) actual.getSummary().get("complianceScore");
        if (actualScore > maxScore) {
            failWithMessage("Expected compliance score below %.1f but was %.1f", maxScore, actualScore);
        }
        return this;
    }

    public DriftAssertions hasComplianceScoreBetween(double minScore, double maxScore) {
        isNotNull();
        double actualScore = (double) actual.getSummary().get("complianceScore");
        if (actualScore < minScore || actualScore > maxScore) {
            failWithMessage("Expected compliance score between %.1f and %.1f but was %.1f",
                    minScore, maxScore, actualScore);
        }
        return this;
    }

    public DriftAssertions hasPerfectCompliance() {
        return hasComplianceScoreAbove(99.9);
    }

    // ========================================
    // Formatting Helpers
    // ========================================

    private String formatDrifts(List<DriftItem> drifts) {
        if (drifts.isEmpty()) {
            return "  (none)";
        }
        StringBuilder sb = new StringBuilder();
        for (DriftItem drift : drifts) {
            sb.append(String.format("  [%s] %s:  %s - %s%n",
                    drift.severity(),
                    drift.objectType(),
                    drift.objectName(),
                    drift.description()));
        }
        return sb.toString();
    }

    private String formatBlockingDrifts() {
        List<DriftItem> blocking = actual.getDriftItems().stream()
                .filter(d -> d.severity() == DriftSeverity.CRITICAL || d.severity() == DriftSeverity.HIGH)
                .collect(Collectors.toList());
        return formatDrifts(blocking);
    }
}