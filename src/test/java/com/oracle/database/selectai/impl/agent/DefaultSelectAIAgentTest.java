/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.GenericToolParams;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolType;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import oracle.jdbc.OracleTypes;

import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultSelectAIAgentTest {
    @Test
    void nameOnlyFactoriesLoadExistingResources() throws Exception {
        Connection connection = mock(Connection.class);
        stubAgentMetadata(connection, "Agent description");
        stubMetadata(connection, AgentSql.GET_TASK.get(), "Task description");
        stubMetadata(connection, AgentSql.GET_TOOL.get(), "Tool description");
        stubMetadata(connection, AgentSql.GET_TEAM.get(), "Team description");
        stubAgentAttributes(connection);
        stubEmptyAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES.get());
        stubEmptyAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES.get());
        stubEmptyAttributes(connection, AgentSql.GET_TEAM_ATTRIBUTES.get());
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.agent("AGENT").getDescription()).isEqualTo("Agent description");
        assertThat(client.task("TASK").getDescription()).isEqualTo("Task description");
        assertThat(client.tool("TOOL").getDescription()).isEqualTo("Tool description");
        assertThat(client.team("TEAM").getTeamName()).isEqualTo("TEAM");

        InOrder lookups = inOrder(connection);
        lookups.verify(connection).prepareStatement(AgentSql.GET_AGENT.get());
        lookups.verify(connection).prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get());
        lookups.verify(connection).prepareStatement(AgentSql.GET_TASK.get());
        lookups.verify(connection).prepareStatement(AgentSql.GET_TASK_ATTRIBUTES.get());
        lookups.verify(connection).prepareStatement(AgentSql.GET_TOOL.get());
        lookups.verify(connection).prepareStatement(AgentSql.GET_TOOL_ATTRIBUTES.get());
        lookups.verify(connection).prepareStatement(AgentSql.GET_TEAM.get());
        lookups.verify(connection).prepareStatement(AgentSql.GET_TEAM_ATTRIBUTES.get());

        AgentAttributes agentAttributes = AgentAttributes.builder()
                .profileName("GENAI")
                .role("Role")
                .build();
        Agent agent = client.agent("AGENT_CONFIG", agentAttributes, null, null);
        assertThat(agent.getAgentAttributes()).isSameAs(agentAttributes);

        TaskAttributes taskAttributes = TaskAttributes.builder()
                .instruction("Complete the task")
                .tools(List.of("SQL"))
                .build();
        Task task = client.task("TASK_CONFIG", taskAttributes, null, TaskStatus.DISABLED);
        assertThat(task.getTaskAttributes()).isSameAs(taskAttributes);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DISABLED);

        ToolAttributes toolAttributes = ToolAttributes.builder()
                .toolType(ToolType.SQL)
                .build();
        Tool tool = client.tool("TOOL_CONFIG", toolAttributes, null, ToolStatus.ENABLED);
        assertThat(tool.getToolAttributes()).isSameAs(toolAttributes);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);

        TeamAttributes teamAttributes = TeamAttributes.builder()
                .agents(List.of(TeamMember.builder("AGENT").task("TASK").build()))
                .build();
        Team team = client.team("TEAM_CONFIG", teamAttributes, null, TeamStatus.DISABLED);
        assertThat(team.getTeamAttributes()).isSameAs(teamAttributes);
        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISABLED);
    }

    @Test
    void agentCreateBindsClobPayloadAndStatus() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.CREATE_AGENT.get())).thenReturn(statement);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));
        Agent agent = client.agent("AGENT",
                AgentAttributes.builder().profileName("GENAI").role("Role").build(),
                "Description",
                AgentStatus.DISABLED);

        assertThat(agent.create()).isTrue();

        verify(statement).setString(1, "AGENT");
        verify(statement).setString(3, "DISABLED");
        verify(statement).setCharacterStream(org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.any(java.io.Reader.class), anyInt());
        verify(statement).setCharacterStream(org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.any(java.io.Reader.class), anyInt());
        verify(statement).execute();
    }

    @Test
    void pendingAgentReturnsConfiguredStatusWithoutDatabaseAccess() throws Exception {
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AgentAttributes attributes = AgentAttributes.builder().profileName("GENAI").role("Role").build();
        Agent agent = new DefaultSelectAIAgent(connectionProvider)
                .agent("AGENT", attributes, null, AgentStatus.DISABLED);

        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(new DefaultSelectAIAgent(connectionProvider)
                .agent("AGENT_WITH_DEFAULT", attributes, null, null).getStatus()).isNull();
        verifyNoInteractions(connectionProvider);
    }

    @Test
    void existingAgentStatusRefreshDoesNotChangeDescription() throws Exception {
        Connection connection = mock(Connection.class);
        stubAgentMetadata(connection, "Loaded");
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_AGENT_STATUS,
                "ENABLED", "DISABLED");
        stubAgentAttributes(connection);
        Agent agent = new DefaultSelectAIAgent(provider(connection)).agent("AGENT");

        assertThat(agent.getDescription()).isEqualTo("Loaded");
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ENABLED);
        assertThat(agent.getDescription()).isEqualTo("Loaded");
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(agent.getDescription()).isEqualTo("Loaded");
        verify(statusStatement, times(2)).setString(1, "AGENT");
    }

    @Test
    void existingAgentUsesDatabaseNameForSubsequentLookups() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_AGENT.get())).thenReturn(metadataStatement);
        when(metadataStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("agent_name")).thenReturn("AGENT");
        when(resultSet.getString("status")).thenReturn("ENABLED");
        PreparedStatement attributesStatement = stubAgentAttributes(connection);
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_AGENT_STATUS, "ENABLED");

        Agent agent = new DefaultSelectAIAgent(provider(connection)).agent("agent");

        assertThat(agent.getAgentName()).isEqualTo("AGENT");
        verify(metadataStatement).setString(1, "agent");
        verify(attributesStatement).setString(1, "AGENT");
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ENABLED);
        verify(statusStatement).setString(1, "AGENT");
    }

    @Test
    void createdAgentReadsDatabaseStatusInsteadOfConfiguredStatus() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement createStatement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.CREATE_AGENT.get())).thenReturn(createStatement);
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_AGENT_STATUS, "ENABLED");
        Agent agent = new DefaultSelectAIAgent(provider(connection)).agent("AGENT",
                AgentAttributes.builder().profileName("GENAI").role("Role").build(),
                null, AgentStatus.DISABLED);

        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(agent.create()).isTrue();
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ENABLED);
        verify(statusStatement).setString(1, "AGENT");
    }

    @Test
    void missingAgentFailsDuringFactoryCall() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_AGENT.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        assertThatThrownBy(() -> new DefaultSelectAIAgent(provider(connection)).agent("MISSING_AGENT"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI agent not found: MISSING_AGENT");
    }

    @Test
    void agentFactoryPreservesSqlErrorMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException sqlException = new SQLException("view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_AGENT.get())).thenThrow(sqlException);
        assertThatThrownBy(() -> new DefaultSelectAIAgent(provider(connection)).agent("AGENT"))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(sqlException);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void agentFactoryFailsWhenAttributesCannotBeLoaded() throws Exception {
        Connection connection = mock(Connection.class);
        stubAgentMetadata(connection, "Agent description");
        SQLException sqlException = new SQLException("attribute view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get())).thenThrow(sqlException);

        assertThatThrownBy(() -> new DefaultSelectAIAgent(provider(connection)).agent("AGENT"))
                .isInstanceOf(SelectAIException.class)
                .hasCause(sqlException)
                .hasMessageContaining("AGENT");
    }

    @Test
    void pendingTaskToolAndTeamReturnConfiguredStatusWithoutDatabaseAccess() throws Exception {
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(connectionProvider);

        assertThat(client.task("TASK", TaskAttributes.builder().build(), "Task description",
                TaskStatus.DISABLED).getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(client.tool("TOOL", ToolAttributes.builder().build(), "Tool description",
                ToolStatus.ENABLED).getStatus()).isEqualTo(ToolStatus.ENABLED);
        assertThat(client.team("TEAM", TeamAttributes.builder().build(), "Team description",
                TeamStatus.DISABLED).getStatus()).isEqualTo(TeamStatus.DISABLED);
        assertThat(client.task("TASK_DEFAULT", TaskAttributes.builder().build(), null, null)
                .getStatus()).isNull();
        assertThat(client.tool("TOOL_DEFAULT", ToolAttributes.builder().build(), null, null)
                .getStatus()).isNull();
        assertThat(client.team("TEAM_DEFAULT", TeamAttributes.builder().build(), null, null)
                .getStatus()).isNull();
        verifyNoInteractions(connectionProvider);
    }

    @Test
    void existingTaskStatusRefreshDoesNotChangeDescription() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TASK.get(), "Loaded");
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TASK_STATUS,
                "ENABLED", "DISABLED");
        stubEmptyAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES.get());
        Task task = new DefaultSelectAIAgent(provider(connection)).task("TASK");

        assertThat(task.getDescription()).isEqualTo("Loaded");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);
        assertThat(task.getDescription()).isEqualTo("Loaded");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(task.getDescription()).isEqualTo("Loaded");
        verify(statusStatement, times(2)).setString(1, "TASK");
    }

    @Test
    void droppedTaskAttributeGetterReportsMissingResource() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement dropStatement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.DROP_TASK.get())).thenReturn(dropStatement);
        stubEmptyAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES.get());
        PreparedStatement resourceStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_TASK.get())).thenReturn(resourceStatement);
        when(resourceStatement.executeQuery()).thenReturn(mock(ResultSet.class));
        Task task = DefaultTask.fromMetadata(provider(connection), "TASK",
                TaskAttributes.builder().build(), null, "ENABLED");

        assertThat(task.drop(false)).isTrue();
        assertThatThrownBy(task::getTaskAttributes)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI task not found: TASK");
        verify(resourceStatement).setString(1, "TASK");
    }

    @Test
    void existingTaskUsesDatabaseNameForSubsequentLookups() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_TASK.get())).thenReturn(metadataStatement);
        when(metadataStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("task_name")).thenReturn("TASK");
        when(resultSet.getString("status")).thenReturn("ENABLED");
        PreparedStatement attributesStatement = stubEmptyAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES.get());
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TASK_STATUS, "ENABLED");

        Task task = new DefaultSelectAIAgent(provider(connection)).task("task");

        assertThat(task.getTaskName()).isEqualTo("TASK");
        verify(metadataStatement).setString(1, "task");
        verify(attributesStatement).setString(1, "TASK");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);
        verify(statusStatement).setString(1, "TASK");
    }

    @Test
    void existingToolStatusRefreshDoesNotChangeDescription() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TOOL.get(), "Loaded");
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TOOL_STATUS,
                "ENABLED", "DISABLED");
        stubEmptyAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES.get());
        Tool tool = new DefaultSelectAIAgent(provider(connection)).tool("TOOL");

        assertThat(tool.getDescription()).isEqualTo("Loaded");
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);
        assertThat(tool.getDescription()).isEqualTo("Loaded");
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.DISABLED);
        assertThat(tool.getDescription()).isEqualTo("Loaded");
        verify(statusStatement, times(2)).setString(1, "TOOL");
    }

    @Test
    void existingToolUsesDatabaseNameForSubsequentLookups() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_TOOL.get())).thenReturn(metadataStatement);
        when(metadataStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("tool_name")).thenReturn("TOOL");
        when(resultSet.getString("status")).thenReturn("ENABLED");
        PreparedStatement attributesStatement = stubEmptyAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES.get());
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TOOL_STATUS, "ENABLED");

        Tool tool = new DefaultSelectAIAgent(provider(connection)).tool("tool");

        assertThat(tool.getToolName()).isEqualTo("TOOL");
        verify(metadataStatement).setString(1, "tool");
        verify(attributesStatement).setString(1, "TOOL");
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);
        verify(statusStatement).setString(1, "TOOL");
    }

    @Test
    void existingTeamStatusRefreshDoesNotChangeDescription() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TEAM.get(), "Loaded");
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TEAM_STATUS,
                "ENABLED", "DISABLED");
        stubEmptyAttributes(connection, AgentSql.GET_TEAM_ATTRIBUTES.get());
        Team team = new DefaultSelectAIAgent(provider(connection)).team("TEAM");

        assertThat(team.getDescription()).isEqualTo("Loaded");
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ENABLED);
        assertThat(team.getDescription()).isEqualTo("Loaded");
        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISABLED);
        assertThat(team.getDescription()).isEqualTo("Loaded");
        verify(statusStatement, times(2)).setString(1, "TEAM");
    }

    @Test
    void existingTeamUsesDatabaseNameForSubsequentLookups() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_TEAM.get())).thenReturn(metadataStatement);
        when(metadataStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("agent_team_name")).thenReturn("TEAM");
        when(resultSet.getString("status")).thenReturn("ENABLED");
        PreparedStatement attributesStatement = stubEmptyAttributes(connection, AgentSql.GET_TEAM_ATTRIBUTES.get());
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TEAM_STATUS, "ENABLED");

        Team team = new DefaultSelectAIAgent(provider(connection)).team("team");

        assertThat(team.getTeamName()).isEqualTo("TEAM");
        verify(metadataStatement).setString(1, "team");
        verify(attributesStatement).setString(1, "TEAM");
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ENABLED);
        verify(statusStatement).setString(1, "TEAM");
    }

    @Test
    void createdTaskReadsDatabaseStatusInsteadOfConfiguredStatus() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement createStatement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.CREATE_TASK.get())).thenReturn(createStatement);
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TASK_STATUS, "ENABLED");
        Task task = new DefaultSelectAIAgent(provider(connection)).task("TASK",
                TaskAttributes.builder().build(), null, TaskStatus.DISABLED);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(task.create()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ENABLED);
        verify(statusStatement).setString(1, "TASK");
    }

    @Test
    void createdToolReadsDatabaseStatusInsteadOfConfiguredStatus() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement createStatement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.CREATE_TOOL.get())).thenReturn(createStatement);
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TOOL_STATUS, "ENABLED");
        Tool tool = new DefaultSelectAIAgent(provider(connection)).tool("TOOL",
                ToolAttributes.builder().build(), null, ToolStatus.DISABLED);

        assertThat(tool.getStatus()).isEqualTo(ToolStatus.DISABLED);
        assertThat(tool.create()).isTrue();
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);
        verify(statusStatement).setString(1, "TOOL");
    }

    @Test
    void createdTeamReadsDatabaseStatusInsteadOfConfiguredStatus() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement createStatement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.CREATE_TEAM.get())).thenReturn(createStatement);
        PreparedStatement statusStatement = stubStatus(connection, AgentSql.GET_TEAM_STATUS, "ENABLED");
        Team team = new DefaultSelectAIAgent(provider(connection)).team("TEAM",
                TeamAttributes.builder().build(), null, TeamStatus.DISABLED);

        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISABLED);
        assertThat(team.create()).isTrue();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ENABLED);
        verify(statusStatement).setString(1, "TEAM");
    }

    @Test
    void missingResourcesFailAtTheirLookupBoundary() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement taskStatement = mock(PreparedStatement.class);
        PreparedStatement toolStatement = mock(PreparedStatement.class);
        PreparedStatement teamStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_TASK.get())).thenReturn(taskStatement);
        when(connection.prepareStatement(AgentSql.GET_TOOL.get())).thenReturn(toolStatement);
        when(connection.prepareStatement(AgentSql.GET_TEAM.get())).thenReturn(teamStatement);
        when(taskStatement.executeQuery()).thenReturn(mock(ResultSet.class));
        when(toolStatement.executeQuery()).thenReturn(mock(ResultSet.class));
        when(teamStatement.executeQuery()).thenReturn(mock(ResultSet.class));
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(() -> client.task("MISSING_TASK"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI task not found: MISSING_TASK");
        assertThatThrownBy(() -> client.tool("MISSING_TOOL"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI tool not found: MISSING_TOOL");
        assertThatThrownBy(() -> client.team("MISSING_TEAM"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI team not found: MISSING_TEAM");
    }

    @Test
    void resourceLookupsPreserveSqlErrorMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException sqlException = new SQLException("view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TASK.get())).thenThrow(sqlException);
        when(connection.prepareStatement(AgentSql.GET_TOOL.get())).thenThrow(sqlException);
        when(connection.prepareStatement(AgentSql.GET_TEAM.get())).thenThrow(sqlException);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertSqlStatusFailure(() -> client.task("TASK"), sqlException);
        assertSqlStatusFailure(() -> client.tool("TOOL"), sqlException);
        assertSqlStatusFailure(() -> client.team("TEAM"), sqlException);
    }

    @Test
    void teamFactoryFailsWhenAttributesCannotBeLoaded() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TEAM.get(), "Team description");
        SQLException sqlException = new SQLException("attribute view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TEAM_ATTRIBUTES.get())).thenThrow(sqlException);

        assertThatThrownBy(() -> new DefaultSelectAIAgent(provider(connection)).team("TEAM"))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(sqlException);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    private static void assertSqlStatusFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable lookup,
                                               SQLException sqlException) {
        assertThatThrownBy(lookup)
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(sqlException);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void getAgentAttributesFetchesCurrentDatabaseAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubAgentMetadata(connection, "Agent description");
        PreparedStatement statement = stubAgentAttributes(connection);

        Agent agent = new DefaultSelectAIAgent(provider(connection)).agent("AGENT");

        assertThat(agent.getAgentAttributes().getProfileName()).isEqualTo("GENAI");
        assertThat(agent.getAgentAttributes().getRole()).isEqualTo("Database agent");
        verify(statement, times(3)).setString(1, "AGENT");
    }

    @Test
    void getAgentAttributesWrapsDatabaseFailures() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException sqlException = new SQLException("view unavailable", "42000", 942);
        stubAgentMetadata(connection, "Agent description");
        PreparedStatement statement = stubAgentAttributes(connection);
        when(connection.prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get()))
                .thenReturn(statement).thenThrow(sqlException);

        Agent agent = new DefaultSelectAIAgent(provider(connection)).agent("AGENT");

        assertThatThrownBy(agent::getAgentAttributes)
                .isInstanceOf(SelectAIException.class)
                .hasCause(sqlException)
                .hasMessageContaining("AGENT");
    }

    @Test
    void getTaskAttributesFetchesCurrentDatabaseAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TASK.get(), "Task description");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_TASK_ATTRIBUTES.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true, false,
                true, true, true, true, false);
        when(resultSet.getString("attribute_name"))
                .thenReturn("instruction", "tools", "input", "enable_human_tool",
                        "instruction", "tools", "input", "enable_human_tool");
        when(resultSet.getString("attribute_value"))
                .thenReturn("Process the request", "[\"SQL\"]", "[\"input_task\"]", "true",
                        "Process the request", "[\"SQL\"]", "[\"input_task\"]", "true");

        Task task = new DefaultSelectAIAgent(provider(connection)).task("TASK");

        TaskAttributes attributes = task.getTaskAttributes();
        assertThat(attributes.getInstruction()).isEqualTo("Process the request");
        assertThat(attributes.getTools()).containsExactly("SQL");
        assertThat(attributes.getInput()).containsExactly("input_task");
        assertThat(attributes.getEnableHumanTool()).isTrue();
        verify(statement, times(2)).setString(1, "TASK");
    }

    @Test
    void getToolAttributesFetchesCurrentDatabaseAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TOOL.get(), "Tool description");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_TOOL_ATTRIBUTES.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true, true, false,
                true, true, true, true, true, false);
        when(resultSet.getString("attribute_name"))
                .thenReturn("instruction", "function", "tool_type", "tool_params", "tool_inputs",
                        "instruction", "function", "tool_type", "tool_params", "tool_inputs");
        when(resultSet.getString("attribute_value"))
                .thenReturn("Run SQL", "RUN_SQL", "SQL", "{\"profile_name\":\"GENAI\"}",
                        "[{\"name\":\"nl_query\",\"description\":\"Query to run\"}]",
                        "Run SQL", "RUN_SQL", "SQL", "{\"profile_name\":\"GENAI\"}",
                        "[{\"name\":\"nl_query\",\"description\":\"Query to run\"}]");

        Tool tool = new DefaultSelectAIAgent(provider(connection)).tool("TOOL");

        ToolAttributes attributes = tool.getToolAttributes();
        assertThat(attributes.getInstruction()).isEqualTo("Run SQL");
        assertThat(attributes.getFunction()).isEqualTo("RUN_SQL");
        assertThat(attributes.getToolType()).isEqualTo(ToolType.SQL);
        assertThat(attributes.getToolParams()).isInstanceOf(SqlToolParams.class);
        assertThat(((SqlToolParams) attributes.getToolParams()).getProfileName()).isEqualTo("GENAI");
        assertThat(attributes.getToolInputs()).singleElement().satisfies(input -> {
            assertThat(input.getName()).isEqualTo("nl_query");
            assertThat(input.getDescription()).isEqualTo("Query to run");
        });
        verify(statement, times(2)).setString(1, "TOOL");
    }

    @Test
    void getTeamAttributesFetchesCurrentDatabaseAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TEAM.get(), "Team description");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_TEAM_ATTRIBUTES.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true, false,
                true, true, true, true, false);
        when(resultSet.getString("attribute_name"))
                .thenReturn("agents", "process", "long_term_memory_length", "supervisor_agent",
                        "agents", "process", "long_term_memory_length", "supervisor_agent");
        when(resultSet.getString("attribute_value"))
                .thenReturn("[{\"name\":\"AGENT\",\"task\":\"TASK\"}]",
                        "sequential", "12", "SUPERVISOR",
                        "[{\"name\":\"AGENT\",\"task\":\"TASK\"}]",
                        "sequential", "12", "SUPERVISOR");

        Team team = new DefaultSelectAIAgent(provider(connection)).team("TEAM");

        TeamAttributes attributes = team.getTeamAttributes();
        assertThat(attributes.getAgents()).hasSize(1);
        assertThat(attributes.getAgents().get(0).getName()).isEqualTo("AGENT");
        assertThat(attributes.getAgents().get(0).getTask()).isEqualTo("TASK");
        assertThat(attributes.getProcess()).isEqualTo("sequential");
        assertThat(attributes.getLongTermMemoryLength()).isEqualTo(12);
        assertThat(attributes.getSupervisorAgent()).isEqualTo("SUPERVISOR");
        verify(statement, times(2)).setString(1, "TEAM");
    }

    @Test
    void configuredToolCannotRunBeforeCreate() {
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(noopProvider());
        Tool tool = client.tool("SQL_TOOL",
                ToolAttributes.builder().toolType(ToolType.SQL).build(),
                null,
                null);

        assertThatThrownBy(() -> tool.run("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("call create() first");
    }

    @Test
    void listTeamsLoadsMetadataAndAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TEAM.get(), "Support team");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TEAMS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("agent_team_name")).thenReturn("TEAM");
        when(rows.getString("description")).thenReturn("Support team");
        when(rows.getString("status")).thenReturn("ENABLED");
        PreparedStatement attributesStatement = stubEmptyAttributes(connection, AgentSql.GET_TEAM_ATTRIBUTES.get());
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        List<Team> teams = client.listTeams();

        assertThat(teams).extracting(Team::getTeamName).containsExactly("TEAM");
        assertThat(teams.get(0).getDescription()).isEqualTo("Support team");

        verify(statement).executeQuery();
        verify(attributesStatement).setString(1, "TEAM");
        verify(attributesStatement).executeQuery();
    }

    @Test
    void listTeamsPreservesAttributeQueryFailureMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TEAMS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("agent_team_name")).thenReturn("TEAM");
        SQLException failure = new SQLException("attribute view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TEAM_ATTRIBUTES.get())).thenThrow(failure);

        assertThatThrownBy(new DefaultSelectAIAgent(provider(connection))::listTeams)
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void listTeamsReturnsEmptyListWhenViewHasNoRows() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TEAMS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);

        assertThat(new DefaultSelectAIAgent(provider(connection)).listTeams()).isEmpty();
        verify(statement).executeQuery();
    }

    @Test
    void listAgentsLoadsMetadataAndAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("agent_name")).thenReturn("AGENT");
        when(rows.getString("description")).thenReturn("Agent description");
        when(rows.getString("status")).thenReturn("ENABLED");
        PreparedStatement attributesStatement = stubAgentAttributes(connection);

        List<Agent> agents = new DefaultSelectAIAgent(provider(connection)).listAgents();

        assertThat(agents).extracting(Agent::getAgentName).containsExactly("AGENT");
        assertThat(agents.get(0).getDescription()).isEqualTo("Agent description");
        verify(statement).executeQuery();
        verify(attributesStatement).setString(1, "AGENT");
        verify(attributesStatement).executeQuery();
    }

    @Test
    void listAgentsPreservesAttributeQueryFailureMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("agent_name")).thenReturn("AGENT");
        when(rows.getString("status")).thenReturn("ENABLED");
        SQLException failure = new SQLException("attribute view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get())).thenThrow(failure);

        assertThatThrownBy(new DefaultSelectAIAgent(provider(connection))::listAgents)
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void listTasksLoadsMetadataAndAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TASK.get(), "Task description");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TASKS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("task_name")).thenReturn("TASK");
        when(rows.getString("description")).thenReturn("Task description");
        when(rows.getString("status")).thenReturn("DISABLED");
        PreparedStatement attributesStatement = stubEmptyAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES.get());

        List<Task> tasks = new DefaultSelectAIAgent(provider(connection)).listTasks();

        assertThat(tasks).extracting(Task::getTaskName).containsExactly("TASK");
        assertThat(tasks.get(0).getDescription()).isEqualTo("Task description");
        verify(statement).executeQuery();
        verify(attributesStatement).setString(1, "TASK");
        verify(attributesStatement).executeQuery();
    }

    @Test
    void listTasksPreservesAttributeQueryFailureMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TASKS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("task_name")).thenReturn("TASK");
        SQLException failure = new SQLException("attribute view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TASK_ATTRIBUTES.get())).thenThrow(failure);

        assertThatThrownBy(new DefaultSelectAIAgent(provider(connection))::listTasks)
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void listToolsLoadsMetadataAndAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TOOL.get(), "Tool description");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TOOLS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("tool_name")).thenReturn("TOOL");
        when(rows.getString("description")).thenReturn("Tool description");
        when(rows.getString("status")).thenReturn("ENABLED");
        PreparedStatement attributesStatement = stubEmptyAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES.get());

        List<Tool> tools = new DefaultSelectAIAgent(provider(connection)).listTools();

        assertThat(tools).extracting(Tool::getToolName).containsExactly("TOOL");
        assertThat(tools.get(0).getDescription()).isEqualTo("Tool description");
        verify(statement).executeQuery();
        verify(attributesStatement).setString(1, "TOOL");
        verify(attributesStatement).executeQuery();
    }

    @Test
    void listToolsPreservesToolTypeIntroducedByNewerDatabaseRelease() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TOOLS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("tool_name")).thenReturn("TOOL");
        when(rows.getString("description")).thenReturn("Future tool");
        when(rows.getString("status")).thenReturn("ENABLED");

        PreparedStatement attributesStatement = mock(PreparedStatement.class);
        ResultSet attributes = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_TOOL_ATTRIBUTES.get())).thenReturn(attributesStatement);
        when(attributesStatement.executeQuery()).thenReturn(attributes);
        when(attributes.next()).thenReturn(true, true, true, false, true, true, true, false);
        when(attributes.getString("attribute_name"))
                .thenReturn("tool_type", "tool_params", "tool_inputs", "tool_type", "tool_params", "tool_inputs");
        when(attributes.getString("attribute_value"))
                .thenReturn("FUTURE_TOOL", "{\"future_option\":true}", "[{\"name\":\"query\"}]",
                        "FUTURE_TOOL", "{\"future_option\":true}", "[{\"name\":\"query\"}]");

        List<Tool> tools = new DefaultSelectAIAgent(provider(connection)).listTools();

        assertThat(tools).singleElement().satisfies(tool -> {
            ToolAttributes loaded = tool.getToolAttributes();
            assertThat(loaded.getToolTypeValue()).isEqualTo("FUTURE_TOOL");
            assertThat(loaded.getToolType().isKnown()).isFalse();
            assertThat(loaded.getToolParams()).isInstanceOf(GenericToolParams.class);
            assertThat(loaded.getToolParams().toMap()).containsEntry("future_option", true);
            assertThat(loaded.getToolInputs()).singleElement().satisfies(input ->
                    assertThat(input.getName()).isEqualTo("query"));
        });
        verify(attributesStatement, org.mockito.Mockito.times(2)).setString(1, "TOOL");
        verify(attributesStatement, org.mockito.Mockito.times(2)).executeQuery();
    }

    @Test
    void listToolsPreservesAttributeQueryFailureMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TOOLS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("tool_name")).thenReturn("TOOL");
        SQLException failure = new SQLException("attribute view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TOOL_ATTRIBUTES.get())).thenThrow(failure);

        assertThatThrownBy(new DefaultSelectAIAgent(provider(connection))::listTools)
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void listAgentsReturnsEmptyListWhenNoRowsExist() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_AGENTS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);

        assertThat(new DefaultSelectAIAgent(provider(connection)).listAgents()).isEmpty();
    }

    @Test
    void listToolsWrapsSqlFailureWithMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("boom", "42000", 20000);
        when(connection.prepareStatement(AgentSql.LIST_TOOLS.get())).thenThrow(failure);

        assertThatThrownBy(new DefaultSelectAIAgent(provider(connection))::listTools)
                .isInstanceOf(SelectAIException.class)
                .hasCause(failure)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(20000);
                    assertThat(wrapped.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void listTasksRejectsUnsupportedStatus() throws Exception {
        Connection connection = mock(Connection.class);
        stubMetadata(connection, AgentSql.GET_TASK.get(), "Task description");
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.LIST_TASKS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("task_name")).thenReturn("TASK");
        when(rows.getString("status")).thenReturn("UNKNOWN");
        stubEmptyAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES.get());

        assertThatThrownBy(new DefaultSelectAIAgent(provider(connection))::listTasks)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Unsupported Select AI task status");
    }

    @Test
    void existingResourceStatusRejectsUnsupportedValuesForEachResourceType() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement agentStatus = stubStatus(connection, AgentSql.GET_AGENT_STATUS, "UNKNOWN");
        PreparedStatement taskStatus = stubStatus(connection, AgentSql.GET_TASK_STATUS, "UNKNOWN");
        PreparedStatement toolStatus = stubStatus(connection, AgentSql.GET_TOOL_STATUS, "UNKNOWN");
        PreparedStatement teamStatus = stubStatus(connection, AgentSql.GET_TEAM_STATUS, "UNKNOWN");
        ConnectionProvider provider = provider(connection);

        Agent agent = DefaultAgent.fromMetadata(provider, "AGENT", agentAttributes(), null, "ENABLED");
        Task task = DefaultTask.fromMetadata(provider, "TASK", TaskAttributes.builder().build(), null, "ENABLED");
        Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
        Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");

        assertThatThrownBy(agent::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Unsupported Select AI agent status");
        assertThatThrownBy(task::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Unsupported Select AI task status");
        assertThatThrownBy(tool::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Unsupported Select AI tool status");
        assertThatThrownBy(team::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Unsupported Select AI team status");

        verify(agentStatus).setString(1, "AGENT");
        verify(taskStatus).setString(1, "TASK");
        verify(toolStatus).setString(1, "TOOL");
        verify(teamStatus).setString(1, "TEAM");
    }

    @Test
    void getDefinitionBindsObjectTypeAndDependentObjectsParams() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        Clob clob = mock(Clob.class);
        when(connection.prepareCall(AgentSql.GET_DEFINITION.get())).thenReturn(statement);
        when(statement.getClob(1)).thenReturn(clob);
        when(clob.getCharacterStream()).thenReturn(new java.io.StringReader("BEGIN NULL; END;"));
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThat(client.getDefinition(AgentObjectType.TEAM, "TEAM", true)).contains("BEGIN");

        verify(statement).setString(2, "TEAM");
        verify(statement).setString(3, "TEAM");
        verify(statement).setCharacterStream(anyInt(), org.mockito.ArgumentMatchers.any(java.io.Reader.class),
                anyInt());
    }

    @Test
    void importTeamBindsInlineSpecification() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.IMPORT_TEAM.get())).thenReturn(statement);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));
        ImportTeamRequest request = ImportTeamRequest.builder("GENAI", "TEAM")
                .specification("{}")
                .force(true)
                .build();

        assertThat(client.importTeam(request)).isTrue();

        verify(statement).setString(1, "GENAI");
        verify(statement).setString(2, "TEAM");
        verify(statement).setObject(6, true, OracleTypes.PLSQL_BOOLEAN);
        verify(statement).execute();
    }

    @Test
    void createTeamRequiresAttributes() {
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(noopProvider());

        assertThatThrownBy(() -> client.team("TEAM", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributes");

        assertThat(client.team("TEAM", TeamAttributes.builder()
                        .agents(List.of(TeamMember.builder("AGENT").task("TASK").build()))
                        .build(),
                null, null).getTeamName()).isEqualTo("TEAM");
    }

    @Test
    void sharedTeamStateOperationsUseOwnerQualifiedNameWithoutLoadingTeamMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement getState = mock(CallableStatement.class);
        CallableStatement setState = mock(CallableStatement.class);
        when(connection.prepareCall(AgentSql.GET_TEAM_STATE.get())).thenReturn(getState);
        when(connection.prepareCall(AgentSql.SET_EXECUTION_STATE.get())).thenReturn(setState);
        when(getState.getString(1)).thenReturn("WAITING_FOR_HUMAN");
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));
        TeamExecutionState firstState = client.getTeamStateByConversationId(
                "TEAM_OWNER.SHARED_TEAM", "C1");
        assertThat(firstState).isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);
        assertThat(client.getTeamStateByConversationId("TEAM_OWNER.SHARED_TEAM", "C1"))
                .isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);
        assertThat(client.setTeamExecutionState("TEAM_OWNER.SHARED_TEAM",
                ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED).conversationId("C1").build()))
                .isTrue();

        verify(getState, org.mockito.Mockito.times(2)).registerOutParameter(1, Types.VARCHAR);
        verify(getState, org.mockito.Mockito.times(2)).setString(2, "TEAM_OWNER.SHARED_TEAM");
        verify(getState, org.mockito.Mockito.times(2)).setCharacterStream(eq(3), any(Reader.class), anyInt());
        verify(getState, org.mockito.Mockito.times(2)).execute();
        verify(setState).setString(1, "TEAM_OWNER.SHARED_TEAM");
        verify(setState).setCharacterStream(eq(2), any(Reader.class), anyInt());
        verify(setState).setCharacterStream(eq(3), any(Reader.class), anyInt());
        verify(setState).execute();
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void sharedTeamStateOperationsValidateInputsBeforeJdbc() {
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(noopProvider());

        assertThatThrownBy(() -> client.getTeamStateByConversationId(" ", "C1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getTeamStateByConversationId("OWNER.TEAM", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.setTeamExecutionState("OWNER.TEAM", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.setTeamExecutionState("OWNER.TEAM",
                ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
    }

    @Test
    void sharedTeamStateOperationsWrapSqlFailures() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement getState = mock(CallableStatement.class);
        SQLException failure = new SQLException("state unavailable", "42000", 942);
        when(connection.prepareCall(AgentSql.GET_TEAM_STATE.get())).thenReturn(getState);
        when(getState.execute()).thenThrow(failure);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(() -> client.getTeamStateByConversationId("OWNER.TEAM", "C1"))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getErrorCode()).isEqualTo(942);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void sqlFailuresAreWrappedWithMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException sqlException = new SQLException("boom", "42000", 20000);
        when(connection.prepareStatement(AgentSql.LIST_TEAMS.get())).thenThrow(sqlException);
        DefaultSelectAIAgent client = new DefaultSelectAIAgent(provider(connection));

        assertThatThrownBy(client::listTeams)
                .isInstanceOf(SelectAIException.class)
                .hasCause(sqlException)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(20000);
                    assertThat(wrapped.getSqlState()).isEqualTo("42000");
                });
    }

    private static void stubAgentMetadata(Connection connection, String description) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(AgentSql.GET_AGENT.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("agent_name")).thenReturn("AGENT");
        when(resultSet.getString("status")).thenReturn("ENABLED");
        when(resultSet.getString("description")).thenReturn(description);
    }

    private static void stubMetadata(Connection connection, String sql, String description) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("task_name")).thenReturn("TASK");
        when(resultSet.getString("tool_name")).thenReturn("TOOL");
        when(resultSet.getString("agent_team_name")).thenReturn("TEAM");
        when(resultSet.getString("status")).thenReturn("ENABLED");
        when(resultSet.getString("description")).thenReturn(description);
    }

    private static PreparedStatement stubStatus(Connection connection, AgentSql query, String... values)
            throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(query.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("status"))
                .thenReturn(values[0], Arrays.copyOfRange(values, 1, values.length));
        return statement;
    }

    private static PreparedStatement stubAgentAttributes(Connection connection) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get())).thenReturn(statement);
        when(statement.executeQuery()).thenAnswer(invocation -> {
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getString("attribute_name")).thenReturn("profile_name", "role");
            when(resultSet.getString("attribute_value")).thenReturn("GENAI", "Database agent");
            return resultSet;
        });
        return statement;
    }

    private static AgentAttributes agentAttributes() {
        return AgentAttributes.builder()
                .profileName("PROFILE")
                .role("Assist")
                .build();
    }

    private static PreparedStatement stubEmptyAttributes(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        return statement;
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

    private static ConnectionProvider noopProvider() {
        return new ConnectionProvider() {
            @Override
            public <T> T withConnection(com.oracle.database.selectai.ConnectionCallback<T> callback) {
                return null;
            }
        };
    }
}
