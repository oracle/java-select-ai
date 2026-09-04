/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.ConnectionCallback;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.SelectAIException;

import java.sql.SQLException;

/**
 * Connection provider that reuses the single connection exposed by
 * {@link DbConnection}.
 * <p>
 * This provider intentionally does not close the connection after each
 * operation because the connection is owned by the existing DbConnection
 * instance. It preserves the current single-connection behavior.
 */
final class SingleConnectionProvider implements ConnectionProvider, AutoCloseable {
    /** DbConnection wrapper that owns the JDBC connection. */
    private final DbConnection dbConnection;

    /**
     * Creates a provider around a DbConnection.
     *
     * @param dbConnection DbConnection that owns the JDBC connection
     */
    SingleConnectionProvider(DbConnection dbConnection) {
        if (dbConnection == null) {
            throw new IllegalArgumentException("dbConnection must not be null");
        }
        this.dbConnection = dbConnection;
    }

    @Override
    public <T> T withConnection(ConnectionCallback<T> callback) throws SQLException, SelectAIException {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        return callback.execute(dbConnection.getConnection());
    }

    /**
     * Returns the connection wrapper used by this provider.
     *
     * @return DbConnection that owns the JDBC connection
     */
    DbConnection getDbConnection() {
        return dbConnection;
    }

    /**
     * Closes the owned DbConnection.
     *
     * @throws SelectAIException when the underlying JDBC connection cannot be closed
     */
    @Override
    public void close() throws SelectAIException {
        dbConnection.close();
    }
}
