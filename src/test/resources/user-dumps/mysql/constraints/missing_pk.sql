-- Test: PK-001 - Missing primary key
-- Expected: CRITICAL drift - no row uniqueness

CREATE TABLE users (
                       id INT,  -- Should be PRIMARY KEY
                       name VARCHAR(100)
) ENGINE=InnoDB;