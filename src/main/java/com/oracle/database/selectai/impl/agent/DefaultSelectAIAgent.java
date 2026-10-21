/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.AvailableTeam;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.impl.DataSourceConnectionProvider;
import com.oracle.database.selectai.impl.JdbcExecutionConnectionProvider;
import com.oracle.database.selectai.impl.SingleConnectionProvider;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Default JDBC-backed {@link SelectAIAgent} implementation.
 * <p>
 * This implementation supports SDK-owned single-connection mode and
 * application-managed {@link DataSource} mode. It creates the public agent,
 * task, tool, and team handles and translates their operations into
 * {@code DBMS_CLOUD_AI_AGENT} calls. Applications should use the
 * {@link SelectAIAgent} interface and its factory methods rather than depend
 * on this implementation class directly.
 */
final class DefaultSelectAIAgent implements SelectAIAgent {
    /** Logger for Select AI Agent entry-point operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSelectAIAgent.class);
    /** SDK-owned connection wrapper in DbConnectionConfig mode. */
    private final DbConnection dbConnection;
    /** Provider that controls connection acquisition and release. */
    private final ConnectionProvider connectionProvider;

    /**
     * Creates a SelectAIAgent client from database connection configuration.
     * The SDK opens and owns the JDBC connection until {@link #close()} is
     * called.
     *
     * @param dbConnectionConfig database connection configuration
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the JDBC connection cannot be initialized
     */
    DefaultSelectAIAgent(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        this(dbConnectionConfig, SelectAIOptions.defaults());
    }

    /**
     * Creates a SelectAIAgent client from database connection configuration
     * and SDK execution options.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options; {@code null} uses defaults
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the JDBC connection cannot be initialized
     */
    DefaultSelectAIAgent(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        this(createDbConnection(dbConnectionConfig), options);
    }

    /**
     * Creates a DataSource-backed SelectAIAgent client.
     * The application owns the supplied {@link DataSource}; the SDK borrows
     * and closes connections for individual operations.
     *
     * @param dataSource application-managed or pooled JDBC data source
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    DefaultSelectAIAgent(DataSource dataSource) {
        this(dataSource, SelectAIOptions.defaults());
    }

    /**
     * Creates a DataSource-backed SelectAIAgent client with SDK execution
     * options.
     *
     * @param dataSource application-managed or pooled JDBC data source
     * @param options SDK execution options; {@code null} uses defaults
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    DefaultSelectAIAgent(DataSource dataSource, SelectAIOptions options) {
        this(null, new DataSourceConnectionProvider(dataSource), options);
    }

    /** Creates a SelectAIAgent client from a connection provider for tests. */
    DefaultSelectAIAgent(ConnectionProvider connectionProvider) {
        this(null, connectionProvider, SelectAIOptions.defaults());
    }

    private DefaultSelectAIAgent(DbConnection dbConnection, SelectAIOptions options) {
        this(dbConnection, new SingleConnectionProvider(dbConnection), options);
    }

