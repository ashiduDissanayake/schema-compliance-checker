package com.wso2.migration. checker;

import com.fasterxml.jackson.databind. JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wso2.migration.checker.container.ContainerFactory;
import com.wso2.migration.checker.container.DatabaseType;
import com.wso2.migration.checker.core.ComplianceOrchestrator;
import com.wso2.migration.checker.core.DiffEngine;
import com.wso2.migration.checker.inspector.SchemaInspector;
import com.wso2.migration.checker.model.SchemaSnapshot;
import com.wso2.migration.checker.report.ComplianceReport;
import com.wso2.migration.checker.report.DriftItem;
import com.wso2.migration.checker.report.DriftSeverity;
import org.junit.jupiter.api.AfterEach;
import org. junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter. api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers. containers.JdbcDatabaseContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for all integration tests.
 * Provides common utilities for container management, schema comparison, and assertions.
 */
public abstract class BaseIntegrationTest {

    protected static final Logger LOG = LoggerFactory.getLogger(BaseIntegrationTest.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Track containers for cleanup
    private final List<JdbcDatabaseContainer<? >> activeContainers = new ArrayList<>();
    private static final AtomicInteger TEST_COUNTER = new AtomicInteger(0);

    protected TestInfo testInfo;
    protected Path testReportDir;

    @BeforeAll
    static void setupAll() {
        LOG.info("=". repeat(70));
        LOG.info("Schema Compliance Checker - Test Suite");
        LOG.info("=". repeat(70));
    }

    @BeforeEach
    void setup(TestInfo testInfo) {
        this.testInfo = testInfo;
        int testNumber = TEST_COUNTER. incrementAndGet();

        LOG.info("");
        LOG.info("-".repeat(70));
        LOG.info("Test #{}:  {}", testNumber, testInfo. getDisplayName());
        LOG.info("-".repeat(70));

        // Create test-specific report directory
        testReportDir = TestConstants.TEST_REPORTS_PATH
                .resolve(testInfo.getTestClass().map(Class::getSimpleName).orElse("Unknown"))
                .resolve(testInfo. getTestMethod().map(m -> m.getName()).orElse("unknown"));

        try {
            Files.createDirectories(testReportDir);
        } catch (IOException e) {
            LOG.warn("Could not create test report directory: {}", e.getMessage());
        }
    }

    @AfterEach
    void teardown() {
        // Cleanup all containers
        for (JdbcDatabaseContainer<? > container : activeContainers) {
            try {
                if (container != null && container.isRunning()) {
                    container.stop();
                    LOG.debug("Container stopped: {}", container.getContainerId());
                }
            } catch (Exception e) {
                LOG.warn("Failed to stop container: {}", e. getMessage());
            }
        }
        activeContainers.clear();

        LOG.info("Test completed:  {}", testInfo.getDisplayName());
    }

    // ========================================
    // Container Management
    // ========================================

    /**
     * Creates and starts a MySQL container with the given SQL dump.
     */
    protected JdbcDatabaseContainer<?> startMySqlContainer(Path sqlDumpPath, String label) {
        JdbcDatabaseContainer<?> container = ContainerFactory.createAndStart(
                DatabaseType.MYSQL, sqlDumpPath, label);
        activeContainers.add(container);
        return container;
    }

    /**
     * Captures a schema snapshot from a running container.
     */
    protected SchemaSnapshot captureSnapshot(JdbcDatabaseContainer<? > container, DatabaseType dbType) {
        SchemaInspector inspector = new SchemaInspector(dbType);
        return inspector.captureSnapshot(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword()
        );
    }

    /**
     * Gets the count of running Docker containers with our naming pattern.
     */
    protected int getRunningContainerCount() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-q", "--filter", "name=compliance"
            );
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            if (output.trim().isEmpty()) {
                return 0;
            }
            return output.trim().split("\n").length;
        } catch (Exception e) {
            LOG.warn("Could not check container count: {}", e.getMessage());
            return -1;
        }
    }

    // ========================================
    // Comparison Utilities
    // ========================================

    /**
     * Runs a full compliance check between standard and user schemas.
     */
    protected ComplianceReport runComplianceCheck(Path standardPath, Path userDumpPath) {
        ComplianceOrchestrator orchestrator = new ComplianceOrchestrator(
                DatabaseType.MYSQL,
                standardPath,
                userDumpPath,
                testReportDir
        );
        return orchestrator.execute();
    }

    /**
     * Compares two snapshots directly using DiffEngine.
     */
    protected ComplianceReport compareSnapshots(SchemaSnapshot standard, SchemaSnapshot user) {
        DiffEngine diffEngine = new DiffEngine();
        return diffEngine.compare(standard, user);
    }

    /**
     * Quick comparison for MySQL schemas.
     */
    protected ComplianceReport compareMySqlSchemas(Path standardPath, Path userDumpPath) {
        JdbcDatabaseContainer<?> stdContainer = null;
        JdbcDatabaseContainer<?> userContainer = null;

        try {
            stdContainer = startMySqlContainer(standardPath, "STANDARD");
            userContainer = startMySqlContainer(userDumpPath, "USER");

            SchemaSnapshot stdSnapshot = captureSnapshot(stdContainer, DatabaseType.MYSQL);
            SchemaSnapshot userSnapshot = captureSnapshot(userContainer, DatabaseType.MYSQL);

            return compareSnapshots(stdSnapshot, userSnapshot);
        } finally {
            // Containers will be cleaned up in teardown
        }
    }

    // ========================================
    // Assertion Helpers
    // ========================================

    /**
     * Asserts that no drift was detected.
     */
    protected void assertNoDrift(ComplianceReport report) {
        assertThat(report.getDriftItems())
                .as("Expected no drift items but found: %s", report.getDriftItems())
                .isEmpty();
        assertThat(report.isMigrationReady())
                .as("Schema should be migration-ready")
                .isTrue();
    }

    /**
     * Asserts that drift was detected.
     */
    protected void assertDriftDetected(ComplianceReport report) {
        assertThat(report.getDriftItems())
                .as("Expected drift items to be detected")
                .isNotEmpty();
    }

    /**
     * Asserts that a specific drift item exists.
     */
    protected void assertDriftContains(ComplianceReport report,
                                       String objectType,
                                       String objectNamePattern,
                                       DriftSeverity severity) {
        assertThat(report.getDriftItems())
                .as("Expected drift for %s matching '%s' with severity %s",
                        objectType, objectNamePattern, severity)
                .anyMatch(drift ->
                        drift.objectType().equalsIgnoreCase(objectType) &&
                                drift.objectName().toUpperCase().contains(objectNamePattern.toUpperCase()) &&
                                drift.severity() == severity
                );
    }

    /**
     * Asserts that a missing object drift exists.
     */
    protected void assertMissingObject(ComplianceReport report,
                                       String objectType,
                                       String objectName,
                                       DriftSeverity expectedSeverity) {
        assertThat(report.getDriftItems())
                .as("Expected missing %s:  %s", objectType, objectName)
                .anyMatch(drift ->
                        drift.objectType().equalsIgnoreCase(objectType) &&
                                drift.objectName().toUpperCase().contains(objectName.toUpperCase()) &&
                                drift. driftType() == DriftItem.DriftType.MISSING_IN_USER &&
                                drift.severity() == expectedSeverity
                );
    }

    /**
     * Asserts that an extra object drift exists.
     */
    protected void assertExtraObject(ComplianceReport report,
                                     String objectType,
                                     String objectName,
                                     DriftSeverity expectedSeverity) {
        assertThat(report.getDriftItems())
                .as("Expected extra %s: %s", objectType, objectName)
                .anyMatch(drift ->
                        drift.objectType().equalsIgnoreCase(objectType) &&
                                drift.objectName().toUpperCase().contains(objectName.toUpperCase()) &&
                                drift.driftType() == DriftItem.DriftType.MISSING_IN_STANDARD &&
                                drift.severity() == expectedSeverity
                );
    }

    /**
     * Asserts that a modified object drift exists.
     */
    protected void assertModifiedObject(ComplianceReport report,
                                        String objectType,
                                        String objectName,
                                        DriftSeverity expectedSeverity) {
        assertThat(report.getDriftItems())
                .as("Expected modified %s: %s", objectType, objectName)
                .anyMatch(drift ->
                        drift.objectType().toUpperCase().contains(objectType. toUpperCase()) &&
                                drift.objectName().toUpperCase().contains(objectName.toUpperCase()) &&
                                drift. driftType() == DriftItem.DriftType.MODIFIED &&
                                drift.severity() == expectedSeverity
                );
    }

    /**
     * Asserts the compliance score is within expected range.
     */
    protected void assertComplianceScore(ComplianceReport report, double minScore, double maxScore) {
        double score = (double) report.getSummary().get("complianceScore");
        assertThat(score)
                .as("Compliance score should be between %. 1f and %.1f", minScore, maxScore)
                .isBetween(minScore, maxScore);
    }

    /**
     * Asserts drift count by severity.
     */
    protected void assertDriftCount(ComplianceReport report, DriftSeverity severity, int expectedCount) {
        long actualCount = report.getDriftsBySeverity(severity).size();
        assertThat(actualCount)
                .as("Expected %d %s drifts but found %d", expectedCount, severity, actualCount)
                .isEqualTo(expectedCount);
    }

    /**
     * Asserts minimum drift count by severity.
     */
    protected void assertMinDriftCount(ComplianceReport report, DriftSeverity severity, int minCount) {
        long actualCount = report.getDriftsBySeverity(severity).size();
        assertThat(actualCount)
                .as("Expected at least %d %s drifts but found %d", minCount, severity, actualCount)
                .isGreaterThanOrEqualTo(minCount);
    }

    // ========================================
    // JSON Report Utilities
    // ========================================

    /**
     * Reads and parses a JSON report file.
     */
    protected JsonNode readJsonReport(Path reportPath) throws IOException {
        return OBJECT_MAPPER.readTree(reportPath.toFile());
    }

    /**
     * Finds the latest JSON report in the test report directory.
     */
    protected Path findLatestJsonReport() throws IOException {
        return Files.list(testReportDir)
                .filter(p -> p.toString().endsWith(".json"))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a)
                                .compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElseThrow(() -> new IOException("No JSON report found"));
    }

    // ========================================
    // File Utilities
    // ========================================

    /**
     * Creates a temporary SQL file with the given content.
     */
    protected Path createTempSqlFile(String content, String prefix) throws IOException {
        Path tempFile = Files.createTempFile(prefix, ".sql");
        Files.writeString(tempFile, content);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    /**
     * Reads SQL file content.
     */
    protected String readSqlFile(Path path) throws IOException {
        return Files.readString(path);
    }

    /**
     * Checks if a file exists and is readable.
     */
    protected boolean isFileAccessible(Path path) {
        return Files.exists(path) && Files.isReadable(path);
    }
}