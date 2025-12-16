package com.wso2.migration.checker.inspector;

import com.wso2.migration.checker.container.DatabaseType;
import com.wso2.migration.checker.inspector.dialect.*;
import com.wso2.migration.checker.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schemacrawler.inclusionrule.RegularExpressionInclusionRule;
import schemacrawler.schema.*;
import schemacrawler.schemacrawler.*;
import schemacrawler.tools.utility.SchemaCrawlerUtility;
import us.fatehi.utility.datasource.DatabaseConnectionSources;
import us.fatehi.utility.datasource.UserCredentials;
import us.fatehi.utility.datasource.DatabaseConnectionSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inspects database schemas using SchemaCrawler and custom dialect queries.
 * Combines SchemaCrawler's table/column extraction with dialect-specific
 * stored logic extraction.
 */
public class SchemaInspector {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInspector.class);

    private final DatabaseType databaseType;
    private final DatabaseDialect dialect;

    public SchemaInspector(DatabaseType databaseType) {
        this.databaseType = databaseType;
        this.dialect = createDialect(databaseType);
    }

    private DatabaseDialect createDialect(DatabaseType type) {
        return switch (type) {
            case MYSQL -> new MySqlDialect();
            case ORACLE -> new OracleDialect();
            case MSSQL -> new MsSqlDialect();
            case POSTGRESQL -> new MySqlDialect(); // PostgreSQL uses similar INFORMATION_SCHEMA
        };
    }

    /**
     * Creates SchemaCrawler options for maximum schema extraction.
     */
    private SchemaCrawlerOptions createCrawlerOptions() {
        LimitOptions limitOptions = LimitOptionsBuilder.builder()
                .includeSchemas(new RegularExpressionInclusionRule(".*"))
                .includeTables(new RegularExpressionInclusionRule(".*"))
                .includeRoutines(new RegularExpressionInclusionRule(".*"))
                .tableTypes("TABLE", "VIEW", "SYSTEM TABLE")
                .toOptions();

        LoadOptions loadOptions = LoadOptionsBuilder.builder()
                .withSchemaInfoLevel(SchemaInfoLevelBuilder.maximum())
                .toOptions();

        return SchemaCrawlerOptionsBuilder.newSchemaCrawlerOptions()
                .withLimitOptions(limitOptions)
                .withLoadOptions(loadOptions);
    }

    /**
     * Captures a complete snapshot of the database schema.
     *
     * @param jdbcUrl  JDBC connection URL
     * @param username Database username
     * @param password Database password
     * @return Complete schema snapshot
     */
    public SchemaSnapshot captureSnapshot(String jdbcUrl, String username, String password) {
        LOG.info("📸 Capturing schema snapshot from: {}", jdbcUrl);

        DatabaseConnectionSource dataSource = DatabaseConnectionSources.newDatabaseConnectionSource(jdbcUrl, new UserCredentials() {
            @Override
            public void clearPassword() {}
            @Override
            public String getPassword() { return password; }
            @Override
            public String getUser() { return username; }
            @Override
            public boolean hasPassword() { return password != null; }
            @Override
            public boolean hasUser() { return username != null; }
        });

        try (Connection connection = dataSource.get()) {
            // Get schema name
            String schemaName = dialect.getDefaultSchema(connection);
            LOG.info("   Schema: {}", schemaName);

            // 1. Use SchemaCrawler for tables, columns, indexes, constraints
            Catalog catalog = SchemaCrawlerUtility.getCatalog(dataSource, createCrawlerOptions());

            List<TableInfo> tables = extractTables(catalog);
            LOG.info("   ✓ Extracted {} tables", tables.size());

            // 2. Use dialect-specific queries for stored logic
            List<ViewInfo> views = dialect.extractViews(connection, schemaName);
            LOG.info("   ✓ Extracted {} views", views.size());

            List<RoutineInfo> routines = dialect.extractRoutines(connection, schemaName);
            LOG.info("   ✓ Extracted {} routines", routines.size());

            List<TriggerInfo> triggers = dialect.extractTriggers(connection, schemaName);
            LOG.info("   ✓ Extracted {} triggers", triggers.size());

            List<SequenceInfo> sequences = dialect.extractSequences(connection, schemaName);
            LOG.info("   ✓ Extracted {} sequences", sequences.size());

            // Build metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("jdbcUrl", jdbcUrl);
            metadata.put("databaseProductName", connection.getMetaData().getDatabaseProductName());
            metadata.put("databaseProductVersion", connection.getMetaData().getDatabaseProductVersion());
            metadata.put("driverName", connection.getMetaData().getDriverName());

            return new SchemaSnapshot(
                    connection.getMetaData().getDatabaseProductName(),
                    databaseType.getCode(),
                    schemaName,
                    Instant.now(),
                    tables,
                    views,
                    routines,
                    triggers,
                    sequences,
                    metadata
            );

        } catch (SQLException e) {
            LOG.error("❌ Failed to capture schema snapshot: {}", e.getMessage());
            throw new RuntimeException("Schema inspection failed", e);
        }
    }

    /**
     * Extracts table information from SchemaCrawler catalog.
     */
    private List<TableInfo> extractTables(Catalog catalog) {
        List<TableInfo> tables = new ArrayList<>();

        for (Table table : catalog.getTables()) {
            // Skip views - we handle them separately
            if (table instanceof View) continue;

            List<ColumnInfo> columns = extractColumns(table);
            List<IndexInfo> indexes = extractIndexes(table);
            List<ConstraintInfo> constraints = extractConstraints(table);

            String tableType = table.getTableType() != null ? table.getTableType().getTableType() : "TABLE";
            String engine = table.getAttribute("ENGINE", "");
            String comment = table.getRemarks();

            tables.add(new TableInfo(
                    table.getName(),
                    table.getSchema().getName(),
                    columns,
                    indexes,
                    constraints,
                    tableType,
                    engine,
                    comment
            ));
        }

        return tables;
    }

    /**
     * Extracts column information from a table.
     */
    private List<ColumnInfo> extractColumns(Table table) {
        List<ColumnInfo> columns = new ArrayList<>();

        for (Column column : table.getColumns()) {
            ColumnDataType dataType = column.getColumnDataType();

            columns.add(new ColumnInfo(
                    column.getName(),
                    dataType != null ? dataType.getName() : "UNKNOWN",
                    column.getSize(),
                    column.getDecimalDigits(),
                    column.isNullable(),
                    column.getDefaultValue(),
                    column.isPartOfPrimaryKey(),
                    column.isPartOfForeignKey(),
                    column.isAutoIncremented(),
                    column.getOrdinalPosition()
            ));
        }

        return columns.stream()
                .sorted(Comparator.comparingInt(ColumnInfo::ordinalPosition))
                .collect(Collectors.toList());
    }

    /**
     * Extracts index information from a table.
     */
    private List<IndexInfo> extractIndexes(Table table) {
        List<IndexInfo> indexes = new ArrayList<>();

        for (Index index : table.getIndexes()) {
            List<String> columnNames = index.getColumns().stream()
                    .map(IndexColumn::getName)
                    .collect(Collectors.toList());

            indexes.add(new IndexInfo(
                    index.getName(),
                    table.getName(),
                    columnNames,
                    index.isUnique(),
                    false, // SchemaCrawler doesn't expose clustered info directly
                    index.getIndexType() != null ? index.getIndexType().name() : "BTREE"
            ));
        }

        return indexes;
    }

    /**
     * Extracts constraint information from a table.
     */
    private List<ConstraintInfo> extractConstraints(Table table) {
        List<ConstraintInfo> constraints = new ArrayList<>();

        // Primary Key
        PrimaryKey pk = table.getPrimaryKey();
        if (pk != null) {
            List<String> pkColumns = pk.getConstrainedColumns().stream()
                    .map(NamedObject::getName)
                    .collect(Collectors.toList());

            constraints.add(new ConstraintInfo(
                    pk.getName(),
                    table.getName(),
                    ConstraintInfo.ConstraintType.PRIMARY_KEY,
                    pkColumns,
                    null, null, null, null, null
            ));
        }

        // Foreign Keys
        for (ForeignKey fk : table.getForeignKeys()) {
            List<String> fkColumns = new ArrayList<>();
            List<String> refColumns = new ArrayList<>();
            String refTable = null;

            for (var ref : fk.getColumnReferences()) {
                fkColumns.add(ref.getForeignKeyColumn().getName());
                refColumns.add(ref.getPrimaryKeyColumn().getName());
                if (refTable == null) {
                    refTable = ref.getPrimaryKeyColumn().getParent().getName();
                }
            }

            constraints.add(new ConstraintInfo(
                    fk.getName(),
                    table.getName(),
                    ConstraintInfo.ConstraintType.FOREIGN_KEY,
                    fkColumns,
                    refTable,
                    refColumns,
                    fk.getDeleteRule() != null ? fk.getDeleteRule().name() : null,
                    fk.getUpdateRule() != null ? fk.getUpdateRule().name() : null,
                    null
            ));
        }

        // Unique constraints from indexes
        for (Index index : table.getIndexes()) {
            if (index.isUnique() && !isPrimaryKeyIndex(index, pk)) {
                List<String> uniqueColumns = index.getColumns().stream()
                        .map(IndexColumn::getName)
                        .collect(Collectors.toList());

                constraints.add(new ConstraintInfo(
                        index.getName(),
                        table.getName(),
                        ConstraintInfo.ConstraintType.UNIQUE,
                        uniqueColumns,
                        null, null, null, null, null
                ));
            }
        }

        return constraints;
    }

    private boolean isPrimaryKeyIndex(Index index, PrimaryKey pk) {
        if (pk == null) return false;
        return index.getName().equals(pk.getName());
    }
}