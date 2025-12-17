-- Test: TRG-003 - Trigger timing change
-- Expected: HIGH drift - different execution point

CREATE TABLE items (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       data VARCHAR(100)
) ENGINE=InnoDB;

DELIMITER //

-- Changed from BEFORE INSERT to AFTER INSERT
CREATE TRIGGER trg_items_insert
    AFTER INSERT ON items  -- Should be BEFORE INSERT
    FOR EACH ROW
BEGIN
    SELECT NEW.data;  -- Can't modify NEW in AFTER trigger
END //

DELIMITER ;