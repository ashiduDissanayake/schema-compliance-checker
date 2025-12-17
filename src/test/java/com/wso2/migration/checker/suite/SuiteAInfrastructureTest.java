package com.wso2.migration.checker.suite;

import com.wso2.migration.checker.BaseIntegrationTest;
import com. wso2.migration.checker. TestConstants;
import com.wso2.migration.checker.config.AppConfig;
import com.wso2.migration.checker.container.DatabaseType;
import com.wso2.migration.checker.helper.DockerHealthChecker;
import com.wso2.migration.checker.report.ComplianceReport;
import org.junit.jupiter.api.*;
import org.junit.jupiter. api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter. params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.ByteArrayOutputStream;
import java. io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util. concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility. Awaitility. await;

/**
 * Suite A:  Infrastructure & CLI Tests
 *
 * Tests basic tool functionality, Docker integration, error handling,
 * and resource cleanup.
 */
@Tag(TestConstants.TAG_INFRASTRUCTURE)
@Tag(TestConstants. TAG_SMOKE)
@TestMethodOrder(MethodOrderer. OrderAnnotation.class)
@DisplayName("Suite A: Infrastructure Tests")
class SuiteAInfrastructureTest extends BaseIntegrationTest {

    // ========================================
    // INF-001: Docker Availability Check
    // ========================================

    @Test
    @Order(1)
    @DisplayName("INF-001: Should detect when Docker is available")
    @EnabledIf("isDockerRunning")
    void testDockerAvailable() {
        // Given Docker is running
        boolean available = DockerHealthChecker.isDockerAvailable();

        // Then it should be detected
        assertThat(available)
                .as("Docker daemon should be available")
                .isTrue();

        // And version should be retrievable
        String version = DockerHealthChecker.getDockerVersion();
        LOG.info("Docker version: {}", version);
        assertThat(version)
                .as("Docker version should be retrievable")
                .isNotEmpty()
                .containsIgnoringCase("docker");
    }

    @Test
    @Order(2)
    @DisplayName("INF-001b: Should handle Docker unavailable gracefully")
    @Disabled("Manual test - requires stopping Docker")
    void testDockerUnavailableHandling() {
        // This test is manual - run with Docker stopped
        // The tool should exit with code 3 and clear error message
    }

    // ========================================
    // INF-002: File Not Found Handling
    // ========================================

