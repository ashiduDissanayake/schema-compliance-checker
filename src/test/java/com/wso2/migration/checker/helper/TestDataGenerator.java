package com.wso2.migration.checker.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates test SQL files programmatically for various test scenarios.
 */
public final class TestDataGenerator {

    private TestDataGenerator() {}

    /**
     * Generates a MySQL schema with all supported data types.
     */
    public static String generateMySqlAllTypesSchema() {
        return """
            -- All MySQL Data Types Test Schema
            
            CREATE TABLE all_numeric_types (
                col_tinyint TINYINT,
                col_tinyint_unsigned TINYINT UNSIGNED,
                col_smallint SMALLINT,
                col_smallint_unsigned SMALLINT UNSIGNED,
                col_mediumint MEDIUMINT,
                col_mediumint_unsigned MEDIUMINT UNSIGNED,
                col_int INT,
                col_int_unsigned INT UNSIGNED,
                col_bigint BIGINT,
                col_bigint_unsigned BIGINT UNSIGNED,
                col_decimal DECIMAL(10,2),
                col_float FLOAT,
                col_double DOUBLE,
                col_bit BIT(8),
                col_boolean BOOLEAN,
                PRIMARY KEY (col_int)
            ) ENGINE=InnoDB;
            
            CREATE TABLE all_string_types (
                id INT AUTO_INCREMENT PRIMARY KEY,
                col_char CHAR(10),
                col_varchar VARCHAR(255),
                col_tinytext TINYTEXT,
                col_text TEXT,
                col_mediumtext MEDIUMTEXT,
                col_longtext LONGTEXT,
                col_binary BINARY(16),
                col_varbinary VARBINARY(256),
                col_tinyblob TINYBLOB,
                col_blob BLOB,
                col_mediumblob MEDIUMBLOB,
                col_longblob LONGBLOB,
                col_enum ENUM('a', 'b', 'c'),
                col_set SET('x', 'y', 'z'),
                col_json JSON
            ) ENGINE=InnoDB;
            
            CREATE TABLE all_datetime_types (
                id INT AUTO_INCREMENT PRIMARY KEY,
                col_date DATE,
                col_time TIME,
                col_time_frac TIME(6),
                col_datetime DATETIME,
                col_datetime_frac DATETIME(6),
                col_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                col_timestamp_frac TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                col_year YEAR
            ) ENGINE=InnoDB;
            """;
    }

