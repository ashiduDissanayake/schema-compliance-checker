-- Test: CHK-001 - Missing check constraint
-- Expected: MEDIUM drift - no data validation

CREATE TABLE products (
                          id INT PRIMARY KEY,
                          price DECIMAL(10,2),
                          quantity INT
    -- MISSING: CONSTRAINT chk_price CHECK (price > 0)
    -- MISSING: CONSTRAINT chk_quantity CHECK (quantity >= 0)
) ENGINE=InnoDB;