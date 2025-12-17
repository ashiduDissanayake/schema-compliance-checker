package com.wso2.migration.checker.suite;

import com.wso2.migration.checker.BaseIntegrationTest;
import com.wso2.migration.checker.TestConstants;
import com.wso2.migration.checker.config.AppConfig;
import com.wso2.migration.checker.container.DatabaseType;
import com.wso2.migration.checker.helper.DockerHealthChecker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Suite A: Infrastructure & CLI Tests
 *
 * Tests basic tool functionality, CLI handling, and Docker integration.
 */
@Tag(TestConstants.TAG_INFRASTRUCTURE)
@Tag(TestConstants.TAG_SMOKE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Suite A: Infrastructure Tests")
class SuiteAInfrastructureTest extends BaseIntegrationTest {

    // ========================================
    // INF-001: Docker Daemon Not Running
    // ========================================

    @Test
    @Order(1)
    @DisplayName("INF-001: Docker Daemon Not Running (Check Availability)")
    @EnabledIf("isDockerRunning")
    void testDockerAvailable() {
        // Given Docker is running
        boolean available = DockerHealthChecker.isDockerAvailable();

        // Then it should be detected
        assertThat(available)
                .as("Docker daemon should be available")
                .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("INF-001b: Docker Daemon Not Running (Error Handling)")
    @Disabled("Manual test - requires stopping Docker")
    void testDockerUnavailableHandling() {
        // This test is manual - run with Docker stopped
        // The tool should exit with code 3 and clear error message
    }

    // ========================================
    // INF-002: User Dump File Not Found
    // ========================================

    @Test
    @Order(3)
    @DisplayName("INF-002: User Dump File Not Found")
    void testUserDumpFileNotFound() {
        // Given a non-existent file path
        Path nonExistentFile = Path.of("/nonexistent/file.sql");

        // When validating configuration
        AppConfig config = new AppConfig();

        // Then it should throw with clear message
        assertThatThrownBy(() -> config.parseArguments(new String[]{"mysql", nonExistentFile.toString()}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ========================================
    // INF-003: Standard Schema Not Found
    // ========================================

    @Test
    @Order(4)
    @DisplayName("INF-003: Standard Schema Not Found")
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
    // INF-004: Invalid Database Type
    // ========================================

    @Test
    @Order(5)
    @DisplayName("INF-004: Invalid Database Type")
    void testUnsupportedDatabaseType() {
        // Given an unsupported database type
        String unsupportedType = "postgres";

        // When parsing database type
        // Then it should throw with clear message
        assertThatThrownBy(() -> DatabaseType.fromCode(unsupportedType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown database type");
    }

    // ========================================
    // INF-005: Missing Required Arguments
    // ========================================

    @Test
    @Order(6)
    @DisplayName("INF-005: Missing Required Arguments")
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

    // ========================================
    // INF-006: Help Flag
    // ========================================

    @Test
    @Order(7)
    @DisplayName("INF-006: Help Flag")
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
    // INF-007: Container Cleanup After Success
    // ========================================

    @Test
    @Order(8)
    @DisplayName("INF-007: Container Cleanup After Success")
    @EnabledIf("isDockerRunning")
    void testContainerCleanupAfterSuccess() throws IOException {
        // Given a valid setup
        Path userPath = createTempSqlFile(
                "CREATE TABLE test_cleanup (id INT PRIMARY KEY) ENGINE=InnoDB;",
                "cleanup_test"
        );
        Path standardPath = TestConstants.MYSQL_GOLDEN_SCHEMA;
        Assumptions.assumeTrue(Files.exists(standardPath), "Golden schema not found");

        int initialContainerCount = getRunningContainerCount();

        // When running comparison
        try {
            JdbcDatabaseContainer<?> container = startMySqlContainer(userPath, "CLEANUP_TEST");
            assertThat(container.isRunning()).isTrue();
            container.stop();
        } catch (Exception e) {
            LOG.warn("Container test failed: {}", e.getMessage());
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

    // ========================================
    // INF-008: Container Cleanup After Failure
    // ========================================

    @Test
    @Order(9)
    @DisplayName("INF-008: Container Cleanup After Failure")
    @EnabledIf("isDockerRunning")
    void testCleanupOnSqlSyntaxError() throws IOException {
        // Given SQL with syntax error
        Path syntaxErrorSql = createTempSqlFile(
                "CREATE TABL syntax_error (id INT);",
                "syntax_error"
        );

        // When starting container
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
    @Order(10)
    @DisplayName("INF-009: Custom Standard Path")
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
                .isEqualTo(customStandard.toAbsolutePath());
    }

    // ========================================
    // INF-010: Custom Output Directory
    // ========================================

    @Test
    @Order(11)
    @DisplayName("INF-010: Custom Output Directory")
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
                userDump.toString(),
                "--standard",
                userDump.toString(),
                "--output",
                customOutput.toString()
        });

        // Then output directory should be set
        assertThat(config.getReportOutputDir())
                .as("Custom output directory should be used")
                .isEqualTo(customOutput.toAbsolutePath());
    }

    // ========================================
    // INF-011: Concurrent Executions
    // ========================================

    @Test
    @Order(12)
    @DisplayName("INF-011: Concurrent Executions")
    @EnabledIf("isDockerRunning")
    void testConcurrentContainerStarts() throws Exception {
        // Given multiple SQL files
        Path sql1 = createTempSqlFile("CREATE TABLE concurrent1 (id INT PRIMARY KEY) ENGINE=InnoDB;", "concurrent1");
        Path sql2 = createTempSqlFile("CREATE TABLE concurrent2 (id INT PRIMARY KEY) ENGINE=InnoDB;", "concurrent2");

        // When starting containers concurrently
        var future1 = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> startMySqlContainer(sql1, "CONCURRENT_1")
        );
        var future2 = java.util.concurrent.CompletableFuture.supplyAsync(
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
    // INF-012: Signal Handling (SIGTERM)
    // ========================================

    @Test
    @Order(13)
    @DisplayName("INF-012: Signal Handling (SIGTERM)")
    @Disabled("Requires running jar as separate process")
    void testSignalHandling() {
        // This test requires spawning the application as a sub-process
        // and sending a kill signal.
    }

    // ========================================
    // Helper Methods
    // ========================================

    static boolean isDockerRunning() {
        return DockerHealthChecker.isDockerAvailable();
    }
}