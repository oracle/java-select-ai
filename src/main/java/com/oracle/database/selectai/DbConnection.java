/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.impl.DbConnectionFactory;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

import java.sql.Connection;

/**
 * Connection contract used by SDK implementations to execute SQL and PL/SQL
 * against an Oracle database that has access to {@code DBMS_CLOUD_AI}.
 * <p>
 * {@code DbConnection} extends {@link AutoCloseable}. A {@code DbConnection}
 * instance represents a JDBC-backed database connection used by the SDK.
 * Callers should close the {@code DbConnection} when they own it and no longer
 * need it, either by using try-with-resources or by calling {@link #close()}
 * explicitly.
 * <p>
 * When a {@link SelectAI} client is created from
 * {@link com.oracle.database.selectai.model.DbConnectionConfig}, the SDK owns the
 * underlying JDBC connection and closes it when {@link SelectAI#close()} is
 * called. When application code creates or holds a {@code DbConnection}
 * directly, that application code is responsible for closing it.
 */
public interface DbConnection extends AutoCloseable {

    /**
     * Creates a JDBC-backed DbConnection from database connection configuration.
     * <p>
     * The returned object owns one JDBC connection. Call {@link #close()} when
     * the connection is no longer needed.
     *
     * @param dbConnectionConfig database connection configuration
     * @return initialized DbConnection
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the JDBC connection cannot be initialized
     */
    static DbConnection create(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        return DbConnectionFactory.create(dbConnectionConfig);
    }

    /**
     * Returns the JDBC URL used to create the underlying connection.
     *
     * @return JDBC URL
     */
    String getJdbcUrl();

    /**
     * Returns the database user that owns or invokes the Select AI resources.
     *
     * @return database username
     */
    String getDbUser();

    /**
     * Returns the underlying JDBC connection used by SDK operations.
     * <p>
     * The returned connection is not a defensive copy. Closing or mutating it
     * directly affects SDK operations that use this {@code DbConnection}. Do not
     * share one returned JDBC connection across multiple application threads
     * unless caller-side synchronization is used.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/GetConnectionSample.html">
     * GetConnectionSample source</a>.
     *
     * @return JDBC connection
     */
    Connection getConnection();

    /**
     * Closes the JDBC connection owned by this DbConnection.
     * <p>
     * Calling this method is idempotent for SDK-provided implementations. After
     * close, callers must not continue using SDK objects that depend on this
     * connection.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/CloseSelectAISample.html">
     * CloseSelectAISample source</a>.
     *
     * @throws SelectAIException when the underlying JDBC connection cannot be closed
     */
    @Override
    void close() throws SelectAIException;
}
