/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.model.SelectAIException;

import java.sql.SQLException;

/**
 * Supplies JDBC connections for SDK operations.
 * <p>
 * SDK-provided DataSource-backed implementations obtain a connection lazily
 * for each operation and close it when the operation completes. If the
 * DataSource is pooled, closing normally returns the connection to the pool.
 * The SDK does not own or close the application-provided DataSource.
 * <p>
 * SDK-provided single-connection implementations reuse one retained JDBC
 * connection for the lifetime of the owning client or connection object. The
 * connection is opened when the owner is created and is closed when the owner
 * is closed.
 * <p>
 * Connection ownership and cleanup depend on the implementation. A retained
 * connection is closed by its owning {@code SelectAI}, {@code DatabaseAdmin},
 * or {@code DbConnection} object.
 * <p>
 * This interface does not define transaction boundaries. SDK-provided
 * implementations do not explicitly commit or roll back transactions and do
 * not change the connection {@code autoCommit} setting. Any implicit commit or
 * rollback performed by the underlying Oracle Database API is outside the
 * SDK's transaction management.
 * <p>
 * Thread safety is implementation-specific and is not guaranteed by this
 * interface. DataSource-backed implementations may support concurrent
 * independent operations according to the {@code DataSource} contract. A
 * single-connection implementation should be treated as single-threaded
 * unless the connection owner provides synchronization.
 * <p>
 * Application code normally obtains SDK clients through public factory methods
 * such as {@link SelectAI#create(javax.sql.DataSource)} and
 * {@link SelectAI#create(com.oracle.database.selectai.model.DbConnectionConfig)}.
 * Concrete provider implementations are internal SDK details.
 */
public interface ConnectionProvider {
    /**
     * Executes one SDK operation with a connection supplied by this provider.
     * <p>
     * The callback is invoked synchronously. The callback must close any JDBC
     * resources it creates, such as statements and result sets, but must not
     * close, retain, or use the supplied connection after the callback returns.
     * <p>
     * For SDK-provided DataSource implementations, the connection is borrowed
     * for the operation and closed afterward. For SDK-provided single-connection
     * implementations, the connection is reused and remains owned by the
     * configured connection owner.
     *
     * @param callback non-null callback containing the JDBC operation
     * @param <T> result type returned by the callback; may be {@code null}
     * @return result returned by the callback
     * @throws IllegalArgumentException when {@code callback} is null
     * @throws SQLException if connection acquisition or a JDBC operation
     *         performed by the callback fails
     * @throws SelectAIException when SDK-specific work fails
     * @throws RuntimeException when the callback fails with an unchecked exception
     */
    <T> T withConnection(ConnectionCallback<T> callback) throws SQLException, SelectAIException;
}
