-- Test: FK-001 - Missing foreign key
-- Expected: HIGH drift - no referential integrity

CREATE TABLE parent_table (
                              id INT PRIMARY KEY
) ENGINE=InnoDB;

CREATE TABLE child_table (
                             id INT PRIMARY KEY,
                             parent_id INT NOT NULL
    -- MISSING:  FOREIGN KEY (parent_id) REFERENCES parent_table(id)
) ENGINE=InnoDB;