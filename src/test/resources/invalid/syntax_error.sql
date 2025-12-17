-- Test: ERR-003 - SQL syntax error handling

CREATE TABLE valid_table (
                             id INT PRIMARY KEY
);

-- Intentional syntax errors below
CREATE TABL broken_syntax (
    id INT
);

SELEC * FORM another_error;

INSERT INTO nonexistent_table VALUSE (1, 2, 3);