
-- Test: VW-001 - Missing view
-- Expected: MEDIUM drift - reporting/query broken

CREATE TABLE employees (
                           id INT AUTO_INCREMENT PRIMARY KEY,
                           name VARCHAR(100),
                           department VARCHAR(50),
                           salary DECIMAL(10,2),
                           status VARCHAR(20) DEFAULT 'active'
) ENGINE=InnoDB;

-- MISSING: vw_active_employees view
-- MISSING:  vw_department_summary view