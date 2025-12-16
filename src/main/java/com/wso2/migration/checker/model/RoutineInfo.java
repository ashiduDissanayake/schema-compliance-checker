package com.wso2.migration. checker.model;

import java. util.List;
import java. util.Objects;

/**
 * Represents a stored routine (procedure, function, package).
 */
public record RoutineInfo(
        String name,
        String schema,
        RoutineType type,
        String returnType,
        List<ParameterInfo> parameters,
        String definition,
        String language
) {
    public enum RoutineType {
        PROCEDURE, FUNCTION, PACKAGE, PACKAGE_BODY
    }

    public record ParameterInfo(
            String name,
            String dataType,
            ParameterMode mode,
            int ordinalPosition
    ) {
        public enum ParameterMode {
            IN, OUT, INOUT
        }
    }

    public String normalizedSignature() {
        String params = parameters != null ? parameters. stream()
                .map(p -> p.name().toUpperCase() + ":" + p.dataType().toUpperCase() + ":" + p.mode())
                .reduce((a, b) -> a + "," + b)
                .orElse("") : "";

        return String.format("%s|%s|%s|%s",
                name.toUpperCase(),
                type,
                returnType != null ? returnType.toUpperCase() : "VOID",
                params
        );
    }

    /**
     * Normalizes definition for comparison by removing whitespace and comments.
     */
    public String normalizedDefinition() {
        if (definition == null) return "";
        return definition
                .replaceAll("--.*?\n", "\n")           // Remove single-line comments
                .replaceAll("/\\*.*?\\*/", "")         // Remove multi-line comments
                .replaceAll("\\s+", " ")               // Normalize whitespace
                .trim()
                .toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoutineInfo that)) return false;
        return this.normalizedSignature().equals(that.normalizedSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedSignature());
    }
}