    /**
     * Generates a schema with all constraint types.
     */
    public static String generateMySqlConstraintsSchema() {
        return """
            -- All MySQL Constraint Types Test Schema
            
            -- Primary Key variations
            CREATE TABLE pk_single (
                id INT PRIMARY KEY,
                data VARCHAR(100)
            ) ENGINE=InnoDB;
            
            CREATE TABLE pk_composite (
                tenant_id INT,
                user_id INT,
                data VARCHAR(100),
                PRIMARY KEY (tenant_id, user_id)
            ) ENGINE=InnoDB;
            
            CREATE TABLE pk_named (
                id INT,
                data VARCHAR(100),
                CONSTRAINT pk_custom_name PRIMARY KEY (id)
            ) ENGINE=InnoDB;
            
            -- Foreign Key with different actions
            CREATE TABLE fk_parent (
                id INT PRIMARY KEY,
                name VARCHAR(100) NOT NULL
            ) ENGINE=InnoDB;
            
            CREATE TABLE fk_cascade (
                id INT PRIMARY KEY,
                parent_id INT NOT NULL,
                CONSTRAINT fk_cascade_ref FOREIGN KEY (parent_id) 
                    REFERENCES fk_parent(id) ON DELETE CASCADE ON UPDATE CASCADE
            ) ENGINE=InnoDB;
            
            CREATE TABLE fk_restrict (
                id INT PRIMARY KEY,
                parent_id INT NOT NULL,
                CONSTRAINT fk_restrict_ref FOREIGN KEY (parent_id) 
                    REFERENCES fk_parent(id) ON DELETE RESTRICT ON UPDATE RESTRICT
            ) ENGINE=InnoDB;
            
            CREATE TABLE fk_setnull (
                id INT PRIMARY KEY,
                parent_id INT,
                CONSTRAINT fk_setnull_ref FOREIGN KEY (parent_id) 
                    REFERENCES fk_parent(id) ON DELETE SET NULL ON UPDATE SET NULL
            ) ENGINE=InnoDB;
            
            -- Unique constraints
            CREATE TABLE unique_single (
                id INT PRIMARY KEY,
                email VARCHAR(255) UNIQUE,
                username VARCHAR(100),
                CONSTRAINT uq_username UNIQUE (username)
            ) ENGINE=InnoDB;
            
            CREATE TABLE unique_composite (
                id INT PRIMARY KEY,
                tenant_id INT,
                code VARCHAR(50),
                CONSTRAINT uq_tenant_code UNIQUE (tenant_id, code)
            ) ENGINE=InnoDB;
            
            -- Check constraints (MySQL 8.0.16+)
            CREATE TABLE check_constraints (
                id INT PRIMARY KEY,
                age INT,
                price DECIMAL(10,2),
                status VARCHAR(20),
                CONSTRAINT chk_age CHECK (age >= 0 AND age <= 150),
                CONSTRAINT chk_price CHECK (price > 0),
                CONSTRAINT chk_status CHECK (status IN ('active', 'inactive'))
            ) ENGINE=InnoDB;
            
            -- NOT NULL constraints
            CREATE TABLE notnull_test (
                id INT NOT NULL,
                required_field VARCHAR(100) NOT NULL,
                optional_field VARCHAR(100),
                PRIMARY KEY (id)
            ) ENGINE=InnoDB;
            
            -- AUTO_INCREMENT
            CREATE TABLE auto_increment_test (
                id INT AUTO_INCREMENT PRIMARY KEY,
                data VARCHAR(100)
            ) ENGINE=InnoDB;
            
            -- DEFAULT constraints
            CREATE TABLE default_test (
                id INT AUTO_INCREMENT PRIMARY KEY,
                status VARCHAR(20) DEFAULT 'active',
                priority INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB;
            
            -- Generated columns (MySQL 5.7+)
            CREATE TABLE generated_columns (
                id INT AUTO_INCREMENT PRIMARY KEY,
                first_name VARCHAR(50),
                last_name VARCHAR(50),
                full_name VARCHAR(101) GENERATED ALWAYS AS (CONCAT(first_name, ' ', last_name)) STORED,
                name_length INT GENERATED ALWAYS AS (LENGTH(CONCAT(first_name, last_name))) VIRTUAL,
                price DECIMAL(10,2),
                quantity INT,
                total DECIMAL(12,2) GENERATED ALWAYS AS (price * quantity) STORED
            ) ENGINE=InnoDB;
            """;
    }

