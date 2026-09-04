/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.util.Locale;

/**
 * Controls how Select AI chooses database objects for prompt metadata.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Select AI profile object-list configuration</a>
 */
public enum ObjectListMode {
    /** Let Select AI automatically choose relevant objects from metadata. */
    AUTOMATED("automated"),
    /** Consider all accessible objects unless constrained by object list. */
    ALL("all");

    /** Wire-format value expected by the {@code object_list_mode} profile attribute. */
    private final String value;

    ObjectListMode(String value) {
        this.value = value;
    }

    /**
     * Returns the value used in the JSON profile attribute payload.
     *
     * @return wire-format string value used in profile attributes
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses an object-list mode using case-insensitive matching.
     *
     * @param value attribute value to parse
     * @return matching enum constant for input value, or {@code null} when input is null/blank
     */
    public static ObjectListMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "automated" -> AUTOMATED;
            case "all" -> ALL;
            default -> throw new IllegalArgumentException(
                    "objectListMode must be one of: automated, all");
        };
    }
}