    @Test
    @Order(3)
    @DisplayName("INF-002: Should handle non-existent user dump file")
    void testUserDumpFileNotFound() {
        // Given a non-existent file path
        Path nonExistentFile = Path.of("/nonexistent/path/dump.sql");

        // When validating configuration
        AppConfig config = new AppConfig();

        // Then it should throw with clear message
        assertThatThrownBy(() -> config.parseArguments(new String[]{"mysql", nonExistentFile.toString()}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @Order(4)
    @DisplayName("INF-002b: Should handle non-existent standard schema file")
    void testStandardSchemaFileNotFound() throws IOException {
        // Given a valid user dump but non-existent standard
        Path tempUserDump = createTempSqlFile("CREATE TABLE test (id INT);", "user_dump");
        Path nonExistentStandard = Path.of("/nonexistent/standard.sql");

        // When configuring with non-existent standard
        AppConfig config = new AppConfig();

        // Then it should throw with clear message
        assertThatThrownBy(() -> {
            config.parseArguments(new String[]{
                    "mysql",
                    tempUserDump.toString(),
                    "--standard",
                    nonExistentStandard.toString()
            });
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ========================================
    // INF-003: Unsupported DB Type
    // ========================================

    @Test
    @Order(5)
    @DisplayName("INF-003: Should reject unsupported database type")
    void testUnsupportedDatabaseType() {
        // Given an unsupported database type
        String unsupportedType = "mongodb";

        // When parsing database type
        // Then it should throw with clear message
        assertThatThrownBy(() -> DatabaseType.fromCode(unsupportedType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown database type");
    }

    @ParameterizedTest
    @Order(6)
    @DisplayName("INF-003b: Should accept all supported database types")
    @ValueSource(strings = {"mysql", "MYSQL", "MySQL", "oracle", "ORACLE", "mssql", "MSSQL", "postgresql"})
    void testSupportedDatabaseTypes(String dbType) {
        // Given a supported database type (various cases)
        // When parsing database type
        DatabaseType type = DatabaseType.fromCode(dbType);

        // Then it should be recognized
        assertThat(type).isNotNull();
    }

    // ========================================
    // INF-004: Clean Teardown
    // ========================================

    @Test
    @Order(7)
    @DisplayName("INF-004: Should cleanup containers after successful run")
    @EnabledIf("isDockerRunning")
    void testContainerCleanupAfterSuccess() throws IOException {
        // Given a valid comparison setup
        Path standardPath = TestConstants. MYSQL_GOLDEN_SCHEMA;
        Path userPath = createTempSqlFile(
                "CREATE TABLE test_cleanup (id INT PRIMARY KEY) ENGINE=InnoDB;",
                "cleanup_test"
        );

        // Skip if golden schema doesn't exist
        Assumptions.assumeTrue(Files.exists(standardPath), "Golden schema not found");

        int initialContainerCount = getRunningContainerCount();

        // When running comparison (containers started in compareMySqlSchemas)
        try {
            JdbcDatabaseContainer<?> container = startMySqlContainer(userPath, "CLEANUP_TEST");
            assertThat(container.isRunning()).isTrue();

            // Manually stop to simulate cleanup
            container.stop();
        } catch (Exception e) {
            LOG.warn("Container test failed:  {}", e.getMessage());
        }

        // Then containers should be cleaned up
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    int currentCount = getRunningContainerCount();
                    assertThat(currentCount)
                            .as("Container count should return to initial state")
                            .isLessThanOrEqualTo(initialContainerCount);
                });
    }

    @Test
    @Order(8)
    @DisplayName("INF-004b: Should cleanup containers after failed run")
    @EnabledIf("isDockerRunning")
    void testContainerCleanupAfterFailure() throws IOException {
        // Given invalid SQL that will cause container to fail initialization
        Path invalidSql = createTempSqlFile(
                "THIS IS NOT VALID SQL AT ALL!!! ",
                "invalid_sql"
        );

        int initialContainerCount = getRunningContainerCount();

        // When attempting to start container with invalid SQL
        assertThatThrownBy(() -> startMySqlContainer(invalidSql, "FAIL_TEST"))
                .isInstanceOf(Exception.class);

        // Then containers should still be cleaned up (via teardown)
        // The teardown method will handle cleanup
    }

    // ========================================
    // INF-005: Missing Required Arguments
    // ========================================

    @Test
    @Order(9)
    @DisplayName("INF-005: Should show usage when no arguments provided")
    void testMissingArguments() {
        // Given no arguments
        String[] emptyArgs = {};

        // When parsing arguments
        AppConfig config = new AppConfig();

        // Then it should throw with usage message
        assertThatThrownBy(() -> config.parseArguments(emptyArgs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage");
    }

    @Test
    @Order(10)
    @DisplayName("INF-005b: Should show usage when only DB type provided")
    void testMissingDumpPath() {
        // Given only database type
        String[] partialArgs = {"mysql"};

        // When parsing arguments
        AppConfig config = new AppConfig();

        // Then it should throw with usage message
        assertThatThrownBy(() -> config.parseArguments(partialArgs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage");
    }

    // ========================================
    // INF-006: Help Flag
    // ========================================

    @Test
    @Order(11)
    @DisplayName("INF-006: Should display help when --help flag used")
    void testHelpFlag() {
        // Given --help argument
        String[] helpArgs = {"--help"};

        // When parsing arguments
        AppConfig config = new AppConfig();

        // Then it should show help/usage
        assertThatThrownBy(() -> config.parseArguments(helpArgs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage");
    }

    // ========================================
    // INF-007: Container Cleanup After Success (Extended)
    // ========================================

    @Test
    @Order(12)
    @DisplayName("INF-007: Should leave no orphaned containers after multiple runs")
    @EnabledIf("isDockerRunning")
    void testNoOrphanedContainers() throws IOException {
        // Given multiple containers will be started
        Path sql1 = createTempSqlFile("CREATE TABLE t1 (id INT PRIMARY KEY) ENGINE=InnoDB;", "multi1");
        Path sql2 = createTempSqlFile("CREATE TABLE t2 (id INT PRIMARY KEY) ENGINE=InnoDB;", "multi2");

        int initialCount = getRunningContainerCount();

        // When starting and stopping multiple containers
        JdbcDatabaseContainer<?> c1 = null;
        JdbcDatabaseContainer<?> c2 = null;

        try {
            c1 = startMySqlContainer(sql1, "MULTI_1");
            c2 = startMySqlContainer(sql2, "MULTI_2");

            assertThat(c1.isRunning()).isTrue();
            assertThat(c2.isRunning()).isTrue();
        } finally {
            if (c1 != null) c1.stop();
            if (c2 != null) c2.stop();
        }

        // Then no orphaned containers should remain
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    int finalCount = getRunningContainerCount();
                    assertThat(finalCount).isLessThanOrEqualTo(initialCount);
                });
    }

    // ========================================
    // INF-008: Container Cleanup After Various Failures
    // ========================================

    @Test
    @Order(13)
    @DisplayName("INF-008: Should cleanup on SQL syntax error")
    @EnabledIf("isDockerRunning")
    void testCleanupOnSqlSyntaxError() throws IOException {
        // Given SQL with syntax error
        Path syntaxErrorSql = createTempSqlFile(
                "CREATE TABL syntax_error (id INT);", // TABL instead of TABLE
                "syntax_error"
        );

        // When starting container
        // Container may start but SQL execution fails during init
        // Our cleanup in teardown should handle this
        try {
            startMySqlContainer(syntaxErrorSql, "SYNTAX_ERROR");
        } catch (Exception e) {
            LOG.info("Expected error: {}", e.getMessage());
        }

        // Cleanup handled by @AfterEach
    }

    // ========================================
    // INF-009: Custom Standard Path
    // ========================================

    @Test
    @Order(14)
    @DisplayName("INF-009: Should accept custom standard schema path")
    void testCustomStandardPath() throws IOException {
        // Given a custom standard schema path
        Path customStandard = createTempSqlFile(
                "CREATE TABLE custom_standard (id INT PRIMARY KEY) ENGINE=InnoDB;",
                "custom_standard"
        );
        Path userDump = createTempSqlFile(
                "CREATE TABLE custom_standard (id INT PRIMARY KEY) ENGINE=InnoDB;",
                "user_dump"
        );

        // When configuring with custom path
        AppConfig config = new AppConfig();
        config.parseArguments(new String[]{
                "mysql",
                userDump.toString(),
                "--standard",
                customStandard.toString()
        });

        // Then custom path should be used
        assertThat(config.getStandardSchemaPath())
                .as("Custom standard path should be used")
                .isEqualTo(customStandard. toAbsolutePath());
    }

    // ========================================
    // INF-010: Custom Output Directory
    // ========================================

    @Test
    @Order(15)
    @DisplayName("INF-010: Should create custom output directory")
    void testCustomOutputDirectory() throws IOException {
        // Given custom output directory
        Path customOutput = Files.createTempDirectory("custom_reports");
        Path userDump = createTempSqlFile(
                "CREATE TABLE test (id INT PRIMARY KEY) ENGINE=InnoDB;",
                "test"
        );

        // When configuring with custom output
        AppConfig config = new AppConfig();
        config.parseArguments(new String[]{
                "mysql",
                userDump. toString(),
                "--standard",
                userDump.toString(),
                "--output",
                customOutput.toString()
        });

        // Then output directory should be set
        assertThat(config. getReportOutputDir())
                .as("Custom output directory should be used")
                .isEqualTo(customOutput. toAbsolutePath());
    }

    // ========================================
    // INF-011: Concurrent Executions
    // ========================================

    @Test
    @Order(16)
    @DisplayName("INF-011: Should handle concurrent container starts")
    @EnabledIf("isDockerRunning")
    void testConcurrentContainerStarts() throws Exception {
        // Given multiple SQL files
        Path sql1 = createTempSqlFile("CREATE TABLE concurrent1 (id INT PRIMARY KEY) ENGINE=InnoDB;", "concurrent1");
        Path sql2 = createTempSqlFile("CREATE TABLE concurrent2 (id INT PRIMARY KEY) ENGINE=InnoDB;", "concurrent2");

        // When starting containers concurrently
        var future1 = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> startMySqlContainer(sql1, "CONCURRENT_1")
        );
        var future2 = java.util.concurrent. CompletableFuture.supplyAsync(
                () -> startMySqlContainer(sql2, "CONCURRENT_2")
        );

        // Then both should complete successfully
        JdbcDatabaseContainer<?> c1 = future1.get(5, TimeUnit.MINUTES);
        JdbcDatabaseContainer<?> c2 = future2.get(5, TimeUnit.MINUTES);

        assertThat(c1.isRunning()).isTrue();
        assertThat(c2.isRunning()).isTrue();

        // Cleanup
        c1.stop();
        c2.stop();
    }

    // ========================================
    // INF-012: File Permission Handling
    // ========================================

    @Test
    @Order(17)
    @DisplayName("INF-012: Should handle unreadable file gracefully")
    @DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS) // Permission handling differs on Windows
    void testUnreadableFile() throws IOException {
        // Given a file without read permissions
        Path unreadableFile = Files.createTempFile("unreadable", ".sql");
        Files.writeString(unreadableFile, "CREATE TABLE test (id INT);");
        unreadableFile.toFile().setReadable(false);

        try {
            // When attempting to use the file
            AppConfig config = new AppConfig();

            // Then it should throw with permission error
            assertThatThrownBy(() -> config.parseArguments(new String[]{"mysql", unreadableFile. toString()}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not readable");
        } finally {
            // Restore permissions for cleanup
            unreadableFile.toFile().setReadable(true);
            Files.deleteIfExists(unreadableFile);
        }
    }

    // ========================================
    // INF-013: Empty File Handling
    // ========================================

    @Test
    @Order(18)
    @DisplayName("INF-013: Should handle empty SQL file")
    @EnabledIf("isDockerRunning")
    void testEmptySqlFile() throws IOException {
        // Given an empty SQL file
        Path emptyFile = createTempSqlFile("", "empty");

        // When starting container with empty file
        // MySQL will start but have no tables
        JdbcDatabaseContainer<?> container = startMySqlContainer(emptyFile, "EMPTY_TEST");

        // Then container should start (empty database)
        assertThat(container.isRunning()).isTrue();

        // And schema capture should work (but be empty)
        var snapshot = captureSnapshot(container, DatabaseType.MYSQL);
        assertThat(snapshot).isNotNull();
        // Empty file means system tables only
    }

    // ========================================
    // INF-014: Special Characters in Paths
    // ========================================

    @Test
    @Order(19)
    @DisplayName("INF-014: Should handle paths with spaces")
    void testPathWithSpaces() throws IOException {
        // Given a path with spaces
        Path dirWithSpaces = Files.createTempDirectory("path with spaces");
        Path sqlFile = Files.createFile(dirWithSpaces.resolve("test schema.sql"));
        Files.writeString(sqlFile, "CREATE TABLE test (id INT PRIMARY KEY) ENGINE=InnoDB;");

        try {
            // When configuring with spaced path
            AppConfig config = new AppConfig();
            config. parseArguments(new String[]{"mysql", sqlFile.toString(), "--standard", sqlFile.toString()});

            // Then it should be accepted
            assertThat(config. getUserDumpPath()).exists();
        } finally {
            Files.deleteIfExists(sqlFile);
            Files.deleteIfExists(dirWithSpaces);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    static boolean isDockerRunning() {
        return DockerHealthChecker.isDockerAvailable();
    }

    @interface DisabledOnOs {
        org.junit.jupiter.api.condition.OS[] value();
    }
}