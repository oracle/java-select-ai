/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AvailableTeam;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolParams;
import com.oracle.database.selectai.model.agent.ToolType;
import oracle.jdbc.OracleTypes;

import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBC helpers shared by Select AI Agent implementations.
 * <p>
 * These helpers centralize bind-variable usage, Oracle PL/SQL type handling,
 * JSON conversion, metadata mapping, and conversion of JDBC failures into
 * {@link SelectAIException}. Attribute queries also verify resource existence
 * when the attribute view has no rows, so a deleted resource is not exposed as
 * an apparently valid empty configuration.
 */
final class AgentJdbc {
    /** JSON mapper for small generated parameter payloads. */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private AgentJdbc() {
    }

    /** Requires a non-blank value. */
    static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    /** Builds the database params payload for a conversation-specific state query. */
    static String conversationParams(String conversationId) {
        requireText(conversationId, "conversationId");
        return MAPPER.createObjectNode()
                .put("conversation_id", conversationId)
                .toString();
    }

    /**
     * Requires a valid database parameter object with an explicit conversation ID.
     * The original JSON is returned unchanged so database-specific parameters are
     * preserved.
     */
    static String requireConversationParams(String paramsJson) {
        requireText(paramsJson, "paramsJson");
        try {
            JsonNode params = MAPPER.readTree(paramsJson);
            JsonNode conversationId = params == null || !params.isObject()
                    ? null : params.get("conversation_id");
            if (conversationId == null || !conversationId.isTextual()
                    || conversationId.asText().isBlank()) {
                throw new IllegalArgumentException(
                        "paramsJson must contain a non-blank conversation_id");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "paramsJson must be valid JSON containing conversation_id", e);
        }
        return paramsJson;
    }

    /** Requires a configured resource to be created before database-backed use. */
    static void requireCreated(boolean created, String resourceType, String operation) {
        if (!created) {
            throw new IllegalStateException(operation + " requires a created " + resourceType + "; call create() first");
        }
    }