    /**
     * Generates a schema with stored procedures and functions.
     */
    public static String generateMySqlRoutinesSchema() {
        return """
            -- MySQL Stored Routines Test Schema
            
            CREATE TABLE users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100),
                email VARCHAR(255),
                status VARCHAR(20) DEFAULT 'active',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB;
            
            DELIMITER //
            
            -- Simple procedure
            CREATE PROCEDURE sp_get_all_users()
            BEGIN
                SELECT * FROM users;
            END //
            
            -- Procedure with IN parameter
            CREATE PROCEDURE sp_get_user_by_id(IN p_id INT)
            BEGIN
                SELECT * FROM users WHERE id = p_id;
            END //
            
            -- Procedure with OUT parameter
            CREATE PROCEDURE sp_count_users(OUT p_count INT)
            BEGIN
                SELECT COUNT(*) INTO p_count FROM users;
            END //
            
            -- Procedure with INOUT parameter
            CREATE PROCEDURE sp_increment(INOUT p_value INT)
            BEGIN
                SET p_value = p_value + 1;
            END //
            
            -- Procedure with multiple parameters
            CREATE PROCEDURE sp_create_user(
                IN p_name VARCHAR(100),
                IN p_email VARCHAR(255),
                OUT p_id INT
            )
            BEGIN
                INSERT INTO users (name, email) VALUES (p_name, p_email);
                SET p_id = LAST_INSERT_ID();
            END //
            
            -- Procedure with cursor
            CREATE PROCEDURE sp_process_users()
            BEGIN
                DECLARE done INT DEFAULT FALSE;
                DECLARE v_id INT;
                DECLARE v_name VARCHAR(100);
                DECLARE cur CURSOR FOR SELECT id, name FROM users;
                DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
                
                OPEN cur;
                read_loop: LOOP
                    FETCH cur INTO v_id, v_name;
                    IF done THEN
                        LEAVE read_loop;
                    END IF;
                END LOOP;
                CLOSE cur;
            END //
            
            -- Procedure with error handling
            CREATE PROCEDURE sp_safe_insert(IN p_name VARCHAR(100))
            BEGIN
                DECLARE EXIT HANDLER FOR SQLEXCEPTION
                BEGIN
                    ROLLBACK;
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insert failed';
                END;
                
                START TRANSACTION;
                INSERT INTO users (name) VALUES (p_name);
                COMMIT;
            END //
            
            -- Function returning scalar
            CREATE FUNCTION fn_get_user_count()
            RETURNS INT
            DETERMINISTIC
            READS SQL DATA
            BEGIN
                DECLARE v_count INT;
                SELECT COUNT(*) INTO v_count FROM users;
                RETURN v_count;
            END //
            
            -- Function with parameter
            CREATE FUNCTION fn_get_user_name(p_id INT)
            RETURNS VARCHAR(100)
            DETERMINISTIC
            READS SQL DATA
            BEGIN
                DECLARE v_name VARCHAR(100);
                SELECT name INTO v_name FROM users WHERE id = p_id;
                RETURN COALESCE(v_name, 'Unknown');
            END //
            
            -- Function with calculation
            CREATE FUNCTION fn_calculate_discount(p_price DECIMAL(10,2), p_percent INT)
            RETURNS DECIMAL(10,2)
            DETERMINISTIC
            NO SQL
            BEGIN
                RETURN p_price * (1 - p_percent / 100);
            END //
            
            DELIMITER ;
            """;
    }

    /**
     * Generates a schema with triggers.
     */
    public static String generateMySqlTriggersSchema() {
        return """
            -- MySQL Triggers Test Schema
            
            CREATE TABLE products (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100),
                price DECIMAL(10,2),
                stock INT DEFAULT 0,
                status VARCHAR(20) DEFAULT 'active'
            ) ENGINE=InnoDB;
            
            CREATE TABLE audit_log (
                id INT AUTO_INCREMENT PRIMARY KEY,
                table_name VARCHAR(100),
                action VARCHAR(20),
                record_id INT,
                old_data JSON,
                new_data JSON,
                changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                changed_by VARCHAR(100)
            ) ENGINE=InnoDB;
            
            CREATE TABLE stock_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                product_id INT,
                old_stock INT,
                new_stock INT,
                change_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB;
            
            DELIMITER //
            
            -- BEFORE INSERT trigger
            CREATE TRIGGER trg_products_before_insert
            BEFORE INSERT ON products
            FOR EACH ROW
            BEGIN
                IF NEW.name IS NULL OR NEW.name = '' THEN
                    SET NEW.name = 'Unnamed Product';
                END IF;
                IF NEW.price IS NULL OR NEW.price < 0 THEN
                    SET NEW.price = 0;
                END IF;
            END //
            
            -- AFTER INSERT trigger
            CREATE TRIGGER trg_products_after_insert
            AFTER INSERT ON products
            FOR EACH ROW
            BEGIN
                INSERT INTO audit_log (table_name, action, record_id, new_data)
                VALUES ('products', 'INSERT', NEW.id, 
                    JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'price', NEW.price));
            END //
            
            -- BEFORE UPDATE trigger
            CREATE TRIGGER trg_products_before_update
            BEFORE UPDATE ON products
            FOR EACH ROW
            BEGIN
                IF NEW.price < 0 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Price cannot be negative';
                END IF;
            END //
            
            -- AFTER UPDATE trigger
            CREATE TRIGGER trg_products_after_update
            AFTER UPDATE ON products
            FOR EACH ROW
            BEGIN
                INSERT INTO audit_log (table_name, action, record_id, old_data, new_data)
                VALUES ('products', 'UPDATE', NEW.id,
                    JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'price', OLD.price),
                    JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'price', NEW.price));
                
                IF OLD.stock != NEW.stock THEN
                    INSERT INTO stock_history (product_id, old_stock, new_stock)
                    VALUES (NEW.id, OLD.stock, NEW.stock);
                END IF;
            END //
            
            -- BEFORE DELETE trigger
            CREATE TRIGGER trg_products_before_delete
            BEFORE DELETE ON products
            FOR EACH ROW
            BEGIN
                IF OLD.status = 'protected' THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete protected product';
                END IF;
            END //
            
            -- AFTER DELETE trigger
            CREATE TRIGGER trg_products_after_delete
            AFTER DELETE ON products
            FOR EACH ROW
            BEGIN
                INSERT INTO audit_log (table_name, action, record_id, old_data)
                VALUES ('products', 'DELETE', OLD.id,
                    JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'price', OLD.price));
            END //
            
            DELIMITER ;
            """;
    }

