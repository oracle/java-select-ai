/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Default {@link Task} implementation backed by {@code DBMS_CLOUD_AI_AGENT}
 * lifecycle operations.
 * <p>
 * Name-only construction loads an existing task and its attributes. A task
 * configured for creation becomes database-backed after {@link #create()}
 * succeeds. Database-backed status and attributes are fetched on access; the
 * description remains the value supplied or loaded at initialization.
 * Successful single-attribute and bulk-attribute updates reload the typed
 * attributes from the database.
 */
final class DefaultTask implements Task {
    /** Logger for task lifecycle operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTask.class);
    /** Connection provider used for database operations. */
    private final ConnectionProvider connectionProvider;
    /** Task name. */
    private final String taskName;
    /** Task attributes supplied for creation or last loaded from the database. */
    private TaskAttributes taskAttributes;
    /** Task description supplied or loaded when this handle was initialized. */
    private final String description;
    /** Configured or last fetched status. */
    private TaskStatus status;
    /** Whether this handle was configured for explicit creation. */
    private final boolean configuredForCreate;
    /** Local guard for operations requiring creation; cleared when this handle drops the task. */
    private boolean created;
    /** Whether status and attribute getters query the database, including after a drop. */
    private boolean databaseBacked;

    /**
     * Loads an existing task and its attributes from the database.
     *
     * @param connectionProvider provider used to load the task
     * @param taskName name of the existing task
     * @throws IllegalArgumentException if the provider is null or the name is blank
     * @throws SelectAIException if the task is missing or its metadata cannot be loaded
     */
    DefaultTask(ConnectionProvider connectionProvider, String taskName) throws SelectAIException {
        AgentJdbc.requireText(taskName, "taskName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }

        ExistingTaskMetadata metadata = getTaskMetadata(connectionProvider, taskName);
        TaskAttributes attributes = AgentJdbc.fetchTaskAttributes(connectionProvider, metadata.taskName());
        this.connectionProvider = connectionProvider;
        this.taskName = metadata.taskName();
        this.taskAttributes = attributes;
        this.description = metadata.description();
        this.status = metadata.status();
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    private DefaultTask(ConnectionProvider connectionProvider, String taskName,
                        TaskAttributes taskAttributes, String description, String status)
            throws SelectAIException {
        AgentJdbc.requireText(taskName, "taskName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (taskAttributes == null) {
            throw new IllegalArgumentException("taskAttributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.taskName = taskName;
        this.taskAttributes = taskAttributes;
        this.description = description;
        try {
            this.status = TaskStatus.fromValue(status);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI task status: " + status, e);
        }
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    /** Creates an existing task handle from metadata and attributes already loaded by the caller. */
    static DefaultTask fromMetadata(ConnectionProvider connectionProvider, String taskName,
                                    TaskAttributes taskAttributes, String description, String status)
            throws SelectAIException {
        return new DefaultTask(connectionProvider, taskName, taskAttributes, description, status);
    }

    /**
     * Configures a task for creation without querying the database.
     *
     * @param connectionProvider provider used for later database operations
     * @param taskName name of the task to create
     * @param attributes attributes to use when creating the task
     * @param description optional task description
     * @param status optional initial task status
     * @throws IllegalArgumentException if the provider or attributes are null, or the name is blank
     */
    DefaultTask(ConnectionProvider connectionProvider, String taskName,
                       TaskAttributes attributes, String description, TaskStatus status) {
        AgentJdbc.requireText(taskName, "taskName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.taskName = taskName;
        this.taskAttributes = attributes;
        this.description = description;
        this.status = status;
        this.configuredForCreate = true;
        this.created = false;
        this.databaseBacked = false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean create() throws SelectAIException {
        if (!configuredForCreate) {
            throw new IllegalStateException("create requires a task configured for creation");
        }
        LOGGER.debug("Creating Select AI agent task {}", taskName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.CREATE_TASK.get())) {
                    stmt.setString(1, taskName);
                    AgentJdbc.bindClob(stmt, 2, taskAttributes.toJson());
                    AgentJdbc.bindString(stmt, 3, status == null ? null : status.getValue());
                    AgentJdbc.bindClob(stmt, 4, description);
                    stmt.execute();
                    created = true;
                    databaseBacked = true;
                    return true;
                }
            });
            LOGGER.info("Created Select AI agent task {}", taskName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.CREATE_TASK failed for task {}", taskName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.CREATE_TASK", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean enable() throws SelectAIException {
        return executeNameOnly("enable", AgentSql.ENABLE_TASK.get(), "ENABLE_TASK", "Enabled Select AI agent task");
    }

    /** {@inheritDoc} */
    @Override
    public boolean disable() throws SelectAIException {
        return executeNameOnly("disable", AgentSql.DISABLE_TASK.get(), "DISABLE_TASK", "Disabled Select AI agent task");
    }

    /** {@inheritDoc} */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        if (!force) {
            AgentJdbc.requireCreated(created, "task", "drop");
        }
        LOGGER.debug("Dropping Select AI agent task {}", taskName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.DROP_TASK.get())) {
                    stmt.setString(1, taskName);
                    AgentJdbc.bindPlsqlBoolean(stmt, 2, force);
                    stmt.execute();
                    created = false;
                    return true;
                }
            });
            LOGGER.info("Dropped Select AI agent task {}", taskName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.DROP_TASK failed for task {}", taskName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.DROP_TASK", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException {
        return setAttributeInternal(attributeName, statement -> AgentJdbc.bindClob(statement, 4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException {
        return setAttributeInternal(attributeName, statement -> statement.setBoolean(4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException {
        if (attributeValue == null) {
            throw new IllegalArgumentException("attributeValue must not be null");
        }
        return setAttributeInternal(attributeName, statement -> statement.setInt(4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException {
        if (attributeValue == null) {
            throw new IllegalArgumentException("attributeValue must not be null");
        }
        return setAttributeInternal(attributeName, statement -> statement.setFloat(4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(TaskAttributes taskAttributes) throws SelectAIException {
        AgentJdbc.requireCreated(created, "task", "setAttributes");
        if (taskAttributes == null) {
            throw new IllegalArgumentException("taskAttributes must not be null");
        }
        LOGGER.debug("Setting attributes for Select AI agent task {}", taskName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement statement = connection.prepareCall(AgentSql.SET_TASK_ATTRIBUTES.get())) {
                    statement.setString(1, taskName);
                    statement.setString(2, AgentObjectType.TASK.name());
                    AgentJdbc.bindClob(statement, 3, taskAttributes.toJson());
                    statement.execute();
                    this.taskAttributes = AgentJdbc.fetchTaskAttributes(connection, taskName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES failed for task {}", taskName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES", e);
        }
    }

    private boolean setAttributeInternal(String attributeName, AttributeBinder binder)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "task", "setAttribute");
        AgentJdbc.requireText(attributeName, "attributeName");
        LOGGER.debug("Setting attribute {} for Select AI agent task {}", attributeName, taskName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement statement = connection.prepareCall(AgentSql.SET_TASK_ATTRIBUTE.get())) {
                    statement.setString(1, taskName);
                    statement.setString(2, AgentObjectType.TASK.name());
                    statement.setString(3, attributeName);
                    binder.bind(statement);
                    statement.execute();
                    this.taskAttributes = AgentJdbc.fetchTaskAttributes(connection, taskName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE failed for task {} attribute {}",
                    taskName, attributeName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE", e);
        }
    }

    @FunctionalInterface
    private interface AttributeBinder {
        void bind(CallableStatement statement) throws SQLException;
    }

    private boolean executeNameOnly(String operation, String sql, String dbmsName, String success)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "task", operation);
        LOGGER.debug("{} Select AI agent task {}", operation, taskName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(sql)) {
                    stmt.setString(1, taskName);
                    stmt.execute();
                    return true;
                }
            });
            LOGGER.info("{} {}", success, taskName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.{} failed for task {}", dbmsName, taskName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT." + dbmsName, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getTaskName() {
        return taskName;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return description;
    }

    /** {@inheritDoc} */
    @Override
    public TaskStatus getStatus() throws SelectAIException {
        if (!databaseBacked) {
            return status;
        }

        LOGGER.debug("Fetching current status for task {}", taskName);
        String value = AgentJdbc.fetchStatus(connectionProvider, AgentSql.GET_TASK_STATUS, taskName, "task");
        try {
            status = TaskStatus.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI task status: " + value, e);
        }
        return status;
    }

    private static ExistingTaskMetadata getTaskMetadata(ConnectionProvider connectionProvider, String taskName)
            throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(AgentSql.GET_TASK.get())) {
                    statement.setString(1, taskName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SelectAIException("Select AI task not found: " + taskName);
                        }
                        String value = resultSet.getString("status");
                        TaskStatus currentStatus;
                        try {
                            currentStatus = TaskStatus.fromValue(value);
                        } catch (IllegalArgumentException e) {
                            throw new SelectAIException("Unsupported Select AI task status: " + value, e);
                        }
                        return new ExistingTaskMetadata(resultSet.getString("task_name"), currentStatus,
                                resultSet.getString("description"));
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to load metadata for task {}", taskName, e);
            throw AgentJdbc.toException("Failed to load Select AI task metadata: " + taskName, e);
        }
    }

    private record ExistingTaskMetadata(String taskName, TaskStatus status, String description) { }

    /** {@inheritDoc} */
    @Override
    public TaskAttributes getTaskAttributes() throws SelectAIException {
        if (!databaseBacked) {
            LOGGER.debug("Returning configured task attributes for pending task {}", taskName);
            return taskAttributes;
        }

        LOGGER.debug("Fetching current task attributes for task {}", taskName);
        taskAttributes = AgentJdbc.fetchTaskAttributes(connectionProvider, taskName);
        return taskAttributes;
    }

}
