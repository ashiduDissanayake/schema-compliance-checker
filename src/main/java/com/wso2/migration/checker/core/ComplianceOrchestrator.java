package com.wso2.migration.checker.core;

import com.wso2.migration. checker.container.ContainerFactory;
import com.wso2.migration.checker.container.DatabaseType;
import com.wso2.migration.checker.inspector.SchemaInspector;
import com.wso2.migration.checker.model.SchemaSnapshot;
import com.wso2.migration.checker.report.ComplianceReport;
import com.wso2.migration.checker.report. ReportGenerator;
import org. slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util. concurrent.Executors;

/**
 * Main orchestrator that coordinates the entire compliance checking workflow.
 *
 * Workflow:
 * 1. Start two Docker containers (Standard & User) in parallel
 * 2. Capture schema snapshots from both
 * 3. Compare snapshots using DiffEngine
 * 4. Generate reports (CLI + JSON)
 */
public class ComplianceOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(ComplianceOrchestrator.class);

    private final DatabaseType databaseType;
    private final Path standardSchemaPath;
    private final Path userDumpPath;
    private final Path reportOutputDir;

    private final DiffEngine diffEngine;
    private final ReportGenerator reportGenerator;

    public ComplianceOrchestrator(DatabaseType databaseType,
                                  Path standardSchemaPath,
                                  Path userDumpPath,
                                  Path reportOutputDir) {
        this.databaseType = databaseType;
        this.standardSchemaPath = standardSchemaPath;
        this.userDumpPath = userDumpPath;
        this.reportOutputDir = reportOutputDir;
        this.diffEngine = new DiffEngine();
        this.reportGenerator = new ReportGenerator();
    }

    /**
     * Executes the complete compliance check workflow.
     *
     * @return ComplianceReport containing all findings
     */
    public ComplianceReport execute() {
        LOG.info("🚀 Starting Schema Compliance Check");
        LOG.info("   Database Type: {}", databaseType. getDisplayName());
        LOG.info("   Standard Schema: {}", standardSchemaPath.getFileName());
        LOG.info("   User Dump: {}", userDumpPath.getFileName());

        long startTime = System.currentTimeMillis();

        try (ExecutorService executor = Executors. newFixedThreadPool(2)) {

            // Start both containers in parallel
            LOG.info("\n🐳 Phase 1: Starting Docker Containers.. .");

            CompletableFuture<ContainerWithSnapshot> standardFuture = CompletableFuture.supplyAsync(
                    () -> startAndCapture(standardSchemaPath, "STANDARD"), executor);

            CompletableFuture<ContainerWithSnapshot> userFuture = CompletableFuture.supplyAsync(
                    () -> startAndCapture(userDumpPath, "USER"), executor);

            // Wait for both to complete
            ContainerWithSnapshot standardResult = standardFuture.join();
            ContainerWithSnapshot userResult = userFuture.join();

            // Perform comparison
            LOG.info("\n⚖️  Phase 2: Analyzing Schema Differences...");
            ComplianceReport report = diffEngine.compare(
                    standardResult.snapshot(),
                    userResult.snapshot()
            );

            // Generate reports
            LOG.info("\n📊 Phase 3: Generating Reports...");
            reportGenerator. generateReports(report, reportOutputDir);

            // Cleanup containers
            LOG.info("\n🧹 Phase 4: Cleaning Up...");
            cleanup(standardResult.container());
            cleanup(userResult.container());

            long elapsed = System.currentTimeMillis() - startTime;
            LOG. info("\n✅ Compliance check completed in {} seconds", elapsed / 1000.0);

            return report;

        } catch (Exception e) {
            LOG.error("❌ Compliance check failed: {}", e.getMessage(), e);
            throw new RuntimeException("Compliance check failed", e);
        }
    }

    /**
     * Starts a container with the given SQL dump and captures its schema.
     */
    private ContainerWithSnapshot startAndCapture(Path sqlPath, String label) {
        JdbcDatabaseContainer<?> container = null;
        try {
            container = ContainerFactory.createAndStart(databaseType, sqlPath, label);

            SchemaInspector inspector = new SchemaInspector(databaseType);
            SchemaSnapshot snapshot = inspector.captureSnapshot(
                    container.getJdbcUrl(),
                    container.getUsername(),
                    container.getPassword()
            );

            LOG.info("   ✓ {} snapshot captured:  {}", label, snapshot.summary());

            return new ContainerWithSnapshot(container, snapshot);

        } catch (Exception e) {
            if (container != null) {
                cleanup(container);
            }
            throw new RuntimeException("Failed to start/capture " + label + " schema", e);
        }
    }

    private void cleanup(JdbcDatabaseContainer<?> container) {
        if (container != null && container.isRunning()) {
            try {
                container.stop();
                LOG.debug("   Container stopped successfully");
            } catch (Exception e) {
                LOG.warn("   Failed to stop container: {}", e.getMessage());
            }
        }
    }

    /**
     * Internal record to hold container and its captured snapshot together.
     */
    private record ContainerWithSnapshot(
            JdbcDatabaseContainer<? > container,
            SchemaSnapshot snapshot
    ) {}
}