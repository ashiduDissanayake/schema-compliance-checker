package com.wso2.migration.checker.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers. MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org. testcontainers.containers.PostgreSQLContainer;
import org. testcontainers.utility.MountableFile;

import java. nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Factory for creating and configuring database containers with SQL dump injection.
 */
public final class ContainerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerFactory.class);

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String ORACLE_IMAGE = "gvenzl/oracle-xe: 21-slim";
    private static final String MSSQL_IMAGE = "mcr.microsoft.com/mssql/server:2022-latest";
    private static final String POSTGRESQL_IMAGE = "postgres: 16";

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    private ContainerFactory() {
        // Utility class
    }

    /**
     * Creates and starts a database container with the specified SQL dump.
     *
     * @param type         The database type
     * @param sqlDumpPath  Path to the SQL dump file
     * @param containerName Name identifier for logging
     * @return Started container ready for connections
     */
    public static JdbcDatabaseContainer<? > createAndStart(
            DatabaseType type,
            Path sqlDumpPath,
            String containerName) {

        LOG.info("🐳 Initializing {} container [{}].. .", type.getDisplayName(), containerName);
        validateDumpFile(sqlDumpPath);

        JdbcDatabaseContainer<?> container = switch (type) {
            case MYSQL -> createMySqlContainer(sqlDumpPath);
            case ORACLE -> createOracleContainer(sqlDumpPath);
            case MSSQL -> createMsSqlContainer(sqlDumpPath);
            case POSTGRESQL -> createPostgreSqlContainer(sqlDumpPath);
        };

        container.withStartupTimeout(STARTUP_TIMEOUT);

        LOG.info("   ⏳ Starting container with dump: {}", sqlDumpPath. getFileName());
        long startTime = System.currentTimeMillis();
        container.start();
        long elapsed = System.currentTimeMillis() - startTime;

        LOG.info("   ✅ {} container ready in {}ms", type.getDisplayName(), elapsed);
        LOG.info("   📍 JDBC URL: {}", container.getJdbcUrl());

        return container;
    }

    private static void validateDumpFile(Path path) {
        if (!Files. exists(path)) {
            throw new IllegalArgumentException("SQL dump file not found: " + path. toAbsolutePath());
        }
        if (! Files.isReadable(path)) {
            throw new IllegalArgumentException("SQL dump file not readable: " + path.toAbsolutePath());
        }
    }

    @SuppressWarnings("resource")
    private static MySQLContainer<? > createMySqlContainer(Path sqlDumpPath) {
        return new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName("compliance_check")
                .withUsername("checker")
                .withPassword("checker_pass")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(sqlDumpPath),
                        "/docker-entrypoint-initdb.d/init.sql"
                )
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--default-authentication-plugin=mysql_native_password"
                );
    }

    @SuppressWarnings("resource")
    private static OracleContainer createOracleContainer(Path sqlDumpPath) {
        return new OracleContainer(ORACLE_IMAGE)
                .withDatabaseName("compliance_check")
                .withUsername("checker")
                .withPassword("checker_pass")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(sqlDumpPath),
                        "/container-entrypoint-initdb. d/init.sql"
                );
    }

    @SuppressWarnings("resource")
    private static MSSQLServerContainer<? > createMsSqlContainer(Path sqlDumpPath) {
        return new MSSQLServerContainer<>(MSSQL_IMAGE)
                .acceptLicense()
                .withPassword("Checker_Pass1!")
                .withInitScript(sqlDumpPath.toString());
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> createPostgreSqlContainer(Path sqlDumpPath) {
        return new PostgreSQLContainer<>(POSTGRESQL_IMAGE)
                .withDatabaseName("compliance_check")
                .withUsername("checker")
                .withPassword("checker_pass")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(sqlDumpPath),
                        "/docker-entrypoint-initdb.d/init.sql"
                );
    }

    /**
     * Extracts connection details from a running container.
     */
    public static ConnectionInfo getConnectionInfo(JdbcDatabaseContainer<?> container) {
        return new ConnectionInfo(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                container.getHost(),
                container.getFirstMappedPort()
        );
    }

    public record ConnectionInfo(
            String jdbcUrl,
            String username,
            String password,
            String host,
            int port
    ) {}
}