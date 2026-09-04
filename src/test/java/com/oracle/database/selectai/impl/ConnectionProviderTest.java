/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionProviderTest {

    /**
     * Test: Close a data-source connection after a successful operation.
     * Expected: The callback receives the borrowed connection and it is closed afterward.
     */
    @Test
    void dataSourceProviderClosesBorrowedConnectionAfterSuccess() throws Exception {
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
     * Test: Close a data-source connection after a SQL failure.
     * Expected: The SQL exception is propagated and the borrowed connection is closed.
     */
    @Test
    void dataSourceProviderClosesBorrowedConnectionAfterSqlFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException sqlException = new SQLException("operation failed", "42000", 20000);
        when(dataSource.getConnection()).thenReturn(connection);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> {
            throw sqlException;
        }))
                .isSameAs(sqlException);

        verify(connection).close();
    }

    /**
     * Test: Reject a null connection returned by a data source.
     * Expected: A SelectAIException reports the null connection.
     */
    @Test
    void dataSourceProviderRejectsNullBorrowedConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(null);
        DataSourceConnectionProvider provider = new DataSourceConnectionProvider(dataSource);

        assertThatThrownBy(() -> provider.withConnection(activeConnection -> true))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("null connection");

        verify(dataSource).getConnection();
    }

    /**
     * Test: Reuse a configured single connection for a successful operation.
     * Expected: The callback receives that connection and it is not closed by the provider.
     */
    @Test
    void singleConnectionProviderReusesConfiguredConnectionAndDoesNotCloseAfterOperation() throws Exception {
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
     * Test: Close the database connection owned by a single-connection provider.
     * Expected: close() delegates to the configured DbConnection.
     */
    @Test
    void singleConnectionProviderClosesOwnedDbConnectionWhenProviderCloses() throws Exception {
        DbConnection dbConnection = mock(DbConnection.class);
        SingleConnectionProvider provider = new SingleConnectionProvider(dbConnection);

        provider.close();

        verify(dbConnection).close();
    }
}
