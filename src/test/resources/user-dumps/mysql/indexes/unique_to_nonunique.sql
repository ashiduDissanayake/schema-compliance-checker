-- Test: IDX-005 - Unique index to non-unique
-- Expected: HIGH drift - duplicate values allowed

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       email VARCHAR(255),
                       INDEX idx_email (email)  -- Should be UNIQUE INDEX
) ENGINE=InnoDB;