/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionCallback;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AvailableTeam;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ToolType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.Reader;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentJdbcTest {
    @Test
    void mapsAgentAttributesAndIgnoresUnknownDatabaseAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = stubAttributes(connection, AgentSql.GET_AGENT_ATTRIBUTES,
                Map.entry("PROFILE_NAME", "GENAI"), Map.entry("ROLE", "Assist"),
                Map.entry("TOOLS", "[\"SQL\",\"RAG\"]"),
                Map.entry("ENABLE_HUMAN_TOOL", "TRUE"),
                Map.entry("SHORT_TERM_MEMORY_LENGTH", "12"),
                Map.entry("SUPERVISOR", "false"), Map.entry("FUTURE_ATTRIBUTE", "ignored"));

        AgentAttributes attributes = AgentJdbc.fetchAgentAttributes(connection, "AGENT");

        assertThat(attributes.getProfileName()).isEqualTo("GENAI");
        assertThat(attributes.getRole()).isEqualTo("Assist");
        assertThat(attributes.getTools()).containsExactly("SQL", "RAG");
        assertThat(attributes.getEnableHumanTool()).isTrue();
        assertThat(attributes.getShortTermMemoryLength()).isEqualTo(12);
        assertThat(attributes.getSupervisor()).isFalse();
        verify(statement).setString(1, "AGENT");
    }

    @Test
    void mapsTaskAttributesFromDatabaseRows() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES,
                Map.entry("instruction", "Handle {query}"),
                Map.entry("tools", "[\"SQL\"]"),
                Map.entry("input", "[\"UPSTREAM\"]"),
                Map.entry("enable_human_tool", "false"));

        TaskAttributes attributes = AgentJdbc.fetchTaskAttributes(connection, "TASK");

        assertThat(attributes.getInstruction()).isEqualTo("Handle {query}");
        assertThat(attributes.getTools()).containsExactly("SQL");
        assertThat(attributes.getInput()).containsExactly("UPSTREAM");
        assertThat(attributes.getEnableHumanTool()).isFalse();
    }

    @Test
    void mapsToolAttributesIncludingJsonObjectsAndArrays() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES,
                Map.entry("instruction", "Query data"),
                Map.entry("function", "APP.TOOL_FN"),
                Map.entry("tool_type", " sql "),
                Map.entry("tool_params", "{\"profile_name\":\"GENAI\"}"),
                Map.entry("tool_inputs", "[{\"name\":\"query\"}]"));

        ToolAttributes attributes = AgentJdbc.fetchToolAttributes(connection, "TOOL");

        assertThat(attributes.getInstruction()).isEqualTo("Query data");
        assertThat(attributes.getFunction()).isEqualTo("APP.TOOL_FN");
        assertThat(attributes.getToolType()).isEqualTo(ToolType.SQL);
        assertThat(attributes.getToolParams()).isInstanceOf(SqlToolParams.class);
        assertThat(((SqlToolParams) attributes.getToolParams()).getProfileName()).isEqualTo("GENAI");
        assertThat(attributes.getToolInputs()).singleElement().satisfies(input -> {
            assertThat(input.getName()).isEqualTo("query");
            assertThat(input.getDescription()).isNull();
        });
        assertThat(attributes.toJson()).contains("\"tool_inputs\":[{\"name\":\"query\"}]");
    }

    @Test
    void hydratesToolInputNamesAndDescriptionsInOrder() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES, Map.entry("tool_inputs",
                "[{\"name\":\"data_guard\",\"description\":\"Use \\\"Enabled\\\" or \\\"Disabled\\\"\"},"
                        + "{\"name\":\"query\",\"description\":null},{\"name\":null}]"));

        ToolAttributes attributes = AgentJdbc.fetchToolAttributes(connection, "TOOL");

        assertThat(attributes.getToolInputs()).hasSize(3);
        assertThat(attributes.getToolInputs().get(0).getName()).isEqualTo("data_guard");
        assertThat(attributes.getToolInputs().get(0).getDescription()).isEqualTo("Use \"Enabled\" or \"Disabled\"");
        assertThat(attributes.getToolInputs().get(1).getName()).isEqualTo("query");
        assertThat(attributes.getToolInputs().get(1).getDescription()).isNull();
        assertThat(attributes.getToolInputs().get(2).getName()).isNull();
        assertThatThrownBy(attributes.getToolInputs()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"null"})
    void absentToolInputsRemainUnset(String json) throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES,
                new java.util.AbstractMap.SimpleEntry<>("tool_inputs", json));

        assertThat(AgentJdbc.fetchToolAttributes(connection, "TOOL").getToolInputs()).isNull();
    }

    @Test
    void emptyToolInputArrayIsPreserved() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES, Map.entry("tool_inputs", "[]"));

        ToolAttributes attributes = AgentJdbc.fetchToolAttributes(connection, "TOOL");

        assertThat(attributes.getToolInputs()).isEmpty();
        assertThat(attributes.toJson()).isEqualTo("{\"tool_inputs\":[]}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "[broken", "{}", "42", "[null]", "[\"name\"]",
            "[{\"name\":42}]", "[{\"description\":[]}]"})
    void malformedToolInputsReportMappingFailure(String json) throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES, Map.entry("tool_inputs", json));

        assertThatThrownBy(() -> AgentJdbc.fetchToolAttributes(connection, "TOOL"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Failed to map Select AI tool attributes")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toolParameterHydrationPreservesUnknownFieldsAndNumericPrecision() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TOOL_ATTRIBUTES,
                Map.entry("tool_type", "SQL"),
                Map.entry("tool_params", "{\"profile_name\":\"SALES\","
                        + "\"future_options\":{\"precision\":1.234567890123456789},\"optional\":null}"));

        ToolAttributes attributes = AgentJdbc.fetchToolAttributes(connection, "TOOL");

        assertThat(attributes.getToolParams()).isInstanceOf(SqlToolParams.class);
        assertThat(attributes.getToolParams().toMap()).containsEntry("optional", null);
        assertThat(attributes.toJson()).contains("1.234567890123456789", "\"optional\":null");
    }

    @Test
    void mapsTeamMembersAndOptionalTeamAttributes() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TEAM_ATTRIBUTES,
                Map.entry("agents", "[{\"name\":\"AGENT\",\"task\":\"TASK\"}]"),
                Map.entry("process", "sequential"),
                Map.entry("long_term_memory_length", "20"),
                Map.entry("supervisor_agent", "SUPERVISOR"));

        TeamAttributes attributes = AgentJdbc.fetchTeamAttributes(connection, "TEAM");

        assertThat(attributes.getAgents()).hasSize(1);
        assertThat(attributes.getAgents().get(0).getName()).isEqualTo("AGENT");
        assertThat(attributes.getAgents().get(0).getTask()).isEqualTo("TASK");
        assertThat(attributes.getProcess()).isEqualTo("sequential");
        assertThat(attributes.getLongTermMemoryLength()).isEqualTo(20);
        assertThat(attributes.getSupervisorAgent()).isEqualTo("SUPERVISOR");
    }

    @Test
    void mapsAvailableTeamsFromPackageDiscoveryJson() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        Clob clob = mock(Clob.class);
        when(connection.prepareCall(AgentSql.LIST_AVAILABLE_TEAMS.get())).thenReturn(statement);
        when(statement.getClob(1)).thenReturn(clob);
        when(clob.getCharacterStream()).thenReturn(new java.io.StringReader(
                "[{\"name\":\"OWNER.TEAM\",\"description\":\"Shared\"},"
                        + "{\"team_name\":\"LOCAL_TEAM\",\"description\":\"Owned\"}]"));

        List<AvailableTeam> teams = AgentJdbc.fetchAvailableTeams(connection);

        assertThat(teams).hasSize(2);
        assertThat(teams.get(0).getTeamName()).isEqualTo("OWNER.TEAM");
        assertThat(teams.get(0).getDescription()).isEqualTo("Shared");
        assertThat(teams.get(0).getRawJson()).contains("OWNER.TEAM");
        assertThat(teams.get(1).getTeamName()).isEqualTo("LOCAL_TEAM");
        verify(statement).registerOutParameter(1, Types.CLOB);
        verify(statement).execute();
    }

    @Test
    void rejectsMalformedAvailableTeamDiscoveryJson() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        Clob clob = mock(Clob.class);
        when(connection.prepareCall(AgentSql.LIST_AVAILABLE_TEAMS.get())).thenReturn(statement);
        when(statement.getClob(1)).thenReturn(clob);
        when(clob.getCharacterStream()).thenReturn(new java.io.StringReader("{\"name\":\"TEAM\"}"));

        assertThatThrownBy(() -> AgentJdbc.fetchAvailableTeams(connection))
                .isInstanceOfSatisfying(SelectAIException.class,
                        error -> assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void mapsPartialAgentMetadataWithoutRequiredFieldValidation() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_AGENT_ATTRIBUTES, Map.entry("role", "Assist"));

        AgentAttributes attributes = AgentJdbc.fetchAgentAttributes(connection, "AGENT");

        assertThat(attributes.getProfileName()).isNull();
        assertThat(attributes.getRole()).isEqualTo("Assist");
    }

    @Test
    void emptyAttributeRowsAreAllowedWhenResourceStillExists() throws Exception {
        for (AgentObjectType type : AgentObjectType.values()) {
            Connection connection = mock(Connection.class);
            stubAttributes(connection, attributeQuery(type));
            PreparedStatement resource = stubResourceLookup(connection, resourceQuery(type), true);

            assertThat(fetchAttributes(connection, type)).isNotNull();

            verify(resource).setString(1, type.name());
        }
    }

    @Test
    void emptyAttributeRowsReportMissingResource() throws Exception {
        for (AgentObjectType type : AgentObjectType.values()) {
            Connection connection = mock(Connection.class);
            stubAttributes(connection, attributeQuery(type));
            PreparedStatement resource = stubResourceLookup(connection, resourceQuery(type), false);

            assertThatThrownBy(() -> fetchAttributes(connection, type))
                    .isInstanceOf(SelectAIException.class)
                    .hasMessage("Select AI " + type.name().toLowerCase(java.util.Locale.ROOT)
                            + " not found: " + type.name());

            verify(resource).setString(1, type.name());
        }
    }

    @Test
    void attributeExistenceProbePreservesJdbcCauseCodeAndState() throws Exception {
        Connection connection = mock(Connection.class);
        stubAttributes(connection, AgentSql.GET_TASK_ATTRIBUTES);
        SQLException cause = new SQLException("view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TASK.get())).thenThrow(cause);

        assertThatThrownBy(() -> AgentJdbc.fetchTaskAttributes(provider(connection), "TASK"))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(942);
                    assertThat(error.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void malformedJsonFailsButUnknownToolTypesArePreserved() throws Exception {
        Connection taskConnection = mock(Connection.class);
        stubAttributes(taskConnection, AgentSql.GET_TASK_ATTRIBUTES, Map.entry("tools", "[broken"));
        Connection toolConnection = mock(Connection.class);
        stubAttributes(toolConnection, AgentSql.GET_TOOL_ATTRIBUTES, Map.entry("tool_type", "UNKNOWN"));
        Connection teamConnection = mock(Connection.class);
        stubAttributes(teamConnection, AgentSql.GET_TEAM_ATTRIBUTES, Map.entry("agents", "{}"));

        assertThatThrownBy(() -> AgentJdbc.fetchTaskAttributes(taskConnection, "TASK"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        error -> assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class));
        ToolAttributes toolAttributes = AgentJdbc.fetchToolAttributes(toolConnection, "TOOL");
        assertThat(toolAttributes.getToolTypeValue()).isEqualTo("UNKNOWN");
        assertThat(toolAttributes.getToolType().isKnown()).isFalse();
        assertThat(toolAttributes.toJson()).contains("\"tool_type\":\"UNKNOWN\"");
        assertThatThrownBy(() -> AgentJdbc.fetchTeamAttributes(teamConnection, "TEAM"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        error -> assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void providerAttributeLookupPreservesJdbcCauseCodeAndState() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException cause = new SQLException("view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TASK_ATTRIBUTES.get())).thenThrow(cause);

        assertThatThrownBy(() -> AgentJdbc.fetchTaskAttributes(provider(connection), "TASK"))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(942);
                    assertThat(error.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void statusLookupReportsMissingResource() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(AgentSql.GET_AGENT_STATUS.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));

        assertThatThrownBy(() -> AgentJdbc.fetchStatus(provider(connection),
                AgentSql.GET_AGENT_STATUS, "MISSING", "agent"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI agent not found: MISSING");
        verify(statement).setString(1, "MISSING");
    }

    @Test
    void statusLookupPreservesJdbcCauseCodeAndState() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException cause = new SQLException("view unavailable", "42000", 942);
        when(connection.prepareStatement(AgentSql.GET_TOOL_STATUS.get())).thenThrow(cause);

        assertThatThrownBy(() -> AgentJdbc.fetchStatus(provider(connection),
                AgentSql.GET_TOOL_STATUS, "TOOL", "tool"))
                .isInstanceOfSatisfying(SelectAIException.class, error -> {
                    assertThat(error.getCause()).isSameAs(cause);
                    assertThat(error.getErrorCode()).isEqualTo(942);
                    assertThat(error.getSqlState()).isEqualTo("42000");
                });
    }

    @Test
    void bindsNullableStringsAndClobsWithoutSqlConcatenation() throws Exception {
        CallableStatement statement = mock(CallableStatement.class);
        AgentJdbc.bindString(statement, 1, null);
        AgentJdbc.bindString(statement, 2, "TEAM");
        AgentJdbc.bindClob(statement, 3, null);
        AgentJdbc.bindClob(statement, 4, "Message");

        verify(statement).setNull(1, Types.VARCHAR);
        verify(statement).setString(2, "TEAM");
        verify(statement).setNull(3, Types.CLOB);
        ArgumentCaptor<Reader> reader = ArgumentCaptor.forClass(Reader.class);
        verify(statement).setCharacterStream(eq(4), reader.capture(), eq(7));
        char[] content = new char[7];
        assertThat(reader.getValue().read(content)).isEqualTo(7);
        assertThat(new String(content)).isEqualTo("Message");
    }

    @Test
    void readsClobsAndConvertsReaderFailureToSQLException() throws Exception {
        Clob clob = mock(Clob.class);
        String content = "x".repeat(9000);
        when(clob.getCharacterStream()).thenReturn(new java.io.StringReader(content));
        assertThat(AgentJdbc.readClob(clob)).isEqualTo(content);
        verify(clob).free();
        assertThat(AgentJdbc.readClob(null)).isNull();

        Clob broken = mock(Clob.class);
        Reader reader = mock(Reader.class);
        IOException cause = new IOException("read failed");
        when(broken.getCharacterStream()).thenReturn(reader);
        when(reader.read(any(char[].class))).thenThrow(cause);
        assertThatThrownBy(() -> AgentJdbc.readClob(broken))
                .isInstanceOf(SQLException.class)
                .hasCause(cause);
        var order = inOrder(reader, broken);
        order.verify(reader).close();
        order.verify(broken).free();
    }

    @Test
    void freesClobWhenStreamAcquisitionFailsAndPreservesSqlMetadata() throws Exception {
        Clob clob = mock(Clob.class);
        SQLException failure = new SQLException("Cannot open stream", "72000", 22275);
        when(clob.getCharacterStream()).thenThrow(failure);

        assertThatThrownBy(() -> AgentJdbc.readClob(clob)).isSameAs(failure);
        verify(clob).free();
    }

    @Test
    void returnsClobCleanupFailureWhenReadingSucceeded() throws Exception {
        Clob clob = mock(Clob.class);
        Reader reader = mock(Reader.class);
        when(clob.getCharacterStream()).thenReturn(reader);
        when(reader.read(any(char[].class))).thenReturn(-1);
        SQLException failure = new SQLException("Cannot free LOB", "72000", 22275);
        doThrow(failure).when(clob).free();

        assertThatThrownBy(() -> AgentJdbc.readClob(clob)).isSameAs(failure);
        var order = inOrder(reader, clob);
        order.verify(reader).close();
        order.verify(clob).free();
    }

    @Test
    void preservesReadFailureWhenStreamCloseAndLobFreeAlsoFail() throws Exception {
        Clob clob = mock(Clob.class);
        Reader reader = mock(Reader.class);
        IOException readFailure = new IOException("Cannot read");
        IOException closeFailure = new IOException("Cannot close stream");
        SQLException freeFailure = new SQLException("Cannot free LOB");
        when(clob.getCharacterStream()).thenReturn(reader);
        when(reader.read(any(char[].class))).thenThrow(readFailure);
        doThrow(closeFailure).when(reader).close();
        doThrow(freeFailure).when(clob).free();

        assertThatThrownBy(() -> AgentJdbc.readClob(clob))
                .isInstanceOf(SQLException.class)
                .hasCause(readFailure);
        assertThat(readFailure.getSuppressed()).containsExactly(closeFailure, freeFailure);
        verify(clob).free();
    }

    @Test
    void suppressesCleanupFailureOnStreamAcquisitionFailure() throws Exception {
        Clob clob = mock(Clob.class);
        SQLException failure = new SQLException("Cannot open stream");
        SQLException cleanupFailure = new SQLException("Cannot free LOB");
        when(clob.getCharacterStream()).thenThrow(failure);
        doThrow(cleanupFailure).when(clob).free();

        assertThatThrownBy(() -> AgentJdbc.readClob(clob)).isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
        verify(clob).free();
    }

    @Test
    void freesClobWhenStreamCloseFails() throws Exception {
        Clob clob = mock(Clob.class);
        Reader reader = mock(Reader.class);
        IOException failure = new IOException("Cannot close stream");
        when(clob.getCharacterStream()).thenReturn(reader);
        when(reader.read(any(char[].class))).thenReturn(-1);
        doThrow(failure).when(reader).close();

        assertThatThrownBy(() -> AgentJdbc.readClob(clob))
                .isInstanceOf(SQLException.class).hasCause(failure);
        verify(clob).free();
    }

    @Test
    void validatesLocalArgumentsAndDefinitionParameters() {
        assertThatThrownBy(() -> AgentJdbc.requireText(" ", "name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentJdbc.requireCreated(false, "tool", "run"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AgentJdbc.objectTypeValue(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AgentJdbc.objectTypeValue(AgentObjectType.TEAM)).isEqualTo("TEAM");
        assertThat(AgentJdbc.definitionParams(false)).isNull();
        assertThat(AgentJdbc.definitionParams(true)).isEqualTo("{\"dependent_objects\":true}");
    }

    @SafeVarargs
    private static PreparedStatement stubAttributes(Connection connection, AgentSql sql,
                                                    Map.Entry<String, String>... entries) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        List<Map.Entry<String, String>> attributes = new ArrayList<>(List.of(entries));
        AtomicInteger index = new AtomicInteger(-1);
        when(connection.prepareStatement(sql.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenAnswer(invocation -> index.incrementAndGet() < attributes.size());
        when(rows.getString("attribute_name"))
                .thenAnswer(invocation -> attributes.get(index.get()).getKey());
        when(rows.getString("attribute_value"))
                .thenAnswer(invocation -> attributes.get(index.get()).getValue());
        return statement;
    }

    private static PreparedStatement stubResourceLookup(Connection connection, AgentSql query, boolean exists)
            throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(query.get())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(exists);
        return statement;
    }

    private static AgentSql attributeQuery(AgentObjectType type) {
        return switch (type) {
            case AGENT -> AgentSql.GET_AGENT_ATTRIBUTES;
            case TASK -> AgentSql.GET_TASK_ATTRIBUTES;
            case TOOL -> AgentSql.GET_TOOL_ATTRIBUTES;
            case TEAM -> AgentSql.GET_TEAM_ATTRIBUTES;
        };
    }

    private static AgentSql resourceQuery(AgentObjectType type) {
        return switch (type) {
            case AGENT -> AgentSql.GET_AGENT;
            case TASK -> AgentSql.GET_TASK;
            case TOOL -> AgentSql.GET_TOOL;
            case TEAM -> AgentSql.GET_TEAM;
        };
    }

    private static Object fetchAttributes(Connection connection, AgentObjectType type)
            throws SQLException, SelectAIException {
        return switch (type) {
            case AGENT -> AgentJdbc.fetchAgentAttributes(connection, type.name());
            case TASK -> AgentJdbc.fetchTaskAttributes(connection, type.name());
            case TOOL -> AgentJdbc.fetchToolAttributes(connection, type.name());
            case TEAM -> AgentJdbc.fetchTeamAttributes(connection, type.name());
        };
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
