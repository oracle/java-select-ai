/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.agent;

import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.SelectAIException;

/**
 * Contract for Select AI agent lifecycle management and metadata access.
 * <p>
 * A Select AI agent is a database-side resource managed by the
 * {@code DBMS_CLOUD_AI_AGENT} PL/SQL package. Agents can use a configured
 * Select AI profile and may participate in task, tool, and team orchestration.
 * <p>
 * An {@code Agent} instance can represent an existing database agent or be
 * configured for creation. The name-only {@link SelectAIAgent#agent(String)}
 * factory verifies that the agent exists and loads its metadata and attributes
 * before returning. {@link SelectAIAgent#listAgents()} also returns handles with
 * loaded metadata and attributes. A handle configured for creation is obtained with
 * {@link SelectAIAgent#agent(String, AgentAttributes, String, AgentStatus)}.
 * <p>
 * A creation-configured handle retains its supplied attributes, description,
 * and status without querying the database. Only such a handle can call
 * {@link #create()}; after creation succeeds, it becomes database-backed.
 * {@link #enable()}, {@link #disable()}, and {@link #drop(boolean)} require an
 * existing agent and operate on the database-side resource.
 * <p>
 * Before creation, {@link #getStatus()} and {@link #getAgentAttributes()} return
 * the configured values. For a database-backed handle, each call to either
 * method queries the database. {@code getStatus()} does not change the
 * description returned by {@link #getDescription()}. The description is
 * supplied or loaded when the handle is initialized; {@code getDescription()}
 * does not query the database.
 * <p>
 * Agent names and a non-null attributes object are checked locally. Required
 * values within {@link AgentAttributes}, including the profile name and role,
 * are validated by the database during {@link #create()}. Loading metadata
 * and executing lifecycle operations require database access and privileges.
 * <p>
 * For a complete runnable example, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateAgentSample.html">
 * CreateAgentSample source</a>.
 *
 * @see SelectAIAgent
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-agent-package.html">
 *      DBMS_CLOUD_AI_AGENT package documentation</a>
 * @since 1.1.0
 */
public interface Agent {
    /**
     * Creates this creation-configured agent in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateAgentSample.html">
     * CreateAgentSample source</a>.
     *
     * @return {@code true} when the database creates the agent
     * @throws IllegalStateException when this handle was loaded from an existing agent
     * @throws SelectAIException when the database rejects or cannot complete the operation
     */
    boolean create() throws SelectAIException;
    /**
     * Enables this database-backed agent.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/EnableAgentSample.html">
     * EnableAgentSample source</a>.
     *
     * @return {@code true} when the database enables the agent
     * @throws IllegalStateException when this agent has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean enable() throws SelectAIException;
    /**
     * Disables this database-backed agent.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DisableAgentSample.html">
     * DisableAgentSample source</a>.
     *
     * @return {@code true} when the database disables the agent
     * @throws IllegalStateException when this agent has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean disable() throws SelectAIException;
    /**
     * Drops this database-backed agent. When {@code force} is {@code true},
     * the operation may be retried after the handle has already been dropped;
     * the database force-drop semantics determine the result.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DropAgentSample.html">
     * DropAgentSample source</a>.
     *
     * @param force database force-drop option
     * @return {@code true} when the drop completes
     * @throws IllegalStateException when {@code force} is {@code false} and
     *         this agent has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean drop(boolean force) throws SelectAIException;

    /**
     * Updates one agent attribute in the database.
     * <p>
     * The database validates the attribute name and value for the selected
     * database release. A {@code null} string value is passed to the database
     * as SQL {@code NULL}; whether that clears an attribute is database-defined.
     *
     * @param attributeName database agent attribute name
     * @param attributeValue string value, or {@code null} where supported
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this agent has not been created
     * @throws SelectAIException when the database update fails
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetAgentAttributeSample.html">
     *      SetAgentAttributeSample source</a>
     */
    boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException;

    /**
     * Updates one boolean agent attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetAgentAttributeSample.html">
     * SetAgentAttributeSample source</a>.
     *
     * @param attributeName database agent attribute name
     * @param attributeValue boolean value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this agent has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException;

    /**
     * Updates one integer agent attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetAgentAttributeSample.html">
     * SetAgentAttributeSample source</a>.
     *
     * @param attributeName database agent attribute name
     * @param attributeValue integer value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this agent has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException;

    /**
     * Updates one floating-point agent attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetAgentAttributeSample.html">
     * SetAgentAttributeSample source</a>.
     *
     * @param attributeName database agent attribute name
     * @param attributeValue floating-point value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this agent has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException;

    /**
     * Updates multiple agent attributes in one database operation.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetAgentAttributesSample.html">
     * SetAgentAttributesSample source</a>.
     * <p>
     * The payload is serialized as the JSON document accepted by
     * {@code DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES}. Values omitted from the
     * payload are handled according to the database package semantics.
     *
     * @param agentAttributes agent attributes to update
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code agentAttributes} is null
     * @throws IllegalStateException when this agent has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttributes(AgentAttributes agentAttributes) throws SelectAIException;

    /**
     * Returns the database-reported name for a loaded agent, or the configured
     * name before creation. This method does not access the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadAgentSample.html">
     * LoadAgentSample source</a>.
     *
     * @return configured or database-reported agent name
     */
    String getAgentName();
    /**
     * Returns the description supplied or loaded when this handle was initialized.
     * This method does not access the database and the value is not changed by
     * {@link #getStatus()}.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadAgentSample.html">
     * LoadAgentSample source</a>.
     *
     * @return supplied or loaded agent description, or {@code null} when absent
     */
    String getDescription();
    /**
     * Returns the configured status before creation or the current database
     * status for an existing agent. A pending agent with no configured status
     * returns {@code null}. This method does not change the description.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadAgentSample.html">
     * LoadAgentSample source</a>.
     *
     * @return configured or current agent status
     * @throws SelectAIException when current agent metadata cannot be fetched
     */
    AgentStatus getStatus() throws SelectAIException;
    /**
     * Returns agent attributes for this handle.
     * <p>
     * For a database-backed agent, this method reloads the attributes from the
     * database on each call and therefore can fail if the resource was deleted.
     * For a creation-configured agent, it returns the configured attributes
     * without querying the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadAgentSample.html">
     * LoadAgentSample source</a>.
     *
     * @return current agent attributes for an existing agent, or configured
     *         attributes for an agent pending creation
     * @throws SelectAIException when the agent no longer exists or current attributes cannot be fetched
     */
    AgentAttributes getAgentAttributes() throws SelectAIException;
}
