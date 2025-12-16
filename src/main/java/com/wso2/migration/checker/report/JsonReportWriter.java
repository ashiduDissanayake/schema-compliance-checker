package com.wso2.migration.checker.report;

import com.fasterxml. jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java. time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates JSON compliance reports.
 */
public class JsonReportWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JsonReportWriter.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ObjectMapper mapper;

    public JsonReportWriter() {
        this.mapper = new ObjectMapper();
        this.mapper. registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature. INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Writes the compliance report to a JSON file.
     */
    public Path writeReport(ComplianceReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        String filename = String.format("compliance_report_%s.json",
                LocalDateTime.now().format(FILE_DATE_FORMAT));
        Path outputPath = outputDir. resolve(filename);

        Map<String, Object> jsonReport = buildJsonStructure(report);

        mapper.writeValue(outputPath.toFile(), jsonReport);

        LOG.info("📄 Report written to: {}", outputPath. toAbsolutePath());
        return outputPath;
    }

    private Map<String, Object> buildJsonStructure(ComplianceReport report) {
        Map<String, Object> json = new LinkedHashMap<>();

        // Header
        json.put("reportId", report.getReportId());
        json.put("generatedAt", report.getGeneratedAt().toString());
        json.put("toolVersion", "1.0.0");

        // Summary
        json.put("summary", report.getSummary());

        // Migration Readiness
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("isReady", report.isMigrationReady());
        readiness.put("blockers", report.getDriftsBySeverity(DriftSeverity. CRITICAL).size());
        readiness.put("warnings", report.getDriftsBySeverity(DriftSeverity. HIGH).size());
        json.put("migrationReadiness", readiness);

        // Schema Info
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("standard", buildSnapshotInfo(report.getStandardSnapshot()));
        schemas.put("user", buildSnapshotInfo(report.getUserSnapshot()));
        json.put("schemas", schemas);

        // Drift Details by Category
        Map<String, List<Map<String, Object>>> driftsByCategory = new LinkedHashMap<>();
        for (DriftItem drift : report.getDriftItems()) {
            driftsByCategory
                    .computeIfAbsent(drift.category(), k -> new ArrayList<>())
                    .add(buildDriftItemJson(drift));
        }
        json.put("drifts", driftsByCategory);

        // Recommendations
        List<String> recommendations = generateRecommendations(report);
        json.put("recommendations", recommendations);

        return json;
    }

    private Map<String, Object> buildSnapshotInfo(com.wso2.migration.checker.model.SchemaSnapshot snapshot) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("databaseType", snapshot. databaseType());
        info.put("schemaName", snapshot.schemaName());
        info.put("capturedAt", snapshot.capturedAt().toString());
        info.put("tableCount", snapshot.tables().size());
        info.put("viewCount", snapshot.views().size());
        info.put("routineCount", snapshot.routines().size());
        info.put("triggerCount", snapshot.triggers().size());
        info.put("sequenceCount", snapshot.sequences().size());
        info.put("metadata", snapshot.metadata());
        return info;
    }

    private Map<String, Object> buildDriftItemJson(DriftItem drift) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("objectType", drift.objectType());
        item.put("objectName", drift.objectName());
        item.put("driftType", drift.driftType().getLabel());
        item.put("severity", drift.severity().name());
        item.put("severityIcon", drift.severity().getIcon());
        item.put("standardValue", drift.standardValue());
        item.put("userValue", drift.userValue());
        item.put("description", drift.description());
        item.put("recommendation", drift.recommendation());
        return item;
    }

    private List<String> generateRecommendations(ComplianceReport report) {
        List<String> recommendations = new ArrayList<>();

        long criticalCount = report.getDriftsBySeverity(DriftSeverity.CRITICAL).size();
        long highCount = report.getDriftsBySeverity(DriftSeverity.HIGH).size();

        if (criticalCount > 0) {
            recommendations.add(String.format(
                    "CRITICAL: %d critical issues must be resolved before migration", criticalCount));
        }

        if (highCount > 0) {
            recommendations.add(String.format(
                    "HIGH: %d high-priority issues should be reviewed", highCount));
        }

        // Category-specific recommendations
        if (! report.getDriftsByCategory("Tables").isEmpty()) {
            recommendations. add("Review missing or modified tables - these may cause data integrity issues");
        }

        if (!report.getDriftsByCategory("Routines").isEmpty()) {
            recommendations.add("Stored procedures/functions differ - test application functionality thoroughly");
        }

        if (!report.getDriftsByCategory("Triggers").isEmpty()) {
            recommendations.add("Trigger differences detected - verify automated business logic");
        }

        if (report.isMigrationReady()) {
            recommendations.add("✅ Schema is migration-ready.  Proceed with standard migration procedures.");
        } else {
            recommendations.add("❌ Schema is NOT migration-ready. Resolve critical and high issues first.");
        }

        return recommendations;
    }
}