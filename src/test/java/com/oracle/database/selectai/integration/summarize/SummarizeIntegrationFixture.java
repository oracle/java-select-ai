/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.summarize;

import com.oracle.database.selectai.integration.IntegrationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Shared defaults for summarization integration tests.
 */
abstract class SummarizeIntegrationFixture extends IntegrationTestFixture {

    @BeforeEach
    final void setUpConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
    }

    @AfterEach
    final void tearDownConnection() throws Exception {
        closeIsolatedConnection();
    }

    protected static final String SUMMARY_PROMPT = "Summarize in two sentences";

    protected final String summarizeCredential() {
        String configuredCredential = env("SELECT_AI_IT_SUMMARIZE_CREDENTIAL_NAME");

        return configuredCredential != null
                ? configuredCredential
                : isolatedCredentialName();
    }
}
