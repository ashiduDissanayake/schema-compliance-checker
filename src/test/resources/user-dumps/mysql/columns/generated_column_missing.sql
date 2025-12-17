-- Test: PROP-GEN - Generated column missing
-- Expected:  CRITICAL drift - missing computed column

CREATE TABLE order_items (
                             id INT AUTO_INCREMENT PRIMARY KEY,
                             price DECIMAL(10,2),
                             quantity INT
    -- MISSING: total DECIMAL(12,2) GENERATED ALWAYS AS (price * quantity) STORED
) ENGINE=InnoDB;