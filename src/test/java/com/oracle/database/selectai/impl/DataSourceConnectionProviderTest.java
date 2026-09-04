/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSourceConnectionProviderTest {

    /**
     * Test: Reject a null data source when constructing the provider.
     * Expected: An IllegalArgumentException identifies the missing data source.
     */
    @Test
    void constructorRejectsNullDataSource() {
        assertThatThrownBy(() -> new DataSourceConnectionProvider(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSource");
    }

    /**
     * Test: Reject a null callback passed to withConnection().
     * Expected: An IllegalArgumentException identifies the missing callback.
     */
    @Test
    void withConnectionRejectsNullCallback() {
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(mock(DataSource.class));

        assertThatThrownBy(() -> provider.withConnection(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("callback");
    }

    /**
     * Test: Handle a data source that returns a null connection.
     * Expected: A SelectAIException reports the null connection.
     */
    @Test
    void withConnectionRejectsNullConnectionFromDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(null);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> true))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("null connection");
    }

    /**
     * Test: Handle failure while acquiring a connection from the data source.
     * Expected: The acquisition exception is propagated and the callback is not invoked.
     */
    @Test
    void withConnectionPropagatesDataSourceAcquisitionFailureWithoutInvokingCallback() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLNonTransientConnectionException connectionFailure =
                new SQLNonTransientConnectionException("database service unavailable", "08001", 12541);
        when(dataSource.getConnection()).thenThrow(connectionFailure);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            throw new AssertionError("Callback must not run when connection acquisition fails");
        })).isSameAs(connectionFailure);

        verify(dataSource).getConnection();
    }

    /**
     * Test: Borrow and release a connection after a successful operation.
     * Expected: The callback receives the borrowed connection and it is closed afterward.
     */
    @Test
    void withConnectionBorrowsConnectionAndClosesItAfterSuccess() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        Boolean result = provider.withConnection(activeConnection -> {
            assertThat(activeConnection).isSameAs(connection);
            return true;
        });

        assertThat(result).isTrue();
        verify(dataSource).getConnection();
        verify(connection).close();
    }

    /**
     * Test: Release a borrowed connection when the callback raises a SQL exception.
     * Expected: The same SQL exception is propagated and the connection is closed.
     */
    @Test
    void withConnectionClosesBorrowedConnectionAfterSqlException() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException sqlException = new SQLException("operation failed", "42000", 20000);
        when(dataSource.getConnection()).thenReturn(connection);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            throw sqlException;
        })).isSameAs(sqlException);

        verify(connection).close();
    }

    /**
     * Test: Release a borrowed connection when the callback raises a SelectAIException.
     * Expected: The same SelectAIException is propagated and the connection is closed.
     */
    @Test
    void withConnectionClosesBorrowedConnectionAfterSelectAIException() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SelectAIException exception = new SelectAIException("select ai failed");
        when(dataSource.getConnection()).thenReturn(connection);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            throw exception;
        })).isSameAs(exception);

        verify(connection).close();
    }

    /**
     * Test: Release a borrowed connection when the callback times out.
     * Expected: The timeout is propagated and the connection is closed.
     */
    @Test
    void withConnectionClosesBorrowedConnectionAfterTimeout() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLTimeoutException timeout = new SQLTimeoutException("operation timed out", "57014", 1013);
        when(dataSource.getConnection()).thenReturn(connection);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            throw timeout;
        })).isSameAs(timeout);

        verify(connection).close();
    }

    /**
     * Test: Release a borrowed connection when the callback is cancelled.
     * Expected: The cancellation is propagated and the connection is closed.
     */
    @Test
    void withConnectionClosesBorrowedConnectionAfterCancellation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        CancellationException cancellation = new CancellationException("operation cancelled");
        when(dataSource.getConnection()).thenReturn(connection);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            throw cancellation;
        })).isSameAs(cancellation);

        verify(connection).close();
    }

    /**
     * Test: Acquire a fresh connection after a previous operation fails.
     * Expected: The failed connection is closed and the next operation uses and closes a new connection.
     */
    @Test
    void withConnectionUsesFreshConnectionAfterPreviousOperationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection failedConnection = mock(Connection.class);
        Connection recoveredConnection = mock(Connection.class);
        SQLException operationFailure = new SQLException("connection interrupted", "08006", 17002);
        when(dataSource.getConnection()).thenReturn(failedConnection, recoveredConnection);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            assertThat(activeConnection).isSameAs(failedConnection);
            throw operationFailure;
        })).isSameAs(operationFailure);

        String recovered = provider.withConnection(activeConnection -> {
            assertThat(activeConnection).isSameAs(recoveredConnection);
            return "recovered";
        });
        assertThat(recovered).isEqualTo("recovered");

        verify(dataSource, times(2)).getConnection();
        verify(failedConnection).close();
        verify(recoveredConnection).close();
    }
}
