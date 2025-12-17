-- Test:  TBL-002 - Missing table detection
-- Expected:  CRITICAL drift - missing 'products' table

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(255) UNIQUE
) ENGINE=InnoDB;

CREATE TABLE orders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        total DECIMAL(10,2),
                        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- MISSING:  products table that exists in golden schema