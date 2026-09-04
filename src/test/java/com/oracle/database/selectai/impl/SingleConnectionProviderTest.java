/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleConnectionProviderTest {

    /**
     * Test: Reject a null database connection when constructing the provider.
     * Expected: An IllegalArgumentException identifies the missing connection.
     */
    @Test
    void constructorRejectsNullDbConnection() {
        assertThatThrownBy(() -> new SingleConnectionProvider(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbConnection");
    }

    /**
     * Test: Reject a null callback passed to withConnection().
     * Expected: An IllegalArgumentException identifies the missing callback.
     */
    @Test
    void withConnectionRejectsNullCallback() {
        SingleConnectionProvider provider = new SingleConnectionProvider(mock(DbConnection.class));

        assertThatThrownBy(() -> provider.withConnection(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("callback");
    }

    /**
     * Test: Reuse the configured connection for a successful operation.
     * Expected: The callback receives that connection and the provider does not close it.
     */
    @Test
    void withConnectionReusesLegacyConnectionAndDoesNotCloseIt() throws Exception {
        DbConnection dbConnection = mock(DbConnection.class);
        Connection connection = mock(Connection.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        SingleConnectionProvider provider = new SingleConnectionProvider(dbConnection);

        Boolean result = provider.withConnection(activeConnection -> {
            assertThat(activeConnection).isSameAs(connection);
            return true;
        });

        assertThat(result).isTrue();
        verify(dbConnection).getConnection();
        verify(connection, never()).close();
    }

    /**
     * Test: Propagate a SQL exception raised by the callback.
     * Expected: The same exception is returned and the connection remains open.
     */
    @Test
    void withConnectionPropagatesSqlExceptionFromCallbackWithoutClosingConnection() throws Exception {
        DbConnection dbConnection = mock(DbConnection.class);
        Connection connection = mock(Connection.class);
        SQLException sqlException = new SQLException("operation failed");
        when(dbConnection.getConnection()).thenReturn(connection);
        SingleConnectionProvider provider = new SingleConnectionProvider(dbConnection);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            throw sqlException;
        })).isSameAs(sqlException);

        verify(connection, never()).close();
    }

    /**
     * Test: Handle a closed connection returned by the database connection wrapper.
     * Expected: The closed connection is reused for the next operation and is never closed by the provider.
     */
    @Test
    void withConnectionDoesNotReplaceClosedSingleConnection() throws Exception {
        DbConnection dbConnection = mock(DbConnection.class);
        Connection closedConnection = mock(Connection.class);
        SQLException closedFailure = new SQLException("Connection is closed", "08003", 17002);
        when(dbConnection.getConnection()).thenReturn(closedConnection);
        SingleConnectionProvider provider = new SingleConnectionProvider(dbConnection);
        List<Connection> connectionsUsed = new ArrayList<>();

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            connectionsUsed.add(activeConnection);
            throw closedFailure;
        })).isSameAs(closedFailure);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            connectionsUsed.add(activeConnection);
            throw closedFailure;
        })).isSameAs(closedFailure);

        assertThat(connectionsUsed).containsExactly(closedConnection, closedConnection);
        verify(dbConnection, times(2)).getConnection();
        verify(closedConnection, never()).close();
    }

    /**
     * Test: Return the database connection wrapper owned by the provider.
     * Expected: getDbConnection() returns the configured wrapper instance.
     */
    @Test
    void getDbConnectionReturnsWrappedConnectionOwner() {
        DbConnection dbConnection = mock(DbConnection.class);

        SingleConnectionProvider provider = new SingleConnectionProvider(dbConnection);

        assertThat(provider.getDbConnection()).isSameAs(dbConnection);
    }
}
