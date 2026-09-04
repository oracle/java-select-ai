/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.generate;

import com.oracle.database.selectai.integration.IntegrationTestFixture;

/**
 * Shared defaults for generation and chat-session integration tests.
 */
abstract class GenerateIntegrationFixture extends IntegrationTestFixture {

    protected static final String GENERATE_MODEL = "openai.gpt-5.6-luna";
    protected static final String TRANSLATE_TEXT = "Thank you";
    protected static final String SOURCE_LANGUAGE = "en";
    protected static final String TARGET_LANGUAGE = "de";
    protected static final String EXPECTED_TRANSLATION = "Danke";
}
