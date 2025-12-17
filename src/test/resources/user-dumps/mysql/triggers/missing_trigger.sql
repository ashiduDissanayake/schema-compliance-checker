-- Test: TRG-001 - Missing trigger
-- Expected: HIGH drift - audit/automation broken

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

-- MISSING: trg_product_audit trigger