/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.syntheticdata;

import com.oracle.database.selectai.integration.IntegrationTestFixture;

/**
 * Shared defaults for synthetic-data integration tests.
 */
abstract class SyntheticDataIntegrationFixture extends IntegrationTestFixture {

    protected static final String SINGLE_OBJECT_NAME = "people";
    protected static final String BATCH_OBJECT_OWNER = "ADMIN";
    protected static final String[] BATCH_OBJECT_NAMES = {"people", "gymnast"};
}
