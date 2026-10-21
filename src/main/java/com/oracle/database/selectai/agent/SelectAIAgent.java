/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.agent;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.impl.agent.SelectAIAgentFactory;
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
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;

import javax.sql.DataSource;
import java.util.List;

/**
 * Client interface for Oracle Select AI Agent operations.
 * <p>
 * This interface provides access to the agent framework resources managed by
 * {@code DBMS_CLOUD_AI_AGENT}: agents, tasks, tools, and teams. It also
 * provides collection-level operations for listing agents, tasks, tools, and
 * teams, executing teams, inspecting and updating team state, accessing
 * runtime variables, retrieving object definitions, and importing team
 * definitions.
 * <p>
 * Use {@code SelectAIAgent} separately from {@code SelectAI} so agent
 * orchestration and application-facing Select AI profile operations remain
 * explicit. Agent resources may use Select AI profiles, but creating or
 * managing an agent resource does not create or modify a profile.
 * <p>
 * {@code SelectAIAgent} extends {@link AutoCloseable}. Creating a client with
 * {@link #create(DbConnectionConfig)} opens one SDK-owned JDBC connection
 * immediately. Call {@link #close()} when the client is no longer needed to
 * release that connection and its database session.
 * <p>
 * Creating a client with {@link #create(DataSource)} does not obtain a
 * connection immediately. The application owns the {@code DataSource}; the
 * SDK borrows a connection for each database operation and closes that
 * connection after use, returning pooled connections to their pool. Calling
 * {@link #close()} does not close the caller-owned {@code DataSource}. Each
 * operation may therefore use a different pooled connection; applications
 * should not depend on uncommitted transaction or other session state being
 * available to a later operation.
 * <p>
 * The name-only {@link #agent(String)}, {@link #task(String)},
 * {@link #tool(String)}, and {@link #team(String)} factories verify that the
 * current user owns the resources and load their metadata and attributes before returning.
 * Granted cross-schema teams can be run through {@link #runTeam(String, RunTeamRequest)}
 * without loading owner-only metadata.
 * The owner-scoped list methods also load metadata and attributes for each
 * returned handle. {@link #listAvailableTeams()} provides a separate read-only
 * discovery view backed by {@code DBMS_CLOUD_AI_AGENT.LIST_TEAMS}; its results
 * may include shared teams that cannot be opened with {@link #team(String)}.
 * The overloads that accept attributes retain their configuration without a
 * database query; a resource is not persisted until its {@code create()}
 * method succeeds. For agents, required attribute values such as profile name
 * and role are validated by the database during {@link Agent#create()}, not
 * by the {@link AgentAttributes} builder.
 * <p>
 * For a complete runnable example, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateAgentSample.html">
 * CreateAgentSample source</a>.
 * For an end-to-end example that creates a tool, task, agent, and team, runs
 * the team, and continues the same conversation, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/AgentWorkflowSample.html">
 * AgentWorkflowSample source</a>.
 *
 * @see SelectAI
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-agent-package.html">
 *      DBMS_CLOUD_AI_AGENT package documentation</a>
 * @since 1.1.0
 */
public interface SelectAIAgent extends AutoCloseable {
    /**
     * Creates a SelectAIAgent client from database connection configuration.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunTeamSample.html">
     * RunTeamSample source</a>.
     *
     * @param dbConnectionConfig database connection configuration
     * @return SelectAIAgent client
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the database connection cannot be initialized
     */
    static SelectAIAgent create(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        return SelectAIAgentFactory.create(dbConnectionConfig);
    }

    /**
     * Creates a SelectAIAgent client from database connection configuration and
     * SDK execution options.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentWithOptionsSample.html">
     * CreateSelectAIAgentWithOptionsSample source</a>.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options; {@code null} uses defaults
     * @return SelectAIAgent client
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the database connection cannot be initialized
     */
    static SelectAIAgent create(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        return SelectAIAgentFactory.create(dbConnectionConfig, options);
    }

