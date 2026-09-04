/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.model.SelectAIOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcExecutionConnectionProviderTest {

    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private CallableStatement callableStatement;
    @Mock
    private Statement statement;

    /**
     * Test: Apply the configured timeout to prepared, callable, and regular statements.
     * Expected: Each statement receives the configured 30-second timeout.
     */
    @Test
    void appliesQueryTimeoutToPreparedCallableAndRegularStatements() throws Exception {
        when(connection.prepareStatement("select 1 from dual")).thenReturn(preparedStatement);
        when(connection.prepareCall("begin null; end;")).thenReturn(callableStatement);
        when(connection.createStatement()).thenReturn(statement);
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(30)
                .build();

        JdbcExecutionConnectionProvider provider =
                new JdbcExecutionConnectionProvider(singleConnectionProvider(), options);

        Boolean result = provider.withConnection(wrappedConnection -> {
            assertThat(wrappedConnection.prepareStatement("select 1 from dual")).isSameAs(preparedStatement);
            assertThat(wrappedConnection.prepareCall("begin null; end;")).isSameAs(callableStatement);
            assertThat(wrappedConnection.createStatement()).isSameAs(statement);
            return true;
        });

        assertThat(result).isTrue();
        verify(preparedStatement).setQueryTimeout(30);
        verify(callableStatement).setQueryTimeout(30);
        verify(statement).setQueryTimeout(30);
    }

    /**
     * Test: Leave the connection unwrapped when no statement options are configured.
     * Expected: The original connection is returned without creating statements.
     */
    @Test
    void doesNotWrapConnectionWhenNoStatementOptionsAreConfigured() throws Exception {
        JdbcExecutionConnectionProvider provider =
                new JdbcExecutionConnectionProvider(singleConnectionProvider(), SelectAIOptions.defaults());

        Connection returnedConnection = provider.withConnection(wrappedConnection -> wrappedConnection);

        assertThat(returnedConnection).isSameAs(connection);
        verify(connection, never()).prepareStatement("select 1 from dual");
    }

    /**
     * Test: Propagate a failure while applying a statement timeout.
     * Expected: The SQL exception from setQueryTimeout() is returned unchanged.
     */
    @Test
    void propagatesSqlExceptionFromSetQueryTimeout() throws Exception {
        SQLException sqlException = new SQLException("timeout unsupported", "99999", 17023);
        when(connection.prepareCall("begin null; end;")).thenReturn(callableStatement);
        doThrow(sqlException).when(callableStatement).setQueryTimeout(30);
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(30)
                .build();
        JdbcExecutionConnectionProvider provider =
                new JdbcExecutionConnectionProvider(singleConnectionProvider(), options);

        assertThatThrownBy(() -> provider.withConnection(
                wrappedConnection -> wrappedConnection.prepareCall("begin null; end;")))
                .isSameAs(sqlException);
    }

    /**
     * Test: Reject a null delegate connection provider.
     * Expected: An IllegalArgumentException identifies the missing provider.
     */
    @Test
    void rejectsNullDelegate() {
        assertThatThrownBy(() -> new JdbcExecutionConnectionProvider(null, SelectAIOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionProvider");
    }

    private ConnectionProvider singleConnectionProvider() {
        return new ConnectionProvider() {
            @Override
            public <T> T withConnection(com.oracle.database.selectai.ConnectionCallback<T> callback)
                    throws SQLException, com.oracle.database.selectai.model.SelectAIException {
                return callback.execute(connection);
            }
        };
    }
}
