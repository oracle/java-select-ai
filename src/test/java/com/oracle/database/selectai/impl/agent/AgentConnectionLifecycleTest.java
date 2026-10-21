/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Isolated("Temporarily replaces DriverManager's registered drivers")
class AgentConnectionLifecycleTest {
    private static final String JDBC_URL = "jdbc:oracle:thin:@unit-test";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuredClientReusesConnectionUntilClosed(boolean withOptions) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement firstStatement = mock(PreparedStatement.class);
        PreparedStatement secondStatement = mock(PreparedStatement.class);
        ResultSet firstRows = mock(ResultSet.class);
        ResultSet secondRows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS.get()))
                .thenReturn(firstStatement, secondStatement);
        when(firstStatement.executeQuery()).thenReturn(firstRows);
        when(secondStatement.executeQuery()).thenReturn(secondRows);

        try (TestDriver driver = new TestDriver(connection, null)) {
            try (SelectAIAgent client = createClient(withOptions)) {
                assertThat(client.listAgents()).isEmpty();
                assertThat(client.listAgents()).isEmpty();
                verify(connection, never()).close();
            }

            assertThat(driver.connectionCalls).isEqualTo(1);
            assertThat(driver.lastProperties).containsEntry("user", "test_user")
                    .containsEntry("password", "test_password");
        }

        verify(connection, times(2)).prepareStatement(AgentSql.LIST_AGENTS.get());
        verify(firstStatement).executeQuery();
        verify(secondStatement).executeQuery();
        verify(firstRows).close();
        verify(secondRows).close();
        verify(firstStatement).close();
        verify(secondStatement).close();
        verify(connection).close();
        if (withOptions) {
            verify(firstStatement).setQueryTimeout(7);
            verify(secondStatement).setQueryTimeout(7);
        } else {
            verify(firstStatement, never()).setQueryTimeout(anyInt());
            verify(secondStatement, never()).setQueryTimeout(anyInt());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuredClientPreservesInitializationFailure(boolean withOptions) throws Exception {
        SQLException cause = new SQLException("listener unavailable", "08006", 12514);

        try (TestDriver driver = new TestDriver(null, cause)) {
            assertThatThrownBy(() -> createClient(withOptions))
                    .isInstanceOfSatisfying(SelectAIException.class, error -> {
                        assertThat(error.getMessage()).isEqualTo("Failed to initialize database connection");
                        assertThat(error.getCause()).isSameAs(cause);
                        assertThat(error.getErrorCode()).isEqualTo(12514);
                        assertThat(error.getSqlState()).isEqualTo("08006");
                    });
            assertThat(driver.connectionCalls).isEqualTo(1);
        }
    }

    @Test
    void configuredClientPreservesCloseFailure() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException cause = new SQLException("connection close failed", "08006", 17002);
        doThrow(cause).when(connection).close();

        try (TestDriver driver = new TestDriver(connection, null)) {
            SelectAIAgent client = SelectAIAgent.create(config(), null);

            assertThatThrownBy(client::close)
                    .isInstanceOfSatisfying(SelectAIException.class, error -> {
                        assertThat(error.getMessage()).isEqualTo("Failed to close database connection");
                        assertThat(error.getCause()).isSameAs(cause);
                        assertThat(error.getErrorCode()).isEqualTo(17002);
                        assertThat(error.getSqlState()).isEqualTo("08006");
                    });
            verify(connection).close();
            assertThat(driver.connectionCalls).isEqualTo(1);
        }
    }

    @Test
    void configuredClientClosesOwnedConnectionWhenAnOperationFails() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException cause = new SQLException("query failed", "42000", 942);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(cause);

        try (TestDriver driver = new TestDriver(connection, null)) {
            assertThatThrownBy(() -> {
                try (SelectAIAgent client = SelectAIAgent.create(config())) {
                    client.listAgents();
                }
            }).isInstanceOfSatisfying(SelectAIException.class, error -> {
                assertThat(error.getCause()).isSameAs(cause);
                assertThat(error.getErrorCode()).isEqualTo(942);
                assertThat(error.getSqlState()).isEqualTo("42000");
            });
            verify(statement).close();
            verify(connection).close();
            assertThat(driver.connectionCalls).isEqualTo(1);
        }
    }

    @Test
    void configuredClientDoesNotReconnectAfterClose() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException cause = new SQLException("closed connection", "08003", 17008);
        when(connection.isClosed()).thenReturn(false, true);

        try (TestDriver driver = new TestDriver(connection, null)) {
            SelectAIAgent client = SelectAIAgent.create(config());
            client.close();
            client.close();
            when(connection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenThrow(cause);

            assertThatThrownBy(client::listAgents)
                    .isInstanceOfSatisfying(SelectAIException.class, error -> {
                        assertThat(error.getCause()).isSameAs(cause);
                        assertThat(error.getErrorCode()).isEqualTo(17008);
                        assertThat(error.getSqlState()).isEqualTo("08003");
                    });
            verify(connection).close();
            assertThat(driver.connectionCalls).isEqualTo(1);
        }
    }

    private static SelectAIAgent createClient(boolean withOptions) throws SelectAIException {
        return withOptions
                ? SelectAIAgent.create(config(), SelectAIOptions.builder().queryTimeoutSeconds(7).build())
                : SelectAIAgent.create(config());
    }

    private static DbConnectionConfig config() {
        return DbConnectionConfig.builder().dbUser("test_user").dbPassword("test_password")
                .jdbcUrl(JDBC_URL).build();
    }

    /** Isolates DriverManager so even connection-failure tests cannot reach a real database. */
    private static final class TestDriver implements Driver, AutoCloseable {
        private final List<Driver> originalDrivers;
        private final Connection connection;
        private final SQLException failure;
        private int connectionCalls;
        private Properties lastProperties;

        private TestDriver(Connection connection, SQLException failure) throws SQLException {
            this.connection = connection;
            this.failure = failure;
            originalDrivers = Collections.list(DriverManager.getDrivers());
            for (Driver driver : originalDrivers) {
                DriverManager.deregisterDriver(driver);
            }
            DriverManager.registerDriver(this);
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            connectionCalls++;
            lastProperties = new Properties();
            lastProperties.putAll(info);
            if (failure != null) {
                throw failure;
            }
            return connection;
        }

        @Override
        public boolean acceptsURL(String url) {
            return JDBC_URL.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Not supported by the test driver");
        }

        @Override
        public void close() throws SQLException {
            DriverManager.deregisterDriver(this);
            for (Driver driver : originalDrivers) {
                DriverManager.registerDriver(driver);
            }
        }
    }
}
