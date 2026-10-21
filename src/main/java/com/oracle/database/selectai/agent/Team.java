/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.agent;

import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.ExecutionStateParams;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamDescription;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.SelectAIException;

/**
 * Contract for Select AI agent team lifecycle, orchestration, and definition
 * management operations.
 * <p>
 * A team is a database-side resource managed by the
 * {@code DBMS_CLOUD_AI_AGENT} PL/SQL package. Teams coordinate agents and can
 * run multi-agent workflows using their configured members and attributes.
 * <p>
 * A {@code Team} instance can represent an existing database team or be
 * configured for creation. The name-only {@link SelectAIAgent#team(String)}
 * factory verifies that the team exists and loads its metadata and attributes
 * before returning. {@link SelectAIAgent#listTeams()} also returns handles with
 * loaded metadata and attributes. A handle configured for creation is obtained with
 * {@link SelectAIAgent#team(String, TeamAttributes, String, TeamStatus)}.
 * A granted team owned by another user can be run by its qualified name via
 * {@link SelectAIAgent#runTeam(String, RunTeamRequest)}; that path does not
 * create a {@code Team} handle or expose owner-only metadata.
 * <p>
 * A creation-configured handle retains its supplied attributes, description,
 * and status without querying the database. Only such a handle can call
 * {@link #create()}; after creation succeeds, it becomes database-backed.
 * {@link #enable()}, {@link #disable()}, {@link #drop(boolean)},
 * orchestration, state, and export operations require an existing database-side
 * team.
 * Existing teams can update individual attributes with
 * {@link #setAttribute(String, String)} or multiple attributes with
 * {@link #setAttributes(TeamAttributes)}; both operations reload the typed
 * attributes after the database update succeeds.
 * <p>
 * Before creation, {@link #getStatus()} and {@link #getTeamAttributes()} return
 * the configured values. For a database-backed handle, each call to either
 * method queries the database. {@code getStatus()} does not change the
 * description returned by {@link #getDescription()}. The description is
 * supplied or loaded when the handle is initialized; {@code getDescription()}
 * does not query the database. Methods such as
 * {@link #describe()} and {@link #getStateByConversationId(String)} retrieve
 * other database-side information.
 * <p>
 * Names and required Java arguments are checked locally. Loading metadata and
 * executing team operations require database access; the database checks
 * privileges and validates team attributes when they are submitted. Team
 * execution and state-management payloads must conform to the formats
 * accepted by the database package.
 * <p>
 * For a complete runnable example, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTeamSample.html">
 * CreateTeamSample source</a>.
 * For an end-to-end example that creates the resources needed by a team, runs
 * it, and continues the same database conversation, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/AgentWorkflowSample.html">
 * AgentWorkflowSample source</a>.
 * For a supervisor agent coordinating separate worker-task mappings, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SupervisorTeamSample.html">
 * SupervisorTeamSample source</a>.
 *
 * @see SelectAIAgent
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-agent-package.html">
 *      DBMS_CLOUD_AI_AGENT package documentation</a>
 * @since 1.1.0
 */
