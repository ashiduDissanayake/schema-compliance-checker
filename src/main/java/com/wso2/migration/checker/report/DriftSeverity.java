package com.wso2.migration.checker.report;

/**
 * Severity levels for schema drift items.
 */
public enum DriftSeverity {
    CRITICAL("🔴", "Critical - Blocks Migration"),
    HIGH("🟠", "High - Likely to Cause Issues"),
    MEDIUM("🟡", "Medium - Should Be Reviewed"),
    LOW("🟢", "Low - Minor Difference"),
    INFO("🔵", "Info - Cosmetic Only");

    private final String icon;
    private final String description;

    DriftSeverity(String icon, String description) {
        this.icon = icon;
        this.description = description;
    }

    public String getIcon() { return icon; }
    public String getDescription() { return description; }
}