/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.model.SelectAIException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional unit of JDBC work executed synchronously with a connection
 * supplied by a {@link ConnectionProvider}.
 * <p>
 * The callback does not own the supplied connection and must not close it,
 * retain it, or use it after this method returns. Connection acquisition and
 * operation lifecycle are controlled by the {@link ConnectionProvider}; a
 * retained connection is closed by its owning {@link SelectAI},
 * {@link DatabaseAdmin}, or {@link DbConnection} object.
 * <p>
 * Depending on the provider, the connection may be borrowed for one operation
 * or reused across multiple SDK operations.
 *
 * @param <T> result type returned by the JDBC work; may be {@code null}
 */
@FunctionalInterface
public interface ConnectionCallback<T> {
    /**
     * Executes JDBC work with the connection supplied by the provider.
     * <p>
     * This method is invoked synchronously. Any JDBC resources created by the
     * callback, such as statements or result sets, must be closed by the
     * callback. The callback must not close the supplied connection.
     *
     * @param connection non-null JDBC connection supplied for the current operation
     * @return result produced by the JDBC work, or {@code null}
     * @throws SQLException if a JDBC operation performed by the callback fails
     * @throws SelectAIException when SDK-specific validation or nested work fails
     * @throws RuntimeException when the callback fails with an unchecked exception
     */
    T execute(Connection connection) throws SQLException, SelectAIException;
}