    /**
     * Creates a DataSource-backed SelectAIAgent client.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentDataSourceSample.html">
     * CreateSelectAIAgentDataSourceSample source</a>.
     *
     * @param dataSource DataSource used to obtain JDBC connections
     * @return SelectAIAgent client
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    static SelectAIAgent create(DataSource dataSource) {
        return SelectAIAgentFactory.create(dataSource);
    }

    /**
     * Creates a DataSource-backed SelectAIAgent client with SDK execution options.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentDataSourceWithOptionsSample.html">
     * CreateSelectAIAgentDataSourceWithOptionsSample source</a>.
     *
     * @param dataSource DataSource used to obtain JDBC connections
     * @param options SDK execution options; {@code null} uses defaults
     * @return SelectAIAgent client
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    static SelectAIAgent create(DataSource dataSource, SelectAIOptions options) {
        return SelectAIAgentFactory.create(dataSource, options);
    }

    /**
     * Loads an existing agent, including its metadata and attributes.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadAgentSample.html">
     * LoadAgentSample source</a>.
     *
     * @param agentName agent name
     * @return database-backed agent object
     * @throws IllegalArgumentException when {@code agentName} is null or blank
     * @throws SelectAIException when the agent does not exist or its metadata or attributes cannot be loaded
     */
    Agent agent(String agentName) throws SelectAIException;

    /**
     * Initializes an agent object for explicit creation.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateAgentSample.html">
     * CreateAgentSample source</a>.
     *
     * @param agentName agent name
     * @param attributes agent creation attributes
     * @param description optional agent description
     * @param status optional initial status; {@code null} uses the database default
     * @return agent object configured for creation
     * @throws IllegalArgumentException when {@code agentName} is null or blank,
     *         or {@code attributes} is null
     */
    Agent agent(String agentName, AgentAttributes attributes, String description, AgentStatus status);

    /**
     * Loads an existing task, including its metadata and attributes.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTaskSample.html">
     * LoadTaskSample source</a>.
     *
     * @param taskName task name
     * @return database-backed task object
     * @throws IllegalArgumentException when {@code taskName} is null or blank
     * @throws SelectAIException when the task does not exist or its metadata or attributes cannot be loaded
     */
    Task task(String taskName) throws SelectAIException;

    /**
     * Initializes a task object for explicit creation.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTaskSample.html">
     * CreateTaskSample source</a>.
     *
     * @param taskName task name
     * @param attributes task creation attributes
     * @param description optional task description
     * @param status optional initial status; {@code null} uses the database default
     * @return task object configured for creation
     * @throws IllegalArgumentException when {@code taskName} is null or blank,
     *         or {@code attributes} is null
     */
    Task task(String taskName, TaskAttributes attributes, String description, TaskStatus status);

    /**
     * Loads an existing tool, including its metadata and attributes.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     * LoadToolSample source</a>.
     *
     * @param toolName tool name
     * @return database-backed tool object
     * @throws IllegalArgumentException when {@code toolName} is null or blank
     * @throws SelectAIException when the tool does not exist or its metadata or attributes cannot be loaded
     */
    Tool tool(String toolName) throws SelectAIException;

    /**
     * Initializes a tool object for explicit creation.
     * <p>
     * For runnable examples, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
     * CreateToolSample source</a>,
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSqlToolSample.html">
     * CreateSqlToolSample source</a>, and
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     * ToolParamsSample source</a>.
     *
     * @param toolName tool name
     * @param attributes tool creation attributes
     * @param description optional tool description
     * @param status optional initial status; {@code null} uses the database default
     * @return tool object configured for creation
     * @throws IllegalArgumentException when {@code toolName} is null or blank,
     *         or {@code attributes} is null
     */
    Tool tool(String toolName, ToolAttributes attributes, String description, ToolStatus status);

    /**
     * Loads a team owned by the current database user, including its metadata
     * and attributes. To run a granted team owned by another user, use
     * {@link #runTeam(String, RunTeamRequest)} with an owner-qualified name.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTeamSample.html">
     * LoadTeamSample source</a>.
     *
     * @param teamName team name
     * @return database-backed team object
     * @throws IllegalArgumentException when {@code teamName} is null or blank
     * @throws SelectAIException when the team does not exist or its metadata or attributes cannot be loaded
     */
    Team team(String teamName) throws SelectAIException;

    /**
     * Initializes a team object for explicit creation.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTeamSample.html">
     * CreateTeamSample source</a>.
     *
     * @param teamName team name
     * @param attributes team creation attributes
     * @param description optional team description
     * @param status optional initial status; {@code null} uses the database default
     * @return team object configured for creation
     * @throws IllegalArgumentException when {@code teamName} is null or blank,
     *         or {@code attributes} is null
     */
    Team team(String teamName, TeamAttributes attributes, String description, TeamStatus status);

