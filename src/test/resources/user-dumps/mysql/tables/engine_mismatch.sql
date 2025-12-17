-- Test: TBL-006 - Engine mismatch detection
-- Expected: MEDIUM drift - MyISAM instead of InnoDB

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(255) UNIQUE
) ENGINE=MyISAM;  -- Should be InnoDB

CREATE TABLE orders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        total DECIMAL(10,2)
    -- Note: MyISAM doesn't support foreign keys
) ENGINE=MyISAM;  -- Should be InnoDB