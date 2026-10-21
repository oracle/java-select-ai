/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.util.Arrays;

/**
 * Select AI provider identifiers currently recognized by the SDK.
 * <p>
 * This enum is used for creation and update validation. Existing database
 * profiles may contain provider values introduced by a newer database release;
 * profile metadata hydration preserves those values as strings instead of
 * requiring them to be present in this enum.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Select AI profile provider configuration</a>
 */
public enum Provider {
    /** OpenAI provider. */
    openai,
    /** Cohere provider. */
    cohere,
    /** Azure OpenAI provider. */
    azure,
    /** In-database Select AI provider. */
    database,
    /** OCI Generative AI provider. */
    oci,
    /** Google provider. */
    google,
    /** Anthropic provider. */
    anthropic,
    /** Hugging Face provider. */
    huggingface,
    /** AWS Bedrock provider. */
    aws;

    /**
     * Parses a provider name using case-insensitive matching.
     *
     * @param value provider name supplied to a creation or update builder
     * @return matching provider enum constant, or {@code null} when input is null/blank
     */
    public static Provider fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + value));
    }
}
