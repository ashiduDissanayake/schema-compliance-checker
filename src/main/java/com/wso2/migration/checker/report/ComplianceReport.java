package com. wso2.migration.checker. report;

import com.wso2.migration.checker.model.SchemaSnapshot;

import java.time.Instant;
import java. util.*;
import java.util.stream. Collectors;

/**
 * Complete compliance report comparing two schema snapshots.
 */
public class ComplianceReport {

    private final String reportId;
    private final Instant generatedAt;
    private final SchemaSnapshot standardSnapshot;
    private final SchemaSnapshot userSnapshot;
    private final List<DriftItem> driftItems;
    private final Map<String, Object> summary;

    public ComplianceReport(SchemaSnapshot standardSnapshot, SchemaSnapshot userSnapshot) {
        this.reportId = UUID.randomUUID().toString();
        this.generatedAt = Instant.now();
        this.standardSnapshot = standardSnapshot;
        this.userSnapshot = userSnapshot;
        this. driftItems = new ArrayList<>();
        this.summary = new HashMap<>();
    }

    public void addDrift(DriftItem item) {
        driftItems.add(item);
    }

    public void addAllDrifts(List<DriftItem> items) {
        driftItems.addAll(items);
    }

    /**
     * Calculates and returns the compliance summary.
     */
    public Map<String, Object> calculateSummary() {
        summary.clear();

        // Counts by severity
        Map<DriftSeverity, Long> bySeverity = driftItems. stream()
                .collect(Collectors.groupingBy(DriftItem::severity, Collectors.counting()));

        // Counts by category
        Map<String, Long> byCategory = driftItems.stream()
                .collect(Collectors.groupingBy(DriftItem::category, Collectors.counting()));

        // Counts by type
        Map<DriftItem.DriftType, Long> byType = driftItems.stream()
                .collect(Collectors. groupingBy(DriftItem:: driftType, Collectors.counting()));

        long criticalCount = bySeverity. getOrDefault(DriftSeverity.CRITICAL, 0L);
        long highCount = bySeverity.getOrDefault(DriftSeverity.HIGH, 0L);

        // Calculate compliance score (0-100)
        int totalObjects = standardSnapshot.tables().size() +
                standardSnapshot.routines().size() +
                standardSnapshot.triggers().size() +
                standardSnapshot.sequences().size() +
                standardSnapshot.views().size();

        int driftCount = driftItems.size();
        double complianceScore = totalObjects > 0
                ? Math.max(0, 100.0 - (driftCount * 100.0 / totalObjects))
                : 100.0;

        summary.put("reportId", reportId);
        summary.put("generatedAt", generatedAt.toString());
        summary.put("complianceScore", Math.round(complianceScore * 10) / 10.0);
        summary.put("totalDrifts", driftCount);
        summary.put("criticalDrifts", criticalCount);
        summary.put("highDrifts", highCount);
        summary.put("migrationReady", criticalCount == 0 && highCount == 0);
        summary.put("bySeverity", bySeverity);
        summary.put("byCategory", byCategory);
        summary.put("byType", byType);
        summary.put("standardSummary", standardSnapshot.summary());
        summary.put("userSummary", userSnapshot.summary());

        return summary;
    }

    /**
     * Determines if the schema is migration-ready.
     */
    public boolean isMigrationReady() {
        return driftItems.stream()
                .noneMatch(d -> d.severity() == DriftSeverity. CRITICAL || d.severity() == DriftSeverity.HIGH);
    }

    // Getters
    public String getReportId() { return reportId; }
    public Instant getGeneratedAt() { return generatedAt; }
    public SchemaSnapshot getStandardSnapshot() { return standardSnapshot; }
    public SchemaSnapshot getUserSnapshot() { return userSnapshot; }
    public List<DriftItem> getDriftItems() { return Collections.unmodifiableList(driftItems); }
    public Map<String, Object> getSummary() { return summary. isEmpty() ? calculateSummary() : summary; }

    /**
     * Gets drift items filtered by severity.
     */
    public List<DriftItem> getDriftsBySeverity(DriftSeverity severity) {
        return driftItems.stream()
                .filter(d -> d.severity() == severity)
                .collect(Collectors. toList());
    }

    /**
     * Gets drift items filtered by category.
     */
    public List<DriftItem> getDriftsByCategory(String category) {
        return driftItems.stream()
                .filter(d -> d.category().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }
}