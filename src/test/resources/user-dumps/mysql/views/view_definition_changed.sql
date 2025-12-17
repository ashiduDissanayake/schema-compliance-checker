-- Test: VW-004 - View definition change
-- Expected: MEDIUM drift - different query results

CREATE TABLE products (
                          id INT AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(100),
                          category VARCHAR(50),
                          price DECIMAL(10,2),
                          status VARCHAR(20) DEFAULT 'active'
) ENGINE=InnoDB;

-- Changed view definition
CREATE VIEW vw_active_products AS
SELECT id, name, price
FROM products
WHERE status = 'active' AND price > 0;  -- Original didn't have price > 0