/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDatabaseAdminTest {
    @Mock
    private DbConnection dbConnection;
    @Mock
    private Connection connection;
    @Mock
    private CallableStatement statement;

    /**
     * Test: Verifies constructors reject null connection inputs.
     * Expected: Null connection providers and null connection configuration each raise
     * IllegalArgumentException before any database access.
     */
    @Test
    void constructorsRejectNullConnectionInputs() {
        assertThatThrownBy(() -> new DefaultDatabaseAdmin((ConnectionProvider) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionProvider");
        assertThatThrownBy(() -> new DefaultDatabaseAdmin((ConnectionProvider) null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionProvider");
        assertThatThrownBy(() -> new DefaultDatabaseAdmin((DbConnectionConfig) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbConnectionConfig");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies data source constructor uses per operation connection lifecycle.
     * Expected: enableDataAccess() returns true, borrows a data-source connection, executes
     * the enable procedure, closes its statement, and closes the connection.
     */
    @Test
    void dataSourceConstructorUsesPerOperationConnectionLifecycle() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        CallableStatement enableStatement = mock(CallableStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.ENABLE_DATA_ACCESS.get())).thenReturn(enableStatement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dataSource);

        assertThat(admin.enableDataAccess()).isTrue();

        verify(dataSource).getConnection();
        verify(connection).prepareCall(Sql.ENABLE_DATA_ACCESS.get());
        verify(enableStatement).execute();
        verify(enableStatement).close();
        verify(connection).close();
    }

    /**
     * Test: Verifies data source mode closes connection when operation fails.
     * Expected: enableDataAccess() wraps the SQL cause in SelectAIException and closes both the
     * statement and borrowed connection after failure.
     */
    @Test
    void dataSourceModeClosesConnectionWhenOperationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLException sqlException = new SQLException("enable failed", "42000", 20000);
        CallableStatement enableStatement = mock(CallableStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.ENABLE_DATA_ACCESS.get())).thenReturn(enableStatement);
        when(enableStatement.execute()).thenThrow(sqlException);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dataSource);

        assertThatThrownBy(admin::enableDataAccess)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("ENABLE_DATA_ACCESS")
                .hasCause(sqlException);

        verify(enableStatement).close();
        verify(connection).close();
    }

    /**
     * Test: Verifies data source acquisition failure is wrapped with sql metadata.
     * Expected: enableDataAccess() throws SelectAIException naming the procedure, preserving
     * the SQL cause, error code 12541, and SQL state 08001.
     */
    @Test
    void dataSourceAcquisitionFailureIsWrappedWithSqlMetadata() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLException connectionFailure =
                new SQLException("database service unavailable", "08001", 12541);
        when(dataSource.getConnection()).thenThrow(connectionFailure);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dataSource);

        assertThatThrownBy(admin::enableDataAccess)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("DBMS_CLOUD_AI.ENABLE_DATA_ACCESS")
                .hasCause(connectionFailure)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(12541);
                    assertThat(wrapped.getSqlState()).isEqualTo("08001");
                });
    }

    /**
     * Test: Verifies data source mode borrows connection for each operation.
     * Expected: Enable and disable each return true, use separate data-source connections, and
     * close both connections.
     */
    @Test
    void dataSourceModeBorrowsConnectionForEachOperation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        CallableStatement enableStatement = mock(CallableStatement.class);
        CallableStatement disableStatement = mock(CallableStatement.class);
        when(dataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        when(firstConnection.prepareCall(Sql.ENABLE_DATA_ACCESS.get())).thenReturn(enableStatement);
        when(secondConnection.prepareCall(Sql.DISABLE_DATA_ACCESS.get())).thenReturn(disableStatement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dataSource);

        assertThat(admin.enableDataAccess()).isTrue();
        assertThat(admin.disableDataAccess()).isTrue();

        verify(firstConnection).prepareCall(Sql.ENABLE_DATA_ACCESS.get());
        verify(secondConnection).prepareCall(Sql.DISABLE_DATA_ACCESS.get());
        verify(firstConnection).close();
        verify(secondConnection).close();
    }

    /**
     * Test: Verifies enable and disable data access execute procedures.
     * Expected: Enable and disable each return true, prepare their corresponding procedures,
     * execute and close both statements, and leave no extra interactions.
     */
    @Test
    void enableAndDisableDataAccessExecuteProcedures() throws Exception {
        CallableStatement enableStatement = mock(CallableStatement.class);
        CallableStatement disableStatement = mock(CallableStatement.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.ENABLE_DATA_ACCESS.get())).thenReturn(enableStatement);
        when(connection.prepareCall(Sql.DISABLE_DATA_ACCESS.get())).thenReturn(disableStatement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.enableDataAccess()).isTrue();
        assertThat(admin.disableDataAccess()).isTrue();

        verify(connection).prepareCall(Sql.ENABLE_DATA_ACCESS.get());
        verify(connection).prepareCall(Sql.DISABLE_DATA_ACCESS.get());
        verify(enableStatement).execute();
        verify(disableStatement).execute();
        verify(enableStatement).close();
        verify(disableStatement).close();
        verifyNoMoreInteractions(enableStatement, disableStatement);
    }

    /**
     * Test: Verifies data access methods wrap sql failures.
     * Expected: Enable and disable each throw SelectAIException naming the corresponding
     * procedure and preserving its SQL cause.
     */
    @Test
    void dataAccessMethodsWrapSqlFailures() throws Exception {
        SQLException enableException = new SQLException("enable failed", "42000", 20000);
        SQLException disableException = new SQLException("disable failed", "42000", 20001);
        CallableStatement enableStatement = mock(CallableStatement.class);
        CallableStatement disableStatement = mock(CallableStatement.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.ENABLE_DATA_ACCESS.get())).thenReturn(enableStatement);
        when(connection.prepareCall(Sql.DISABLE_DATA_ACCESS.get())).thenReturn(disableStatement);
        when(enableStatement.execute()).thenThrow(enableException);
        when(disableStatement.execute()).thenThrow(disableException);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThatThrownBy(admin::enableDataAccess)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("ENABLE_DATA_ACCESS")
                .hasCause(enableException);
        assertThatThrownBy(admin::disableDataAccess)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("DISABLE_DATA_ACCESS")
                .hasCause(disableException);
    }

    /**
     * Test: Verifies retained connection failure is wrapped with sql metadata.
     * Expected: A closed retained connection produces SelectAIException naming the enable
     * procedure and preserving error code 17002 and SQL state 08003.
     */
    @Test
    void retainedConnectionFailureIsWrappedWithSqlMetadata() throws Exception {
        SQLException closedConnection =
                new SQLException("Connection is closed", "08003", 17002);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.ENABLE_DATA_ACCESS.get())).thenThrow(closedConnection);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThatThrownBy(admin::enableDataAccess)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("DBMS_CLOUD_AI.ENABLE_DATA_ACCESS")
                .hasCause(closedConnection)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(17002);
                    assertThat(wrapped.getSqlState()).isEqualTo("08003");
                });
    }

    /**
     * Test: Verifies close closes configuration backed owned connection.
     * Expected: close() delegates to the configuration-backed DbConnection.
     */
    @Test
    void closeClosesConfigurationBackedOwnedConnection() throws Exception {
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        admin.close();

        verify(dbConnection).close();
    }

    /**
     * Test: Verifies close does not close caller owned data source.
     * Expected: close() does not interact with or close the caller-owned data source.
     */
    @Test
    void closeDoesNotCloseCallerOwnedDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dataSource);

        admin.close();

        verifyNoInteractions(dataSource);
    }

    /**
     * Test: Verifies public data source factory applies query timeout to admin statements.
     * Expected: The factory-created admin returns true from enableDataAccess() and applies a
     * seven-second timeout to the enable statement.
     */
    @Test
    void publicDataSourceFactoryAppliesQueryTimeoutToAdminStatements() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.ENABLE_DATA_ACCESS.get())).thenReturn(statement);
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(7)
                .build();

        com.oracle.database.selectai.DatabaseAdmin admin =
                com.oracle.database.selectai.DatabaseAdmin.create(dataSource, options);

        assertThat(admin.enableDataAccess()).isTrue();

        verify(statement).setQueryTimeout(7);
    }

    /**
     * Test: Verifies grant and revoke privileges bind users for non blank users.
     * Expected: Grant and revoke return true, ignore blank users, bind each nonblank user by
     * name, and execute two grant statements plus one revoke statement.
     */
    @Test
    void grantAndRevokePrivilegesBindUsersForNonBlankUsers() throws Exception {
        CallableStatement firstGrantStatement = mock(CallableStatement.class);
        CallableStatement secondGrantStatement = mock(CallableStatement.class);
        CallableStatement revokeStatement = mock(CallableStatement.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(firstGrantStatement, secondGrantStatement, revokeStatement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.grantPrivileges(List.of("AI_TEST1", " ", "AI_TEST2"))).isTrue();
        assertThat(admin.revokePrivileges(List.of("AI_TEST1"))).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection, times(3)).prepareCall(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().get(0))
                .contains("GRANT EXECUTE")
                .contains("DBMS_ASSERT.SCHEMA_NAME(:user)")
                .doesNotContain("TO AI_TEST1");
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("GRANT EXECUTE")
                .contains("DBMS_ASSERT.SCHEMA_NAME(:user)")
                .doesNotContain("TO AI_TEST2");
        assertThat(sqlCaptor.getAllValues().get(2))
                .contains("REVOKE EXECUTE")
                .contains("DBMS_ASSERT.SCHEMA_NAME(:user)")
                .doesNotContain("FROM AI_TEST1");
        verify(firstGrantStatement).setString("user", "AI_TEST1");
        verify(secondGrantStatement).setString("user", "AI_TEST2");
        verify(revokeStatement).setString("user", "AI_TEST1");
        verify(firstGrantStatement).execute();
        verify(secondGrantStatement).execute();
        verify(revokeStatement).execute();
    }

    /**
     * Test: Verifies privilege methods return false for empty user lists.
     * Expected: All grant and revoke methods return false for null or empty user lists and make
     * no database interactions.
     */
    @Test
    void privilegeMethodsReturnFalseForEmptyUserLists() throws Exception {
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.grantPrivileges(List.of())).isFalse();
        assertThat(admin.revokePrivileges(List.of())).isFalse();
        assertThat(admin.grantPrivileges(null)).isFalse();
        assertThat(admin.revokePrivileges(null)).isFalse();
        assertThat(admin.grantNetworkAccess(List.of(), "*.openai.azure.com",
                List.of("http"), null, null)).isFalse();
        assertThat(admin.revokeNetworkAccess(List.of(), "*.openai.azure.com",
                List.of("http"), null, null)).isFalse();
        assertThat(admin.grantNetworkAccess(null, "*.openai.azure.com",
                List.of("http"), null, null)).isFalse();
        assertThat(admin.revokeNetworkAccess(null, "*.openai.azure.com",
                List.of("http"), null, null)).isFalse();
        assertThat(admin.grantHttpAccess(List.of(), "*.openai.azure.com")).isFalse();
        assertThat(admin.revokeHttpAccess(List.of(), "*.openai.azure.com")).isFalse();
        assertThat(admin.grantHttpAccess(null, "*.openai.azure.com")).isFalse();
        assertThat(admin.revokeHttpAccess(null, "*.openai.azure.com")).isFalse();

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies privilege methods wrap sql failures.
     * Expected: Grant and revoke each throw SelectAIException with the operation-specific
     * message and original SQL cause.
     */
    @Test
    void privilegeMethodsWrapSqlFailures() throws Exception {
        SQLException grantException = new SQLException("grant failed", "42000", 942);
        SQLException revokeException = new SQLException("revoke failed", "42000", 942);
        CallableStatement grantStatement = mock(CallableStatement.class);
        CallableStatement revokeStatement = mock(CallableStatement.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(grantStatement, revokeStatement);
        when(grantStatement.execute()).thenThrow(grantException);
        when(revokeStatement.execute()).thenThrow(revokeException);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThatThrownBy(() -> admin.grantPrivileges(List.of("AI_TEST1")))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("grant Select AI privileges")
                .hasCause(grantException);
        assertThatThrownBy(() -> admin.revokePrivileges(List.of("AI_TEST1")))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("revoke Select AI privileges")
                .hasCause(revokeException);
    }

    /**
     * Test: Verifies grant network access binds host ports privileges and user.
     * Expected: Grant returns true, builds the APPEND_HOST_ACE call with the host, both 443
     * ports, both privileges, and AI_TEST1, then executes it.
     */
    @Test
    void grantNetworkAccessBindsHostPortsPrivilegesAndUser() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(statement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.grantNetworkAccess(List.of("AI_TEST1"), "*.openai.azure.com",
                List.of("http", "connect"), 443, 443)).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareCall(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE")
                .contains("lower_port => ?")
                .contains("upper_port => ?")
                .contains("xs$name_list(?, ?)");
        verify(statement).setString(1, "*.openai.azure.com");
        verify(statement).setInt(2, 443);
        verify(statement).setInt(3, 443);
        verify(statement).setString(4, "http");
        verify(statement).setString(5, "connect");
        verify(statement).setString(6, "AI_TEST1");
        verify(statement).execute();
    }

    /**
     * Test: Verifies network access wraps sql failures.
     * Expected: Grant network access throws SelectAIException naming the grant operation and
     * preserving the ACL SQL cause.
     */
    @Test
    void networkAccessWrapsSqlFailures() throws Exception {
        SQLException sqlException = new SQLException("acl update failed", "42000", 24247);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(statement);
        when(statement.execute()).thenThrow(sqlException);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThatThrownBy(() -> admin.grantNetworkAccess(List.of("AI_TEST1"), "*.openai.azure.com",
                List.of("http"), null, null))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("grant network access")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies revoke network access binds null ports and single privilege.
     * Expected: Revoke returns true, builds REMOVE_HOST_ACE with null ports, the connect
     * privilege, and AI_TEST1, then executes it.
     */
    @Test
    void revokeNetworkAccessBindsNullPortsAndSinglePrivilege() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(statement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.revokeNetworkAccess(List.of("AI_TEST1"), "*.openai.azure.com",
                List.of("connect"), null, null)).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareCall(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("DBMS_NETWORK_ACL_ADMIN.REMOVE_HOST_ACE")
                .contains("xs$name_list(?)");
        verify(statement).setString(1, "*.openai.azure.com");
        verify(statement).setNull(2, Types.INTEGER);
        verify(statement).setNull(3, Types.INTEGER);
        verify(statement).setString(4, "connect");
        verify(statement).setString(5, "AI_TEST1");
        verify(statement).execute();
    }

    /**
     * Test: Verifies grant http access uses http privilege with null ports.
     * Expected: Grant HTTP access returns true, builds APPEND_HOST_ACE with the HTTP privilege,
     * null ports, and AI_TEST1, then executes it.
     */
    @Test
    void grantHttpAccessUsesHttpPrivilegeWithNullPorts() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(statement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.grantHttpAccess(List.of("AI_TEST1"), "*.openai.azure.com")).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareCall(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE")
                .contains("xs$name_list(?)");
        verify(statement).setString(1, "*.openai.azure.com");
        verify(statement).setNull(2, Types.INTEGER);
        verify(statement).setNull(3, Types.INTEGER);
        verify(statement).setString(4, "http");
        verify(statement).setString(5, "AI_TEST1");
        verify(statement).execute();
    }

    /**
     * Test: Verifies revoke http access uses http privilege with null ports.
     * Expected: Revoke HTTP access returns true, builds REMOVE_HOST_ACE with the HTTP privilege,
     * null ports, and AI_TEST1, then executes it.
     */
    @Test
    void revokeHttpAccessUsesHttpPrivilegeWithNullPorts() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(statement);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.revokeHttpAccess(List.of("AI_TEST1"), "*.openai.azure.com")).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareCall(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("DBMS_NETWORK_ACL_ADMIN.REMOVE_HOST_ACE")
                .contains("xs$name_list(?)");
        verify(statement).setString(1, "*.openai.azure.com");
        verify(statement).setNull(2, Types.INTEGER);
        verify(statement).setNull(3, Types.INTEGER);
        verify(statement).setString(4, "http");
        verify(statement).setString(5, "AI_TEST1");
        verify(statement).execute();
    }

    /**
     * Test: Verifies network access validates request before touching database.
     * Expected: Blank host or privilege input raises IllegalArgumentException before any
     * database interaction.
     */
    @Test
    void networkAccessValidatesRequestBeforeTouchingDatabase() {
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThatThrownBy(() -> admin.grantNetworkAccess(List.of("AI_TEST1"), " ",
                List.of("http"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");

        assertThatThrownBy(() -> admin.revokeNetworkAccess(List.of("AI_TEST1"),
                "*.openai.azure.com", List.of(" "), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("privileges");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies network access rejects invalid port ranges before JDBC execution.
     * Expected: Negative ports, ports above 65535, and reversed ranges each raise
     * IllegalArgumentException without accessing the database.
     */
    @Test
    void networkAccessRejectsInvalidPortRangesBeforeDatabaseCall() {
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThatThrownBy(() -> admin.grantNetworkAccess(List.of("AI_TEST1"),
                "*.openai.azure.com", List.of("http"), -1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowerPort");
        assertThatThrownBy(() -> admin.grantNetworkAccess(List.of("AI_TEST1"),
                "*.openai.azure.com", List.of("http"), 65536, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowerPort");
        assertThatThrownBy(() -> admin.grantNetworkAccess(List.of("AI_TEST1"),
                "*.openai.azure.com", List.of("http"), 443, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("less than or equal");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies blank network users are ignored after request validation.
     * Expected: The operation returns true, obtains the connection, and does not prepare
     * or execute an ACL statement for blank users.
     */
    @Test
    void networkAccessSkipsBlankUsersWithoutPreparingStatement() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        DefaultDatabaseAdmin admin = new DefaultDatabaseAdmin(dbConnection, null);

        assertThat(admin.grantNetworkAccess(Arrays.asList(" ", null),
                "*.openai.azure.com", List.of("http"), null, null)).isTrue();

        verify(dbConnection).getConnection();
        verifyNoInteractions(connection);
    }

    /**
     * Test: Verifies factory creates data source backed admin.
     * Expected: DatabaseAdmin.create(dataSource) returns a DefaultDatabaseAdmin instance.
     */
    @Test
    void factoryCreatesDataSourceBackedAdmin() {
        DataSource dataSource = mock(DataSource.class);

        assertThat(com.oracle.database.selectai.DatabaseAdmin.create(dataSource)).isInstanceOf(DefaultDatabaseAdmin.class);
    }

}
