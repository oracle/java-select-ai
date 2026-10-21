/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AvailableTeam;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import oracle.jdbc.OracleTypes;

import javax.sql.DataSource;
import java.io.Reader;
import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AgentClientOperationsTest {
    @Test
    void configuredFactoriesValidateLocallyWithoutOpeningConnections() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider);
        AgentAttributes agentAttributes = AgentAttributes.builder().profileName("PROFILE").role("Assist").build();

        assertThatThrownBy(() -> client.agent(" ", agentAttributes, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.agent("AGENT", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.task(null, TaskAttributes.builder().build(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.task("TASK", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.tool(" ", ToolAttributes.builder().build(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.tool("TOOL", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.team(" ", TeamAttributes.builder().build(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.team("TEAM", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void existingResourceFactoriesValidateNamesBeforeDatabaseAccess() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider);

        assertThatThrownBy(() -> client.agent(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.task(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.tool(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.team(" ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void patternListsBindRegexForEachOwnedResourceType() throws Exception {
        Connection connection = mock(Connection.class);
        String pattern = "^JSAI_.*$";
        AgentSql[] queries = {
                AgentSql.LIST_AGENTS_BY_PATTERN,
                AgentSql.LIST_TASKS_BY_PATTERN,
                AgentSql.LIST_TOOLS_BY_PATTERN,
                AgentSql.LIST_TEAMS_BY_PATTERN
        };
        PreparedStatement[] statements = new PreparedStatement[queries.length];
        for (int i = 0; i < queries.length; i++) {
            statements[i] = mock(PreparedStatement.class);
            when(connection.prepareStatement(queries[i].get())).thenReturn(statements[i]);
            when(statements[i].executeQuery()).thenReturn(mock(ResultSet.class));
        }
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.listAgents(pattern)).isEmpty();
        assertThat(client.listTasks(pattern)).isEmpty();
        assertThat(client.listTools(pattern)).isEmpty();
        assertThat(client.listTeams(pattern)).isEmpty();

        for (int i = 0; i < queries.length; i++) {
            verify(connection).prepareStatement(queries[i].get());
            verify(statements[i]).setString(1, pattern);
            verify(statements[i]).executeQuery();
            verify(statements[i]).close();
        }
    }

    @Test
    void patternListsRejectNullOrBlankBeforeDatabaseAccess() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider);

        assertThatThrownBy(() -> client.listAgents(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.listTasks(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.listTools(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.listTeams(" ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void teamListingUsesOwnerMetadataView() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.LIST_TEAMS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.listTeams()).isEmpty();

        verify(connection).prepareStatement(AgentSql.LIST_TEAMS.get());
    }

    @Test
    void availableTeamListingUsesPackageDiscoveryAndReturnsReadOnlySnapshots() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        Clob clob = mock(Clob.class);
        when(connection.prepareCall(AgentSql.LIST_AVAILABLE_TEAMS.get())).thenReturn(statement);
        when(statement.getClob(1)).thenReturn(clob);
        when(clob.getCharacterStream()).thenReturn(new StringReader(
                "[{\"name\":\"OWNER.SHARED_TEAM\",\"description\":\"Shared\"}]"));
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        List<AvailableTeam> teams = client.listAvailableTeams();

        assertThat(teams).singleElement().satisfies(team -> {
            assertThat(team.getTeamName()).isEqualTo("OWNER.SHARED_TEAM");
            assertThat(team.getDescription()).isEqualTo("Shared");
        });
        verify(connection).prepareCall(AgentSql.LIST_AVAILABLE_TEAMS.get());
        verify(connection, never()).prepareStatement(AgentSql.LIST_TEAMS.get());
    }

    @Test
    void availableTeamListingPreservesJdbcFailureMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        SQLException cause = new SQLException("team discovery failed", "72000", 20411);
        when(connection.prepareCall(AgentSql.LIST_AVAILABLE_TEAMS.get())).thenReturn(statement);
        when(statement.execute()).thenThrow(cause);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(client::listAvailableTeams)
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(20411);
                    assertThat(error.getSqlState()).isEqualTo("72000");
                });
    }

    @Test
    void patternListPreservesDatabaseRegexErrorDetails() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException cause = new SQLException("invalid regular expression", "72000", 12726);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS_BY_PATTERN.get())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(cause);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(() -> client.listAgents("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(12726);
                    assertThat(error.getSqlState()).isEqualTo("72000");
                });
    }

    @Test
    void databaseConfigurationFactoryRejectsNullBeforeConnecting() {
        assertThatThrownBy(() -> SelectAIAgent.create((DbConnectionConfig) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getDefinitionBindsNullParamsWhenDependentsAreExcluded() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.GET_DEFINITION.get())).thenReturn(statement);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.getDefinition(AgentObjectType.TOOL, "TOOL", false)).isNull();

        verify(statement).registerOutParameter(1, Types.CLOB);
        verify(statement).setString(2, "TOOL");
        verify(statement).setString(3, "TOOL");
        verify(statement).setNull(4, Types.CLOB);
        verify(statement).execute();
    }

    @Test
    void getDefinitionValidatesTypeAndNameBeforeDatabaseAccess() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider);

        assertThatThrownBy(() -> client.getDefinition(AgentObjectType.AGENT, " ", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getDefinition(null, "AGENT", false))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void runSharedTeamBindsQualifiedNameAndRequestWithoutMetadataLookup() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        Clob result = mock(Clob.class);
        when(connection.prepareCall(AgentSql.RUN_TEAM.get())).thenReturn(statement);
        when(statement.getClob(1)).thenReturn(result);
        when(result.getCharacterStream()).thenReturn(new StringReader("Completed"));
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        TeamRunResult answer = client.runTeam("TEAM_OWNER.SHARED_TEAM", RunTeamRequest.builder()
                .userPrompt("Help with my order")
                .conversationId("C1")
                .build());

        assertThat(answer.getRawValue()).isEqualTo("Completed");
        verify(statement).registerOutParameter(1, Types.CLOB);
        verify(statement).setString(2, "TEAM_OWNER.SHARED_TEAM");
        assertThat(boundClob(statement, 3)).isEqualTo("Help with my order");
        assertThat(boundClob(statement, 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
        verify(statement).execute();
        verify(statement).close();
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void runTeamRejectsInvalidInputBeforeDatabaseAccess() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider);
        RunTeamRequest request = RunTeamRequest.builder().build();

        assertThatThrownBy(() -> client.runTeam(null, request)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.runTeam(" ", request)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.runTeam("OWNER.TEAM", null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void teamAccessOperationsUseOwnerScopedClientMethods() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement grant = mock(CallableStatement.class);
        CallableStatement revoke = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.GRANT_TEAM_ACCESS.get())).thenReturn(grant);
        when(connection.prepareCall(AgentSql.REVOKE_TEAM_ACCESS.get())).thenReturn(revoke);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.grantTeamAccess("TEAM", "APP_USER")).isTrue();
        assertThat(client.revokeTeamAccess("TEAM", "APP_USER")).isTrue();

        verify(grant).setString(1, "TEAM");
        verify(grant).setString(2, "APP_USER");
        verify(grant).execute();
        verify(revoke).setString(1, "TEAM");
        verify(revoke).setString(2, "APP_USER");
        verify(revoke).execute();
    }

    @Test
    void teamAccessClientMethodsValidateOwnerScopedNamesBeforeJdbc() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider);

        assertThatThrownBy(() -> client.grantTeamAccess("OWNER.TEAM", "APP_USER"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.revokeTeamAccess("TEAM", "PUBLIC"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void teamAccessClientFailuresPreserveJdbcMetadataAndReleaseResources(boolean grant) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        AgentSql operation = grant ? AgentSql.GRANT_TEAM_ACCESS : AgentSql.REVOKE_TEAM_ACCESS;
        SQLException cause = new SQLException("team sharing rejected", "42000", 1031);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareCall(operation.get())).thenReturn(statement);
        when(statement.execute()).thenThrow(cause);

        try (SelectAIAgent client = SelectAIAgent.create(dataSource)) {
            assertThatThrownBy(() -> {
                if (grant) {
                    client.grantTeamAccess("TEAM", "APP_USER");
                } else {
                    client.revokeTeamAccess("TEAM", "APP_USER");
                }
            }).isInstanceOfSatisfying(SelectAIException.class, error -> {
                assertThat(error.getMessage()).isEqualTo(
                        "Failed to execute DBMS_CLOUD_AI_AGENT." + operation.name());
                assertThat(error.getCause()).isSameAs(cause);
                assertThat(error.getErrorCode()).isEqualTo(1031);
                assertThat(error.getSqlState()).isEqualTo("42000");
            });
        }

        verify(statement).setString(1, "TEAM");
        verify(statement).setString(2, "APP_USER");
        verify(statement).execute();
        verify(statement).close();
        verify(connection).prepareCall(operation.get());
        verify(connection).close();
        verify(dataSource).getConnection();
    }

    @Test
    void runtimeVariablesUseBoundValuesAndDefaultEphemeralScope() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement getStatement = mock(CallableStatement.class);
        CallableStatement setStatement = mock(CallableStatement.class);
        Clob result = mock(Clob.class);
        when(connection.prepareCall(AgentSql.GET_VARIABLE.get())).thenReturn(getStatement);
        when(connection.prepareCall(AgentSql.SET_VARIABLE.get())).thenReturn(setStatement);
        when(getStatement.getClob(1)).thenReturn(result);
        when(result.getCharacterStream()).thenReturn(new StringReader("ACCOUNT-42"));
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.getVariable("state_variables", "account_id"))
                .isEqualTo("ACCOUNT-42");
        assertThat(client.setVariable("state_variables", "account_id", "ACCOUNT-43"))
                .isTrue();

        verify(getStatement).registerOutParameter(1, Types.CLOB);
        verify(getStatement).setString(2, "state_variables");
        verify(getStatement).setString(3, "account_id");
        verify(getStatement).setString(4, "ephemeral");
        verify(getStatement).execute();
        verify(setStatement).setString(1, "state_variables");
        verify(setStatement).setString(2, "account_id");
        assertThat(boundClob(setStatement, 3)).isEqualTo("ACCOUNT-43");
        verify(setStatement).setString(4, "ephemeral");
        verify(setStatement).execute();
    }

    @Test
    void runtimeVariableSupportsExplicitScopeAndNullClearsTheValue() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement getStatement = mock(CallableStatement.class);
        CallableStatement setStatement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.GET_VARIABLE.get())).thenReturn(getStatement);
        when(connection.prepareCall(AgentSql.SET_VARIABLE.get())).thenReturn(setStatement);
        when(getStatement.getClob(1)).thenReturn(null);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.getVariable("instruction_variables", "audience", "ephemeral"))
                .isNull();
        assertThat(client.setVariable("instruction_variables", "audience", null, "ephemeral"))
                .isTrue();

        verify(getStatement).setString(4, "ephemeral");
        verify(setStatement).setNull(3, Types.CLOB);
        verify(setStatement).setString(4, "ephemeral");
    }

    @Test
    void runtimeVariableRejectsInvalidArgumentsBeforeDatabaseAccess() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider);

        assertThatThrownBy(() -> client.getVariable(" ", "name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getVariable("state_variables", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.setVariable("state_variables", "name", "value", " "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void runtimeVariableFailuresPreserveJdbcFailureMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement getStatement = mock(CallableStatement.class);
        CallableStatement setStatement = mock(CallableStatement.class);
        SQLException getCause = new SQLException("get variable failed", "72000", 20412);
        SQLException setCause = new SQLException("set variable failed", "72000", 20413);
        when(connection.prepareCall(AgentSql.GET_VARIABLE.get())).thenReturn(getStatement);
        when(connection.prepareCall(AgentSql.SET_VARIABLE.get())).thenReturn(setStatement);
        when(getStatement.execute()).thenThrow(getCause);
        when(setStatement.execute()).thenThrow(setCause);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(() -> client.getVariable("state_variables", "account_id"))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(getCause);
                    assertThat(error.getErrorCode()).isEqualTo(20412);
                    assertThat(error.getSqlState()).isEqualTo("72000");
                });
        assertThatThrownBy(() -> client.setVariable("state_variables", "account_id", "ACCOUNT-43"))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(setCause);
                    assertThat(error.getErrorCode()).isEqualTo(20413);
                    assertThat(error.getSqlState()).isEqualTo("72000");
                });
    }

    @Test
    void runTeamPreservesDatabaseErrorDetails() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        SQLException cause = new SQLException("access denied", "42000", 1031);
        when(connection.prepareCall(AgentSql.RUN_TEAM.get())).thenReturn(statement);
        when(statement.execute()).thenThrow(cause);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(() -> client.runTeam("OWNER.TEAM", RunTeamRequest.builder().build()))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(1031);
                    assertThat(error.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void runSharedTeamUsesNewPooledConnectionForEachConversationStep() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection first = mock(Connection.class);
        Connection second = mock(Connection.class);
        CallableStatement firstRun = mock(CallableStatement.class);
        CallableStatement secondRun = mock(CallableStatement.class);
        when(dataSource.getConnection()).thenReturn(first, second);
        when(first.prepareCall(AgentSql.RUN_TEAM.get())).thenReturn(firstRun);
        when(second.prepareCall(AgentSql.RUN_TEAM.get())).thenReturn(secondRun);

        try (SelectAIAgent client = SelectAIAgent.create(dataSource)) {
            client.runTeam("OWNER.TEAM", RunTeamRequest.builder()
                    .userPrompt("First question").conversationId("C1").build());
            client.runTeam("OWNER.TEAM", RunTeamRequest.builder()
                    .userPrompt("Human response").conversationId("C1").build());
        }

        assertThat(boundClob(firstRun, 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
        assertThat(boundClob(secondRun, 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
        verify(first).close();
        verify(second).close();
        verify(first, never()).prepareStatement(anyString());
        verify(second, never()).prepareStatement(anyString());
    }

    @Test
    void getDefinitionPreservesJdbcFailureMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        SQLException cause = new SQLException("definition failed", "72000", 20403);
        when(connection.prepareCall(AgentSql.GET_DEFINITION.get())).thenReturn(statement);
        when(statement.execute()).thenThrow(cause);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(() -> client.getDefinition(AgentObjectType.AGENT, "AGENT", true))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(20403);
                    assertThat(error.getSqlState()).isEqualTo("72000");
                });
    }

    @Test
    void importsTeamFromLocationWithNullableSpecificationAndForceDefault() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.IMPORT_TEAM.get())).thenReturn(statement);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.importTeam(ImportTeamRequest.builder("PROFILE", "TEAM")
                .objectStorageCredentialName("CREDENTIAL")
                .location("https://example.invalid/team.json")
                .build())).isTrue();

        verify(statement).setString(1, "PROFILE");
        verify(statement).setString(2, "TEAM");
        verify(statement).setNull(3, Types.CLOB);
        verify(statement).setString(4, "CREDENTIAL");
        verify(statement).setString(5, "https://example.invalid/team.json");
        verify(statement).setObject(6, false, OracleTypes.PLSQL_BOOLEAN);
        verify(statement).execute();
    }

    @Test
    void importTeamValidatesNullAndPreservesJdbcErrorMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        SQLException cause = new SQLException("import failed", "72000", 20400);
        when(connection.prepareCall(AgentSql.IMPORT_TEAM.get())).thenReturn(statement);
        when(statement.execute()).thenThrow(cause);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(() -> client.importTeam(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.importTeam(ImportTeamRequest.builder("PROFILE", "TEAM")
                .specification("{}").build()))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(20400);
                    assertThat(error.getSqlState()).isEqualTo("72000");
                });
        ArgumentCaptor<Reader> reader = ArgumentCaptor.forClass(Reader.class);
        verify(statement).setCharacterStream(eq(3), reader.capture(), anyInt());
        char[] content = new char[2];
        assertThat(reader.getValue().read(content)).isEqualTo(2);
        assertThat(new String(content)).isEqualTo("{}");
    }

    @Test
    void dataSourceClientBorrowsAndClosesAConnectionPerOperation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        PreparedStatement firstStatement = mock(PreparedStatement.class);
        PreparedStatement secondStatement = mock(PreparedStatement.class);
        ResultSet firstRows = mock(ResultSet.class);
        ResultSet secondRows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        when(firstConnection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenReturn(firstStatement);
        when(secondConnection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenReturn(secondStatement);
        when(firstStatement.executeQuery()).thenReturn(firstRows);
        when(secondStatement.executeQuery()).thenReturn(secondRows);

        try (SelectAIAgent client = SelectAIAgent.create(dataSource)) {
            assertThat(client.listAgents()).isEmpty();
            assertThat(client.listAgents()).isEmpty();
        }

        verify(firstConnection).close();
        verify(secondConnection).close();
        verify(firstStatement).close();
        verify(secondStatement).close();
        verify(firstRows).close();
        verify(secondRows).close();
        verify(dataSource, org.mockito.Mockito.times(2)).getConnection();
    }

    @Test
    void dataSourceConnectionClosesWhenAnAgentQueryFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException cause = new SQLException("query failed", "42000", 942);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenThrow(cause);

        try (SelectAIAgent client = SelectAIAgent.create(dataSource)) {
            assertThatThrownBy(client::listAgents)
                    .isInstanceOfSatisfying(SelectAIException.class,
                            error -> assertThat(error.getCause()).isSameAs(cause));
        }
        verify(connection).close();
    }

    @Test
    void agentClientAppliesConfiguredStatementTimeout() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);

        try (SelectAIAgent client = SelectAIAgent.create(dataSource,
                SelectAIOptions.builder().queryTimeoutSeconds(7).build())) {
            assertThat(client.listAgents()).isEmpty();
        }

        verify(statement).setQueryTimeout(7);
        verify(connection).close();
    }

    private static ConnectionProvider provider(Connection connection) {
        return new ConnectionProvider() {
            @Override
            public <T> T withConnection(com.oracle.database.selectai.ConnectionCallback<T> callback)
                    throws SQLException, SelectAIException {
                return callback.execute(connection);
            }
        };
    }

    private static String boundClob(CallableStatement statement, int index) throws Exception {
        ArgumentCaptor<Reader> reader = ArgumentCaptor.forClass(Reader.class);
        verify(statement).setCharacterStream(eq(index), reader.capture(), anyInt());
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[128];
        int length;
        while ((length = reader.getValue().read(buffer)) != -1) {
            value.append(buffer, 0, length);
        }
        return value.toString();
    }
}
