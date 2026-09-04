/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.ConnectionCallback;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.model.SelectAIException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connection provider that borrows a connection from a {@link DataSource} for
 * each SDK operation and closes it after the operation completes.
 * <p>
 * For pooled data sources, closing the connection normally returns it to the
 * pool instead of closing the physical database connection.
 */
final class DataSourceConnectionProvider implements ConnectionProvider {
    /** DataSource used to borrow per-operation connections. */
    private final DataSource dataSource;

    /**
     * Creates a provider backed by a DataSource.
     *
     * @param dataSource DataSource used by SDK operations
     */
    DataSourceConnectionProvider(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.dataSource = dataSource;
    }

    @Override
    public <T> T withConnection(ConnectionCallback<T> callback) throws SQLException, SelectAIException {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        try (Connection connection = dataSource.getConnection()) {
            if (connection == null) {
                throw new SelectAIException("DataSource returned null connection");
            }
            return callback.execute(connection);
        }
    }
}
