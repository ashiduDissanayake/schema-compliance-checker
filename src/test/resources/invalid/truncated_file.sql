-- Test: ERR-005 - Truncated file handling

CREATE TABLE complete_table (
                                id INT PRIMARY KEY,
                                name VARCHAR(100),
                                email VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE incomplete_table (
                                  id INT PRIMARY KEY,
                                  name VARCHAR(100),
                                  description TEXT,
                                  status VARCHAR(20
-- File truncated here - incomplete SQL