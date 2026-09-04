/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.provider;

import com.oracle.database.selectai.integration.IntegrationTestFixture;

/**
 * Provider-specific defaults shared by provider integration tests.
 */
abstract class ProviderIntegrationFixture extends IntegrationTestFixture {

    protected static final String CHAT_PROMPT =
            "What is 8 + 9? Reply with only the integer.";
    protected static final String PROFILE_DESCRIPTION =
            "Java provider integration test profile";

    protected static final String DEFAULT_OPENAI_MODEL = "gpt-5.6-luna";
    protected static final String DEFAULT_OPENAI_COMPATIBLE_MODEL = "gpt-5.6-luna";
    protected static final String DEFAULT_COHERE_MODEL = "command-a-03-2025";
    protected static final String DEFAULT_AZURE_RESOURCE_NAME = "ADBST-AI-RESOURCE-JAPAN-EAST";
    protected static final String DEFAULT_AZURE_DEPLOYMENT_NAME =
            "ADBST-AI-RESOURCE-JAPAN-EAST-DEPLOYMENT";
    protected static final String DEFAULT_AZURE_EMBEDDING_DEPLOYMENT_NAME =
            "ADBST-AI-RESOURCE-JAPAN-EAST-text-embedding-3-large";
    protected static final String DEFAULT_AWS_MODEL = "meta.llama3-70b-instruct-v1:0";
    protected static final String DEFAULT_GOOGLE_MODEL = "gemini-3-flash-preview";
    protected static final String DEFAULT_ANTHROPIC_MODEL = "claude-opus-4-6";
    protected static final String DEFAULT_HUGGINGFACE_MODEL = "test-model";
    protected static final String DEFAULT_REGION = "us-chicago-1";
    protected static final String DEFAULT_OCI_API_FORMAT = "GENERIC";
}
