/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;

import javax.sql.DataSource;

/**
 * Bridge factory used by the public {@link SelectAI} interface to create
 * internal implementation instances.
 * <p>
 * Application code should use {@link SelectAI#create(DbConnectionConfig)} or
 * {@link SelectAI#create(DataSource)} instead of using this class directly.
 */
public final class SelectAIFactory {
    /** Utility class. */
    private SelectAIFactory() {
    }

    /**
     * Creates a SelectAI client from database connection configuration.
     *
     * @param dbConnectionConfig database connection configuration
     * @return SelectAI client
     * @throws SelectAIException when initialization fails
     */
    public static SelectAI create(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        return new DefaultSelectAI(dbConnectionConfig);
    }

    /**
     * Creates a SelectAI client from database connection configuration and SDK
     * execution options.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options
     * @return SelectAI client
     * @throws SelectAIException when initialization fails
     */
    public static SelectAI create(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        return new DefaultSelectAI(dbConnectionConfig, options);
    }

    /**
     * Creates a DataSource-backed SelectAI client.
     *
     * @param dataSource DataSource used to obtain operation connections
     * @return SelectAI client
     */
    public static SelectAI create(DataSource dataSource) {
        return new DefaultSelectAI(dataSource);
    }

    /**
     * Creates a DataSource-backed SelectAI client with SDK execution options.
     *
     * @param dataSource DataSource used to obtain operation connections
     * @param options SDK execution options
     * @return SelectAI client
     */
    public static SelectAI create(DataSource dataSource, SelectAIOptions options) {
        return new DefaultSelectAI(dataSource, options);
    }
}
