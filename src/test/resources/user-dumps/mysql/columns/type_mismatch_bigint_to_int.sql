-- Test: NUM-002 - BIGINT to INT type mismatch
-- Expected:  CRITICAL drift - data loss risk

CREATE TABLE large_ids (
                           id INT PRIMARY KEY,  -- Should be BIGINT
                           name VARCHAR(100)
) ENGINE=InnoDB;