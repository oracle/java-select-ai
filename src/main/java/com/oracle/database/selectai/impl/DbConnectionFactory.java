/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

/**
 * Bridge factory used by the public {@link DbConnection} interface to create
 * internal JDBC-backed connection instances.
 * <p>
 * Application code should use {@link DbConnection#create(DbConnectionConfig)}
 * instead of using this class directly.
 */
public final class DbConnectionFactory {
    /** Utility class. */
    private DbConnectionFactory() {
    }

    /**
     * Creates a JDBC-backed DbConnection from database connection configuration.
     *
     * @param dbConnectionConfig database connection configuration
     * @return JDBC-backed DbConnection
     * @throws SelectAIException when the JDBC connection cannot be initialized
     */
    public static DbConnection create(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        if (dbConnectionConfig == null) {
            throw new IllegalArgumentException("dbConnectionConfig must not be null");
        }
        return DefaultDbConnection.builder()
                .dbUser(dbConnectionConfig.getDbUser())
                .dbPassword(dbConnectionConfig.getDbPassword())
                .walletPassword(dbConnectionConfig.getWalletPassword())
                .jdbcUrl(dbConnectionConfig.getJdbcUrl())
                .jdbcProperties(dbConnectionConfig.getJdbcProperties())
                .build();
    }
}
