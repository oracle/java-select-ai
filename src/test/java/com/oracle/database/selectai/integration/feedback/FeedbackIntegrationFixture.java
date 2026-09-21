/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.feedback;

import com.oracle.database.selectai.integration.IntegrationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Shared defaults and expected values for feedback integration tests.
 */
abstract class FeedbackIntegrationFixture extends IntegrationTestFixture {

    @BeforeAll
    static void createFeedbackSchema(TestInfo testInfo) throws Exception {
        createFreshIntegrationSchema(testClassName(testInfo));
    }

    @BeforeEach
    final void setUpConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
    }

    @AfterEach
    final void tearDownConnection() throws Exception {
        closeIsolatedConnection();
    }

    protected static final String PROMPT = "Total points of each gymnasts";
    protected static final String SHOWSQL_SQL_ID = "ahgttusrvh9x5";
    protected static final String RUNSQL_SQL_ID = "6s20ukn8j3p5j";
    protected static final String EXPLAINSQL_SQL_ID = "2a617cynwfm36";
    protected static final String NEGATIVE_RESPONSE =
            "SELECT p.name, g.total_points FROM people p JOIN gymnast g "
                    + "ON p.id = g.id ORDER BY g.total_points DESC";
    protected static final String NEGATIVE_RESPONSE_WITH_NAME_ORDER =
            "SELECT p.name, g.total_points FROM people p JOIN gymnast g "
                    + "ON p.id = g.id ORDER BY p.name ASC";
}
