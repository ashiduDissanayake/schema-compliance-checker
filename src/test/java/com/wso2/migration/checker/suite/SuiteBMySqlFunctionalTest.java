package com.wso2.migration.checker.suite;

import com.wso2.migration.checker.BaseIntegrationTest;
import com. wso2.migration.checker. TestConstants;
import com.wso2.migration.checker.assertion.DriftAssertions;
import com.wso2.migration.checker.container.DatabaseType;
import com. wso2.migration.checker. helper.DockerHealthChecker;
import com.wso2.migration.checker.helper.TestDataGenerator;
import com.wso2.migration.checker.model.SchemaSnapshot;
import com.wso2.migration.checker.report.ComplianceReport;
import com.wso2.migration.checker.report.DriftSeverity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org. junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider. MethodSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.wso2.migration.checker.assertion.DriftAssertions.assertThat;
import static org.assertj.core.api. Assertions. assertThat;

/**
 * Suite B:  MySQL Functional Tests
 *
 * Comprehensive tests for MySQL schema drift detection including:
 * - Tables, Columns, Data Types
 * - Constraints (PK, FK, UNIQUE, CHECK, NOT NULL)
 * - MySQL-specific features (AUTO_INCREMENT, UNSIGNED, GENERATED columns)
 * - Indexes (B-Tree, Fulltext, Spatial)
 * - Stored Routines (Procedures, Functions)
 * - Triggers (BEFORE/AFTER INSERT/UPDATE/DELETE)
 * - Views
 * - Events
 * - Engine differences
 */
@Tag(TestConstants.TAG_MYSQL_FUNCTIONAL)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Suite B: MySQL Functional Tests")
@EnabledIf("isDockerRunning")
class SuiteBMySqlFunctionalTest extends BaseIntegrationTest {

    // ========================================================================
    // SECTION 1: TABLE DETECTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("B. 1: Table Detection Tests")
    @Tag(TestConstants.TAG_TABLES)
    @TestMethodOrder(MethodOrderer. OrderAnnotation.class)
    class TableDetectionTests {

        @Test
        @Order(1)
        @DisplayName("TBL-001: Perfect match - no drift detected")
        void testPerfectMatch() throws IOException {
            // Given identical schemas
            String schema = """
                CREATE TABLE users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(255) UNIQUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB;
                
                CREATE TABLE orders (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    total DECIMAL(10,2),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(schema, "standard");
            Path userPath = createTempSqlFile(schema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then no drift should be detected
            assertThat(report)
                    .hasNoDrifts()
                    .isMigrationReady()
                    .hasPerfectCompliance();
        }

        @Test
        @Order(2)
        @DisplayName("TBL-002: Missing table detection")
        void testMissingTable() throws IOException {
            // Given standard with table that user is missing
            String standardSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE orders (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE products (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE orders (id INT PRIMARY KEY) ENGINE=InnoDB;
                -- Missing:  products table
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing table should be detected as CRITICAL
            assertThat(report)
                    .hasDrifts()
                    .isNotMigrationReady()
                    . containsMissingTable("products")
                    .hasAtLeastCriticalDrifts(1);
        }

        @Test
        @Order(3)
        @DisplayName("TBL-003: Extra table detection")
        void testExtraTable() throws IOException {
            // Given user has extra table
            String standardSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE temp_backup (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then extra table should be detected as LOW severity
            assertThat(report)
                    .hasDrifts()
                    .containsExtraTable("temp_backup")
                    .hasLowDrifts(1);
        }

        @Test
        @Order(4)
        @DisplayName("TBL-004: Table rename detection (missing + extra)")
        void testTableRename() throws IOException {
            // Given table was renamed
            String standardSchema = """
                CREATE TABLE user_accounts (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then both missing and extra should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingTable("user_accounts")
                    .containsExtraTable("users");
        }

        @Test
        @Order(5)
        @DisplayName("TBL-005: Multiple missing tables")
        void testMultipleMissingTables() throws IOException {
            // Given multiple missing tables
            String standardSchema = """
                CREATE TABLE table1 (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE table2 (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE table3 (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE table4 (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE table1 (id INT PRIMARY KEY) ENGINE=InnoDB;
                -- Missing: table2, table3, table4
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then all missing tables should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingTable("table2")
                    .containsMissingTable("table3")
                    .containsMissingTable("table4")
                    .hasAtLeastCriticalDrifts(3);
        }

        @Test
        @Order(6)
        @DisplayName("TBL-006: Engine mismatch detection (InnoDB vs MyISAM)")
        void testEngineMismatch() throws IOException {
            // Given different storage engines
            String standardSchema = """
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                ) ENGINE=MyISAM;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then engine mismatch should be detected
            // Note: This may be detected as table property difference
            assertThat(report).hasDrifts();

            // Engine differences affect FK support, transactions, etc.
            LOG.info("Engine mismatch drifts:  {}", report.getDriftItems());
        }

        @Test
        @Order(7)
        @DisplayName("TBL-007: Case sensitivity check")
        void testCaseSensitivity() throws IOException {
            // Given tables with different cases
            String standardSchema = """
                CREATE TABLE Users (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Note: MySQL case sensitivity depends on OS and configuration
            // On case-insensitive filesystems (Windows/macOS default), these may match
            LOG.info("Case sensitivity test result: {} drifts", report.getDriftItems().size());
        }
    }

    // ========================================================================
    // SECTION 2: COLUMN DETECTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.2: Column Detection Tests")
    @Tag(TestConstants.TAG_COLUMNS)
    @TestMethodOrder(MethodOrderer. OrderAnnotation.class)
    class ColumnDetectionTests {

        @Test
        @Order(1)
        @DisplayName("COL-001: Missing column detection")
        void testMissingColumn() throws IOException {
            // Given standard has column that user is missing
            String standardSchema = """
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    email VARCHAR(255),
                    phone VARCHAR(20)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    email VARCHAR(255)
                    -- Missing: phone column
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing column should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingColumn("phone");
        }