    /**
     * Runs an existing team by name without loading owner-only team metadata.
     * A consumer can run a team granted by another database user with a name
     * such as {@code TEAM_OWNER.SHARED_SUPPORT_TEAM}. The database checks team
     * existence and the caller's access; this method does not grant access to
     * manage the team's definition.
     * Without established session context, the request must contain a
     * database-created conversation identifier. The operation is synchronous.
     * In {@code DataSource} mode, the borrowed JDBC connection can remain
     * occupied for the duration of the workflow. The SDK does not provide a
     * cancellation API, and a JDBC timeout or interrupted call may not stop
     * server-side or provider work. Retry only when the workflow is known to
     * be safe to repeat.
     *
     * <p>For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunSharedTeamSample.html">
     * RunSharedTeamSample source</a>.</p>
     * <p>For a human-input workflow resumed in a new process through a pooled
     * {@code DataSource}, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/PooledTeamResumeSample.html">
     * PooledTeamResumeSample source</a>.</p>
     *
     * @param teamName team name, optionally owner-qualified
     * @param request prompt and optional runtime parameters
     * @return typed team-run result retaining the database response
     * @throws IllegalArgumentException when {@code teamName} is null or blank,
     *         or {@code request} is null
     * @throws SelectAIException when the database rejects or cannot complete the run
     */
    TeamRunResult runTeam(String teamName, RunTeamRequest request) throws SelectAIException;

    /**
     * Returns the latest state for a team run identified by a database
     * conversation identifier.
     *
     * <p>This operation does not load a {@link Team} handle and supports an
     * owner-qualified team name for a shared team.</p>
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GetTeamStateSample.html">
     * GetTeamStateSample source</a>.
     * For a runnable example that verifies {@code WAITING_FOR_HUMAN} before
     * resuming through a pooled {@code DataSource}, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/PooledTeamResumeSample.html">
     * PooledTeamResumeSample source</a>.
     *
     * @param teamName team name, optionally owner-qualified
     * @param conversationId existing database conversation identifier
     * @return typed team execution state retaining the original database value
     * @throws IllegalArgumentException when either argument is null or blank
     * @throws SelectAIException when the database operation fails
     * @since 1.1.0
     */
    TeamExecutionState getTeamStateByConversationId(String teamName, String conversationId)
            throws SelectAIException;

    /**
     * Updates the execution state of a team run.
     *
     * <p>This operation does not load a {@link Team} handle and therefore also
     * supports an owner-qualified team name for a team granted to the current
     * database user. Set the request's conversation identifier to address a
     * run across pooled database connections.</p>
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamExecutionStateSample.html">
     * SetTeamExecutionStateSample source</a>.
     *
     * @param teamName team name, optionally owner-qualified
     * @param request execution state and a typed conversation identifier
     * @return {@code true} when the database operation succeeds
     * @throws IllegalArgumentException when {@code teamName} is null or blank,
     *         or {@code request} is null or does not identify a conversation
     * @throws SelectAIException when the database operation fails
     */
    boolean setTeamExecutionState(String teamName, ExecutionStateRequest request)
            throws SelectAIException;

    /**
     * Grants a database user or role permission to run an owner-managed team.
     * The connected database user must own the team, and {@code teamName} must
     * be the owner's unqualified team name. This method does not load a
     * {@link Team} handle and is useful when the owner wants a client-level
     * sharing operation.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GrantTeamAccessSample.html">
     * GrantTeamAccessSample source</a>.
     *
     * @param teamName owner's unqualified team name
     * @param userOrRoleName database user or role receiving execution access
     * @return {@code true} when the database accepts the grant
     * @throws IllegalArgumentException when either name is null, blank, invalid,
     *         or the grantee is {@code PUBLIC}
     * @throws SelectAIException when the database rejects or cannot complete the grant
     * @since 1.1.0
     */
    boolean grantTeamAccess(String teamName, String userOrRoleName) throws SelectAIException;

    /**
     * Revokes a database user or role permission to run an owner-managed team.
     * The connected database user must own the team, and {@code teamName} must
     * be the owner's unqualified team name.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RevokeTeamAccessSample.html">
     * RevokeTeamAccessSample source</a>.
     *
     * @param teamName owner's unqualified team name
     * @param userOrRoleName database user or role losing execution access
     * @return {@code true} when the database accepts the revoke
     * @throws IllegalArgumentException when either name is null, blank, invalid,
     *         or the grantee is {@code PUBLIC}
     * @throws SelectAIException when the database rejects or cannot complete the revoke
     * @since 1.1.0
     */
    boolean revokeTeamAccess(String teamName, String userOrRoleName) throws SelectAIException;

