package com.wso2.migration.checker;

import com.wso2.migration. checker.config.AppConfig;
import com.wso2.migration.checker.core.ComplianceOrchestrator;
import com.wso2.migration.checker.report.ComplianceReport;
import com. wso2.migration.checker. util.ConsoleFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point for the Schema Compliance Checker.
 *
 * This tool validates database schema integrity by comparing a user's database
 * dump against a golden standard schema in isolated Docker containers.
 *
 * Usage:
 *   java -jar schema-compliance-checker.jar <db-type> <user-dump-path> [options]
 *
 * Examples:
 *   java -jar schema-compliance-checker.jar mysql /path/to/dump.sql
 *   java -jar schema-compliance-checker.jar oracle dump.sql --standard /path/to/golden.sql
 *   java -jar schema-compliance-checker.jar mssql backup.sql --output ./my-reports
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ConsoleFormatter.printBanner();

        try {
            // Parse configuration
            AppConfig config = new AppConfig();
            config.parseArguments(args);

            // Display configuration
            printConfiguration(config);

            // Execute compliance check
            ComplianceOrchestrator orchestrator = new ComplianceOrchestrator(
                    config.getDatabaseType(),
                    config.getStandardSchemaPath(),
                    config.getUserDumpPath(),
                    config.getReportOutputDir()
            );

            ComplianceReport report = orchestrator. execute();

            // Exit with appropriate code
            int exitCode = report.isMigrationReady() ? 0 : 1;
            System. exit(exitCode);

        } catch (IllegalArgumentException e) {
            // Configuration/usage error
            ConsoleFormatter.printError(e. getMessage());
            System.exit(2);

        } catch (Exception e) {
            // Unexpected error
            LOG.error("Unexpected error", e);
            ConsoleFormatter.printError("Unexpected error:  " + e.getMessage());
            System.exit(3);
        }
    }

    private static void printConfiguration(AppConfig config) {
        ConsoleFormatter.printSection("Configuration");
        ConsoleFormatter.printInfo("Database Type:     " + config.getDatabaseType().getDisplayName());
        ConsoleFormatter.printInfo("Standard Schema:  " + config.getStandardSchemaPath().getFileName());
        ConsoleFormatter.printInfo("User Dump:        " + config.getUserDumpPath().getFileName());
        ConsoleFormatter. printInfo("Report Output:    " + config.getReportOutputDir());
        System.out.println();
    }
}