package com.wso2.migration.checker.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream. Collectors;

/**
 * Represents a complete database table structure.
 */
public record TableInfo(
        String name,
        String schema,
        List<ColumnInfo> columns,
        List<IndexInfo> indexes,
        List<ConstraintInfo> constraints,
        String tableType,
        String engine,
        String comment
) {
    /**
     * Creates a map of columns by name for quick lookup.
     */
    public Map<String, ColumnInfo> columnMap() {
        return columns.stream()
                .collect(Collectors.toMap(
                        c -> c.name().toUpperCase(),
                        c -> c,
                        (a, b) -> a
                ));
    }

    public String normalizedSignature() {
        String cols = columns.stream()
                .map(ColumnInfo::normalizedSignature)
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        return String.format("%s|%s", name.toUpperCase(), cols);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableInfo that)) return false;
        return name.equalsIgnoreCase(that.name) &&
                columns.size() == that.columns.size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(name. toUpperCase());
    }
}