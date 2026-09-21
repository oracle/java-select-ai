/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.connection;

import com.oracle.database.selectai.integration.IntegrationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Feature fixture for connection and JDBC integration tests.
 */
abstract class ConnectionIntegrationFixture extends IntegrationTestFixture {

    @Override
    protected boolean requiresProfile() {
        return false;
    }

    @BeforeEach
    final void setUpConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
    }

    @AfterEach
    final void tearDownConnection() throws Exception {
        closeIsolatedConnection();
    }
}
