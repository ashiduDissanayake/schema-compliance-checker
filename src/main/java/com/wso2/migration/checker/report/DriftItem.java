package com.wso2.migration.checker.report;

/**
 * Represents a single schema drift detection.
 */
public record DriftItem(
        String category,
        String objectType,
        String objectName,
        DriftType driftType,
        DriftSeverity severity,
        String standardValue,
        String userValue,
        String description,
        String recommendation
) {
    public enum DriftType {
        MISSING_IN_USER("Missing in User Schema"),
        MISSING_IN_STANDARD("Extra in User Schema"),
        MODIFIED("Modified"),
        TYPE_MISMATCH("Data Type Mismatch"),
        DEFINITION_CHANGED("Definition Changed");

        private final String label;
        DriftType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /**
     * Creates a drift item for missing objects.
     */
    public static DriftItem missing(String category, String objectType, String name, DriftSeverity severity) {
        return new DriftItem(
                category, objectType, name,
                DriftType.MISSING_IN_USER, severity,
                "Present", "Missing",
                String.format("%s '%s' exists in standard but missing in user schema", objectType, name),
                String.format("Add the missing %s '%s' to match the standard schema", objectType. toLowerCase(), name)
        );
    }

    /**
     * Creates a drift item for extra objects.
     */
    public static DriftItem extra(String category, String objectType, String name, DriftSeverity severity) {
        return new DriftItem(
                category, objectType, name,
                DriftType.MISSING_IN_STANDARD, severity,
                "Not Present", "Present",
                String.format("%s '%s' exists in user schema but not in standard", objectType, name),
                String.format("Review if %s '%s' should be removed or is a custom addition", objectType.toLowerCase(), name)
        );
    }

    /**
     * Creates a drift item for modified objects.
     */
    public static DriftItem modified(String category, String objectType, String name,
                                     DriftSeverity severity, String stdVal, String userVal, String detail) {
        return new DriftItem(
                category, objectType, name,
                DriftType.MODIFIED, severity,
                stdVal, userVal,
                detail,
                String.format("Modify %s '%s' to match the standard schema", objectType. toLowerCase(), name)
        );
    }
}