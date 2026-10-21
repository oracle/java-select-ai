/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.agent;

import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolRunResult;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.SelectAIException;

/**
 * Contract for Select AI agent tool lifecycle, description, and execution
 * operations.
 * <p>
 * A tool is a database-side resource managed by the
 * {@code DBMS_CLOUD_AI_AGENT} PL/SQL package. Tools expose callable
 * functionality to agents or teams and may define an input schema.
 * <p>
 * A {@code Tool} instance can represent an existing database tool or be
 * configured for creation. The name-only {@link SelectAIAgent#tool(String)}
 * factory verifies that the tool exists and loads its metadata and attributes
 * before returning. {@link SelectAIAgent#listTools()} also returns handles with
 * loaded metadata and attributes. A handle configured for creation is obtained with
 * {@link SelectAIAgent#tool(String, ToolAttributes, String, ToolStatus)}.
 * <p>
 * A creation-configured handle retains its supplied attributes, description,
 * and status without querying the database. Only such a handle can call
 * {@link #create()}; after creation succeeds, it becomes database-backed.
 * {@link #enable()}, {@link #disable()}, {@link #drop(boolean)},
 * {@link #describe()}, and {@link #run(String)} require an existing
 * database-side tool.
 * Existing tools can update individual attributes with
 * {@link #setAttribute(String, String)} or multiple attributes with
 * {@link #setAttributes(ToolAttributes)}; both operations reload the typed
 * attributes after the database update succeeds.
 * <p>
 * Before creation, {@link #getStatus()} and {@link #getToolAttributes()} return
 * the configured values. For a database-backed handle, each call to either
 * method queries the database. {@code getStatus()} does not change the
 * description returned by {@link #getDescription()}. The description is
 * supplied or loaded when the handle is initialized; {@code getDescription()}
 * does not query the database. Use {@link #describe()} to retrieve
 * the database-generated tool definition.
 * <p>
 * Names and required Java arguments are checked locally. Loading metadata and
 * executing tool operations require database access; the database checks
 * privileges and validates tool attributes when they are submitted. The input
 * supplied to {@link #run(String)} must be a JSON payload accepted by the
 * configured tool; the Java method checks only that it is not null or blank.
 * <p>
 * For a complete runnable example, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
 * CreateToolSample source</a>.
 *
 * @see SelectAIAgent
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-agent-package.html">
 *      DBMS_CLOUD_AI_AGENT package documentation</a>
 * @since 1.1.0
 */
public interface Tool {
    /**
     * Creates this creation-configured tool in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
     * CreateToolSample source</a>.
     *
     * @return {@code true} when the database creates the tool
     * @throws IllegalStateException when this handle was loaded from an existing tool
     * @throws SelectAIException when the database rejects or cannot complete the operation
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSqlToolSample.html">
     *      CreateSqlToolSample source</a>
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
     *      CreateToolWithInputsSample source</a>
     */
    boolean create() throws SelectAIException;
    /**
     * Enables this database-backed tool.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/EnableToolSample.html">
     * EnableToolSample source</a>.
     *
     * @return {@code true} when the database enables the tool
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean enable() throws SelectAIException;
    /**
     * Disables this database-backed tool.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DisableToolSample.html">
     * DisableToolSample source</a>.
     *
     * @return {@code true} when the database disables the tool
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean disable() throws SelectAIException;
    /**
     * Drops this database-backed tool. When {@code force} is {@code true},
     * the operation may be retried after the handle has already been dropped;
     * the database force-drop semantics determine the result.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DropToolSample.html">
     * DropToolSample source</a>.
     *
     * @param force database force-drop option
     * @return {@code true} when the drop completes
     * @throws IllegalStateException when {@code force} is {@code false} and
     *         this tool has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean drop(boolean force) throws SelectAIException;

    /**
     * Updates one tool attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolAttributeSample.html">
     * SetToolAttributeSample source</a>.
     *
     * @param attributeName database tool attribute name
     * @param attributeValue string value, or {@code null} where supported
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException;

    /**
     * Updates one boolean tool attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolAttributeSample.html">
     * SetToolAttributeSample source</a>.
     *
     * @param attributeName database tool attribute name
     * @param attributeValue boolean value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException;

    /**
     * Updates one integer tool attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolAttributeSample.html">
     * SetToolAttributeSample source</a>.
     *
     * @param attributeName database tool attribute name
     * @param attributeValue integer value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException;

    /**
     * Updates one floating-point tool attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolAttributeSample.html">
     * SetToolAttributeSample source</a>.
     *
     * @param attributeName database tool attribute name
     * @param attributeValue floating-point value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException;

    /**
     * Updates multiple tool attributes in one database operation.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolAttributesSample.html">
     * SetToolAttributesSample source</a>.
     *
     * @param toolAttributes tool attributes to update
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code toolAttributes} is null
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database update fails
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetSqlToolParamsSample.html">
     *      SetSqlToolParamsSample source</a>
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolInputsSample.html">
     *      SetToolInputsSample source</a>
     */
    boolean setAttributes(ToolAttributes toolAttributes) throws SelectAIException;

    /**
     * Returns the database-generated tool definition and input metadata as raw JSON.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DescribeToolSample.html">
     * DescribeToolSample source</a>.
     *
     * @return JSON returned by the database package
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database operation fails
     */
    String describe() throws SelectAIException;
    /**
     * Runs this tool with a JSON input payload.
     * <p>
     * The SDK checks only that the payload is non-null and non-blank. The
     * configured tool and database validate the JSON shape and its values.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunToolSample.html">
     * RunToolSample source</a>.
     *
     * @param inputJson JSON input accepted by the configured tool
     * @return typed result retaining the raw value returned by the database package
     * @throws IllegalArgumentException when {@code inputJson} is null or blank
     * @throws IllegalStateException when this tool has not been created
     * @throws SelectAIException when the database operation fails
     */
    ToolRunResult run(String inputJson) throws SelectAIException;
    /**
     * Returns the configured or database-reported tool name without database access.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     * LoadToolSample source</a>.
     *
     * @return configured or database-reported tool name
     */
    String getToolName();
    /**
     * Returns the description supplied or loaded when this handle was initialized.
     * This method does not access the database and the value is not changed by
     * {@link #getStatus()}.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     * LoadToolSample source</a>.
     *
     * @return supplied or loaded tool description, or {@code null} when absent
     */
    String getDescription();
    /**
     * Returns the configured status before creation or the current database
     * status for an existing tool. A pending tool with no configured status
     * returns {@code null}. This method does not change the description.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     * LoadToolSample source</a>.
     *
     * @return configured or current tool status
     * @throws SelectAIException when current tool metadata cannot be fetched
     */
    ToolStatus getStatus() throws SelectAIException;
    /**
     * Returns tool attributes for this handle.
     * <p>
     * For a database-backed tool, this method reloads the attributes from the
     * database on each call and therefore can fail if the resource was deleted.
     * For a creation-configured tool, it returns the configured attributes
     * without querying the database.
     * Built-in tool parameters use immutable
     * {@link com.oracle.database.selectai.model.agent.ToolParams} models.
     * Input metadata is exposed as immutable
     * {@link com.oracle.database.selectai.model.agent.ToolInput} entries.
     * Unknown parameter fields and newer database schemas are preserved.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     * LoadToolSample source</a>.
     *
     * @return current tool attributes for an existing tool, or configured
     *         attributes for a tool pending creation
     * @throws SelectAIException when the tool no longer exists or current attributes cannot be fetched
     */
    ToolAttributes getToolAttributes() throws SelectAIException;
}
