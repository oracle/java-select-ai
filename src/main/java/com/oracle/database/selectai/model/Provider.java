/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.util.Arrays;

/**
 * Supported Select AI provider identifiers accepted by profile attributes.
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
     * @param value provider name from user input or database attributes
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