    /**
     * Reads an ephemeral runtime variable from the current database session.
     *
     * <p>Runtime variables are session-scoped. In {@code DataSource} mode,
     * separate calls may use different pooled connections, so applications
     * that require the value to remain available across calls should use a
     * connection mode that retains one database session or manage the session
     * explicitly.</p>
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GetVariableSample.html">
     * GetVariableSample source</a>.
     *
     * @param namespace runtime-variable namespace, such as {@code state_variables}
     * @param name runtime-variable key
     * @return the variable value, or {@code null} when the key is not set
     * @throws IllegalArgumentException when {@code namespace} or {@code name} is null or blank
     * @throws SelectAIException when the database operation fails
     * @since 1.1.0
     */
    default String getVariable(String namespace, String name) throws SelectAIException {
        return getVariable(namespace, name, "ephemeral");
    }

    /**
     * Reads a runtime variable from the current database session and scope.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GetVariableSample.html">
     * GetVariableSample source</a>.
     *
     * @param namespace runtime-variable namespace, such as {@code state_variables}
     * @param name runtime-variable key
     * @param scope runtime-variable scope; the database currently supports {@code ephemeral}
     * @return the variable value, or {@code null} when the key is not set
     * @throws IllegalArgumentException when an argument is null or blank
     * @throws SelectAIException when the database operation fails
     * @since 1.1.0
     */
    String getVariable(String namespace, String name, String scope) throws SelectAIException;

    /**
     * Sets or clears an ephemeral runtime variable in the current database session.
     * A {@code null} value clears the variable.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetVariableSample.html">
     * SetVariableSample source</a>.
     *
     * @param namespace runtime-variable namespace, such as {@code state_variables}
     * @param name runtime-variable key
     * @param value value to store, or {@code null} to clear the key
     * @return {@code true} when the database accepts the operation
     * @throws IllegalArgumentException when {@code namespace} or {@code name} is null or blank
     * @throws SelectAIException when the database operation fails
     * @since 1.1.0
     */
    default boolean setVariable(String namespace, String name, String value) throws SelectAIException {
        return setVariable(namespace, name, value, "ephemeral");
    }

    /**
     * Sets or clears a runtime variable in the current database session and scope.
     * A {@code null} value clears the variable.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetVariableSample.html">
     * SetVariableSample source</a>.
     *
     * @param namespace runtime-variable namespace, such as {@code state_variables}
     * @param name runtime-variable key
     * @param value value to store, or {@code null} to clear the key
     * @param scope runtime-variable scope; the database currently supports {@code ephemeral}
     * @return {@code true} when the database accepts the operation
     * @throws IllegalArgumentException when an argument is null or blank, except {@code value}
     * @throws SelectAIException when the database operation fails
     * @since 1.1.0
     */
    boolean setVariable(String namespace, String name, String value, String scope)
            throws SelectAIException;

    /**
     * Lists agents owned by the current database user.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListAgentsSample.html">
     * ListAgentsSample source</a>.
     *
     * @return agent handles initialized from {@code USER_AI_AGENTS} metadata
     *         and their current database attributes
     * @throws SelectAIException when agent metadata or attributes cannot be loaded
     */
    List<Agent> listAgents() throws SelectAIException;

    /**
     * Lists owned agents whose names match a database regular-expression pattern.
     * Matching is case-insensitive. Each returned handle includes metadata and
     * attributes loaded from the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListAgentsSample.html">
     * ListAgentsSample source</a>.
     *
     * @param agentNamePattern regular-expression pattern matched against agent names
     * @return matching agent handles
     * @throws IllegalArgumentException when {@code agentNamePattern} is null or blank
     * @throws SelectAIException when listing agents fails
     */
    List<Agent> listAgents(String agentNamePattern) throws SelectAIException;

    /**
     * Lists tasks owned by the current database user.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListTasksSample.html">
     * ListTasksSample source</a>.
     *
     * @return task handles initialized from {@code USER_AI_AGENT_TASKS} metadata
     *         and their current database attributes
     * @throws SelectAIException when task metadata or attributes cannot be loaded
     */
    List<Task> listTasks() throws SelectAIException;

