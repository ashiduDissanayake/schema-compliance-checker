package com.wso2.migration. checker.model;

import java.util.Objects;

/**
 * Represents a database column with all its metadata.
 */
public record ColumnInfo(
        String name,
        String dataType,
        int size,
        int scale,
        boolean nullable,
        String defaultValue,
        boolean isPrimaryKey,
        boolean isForeignKey,
        boolean isAutoIncrement,
        int ordinalPosition
) {
    /**
     * Creates a normalized identifier for comparison (ignores case and whitespace differences).
     */
    public String normalizedSignature() {
        return String.format("%s|%s|%d|%d|%b|%b|%b",
                name. toUpperCase().trim(),
                normalizeDataType(dataType),
                size,
                scale,
                nullable,
                isPrimaryKey,
                isForeignKey
        );
    }

    private String normalizeDataType(String type) {
        if (type == null) return "UNKNOWN";
        // Normalize common type aliases across databases
        String normalized = type.toUpperCase().trim();
        return switch (normalized) {
            case "INT", "INTEGER", "INT4" -> "INTEGER";
            case "BIGINT", "INT8" -> "BIGINT";
            case "VARCHAR2" -> "VARCHAR";
            case "NUMBER" -> "NUMERIC";
            case "DATETIME2", "TIMESTAMP" -> "TIMESTAMP";
            default -> normalized;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnInfo that)) return false;
        return this.normalizedSignature().equals(that.normalizedSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedSignature());
    }
}