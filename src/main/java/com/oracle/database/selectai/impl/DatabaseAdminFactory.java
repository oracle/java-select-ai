/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;

import javax.sql.DataSource;

/**
 * Bridge factory used by the public {@link DatabaseAdmin} interface to create
 * internal implementation instances.
 * <p>
 * Application code should use {@link DatabaseAdmin#create(DbConnectionConfig)}
 * or {@link DatabaseAdmin#create(DataSource)} instead of using this class
 * directly.
 */
public final class DatabaseAdminFactory {
    /** Utility class. */
    private DatabaseAdminFactory() {
    }

    /**
     * Creates a DatabaseAdmin client from database connection configuration.
     *
     * @param dbConnectionConfig database connection configuration
     * @return DatabaseAdmin client
     * @throws SelectAIException when initialization fails
     */
    public static DatabaseAdmin create(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        return new DefaultDatabaseAdmin(dbConnectionConfig);
    }

    /**
     * Creates a DatabaseAdmin client from database connection configuration and
     * SDK execution options.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options
     * @return DatabaseAdmin client
     * @throws SelectAIException when initialization fails
     */
    public static DatabaseAdmin create(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        return new DefaultDatabaseAdmin(dbConnectionConfig, options);
    }

    /**
     * Creates a DataSource-backed DatabaseAdmin client.
     *
     * @param dataSource DataSource used to obtain operation connections
     * @return DatabaseAdmin client
     */
    public static DatabaseAdmin create(DataSource dataSource) {
        return new DefaultDatabaseAdmin(dataSource);
    }

    /**
     * Creates a DataSource-backed DatabaseAdmin client with SDK execution options.
     *
     * @param dataSource DataSource used to obtain operation connections
     * @param options SDK execution options
     * @return DatabaseAdmin client
     */
    public static DatabaseAdmin create(DataSource dataSource, SelectAIOptions options) {
        return new DefaultDatabaseAdmin(dataSource, options);
    }
}
