-- Test: SP-007 - Procedure body logic change
-- Expected: MEDIUM drift - different behavior

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100),
                       status VARCHAR(20) DEFAULT 'active'
) ENGINE=InnoDB;

DELIMITER //

CREATE PROCEDURE sp_get_active_users()
BEGIN
    -- Changed logic:  original was SELECT * FROM users WHERE status = 'active';
SELECT * FROM users WHERE status IN ('active', 'pending');
END //

DELIMITER ;