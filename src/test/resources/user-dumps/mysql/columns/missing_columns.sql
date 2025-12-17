-- Test: COL-001 - Missing column detection
-- Expected:  CRITICAL drift - missing 'phone' column

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(255) UNIQUE
    -- MISSING: phone VARCHAR(20)
) ENGINE=InnoDB;