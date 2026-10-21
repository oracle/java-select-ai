/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable value object for a Select AI Agent tool type.
 * <p>
 * This type intentionally is not a closed Java {@code enum}. The constants
 * cover tool types known by this SDK release, while {@link #of(String)} also
 * accepts tool types introduced by newer database releases. This keeps
 * database metadata forward-compatible without requiring an SDK release for
 * every new tool type.
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
 *      CreateToolSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
 *      LoadToolSample source</a>
 * @since 1.1.0
 */
public final class ToolType {
    /** SQL tool for natural-language-to-SQL workflows. */
    public static final ToolType SQL = new ToolType("SQL");
    /** RAG tool for vector-index retrieval workflows. */
    public static final ToolType RAG = new ToolType("RAG");
    /** Web-search tool. */
    public static final ToolType WEBSEARCH = new ToolType("WEBSEARCH");
    /** Notification tool. */
    public static final ToolType NOTIFICATION = new ToolType("NOTIFICATION");
    /** Human-input tool. */
    public static final ToolType HUMAN = new ToolType("HUMAN");

    /** Canonical known type or the original future database value. */
    private final String value;

    private ToolType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("toolType must not be null or blank");
        }
        String trimmed = value.trim();
        this.value = canonicalKnownValue(trimmed);
    }

    /**
     * Creates a tool type from a database or caller-supplied value.
     *
     * @param value database tool type; known values are normalized to their
     *        canonical uppercase representation and unknown values are retained
     * @return tool type value
     * @throws IllegalArgumentException when {@code value} is null or blank
     */
    public static ToolType of(String value) {
        return new ToolType(value);
    }

    /**
     * Returns the value sent to or read from the database.
     *
     * @return raw future value or canonical value for a known tool type
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Indicates whether this value is one of the tool types known by this SDK.
     *
     * @return {@code true} for a known SDK tool type
     */
    public boolean isKnown() {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SQL", "RAG", "WEBSEARCH", "NOTIFICATION", "HUMAN" -> true;
            default -> false;
        };
    }

    private static String canonicalKnownValue(String value) {
        String uppercase = value.toUpperCase(Locale.ROOT);
        return switch (uppercase) {
            case "SQL", "RAG", "WEBSEARCH", "NOTIFICATION", "HUMAN" -> uppercase;
            default -> value;
        };
    }

    /**
     * Compares this tool type with another tool type value.
     *
     * @param other object to compare
     * @return {@code true} when both types have the same value
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolType that)) {
            return false;
        }
        return value.equals(that.value);
    }

    /**
     * Returns a hash code based on the tool type value.
     *
     * @return tool type hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * Returns the database tool type value.
     *
     * @return tool type value
     */
    @Override
    public String toString() {
        return value;
    }
}