    /**
     * Generates a schema with events.
     */
    public static String generateMySqlEventsSchema() {
        return """
            -- MySQL Events Test Schema
            
            -- Enable event scheduler
            SET GLOBAL event_scheduler = ON;
            
            CREATE TABLE event_log (
                id INT AUTO_INCREMENT PRIMARY KEY,
                event_name VARCHAR(100),
                executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                message VARCHAR(255)
            ) ENGINE=InnoDB;
            
            CREATE TABLE temp_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                data VARCHAR(255),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP
            ) ENGINE=InnoDB;
            
            -- One-time event
            CREATE EVENT IF NOT EXISTS evt_one_time
            ON SCHEDULE AT CURRENT_TIMESTAMP + INTERVAL 1 DAY
            DO
                INSERT INTO event_log (event_name, message) 
                VALUES ('evt_one_time', 'One-time event executed');
            
            -- Recurring event - every hour
            CREATE EVENT IF NOT EXISTS evt_hourly_cleanup
            ON SCHEDULE EVERY 1 HOUR
            STARTS CURRENT_TIMESTAMP
            DO
                DELETE FROM temp_data WHERE expires_at < NOW();
            
            -- Recurring event - daily at midnight
            CREATE EVENT IF NOT EXISTS evt_daily_maintenance
            ON SCHEDULE EVERY 1 DAY
            STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY)
            DO
            BEGIN
                INSERT INTO event_log (event_name, message)
                VALUES ('evt_daily_maintenance', 'Daily maintenance executed');
                
                -- Archive old event logs
                DELETE FROM event_log WHERE executed_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
            END;
            
            -- Disabled event
            CREATE EVENT IF NOT EXISTS evt_disabled
            ON SCHEDULE EVERY 1 MINUTE
            DISABLE
            DO
                INSERT INTO event_log (event_name, message)
                VALUES ('evt_disabled', 'This should not run');
            
            -- Event with end time
            CREATE EVENT IF NOT EXISTS evt_with_end
            ON SCHEDULE EVERY 5 MINUTE
            STARTS CURRENT_TIMESTAMP
            ENDS CURRENT_TIMESTAMP + INTERVAL 1 MONTH
            DO
                INSERT INTO event_log (event_name, message)
                VALUES ('evt_with_end', 'Limited time event');
            """;
    }

