/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.summarize;

import com.oracle.database.selectai.integration.IntegrationTestFixture;

/**
 * Shared defaults for summarization integration tests.
 */
abstract class SummarizeIntegrationFixture extends IntegrationTestFixture {

    protected static final String SUMMARY_PROMPT = "Summarize in two sentences";
    protected static final String DEFAULT_CREDENTIAL_NAME = "";

    protected final String summarizeCredential() {
        return envOrDefault(
                "SELECT_AI_IT_SUMMARIZE_CREDENTIAL_NAME",
                envOrDefault("SELECT_AI_IT_OCI_CREDENTIAL_NAME", DEFAULT_CREDENTIAL_NAME));
    }
}
