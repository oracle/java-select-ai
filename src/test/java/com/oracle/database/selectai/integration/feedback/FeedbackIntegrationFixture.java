/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.feedback;

import com.oracle.database.selectai.integration.IntegrationTestFixture;

/**
 * Shared defaults and expected values for feedback integration tests.
 */
abstract class FeedbackIntegrationFixture extends IntegrationTestFixture {

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
