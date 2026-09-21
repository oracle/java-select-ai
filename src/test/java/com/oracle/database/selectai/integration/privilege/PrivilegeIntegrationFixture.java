/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.privilege;

import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Feature fixture for privilege and network-access integration tests.
 */
abstract class PrivilegeIntegrationFixture extends IntegrationTestFixture {

    protected DatabaseAdmin databaseAdmin;
    protected SelectAI adminSelectAI;

    @Override
    protected boolean requiresProfile() {
        return false;
    }

    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        var adminConfig = adminDbConfig();
        adminSelectAI = SelectAI.create(adminConfig);
        databaseAdmin = DatabaseAdmin.create(adminConfig);
    }

    @BeforeEach
    final void setUpConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
    }

    @AfterEach
    final void closePrivilegeResources() throws Exception {
        try {
            if (databaseAdmin != null) {
                databaseAdmin.close();
                databaseAdmin = null;
            }
        } finally {
            try {
                if (adminSelectAI != null) {
                    adminSelectAI.close();
                    adminSelectAI = null;
                }
            } finally {
                closeIsolatedConnection();
            }
        }
    }
}
