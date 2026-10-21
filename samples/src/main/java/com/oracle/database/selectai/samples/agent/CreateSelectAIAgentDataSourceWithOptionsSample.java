/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;

import javax.sql.DataSource;
import java.sql.SQLException;

/** Creates a DataSource-backed SelectAIAgent client with execution options. */
public final class CreateSelectAIAgentDataSourceWithOptionsSample {
    private CreateSelectAIAgentDataSourceWithOptionsSample() {
    }

    /**
     * Creates and closes a DataSource-backed SelectAIAgent client.
     *
     * @param args command-line arguments; not used
     * @throws SQLException when DataSource configuration fails
     * @throws SelectAIException when client cleanup fails
     */
    public static void main(String[] args) throws SQLException, SelectAIException {
        DataSource dataSource = SelectAIAgentConnectionSampleSupport.dataSource();
        SelectAIOptions options = SelectAIAgentConnectionSampleSupport.options();
        try (SelectAIAgent ignored = SelectAIAgent.create(dataSource, options)) {
            System.out.printf("Created DataSource-backed SelectAIAgent client with query timeout: %s%n",
                    options.getQueryTimeoutSeconds() == null
                            ? "not configured" : options.getQueryTimeoutSeconds());
        }
    }
}
