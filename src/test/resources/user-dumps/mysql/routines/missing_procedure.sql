-- Test: SP-001 - Missing procedure
-- Expected: CRITICAL drift - application functionality broken

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100)
) ENGINE=InnoDB;

-- MISSING: sp_get_all_users procedure
-- MISSING:  sp_get_user_by_id procedure