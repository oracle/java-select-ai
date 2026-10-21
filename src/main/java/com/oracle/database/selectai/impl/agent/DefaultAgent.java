/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Default {@link Agent} implementation backed by {@code DBMS_CLOUD_AI_AGENT}
 * lifecycle operations.
 * <p>
 * Name-only construction loads an existing agent and its attributes. An agent
 * configured for creation becomes database-backed after {@link #create()}
 * succeeds. Database-backed status and attributes are fetched on access; the
 * description remains the value supplied or loaded at initialization.
 * Successful single-attribute and bulk-attribute updates reload the typed
 * attributes from the database.
 */
final class DefaultAgent implements Agent {
    /** Logger for agent lifecycle operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAgent.class);
    /** Connection provider used for database operations. */
    private final ConnectionProvider connectionProvider;
    /** Agent name. */
    private final String agentName;
    /** Agent attributes supplied for creation or last loaded from the database. */
    private AgentAttributes agentAttributes;
    /** Agent description supplied or loaded when this handle was initialized. */
    private final String description;
    /** Configured or last fetched agent status. */
    private AgentStatus status;
    /** Whether this handle was configured for explicit creation. */
    private final boolean configuredForCreate;
    /** Local guard for operations requiring creation; cleared when this handle drops the agent. */
    private boolean created;
    /** Whether status and attribute getters query the database, including after a drop. */
    private boolean databaseBacked;

    /**
     * Loads an existing agent and its attributes from the database.
     *
     * @param connectionProvider provider used to load the agent
     * @param agentName name of the existing agent
     * @throws IllegalArgumentException if the provider is null or the name is blank
     * @throws SelectAIException if the agent is missing or its metadata cannot be loaded
     */
    DefaultAgent(ConnectionProvider connectionProvider, String agentName) throws SelectAIException {
        AgentJdbc.requireText(agentName, "agentName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }

        ExistingAgentMetadata metadata = getAgentMetadata(connectionProvider, agentName);
        AgentAttributes attributes = AgentJdbc.fetchAgentAttributes(connectionProvider, metadata.agentName());
        this.connectionProvider = connectionProvider;
        this.agentName = metadata.agentName();
        this.agentAttributes = attributes;
        this.description = metadata.description();
        this.status = metadata.status();
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    private DefaultAgent(ConnectionProvider connectionProvider, String agentName,
                         AgentAttributes agentAttributes, String description, String status)
            throws SelectAIException {
        AgentJdbc.requireText(agentName, "agentName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (agentAttributes == null) {
            throw new IllegalArgumentException("agentAttributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.agentName = agentName;
        this.agentAttributes = agentAttributes;
        this.description = description;
        try {
            this.status = AgentStatus.fromValue(status);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI agent status: " + status, e);
        }
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    /** Creates an existing agent handle from metadata and attributes already loaded by the caller. */
    static DefaultAgent fromMetadata(ConnectionProvider connectionProvider, String agentName,
                                     AgentAttributes agentAttributes, String description, String status)
            throws SelectAIException {
        return new DefaultAgent(connectionProvider, agentName, agentAttributes, description, status);
    }

    /**
     * Configures an agent for creation without querying the database.
     *
     * @param connectionProvider provider used for later database operations
     * @param agentName name of the agent to create
     * @param attributes attributes to use when creating the agent
     * @param description optional agent description
     * @param status optional initial agent status
     * @throws IllegalArgumentException if the provider or attributes are null, or the name is blank
     */
    DefaultAgent(ConnectionProvider connectionProvider, String agentName,
                        AgentAttributes attributes, String description, AgentStatus status) {
        AgentJdbc.requireText(agentName, "agentName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.agentName = agentName;
        this.agentAttributes = attributes;
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
            throw new IllegalStateException("create requires an agent configured for creation");
        }
        LOGGER.debug("Creating Select AI agent {}", agentName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.CREATE_AGENT.get())) {
                    stmt.setString(1, agentName);
                    AgentJdbc.bindClob(stmt, 2, agentAttributes.toJson());
                    AgentJdbc.bindString(stmt, 3, status == null ? null : status.getValue());
                    AgentJdbc.bindClob(stmt, 4, description);
                    stmt.execute();
                    created = true;
                    databaseBacked = true;
                    return true;
                }
            });
            LOGGER.info("Created Select AI agent {}", agentName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.CREATE_AGENT failed for agent {}", agentName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.CREATE_AGENT", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean enable() throws SelectAIException {
        return executeNameOnly("enable", AgentSql.ENABLE_AGENT.get(), "ENABLE_AGENT", "Enabled Select AI agent");
    }

    /** {@inheritDoc} */
    @Override
    public boolean disable() throws SelectAIException {
        return executeNameOnly("disable", AgentSql.DISABLE_AGENT.get(), "DISABLE_AGENT", "Disabled Select AI agent");
    }

    /** {@inheritDoc} */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        if (!force) {
            AgentJdbc.requireCreated(created, "agent", "drop");
        }
        LOGGER.debug("Dropping Select AI agent {}", agentName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.DROP_AGENT.get())) {
                    stmt.setString(1, agentName);
                    AgentJdbc.bindPlsqlBoolean(stmt, 2, force);
                    stmt.execute();
                    created = false;
                    return true;
                }
            });
            LOGGER.info("Dropped Select AI agent {}", agentName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.DROP_AGENT failed for agent {}", agentName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.DROP_AGENT", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException {
        return setAttributeInternal(attributeName, stmt -> AgentJdbc.bindClob(stmt, 4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException {
        return setAttributeInternal(attributeName, stmt -> stmt.setBoolean(4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException {
        if (attributeValue == null) {
            throw new IllegalArgumentException("attributeValue must not be null");
        }
        return setAttributeInternal(attributeName, stmt -> stmt.setInt(4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException {
        if (attributeValue == null) {
            throw new IllegalArgumentException("attributeValue must not be null");
        }
        return setAttributeInternal(attributeName, stmt -> stmt.setFloat(4, attributeValue));
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(AgentAttributes agentAttributes) throws SelectAIException {
        AgentJdbc.requireCreated(created, "agent", "setAttributes");
        if (agentAttributes == null) {
            throw new IllegalArgumentException("agentAttributes must not be null");
        }
        LOGGER.debug("Setting attributes for Select AI agent {}", agentName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.SET_AGENT_ATTRIBUTES.get())) {
                    stmt.setString(1, agentName);
                    stmt.setString(2, AgentObjectType.AGENT.name());
                    AgentJdbc.bindClob(stmt, 3, agentAttributes.toJson());
                    stmt.execute();
                    this.agentAttributes = AgentJdbc.fetchAgentAttributes(connection, agentName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES failed for agent {}", agentName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES", e);
        }
    }

    private boolean setAttributeInternal(String attributeName, AttributeBinder binder)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "agent", "setAttribute");
        AgentJdbc.requireText(attributeName, "attributeName");
        LOGGER.debug("Setting attribute {} for Select AI agent {}", attributeName, agentName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.SET_AGENT_ATTRIBUTE.get())) {
                    stmt.setString(1, agentName);
                    stmt.setString(2, AgentObjectType.AGENT.name());
                    stmt.setString(3, attributeName);
                    binder.bind(stmt);
                    stmt.execute();
                    this.agentAttributes = AgentJdbc.fetchAgentAttributes(connection, agentName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE failed for agent {} attribute {}",
                    agentName, attributeName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE", e);
        }
    }

    @FunctionalInterface
    private interface AttributeBinder {
        void bind(CallableStatement statement) throws SQLException;
    }

    private boolean executeNameOnly(String operation, String sql, String dbmsName, String success)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "agent", operation);
        LOGGER.debug("{} Select AI agent {}", operation, agentName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(sql)) {
                    stmt.setString(1, agentName);
                    stmt.execute();
                    return true;
                }
            });
            LOGGER.info("{} {}", success, agentName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.{} failed for agent {}", dbmsName, agentName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT." + dbmsName, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getAgentName() {
        return agentName;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return description;
    }

    /** {@inheritDoc} */
    @Override
    public AgentStatus getStatus() throws SelectAIException {
        if (!databaseBacked) {
            return status;
        }

        LOGGER.debug("Fetching current status for agent {}", agentName);
        String value = AgentJdbc.fetchStatus(connectionProvider, AgentSql.GET_AGENT_STATUS, agentName, "agent");
        try {
            status = AgentStatus.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI agent status: " + value, e);
        }
        return status;
    }

    private static ExistingAgentMetadata getAgentMetadata(ConnectionProvider connectionProvider, String agentName)
            throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(AgentSql.GET_AGENT.get())) {
                    statement.setString(1, agentName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SelectAIException("Select AI agent not found: " + agentName);
                        }
                        String value = resultSet.getString("status");
                        AgentStatus currentStatus;
                        try {
                            currentStatus = AgentStatus.fromValue(value);
                        } catch (IllegalArgumentException e) {
                            throw new SelectAIException("Unsupported Select AI agent status: " + value, e);
                        }
                        return new ExistingAgentMetadata(resultSet.getString("agent_name"), currentStatus,
                                resultSet.getString("description"));
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to load metadata for agent {}", agentName, e);
            throw AgentJdbc.toException("Failed to load Select AI agent metadata: " + agentName, e);
        }
    }

    private record ExistingAgentMetadata(String agentName, AgentStatus status, String description) { }

    /** {@inheritDoc} */
    @Override
    public AgentAttributes getAgentAttributes() throws SelectAIException {
        if (!databaseBacked) {
            LOGGER.debug("Returning configured agent attributes for pending agent {}", agentName);
            return agentAttributes;
        }

        LOGGER.debug("Fetching current agent attributes for agent {}", agentName);
        agentAttributes = AgentJdbc.fetchAgentAttributes(connectionProvider, agentName);
        return agentAttributes;
    }

}
