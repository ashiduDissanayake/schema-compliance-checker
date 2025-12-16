package com.wso2.migration.checker.model;

import java.util.Objects;

/**
 * Represents a database trigger.
 */
public record TriggerInfo(
        String name,
        String tableName,
        TriggerTiming timing,
        TriggerEvent event,
        String definition,
        boolean isEnabled
) {
    public enum TriggerTiming {
        BEFORE, AFTER, INSTEAD_OF
    }

    public enum TriggerEvent {
        INSERT, UPDATE, DELETE, INSERT_UPDATE, INSERT_DELETE, UPDATE_DELETE, INSERT_UPDATE_DELETE
    }

    public String normalizedSignature() {
        return String.format("%s|%s|%s|%s",
                name.toUpperCase(),
                tableName.toUpperCase(),
                timing,
                event
        );
    }

    public String normalizedDefinition() {
        if (definition == null) return "";
        return definition
                .replaceAll("--.*?\n", "\n")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TriggerInfo that)) return false;
        return this.normalizedSignature().equals(that.normalizedSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedSignature());
    }
}