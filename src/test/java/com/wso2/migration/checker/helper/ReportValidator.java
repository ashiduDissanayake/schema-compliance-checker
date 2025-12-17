package com.wso2.migration.checker.helper;

import com.fasterxml.jackson.databind. JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wso2.migration.checker.report.ComplianceReport;
import com.wso2.migration.checker.report.DriftItem;
import com.wso2.migration.checker.report.DriftSeverity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util. ArrayList;
import java.util. List;
import java.util. Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates compliance reports for correctness and completeness.
 */
public final class ReportValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReportValidator() {}

    /**
     * Validates the structure and content of a JSON report file.
     */
    public static void validateJsonReport(Path reportPath) throws IOException {
        assertThat(reportPath).exists().isReadable();

        String content = Files.readString(reportPath);
        assertThat(content).isNotEmpty();

        JsonNode root = MAPPER.readTree(content);

        // Required top-level fields
        assertThat(root.has("reportId")).isTrue();
        assertThat(root.has("generatedAt")).isTrue();
        assertThat(root.has("summary")).isTrue();
        assertThat(root.has("migrationReadiness")).isTrue();
        assertThat(root.has("schemas")).isTrue();
        assertThat(root.has("drifts")).isTrue();
        assertThat(root.has("recommendations")).isTrue();

        // Validate summary
        JsonNode summary = root.get("summary");
        assertThat(summary.has("complianceScore")).isTrue();
        assertThat(summary.has("totalDrifts")).isTrue();
        assertThat(summary.has("migrationReady")).isTrue();

        // Validate compliance score range
        double score = summary.get("complianceScore").asDouble();
        assertThat(score).isBetween(0.0, 100.0);
    }

    /**
     * Validates that a ComplianceReport object is well-formed.
     */
    public static void validateReport(ComplianceReport report) {
        assertThat(report).isNotNull();
        assertThat(report.getReportId()).isNotNull().isNotEmpty();
        assertThat(report.getGeneratedAt()).isNotNull();
        assertThat(report.getStandardSnapshot()).isNotNull();
        assertThat(report.getUserSnapshot()).isNotNull();
        assertThat(report.getDriftItems()).isNotNull();

        Map<String, Object> summary = report.getSummary();
        assertThat(summary).isNotNull().isNotEmpty();
        assertThat(summary).containsKey("complianceScore");
        assertThat(summary).containsKey("totalDrifts");
        assertThat(summary).containsKey("migrationReady");
    }

    /**
     * Validates drift items have required fields.
     */
    public static void validateDriftItems(List<DriftItem> drifts) {
        for (DriftItem drift : drifts) {
            assertThat(drift. category()).isNotNull().isNotEmpty();
            assertThat(drift.objectType()).isNotNull().isNotEmpty();
            assertThat(drift.objectName()).isNotNull().isNotEmpty();
            assertThat(drift. driftType()).isNotNull();
            assertThat(drift. severity()).isNotNull();
            assertThat(drift.description()).isNotNull().isNotEmpty();
        }
    }

    /**
     * Counts drifts by severity.
     */
    public static Map<DriftSeverity, Long> countBySeverity(ComplianceReport report) {
        return report.getDriftItems().stream()
                .collect(java.util.stream.Collectors. groupingBy(
                        DriftItem::severity,
                        java.util.stream.Collectors.counting()
                ));
    }

    /**
     * Finds all drifts matching a pattern.
     */
    public static List<DriftItem> findDrifts(ComplianceReport report,
                                             String objectTypePattern,
                                             String objectNamePattern) {
        List<DriftItem> matches = new ArrayList<>();
        for (DriftItem drift : report. getDriftItems()) {
            boolean typeMatch = objectTypePattern == null ||
                    drift.objectType().toUpperCase().contains(objectTypePattern.toUpperCase());
            boolean nameMatch = objectNamePattern == null ||
                    drift.objectName().toUpperCase().contains(objectNamePattern.toUpperCase());

            if (typeMatch && nameMatch) {
                matches.add(drift);
            }
        }
        return matches;
    }

    /**
     * Asserts that the report contains a specific number of drifts by category.
     */
    public static void assertCategoryCount(ComplianceReport report, String category, int expectedCount) {
        long actualCount = report.getDriftsByCategory(category).size();
        assertThat(actualCount)
                .as("Expected %d drifts in category '%s' but found %d", expectedCount, category, actualCount)
                .isEqualTo(expectedCount);
    }
}