    /**
     * Generates a schema with views.
     */
    public static String generateMySqlViewsSchema() {
        return """
            -- MySQL Views Test Schema
            
            CREATE TABLE employees (
                id INT AUTO_INCREMENT PRIMARY KEY,
                first_name VARCHAR(50),
                last_name VARCHAR(50),
                email VARCHAR(255),
                department_id INT,
                salary DECIMAL(10,2),
                hire_date DATE,
                status VARCHAR(20) DEFAULT 'active'
            ) ENGINE=InnoDB;
            
            CREATE TABLE departments (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100),
                budget DECIMAL(12,2),
                manager_id INT
            ) ENGINE=InnoDB;
            
            -- Simple view
            CREATE VIEW vw_active_employees AS
            SELECT id, first_name, last_name, email, department_id
            FROM employees
            WHERE status = 'active';
            
            -- View with JOIN
            CREATE VIEW vw_employee_departments AS
            SELECT 
                e.id AS employee_id,
                e. first_name,
                e. last_name,
                e. salary,
                d.name AS department_name,
                d.budget AS department_budget
            FROM employees e
            LEFT JOIN departments d ON e.department_id = d.id;
            
            -- View with aggregation
            CREATE VIEW vw_department_stats AS
            SELECT 
                d.id AS department_id,
                d. name AS department_name,
                COUNT(e.id) AS employee_count,
                AVG(e.salary) AS avg_salary,
                SUM(e.salary) AS total_salary
            FROM departments d
            LEFT JOIN employees e ON d.id = e.department_id
            GROUP BY d.id, d. name;
            
            -- View with calculated columns
            CREATE VIEW vw_employee_details AS
            SELECT 
                id,
                CONCAT(first_name, ' ', last_name) AS full_name,
                email,
                salary,
                salary * 12 AS annual_salary,
                DATEDIFF(CURDATE(), hire_date) AS days_employed
            FROM employees;
            
            -- Updatable view
            CREATE VIEW vw_employee_contact AS
            SELECT id, first_name, last_name, email
            FROM employees
            WHERE status = 'active'
            WITH CHECK OPTION;
            
            -- View using UNION
            CREATE VIEW vw_all_names AS
            SELECT first_name AS name, 'Employee' AS type FROM employees
            UNION
            SELECT name, 'Department' AS type FROM departments;
            
            -- Nested view
            CREATE VIEW vw_nested AS
            SELECT * FROM vw_active_employees WHERE department_id IS NOT NULL;
            """;
    }

    /**
     * Generates a schema with various indexes.
     */
    public static String generateMySqlIndexesSchema() {
        return """
            -- MySQL Indexes Test Schema
            
            CREATE TABLE indexed_table (
                id INT AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(255),
                first_name VARCHAR(100),
                last_name VARCHAR(100),
                status VARCHAR(20),
                priority INT,
                description TEXT,
                location POINT NOT NULL SRID 4326,
                created_at DATETIME,
                category VARCHAR(50),
                tags VARCHAR(500)
            ) ENGINE=InnoDB;
            
            -- Standard B-Tree index
            CREATE INDEX idx_email ON indexed_table(email);
            
            -- Composite index
            CREATE INDEX idx_name ON indexed_table(first_name, last_name);
            
            -- Unique index
            CREATE UNIQUE INDEX idx_email_unique ON indexed_table(email);
            
            -- Prefix index
            CREATE INDEX idx_desc_prefix ON indexed_table(description(100));
            
            -- Descending index (MySQL 8.0+)
            CREATE INDEX idx_created_desc ON indexed_table(created_at DESC);
            
            -- Multi-column mixed order
            CREATE INDEX idx_status_priority ON indexed_table(status ASC, priority DESC);
            
            -- Spatial index
            CREATE SPATIAL INDEX idx_location ON indexed_table(location);
            
            -- Fulltext index
            CREATE FULLTEXT INDEX idx_fulltext ON indexed_table(description);
            
            -- Multiple column fulltext
            CREATE FULLTEXT INDEX idx_fulltext_multi ON indexed_table(first_name, last_name, description);
            
            -- Invisible index (MySQL 8.0+)
            CREATE INDEX idx_category_invisible ON indexed_table(category) INVISIBLE;
            """;
    }

    /**
     * Writes generated SQL to a file.
     */
    public static Path writeToFile(String content, Path directory, String filename) throws IOException {
        Files.createDirectories(directory);
        Path filePath = directory.resolve(filename);
        Files.writeString(filePath, content);
        return filePath;
    }
}