public interface Team {
    /**
     * Creates this creation-configured team in the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTeamSample.html">
     * CreateTeamSample source</a>.
     *
     * @return {@code true} when the database creates the team
     * @throws IllegalStateException when this handle was loaded from an existing team
     * @throws SelectAIException when the database rejects or cannot complete the operation
     */
    boolean create() throws SelectAIException;
    /**
     * Enables this database-backed team.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/EnableTeamSample.html">
     * EnableTeamSample source</a>.
     *
     * @return {@code true} when the database enables the team
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean enable() throws SelectAIException;
    /**
     * Disables this database-backed team.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DisableTeamSample.html">
     * DisableTeamSample source</a>.
     *
     * @return {@code true} when the database disables the team
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean disable() throws SelectAIException;
    /**
     * Drops this database-backed team owned by the connected user. When
     * {@code force} is {@code false}, dropping a missing team fails. When
     * {@code force} is {@code true}, a missing-team error is ignored and the
     * operation succeeds. The flag does not bypass authorization or perform a
     * cascading delete.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DropTeamSample.html">
     * DropTeamSample source</a>.
     *
     * @param force database force-drop option
     * @return {@code true} when the drop completes
     * @throws IllegalStateException when {@code force} is {@code false} and
     *         this team has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean drop(boolean force) throws SelectAIException;

    /**
     * Updates one team attribute in the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamAttributeSample.html">
     * SetTeamAttributeSample source</a>.
     *
     * @param attributeName database team attribute name
     * @param attributeValue string value, or {@code null} where supported
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException;

    /**
     * Updates one boolean team attribute in the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamAttributeSample.html">
     * SetTeamAttributeSample source</a>.
     *
     * @param attributeName database team attribute name
     * @param attributeValue boolean value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException;

    /**
     * Updates one integer team attribute in the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamAttributeSample.html">
     * SetTeamAttributeSample source</a>.
     *
     * @param attributeName database team attribute name
     * @param attributeValue integer value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException;

    /**
     * Updates one floating-point team attribute in the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamAttributeSample.html">
     * SetTeamAttributeSample source</a>.
     *
     * @param attributeName database team attribute name
     * @param attributeValue floating-point value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException;

    /**
     * Updates multiple team attributes in one database operation.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamAttributesSample.html">
     * SetTeamAttributesSample source</a>.
     *
     * @param teamAttributes team attributes to update
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code teamAttributes} is null
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttributes(TeamAttributes teamAttributes) throws SelectAIException;

    /**
     * Grants a database user or role permission to run this owner-managed team.
     * The connected database user must own the team. This operation grants
     * execution access to the team as a unit; it does not grant permission to
     * modify the team or independently run its agents, tasks, or tools.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GrantTeamHandleAccessSample.html">
     * GrantTeamHandleAccessSample source</a>.
     *
     * @param userOrRoleName database user or role receiving execution access
     * @return {@code true} when the database accepts the grant
     * @throws IllegalArgumentException when the name is null, blank, invalid,
     *         or {@code PUBLIC}
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database rejects or cannot complete the grant
     * @since 1.1.0
     */
    boolean grantAccess(String userOrRoleName) throws SelectAIException;

    /**
     * Revokes a database user or role permission to run this owner-managed team.
     * Revocation prevents new runs and resumes but does not terminate a run
     * that is already in progress.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RevokeTeamHandleAccessSample.html">
     * RevokeTeamHandleAccessSample source</a>.
     *
     * @param userOrRoleName database user or role losing execution access
     * @return {@code true} when the database accepts the revoke
     * @throws IllegalArgumentException when the name is null, blank, invalid,
     *         or {@code PUBLIC}
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database rejects or cannot complete the revoke
     * @since 1.1.0
     */
    boolean revokeAccess(String userOrRoleName) throws SelectAIException;

    /**
     * Starts or resumes a synchronous team run using the supplied prompt and
     * runtime parameters. Reuse the request's conversation identifier to resume
     * a conversation across database sessions. This method uses an existing
     * database-backed handle; use {@link SelectAIAgent#runTeam(String, RunTeamRequest)}
     * to run a granted team owned by another user. In {@code DataSource} mode,
     * the borrowed JDBC connection can remain occupied for the duration of the
     * workflow. The SDK does not provide cancellation, and an interrupted or
     * timed-out call may not stop server-side work; retry only when the workflow
     * is known to be safe to repeat.
     * Without established session context, provide a database-created
     * conversation identifier in the request; otherwise the database may
     * reject the run with ORA-20053.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunTeamSample.html">
     * RunTeamSample source</a>.
     * For a human-input workflow resumed through a pooled {@code DataSource}
     * using the client-level run API, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/PooledTeamResumeSample.html">
     * PooledTeamResumeSample source</a>.
     *
     * @param request run request; its prompt and parameters may be unset
     * @return typed team result retaining the database response
     * @throws IllegalArgumentException when {@code request} is null
     * @throws SelectAIException when the database operation fails
     */
    TeamRunResult run(RunTeamRequest request) throws SelectAIException;
    /**
     * Returns the database-generated team definition and aggregated capabilities.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DescribeTeamSample.html">
     * DescribeTeamSample source</a>.
     *
     * Additional JSON fields are tolerated; malformed or non-object responses
     * are reported as mapping failures instead of empty metadata.
     *
     * @return typed team description with skills retained as JSON, or
     *         {@code null} when the database returns no value
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database operation or response mapping fails
     */
    TeamDescription describe() throws SelectAIException;
    /**
     * Returns the latest run state for a conversation associated with this team.
     *
     * <p>This convenience method creates the database {@code conversation_id}
     * parameter and is suitable for resuming or inspecting a run across pooled
     * database connections.</p>
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GetOwnedTeamStateSample.html">
     * GetOwnedTeamStateSample source</a>.
     *
     * @param conversationId existing database conversation identifier
     * @return typed team execution state retaining the original database value
     * @throws IllegalArgumentException when {@code conversationId} is null or blank
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database operation fails
     * @since 1.1.0
     */
    TeamExecutionState getStateByConversationId(String conversationId) throws SelectAIException;

