/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.databaseadmin;

import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Feature fixture for database-wide administration integration tests.
 */
abstract class DatabaseAdminIntegrationFixture extends IntegrationTestFixture {

    protected DatabaseAdmin databaseAdmin;

    @BeforeAll
    static void createDatabaseAdminSchema(TestInfo testInfo) throws Exception {
        createFreshIntegrationSchema(testClassName(testInfo));
    }

    @BeforeEach
    final void setUpConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
    }

    @Override
    protected String profileObjectList() {
        return objectListFor("people", "gymnast");
    }

    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        databaseAdmin = DatabaseAdmin.create(adminDbConfig());
    }

    @AfterEach
    final void closeDatabaseAdmin() throws Exception {
        try {
            if (databaseAdmin != null) {
                databaseAdmin.close();
                databaseAdmin = null;
            }
        } finally {
            closeIsolatedConnection();
        }
    }
}