    /**
     * Lists owned tasks whose names match a database regular-expression pattern.
     * Matching is case-insensitive. Each returned handle includes metadata and
     * attributes loaded from the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListTasksSample.html">
     * ListTasksSample source</a>.
     *
     * @param taskNamePattern regular-expression pattern matched against task names
     * @return matching task handles
     * @throws IllegalArgumentException when {@code taskNamePattern} is null or blank
     * @throws SelectAIException when listing tasks fails
     */
    List<Task> listTasks(String taskNamePattern) throws SelectAIException;

    /**
     * Lists tools owned by the current database user.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListToolsSample.html">
     * ListToolsSample source</a>.
     *
     * @return tool handles initialized from {@code USER_AI_AGENT_TOOLS} metadata
     *         and their current database attributes
     * @throws SelectAIException when tool metadata or attributes cannot be loaded
     */
    List<Tool> listTools() throws SelectAIException;

    /**
     * Lists owned tools whose names match a database regular-expression pattern.
     * Matching is case-insensitive. Each returned handle includes metadata and
     * attributes loaded from the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListToolsSample.html">
     * ListToolsSample source</a>.
     *
     * @param toolNamePattern regular-expression pattern matched against tool names
     * @return matching tool handles
     * @throws IllegalArgumentException when {@code toolNamePattern} is null or blank
     * @throws SelectAIException when listing tools fails
     */
    List<Tool> listTools(String toolNamePattern) throws SelectAIException;

    /**
     * Lists agent teams owned by the current database user.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListAgentTeamsSample.html">
     * ListAgentTeamsSample source</a>.
     *
     * @return team handles initialized from {@code USER_AI_AGENT_TEAMS}
     *         metadata and their current database attributes
     * @throws SelectAIException when team metadata or attributes cannot be loaded
     */
    List<Team> listTeams() throws SelectAIException;

    /**
     * Lists owned teams whose names match a database regular-expression pattern.
     * Matching is case-insensitive. Each returned handle includes metadata and
     * attributes loaded from the database. Granted cross-schema teams are not
     * returned; use {@link #runTeam(String, RunTeamRequest)} to run those teams.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListAgentTeamsSample.html">
     * ListAgentTeamsSample source</a>.
     *
     * @param teamNamePattern regular-expression pattern matched against team names
     * @return matching team handles
     * @throws IllegalArgumentException when {@code teamNamePattern} is null or blank
     * @throws SelectAIException when listing teams fails
     */
    List<Team> listTeams(String teamNamePattern) throws SelectAIException;

    /**
     * Lists teams available to the calling database user for discovery.
     * <p>
     * The returned objects are read-only discovery
     * snapshots, not mutable {@link Team} lifecycle handles.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListAvailableTeamsSample.html">
     * ListAvailableTeamsSample source</a>.
     *
     * @return available team discovery snapshots
     * @throws SelectAIException when the database discovery operation or its
     *         JSON response cannot be processed
     */
    List<AvailableTeam> listAvailableTeams() throws SelectAIException;

    /**
     * Returns the definition produced by
     * {@code DBMS_CLOUD_AI_AGENT.GET_DEFINITION} for an agent framework object.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GetDefinitionSample.html">
     * GetDefinitionSample source</a>.
     *
     * @param objectType object type to describe
     * @param objectName agent, task, tool, or team name
     * @param includeDependentObjects whether dependent objects should be included
     * @return definition returned by the database package
     * @throws IllegalArgumentException when {@code objectType} is null or
     *         {@code objectName} is null or blank
     * @throws SelectAIException when the database operation fails
     */
    String getDefinition(AgentObjectType objectType, String objectName,
                         boolean includeDependentObjects) throws SelectAIException;

    /**
     * Imports an agent team definition into the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ImportTeamSample.html">
     * ImportTeamSample source</a>.
     *
     * @param request import request
     * @return {@code true} when import succeeds
     * @throws IllegalArgumentException when {@code request} is null
     * @throws SelectAIException when import fails
     */
    boolean importTeam(ImportTeamRequest request) throws SelectAIException;

    /**
     * Releases resources owned by this client.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSelectAIAgentWithOptionsSample.html">
     * CreateSelectAIAgentWithOptionsSample source</a>.
     *
     * @throws SelectAIException when an owned JDBC connection cannot be closed
     */
    @Override
    void close() throws SelectAIException;
}
