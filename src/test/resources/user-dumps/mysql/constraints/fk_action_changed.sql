-- Test: FK-005 - ON DELETE action changed
-- Expected: HIGH drift - different cascade behavior

CREATE TABLE parent_table (
                              id INT PRIMARY KEY
) ENGINE=InnoDB;

CREATE TABLE child_table (
                             id INT PRIMARY KEY,
                             parent_id INT NOT NULL,
                             CONSTRAINT fk_parent FOREIGN KEY (parent_id)
                                 REFERENCES parent_table(id)
                                 ON DELETE NO ACTION  -- Should be ON DELETE CASCADE
                                 ON UPDATE NO ACTION  -- Should be ON UPDATE CASCADE
) ENGINE=InnoDB;