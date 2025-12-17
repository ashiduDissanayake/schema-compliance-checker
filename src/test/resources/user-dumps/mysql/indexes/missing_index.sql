-- Test: IDX-001 - Missing index
-- Expected: MEDIUM drift - potential performance issue

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       email VARCHAR(255),
                       status VARCHAR(20),
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- MISSING: INDEX idx_email (email)
    -- MISSING: INDEX idx_status (status)
) ENGINE=InnoDB;