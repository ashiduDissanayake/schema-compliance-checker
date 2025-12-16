package com.wso2.migration.checker.model;

import java.util.Objects;

/**
 * Represents a database sequence (Oracle/PostgreSQL style).
 */
public record SequenceInfo(
        String name,
        String schema,
        long startValue,
        long incrementBy,
        Long minValue,
        Long maxValue,
        boolean isCycling,
        int cacheSize
) {
    public String normalizedSignature() {
        return String.format("%s|%d|%d|%b",
                name.toUpperCase(),
                startValue,
                incrementBy,
                isCycling
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SequenceInfo that)) return false;
        return this.normalizedSignature().equals(that.normalizedSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedSignature());
    }
}