    /**
     * Updates the state of a waiting team execution. Use
     * {@link ExecutionStateRequest.Builder#params(ExecutionStateParams)} when
     * the request should expose the typed parameter object explicitly. The
     * {@link ExecutionStateRequest.Builder#conversationId(String)} method is
     * a convenience for the common case.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetOwnedTeamExecutionStateSample.html">
     * SetOwnedTeamExecutionStateSample source</a>.
     *
     * @param request execution state and a typed conversation identifier
     * @return {@code true} when the database accepts the update
     * @throws IllegalArgumentException when {@code request} is null or does not
     *         identify a conversation
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database rejects or cannot complete the operation
     */
    boolean setExecutionState(ExecutionStateRequest request) throws SelectAIException;
    /**
     * Returns the team definition as JSON using the database defaults.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ExportTeamSample.html">
     * ExportTeamSample source</a>.
     *
     * @return exported team definition returned by the database
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database operation fails
     */
    String exportDefinition() throws SelectAIException;

    /**
     * Returns the team definition as JSON using typed export parameters.
     *
     * <p>The currently supported parameter is {@code overwrite}. The database
     * returns the complete exported definition as a JSON string.</p>
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ExportTeamSample.html">
     * ExportTeamSample source</a>.
     *
     * @param params typed export parameters, or {@code null} to use database defaults
     * @return exported team definition returned by the database
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database operation fails
     */
    String exportDefinition(ExportTeamParams params) throws SelectAIException;
    /**
     * Exports this team definition to object storage or a database directory file.
     * Use {@link ExportTeamParams} through {@link ExportTeamRequest} to provide
     * the typed export options supported by the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ExportTeamSample.html">
     * ExportTeamSample source</a>.
     *
     * @param request export destination and optional overwrite configuration
     * @return {@code true} when the export succeeds
     * @throws IllegalArgumentException when {@code request} is null
     * @throws IllegalStateException when this team has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean exportDefinition(ExportTeamRequest request) throws SelectAIException;
    /**
     * Returns the configured or database-reported team name without database access.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTeamSample.html">
     * LoadTeamSample source</a>.
     *
     * @return configured or database-reported team name
     */
    String getTeamName();
    /**
     * Returns the description supplied or loaded when this handle was initialized.
     * This method does not access the database and the value is not changed by
     * {@link #getStatus()}.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTeamSample.html">
     * LoadTeamSample source</a>.
     *
     * @return supplied or loaded team description, or {@code null} when absent
     */
    String getDescription();
    /**
     * Returns the configured status before creation or the current database
     * status for an existing team. A pending team with no configured status
     * returns {@code null}. This method does not change the description.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTeamSample.html">
     * LoadTeamSample source</a>.
     *
     * @return configured or current team status
     * @throws SelectAIException when current team metadata cannot be fetched
     */
    TeamStatus getStatus() throws SelectAIException;
    /**
     * Returns team attributes for this handle.
     * <p>
     * For a database-backed team, this method reloads the attributes from the
     * database on each call and therefore can fail if the resource was deleted.
     * For a creation-configured team, it returns the configured attributes
     * without querying the database.
     * <p>
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTeamSample.html">
     * LoadTeamSample source</a>.
     *
     * @return current team attributes for an existing team, or configured
     *         attributes for a team pending creation
     * @throws SelectAIException when the team no longer exists or current attributes cannot be fetched
     */
    TeamAttributes getTeamAttributes() throws SelectAIException;
}
