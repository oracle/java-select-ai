/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolRunResult;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Default {@link Tool} implementation backed by {@code DBMS_CLOUD_AI_AGENT}
 * lifecycle and tool-execution operations.
 * <p>
 * Name-only construction loads an existing tool and its attributes. A tool
 * configured for creation becomes database-backed after {@link #create()}
 * succeeds. Database-backed status and attributes are fetched on access; the
 * description remains the value supplied or loaded at initialization.
 * Successful single-attribute and bulk-attribute updates reload the typed
 * attributes from the database.
 */
final class DefaultTool implements Tool {
    /** Logger for tool operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTool.class);
    /** Connection provider used for database operations. */
    private final ConnectionProvider connectionProvider;
    /** Tool name. */
    private final String toolName;
    /** Tool attributes supplied for creation or last loaded from the database. */
    private ToolAttributes toolAttributes;
    /** Tool description supplied or loaded when this handle was initialized. */
    private final String description;
    /** Configured or last fetched status. */
    private ToolStatus status;
    /** Whether this handle was configured for explicit creation. */
    private final boolean configuredForCreate;
    /** Local guard for operations requiring creation; cleared when this handle drops the tool. */
    private boolean created;
    /** Whether status and attribute getters query the database, including after a drop. */
    private boolean databaseBacked;

    /**
     * Loads an existing tool and its attributes from the database.
     *
     * @param connectionProvider provider used to load the tool
     * @param toolName name of the existing tool
     * @throws IllegalArgumentException if the provider is null or the name is blank
     * @throws SelectAIException if the tool is missing or its metadata cannot be loaded
     */
    DefaultTool(ConnectionProvider connectionProvider, String toolName) throws SelectAIException {
        AgentJdbc.requireText(toolName, "toolName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }

        ExistingToolMetadata metadata = getToolMetadata(connectionProvider, toolName);
        ToolAttributes attributes = AgentJdbc.fetchToolAttributes(connectionProvider, metadata.toolName());
        this.connectionProvider = connectionProvider;
        this.toolName = metadata.toolName();
        this.toolAttributes = attributes;
        this.description = metadata.description();
        this.status = metadata.status();
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    private DefaultTool(ConnectionProvider connectionProvider, String toolName,
                        ToolAttributes toolAttributes, String description, String status)
            throws SelectAIException {
        AgentJdbc.requireText(toolName, "toolName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (toolAttributes == null) {
            throw new IllegalArgumentException("toolAttributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.toolName = toolName;
        this.toolAttributes = toolAttributes;
        this.description = description;
        try {
            this.status = ToolStatus.fromValue(status);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI tool status: " + status, e);
        }
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    /** Creates an existing tool handle from metadata and attributes already loaded by the caller. */
    static DefaultTool fromMetadata(ConnectionProvider connectionProvider, String toolName,
                                    ToolAttributes toolAttributes, String description, String status)
            throws SelectAIException {
        return new DefaultTool(connectionProvider, toolName, toolAttributes, description, status);
    }

    /**
     * Configures a tool for creation without querying the database.
     *
     * @param connectionProvider provider used for later database operations
     * @param toolName name of the tool to create
     * @param attributes attributes to use when creating the tool
     * @param description optional tool description
     * @param status optional initial tool status
     * @throws IllegalArgumentException if the provider or attributes are null, or the name is blank
     */
    DefaultTool(ConnectionProvider connectionProvider, String toolName,
                       ToolAttributes attributes, String description, ToolStatus status) {
        AgentJdbc.requireText(toolName, "toolName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.toolName = toolName;
        this.toolAttributes = attributes;
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
            throw new IllegalStateException("create requires a tool configured for creation");
        }
        LOGGER.debug("Creating Select AI agent tool {}", toolName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.CREATE_TOOL.get())) {
                    stmt.setString(1, toolName);
                    AgentJdbc.bindClob(stmt, 2, toolAttributes.toJson());
                    AgentJdbc.bindString(stmt, 3, status == null ? null : status.getValue());
                    AgentJdbc.bindClob(stmt, 4, description);
                    stmt.execute();
                    created = true;
                    databaseBacked = true;
                    return true;
                }
            });
            LOGGER.info("Created Select AI agent tool {}", toolName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.CREATE_TOOL failed for tool {}", toolName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.CREATE_TOOL", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean enable() throws SelectAIException {
        return executeNameOnly("enable", AgentSql.ENABLE_TOOL.get(), "ENABLE_TOOL", "Enabled Select AI agent tool");
    }

    /** {@inheritDoc} */
    @Override
    public boolean disable() throws SelectAIException {
        return executeNameOnly("disable", AgentSql.DISABLE_TOOL.get(), "DISABLE_TOOL", "Disabled Select AI agent tool");
    }

    /** {@inheritDoc} */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        if (!force) {
            AgentJdbc.requireCreated(created, "tool", "drop");
        }
        LOGGER.debug("Dropping Select AI agent tool {}", toolName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.DROP_TOOL.get())) {
                    stmt.setString(1, toolName);
                    AgentJdbc.bindPlsqlBoolean(stmt, 2, force);
                    stmt.execute();
                    created = false;
                    return true;
                }
            });
            LOGGER.info("Dropped Select AI agent tool {}", toolName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.DROP_TOOL failed for tool {}", toolName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.DROP_TOOL", e);
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
    public boolean setAttributes(ToolAttributes toolAttributes) throws SelectAIException {
        AgentJdbc.requireCreated(created, "tool", "setAttributes");
        if (toolAttributes == null) {
            throw new IllegalArgumentException("toolAttributes must not be null");
        }
        LOGGER.debug("Setting attributes for Select AI agent tool {}", toolName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement statement = connection.prepareCall(AgentSql.SET_TOOL_ATTRIBUTES.get())) {
                    statement.setString(1, toolName);
                    statement.setString(2, AgentObjectType.TOOL.name());
                    AgentJdbc.bindClob(statement, 3, toolAttributes.toJson());
                    statement.execute();
                    this.toolAttributes = AgentJdbc.fetchToolAttributes(connection, toolName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES failed for tool {}", toolName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES", e);
        }
    }

    private boolean setAttributeInternal(String attributeName, AttributeBinder binder)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "tool", "setAttribute");
        AgentJdbc.requireText(attributeName, "attributeName");
        LOGGER.debug("Setting attribute {} for Select AI agent tool {}", attributeName, toolName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement statement = connection.prepareCall(AgentSql.SET_TOOL_ATTRIBUTE.get())) {
                    statement.setString(1, toolName);
                    statement.setString(2, AgentObjectType.TOOL.name());
                    statement.setString(3, attributeName);
                    binder.bind(statement);
                    statement.execute();
                    this.toolAttributes = AgentJdbc.fetchToolAttributes(connection, toolName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE failed for tool {} attribute {}",
                    toolName, attributeName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE", e);
        }
    }

    @FunctionalInterface
    private interface AttributeBinder {
        void bind(CallableStatement statement) throws SQLException;
    }

    /** {@inheritDoc} */
    @Override
    public String describe() throws SelectAIException {
        AgentJdbc.requireCreated(created, "tool", "describe");
        LOGGER.debug("Describing Select AI agent tool {}", toolName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.DESCRIBE_TOOL.get())) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    stmt.setString(2, toolName);
                    stmt.execute();
                    return AgentJdbc.readClob(stmt.getClob(1));
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.DESCRIBE_TOOL failed for tool {}", toolName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.DESCRIBE_TOOL", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ToolRunResult run(String inputJson) throws SelectAIException {
        AgentJdbc.requireCreated(created, "tool", "run");
        AgentJdbc.requireText(inputJson, "inputJson");
        LOGGER.debug("Running Select AI agent tool {}", toolName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.RUN_TOOL.get())) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    stmt.setString(2, toolName);
                    AgentJdbc.bindClob(stmt, 3, inputJson);
                    stmt.execute();
                    return ToolRunResult.fromRawValue(AgentJdbc.readClob(stmt.getClob(1)));
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.RUN_TOOL failed for tool {}", toolName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.RUN_TOOL", e);
        }
    }

    private boolean executeNameOnly(String operation, String sql, String dbmsName, String success)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "tool", operation);
        LOGGER.debug("{} Select AI agent tool {}", operation, toolName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(sql)) {
                    stmt.setString(1, toolName);
                    stmt.execute();
                    return true;
                }
            });
            LOGGER.info("{} {}", success, toolName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.{} failed for tool {}", dbmsName, toolName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT." + dbmsName, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getToolName() {
        return toolName;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return description;
    }

    /** {@inheritDoc} */
    @Override
    public ToolStatus getStatus() throws SelectAIException {
        if (!databaseBacked) {
            return status;
        }

        LOGGER.debug("Fetching current status for tool {}", toolName);
        String value = AgentJdbc.fetchStatus(connectionProvider, AgentSql.GET_TOOL_STATUS, toolName, "tool");
        try {
            status = ToolStatus.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI tool status: " + value, e);
        }
        return status;
    }

    private static ExistingToolMetadata getToolMetadata(ConnectionProvider connectionProvider, String toolName)
            throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(AgentSql.GET_TOOL.get())) {
                    statement.setString(1, toolName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SelectAIException("Select AI tool not found: " + toolName);
                        }
                        String value = resultSet.getString("status");
                        ToolStatus currentStatus;
                        try {
                            currentStatus = ToolStatus.fromValue(value);
                        } catch (IllegalArgumentException e) {
                            throw new SelectAIException("Unsupported Select AI tool status: " + value, e);
                        }
                        return new ExistingToolMetadata(resultSet.getString("tool_name"), currentStatus,
                                resultSet.getString("description"));
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to load metadata for tool {}", toolName, e);
            throw AgentJdbc.toException("Failed to load Select AI tool metadata: " + toolName, e);
        }
    }

    private record ExistingToolMetadata(String toolName, ToolStatus status, String description) { }

    /** {@inheritDoc} */
    @Override
    public ToolAttributes getToolAttributes() throws SelectAIException {
        if (!databaseBacked) {
            LOGGER.debug("Returning configured tool attributes for pending tool {}", toolName);
            return toolAttributes;
        }

        LOGGER.debug("Fetching current tool attributes for tool {}", toolName);
        toolAttributes = AgentJdbc.fetchToolAttributes(connectionProvider, toolName);
        return toolAttributes;
    }

}
