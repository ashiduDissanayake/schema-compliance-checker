package com.wso2.migration. checker.model;

import java. util.List;
import java. util.Objects;

/**
 * Represents a database view.
 */
public record ViewInfo(
        String name,
        String schema,
        List<String> columns,
        String definition,
        boolean isUpdatable
) {
    public String normalizedSignature() {
        String cols = columns != null ? columns.stream()
                .map(String:: toUpperCase)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("") : "";
        return String.format("%s|%s", name.toUpperCase(), cols);
    }

    public String normalizedDefinition() {
        if (definition == null) return "";
        return definition
                . replaceAll("--.*?\n", "\n")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ViewInfo that)) return false;
        return this.normalizedSignature().equals(that.normalizedSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedSignature());
    }
}