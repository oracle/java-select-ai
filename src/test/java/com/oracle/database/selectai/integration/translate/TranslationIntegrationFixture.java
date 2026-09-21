/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.translate;

import com.oracle.database.selectai.integration.IntegrationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Shared defaults for integration tests that exercise translation behavior.
 */
abstract class TranslationIntegrationFixture extends IntegrationTestFixture {

    @BeforeEach
    final void setUpConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
    }

    @AfterEach
    final void tearDownConnection() throws Exception {
        closeIsolatedConnection();
    }

    @Override
    protected String profileObjectList() {
        return null;
    }

    protected static final String TRANSLATE_TEXT = "Thank you";
    protected static final String SOURCE_LANGUAGE = "en";
    protected static final String TARGET_LANGUAGE = "de";
    protected static final String EXPECTED_TRANSLATION = "Danke";
}
