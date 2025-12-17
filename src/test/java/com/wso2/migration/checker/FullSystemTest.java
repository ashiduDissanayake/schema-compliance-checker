package com.wso2.migration.checker;

import com.wso2.migration.checker.config.AppConfig;
import com.wso2.migration.checker.core.ComplianceOrchestrator;
import com.wso2.migration.checker.report.ComplianceReport;
import org.junit.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class FullSystemTest {

    private static final String BASE_PATH = "src/test/resources/dumps/mysql/";

    @Test
    public void testExactMatch() {
        ComplianceReport report = runCheck("user_exact_match.sql");
        assertTrue("Should be migration ready", report.isMigrationReady());
        assertTrue("Should have no drifts", report.getDriftItems().isEmpty());
    }

    @Test
    public void testMissingTable() {
        ComplianceReport report = runCheck("user_missing_table.sql");
        assertFalse("Should not be migration ready", report.isMigrationReady());

        boolean found = report.getDriftItems().stream()
            .anyMatch(d -> d.description().toLowerCase().contains("missing") && d.objectName().equals("orders"));
        assertTrue("Should detect missing table 'orders'", found);
    }

    @Test
    public void testColumnDrift() {
        ComplianceReport report = runCheck("user_column_drift.sql");
        assertFalse("Should not be migration ready", report.isMigrationReady());

        boolean missingEmail = report.getDriftItems().stream()
            .anyMatch(d -> d.description().toLowerCase().contains("missing") && d.objectName().equals("users.email"));
        assertTrue("Should detect missing column 'email'", missingEmail);

        boolean extraCol = report.getDriftItems().stream()
            .anyMatch(d -> d.driftType() == com.wso2.migration.checker.report.DriftItem.DriftType.MISSING_IN_STANDARD
                        && d.objectName().equals("users.new_col"));
        assertTrue("Should detect extra column 'new_col'", extraCol);
    }

    @Test
    public void testTypeMismatch() {
        ComplianceReport report = runCheck("user_type_mismatch.sql");
        assertFalse("Should not be migration ready", report.isMigrationReady());

        boolean typeMismatch = report.getDriftItems().stream()
            .anyMatch(d -> d.driftType() == com.wso2.migration.checker.report.DriftItem.DriftType.MODIFIED
                        && d.objectName().equals("orders.amount")
                        && d.standardValue().contains("DECIMAL"));
        assertTrue("Should detect type mismatch for 'amount'", typeMismatch);
    }

    private ComplianceReport runCheck(String userDumpFile) {
        Path standard = Paths.get(BASE_PATH, "standard.sql").toAbsolutePath();
        Path user = Paths.get(BASE_PATH, userDumpFile).toAbsolutePath();
        Path output = Paths.get("target/reports").toAbsolutePath();

        AppConfig config = new AppConfig()
            .withDatabaseType(com.wso2.migration.checker.container.DatabaseType.MYSQL)
            .withStandardSchemaPath(standard)
            .withUserDumpPath(user)
            .withReportOutputDir(output);

        ComplianceOrchestrator orchestrator = new ComplianceOrchestrator(
                config.getDatabaseType(),
                config.getStandardSchemaPath(),
                config.getUserDumpPath(),
                config.getReportOutputDir()
        );

        return orchestrator.execute();
    }
}
