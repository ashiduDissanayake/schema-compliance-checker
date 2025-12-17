package com.wso2.migration.checker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java. time.Duration;

/**
 * Constants used across all test suites.
 */
public final class TestConstants {

    private TestConstants() {}

    // Test resource paths
    public static final Path TEST_RESOURCES_PATH = Paths.get(
            System.getProperty("test.resources.path", "src/test/resources"));

    public static final Path STANDARDS_PATH = TEST_RESOURCES_PATH.resolve("standards");
    public static final Path USER_DUMPS_PATH = TEST_RESOURCES_PATH.resolve("user-dumps");
    public static final Path INVALID_FILES_PATH = TEST_RESOURCES_PATH.resolve("invalid");
    public static final Path TEST_REPORTS_PATH = TEST_RESOURCES_PATH.resolve("test-reports");

    // MySQL paths
    public static final Path MYSQL_GOLDEN_SCHEMA = STANDARDS_PATH.resolve("mysql/golden_schema.sql");
    public static final Path MYSQL_USER_DUMPS = USER_DUMPS_PATH.resolve("mysql");

    // Docker configuration
    public static final String MYSQL_IMAGE = "mysql:8.0";
    public static final Duration CONTAINER_STARTUP_TIMEOUT = Duration.ofMinutes(3);
    public static final Duration CONTAINER_STOP_TIMEOUT = Duration.ofSeconds(30);

    // Test tags
    public static final String TAG_INFRASTRUCTURE = "infrastructure";
    public static final String TAG_MYSQL_FUNCTIONAL = "mysql-functional";
    public static final String TAG_SMOKE = "smoke";
    public static final String TAG_TABLES = "tables";
    public static final String TAG_COLUMNS = "columns";
    public static final String TAG_CONSTRAINTS = "constraints";
    public static final String TAG_INDEXES = "indexes";
    public static final String TAG_ROUTINES = "routines";
    public static final String TAG_TRIGGERS = "triggers";
    public static final String TAG_VIEWS = "views";
    public static final String TAG_EVENTS = "events";

    // Exit codes
    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_DRIFT_DETECTED = 1;
    public static final int EXIT_CONFIG_ERROR = 2;
    public static final int EXIT_RUNTIME_ERROR = 3;

    // Drift severities
    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_INFO = "INFO";
}