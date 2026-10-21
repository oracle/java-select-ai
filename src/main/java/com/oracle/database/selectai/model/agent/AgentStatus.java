/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import java.util.Locale;

/**
 * Status values for Select AI agents.
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateAgentSample.html">
 *      CreateAgentSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadAgentSample.html">
 *      LoadAgentSample source</a>
 * @since 1.1.0
 */
public enum AgentStatus {
    /** Resource is enabled after creation. */
    ENABLED("ENABLED"),
    /** Resource is disabled after creation. */
    DISABLED("DISABLED");

    /** Database status value. */
    private final String value;

    AgentStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the database status value.
     *
     * @return database status value
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a database status value.
     *
     * @param value database status value; comparison is case-insensitive
     * @return matching status, or {@code null} when the value is null or blank
     * @throws IllegalArgumentException when the value is not {@code ENABLED}
     *                                  or {@code DISABLED}
     */
    public static AgentStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ENABLED" -> ENABLED;
            case "DISABLED" -> DISABLED;
            default -> throw new IllegalArgumentException("status must be either ENABLED or DISABLED");
        };
    }
}