    /** Binds a VARCHAR value or SQL NULL. */
    static void bindString(CallableStatement stmt, int index, String value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.VARCHAR);
        } else {
            stmt.setString(index, value);
        }
    }

    /** Binds a CLOB value or SQL NULL. */
    static void bindClob(CallableStatement stmt, int index, String value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.CLOB);
        } else {
            stmt.setCharacterStream(index, new StringReader(value), value.length());
        }
    }

    /**
     * Binds a PL/SQL BOOLEAN argument for an Oracle package call.
     * This is required for package parameters such as the agent resource
     * {@code FORCE} argument; {@code setBoolean} binds a SQL boolean type and
     * is not interchangeable with Oracle's PL/SQL boolean parameter type.
     */
    static void bindPlsqlBoolean(CallableStatement stmt, int index, boolean value) throws SQLException {
        stmt.setObject(index, value, OracleTypes.PLSQL_BOOLEAN);
    }

    /**
     * Reads and frees an owned CLOB return value, including on read failure.
     * Cleanup failures are suppressed on the original failure when present.
     */
    static String readClob(Clob clob) throws SQLException {
        if (clob == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[8192];
        // Closing the stream does not release a returned temporary LOB.
        try (AutoCloseable lob = clob::free;
             Reader reader = clob.getCharacterStream()) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        } catch (Exception e) {
            if (e instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new SQLException("Failed to read CLOB", e);
        }
        return builder.toString();
    }

    /** Converts SQL failure to the SDK checked exception. */
    static SelectAIException toException(String message, SQLException e) {
        return new SelectAIException(message, e, e.getErrorCode(), e.getSQLState());
    }

    /** Executes RUN_TEAM without requiring access to owner-only team metadata. */
    static String runTeam(ConnectionProvider connectionProvider, String teamName, RunTeamRequest request)
            throws SQLException, SelectAIException {
        requireText(teamName, "teamName");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return connectionProvider.withConnection(connection -> {
            try (CallableStatement stmt = connection.prepareCall(AgentSql.RUN_TEAM.get())) {
                stmt.registerOutParameter(1, Types.CLOB);
                stmt.setString(2, teamName);
                bindClob(stmt, 3, request.getUserPrompt());
                bindClob(stmt, 4, request.toParamsJson());
                stmt.execute();
                return readClob(stmt.getClob(1));
            }
        });
    }

    /**
     * Loads discovery metadata for teams available to the calling user.
     * The database package returns a CLOB JSON array rather than owner-scoped
     * lifecycle metadata.
     */
    static List<AvailableTeam> fetchAvailableTeams(Connection connection)
            throws SQLException, SelectAIException {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        try (CallableStatement stmt = connection.prepareCall(AgentSql.LIST_AVAILABLE_TEAMS.get())) {
            stmt.registerOutParameter(1, Types.CLOB);
            stmt.execute();
            String json = readClob(stmt.getClob(1));
            try {
                return parseAvailableTeams(json);
            } catch (RuntimeException e) {
                throw new SelectAIException("Failed to parse available Select AI agent teams", e);
            }
        }
    }

    /** Gets team state without requiring an owner-only Team handle. */
    static String getTeamState(ConnectionProvider connectionProvider, String teamName, String paramsJson)
            throws SQLException, SelectAIException {
        requireText(teamName, "teamName");
        requireConversationParams(paramsJson);
        return connectionProvider.withConnection(connection -> {
            try (CallableStatement stmt = connection.prepareCall(AgentSql.GET_TEAM_STATE.get())) {
                stmt.registerOutParameter(1, Types.VARCHAR);
                stmt.setString(2, teamName);
                bindClob(stmt, 3, paramsJson);
                stmt.execute();
                return stmt.getString(1);
            }
        });
    }

    /** Sets team execution state without requiring an owner-only Team handle. */
    static boolean setTeamExecutionState(ConnectionProvider connectionProvider, String teamName,
                                         ExecutionStateRequest request)
            throws SQLException, SelectAIException {
        requireText(teamName, "teamName");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String paramsJson = requireConversationParams(request.toParamsJson());
        return connectionProvider.withConnection(connection -> {
            try (CallableStatement stmt = connection.prepareCall(AgentSql.SET_EXECUTION_STATE.get())) {
                stmt.setString(1, teamName);
                bindClob(stmt, 2, request.getState().getValue());
                bindClob(stmt, 3, paramsJson);
                stmt.execute();
                return true;
            }
        });
    }

    /**
     * Grants or revokes execution access for an owner-managed team.
     *
     * @param connectionProvider provider used for the package call
     * @param grant {@code true} to grant access, or {@code false} to revoke it
     * @param teamName owner's unqualified team name
     * @param userOrRoleName database user or role receiving or losing access
     * @return {@code true} when the package call completes
     * @throws IllegalArgumentException when an identifier is invalid
     * @throws SQLException when the JDBC operation fails
     * @throws SelectAIException when the connection provider reports an SDK failure
     */
    static boolean updateTeamAccess(ConnectionProvider connectionProvider, boolean grant,
                                    String teamName, String userOrRoleName)
            throws SQLException, SelectAIException {
        String validatedTeamName = validateTeamAccessIdentifier("teamName", teamName, false);
        String validatedUserOrRoleName = validateTeamAccessIdentifier(
                "userOrRoleName", userOrRoleName, true);
        AgentSql operation = grant ? AgentSql.GRANT_TEAM_ACCESS : AgentSql.REVOKE_TEAM_ACCESS;
        return connectionProvider.withConnection(connection -> {
            try (CallableStatement statement = connection.prepareCall(operation.get())) {
                statement.setString(1, validatedTeamName);
                statement.setString(2, validatedUserOrRoleName);
                statement.execute();
                return true;
            }
        });
    }

    /** Validates an unqualified Oracle identifier used by team-sharing APIs. */
    private static String validateTeamAccessIdentifier(String parameterName, String value,
                                                       boolean rejectPublic) {
        requireText(value, parameterName);
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException(parameterName
                    + " must not have leading or trailing whitespace");
        }
        if (rejectPublic && "PUBLIC".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("userOrRoleName must not be PUBLIC");
        }
        if (!value.matches("[A-Za-z][A-Za-z0-9_$#]{0,127}")) {
            throw new IllegalArgumentException(parameterName
                    + " must be an unqualified Oracle identifier");
        }
        return value;
    }

    /** Reads a namespaced runtime variable from the current database session. */
    static String getVariable(ConnectionProvider connectionProvider, String namespace, String name,
                              String scope) throws SQLException, SelectAIException {
        requireText(namespace, "namespace");
        requireText(name, "name");
        requireText(scope, "scope");
        return connectionProvider.withConnection(connection -> {
            try (CallableStatement stmt = connection.prepareCall(AgentSql.GET_VARIABLE.get())) {
                stmt.registerOutParameter(1, Types.CLOB);
                stmt.setString(2, namespace);
                stmt.setString(3, name);
                stmt.setString(4, scope);
                stmt.execute();
                return readClob(stmt.getClob(1));
            }
        });
    }

    /** Sets or clears a namespaced runtime variable in the current database session. */
    static boolean setVariable(ConnectionProvider connectionProvider, String namespace, String name,
                               String value, String scope) throws SQLException, SelectAIException {
        requireText(namespace, "namespace");
        requireText(name, "name");
        requireText(scope, "scope");
        return connectionProvider.withConnection(connection -> {
            try (CallableStatement stmt = connection.prepareCall(AgentSql.SET_VARIABLE.get())) {
                stmt.setString(1, namespace);
                stmt.setString(2, name);
                bindClob(stmt, 3, value);
                stmt.setString(4, scope);
                stmt.execute();
                return true;
            }
        });
    }

    /** Fetches only the current status, preserving missing-resource and JDBC failures. */
    static String fetchStatus(ConnectionProvider connectionProvider, AgentSql query, String name,
                              String resourceType) throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(query.get())) {
                    statement.setString(1, name);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SelectAIException("Select AI " + resourceType + " not found: " + name);
                        }
                        return resultSet.getString("status");
                    }
                }
            });
        } catch (SQLException e) {
            throw toException("Failed to fetch Select AI " + resourceType + " status: " + name, e);
        }
    }

    /** Builds GET_DEFINITION params JSON. */
    static String definitionParams(boolean includeDependentObjects) {
        if (!includeDependentObjects) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(Map.of("dependent_objects", true));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize definition parameters", e);
        }
    }

    /** Returns database object type text. */
    static String objectTypeValue(AgentObjectType objectType) {
        if (objectType == null) {
            throw new IllegalArgumentException("objectType must not be null");
        }
        return objectType.name();
    }

    /**
     * Fetches the current attributes for an agent through the configured
     * connection provider.
     *
     * @param connectionProvider provider used to execute the metadata query
     * @param agentName agent whose attributes should be fetched
     * @return typed agent attributes loaded from database metadata
     * @throws SelectAIException when the resource is missing, or the metadata
     *         query or attribute mapping fails
     */
    static AgentAttributes fetchAgentAttributes(ConnectionProvider connectionProvider, String agentName)
            throws SelectAIException {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        try {
            return connectionProvider.withConnection(
                    connection -> fetchAgentAttributes(connection, agentName));
        } catch (SQLException e) {
            throw toException("Failed to fetch Select AI agent attributes: " + agentName, e);
        }
    }

    /**
     * Fetches and maps agent attributes using an existing JDBC connection.
     *
     * @param connection JDBC connection used for the metadata query
     * @param agentName agent whose attributes should be fetched
     * @return typed agent attributes loaded from database metadata
     * @throws SQLException when the metadata query or resource existence check fails
     * @throws SelectAIException when the resource is missing or database
     *         attribute values cannot be mapped
     */
    static AgentAttributes fetchAgentAttributes(Connection connection, String agentName)
            throws SQLException, SelectAIException {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }

        AgentAttributes.Builder builder = AgentAttributes.builder();
        boolean hasRows = false;
        try (PreparedStatement statement = connection.prepareStatement(AgentSql.GET_AGENT_ATTRIBUTES.get())) {
            statement.setString(1, agentName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    hasRows = true;
                    mapAgentAttribute(builder,
                            resultSet.getString("attribute_name"),
                            resultSet.getString("attribute_value"));
                }
            }
        } catch (SQLException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SelectAIException(
                    "Failed to map Select AI agent attributes: " + agentName, e);
        }
        if (!hasRows) {
            requireResourceExists(connection, AgentSql.GET_AGENT, agentName, "agent");
        }
        try {
            return builder.build();
        } catch (RuntimeException e) {
            throw new SelectAIException(
                    "Failed to map Select AI agent attributes: " + agentName, e);
        }
    }

    /** Maps a database attribute row into the typed agent-attribute builder. */
    private static void mapAgentAttribute(AgentAttributes.Builder builder,
                                          String attributeName,
                                          String attributeValue) {
        if (attributeName == null) {
            return;
        }
        switch (attributeName.toLowerCase(Locale.ROOT)) {
            case "profile_name" -> builder.profileName(attributeValue);
            case "role" -> builder.role(attributeValue);
            case "enable_human_tool" -> builder.enableHumanTool(parseBoolean(attributeValue));
            case "tools" -> builder.tools(parseStringList(attributeValue, "tools"));
            case "short_term_memory_length" -> builder.shortTermMemoryLength(parseInteger(attributeValue));
            case "supervisor" -> builder.supervisor(parseBoolean(attributeValue));
            default -> {
                // Ignore attributes introduced by newer database releases.
            }
        }
    }

    /** Fetches typed attributes for a task owned by the current user. */
    static TaskAttributes fetchTaskAttributes(ConnectionProvider connectionProvider, String taskName)
            throws SelectAIException {
        try {
            return mapTaskAttributes(fetchAttributeValues(connectionProvider, taskName,
                    AgentSql.GET_TASK_ATTRIBUTES, AgentSql.GET_TASK, "task"));
        } catch (RuntimeException e) {
            throw new SelectAIException("Failed to map Select AI task attributes: " + taskName, e);
        }
    }

    /** Fetches typed task attributes using an existing JDBC connection. */
    static TaskAttributes fetchTaskAttributes(Connection connection, String taskName)
            throws SQLException, SelectAIException {
        try {
            return mapTaskAttributes(fetchAttributeValues(connection, taskName,
                    AgentSql.GET_TASK_ATTRIBUTES, AgentSql.GET_TASK, "task"));
        } catch (RuntimeException e) {
            throw new SelectAIException("Failed to map Select AI task attributes: " + taskName, e);
        }
    }

    /** Fetches typed attributes for a tool owned by the current user. */
    static ToolAttributes fetchToolAttributes(ConnectionProvider connectionProvider, String toolName)
            throws SelectAIException {
        try {
            return mapToolAttributes(fetchAttributeValues(connectionProvider, toolName,
                    AgentSql.GET_TOOL_ATTRIBUTES, AgentSql.GET_TOOL, "tool"));
        } catch (RuntimeException e) {
            throw new SelectAIException("Failed to map Select AI tool attributes: " + toolName, e);
        }
    }

    /** Fetches typed tool attributes using an existing JDBC connection. */
    static ToolAttributes fetchToolAttributes(Connection connection, String toolName)
            throws SQLException, SelectAIException {
        try {
            return mapToolAttributes(fetchAttributeValues(connection, toolName,
                    AgentSql.GET_TOOL_ATTRIBUTES, AgentSql.GET_TOOL, "tool"));
        } catch (RuntimeException e) {
            throw new SelectAIException("Failed to map Select AI tool attributes: " + toolName, e);
        }
    }

    /** Fetches typed attributes for a team owned by the current user. */
    static TeamAttributes fetchTeamAttributes(ConnectionProvider connectionProvider, String teamName)
            throws SelectAIException {
        try {
            return mapTeamAttributes(fetchAttributeValues(connectionProvider, teamName,
                    AgentSql.GET_TEAM_ATTRIBUTES, AgentSql.GET_TEAM, "team"));
        } catch (RuntimeException e) {
            throw new SelectAIException("Failed to map Select AI team attributes: " + teamName, e);
        }
    }

    /** Fetches typed team attributes using an existing JDBC connection. */
    static TeamAttributes fetchTeamAttributes(Connection connection, String teamName)
            throws SQLException, SelectAIException {
        try {
            return mapTeamAttributes(fetchAttributeValues(connection, teamName,
                    AgentSql.GET_TEAM_ATTRIBUTES, AgentSql.GET_TEAM, "team"));
        } catch (RuntimeException e) {
            throw new SelectAIException("Failed to map Select AI team attributes: " + teamName, e);
        }
    }

    /**
     * Reads attribute rows into a case-normalized map.
     * <p>
     * An empty attribute result is checked against the resource metadata view
     * so a deleted resource is reported as missing rather than as an empty
     * configuration.
     */
    private static Map<String, String> fetchAttributeValues(ConnectionProvider connectionProvider,
                                                            String resourceName,
                                                            AgentSql attributeQuery,
                                                            AgentSql resourceQuery,
                                                            String resourceType)
            throws SelectAIException {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        try {
            return connectionProvider.withConnection(connection -> fetchAttributeValues(connection, resourceName,
                    attributeQuery, resourceQuery, resourceType));
        } catch (SQLException e) {
            throw toException("Failed to fetch Select AI " + resourceType
                    + " attributes: " + resourceName, e);
        }
    }

    /**
     * Reads attribute rows using an existing JDBC connection.
     * <p>
     * An empty attribute result is checked against the resource metadata view
     * so a deleted resource is reported as missing rather than as an empty
     * configuration.
     */
    private static Map<String, String> fetchAttributeValues(Connection connection, String resourceName,
                                                            AgentSql attributeQuery, AgentSql resourceQuery,
                                                            String resourceType)
            throws SQLException, SelectAIException {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        Map<String, String> values = new LinkedHashMap<>();
        boolean hasRows = false;
        try (PreparedStatement statement = connection.prepareStatement(attributeQuery.get())) {
            statement.setString(1, resourceName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    hasRows = true;
                    String attributeName = resultSet.getString("attribute_name");
                    if (attributeName != null) {
                        values.put(attributeName.toLowerCase(Locale.ROOT),
                                resultSet.getString("attribute_value"));
                    }
                }
            }
        }
        if (!hasRows) {
            requireResourceExists(connection, resourceQuery, resourceName, resourceType);
        }
        return values;
    }

    /** Distinguishes a resource with no attribute rows from a deleted resource. */
    private static void requireResourceExists(Connection connection, AgentSql query, String name, String resourceType)
            throws SQLException, SelectAIException {
        try (PreparedStatement statement = connection.prepareStatement(query.get())) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SelectAIException("Select AI " + resourceType + " not found: " + name);
                }
            }
        }
    }

    /** Maps database task attributes into the public typed model. */
    private static TaskAttributes mapTaskAttributes(Map<String, String> values) {
        return TaskAttributes.builder()
                .instruction(values.get("instruction"))
                .tools(parseStringList(values.get("tools"), "tools"))
                .input(parseStringList(values.get("input"), "input"))
                .enableHumanTool(parseBoolean(values.get("enable_human_tool")))
                .build();
    }

    /** Maps database tool attributes into the public typed model. */
    private static ToolAttributes mapToolAttributes(Map<String, String> values) {
        ToolType toolType = parseToolType(values.get("tool_type"));
        return ToolAttributes.builder()
                .instruction(values.get("instruction"))
                .function(values.get("function"))
                .toolType(toolType)
                .toolParams(parseToolParams(toolType, values.get("tool_params")))
                .toolInputs(parseToolInputs(values.get("tool_inputs")))
                .build();
    }

    /** Selects typed parameters while preserving future database fields. */
    private static ToolParams parseToolParams(ToolType toolType, String json) {
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> values = MAPPER.readerFor(new TypeReference<Map<String, Object>>() { })
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(json);
            return ToolParams.fromMap(toolType, values);
        } catch (Exception e) {
            throw new IllegalArgumentException("tool_params must be a JSON object", e);
        }
    }

    /** Maps database team attributes into the public typed model. */
    private static TeamAttributes mapTeamAttributes(Map<String, String> values) {
        return TeamAttributes.builder()
                .agents(parseTeamMembers(values.get("agents")))
                .process(values.get("process"))
                .longTermMemoryLength(parseInteger(values.get("long_term_memory_length")))
                .supervisorAgent(values.get("supervisor_agent"))
                .build();
    }

    /** Parses an optional database boolean value. */
    private static Boolean parseBoolean(String value) {
        return value == null ? null : Boolean.valueOf(value);
    }

    /** Parses an optional database integer value. */
    private static Integer parseInteger(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    /** Parses the JSON array used by the database for agent tool names. */
    private static List<String> parseStringList(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.readValue(value, new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " must be a JSON string array", e);
        }
    }

    /** Parses a known or future database tool type into the public value object. */
    private static ToolType parseToolType(String value) {
        if (value == null) {
            return null;
        }
        return ToolType.of(value);
    }

    /** Parses the input metadata array stored in the tool attributes view. */
    private static List<ToolInput> parseToolInputs(String value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode inputs = MAPPER.readTree(value);
            if (inputs != null && inputs.isNull()) {
                return null;
            }
            if (inputs == null || !inputs.isArray()) {
                throw new IllegalArgumentException("tool_inputs must be a JSON array");
            }
            List<ToolInput> result = new ArrayList<>();
            for (JsonNode input : inputs) {
                if (!input.isObject()) {
                    throw new IllegalArgumentException("tool_inputs entries must be JSON objects");
                }
                JsonNode name = input.get("name");
                JsonNode description = input.get("description");
                if ((name != null && !name.isNull() && !name.isTextual())
                        || (description != null && !description.isNull() && !description.isTextual())) {
                    throw new IllegalArgumentException("tool_inputs name and description must be strings");
                }
                result.add(ToolInput.builder()
                        .name(name == null || name.isNull() ? null : name.textValue())
                        .description(description == null || description.isNull() ? null : description.textValue())
                        .build());
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("tool_inputs must be a valid JSON array", e);
        }
    }

    /** Parses the JSON team-member array stored in the team attributes view. */
    private static List<TeamMember> parseTeamMembers(String value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode members = MAPPER.readTree(value);
            if (!members.isArray()) {
                throw new IllegalArgumentException("agents must be a JSON array");
            }
            List<TeamMember> result = new ArrayList<>();
            for (JsonNode member : members) {
                JsonNode name = member.get("name");
                JsonNode task = member.get("task");
                TeamMember.MemberBuilder builder = TeamMember.builder(
                        name == null || name.isNull() ? null : name.asText());
                if (task != null && !task.isNull()) {
                    builder.task(task.asText());
                }
                result.add(builder.build());
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("agents must be a valid JSON array", e);
        }
    }

    /** Parses the JSON array returned by DBMS_CLOUD_AI_AGENT.LIST_TEAMS. */
    private static List<AvailableTeam> parseAvailableTeams(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            JsonNode teams = MAPPER.readTree(value);
            if (!teams.isArray()) {
                throw new IllegalArgumentException("LIST_TEAMS result must be a JSON array");
            }
            List<AvailableTeam> result = new ArrayList<>();
            for (JsonNode team : teams) {
                if (!team.isObject()) {
                    throw new IllegalArgumentException("LIST_TEAMS result entries must be JSON objects");
                }
                String teamName = firstText(team, "name", "team_name", "agent_team_name");
                result.add(new AvailableTeam(teamName, firstText(team, "description"), team.toString()));
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("LIST_TEAMS result must be a valid JSON array", e);
        }
    }

    /** Reads the first non-null textual field from a discovery object. */
    private static String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }
}
