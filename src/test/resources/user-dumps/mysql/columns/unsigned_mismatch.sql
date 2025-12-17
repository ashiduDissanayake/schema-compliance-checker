-- Test: NUM-009 - UNSIGNED to SIGNED mismatch
-- Expected: HIGH drift - range mismatch

CREATE TABLE counters (
                          id INT AUTO_INCREMENT PRIMARY KEY,
                          view_count INT,  -- Should be INT UNSIGNED
                          like_count INT   -- Should be INT UNSIGNED
) ENGINE=InnoDB;