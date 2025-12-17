-- Test: FN-001 - Missing function
-- Expected: CRITICAL drift - application functionality broken

CREATE TABLE orders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT,
                        total DECIMAL(10,2)
) ENGINE=InnoDB;

-- MISSING: fn_get_order_count function
-- MISSING: fn_calculate_total function