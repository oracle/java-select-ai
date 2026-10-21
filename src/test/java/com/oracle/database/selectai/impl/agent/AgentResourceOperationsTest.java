/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionCallback;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolType;
import com.oracle.database.selectai.impl.DataSourceConnectionProvider;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import oracle.jdbc.OracleTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentResourceOperationsTest {
    @Test
    void agentLifecycleBindsValuesAndRejectsOperationsAfterDrop() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement create = stubCall(connection, AgentSql.CREATE_AGENT);
        CallableStatement enable = stubCall(connection, AgentSql.ENABLE_AGENT);
        CallableStatement disable = stubCall(connection, AgentSql.DISABLE_AGENT);
        CallableStatement drop = stubCall(connection, AgentSql.DROP_AGENT);
        Agent agent = new DefaultAgent(provider(connection), "AGENT",
                AgentAttributes.builder().profileName("PROFILE").role("Help customers").build(),
                "Description", AgentStatus.DISABLED);

        assertThatThrownBy(agent::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agent.drop(false)).isInstanceOf(IllegalStateException.class);
        assertThat(agent.getAgentName()).isEqualTo("AGENT");
        assertThat(agent.getDescription()).isEqualTo("Description");
        assertThat(agent.create()).isTrue();
        assertThat(agent.enable()).isTrue();
        assertThat(agent.disable()).isTrue();
        assertThat(agent.drop(true)).isTrue();
        assertThat(agent.drop(true)).isTrue();
        assertThatThrownBy(agent::disable).isInstanceOf(IllegalStateException.class);

        verify(create).setString(1, "AGENT");
        assertThat(clobInput(create, 2)).contains("\"profile_name\":\"PROFILE\"");
        verify(create).setString(3, "DISABLED");
        assertThat(clobInput(create, 4)).isEqualTo("Description");
        verify(enable).setString(1, "AGENT");
        verify(disable).setString(1, "AGENT");
        verify(drop, times(2)).setString(1, "AGENT");
        verify(drop, times(2)).setObject(2, true, OracleTypes.PLSQL_BOOLEAN);
        verify(enable, times(1)).execute();
        verify(disable, times(1)).execute();
    }

    @Test
    void agentAttributeUpdatesBindObjectTypeAndRefreshAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement setAttribute = stubCall(connection, AgentSql.SET_AGENT_ATTRIBUTE);
        CallableStatement setAttributes = stubCall(connection, AgentSql.SET_AGENT_ATTRIBUTES);
        PreparedStatement attributesQuery = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get())).thenReturn(attributesQuery);
        when(attributesQuery.executeQuery()).thenAnswer(invocation -> agentAttributesResultSet());

        Agent agent = DefaultAgent.fromMetadata(provider(connection), "AGENT", agentAttributes(), null, "ENABLED");

        assertThat(agent.setAttribute("role", "Updated role")).isTrue();
        assertThat(agent.setAttribute("enable_human_tool", true)).isTrue();
        assertThat(agent.setAttribute("short_term_memory_length", 10)).isTrue();
        assertThat(agent.setAttribute("short_term_memory_length", 10.5F)).isTrue();
        assertThat(agent.setAttributes(AgentAttributes.builder()
                .profileName("PROFILE")
                .role("Bulk role")
                .enableHumanTool(false)
                .build())).isTrue();

        verify(setAttribute, times(4)).setString(1, "AGENT");
        verify(setAttribute, times(4)).setString(2, "AGENT");
        verify(setAttribute).setString(3, "role");
        verify(setAttribute).setString(3, "enable_human_tool");
        verify(setAttribute, times(2)).setString(3, "short_term_memory_length");
        verify(setAttribute).setBoolean(4, true);
        verify(setAttribute).setInt(4, 10);
        verify(setAttribute).setFloat(4, 10.5F);
        assertThat(clobInput(setAttribute, 4)).isEqualTo("Updated role");
        verify(setAttribute, times(4)).execute();

        verify(setAttributes).setString(1, "AGENT");
        verify(setAttributes).setString(2, "AGENT");
        assertThat(clobInput(setAttributes, 3)).contains("\"role\":\"Bulk role\"");
        verify(setAttributes).execute();
        verify(attributesQuery, times(5)).executeQuery();
    }

    @Test
    void agentAttributeUpdatesValidateLifecycleAndArguments() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Agent pending = new DefaultAgent(provider, "AGENT", agentAttributes(), null, null);

        assertThatThrownBy(() -> pending.setAttribute("role", "Role"))
                .isInstanceOf(IllegalStateException.class);

        Agent existing = DefaultAgent.fromMetadata(provider, "AGENT", agentAttributes(), null, "ENABLED");
        assertThatThrownBy(() -> existing.setAttribute(" ", "Role"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> existing.setAttribute("count", (Integer) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> existing.setAttribute("count", (Float) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> existing.setAttributes(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void agentAttributeUpdateWrapsSqlFailure() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = stubCall(connection, AgentSql.SET_AGENT_ATTRIBUTE);
        SQLException failure = new SQLException("database rejected agent attribute", "72000", 20001);
        doThrow(failure).when(statement).execute();

        Agent agent = DefaultAgent.fromMetadata(provider(connection), "AGENT", agentAttributes(), null, "ENABLED");

        assertThatThrownBy(() -> agent.setAttribute("role", "Updated role"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE")
                .hasCause(failure);
        verify(statement).execute();
        verify(connection, never()).prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get());
    }

    @Test
    void taskAttributeUpdatesBindObjectTypeAndRefreshAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement setAttribute = stubCall(connection, AgentSql.SET_TASK_ATTRIBUTE);
        CallableStatement setAttributes = stubCall(connection, AgentSql.SET_TASK_ATTRIBUTES);
        PreparedStatement attributesQuery = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_TASK_ATTRIBUTES.get())).thenReturn(attributesQuery);
        when(attributesQuery.executeQuery()).thenAnswer(invocation -> taskAttributesResultSet());

        Task task = DefaultTask.fromMetadata(provider(connection), "TASK",
                TaskAttributes.builder().instruction("Initial").build(), null, "ENABLED");

        assertThat(task.setAttribute("instruction", "Updated")).isTrue();
        assertThat(task.setAttribute("enable_human_tool", true)).isTrue();
        assertThat(task.setAttribute("memory", 10)).isTrue();
        assertThat(task.setAttribute("temperature", 0.5F)).isTrue();
        assertThat(task.setAttributes(TaskAttributes.builder().instruction("Bulk").build())).isTrue();

        verify(setAttribute, times(4)).setString(1, "TASK");
        verify(setAttribute, times(4)).setString(2, "TASK");
        verify(setAttribute).setString(3, "instruction");
        verify(setAttribute).setString(3, "enable_human_tool");
        verify(setAttribute).setString(3, "memory");
        verify(setAttribute).setString(3, "temperature");
        verify(setAttribute).setBoolean(4, true);
        verify(setAttribute).setInt(4, 10);
        verify(setAttribute).setFloat(4, 0.5F);
        assertThat(clobInput(setAttribute, 4)).isEqualTo("Updated");
        verify(setAttribute, times(4)).execute();

        verify(setAttributes).setString(1, "TASK");
        verify(setAttributes).setString(2, "TASK");
        assertThat(clobInput(setAttributes, 3)).contains("\"instruction\":\"Bulk\"");
        verify(setAttributes).execute();
        verify(attributesQuery, times(5)).executeQuery();
    }

    @Test
    void typedToolInputUpdateBindsJsonAndRefreshesMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement update = stubCall(connection, AgentSql.SET_TOOL_ATTRIBUTES);
        PreparedStatement attributesQuery = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_TOOL_ATTRIBUTES.get())).thenReturn(attributesQuery);
        when(attributesQuery.executeQuery()).thenAnswer(invocation -> {
            ResultSet rows = mock(ResultSet.class);
            when(rows.next()).thenReturn(true, false);
            when(rows.getString("attribute_name")).thenReturn("tool_inputs");
            when(rows.getString("attribute_value"))
                    .thenReturn("[{\"name\":\"data_guard\",\"description\":\"Updated\"}]");
            return rows;
        });
        Tool tool = DefaultTool.fromMetadata(provider(connection), "TOOL",
                ToolAttributes.builder().build(), null, "ENABLED");

        assertThat(tool.setAttributes(ToolAttributes.builder().toolInputs(List.of(ToolInput.builder()
                .name("data_guard").description("Updated").build())).build())).isTrue();

        assertThat(clobInput(update, 3))
                .isEqualTo("{\"tool_inputs\":[{\"name\":\"data_guard\",\"description\":\"Updated\"}]}");
        verify(update).setString(1, "TOOL");
        verify(update).setString(2, "TOOL");
        verify(update).execute();
        assertThat(tool.getToolAttributes().getToolInputs()).singleElement().satisfies(input -> {
            assertThat(input.getName()).isEqualTo("data_guard");
            assertThat(input.getDescription()).isEqualTo("Updated");
        });
        verify(attributesQuery, times(2)).executeQuery();
    }

    @Test
    void toolAttributeUpdatesBindObjectTypeAndRefreshAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement setAttribute = stubCall(connection, AgentSql.SET_TOOL_ATTRIBUTE);
        CallableStatement setAttributes = stubCall(connection, AgentSql.SET_TOOL_ATTRIBUTES);
        PreparedStatement attributesQuery = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_TOOL_ATTRIBUTES.get())).thenReturn(attributesQuery);
        when(attributesQuery.executeQuery()).thenAnswer(invocation -> toolAttributesResultSet());

        Tool tool = DefaultTool.fromMetadata(provider(connection), "TOOL",
                ToolAttributes.builder().toolType(ToolType.SQL).build(), null, "ENABLED");

        assertThat(tool.setAttribute("instruction", "Updated")).isTrue();
        assertThat(tool.setAttribute("enabled", true)).isTrue();
        assertThat(tool.setAttribute("limit", 10)).isTrue();
        assertThat(tool.setAttribute("temperature", 0.5F)).isTrue();
        assertThat(tool.setAttributes(ToolAttributes.builder()
                .toolParams(SqlToolParams.builder().profileName("UPDATED_PROFILE").build())
                .build())).isTrue();

        verify(setAttribute, times(4)).setString(1, "TOOL");
        verify(setAttribute, times(4)).setString(2, "TOOL");
        verify(setAttribute).setString(3, "instruction");
        verify(setAttribute).setString(3, "enabled");
        verify(setAttribute).setString(3, "limit");
        verify(setAttribute).setString(3, "temperature");
        verify(setAttribute).setBoolean(4, true);
        verify(setAttribute).setInt(4, 10);
        verify(setAttribute).setFloat(4, 0.5F);
        assertThat(clobInput(setAttribute, 4)).isEqualTo("Updated");
        verify(setAttribute, times(4)).execute();

        verify(setAttributes).setString(1, "TOOL");
        verify(setAttributes).setString(2, "TOOL");
        assertThat(clobInput(setAttributes, 3))
                .isEqualTo("{\"tool_params\":{\"profile_name\":\"UPDATED_PROFILE\"}}");
        verify(setAttributes).execute();
        verify(attributesQuery, times(5)).executeQuery();
        assertThat(tool.getToolAttributes().getToolParams()).isInstanceOfSatisfying(SqlToolParams.class,
                params -> assertThat(params.getProfileName()).isEqualTo("UPDATED_PROFILE"));
    }

    @Test
    void teamAttributeUpdatesBindObjectTypeAndRefreshAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement setAttribute = stubCall(connection, AgentSql.SET_TEAM_ATTRIBUTE);
        CallableStatement setAttributes = stubCall(connection, AgentSql.SET_TEAM_ATTRIBUTES);
        PreparedStatement attributesQuery = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_TEAM_ATTRIBUTES.get())).thenReturn(attributesQuery);
        when(attributesQuery.executeQuery()).thenAnswer(invocation -> teamAttributesResultSet());

        Team team = DefaultTeam.fromMetadata(provider(connection), "TEAM",
                TeamAttributes.builder().process("sequential").build(), null, "ENABLED");

        assertThat(team.setAttribute("process", "Updated")).isTrue();
        assertThat(team.setAttribute("enabled", true)).isTrue();
        assertThat(team.setAttribute("memory", 10)).isTrue();
        assertThat(team.setAttribute("temperature", 0.5F)).isTrue();
        assertThat(team.setAttributes(TeamAttributes.builder().process("Bulk").build())).isTrue();

        verify(setAttribute, times(4)).setString(1, "TEAM");
        verify(setAttribute, times(4)).setString(2, "TEAM");
        verify(setAttribute).setString(3, "process");
        verify(setAttribute).setString(3, "enabled");
        verify(setAttribute).setString(3, "memory");
        verify(setAttribute).setString(3, "temperature");
        verify(setAttribute).setBoolean(4, true);
        verify(setAttribute).setInt(4, 10);
        verify(setAttribute).setFloat(4, 0.5F);
        assertThat(clobInput(setAttribute, 4)).isEqualTo("Updated");
        verify(setAttribute, times(4)).execute();

        verify(setAttributes).setString(1, "TEAM");
        verify(setAttributes).setString(2, "TEAM");
        assertThat(clobInput(setAttributes, 3)).contains("\"process\":\"Bulk\"");
        verify(setAttributes).execute();
        verify(attributesQuery, times(5)).executeQuery();
    }

    @Test
    void nonAgentAttributeUpdatesValidateLifecycleAndArguments() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Task pendingTask = new DefaultTask(provider, "TASK", TaskAttributes.builder().build(), null, null);
        Tool pendingTool = new DefaultTool(provider, "TOOL", ToolAttributes.builder().build(), null, null);
        Team pendingTeam = new DefaultTeam(provider, "TEAM", TeamAttributes.builder().build(), null, null);

        assertThatThrownBy(() -> pendingTask.setAttribute("instruction", "value"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pendingTool.setAttribute("instruction", "value"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pendingTeam.setAttribute("process", "value"))
                .isInstanceOf(IllegalStateException.class);

        Task task = DefaultTask.fromMetadata(provider, "TASK", TaskAttributes.builder().build(), null, "ENABLED");
        Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
        Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");
        assertThatThrownBy(() -> task.setAttribute(" ", "value")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.setAttribute(" ", "value")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.setAttribute(" ", "value")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task.setAttribute("value", (Integer) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.setAttribute("value", (Float) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.setAttributes(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void nonAgentAttributeUpdatesWrapSqlFailures() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("database rejected attribute", "72000", 20002);
        CallableStatement taskStatement = stubCall(connection, AgentSql.SET_TASK_ATTRIBUTE);
        CallableStatement toolStatement = stubCall(connection, AgentSql.SET_TOOL_ATTRIBUTE);
        CallableStatement teamStatement = stubCall(connection, AgentSql.SET_TEAM_ATTRIBUTE);
        doThrow(failure).when(taskStatement).execute();
        doThrow(failure).when(toolStatement).execute();
        doThrow(failure).when(teamStatement).execute();

        ConnectionProvider provider = provider(connection);
        Task task = DefaultTask.fromMetadata(provider, "TASK", TaskAttributes.builder().build(), null, "ENABLED");
        Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
        Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");

        assertSqlFailure(() -> task.setAttribute("instruction", "value"), failure);
        assertSqlFailure(() -> tool.setAttribute("instruction", "value"), failure);
        assertSqlFailure(() -> team.setAttribute("process", "value"), failure);
    }

    @ParameterizedTest
    @MethodSource("attributeUpdateCases")
    void attributeRefreshSqlFailureDoesNotRepeatSuccessfulUpdate(AgentObjectType type, boolean bulk,
                                                                 AgentSql updateSql, AgentSql attributesSql)
            throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        CallableStatement update = stubCall(connection, updateSql);
        PreparedStatement attributesQuery = mock(PreparedStatement.class);
        when(connection.prepareStatement(attributesSql.get())).thenReturn(attributesQuery);
        SQLException cause = new SQLException("attribute refresh failed", "08006", 17002);
        when(attributesQuery.executeQuery()).thenThrow(cause);

        assertSqlFailure(attributeUpdate(new DataSourceConnectionProvider(dataSource), type, bulk), cause);

        var order = inOrder(update, attributesQuery);
        order.verify(update).execute();
        order.verify(attributesQuery).executeQuery();
        verify(update).execute();
        verify(update).close();
        verify(attributesQuery).close();
        verify(connection).prepareCall(updateSql.get());
        verify(connection).prepareStatement(attributesSql.get());
        verify(connection).close();
        verify(dataSource).getConnection();
    }

    @ParameterizedTest
    @MethodSource("attributeUpdateCases")
    void attributeRefreshMappingFailureDoesNotRepeatSuccessfulUpdate(AgentObjectType type, boolean bulk,
                                                                     AgentSql updateSql, AgentSql attributesSql)
            throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        CallableStatement update = stubCall(connection, updateSql);
        PreparedStatement attributesQuery = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(attributesSql.get())).thenReturn(attributesQuery);
        when(attributesQuery.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        String attributeName = switch (type) {
            case AGENT, TASK -> "tools";
            case TOOL -> "tool_inputs";
            case TEAM -> "agents";
        };
        when(rows.getString("attribute_name")).thenReturn(attributeName);
        when(rows.getString("attribute_value")).thenReturn("not-json");

        assertThatThrownBy(attributeUpdate(new DataSourceConnectionProvider(dataSource), type, bulk))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Failed to map Select AI")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        var order = inOrder(update, attributesQuery);
        order.verify(update).execute();
        order.verify(attributesQuery).executeQuery();
        verify(update).execute();
        verify(update).close();
        verify(attributesQuery).close();
        verify(rows).close();
        verify(connection).prepareCall(updateSql.get());
        verify(connection).prepareStatement(attributesSql.get());
        verify(connection).close();
        verify(dataSource).getConnection();
    }

    @Test
    void taskLifecycleUsesDatabaseDefaultsForOptionalValues() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement create = stubCall(connection, AgentSql.CREATE_TASK);
        CallableStatement enable = stubCall(connection, AgentSql.ENABLE_TASK);
        CallableStatement disable = stubCall(connection, AgentSql.DISABLE_TASK);
        CallableStatement drop = stubCall(connection, AgentSql.DROP_TASK);
        Task task = new DefaultTask(provider(connection), "TASK",
                TaskAttributes.builder().instruction("Answer {query}").build(), null, null);

        assertThatThrownBy(task::disable).isInstanceOf(IllegalStateException.class);
        assertThat(task.getTaskName()).isEqualTo("TASK");
        assertThat(task.create()).isTrue();
        assertThat(task.enable()).isTrue();
        assertThat(task.disable()).isTrue();
        assertThat(task.drop(false)).isTrue();
        assertThat(task.drop(true)).isTrue();
        assertThatThrownBy(() -> task.drop(false)).isInstanceOf(IllegalStateException.class);

        assertThat(clobInput(create, 2)).contains("\"instruction\":\"Answer {query}\"");
        verify(create).setNull(3, Types.VARCHAR);
        verify(create).setNull(4, Types.CLOB);
        verify(enable).setString(1, "TASK");
        verify(disable).setString(1, "TASK");
        verify(drop).setObject(2, false, OracleTypes.PLSQL_BOOLEAN);
    }

    @Test
    void toolLifecycleAndFunctionsBindInputsAndReadClobs() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement create = stubCall(connection, AgentSql.CREATE_TOOL);
        CallableStatement enable = stubCall(connection, AgentSql.ENABLE_TOOL);
        CallableStatement disable = stubCall(connection, AgentSql.DISABLE_TOOL);
        CallableStatement drop = stubCall(connection, AgentSql.DROP_TOOL);
        CallableStatement describe = stubCall(connection, AgentSql.DESCRIBE_TOOL);
        CallableStatement run = stubCall(connection, AgentSql.RUN_TOOL);
        Clob description = clob("{\"name\":\"TOOL\"}");
        Clob result = clob("{\"result\":1}");
        when(describe.getClob(1)).thenReturn(description);
        when(run.getClob(1)).thenReturn(result);
        Tool tool = new DefaultTool(provider(connection), "TOOL",
                ToolAttributes.builder().toolType(ToolType.SQL)
                        .toolParams(SqlToolParams.builder().profileName("SALES").build())
                        .toolInputs(List.of(ToolInput.builder().name("query").description("Query to run").build()))
                        .build(), null, null);

        assertThatThrownBy(tool::describe).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tool.run("{}"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tool.create()).isTrue();
        assertThat(tool.enable()).isTrue();
        assertThat(tool.disable()).isTrue();
        assertThatThrownBy(() -> tool.run(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tool.describe()).isEqualTo("{\"name\":\"TOOL\"}");
        assertThat(tool.run("{\"sku\":1}").getRawValue()).isEqualTo("{\"result\":1}");
        assertThat(tool.drop(true)).isTrue();
        assertThat(tool.drop(true)).isTrue();
        assertThatThrownBy(tool::describe).isInstanceOf(IllegalStateException.class);

        assertThat(clobInput(create, 2))
                .contains("\"tool_type\":\"SQL\"", "\"tool_params\":{\"profile_name\":\"SALES\"}",
                        "\"tool_inputs\":[{\"name\":\"query\",\"description\":\"Query to run\"}]")
                .doesNotContain("tool_type_value", "values", "additional_parameters");
        verify(create).setNull(3, Types.VARCHAR);
        verify(create).setNull(4, Types.CLOB);
        verify(enable).setString(1, "TOOL");
        verify(disable).setString(1, "TOOL");
        verify(drop, times(2)).setObject(2, true, OracleTypes.PLSQL_BOOLEAN);
        verify(describe).registerOutParameter(1, Types.CLOB);
        verify(describe).setString(2, "TOOL");
        verify(run).registerOutParameter(1, Types.CLOB);
        verify(run).setString(2, "TOOL");
        assertThat(clobInput(run, 3)).isEqualTo("{\"sku\":1}");
        verify(run, times(1)).execute();
    }

    @Test
    void teamLifecycleAndReadOperationsBindInputsAndReadResults() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement create = stubCall(connection, AgentSql.CREATE_TEAM);
        CallableStatement enable = stubCall(connection, AgentSql.ENABLE_TEAM);
        CallableStatement disable = stubCall(connection, AgentSql.DISABLE_TEAM);
        CallableStatement drop = stubCall(connection, AgentSql.DROP_TEAM);
        CallableStatement run = stubCall(connection, AgentSql.RUN_TEAM);
        CallableStatement describe = stubCall(connection, AgentSql.DESCRIBE_TEAM);
        CallableStatement state = stubCall(connection, AgentSql.GET_TEAM_STATE);
        Clob answer = clob("Answer");
        Clob teamDescription = clob("{\"team\":true}");
        when(run.getClob(1)).thenReturn(answer);
        when(describe.getClob(1)).thenReturn(teamDescription);
        when(state.getString(1)).thenReturn("WAITING_FOR_HUMAN");
        Team team = new DefaultTeam(provider(connection), "TEAM",
                TeamAttributes.builder().process("sequential").build(), null, null);

        assertThatThrownBy(() -> team.run(RunTeamRequest.builder().userPrompt("Prompt").build()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(team.create()).isTrue();
        assertThat(team.enable()).isTrue();
        assertThat(team.disable()).isTrue();
        assertThatThrownBy(() -> team.run(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(team.run(RunTeamRequest.builder().userPrompt("Prompt").build()).getRawValue())
                .isEqualTo("Answer");
        assertThat(team.describe().getSkills()).isNull();
        assertThat(team.getStateByConversationId("C1"))
                .isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);
        assertThat(team.drop(false)).isTrue();
        assertThat(team.drop(true)).isTrue();
        assertThatThrownBy(team::describe).isInstanceOf(IllegalStateException.class);

        assertThat(clobInput(create, 2)).contains("\"process\":\"sequential\"");
        verify(create).setNull(3, Types.VARCHAR);
        verify(create).setNull(4, Types.CLOB);
        verify(enable).setString(1, "TEAM");
        verify(disable).setString(1, "TEAM");
        verify(drop).setObject(2, false, OracleTypes.PLSQL_BOOLEAN);
        verify(run).registerOutParameter(1, Types.CLOB);
        verify(run).setString(2, "TEAM");
        assertThat(clobInput(run, 3)).isEqualTo("Prompt");
        verify(run).setNull(4, Types.CLOB);
        verify(describe).registerOutParameter(1, Types.CLOB);
        verify(state).registerOutParameter(1, Types.VARCHAR);
        verify(state).setString(2, "TEAM");
        verify(state).setCharacterStream(eq(3), any(Reader.class), anyInt());
        assertThat(clobInputs(state, 3, 1))
                .containsOnly("{\"conversation_id\":\"C1\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "[]", "null", "{} {}", ""})
    void teamDescriptionMappingFailuresAreCheckedAndReleaseJdbcResources(String response) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        CallableStatement statement = stubCall(connection, AgentSql.DESCRIBE_TEAM);
        Clob result = clob(response);
        when(statement.getClob(1)).thenReturn(result);
        Team team = DefaultTeam.fromMetadata(new DataSourceConnectionProvider(dataSource), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertThatThrownBy(team::describe)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Failed to map Select AI team description: TEAM")
                .hasCauseInstanceOf(IllegalArgumentException.class);
        verify(result).free();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void teamDescriptionSupportsNullDatabaseResult() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = stubCall(connection, AgentSql.DESCRIBE_TEAM);
        Team team = DefaultTeam.fromMetadata(provider(connection), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertThat(team.describe()).isNull();
        verify(statement).close();
    }

    @Test
    void teamDescriptionCleanupFailurePreservesJdbcMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = stubCall(connection, AgentSql.DESCRIBE_TEAM);
        Clob result = clob("{}");
        when(statement.getClob(1)).thenReturn(result);
        SQLException failure = new SQLException("Cannot free LOB", "72000", 22275);
        doThrow(failure).when(result).free();
        Team team = DefaultTeam.fromMetadata(provider(connection), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertSqlFailure(team::describe, failure);
        verify(result).free();
        verify(statement).close();
    }

    @Test
    void teamStateByConversationIdValidatesInput() throws Exception {
        Connection connection = mock(Connection.class);
        Team team = DefaultTeam.fromMetadata(provider(connection), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertThatThrownBy(() -> team.getStateByConversationId(" "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(connection);
    }

    @Test
    void teamAccessOperationsUseOwnerScopedTeamHandle() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement grant = stubCall(connection, AgentSql.GRANT_TEAM_ACCESS);
        CallableStatement revoke = stubCall(connection, AgentSql.REVOKE_TEAM_ACCESS);
        Team team = DefaultTeam.fromMetadata(provider(connection), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertThat(team.grantAccess("APP_USER")).isTrue();
        assertThat(team.revokeAccess("APP_USER")).isTrue();

        verify(grant).setString(1, "TEAM");
        verify(grant).setString(2, "APP_USER");
        verify(grant).execute();
        verify(revoke).setString(1, "TEAM");
        verify(revoke).setString(2, "APP_USER");
        verify(revoke).execute();
    }

    @Test
    void teamAccessOperationsValidateLifecycleAndIdentifiers() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Team pending = new DefaultTeam(provider, "TEAM", TeamAttributes.builder().build(), null, null);

        assertThatThrownBy(() -> pending.grantAccess("APP_USER"))
                .isInstanceOf(IllegalStateException.class);

        Team team = DefaultTeam.fromMetadata(provider, "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");
        assertThatThrownBy(() -> team.grantAccess("OWNER.TEAM"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.revokeAccess("PUBLIC"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void teamAccessHandleFailuresPreserveJdbcMetadataAndReleaseResources(boolean grant) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        AgentSql operation = grant ? AgentSql.GRANT_TEAM_ACCESS : AgentSql.REVOKE_TEAM_ACCESS;
        CallableStatement statement = stubCall(connection, operation);
        SQLException cause = new SQLException("team sharing rejected", "42000", 1031);
        when(statement.execute()).thenThrow(cause);
        Team team = DefaultTeam.fromMetadata(new DataSourceConnectionProvider(dataSource), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertSqlFailure(() -> {
            if (grant) {
                team.grantAccess("APP_USER");
            } else {
                team.revokeAccess("APP_USER");
            }
        }, cause);

        verify(statement).setString(1, "TEAM");
        verify(statement).setString(2, "APP_USER");
        verify(statement).execute();
        verify(statement).close();
        verify(connection).prepareCall(operation.get());
        verify(connection).close();
        verify(dataSource).getConnection();
    }

    @Test
    void teamRunReusesConversationIdAcrossPooledConnections() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection firstConnection = mock(Connection.class);
        Connection secondConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        CallableStatement firstRun = stubCall(firstConnection, AgentSql.RUN_TEAM);
        CallableStatement secondRun = stubCall(secondConnection, AgentSql.RUN_TEAM);
        Clob waiting = clob("WAITING_FOR_HUMAN");
        Clob completed = clob("Done");
        when(firstRun.getClob(1)).thenReturn(waiting);
        when(secondRun.getClob(1)).thenReturn(completed);
        Team team = DefaultTeam.fromMetadata(new DataSourceConnectionProvider(dataSource), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertThat(team.run(RunTeamRequest.builder()
                .userPrompt("I need help")
                .conversationId("C1")
                .build()).getRawValue()).isEqualTo("WAITING_FOR_HUMAN");
        assertThat(team.run(RunTeamRequest.builder()
                .userPrompt("The order number is 1234")
                .conversationId("C1")
                .build()).getRawValue()).isEqualTo("Done");

        assertThat(clobInput(firstRun, 3)).isEqualTo("I need help");
        assertThat(clobInput(secondRun, 3)).isEqualTo("The order number is 1234");
        assertThat(clobInput(firstRun, 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
        assertThat(clobInput(secondRun, 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
        verify(firstConnection).close();
        verify(secondConnection).close();
    }

    @Test
    void teamRunAllowsAnOmittedPrompt() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement run = stubCall(connection, AgentSql.RUN_TEAM);
        Clob completed = clob("Done");
        when(run.getClob(1)).thenReturn(completed);
        Team team = DefaultTeam.fromMetadata(provider(connection), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        assertThat(team.run(RunTeamRequest.builder().conversationId("C1").build()).getRawValue())
                .isEqualTo("Done");

        verify(run).setNull(3, Types.CLOB);
        assertThat(clobInput(run, 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
    }

    @Test
    void teamStateAndExportOperationsBindOptionalParameters() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement create = stubCall(connection, AgentSql.CREATE_TEAM);
        CallableStatement setState = stubCall(connection, AgentSql.SET_EXECUTION_STATE);
        CallableStatement exportFunction = stubCall(connection, AgentSql.EXPORT_TEAM_FUNCTION);
        CallableStatement exportProcedure = stubCall(connection, AgentSql.EXPORT_TEAM_PROCEDURE);
        CallableStatement getState = stubCall(connection, AgentSql.GET_TEAM_STATE);
        Clob definition = clob("{\"definition\":true}");
        Clob secondDefinition = clob("{\"definition\":true}");
        when(exportFunction.getClob(1)).thenReturn(definition, secondDefinition);
        Team team = new DefaultTeam(provider(connection), "TEAM", TeamAttributes.builder().build(), null, null);

        assertThatThrownBy(() -> team.setExecutionState(null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(team.create()).isTrue();
        assertThatThrownBy(() -> team.setExecutionState(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.exportDefinition((ExportTeamRequest) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(team.setExecutionState(ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED)
                .conversationId("C1").build())).isTrue();
        assertThat(team.exportDefinition())
                .isEqualTo("{\"definition\":true}");
        assertThat(team.exportDefinition(ExportTeamParams.builder().overwrite(true).build()))
                .isEqualTo("{\"definition\":true}");
        assertThat(team.exportDefinition(ExportTeamRequest.builder("DIR:team.json")
                .overwrite(true).build())).isTrue();
        assertThatThrownBy(() -> team.getStateByConversationId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.setExecutionState(ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");

        verify(create).execute();
        verify(setState).setString(1, "TEAM");
        assertThat(clobInputs(setState, 2, 1)).containsExactly("SUCCEEDED");
        assertThat(clobInputs(setState, 3, 1)).containsExactly(
                "{\"conversation_id\":\"C1\"}");
        verify(exportFunction, times(2)).registerOutParameter(1, Types.CLOB);
        verify(exportFunction, times(2)).setString(2, "TEAM");
        verify(exportFunction).setNull(3, Types.CLOB);
        assertThat(clobInput(exportFunction, 3)).isEqualTo("{\"overwrite\":true}");
        verify(exportProcedure).setString(1, "TEAM");
        verify(exportProcedure).setNull(2, Types.VARCHAR);
        verify(exportProcedure).setString(3, "DIR:team.json");
        assertThat(clobInput(exportProcedure, 4)).isEqualTo("{\"overwrite\":true}");
    }

    @ParameterizedTest
    @MethodSource("exportResponses")
    void teamExportPreservesRawResponseAndReleasesResources(String response, boolean withParams)
            throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        CallableStatement statement = stubCall(connection, AgentSql.EXPORT_TEAM_FUNCTION);
        Clob result = response == null ? null : clob(response);
        when(statement.getClob(1)).thenReturn(result);
        Team team = DefaultTeam.fromMetadata(new DataSourceConnectionProvider(dataSource), "TEAM",
                TeamAttributes.builder().build(), null, "ENABLED");

        String exported = withParams
                ? team.exportDefinition(ExportTeamParams.builder().overwrite(false).build())
                : team.exportDefinition();

        assertThat(exported).isEqualTo(response);
        verify(statement).registerOutParameter(1, Types.CLOB);
        verify(statement).setString(2, "TEAM");
        if (withParams) {
            assertThat(clobInput(statement, 3)).isEqualTo("{\"overwrite\":false}");
        } else {
            verify(statement).setNull(3, Types.CLOB);
        }
        verify(statement).execute();
        verify(statement).close();
        verify(connection).close();
        verify(dataSource).getConnection();
    }

    @Test
    void createFailuresPreserveJdbcMetadataAndDoNotMarkResourcesCreated() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("database unavailable", "08006", 17002);
        for (AgentSql sql : List.of(AgentSql.CREATE_AGENT, AgentSql.CREATE_TASK,
                AgentSql.CREATE_TOOL, AgentSql.CREATE_TEAM)) {
            CallableStatement statement = stubCall(connection, sql);
            when(statement.execute()).thenThrow(failure);
        }
        ConnectionProvider provider = provider(connection);
        Agent agent = new DefaultAgent(provider, "AGENT", agentAttributes(), null, null);
        Task task = new DefaultTask(provider, "TASK", TaskAttributes.builder().build(), null, null);
        Tool tool = new DefaultTool(provider, "TOOL", ToolAttributes.builder().build(), null, null);
        Team team = new DefaultTeam(provider, "TEAM", TeamAttributes.builder().build(), null, null);

        assertSqlFailure(agent::create, failure);
        assertSqlFailure(task::create, failure);
        assertSqlFailure(tool::create, failure);
        assertSqlFailure(team::create, failure);
        assertThatThrownBy(agent::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(task::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(tool::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(team::enable).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void agentCreationDefersRequiredAttributeValidationToDatabase() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = stubCall(connection, AgentSql.CREATE_AGENT);
        SQLException failure = new SQLException("missing profile_name", "72000", 20400);
        when(statement.execute()).thenThrow(failure);
        Agent agent = new DefaultAgent(provider(connection), "AGENT", AgentAttributes.builder().build(), null, null);

        assertSqlFailure(agent::create, failure);

        assertThat(clobInput(statement, 2)).isEqualTo("{}");
        verify(statement).execute();
    }

    @Test
    void existingResourcesCannotBeCreatedAgain() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Agent agent = DefaultAgent.fromMetadata(provider, "AGENT", agentAttributes(), null, "ENABLED");
        Task task = DefaultTask.fromMetadata(provider, "TASK", TaskAttributes.builder().build(), null, "ENABLED");
        Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
        Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");

        assertThatThrownBy(agent::create).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(task::create).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(tool::create).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(team::create).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void toolAndTeamFunctionFailuresPreserveJdbcMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement runTool = stubCall(connection, AgentSql.RUN_TOOL);
        CallableStatement runTeam = stubCall(connection, AgentSql.RUN_TEAM);
        SQLException failure = new SQLException("operation failed", "72000", 20400);
        when(runTool.execute()).thenThrow(failure);
        when(runTeam.execute()).thenThrow(failure);
        ConnectionProvider provider = provider(connection);
        Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
        Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");

        assertSqlFailure(() -> tool.run("{}"), failure);
        assertSqlFailure(() -> team.run(RunTeamRequest.builder().userPrompt("Prompt").build()), failure);
    }

    @Test
    void lifecycleFailuresPreserveJdbcMetadataForEachResource() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("lifecycle failed", "72000", 20401);
        for (AgentSql sql : List.of(AgentSql.ENABLE_AGENT, AgentSql.DISABLE_TASK,
                AgentSql.DROP_TOOL, AgentSql.ENABLE_TEAM)) {
            CallableStatement statement = stubCall(connection, sql);
            when(statement.execute()).thenThrow(failure);
        }
        ConnectionProvider provider = provider(connection);
        Agent agent = DefaultAgent.fromMetadata(provider, "AGENT", agentAttributes(), null, "ENABLED");
        Task task = DefaultTask.fromMetadata(provider, "TASK", TaskAttributes.builder().build(), null, "ENABLED");
        Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
        Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");

        assertSqlFailure(agent::enable, failure);
        assertSqlFailure(task::disable, failure);
        assertSqlFailure(() -> tool.drop(true), failure);
        assertSqlFailure(team::enable, failure);
        assertSqlFailure(() -> tool.drop(true), failure);
        verify(connection, times(2)).prepareCall(AgentSql.DROP_TOOL.get());
    }

    @Test
    void toolAndTeamAuxiliaryFunctionFailuresPreserveJdbcMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("operation failed", "72000", 20402);
        for (AgentSql sql : List.of(AgentSql.DESCRIBE_TOOL, AgentSql.DESCRIBE_TEAM,
                AgentSql.GET_TEAM_STATE, AgentSql.SET_EXECUTION_STATE,
                AgentSql.EXPORT_TEAM_FUNCTION, AgentSql.EXPORT_TEAM_PROCEDURE)) {
            CallableStatement statement = stubCall(connection, sql);
            when(statement.execute()).thenThrow(failure);
        }
        ConnectionProvider provider = provider(connection);
        Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
        Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");

        assertSqlFailure(tool::describe, failure);
        assertSqlFailure(team::describe, failure);
        assertSqlFailure(() -> team.getStateByConversationId("C1"), failure);
        assertSqlFailure(() -> team.setExecutionState(ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED)
                .conversationId("C1").build()), failure);
        assertSqlFailure(() -> team.exportDefinition(), failure);
        assertSqlFailure(() -> team.exportDefinition(ExportTeamRequest.builder("DIR:team.json").build()), failure);
    }

    @Test
    void dataSourceFactoryIsLazyAndDoesNotOwnCallerDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        try (SelectAIAgent client = SelectAIAgent.create(dataSource)) {
            Agent agent = client.agent("AGENT", agentAttributes(), null, null);
            assertThat(agent.getAgentName()).isEqualTo("AGENT");
        }
        verifyNoInteractions(dataSource);
        assertThatThrownBy(() -> SelectAIAgent.create((DataSource) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> attributeUpdateCases() {
        return Stream.of(
                Arguments.of(AgentObjectType.AGENT, false, AgentSql.SET_AGENT_ATTRIBUTE, AgentSql.GET_AGENT_ATTRIBUTES),
                Arguments.of(AgentObjectType.AGENT, true, AgentSql.SET_AGENT_ATTRIBUTES, AgentSql.GET_AGENT_ATTRIBUTES),
                Arguments.of(AgentObjectType.TASK, false, AgentSql.SET_TASK_ATTRIBUTE, AgentSql.GET_TASK_ATTRIBUTES),
                Arguments.of(AgentObjectType.TASK, true, AgentSql.SET_TASK_ATTRIBUTES, AgentSql.GET_TASK_ATTRIBUTES),
                Arguments.of(AgentObjectType.TOOL, false, AgentSql.SET_TOOL_ATTRIBUTE, AgentSql.GET_TOOL_ATTRIBUTES),
                Arguments.of(AgentObjectType.TOOL, true, AgentSql.SET_TOOL_ATTRIBUTES, AgentSql.GET_TOOL_ATTRIBUTES),
                Arguments.of(AgentObjectType.TEAM, false, AgentSql.SET_TEAM_ATTRIBUTE, AgentSql.GET_TEAM_ATTRIBUTES),
                Arguments.of(AgentObjectType.TEAM, true, AgentSql.SET_TEAM_ATTRIBUTES, AgentSql.GET_TEAM_ATTRIBUTES));
    }

    private static ThrowingCallable attributeUpdate(ConnectionProvider provider, AgentObjectType type, boolean bulk)
            throws SelectAIException {
        return switch (type) {
            case AGENT -> {
                Agent agent = DefaultAgent.fromMetadata(provider, "AGENT", agentAttributes(), null, "ENABLED");
                yield bulk ? () -> agent.setAttributes(AgentAttributes.builder().role("Updated").build())
                        : () -> agent.setAttribute("role", "Updated");
            }
            case TASK -> {
                Task task = DefaultTask.fromMetadata(provider, "TASK", TaskAttributes.builder().build(), null, "ENABLED");
                yield bulk ? () -> task.setAttributes(TaskAttributes.builder().instruction("Updated").build())
                        : () -> task.setAttribute("instruction", "Updated");
            }
            case TOOL -> {
                Tool tool = DefaultTool.fromMetadata(provider, "TOOL", ToolAttributes.builder().build(), null, "ENABLED");
                yield bulk ? () -> tool.setAttributes(ToolAttributes.builder().instruction("Updated").build())
                        : () -> tool.setAttribute("instruction", "Updated");
            }
            case TEAM -> {
                Team team = DefaultTeam.fromMetadata(provider, "TEAM", TeamAttributes.builder().build(), null, "ENABLED");
                yield bulk ? () -> team.setAttributes(TeamAttributes.builder().process("sequential").build())
                        : () -> team.setAttribute("process", "sequential");
            }
        };
    }

    private static Stream<Arguments> exportResponses() {
        return Stream.of(null, "", "[]", "[\n {\"name\":\"TEAM\"}, {\"name\":\"SECOND\"}\n]",
                        "{ \"name\": \"TEAM\", \"agents\": [] }")
                .flatMap(response -> Stream.of(Arguments.of(response, false), Arguments.of(response, true)));
    }

    private static AgentAttributes agentAttributes() {
        return AgentAttributes.builder().profileName("PROFILE").role("Assist").build();
    }

    private static ResultSet agentAttributesResultSet() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("attribute_name")).thenReturn("profile_name", "role");
        when(resultSet.getString("attribute_value")).thenReturn("PROFILE", "Assist");
        return resultSet;
    }

    private static ResultSet taskAttributesResultSet() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("attribute_name")).thenReturn("instruction");
        when(resultSet.getString("attribute_value")).thenReturn("Answer {query}");
        return resultSet;
    }

    private static ResultSet toolAttributesResultSet() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("attribute_name")).thenReturn("tool_type", "tool_params");
        when(resultSet.getString("attribute_value"))
                .thenReturn("SQL", "{\"profile_name\":\"UPDATED_PROFILE\"}");
        return resultSet;
    }

    private static ResultSet teamAttributesResultSet() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("attribute_name")).thenReturn("process");
        when(resultSet.getString("attribute_value")).thenReturn("sequential");
        return resultSet;
    }

    private static CallableStatement stubCall(Connection connection, AgentSql sql) throws SQLException {
        CallableStatement statement = mock(CallableStatement.class);
        when(connection.prepareCall(sql.get())).thenReturn(statement);
        return statement;
    }

    private static Clob clob(String value) throws SQLException {
        Clob clob = mock(Clob.class);
        when(clob.getCharacterStream()).thenReturn(new StringReader(value));
        return clob;
    }

    private static String clobInput(CallableStatement statement, int index) throws Exception {
        ArgumentCaptor<Reader> reader = ArgumentCaptor.forClass(Reader.class);
        verify(statement).setCharacterStream(eq(index), reader.capture(), anyInt());
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[256];
        int count;
        while ((count = reader.getValue().read(buffer)) != -1) {
            value.append(buffer, 0, count);
        }
        return value.toString();
    }

    private static List<String> clobInputs(CallableStatement statement, int index, int count) throws Exception {
        ArgumentCaptor<Reader> readers = ArgumentCaptor.forClass(Reader.class);
        verify(statement, times(count)).setCharacterStream(eq(index), readers.capture(), anyInt());
        List<String> values = new ArrayList<>();
        for (Reader reader : readers.getAllValues()) {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[256];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                value.append(buffer, 0, read);
            }
            values.add(value.toString());
        }
        return values;
    }

    private static void assertSqlFailure(ThrowingCallable operation, SQLException cause) {
        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(cause.getErrorCode());
                    assertThat(error.getSqlState()).isEqualTo(cause.getSQLState());
                });
    }

    private static ConnectionProvider provider(Connection connection) {
        return new ConnectionProvider() {
            @Override
            public <T> T withConnection(ConnectionCallback<T> callback) throws SQLException, SelectAIException {
                return callback.execute(connection);
            }
        };
    }
}
