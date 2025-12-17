-- Test: PROP-007 - AUTO_INCREMENT removed
-- Expected:  CRITICAL drift - sequence generation broken

CREATE TABLE users (
                       id INT PRIMARY KEY,  -- Should be INT AUTO_INCREMENT PRIMARY KEY
                       name VARCHAR(100)
) ENGINE=InnoDB;