    private DefaultSelectAIAgent(DbConnection dbConnection, ConnectionProvider connectionProvider,
                                 SelectAIOptions options) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (dbConnection == null) {
            LOGGER.debug("SelectAIAgent initialized with DataSource-backed connection provider");
        } else {
            LOGGER.debug("SelectAIAgent initialized with SDK-owned JDBC connection");
        }
        this.dbConnection = dbConnection;
        this.connectionProvider = new JdbcExecutionConnectionProvider(connectionProvider, options);
    }

    private static DbConnection createDbConnection(DbConnectionConfig dbConnectionConfig)
            throws SelectAIException {
        if (dbConnectionConfig == null) {
            throw new IllegalArgumentException("dbConnectionConfig must not be null");
        }
        LOGGER.debug("Initializing SelectAIAgent with user {}", dbConnectionConfig.getDbUser());
        return DbConnection.create(dbConnectionConfig);
    }

    /** Returns the connection provider used by resource handles. */
    private ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws SelectAIException {
        if (dbConnection != null) {
            dbConnection.close();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Agent agent(String agentName) throws SelectAIException {
        return new DefaultAgent(getConnectionProvider(), agentName);
    }

    /** {@inheritDoc} */
    @Override
    public Agent agent(String agentName, AgentAttributes attributes, String description, AgentStatus status) {
        return new DefaultAgent(getConnectionProvider(), agentName, attributes, description, status);
    }

    /** {@inheritDoc} */
    @Override
    public Task task(String taskName) throws SelectAIException {
        return new DefaultTask(getConnectionProvider(), taskName);
    }

    /** {@inheritDoc} */
    @Override
    public Task task(String taskName, TaskAttributes attributes, String description, TaskStatus status) {
        return new DefaultTask(getConnectionProvider(), taskName, attributes, description, status);
    }

    /** {@inheritDoc} */
    @Override
    public Tool tool(String toolName) throws SelectAIException {
        return new DefaultTool(getConnectionProvider(), toolName);
    }

    /** {@inheritDoc} */
    @Override
    public Tool tool(String toolName, ToolAttributes attributes, String description, ToolStatus status) {
        return new DefaultTool(getConnectionProvider(), toolName, attributes, description, status);
    }

    /** {@inheritDoc} */
    @Override
    public Team team(String teamName) throws SelectAIException {
        return new DefaultTeam(getConnectionProvider(), teamName);
    }

    /** {@inheritDoc} */
    @Override
    public Team team(String teamName, TeamAttributes attributes, String description, TeamStatus status) {
        return new DefaultTeam(getConnectionProvider(), teamName, attributes, description, status);
    }

    /** {@inheritDoc} */
    @Override
    public TeamRunResult runTeam(String teamName, RunTeamRequest request) throws SelectAIException {
        AgentJdbc.requireText(teamName, "teamName");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        LOGGER.debug("Running Select AI agent team {}", teamName);
        try {
            return TeamRunResult.fromRawValue(
                    AgentJdbc.runTeam(connectionProvider, teamName, request));
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.RUN_TEAM failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.RUN_TEAM", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public TeamExecutionState getTeamStateByConversationId(String teamName, String conversationId)
            throws SelectAIException {
        AgentJdbc.requireText(teamName, "teamName");
        AgentJdbc.requireText(conversationId, "conversationId");
        LOGGER.debug("Getting Select AI agent team state for {}", teamName);
        try {
            return TeamExecutionState.of(AgentJdbc.getTeamState(
                    connectionProvider, teamName,
                    AgentJdbc.conversationParams(conversationId)));
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.GET_TEAM_STATE failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.GET_TEAM_STATE", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean setTeamExecutionState(String teamName, ExecutionStateRequest request)
            throws SelectAIException {
        AgentJdbc.requireText(teamName, "teamName");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        LOGGER.debug("Setting Select AI agent team execution state for {}", teamName);
        try {
            boolean updated = AgentJdbc.setTeamExecutionState(connectionProvider, teamName, request);
            LOGGER.info("Updated Select AI agent team execution state for {}", teamName);
            return updated;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_EXECUTION_STATE failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_EXECUTION_STATE", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean grantTeamAccess(String teamName, String userOrRoleName)
            throws SelectAIException {
        return updateTeamAccess(true, teamName, userOrRoleName);
    }

    /** {@inheritDoc} */
    @Override
    public boolean revokeTeamAccess(String teamName, String userOrRoleName)
            throws SelectAIException {
        return updateTeamAccess(false, teamName, userOrRoleName);
    }

    private boolean updateTeamAccess(boolean grant, String teamName, String userOrRoleName)
            throws SelectAIException {
        String operation = grant ? "GRANT_TEAM_ACCESS" : "REVOKE_TEAM_ACCESS";
        LOGGER.debug("{} for team {} and grantee {}", operation, teamName, userOrRoleName);
        try {
            boolean updated = AgentJdbc.updateTeamAccess(
                    connectionProvider, grant, teamName, userOrRoleName);
            LOGGER.info("{} completed for team {} and grantee {}",
                    operation, teamName, userOrRoleName);
            return updated;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.{} failed for team {} and grantee {}",
                    operation, teamName, userOrRoleName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT." + operation, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getVariable(String namespace, String name, String scope) throws SelectAIException {
        AgentJdbc.requireText(namespace, "namespace");
        AgentJdbc.requireText(name, "name");
        AgentJdbc.requireText(scope, "scope");
        LOGGER.debug("Getting Select AI Agent runtime variable {}/{}", namespace, name);
        try {
            return AgentJdbc.getVariable(connectionProvider, namespace, name, scope);
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.GET_VARIABLE failed for namespace {} and name {}",
                    namespace, name, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.GET_VARIABLE", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean setVariable(String namespace, String name, String value, String scope)
            throws SelectAIException {
        AgentJdbc.requireText(namespace, "namespace");
        AgentJdbc.requireText(name, "name");
        AgentJdbc.requireText(scope, "scope");
        LOGGER.debug("Setting Select AI Agent runtime variable {}/{}", namespace, name);
        try {
            boolean updated = AgentJdbc.setVariable(connectionProvider, namespace, name, value, scope);
            LOGGER.debug("Updated Select AI Agent runtime variable {}/{}", namespace, name);
            return updated;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_VARIABLE failed for namespace {} and name {}",
                    namespace, name, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_VARIABLE", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<Agent> listAgents() throws SelectAIException {
        LOGGER.debug("Listing Select AI agents");
        return listAgents(AgentSql.LIST_AGENTS, null);
    }

    /** {@inheritDoc} */
    @Override
    public List<Agent> listAgents(String agentNamePattern) throws SelectAIException {
        AgentJdbc.requireText(agentNamePattern, "agentNamePattern");
        LOGGER.debug("Listing Select AI agents by pattern");
        return listAgents(AgentSql.LIST_AGENTS_BY_PATTERN, agentNamePattern);
    }

    private List<Agent> listAgents(AgentSql query, String pattern) throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                List<Agent> agents = new ArrayList<>();
                try (PreparedStatement stmt = connection.prepareStatement(query.get())) {
                    if (pattern != null) {
                        stmt.setString(1, pattern);
                    }
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            String agentName = rows.getString("agent_name");
                            agents.add(DefaultAgent.fromMetadata(connectionProvider, agentName,
                                    AgentJdbc.fetchAgentAttributes(connection, agentName), rows.getString("description"),
                                    rows.getString("status")));
                        }
                    }
                }
                return agents;
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to list Select AI agents", e);
            throw AgentJdbc.toException("Failed to list Select AI agents", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<Task> listTasks() throws SelectAIException {
        LOGGER.debug("Listing Select AI agent tasks");
        return listTasks(AgentSql.LIST_TASKS, null);
    }

    /** {@inheritDoc} */
    @Override
    public List<Task> listTasks(String taskNamePattern) throws SelectAIException {
        AgentJdbc.requireText(taskNamePattern, "taskNamePattern");
        LOGGER.debug("Listing Select AI agent tasks by pattern");
        return listTasks(AgentSql.LIST_TASKS_BY_PATTERN, taskNamePattern);
    }

    private List<Task> listTasks(AgentSql query, String pattern) throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                List<Task> tasks = new ArrayList<>();
                try (PreparedStatement stmt = connection.prepareStatement(query.get())) {
                    if (pattern != null) {
                        stmt.setString(1, pattern);
                    }
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            String taskName = rows.getString("task_name");
                            tasks.add(DefaultTask.fromMetadata(connectionProvider, taskName,
                                    AgentJdbc.fetchTaskAttributes(connection, taskName), rows.getString("description"),
                                    rows.getString("status")));
                        }
                    }
                }
                return tasks;
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to list Select AI agent tasks", e);
            throw AgentJdbc.toException("Failed to list Select AI agent tasks", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<Tool> listTools() throws SelectAIException {
        LOGGER.debug("Listing Select AI agent tools");
        return listTools(AgentSql.LIST_TOOLS, null);
    }

    /** {@inheritDoc} */
    @Override
    public List<Tool> listTools(String toolNamePattern) throws SelectAIException {
        AgentJdbc.requireText(toolNamePattern, "toolNamePattern");
        LOGGER.debug("Listing Select AI agent tools by pattern");
        return listTools(AgentSql.LIST_TOOLS_BY_PATTERN, toolNamePattern);
    }

    private List<Tool> listTools(AgentSql query, String pattern) throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                List<Tool> tools = new ArrayList<>();
                try (PreparedStatement stmt = connection.prepareStatement(query.get())) {
                    if (pattern != null) {
                        stmt.setString(1, pattern);
                    }
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            String toolName = rows.getString("tool_name");
                            tools.add(DefaultTool.fromMetadata(connectionProvider, toolName,
                                    AgentJdbc.fetchToolAttributes(connection, toolName), rows.getString("description"),
                                    rows.getString("status")));
                        }
                    }
                }
                return tools;
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to list Select AI agent tools", e);
            throw AgentJdbc.toException("Failed to list Select AI agent tools", e);
        }
    }

    /**
     * Lists teams owned by the current database user and hydrates each result
     * as a mutable lifecycle handle.
     *
     * @return owned team handles with metadata and attributes
     * @throws SelectAIException when team metadata or attributes cannot be loaded
     */
    @Override
    public List<Team> listTeams() throws SelectAIException {
        LOGGER.debug("Listing Select AI agent teams");
        return listTeams(AgentSql.LIST_TEAMS, null);
    }

    /**
     * Lists owned teams whose names match a database regular-expression pattern.
     *
     * @param teamNamePattern regular-expression pattern matched by the database
     * @return matching owned team handles with metadata and attributes
     * @throws IllegalArgumentException when the pattern is null or blank
     * @throws SelectAIException when listing teams fails
     */
    @Override
    public List<Team> listTeams(String teamNamePattern) throws SelectAIException {
        AgentJdbc.requireText(teamNamePattern, "teamNamePattern");
        LOGGER.debug("Listing Select AI agent teams by pattern");
        return listTeams(AgentSql.LIST_TEAMS_BY_PATTERN, teamNamePattern);
    }

    /**
     * Lists teams available to the current database user through the package
     * discovery API and maps the returned JSON to immutable snapshots.
     *
     * @return available team discovery snapshots
     * @throws SelectAIException when discovery or response parsing fails
     */
    @Override
    public List<AvailableTeam> listAvailableTeams() throws SelectAIException {
        LOGGER.debug("Listing Select AI agent teams available to the current user");
        try {
            return connectionProvider.withConnection(AgentJdbc::fetchAvailableTeams);
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.LIST_TEAMS failed", e);
            throw AgentJdbc.toException("Failed to list available Select AI agent teams", e);
        }
    }

    private List<Team> listTeams(AgentSql query, String pattern) throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                List<Team> teams = new ArrayList<>();
                try (PreparedStatement stmt = connection.prepareStatement(query.get())) {
                    if (pattern != null) {
                        stmt.setString(1, pattern);
                    }
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            String teamName = rows.getString("agent_team_name");
                            teams.add(DefaultTeam.fromMetadata(connectionProvider, teamName,
                                    AgentJdbc.fetchTeamAttributes(connection, teamName), rows.getString("description"),
                                    rows.getString("status")));
                        }
                    }
                }
                return teams;
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to list Select AI agent teams", e);
            throw AgentJdbc.toException("Failed to list Select AI agent teams", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getDefinition(AgentObjectType objectType, String objectName,
                                boolean includeDependentObjects) throws SelectAIException {
        AgentJdbc.requireText(objectName, "objectName");
        String objectTypeValue = AgentJdbc.objectTypeValue(objectType);
        LOGGER.debug("Getting Select AI agent object definition for type {}", objectType);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.GET_DEFINITION.get())) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    stmt.setString(2, objectTypeValue);
                    stmt.setString(3, objectName);
                    AgentJdbc.bindClob(stmt, 4, AgentJdbc.definitionParams(includeDependentObjects));
                    stmt.execute();
                    return AgentJdbc.readClob(stmt.getClob(1));
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.GET_DEFINITION failed for object type {}", objectType, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.GET_DEFINITION", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean importTeam(ImportTeamRequest request) throws SelectAIException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        LOGGER.debug("Importing Select AI agent team {}", request.getTeamName());
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.IMPORT_TEAM.get())) {
                    stmt.setString(1, request.getProfileName());
                    stmt.setString(2, request.getTeamName());
                    AgentJdbc.bindClob(stmt, 3, request.getSpecification());
                    AgentJdbc.bindString(stmt, 4, request.getObjectStorageCredentialName());
                    AgentJdbc.bindString(stmt, 5, request.getLocation());
                    AgentJdbc.bindPlsqlBoolean(stmt, 6, Boolean.TRUE.equals(request.getForce()));
                    stmt.execute();
                    return true;
                }
            });
            LOGGER.info("Imported Select AI agent team {}", request.getTeamName());
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.IMPORT_TEAM failed for team {}", request.getTeamName(), e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.IMPORT_TEAM", e);
        }
    }
}
