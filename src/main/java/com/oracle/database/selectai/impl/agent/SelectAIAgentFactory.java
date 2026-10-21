/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;

import javax.sql.DataSource;

/**
 * Bridge factory used by the public {@link SelectAIAgent} interface to create
 * internal agent client implementations.
 * <p>
 * Application code should use the factory methods on {@link SelectAIAgent}
 * instead of using this class directly.
 */
public final class SelectAIAgentFactory {
    /** Utility class. */
    private SelectAIAgentFactory() {
    }

    /**
     * Creates an agent client from database connection configuration.
     *
     * @param dbConnectionConfig database connection configuration
     * @return SelectAIAgent client
     * @throws SelectAIException when initialization fails
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentWithOptionsSample.html">
     *      CreateSelectAIAgentWithOptionsSample source</a>
     */
    public static SelectAIAgent create(DbConnectionConfig dbConnectionConfig)
            throws SelectAIException {
        return new DefaultSelectAIAgent(dbConnectionConfig);
    }

    /**
     * Creates an agent client from database connection configuration and SDK
     * execution options.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options
     * @return SelectAIAgent client
     * @throws SelectAIException when initialization fails
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentWithOptionsSample.html">
     *      CreateSelectAIAgentWithOptionsSample source</a>
     */
    public static SelectAIAgent create(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        return new DefaultSelectAIAgent(dbConnectionConfig, options);
    }

    /**
     * Creates a DataSource-backed agent client.
     *
     * @param dataSource DataSource used to obtain operation connections
     * @return SelectAIAgent client
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentDataSourceSample.html">
     *      CreateSelectAIAgentDataSourceSample source</a>
     */
    public static SelectAIAgent create(DataSource dataSource) {
        return new DefaultSelectAIAgent(dataSource);
    }

    /**
     * Creates a DataSource-backed agent client with SDK execution options.
     *
     * @param dataSource DataSource used to obtain operation connections
     * @param options SDK execution options
     * @return SelectAIAgent client
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentDataSourceWithOptionsSample.html">
     *      CreateSelectAIAgentDataSourceWithOptionsSample source</a>
     */
    public static SelectAIAgent create(DataSource dataSource, SelectAIOptions options) {
        return new DefaultSelectAIAgent(dataSource, options);
    }
}
