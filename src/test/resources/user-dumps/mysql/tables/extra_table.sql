-- Test: TBL-003 - Extra table detection
-- Expected: LOW drift - extra 'temp_backup' table

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(255) UNIQUE
) ENGINE=InnoDB;

-- Extra table not in golden schema
CREATE TABLE temp_backup (
                             id INT AUTO_INCREMENT PRIMARY KEY,
                             backup_data TEXT,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;