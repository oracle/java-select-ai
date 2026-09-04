/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.util.Locale;

/**
 * OCI Generative AI API formats that can be supplied in profile attributes.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Select AI profile provider configuration</a>
 */
public enum OciApiFormat {
    /** OCI endpoint uses Cohere-compatible request/response format. */
    COHERE("COHERE"),
    /** OCI endpoint uses the generic chat API format. */
    GENERIC("GENERIC");

    /** DBMS_CLOUD_AI OCI API format value. */
    private final String value;

    OciApiFormat(String value) {
        this.value = value;
    }

    /**
     * Returns the DBMS_CLOUD_AI OCI API format value.
     *
     * @return OCI API format value
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses an OCI API format value.
     *
     * @param value OCI API format text
     * @return matching OCI API format, or {@code null} when input is null/blank
     * @throws IllegalArgumentException when value is not a supported OCI API format
     */
    public static OciApiFormat fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "COHERE" -> COHERE;
            case "GENERIC" -> GENERIC;
            default -> throw new IllegalArgumentException(
                    "ociApiformat must be either COHERE or GENERIC");
        };
    }
}
