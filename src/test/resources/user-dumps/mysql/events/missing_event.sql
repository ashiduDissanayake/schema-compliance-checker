-- Test: EVT-001 - Missing event
-- Expected: MEDIUM drift - scheduled task broken

CREATE TABLE cleanup_log (
                             id INT AUTO_INCREMENT PRIMARY KEY,
                             cleaned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- MISSING: evt_daily_cleanup event
-- MISSING:  evt_hourly_stats event