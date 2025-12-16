package com.wso2.migration.checker.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Generates compliance reports in multiple formats (CLI + JSON).
 */
public class ReportGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ReportGenerator.class);

    private final JsonReportWriter jsonWriter;

    public ReportGenerator() {
        this.jsonWriter = new JsonReportWriter();
    }

    /**
     * Generates both CLI output and JSON file.
     */
    public void generateReports(ComplianceReport report, Path outputDir) {
        // Print to console
        printConsoleReport(report);

        // Write JSON file
        try {
            jsonWriter.writeReport(report, outputDir);
        } catch (IOException e) {
            LOG.error("Failed to write JSON report: {}", e.getMessage());
        }
    }

    /**
     * Prints a formatted report to the console.
     */
    public void printConsoleReport(ComplianceReport report) {
        Map<String, Object> summary = report.getSummary();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out. println("║           SCHEMA COMPLIANCE REPORT                               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Report ID:         %-45s ║%n", report.getReportId().substring(0, 8) + ".. .");
        System.out.printf("║  Generated:        %-45s ║%n", report.getGeneratedAt().toString().substring(0, 19));
        System.out.printf("║  Compliance Score: %-45s ║%n", summary.get("complianceScore") + "%");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");

        // Migration readiness
        boolean ready = (boolean) summary.get("migrationReady");
        String readyIcon = ready ? "✅" :  "❌";
        String readyText = ready ? "READY FOR MIGRATION" : "NOT READY - ISSUES FOUND";
        System.out.printf("║  Migration Status:  %s %-42s ║%n", readyIcon, readyText);

        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  DRIFT SUMMARY                                                   ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");

        @SuppressWarnings("unchecked")
        Map<DriftSeverity, Long> bySeverity = (Map<DriftSeverity, Long>) summary.get("bySeverity");

        for (DriftSeverity severity : DriftSeverity.values()) {
            long count = bySeverity.getOrDefault(severity, 0L);
            if (count > 0 || severity == DriftSeverity. CRITICAL || severity == DriftSeverity. HIGH) {
                System.out.printf("║  %s %-12s:  %-48d ║%n",
                        severity.getIcon(), severity.name(), count);
            }
        }

        System. out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out. println("║  DETAILS BY CATEGORY                                             ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");

        @SuppressWarnings("unchecked")
        Map<String, Long> byCategory = (Map<String, Long>) summary.get("byCategory");

        for (Map.Entry<String, Long> entry : byCategory.entrySet()) {
            System.out. printf("║  %-18s: %-46d ║%n", entry.getKey(), entry.getValue());
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Print critical issues
        List<DriftItem> criticals = report.getDriftsBySeverity(DriftSeverity. CRITICAL);
        if (!criticals.isEmpty()) {
            System.out.println("🔴 CRITICAL ISSUES (Must Fix):");
            System.out.println("─". repeat(70));
            for (DriftItem drift : criticals) {
                System.out.printf("   • [%s] %s%n", drift.objectType(), drift.objectName());
                System.out. printf("     └─ %s%n", drift.description());
            }
            System.out.println();
        }

        // Print high issues
        List<DriftItem> highs = report. getDriftsBySeverity(DriftSeverity.HIGH);
        if (!highs.isEmpty()) {
            System.out.println("🟠 HIGH PRIORITY ISSUES (Should Fix):");
            System.out. println("─".repeat(70));
            for (DriftItem drift : highs) {
                System.out.printf("   • [%s] %s%n", drift.objectType(), drift.objectName());
                System.out. printf("     └─ %s%n", drift.description());
            }
            System.out. println();
        }

        // Summary counts for other severities
        long mediumCount = report.getDriftsBySeverity(DriftSeverity.MEDIUM).size();
        long lowCount = report.getDriftsBySeverity(DriftSeverity.LOW).size();

        if (mediumCount > 0 || lowCount > 0) {
            System.out.printf("ℹ️  Additional:  %d medium, %d low priority items (see JSON report for details)%n",
                    mediumCount, lowCount);
            System.out.println();
        }
    }
}