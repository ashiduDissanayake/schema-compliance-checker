package com.wso2.migration.checker.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a database index.
 */
public record IndexInfo(
        String name,
        String tableName,
        List<String> columns,
        boolean isUnique,
        boolean isClustered,
        String indexType
) {
    public String normalizedSignature() {
        String cols = columns.stream()
                .map(String::toUpperCase)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return String.format("%s|%s|%b|%s",
                tableName.toUpperCase(),
                cols,
                isUnique,
                indexType != null ? indexType. toUpperCase() : "BTREE"
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexInfo that)) return false;
        return this.normalizedSignature().equals(that.normalizedSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedSignature());
    }
}