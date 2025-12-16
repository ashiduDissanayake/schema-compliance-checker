package com.wso2.migration.checker.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a database constraint (PK, FK, UNIQUE, CHECK).
 */
public record ConstraintInfo(
        String name,
        String tableName,
        ConstraintType type,
        List<String> columns,
        String referencedTable,
        List<String> referencedColumns,
        String onDeleteAction,
        String onUpdateAction,
        String checkExpression
) {
    public enum ConstraintType {
        PRIMARY_KEY, FOREIGN_KEY, UNIQUE, CHECK
    }

    public String normalizedSignature() {
        String cols = columns != null ? columns.stream()
                .map(String::toUpperCase)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("") : "";

        String refCols = referencedColumns != null ? referencedColumns.stream()
                .map(String:: toUpperCase)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("") : "";

        return String.format("%s|%s|%s|%s|%s",
                tableName.toUpperCase(),
                type,
                cols,
                referencedTable != null ? referencedTable.toUpperCase() : "",
                refCols
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConstraintInfo that)) return false;
        return this.normalizedSignature().equals(that.normalizedSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedSignature());
    }
}