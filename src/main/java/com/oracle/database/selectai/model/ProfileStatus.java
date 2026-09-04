/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.util.Locale;

/**
 * Initial status values accepted by {@code DBMS_CLOUD_AI.CREATE_PROFILE}.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI.CREATE_PROFILE reference</a>
 */
public enum ProfileStatus {
    /** Profile is enabled after creation. */
    ENABLED("ENABLED"),
    /** Profile is disabled after creation. */
    DISABLED("DISABLED");

    /** DBMS_CLOUD_AI status value. */
    private final String value;

    ProfileStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the DBMS_CLOUD_AI status value.
     *
     * @return status value passed to CREATE_PROFILE
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a status value.
     *
     * @param value status text
     * @return matching profile status, or {@code null} when input is null/blank
     * @throws IllegalArgumentException when value is not enabled or disabled
     */
    public static ProfileStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ENABLED" -> ENABLED;
            case "DISABLED" -> DISABLED;
            default -> throw new IllegalArgumentException("status must be either ENABLED or DISABLED");
        };
    }
}
