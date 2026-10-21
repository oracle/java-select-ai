/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.agent;

import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.SelectAIException;

/**
 * Contract for Select AI agent task lifecycle management and metadata access.
 * <p>
 * A task is a database-side resource managed by the
 * {@code DBMS_CLOUD_AI_AGENT} PL/SQL package. Tasks define work that can be
 * assigned to or coordinated by an agent team.
 * <p>
 * A {@code Task} instance can represent an existing database task or be
 * configured for creation. The name-only {@link SelectAIAgent#task(String)}
 * factory verifies that the task exists and loads its metadata and attributes
 * before returning. {@link SelectAIAgent#listTasks()} also returns handles with
 * loaded metadata and attributes. A handle configured for creation is obtained with
 * {@link SelectAIAgent#task(String, TaskAttributes, String, TaskStatus)}.
 * <p>
 * A creation-configured handle retains its supplied attributes, description,
 * and status without querying the database. Only such a handle can call
 * {@link #create()}; after creation succeeds, it becomes database-backed.
 * {@link #enable()}, {@link #disable()}, and {@link #drop(boolean)} require an
 * existing task and operate on the database-side resource.
 * Existing tasks can update individual attributes with {@link #setAttribute(String, String)}
 * or multiple attributes with {@link #setAttributes(TaskAttributes)}; both
 * operations reload the typed attributes after the database update succeeds.
 * <p>
 * Before creation, {@link #getStatus()} and {@link #getTaskAttributes()} return
 * the configured values. For a database-backed handle, each call to either
 * method queries the database. {@code getStatus()} does not change the
 * description returned by {@link #getDescription()}. The description is
 * supplied or loaded when the handle is initialized; {@code getDescription()}
 * does not query the database.
 * <p>
 * Names and required Java arguments are checked locally. Loading metadata and
 * executing lifecycle operations require database access; the database checks
 * privileges and validates task attributes when they are submitted.
 * <p>
 * For a complete runnable example, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTaskSample.html">
 * CreateTaskSample source</a>.
 *
 * @see SelectAIAgent
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-agent-package.html">
 *      DBMS_CLOUD_AI_AGENT package documentation</a>
 * @since 1.1.0
 */
public interface Task {
    /**
     * Creates this creation-configured task in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTaskSample.html">
     * CreateTaskSample source</a>.
     *
     * @return {@code true} when the database creates the task
     * @throws IllegalStateException when this handle was loaded from an existing task
     * @throws SelectAIException when the database rejects or cannot complete the operation
     */
    boolean create() throws SelectAIException;
    /**
     * Enables this database-backed task.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/EnableTaskSample.html">
     * EnableTaskSample source</a>.
     *
     * @return {@code true} when the database enables the task
     * @throws IllegalStateException when this task has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean enable() throws SelectAIException;
    /**
     * Disables this database-backed task.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DisableTaskSample.html">
     * DisableTaskSample source</a>.
     *
     * @return {@code true} when the database disables the task
     * @throws IllegalStateException when this task has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean disable() throws SelectAIException;
    /**
     * Drops this database-backed task. When {@code force} is {@code true},
     * the operation may be retried after the handle has already been dropped;
     * the database force-drop semantics determine the result.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DropTaskSample.html">
     * DropTaskSample source</a>.
     *
     * @param force database force-drop option
     * @return {@code true} when the drop completes
     * @throws IllegalStateException when {@code force} is {@code false} and
     *         this task has not been created
     * @throws SelectAIException when the database operation fails
     */
    boolean drop(boolean force) throws SelectAIException;

    /**
     * Updates one task attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTaskAttributeSample.html">
     * SetTaskAttributeSample source</a>.
     *
     * @param attributeName database task attribute name
     * @param attributeValue string value, or {@code null} where supported
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this task has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException;

    /**
     * Updates one boolean task attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTaskAttributeSample.html">
     * SetTaskAttributeSample source</a>.
     *
     * @param attributeName database task attribute name
     * @param attributeValue boolean value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank
     * @throws IllegalStateException when this task has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException;

    /**
     * Updates one integer task attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTaskAttributeSample.html">
     * SetTaskAttributeSample source</a>.
     *
     * @param attributeName database task attribute name
     * @param attributeValue integer value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this task has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException;

    /**
     * Updates one floating-point task attribute in the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTaskAttributeSample.html">
     * SetTaskAttributeSample source</a>.
     *
     * @param attributeName database task attribute name
     * @param attributeValue floating-point value
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code attributeName} is null or blank,
     *         or {@code attributeValue} is null
     * @throws IllegalStateException when this task has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException;

    /**
     * Updates multiple task attributes in one database operation.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTaskAttributesSample.html">
     * SetTaskAttributesSample source</a>.
     *
     * @param taskAttributes task attributes to update
     * @return {@code true} when the update succeeds
     * @throws IllegalArgumentException when {@code taskAttributes} is null
     * @throws IllegalStateException when this task has not been created
     * @throws SelectAIException when the database update fails
     */
    boolean setAttributes(TaskAttributes taskAttributes) throws SelectAIException;

    /**
     * Returns the configured or database-reported task name without database access.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTaskSample.html">
     * LoadTaskSample source</a>.
     *
     * @return configured or database-reported task name
     */
    String getTaskName();
    /**
     * Returns the description supplied or loaded when this handle was initialized.
     * This method does not access the database and the value is not changed by
     * {@link #getStatus()}.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTaskSample.html">
     * LoadTaskSample source</a>.
     *
     * @return supplied or loaded task description, or {@code null} when absent
     */
    String getDescription();
    /**
     * Returns the configured status before creation or the current database
     * status for an existing task. A pending task with no configured status
     * returns {@code null}. This method does not change the description.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTaskSample.html">
     * LoadTaskSample source</a>.
     *
     * @return configured or current task status
     * @throws SelectAIException when current task metadata cannot be fetched
     */
    TaskStatus getStatus() throws SelectAIException;
    /**
     * Returns task attributes for this handle.
     * <p>
     * For a database-backed task, this method reloads the attributes from the
     * database on each call and therefore can fail if the resource was deleted.
     * For a creation-configured task, it returns the configured attributes
     * without querying the database.
     * For a runnable example, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadTaskSample.html">
     * LoadTaskSample source</a>.
     *
     * @return current task attributes for an existing task, or configured
     *         attributes for a task pending creation
     * @throws SelectAIException when the task no longer exists or current attributes cannot be fetched
     */
    TaskAttributes getTaskAttributes() throws SelectAIException;
}
