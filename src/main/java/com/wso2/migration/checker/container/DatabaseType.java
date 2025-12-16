package com.wso2.migration. checker.container;

/**
 * Supported database types with their specific configurations.
 */
public enum DatabaseType {
    MYSQL("mysql", "MySQL", 3306, "/docker-entrypoint-initdb. d/init.sql"),
    ORACLE("oracle", "Oracle", 1521, "/container-entrypoint-initdb.d/init.sql"),
    MSSQL("mssql", "Microsoft SQL Server", 1433, null),
    POSTGRESQL("postgresql", "PostgreSQL", 5432, "/docker-entrypoint-initdb.d/init.sql");

    private final String code;
    private final String displayName;
    private final int defaultPort;
    private final String initScriptPath;

    DatabaseType(String code, String displayName, int defaultPort, String initScriptPath) {
        this.code = code;
        this.displayName = displayName;
        this.defaultPort = defaultPort;
        this.initScriptPath = initScriptPath;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public int getDefaultPort() { return defaultPort; }
    public String getInitScriptPath() { return initScriptPath; }

    public static DatabaseType fromCode(String code) {
        for (DatabaseType type : values()) {
            if (type. code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown database type: " + code);
    }
}