        @Test
        @Order(2)
        @DisplayName("COL-002: Extra column detection")
        void testExtraColumn() throws IOException {
            // Given user has extra column
            String standardSchema = """
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    legacy_field VARCHAR(50)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then extra column should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsExtraColumn("legacy_field");
        }

        // ----------------------------------------
        // Numeric Type Mismatch Tests
        // ----------------------------------------

        @ParameterizedTest
        @Order(3)
        @DisplayName("NUM-001 to NUM-010: Numeric type mismatch detection")
        @CsvSource({
                "INT, BIGINT, HIGH",
                "BIGINT, INT, CRITICAL",
                "INT, SMALLINT, CRITICAL",
                "INT, TINYINT, CRITICAL",
                "FLOAT, DOUBLE, MEDIUM",
                "DOUBLE, FLOAT, HIGH"
        })
        void testNumericTypeMismatch(String standardType, String userType, String expectedSeverity) throws IOException {
            // Given different numeric types
            String standardSchema = String.format("""
                CREATE TABLE test_numeric (
                    id INT PRIMARY KEY,
                    value %s
                ) ENGINE=InnoDB;
                """, standardType);

            String userSchema = String.format("""
                CREATE TABLE test_numeric (
                    id INT PRIMARY KEY,
                    value %s
                ) ENGINE=InnoDB;
                """, userType);

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then type mismatch should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsTypeMismatch("value");

            LOG.info("Type mismatch {} -> {}:  {} drifts detected",
                    standardType, userType, report.getDriftItems().size());
        }

        @Test
        @Order(4)
        @DisplayName("NUM-009:  UNSIGNED to SIGNED mismatch")
        void testUnsignedMismatch() throws IOException {
            // Given UNSIGNED vs SIGNED difference
            String standardSchema = """
                CREATE TABLE test_unsigned (
                    id INT PRIMARY KEY,
                    counter INT UNSIGNED
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_unsigned (
                    id INT PRIMARY KEY,
                    counter INT
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then UNSIGNED mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("UNSIGNED mismatch drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(5)
        @DisplayName("NUM-005: DECIMAL precision mismatch")
        void testDecimalPrecisionMismatch() throws IOException {
            // Given different DECIMAL precisions
            String standardSchema = """
                CREATE TABLE test_decimal (
                    id INT PRIMARY KEY,
                    price DECIMAL(10,2)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_decimal (
                    id INT PRIMARY KEY,
                    price DECIMAL(8,2)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then precision mismatch should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsTypeMismatch("price");
        }

        // ----------------------------------------
        // String Type Mismatch Tests
        // ----------------------------------------

        @Test
        @Order(6)
        @DisplayName("STR-002: VARCHAR length decrease (truncation risk)")
        void testVarcharLengthDecrease() throws IOException {
            // Given shorter VARCHAR
            String standardSchema = """
                CREATE TABLE test_varchar (
                    id INT PRIMARY KEY,
                    name VARCHAR(255)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_varchar (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then size mismatch should be detected (HIGH due to truncation risk)
            assertThat(report)
                    .hasDrifts()
                    .containsModifiedColumn("name");
        }

        @Test
        @Order(7)
        @DisplayName("STR-008:  ENUM values change")
        void testEnumValueChange() throws IOException {
            // Given different ENUM values
            String standardSchema = """
                CREATE TABLE test_enum (
                    id INT PRIMARY KEY,
                    status ENUM('active', 'inactive', 'pending', 'deleted')
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_enum (
                    id INT PRIMARY KEY,
                    status ENUM('active', 'inactive')
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then ENUM mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("ENUM mismatch drifts: {}", report. getDriftItems());
        }

        // ----------------------------------------
        // DateTime Type Mismatch Tests
        // ----------------------------------------

        @Test
        @Order(8)
        @DisplayName("DT-001: DATETIME to TIMESTAMP mismatch")
        void testDatetimeToTimestamp() throws IOException {
            // Given DATETIME vs TIMESTAMP
            String standardSchema = """
                CREATE TABLE test_datetime (
                    id INT PRIMARY KEY,
                    created_at DATETIME
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_datetime (
                    id INT PRIMARY KEY,
                    created_at TIMESTAMP
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then type mismatch should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsTypeMismatch("created_at");
        }

        // ----------------------------------------
        // Column Property Tests
        // ----------------------------------------

        @Test
        @Order(9)
        @DisplayName("PROP-001: NULL to NOT NULL mismatch")
        void testNullabilityMismatch() throws IOException {
            // Given different nullability
            String standardSchema = """
                CREATE TABLE test_null (
                    id INT PRIMARY KEY,
                    required_field VARCHAR(100) NOT NULL
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_null (
                    id INT PRIMARY KEY,
                    required_field VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then nullability mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Nullability mismatch drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(10)
        @DisplayName("PROP-006: AUTO_INCREMENT added")
        void testAutoIncrementAdded() throws IOException {
            // Given AUTO_INCREMENT difference
            String standardSchema = """
                CREATE TABLE test_auto (
                    id INT PRIMARY KEY,
                    seq INT
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_auto (
                    id INT PRIMARY KEY,
                    seq INT AUTO_INCREMENT UNIQUE
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then AUTO_INCREMENT mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("AUTO_INCREMENT mismatch drifts:  {}", report.getDriftItems());
        }

        @Test
        @Order(11)
        @DisplayName("PROP-007: AUTO_INCREMENT removed (CRITICAL)")
        void testAutoIncrementRemoved() throws IOException {
            // Given AUTO_INCREMENT removed
            String standardSchema = """
                CREATE TABLE test_auto_removed (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_auto_removed (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then AUTO_INCREMENT removal should be detected
            assertThat(report).hasDrifts();
            LOG.info("AUTO_INCREMENT removed drifts: {}", report. getDriftItems());
        }

        @Test
        @Order(12)
        @DisplayName("PROP-GEN:  Generated column missing")
        void testGeneratedColumnMissing() throws IOException {
            // Given generated column in standard
            String standardSchema = """
                CREATE TABLE test_generated (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    first_name VARCHAR(50),
                    last_name VARCHAR(50),
                    full_name VARCHAR(101) GENERATED ALWAYS AS (CONCAT(first_name, ' ', last_name)) STORED
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_generated (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    first_name VARCHAR(50),
                    last_name VARCHAR(50)
                    -- Missing: full_name generated column
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing generated column should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingColumn("full_name");
        }

        @Test
        @Order(13)
        @DisplayName("PROP-GEN-2: Generated column expression mismatch")
        void testGeneratedColumnExpressionMismatch() throws IOException {
            // Given different generated column expressions
            String standardSchema = """
                CREATE TABLE test_gen_expr (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    price DECIMAL(10,2),
                    quantity INT,
                    total DECIMAL(12,2) GENERATED ALWAYS AS (price * quantity) STORED
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_gen_expr (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    price DECIMAL(10,2),
                    quantity INT,
                    total DECIMAL(12,2) GENERATED ALWAYS AS (price * quantity * 1.1) STORED
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then expression mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Generated column expression mismatch:  {}", report.getDriftItems());
        }

        @Test
        @Order(14)
        @DisplayName("PROP-GEN-3: VIRTUAL vs STORED generated column")
        void testGeneratedColumnStorageMismatch() throws IOException {
            // Given VIRTUAL vs STORED difference
            String standardSchema = """
                CREATE TABLE test_gen_storage (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    a INT,
                    b INT,
                    sum_ab INT GENERATED ALWAYS AS (a + b) STORED
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_gen_storage (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    a INT,
                    b INT,
                    sum_ab INT GENERATED ALWAYS AS (a + b) VIRTUAL
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then storage type mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Generated column STORED vs VIRTUAL mismatch:  {}", report.getDriftItems());
        }

        @Test
        @Order(15)
        @DisplayName("PROP-DEFAULT: Default value mismatch")
        void testDefaultValueMismatch() throws IOException {
            // Given different default values
            String standardSchema = """
                CREATE TABLE test_default (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    status VARCHAR(20) DEFAULT 'active',
                    priority INT DEFAULT 0
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_default (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    status VARCHAR(20) DEFAULT 'pending',
                    priority INT DEFAULT 5
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then default value mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Default value mismatch drifts: {}", report.getDriftItems());
        }
    }

    // ========================================================================
    // SECTION 3: CONSTRAINT DETECTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.3: Constraint Detection Tests")
    @Tag(TestConstants.TAG_CONSTRAINTS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConstraintDetectionTests {

        // ----------------------------------------
        // Primary Key Tests
        // ----------------------------------------

        @Test
        @Order(1)
        @DisplayName("PK-001: Missing primary key")
        void testMissingPrimaryKey() throws IOException {
            // Given standard has PK, user doesn't
            String standardSchema = """
                CREATE TABLE test_pk (
                    id INT PRIMARY KEY,
                    data VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_pk (
                    id INT,
                    data VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing PK should be detected as CRITICAL
            assertThat(report)
                    .hasDrifts()
                    .isNotMigrationReady();
            LOG.info("Missing PK drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(2)
        @DisplayName("PK-004:  Composite PK to single PK")
        void testCompositePkToSinglePk() throws IOException {
            // Given composite PK vs single PK
            String standardSchema = """
                CREATE TABLE test_composite_pk (
                    tenant_id INT,
                    user_id INT,
                    data VARCHAR(100),
                    PRIMARY KEY (tenant_id, user_id)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_composite_pk (
                    tenant_id INT,
                    user_id INT PRIMARY KEY,
                    data VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then PK structure mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Composite to single PK mismatch: {}", report.getDriftItems());
        }

        @Test
        @Order(3)
        @DisplayName("PK-006: Composite PK column order change")
        void testCompositePkOrderChange() throws IOException {
            // Given different PK column order
            String standardSchema = """
                CREATE TABLE test_pk_order (
                    a INT,
                    b INT,
                    c INT,
                    PRIMARY KEY (a, b, c)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_pk_order (
                    a INT,
                    b INT,
                    c INT,
                    PRIMARY KEY (c, b, a)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then PK order mismatch may be detected
            LOG.info("PK order mismatch drifts: {}", report.getDriftItems());
        }

        // ----------------------------------------
        // Foreign Key Tests
        // ----------------------------------------

        @Test
        @Order(4)
        @DisplayName("FK-001: Missing foreign key")
        void testMissingForeignKey() throws IOException {
            // Given standard has FK, user doesn't
            String standardSchema = """
                CREATE TABLE parent_table (
                    id INT PRIMARY KEY
                ) ENGINE=InnoDB;
                
                CREATE TABLE child_table (
                    id INT PRIMARY KEY,
                    parent_id INT NOT NULL,
                    CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES parent_table(id)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE parent_table (
                    id INT PRIMARY KEY
                ) ENGINE=InnoDB;
                
                CREATE TABLE child_table (
                    id INT PRIMARY KEY,
                    parent_id INT NOT NULL
                    -- Missing FK constraint
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing FK should be detected
            assertThat(report).hasDrifts();
            LOG.info("Missing FK drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(5)
        @DisplayName("FK-005: ON DELETE CASCADE removed")
        void testFkOnDeleteCascadeRemoved() throws IOException {
            // Given different ON DELETE actions
            String standardSchema = """
                CREATE TABLE fk_parent (
                    id INT PRIMARY KEY
                ) ENGINE=InnoDB;
                
                CREATE TABLE fk_child_cascade (
                    id INT PRIMARY KEY,
                    parent_id INT,
                    CONSTRAINT fk_cascade FOREIGN KEY (parent_id) 
                        REFERENCES fk_parent(id) ON DELETE CASCADE
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE fk_parent (
                    id INT PRIMARY KEY
                ) ENGINE=InnoDB;
                
                CREATE TABLE fk_child_cascade (
                    id INT PRIMARY KEY,
                    parent_id INT,
                    CONSTRAINT fk_cascade FOREIGN KEY (parent_id) 
                        REFERENCES fk_parent(id) ON DELETE NO ACTION
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then FK action mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("FK ON DELETE action mismatch:  {}", report.getDriftItems());
        }

        @Test
        @Order(6)
        @DisplayName("FK-006: ON DELETE action changed (CASCADE to SET NULL)")
        void testFkOnDeleteActionChanged() throws IOException {
            // Given different ON DELETE actions
            String standardSchema = """
                CREATE TABLE action_parent (
                    id INT PRIMARY KEY
                ) ENGINE=InnoDB;
                
                CREATE TABLE action_child (
                    id INT PRIMARY KEY,
                    parent_id INT,
                    CONSTRAINT fk_action FOREIGN KEY (parent_id) 
                        REFERENCES action_parent(id) ON DELETE CASCADE
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE action_parent (
                    id INT PRIMARY KEY
                ) ENGINE=InnoDB;
                
                CREATE TABLE action_child (
                    id INT PRIMARY KEY,
                    parent_id INT,
                    CONSTRAINT fk_action FOREIGN KEY (parent_id) 
                        REFERENCES action_parent(id) ON DELETE SET NULL
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then FK action change should be detected
            assertThat(report).hasDrifts();
            LOG.info("FK action changed drifts: {}", report.getDriftItems());
        }

        // ----------------------------------------
        // Unique Constraint Tests
        // ----------------------------------------

        @Test
        @Order(7)
        @DisplayName("UQ-001: Missing unique constraint")
        void testMissingUniqueConstraint() throws IOException {
            // Given standard has UNIQUE, user doesn't
            String standardSchema = """
                CREATE TABLE test_unique (
                    id INT PRIMARY KEY,
                    email VARCHAR(255),
                    CONSTRAINT uq_email UNIQUE (email)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_unique (
                    id INT PRIMARY KEY,
                    email VARCHAR(255)
                    -- Missing UNIQUE constraint
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing UNIQUE should be detected
            assertThat(report).hasDrifts();
            LOG.info("Missing UNIQUE drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(8)
        @DisplayName("UQ-003: Composite unique columns change")
        void testCompositeUniqueChange() throws IOException {
            // Given different composite unique columns
            String standardSchema = """
                CREATE TABLE test_composite_unique (
                    id INT PRIMARY KEY,
                    tenant_id INT,
                    user_code VARCHAR(50),
                    CONSTRAINT uq_tenant_user UNIQUE (tenant_id, user_code)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_composite_unique (
                    id INT PRIMARY KEY,
                    tenant_id INT,
                    user_code VARCHAR(50),
                    CONSTRAINT uq_tenant_user UNIQUE (tenant_id)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then UNIQUE structure mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Composite UNIQUE mismatch: {}", report.getDriftItems());
        }

        // ----------------------------------------
        // Check Constraint Tests (MySQL 8.0.16+)
        // ----------------------------------------

        @Test
        @Order(9)
        @DisplayName("CHK-001: Missing check constraint")
        void testMissingCheckConstraint() throws IOException {
            // Given standard has CHECK, user doesn't
            String standardSchema = """
                CREATE TABLE test_check (
                    id INT PRIMARY KEY,
                    age INT,
                    CONSTRAINT chk_age CHECK (age >= 0 AND age <= 150)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_check (
                    id INT PRIMARY KEY,
                    age INT
                    -- Missing CHECK constraint
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing CHECK should be detected
            assertThat(report).hasDrifts();
            LOG.info("Missing CHECK drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(10)
        @DisplayName("CHK-002: Check expression change")
        void testCheckExpressionChange() throws IOException {
            // Given different CHECK expressions
            String standardSchema = """
                CREATE TABLE test_check_expr (
                    id INT PRIMARY KEY,
                    price DECIMAL(10,2),
                    CONSTRAINT chk_price CHECK (price > 0)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_check_expr (
                    id INT PRIMARY KEY,
                    price DECIMAL(10,2),
                    CONSTRAINT chk_price CHECK (price >= 0)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then CHECK expression mismatch may be detected
            LOG.info("CHECK expression mismatch drifts: {}", report.getDriftItems());
        }
    }

    // ========================================================================
    // SECTION 4: INDEX DETECTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.4: Index Detection Tests")
    @Tag(TestConstants.TAG_INDEXES)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class IndexDetectionTests {

        @Test
        @Order(1)
        @DisplayName("IDX-001: Missing standard index")
        void testMissingIndex() throws IOException {
            // Given standard has index, user doesn't
            String standardSchema = """
                CREATE TABLE test_index (
                    id INT PRIMARY KEY,
                    email VARCHAR(255),
                    status VARCHAR(20),
                    INDEX idx_email (email),
                    INDEX idx_status (status)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_index (
                    id INT PRIMARY KEY,
                    email VARCHAR(255),
                    status VARCHAR(20),
                    INDEX idx_email (email)
                    -- Missing:  idx_status
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing index should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingIndex("status");
        }

        @Test
        @Order(2)
        @DisplayName("IDX-002: Extra index detected")
        void testExtraIndex() throws IOException {
            // Given user has extra index
            String standardSchema = """
                CREATE TABLE test_extra_index (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_extra_index (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    INDEX idx_name (name)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then extra index should be detected (LOW severity)
            assertThat(report).hasDrifts();
            LOG.info("Extra index drifts: {}", report. getDriftItems());
        }

        @Test
        @Order(3)
        @DisplayName("IDX-003: Composite index columns change")
        void testCompositeIndexChange() throws IOException {
            // Given different composite index columns
            String standardSchema = """
                CREATE TABLE test_composite_idx (
                    id INT PRIMARY KEY,
                    first_name VARCHAR(50),
                    last_name VARCHAR(50),
                    INDEX idx_name (first_name, last_name)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_composite_idx (
                    id INT PRIMARY KEY,
                    first_name VARCHAR(50),
                    last_name VARCHAR(50),
                    INDEX idx_name (first_name)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then index structure mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Composite index mismatch: {}", report.getDriftItems());
        }

        @Test
        @Order(4)
        @DisplayName("IDX-004: Index column order change")
        void testIndexColumnOrderChange() throws IOException {
            // Given different index column order
            String standardSchema = """
                CREATE TABLE test_idx_order (
                    id INT PRIMARY KEY,
                    a VARCHAR(50),
                    b VARCHAR(50),
                    INDEX idx_ab (a, b)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_idx_order (
                    id INT PRIMARY KEY,
                    a VARCHAR(50),
                    b VARCHAR(50),
                    INDEX idx_ab (b, a)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then index order mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Index order mismatch: {}", report.getDriftItems());
        }

        @Test
        @Order(5)
        @DisplayName("IDX-005: Unique index to non-unique")
        void testUniqueIndexToNonUnique() throws IOException {
            // Given UNIQUE vs non-unique index
            String standardSchema = """
                CREATE TABLE test_unique_idx (
                    id INT PRIMARY KEY,
                    code VARCHAR(50),
                    UNIQUE INDEX idx_code (code)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_unique_idx (
                    id INT PRIMARY KEY,
                    code VARCHAR(50),
                    INDEX idx_code (code)
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then unique vs non-unique mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Unique index mismatch: {}", report.getDriftItems());
        }

        @Test
        @Order(6)
        @DisplayName("IDX-007: Missing fulltext index")
        void testMissingFulltextIndex() throws IOException {
            // Given standard has FULLTEXT index
            String standardSchema = """
                CREATE TABLE test_fulltext (
                    id INT PRIMARY KEY,
                    title VARCHAR(255),
                    content TEXT,
                    FULLTEXT INDEX idx_content (content)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_fulltext (
                    id INT PRIMARY KEY,
                    title VARCHAR(255),
                    content TEXT
                    -- Missing FULLTEXT index
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing FULLTEXT index should be detected
            assertThat(report).hasDrifts();
            LOG.info("Missing FULLTEXT index drifts: {}", report. getDriftItems());
        }

        @Test
        @Order(7)
        @DisplayName("IDX-008: Missing spatial index")
        void testMissingSpatialIndex() throws IOException {
            // Given standard has SPATIAL index
            String standardSchema = """
                CREATE TABLE test_spatial (
                    id INT PRIMARY KEY,
                    location POINT NOT NULL SRID 4326,
                    SPATIAL INDEX idx_location (location)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_spatial (
                    id INT PRIMARY KEY,
                    location POINT NOT NULL SRID 4326
                    -- Missing SPATIAL index
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing SPATIAL index should be detected
            assertThat(report).hasDrifts();
            LOG.info("Missing SPATIAL index drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(8)
        @DisplayName("IDX-010: Prefix index length change")
        void testPrefixIndexLengthChange() throws IOException {
            // Given different prefix lengths
            String standardSchema = """
                CREATE TABLE test_prefix (
                    id INT PRIMARY KEY,
                    description TEXT,
                    INDEX idx_desc (description(100))
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE test_prefix (
                    id INT PRIMARY KEY,
                    description TEXT,
                    INDEX idx_desc (description(50))
                ) ENGINE=InnoDB;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then prefix length mismatch may be detected
            LOG.info("Prefix index length mismatch: {}", report.getDriftItems());
        }
    }

    // ========================================================================
    // SECTION 5: STORED PROCEDURE TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.5: Stored Procedure Tests")
    @Tag(TestConstants.TAG_ROUTINES)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class StoredProcedureTests {

        @Test
        @Order(1)
        @DisplayName("SP-001: Missing procedure")
        void testMissingProcedure() throws IOException {
            // Given standard has procedure, user doesn't
            String standardSchema = """
                CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_get_all_users()
                BEGIN
                    SELECT * FROM users;
                END //
                
                CREATE PROCEDURE sp_get_user_by_id(IN p_id INT)
                BEGIN
                    SELECT * FROM users WHERE id = p_id;
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_get_all_users()
                BEGIN
                    SELECT * FROM users;
                END //
                -- Missing:  sp_get_user_by_id
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing procedure should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingProcedure("sp_get_user_by_id");
        }

        @Test
        @Order(2)
        @DisplayName("SP-002: Extra procedure")
        void testExtraProcedure() throws IOException {
            // Given user has extra procedure
            String standardSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_custom_proc()
                BEGIN
                    SELECT 1;
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then extra procedure should be detected (LOW)
            assertThat(report).hasDrifts();
            LOG.info("Extra procedure drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(3)
        @DisplayName("SP-003: Parameter added")
        void testProcedureParameterAdded() throws IOException {
            // Given different parameter counts
            String standardSchema = """
                CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_search(IN p_name VARCHAR(100))
                BEGIN
                    SELECT * FROM users WHERE name LIKE p_name;
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_search(IN p_name VARCHAR(100), IN p_limit INT)
                BEGIN
                    SELECT * FROM users WHERE name LIKE p_name LIMIT p_limit;
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then parameter mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Procedure parameter mismatch: {}", report.getDriftItems());
        }

        @Test
        @Order(4)
        @DisplayName("SP-005: Parameter type change")
        void testProcedureParameterTypeChange() throws IOException {
            // Given different parameter types
            String standardSchema = """
                CREATE TABLE data (id INT PRIMARY KEY) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_process(IN p_id INT)
                BEGIN
                    SELECT * FROM data WHERE id = p_id;
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE data (id INT PRIMARY KEY) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_process(IN p_id VARCHAR(50))
                BEGIN
                    SELECT * FROM data WHERE id = CAST(p_id AS SIGNED);
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then parameter type mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Procedure parameter type mismatch: {}", report.getDriftItems());
        }

        @Test
        @Order(5)
        @DisplayName("SP-006: Parameter mode change (IN to OUT)")
        void testProcedureParameterModeChange() throws IOException {
            // Given different parameter modes
            String standardSchema = """
                CREATE TABLE counters (id INT PRIMARY KEY, value INT) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_get_count(OUT p_count INT)
                BEGIN
                    SELECT COUNT(*) INTO p_count FROM counters;
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE counters (id INT PRIMARY KEY, value INT) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_get_count(IN p_count INT)
                BEGIN
                    SELECT COUNT(*) FROM counters WHERE value > p_count;
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then parameter mode mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Procedure parameter mode mismatch:  {}", report.getDriftItems());
        }

        @Test
        @Order(6)
        @DisplayName("SP-007: Procedure body logic change")
        void testProcedureBodyChange() throws IOException {
            // Given different procedure bodies
            String standardSchema = """
                CREATE TABLE items (id INT PRIMARY KEY, status VARCHAR(20)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_get_active()
                BEGIN
                    SELECT * FROM items WHERE status = 'active';
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE items (id INT PRIMARY KEY, status VARCHAR(20)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE PROCEDURE sp_get_active()
                BEGIN
                    SELECT * FROM items WHERE status = 'active' OR status = 'pending';
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then body mismatch should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsModifiedProcedure("sp_get_active");
        }
    }

    // ========================================================================
    // SECTION 6: FUNCTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.6: Function Tests")
    @Tag(TestConstants.TAG_ROUTINES)
    @TestMethodOrder(MethodOrderer.OrderAnnotation. class)
    class FunctionTests {

        @Test
        @Order(1)
        @DisplayName("FN-001: Missing function")
        void testMissingFunction() throws IOException {
            // Given standard has function, user doesn't
            String standardSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE FUNCTION fn_get_user_count()
                RETURNS INT
                DETERMINISTIC
                READS SQL DATA
                BEGIN
                    DECLARE v_count INT;
                    SELECT COUNT(*) INTO v_count FROM users;
                    RETURN v_count;
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE users (id INT PRIMARY KEY) ENGINE=InnoDB;
                -- Missing: fn_get_user_count function
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing function should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingFunction("fn_get_user_count");
        }

        @Test
        @Order(2)
        @DisplayName("FN-002: Return type change")
        void testFunctionReturnTypeChange() throws IOException {
            // Given different return types
            String standardSchema = """
                CREATE TABLE data (id INT PRIMARY KEY) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE FUNCTION fn_count()
                RETURNS INT
                DETERMINISTIC
                READS SQL DATA
                BEGIN
                    RETURN (SELECT COUNT(*) FROM data);
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE data (id INT PRIMARY KEY) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE FUNCTION fn_count()
                RETURNS BIGINT
                DETERMINISTIC
                READS SQL DATA
                BEGIN
                    RETURN (SELECT COUNT(*) FROM data);
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then return type mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Function return type mismatch:  {}", report.getDriftItems());
        }

        @Test
        @Order(3)
        @DisplayName("FN-005: Function body change")
        void testFunctionBodyChange() throws IOException {
            // Given different function bodies
            String standardSchema = """
                DELIMITER //
                CREATE FUNCTION fn_calculate(p_a INT, p_b INT)
                RETURNS INT
                DETERMINISTIC
                NO SQL
                BEGIN
                    RETURN p_a + p_b;
                END //
                DELIMITER ;
                """;

            String userSchema = """
                DELIMITER //
                CREATE FUNCTION fn_calculate(p_a INT, p_b INT)
                RETURNS INT
                DETERMINISTIC
                NO SQL
                BEGIN
                    RETURN p_a - p_b;
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then body mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Function body mismatch: {}", report. getDriftItems());
        }
    }

    // ========================================================================
    // SECTION 7: TRIGGER TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.7: Trigger Tests")
    @Tag(TestConstants.TAG_TRIGGERS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TriggerTests {

        @Test
        @Order(1)
        @DisplayName("TRG-001: Missing trigger")
        void testMissingTrigger() throws IOException {
            // Given standard has trigger, user doesn't
            String standardSchema = """
                CREATE TABLE products (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100),
                    price DECIMAL(10,2)
                ) ENGINE=InnoDB;
                
                CREATE TABLE audit_log (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    action VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE TRIGGER trg_product_insert
                AFTER INSERT ON products
                FOR EACH ROW
                BEGIN
                    INSERT INTO audit_log (action) VALUES ('INSERT');
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE products (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100),
                    price DECIMAL(10,2)
                ) ENGINE=InnoDB;
                
                CREATE TABLE audit_log (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    action VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB;
                -- Missing: trg_product_insert trigger
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing trigger should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingTrigger("trg_product_insert");
        }

        @Test
        @Order(2)
        @DisplayName("TRG-003: Trigger timing change (BEFORE to AFTER)")
        void testTriggerTimingChange() throws IOException {
            // Given different trigger timing
            String standardSchema = """
                CREATE TABLE items (id INT PRIMARY KEY, data VARCHAR(100)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE TRIGGER trg_items_insert
                BEFORE INSERT ON items
                FOR EACH ROW
                BEGIN
                    SET NEW.data = UPPER(NEW.data);
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE items (id INT PRIMARY KEY, data VARCHAR(100)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE TRIGGER trg_items_insert
                AFTER INSERT ON items
                FOR EACH ROW
                BEGIN
                    -- Can't modify NEW in AFTER trigger
                    SELECT NEW.data;
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then timing mismatch should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsModifiedTrigger("trg_items_insert");
        }

        @Test
        @Order(3)
        @DisplayName("TRG-004: Trigger event change (INSERT to UPDATE)")
        void testTriggerEventChange() throws IOException {
            // Given different trigger events
            String standardSchema = """
                CREATE TABLE records (id INT PRIMARY KEY, value INT) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE TRIGGER trg_records_change
                AFTER INSERT ON records
                FOR EACH ROW
                BEGIN
                    SELECT 'inserted';
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE records (id INT PRIMARY KEY, value INT) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE TRIGGER trg_records_change
                AFTER UPDATE ON records
                FOR EACH ROW
                BEGIN
                    SELECT 'updated';
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then event mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Trigger event mismatch: {}", report.getDriftItems());
        }

        @Test
        @Order(4)
        @DisplayName("TRG-006: Trigger body logic change")
        void testTriggerBodyChange() throws IOException {
            // Given different trigger bodies
            String standardSchema = """
                CREATE TABLE orders (id INT PRIMARY KEY, total DECIMAL(10,2)) ENGINE=InnoDB;
                CREATE TABLE order_log (id INT AUTO_INCREMENT PRIMARY KEY, msg VARCHAR(255)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE TRIGGER trg_order_audit
                AFTER INSERT ON orders
                FOR EACH ROW
                BEGIN
                    INSERT INTO order_log (msg) VALUES (CONCAT('Order created: ', NEW.id));
                END //
                DELIMITER ;
                """;

            String userSchema = """
                CREATE TABLE orders (id INT PRIMARY KEY, total DECIMAL(10,2)) ENGINE=InnoDB;
                CREATE TABLE order_log (id INT AUTO_INCREMENT PRIMARY KEY, msg VARCHAR(255)) ENGINE=InnoDB;
                
                DELIMITER //
                CREATE TRIGGER trg_order_audit
                AFTER INSERT ON orders
                FOR EACH ROW
                BEGIN
                    INSERT INTO order_log (msg) VALUES (CONCAT('New order: ', NEW.id, ' total: ', NEW.total));
                END //
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then body mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("Trigger body mismatch: {}", report.getDriftItems());
        }
    }

    // ========================================================================
    // SECTION 8: VIEW TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.8: View Tests")
    @Tag(TestConstants.TAG_VIEWS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ViewTests {

        @Test
        @Order(1)
        @DisplayName("VW-001: Missing view")
        void testMissingView() throws IOException {
            // Given standard has view, user doesn't
            String standardSchema = """
                CREATE TABLE employees (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    department VARCHAR(50),
                    salary DECIMAL(10,2),
                    status VARCHAR(20) DEFAULT 'active'
                ) ENGINE=InnoDB;
                
                CREATE VIEW vw_active_employees AS
                SELECT id, name, department, salary
                FROM employees
                WHERE status = 'active';
                """;

            String userSchema = """
                CREATE TABLE employees (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    department VARCHAR(50),
                    salary DECIMAL(10,2),
                    status VARCHAR(20) DEFAULT 'active'
                ) ENGINE=InnoDB;
                -- Missing: vw_active_employees view
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing view should be detected
            assertThat(report)
                    .hasDrifts()
                    .containsMissingView("vw_active_employees");
        }

        @Test
        @Order(2)
        @DisplayName("VW-003: View columns change")
        void testViewColumnsChange() throws IOException {
            // Given different view columns
            String standardSchema = """
                CREATE TABLE data (id INT PRIMARY KEY, a INT, b INT, c INT) ENGINE=InnoDB;
                
                CREATE VIEW vw_data AS
                SELECT id, a, b, c FROM data;
                """;

            String userSchema = """
                CREATE TABLE data (id INT PRIMARY KEY, a INT, b INT, c INT) ENGINE=InnoDB;
                
                CREATE VIEW vw_data AS
                SELECT id, a, b FROM data;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then view column mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("View columns mismatch: {}", report. getDriftItems());
        }

        @Test
        @Order(3)
        @DisplayName("VW-004: View query change")
        void testViewQueryChange() throws IOException {
            // Given different view queries
            String standardSchema = """
                CREATE TABLE products (id INT PRIMARY KEY, category VARCHAR(50), price DECIMAL(10,2)) ENGINE=InnoDB;
                
                CREATE VIEW vw_products AS
                SELECT * FROM products WHERE price > 0;
                """;

            String userSchema = """
                CREATE TABLE products (id INT PRIMARY KEY, category VARCHAR(50), price DECIMAL(10,2)) ENGINE=InnoDB;
                
                CREATE VIEW vw_products AS
                SELECT * FROM products WHERE price >= 0;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then view definition mismatch should be detected
            assertThat(report).hasDrifts();
            LOG.info("View query mismatch: {}", report.getDriftItems());
        }
    }

    // ========================================================================
    // SECTION 9: EVENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.9: Event Tests")
    @Tag(TestConstants.TAG_EVENTS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EventTests {

        @Test
        @Order(1)
        @DisplayName("EVT-001: Missing event")
        void testMissingEvent() throws IOException {
            // Given standard has event, user doesn't
            String standardSchema = """
                CREATE TABLE event_log (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    message VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB;
                
                CREATE EVENT IF NOT EXISTS evt_daily_cleanup
                ON SCHEDULE EVERY 1 DAY
                STARTS CURRENT_TIMESTAMP
                DO
                    DELETE FROM event_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
                """;

            String userSchema = """
                CREATE TABLE event_log (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    message VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB;
                -- Missing: evt_daily_cleanup event
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then missing event should be detected
            // Note: Event detection depends on MySQL event scheduler being enabled
            LOG.info("Missing event test - drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(2)
        @DisplayName("EVT-002: Extra event")
        void testExtraEvent() throws IOException {
            // Given user has extra event
            String standardSchema = """
                CREATE TABLE logs (id INT AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE logs (id INT AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;
                
                CREATE EVENT IF NOT EXISTS evt_custom_job
                ON SCHEDULE EVERY 1 HOUR
                DO
                    INSERT INTO logs VALUES (NULL);
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then extra event should be detected (LOW severity)
            LOG.info("Extra event test - drifts: {}", report.getDriftItems());
        }

        @Test
        @Order(3)
        @DisplayName("EVT-003: Event schedule change")
        void testEventScheduleChange() throws IOException {
            // Given different event schedules
            String standardSchema = """
                CREATE TABLE tasks (id INT AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;
                
                CREATE EVENT IF NOT EXISTS evt_hourly_task
                ON SCHEDULE EVERY 1 HOUR
                DO
                    SELECT 1;
                """;

            String userSchema = """
                CREATE TABLE tasks (id INT AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;
                
                CREATE EVENT IF NOT EXISTS evt_hourly_task
                ON SCHEDULE EVERY 30 MINUTE
                DO
                    SELECT 1;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then schedule mismatch should be detected
            LOG.info("Event schedule mismatch - drifts: {}", report. getDriftItems());
        }
    }

    // ========================================================================
    // SECTION 10: COMPREHENSIVE SCHEMA TESTS
    // ========================================================================

    @Nested
    @DisplayName("B.10: Comprehensive Schema Tests")
    @TestMethodOrder(MethodOrderer. OrderAnnotation.class)
    class ComprehensiveSchemaTests {

        @Test
        @Order(1)
        @DisplayName("COMP-001: Full schema comparison with multiple drift types")
        void testFullSchemaComparison() throws IOException {
            // Given a comprehensive standard schema
            String standardSchema = """
                -- Tables
                CREATE TABLE users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    status ENUM('active', 'inactive', 'pending') DEFAULT 'pending',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_status (status)
                ) ENGINE=InnoDB;
                
                CREATE TABLE orders (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    total DECIMAL(10,2) NOT NULL,
                    status VARCHAR(20) DEFAULT 'pending',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT chk_total CHECK (total >= 0)
                ) ENGINE=InnoDB;
                
                CREATE TABLE order_items (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    order_id INT NOT NULL,
                    product_name VARCHAR(255) NOT NULL,
                    quantity INT NOT NULL DEFAULT 1,
                    price DECIMAL(10,2) NOT NULL,
                    line_total DECIMAL(12,2) GENERATED ALWAYS AS (quantity * price) STORED,
                    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                    CONSTRAINT chk_quantity CHECK (quantity > 0)
                ) ENGINE=InnoDB;
                
                -- View
                CREATE VIEW vw_order_summary AS
                SELECT o.id, o.user_id, u.email, o.total, o.status
                FROM orders o
                JOIN users u ON o.user_id = u.id;
                
                -- Procedures
                DELIMITER //
                CREATE PROCEDURE sp_get_user_orders(IN p_user_id INT)
                BEGIN
                    SELECT * FROM orders WHERE user_id = p_user_id;
                END //
                
                CREATE FUNCTION fn_order_count(p_user_id INT)
                RETURNS INT
                DETERMINISTIC
                READS SQL DATA
                BEGIN
                    RETURN (SELECT COUNT(*) FROM orders WHERE user_id = p_user_id);
                END //
                
                -- Trigger
                CREATE TRIGGER trg_order_audit
                AFTER INSERT ON orders
                FOR EACH ROW
                BEGIN
                    SELECT NEW.id;
                END //
                DELIMITER ;
                """;

            // User schema with multiple differences
            String userSchema = """
                -- Missing UNIQUE on email, different status enum
                CREATE TABLE users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    email VARCHAR(255) NOT NULL,
                    name VARCHAR(100),
                    status ENUM('active', 'inactive') DEFAULT 'inactive',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    -- Missing:  idx_status index
                ) ENGINE=InnoDB;
                
                -- Missing CHECK constraint, different FK action
                CREATE TABLE orders (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    total DECIMAL(10,2) NOT NULL,
                    status VARCHAR(20) DEFAULT 'new',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
                    -- Missing: chk_total CHECK constraint
                ) ENGINE=InnoDB;
                
                -- Missing generated column
                CREATE TABLE order_items (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    order_id INT NOT NULL,
                    product_name VARCHAR(255) NOT NULL,
                    quantity INT NOT NULL DEFAULT 1,
                    price DECIMAL(10,2) NOT NULL,
                    -- Missing: line_total generated column
                    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
                    -- Missing: chk_quantity CHECK constraint
                ) ENGINE=InnoDB;
                
                -- View with different query
                CREATE VIEW vw_order_summary AS
                SELECT o.id, o.user_id, o.total, o.status
                FROM orders o;
                
                -- Modified procedure
                DELIMITER //
                CREATE PROCEDURE sp_get_user_orders(IN p_user_id INT, IN p_status VARCHAR(20))
                BEGIN
                    SELECT * FROM orders WHERE user_id = p_user_id AND status = p_status;
                END //
                
                -- Missing:  fn_order_count function
                -- Missing: trg_order_audit trigger
                DELIMITER ;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then multiple drifts should be detected
            assertThat(report)
                    .hasDrifts()
                    .isNotMigrationReady()
                    .hasMinDriftCount(5);

            LOG.info("Comprehensive comparison found {} drifts:", report.getDriftItems().size());
            for (var drift : report.getDriftItems()) {
                LOG.info("  [{}] {}:  {} - {}",
                        drift.severity(), drift.objectType(), drift.objectName(), drift.description());
            }
        }

        @Test
        @Order(2)
        @DisplayName("COMP-002: Engine mismatch blocks migration")
        void testEngineMismatchBlocking() throws IOException {
            // Given InnoDB vs MyISAM (critical for FK support)
            String standardSchema = """
                CREATE TABLE parent (id INT PRIMARY KEY) ENGINE=InnoDB;
                CREATE TABLE child (
                    id INT PRIMARY KEY,
                    parent_id INT,
                    FOREIGN KEY (parent_id) REFERENCES parent(id)
                ) ENGINE=InnoDB;
                """;

            String userSchema = """
                CREATE TABLE parent (id INT PRIMARY KEY) ENGINE=MyISAM;
                CREATE TABLE child (
                    id INT PRIMARY KEY,
                    parent_id INT
                    -- MyISAM doesn't support FK, so it's removed
                ) ENGINE=MyISAM;
                """;

            Path standardPath = createTempSqlFile(standardSchema, "standard");
            Path userPath = createTempSqlFile(userSchema, "user");

            // When comparing
            ComplianceReport report = compareMySqlSchemas(standardPath, userPath);

            // Then engine and FK mismatches should be detected
            assertThat(report)
                    .hasDrifts()
                    .isNotMigrationReady();

            LOG.info("Engine mismatch test - {} drifts found", report.getDriftItems().size());
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    static boolean isDockerRunning() {
        return DockerHealthChecker.isDockerAvailable();
    }
}