package com.wso2.migration.checker.config;

import com.wso2.migration.checker.container.DatabaseType;

import java.io.IOException;
import java.io.InputStream;
import java. nio.file.Files;
import java.nio.file.Path;
import java.nio.file. Paths;
import java.util. Properties;

/**
 * Application configuration loaded from properties file and/or CLI arguments.
 */
public class AppConfig {

    private static final String DEFAULT_CONFIG_FILE = "config/application.properties";
    private static final String DEFAULT_STANDARDS_DIR = "standards";
    private static final String DEFAULT_REPORTS_DIR = "reports";

    private final Properties properties;

    // Core settings
    private DatabaseType databaseType;
    private Path standardSchemaPath;
    private Path userDumpPath;
    private Path reportOutputDir;

    // Feature flags
    private boolean includeViews = true;
    private boolean includeTriggers = true;
    private boolean includeSequences = true;
    private boolean includeRoutineDefinitions = true;

    public AppConfig() {
        this.properties = new Properties();
        loadDefaultProperties();
    }

    private void loadDefaultProperties() {
        Path configPath = Paths.get(DEFAULT_CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                properties.load(is);
            } catch (IOException e) {
                // Use defaults
            }
        }

        // Set defaults from properties
        this.reportOutputDir = Paths.get(
                properties.getProperty("report. output.dir", DEFAULT_REPORTS_DIR));
        this.includeViews = Boolean.parseBoolean(
                properties.getProperty("inspection.include.views", "true"));
        this.includeTriggers = Boolean.parseBoolean(
                properties.getProperty("inspection. include.triggers", "true"));
        this.includeSequences = Boolean.parseBoolean(
                properties.getProperty("inspection.include.sequences", "true"));
        this.includeRoutineDefinitions = Boolean.parseBoolean(
                properties.getProperty("report.include.definitions", "true"));
    }

    /**
     * Parses command-line arguments and configures the application.
     *
     * Usage: java -jar checker.jar <db-type> <user-dump-path> [options]
     *
     * Options:
     *   --standard <path>    Path to standard schema SQL file
     *   --output <dir>       Output directory for reports
     *   --no-views           Skip view comparison
     *   --no-triggers        Skip trigger comparison
     */
    public void parseArguments(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: java -jar schema-compliance-checker.jar <db-type> <user-dump-path> [options]\n" +
                            "\n" +
                            "Database Types:  MYSQL, ORACLE, MSSQL, POSTGRESQL\n" +
                            "\n" +
                            "Options:\n" +
                            "  --standard <path>    Path to standard schema SQL file (default: standards/<db-type>/golden_schema.sql)\n" +
                            "  --output <dir>       Output directory for reports (default: reports/)\n" +
                            "  --no-views           Skip view comparison\n" +
                            "  --no-triggers        Skip trigger comparison\n" +
                            "  --no-sequences       Skip sequence comparison\n" +
                            "\n" +
                            "Example:\n" +
                            "  java -jar schema-compliance-checker.jar mysql /path/to/user_dump.sql\n" +
                            "  java -jar schema-compliance-checker.jar oracle dump. sql --standard /path/to/golden. sql"
            );
        }

        // Parse database type
        this.databaseType = DatabaseType. fromCode(args[0]);

        // Parse user dump path
        this.userDumpPath = Paths.get(args[1]).toAbsolutePath();
        validatePath(userDumpPath, "User dump file");

        // Default standard schema path
        this.standardSchemaPath = Paths.get(DEFAULT_STANDARDS_DIR,
                databaseType.getCode(), "golden_schema.sql").toAbsolutePath();

        // Parse optional arguments
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--standard" -> {
                    if (i + 1 < args.length) {
                        this.standardSchemaPath = Paths.get(args[++i]).toAbsolutePath();
                    }
                }
                case "--output" -> {
                    if (i + 1 < args.length) {
                        this.reportOutputDir = Paths.get(args[++i]).toAbsolutePath();
                    }
                }
                case "--no-views" -> this.includeViews = false;
                case "--no-triggers" -> this.includeTriggers = false;
                case "--no-sequences" -> this.includeSequences = false;
                case "--help", "-h" -> {
                    parseArguments(new String[]{}); // Trigger usage message
                }
            }
        }

        validatePath(standardSchemaPath, "Standard schema file");
    }

    private void validatePath(Path path, String description) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(description + " not found: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(description + " is not readable: " + path);
        }
    }

    /**
     * Resolves the standard schema path for the configured database type.
     */
    public Path resolveStandardSchemaPath() {
        if (standardSchemaPath != null && Files.exists(standardSchemaPath)) {
            return standardSchemaPath;
        }

        // Try default location
        Path defaultPath = Paths.get(DEFAULT_STANDARDS_DIR,
                databaseType.getCode(), "golden_schema.sql");

        if (Files.exists(defaultPath)) {
            return defaultPath. toAbsolutePath();
        }

        throw new IllegalStateException(
                "Standard schema not found. Please place it at: " + defaultPath. toAbsolutePath() +
                        " or specify with --standard <path>");
    }

    // Getters
    public DatabaseType getDatabaseType() { return databaseType; }
    public Path getStandardSchemaPath() { return standardSchemaPath; }
    public Path getUserDumpPath() { return userDumpPath; }
    public Path getReportOutputDir() { return reportOutputDir; }
    public boolean isIncludeViews() { return includeViews; }
    public boolean isIncludeTriggers() { return includeTriggers; }
    public boolean isIncludeSequences() { return includeSequences; }
    public boolean isIncludeRoutineDefinitions() { return includeRoutineDefinitions; }

    // Fluent setters for programmatic configuration
    public AppConfig withDatabaseType(DatabaseType type) {
        this.databaseType = type;
        return this;
    }

    public AppConfig withStandardSchemaPath(Path path) {
        this.standardSchemaPath = path;
        return this;
    }

    public AppConfig withUserDumpPath(Path path) {
        this.userDumpPath = path;
        return this;
    }

    public AppConfig withReportOutputDir(Path path) {
        this.reportOutputDir = path;
        return this;
    }
}