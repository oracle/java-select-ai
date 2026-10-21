/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamDescription;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Default {@link Team} implementation backed by {@code DBMS_CLOUD_AI_AGENT}
 * lifecycle and team-orchestration operations.
 * <p>
 * Name-only construction loads an existing team and its attributes. A team
 * configured for creation becomes database-backed after {@link #create()}
 * succeeds. Database-backed status and attributes are fetched on access; the
 * description remains the value supplied or loaded at initialization.
 * Successful single-attribute and bulk-attribute updates reload the typed
 * attributes from the database.
 */
final class DefaultTeam implements Team {
    /** Logger for team operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTeam.class);
    /** Connection provider used for database operations. */
    private final ConnectionProvider connectionProvider;
    /** Team name. */
    private final String teamName;
    /** Team attributes supplied for creation or last loaded from the database. */
    private TeamAttributes teamAttributes;
    /** Team description supplied or loaded when this handle was initialized. */
    private final String description;
    /** Configured or last fetched status. */
    private TeamStatus status;
    /** Whether this handle was configured for explicit creation. */
    private final boolean configuredForCreate;
    /** Local guard for operations requiring creation; cleared when this handle drops the team. */
    private boolean created;
    /** Whether status and attribute getters query the database, including after a drop. */
    private boolean databaseBacked;

    /**
     * Loads an existing team and its attributes from the database.
     *
     * @param connectionProvider provider used to load the team
     * @param teamName name of the existing team
     * @throws IllegalArgumentException if the provider is null or the name is blank
     * @throws SelectAIException if the team is missing or its metadata cannot be loaded
     */
    DefaultTeam(ConnectionProvider connectionProvider, String teamName) throws SelectAIException {
        AgentJdbc.requireText(teamName, "teamName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }

        ExistingTeamMetadata metadata = getTeamMetadata(connectionProvider, teamName);
        TeamAttributes attributes = AgentJdbc.fetchTeamAttributes(connectionProvider, metadata.teamName());
        this.connectionProvider = connectionProvider;
        this.teamName = metadata.teamName();
        this.teamAttributes = attributes;
        this.description = metadata.description();
        this.status = metadata.status();
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    private DefaultTeam(ConnectionProvider connectionProvider, String teamName,
                        TeamAttributes teamAttributes, String description, String status)
            throws SelectAIException {
        AgentJdbc.requireText(teamName, "teamName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (teamAttributes == null) {
            throw new IllegalArgumentException("teamAttributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.teamName = teamName;
        this.teamAttributes = teamAttributes;
        this.description = description;
        try {
            this.status = TeamStatus.fromValue(status);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI team status: " + status, e);
        }
        this.configuredForCreate = false;
        this.created = true;
        this.databaseBacked = true;
    }

    /** Creates an existing team handle from metadata and attributes already loaded by the caller. */
    static DefaultTeam fromMetadata(ConnectionProvider connectionProvider, String teamName,
                                    TeamAttributes teamAttributes, String description, String status)
            throws SelectAIException {
        return new DefaultTeam(connectionProvider, teamName, teamAttributes, description, status);
    }

    /**
     * Configures a team for creation without querying the database.
     *
     * @param connectionProvider provider used for later database operations
     * @param teamName name of the team to create
     * @param attributes attributes to use when creating the team
     * @param description optional team description
     * @param status optional initial team status
     * @throws IllegalArgumentException if the provider or attributes are null, or the name is blank
     */
    DefaultTeam(ConnectionProvider connectionProvider, String teamName,
                       TeamAttributes attributes, String description, TeamStatus status) {
        AgentJdbc.requireText(teamName, "teamName");
        if (connectionProvider == null) {
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.teamName = teamName;
        this.teamAttributes = attributes;
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
            throw new IllegalStateException("create requires a team configured for creation");
        }
        LOGGER.debug("Creating Select AI agent team {}", teamName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.CREATE_TEAM.get())) {
                    stmt.setString(1, teamName);
                    AgentJdbc.bindClob(stmt, 2, teamAttributes.toJson());
                    AgentJdbc.bindString(stmt, 3, status == null ? null : status.getValue());
                    AgentJdbc.bindClob(stmt, 4, description);
                    stmt.execute();
                    created = true;
                    databaseBacked = true;
                    return true;
                }
            });
            LOGGER.info("Created Select AI agent team {}", teamName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.CREATE_TEAM failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.CREATE_TEAM", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean enable() throws SelectAIException {
        return executeNameOnly("enable", AgentSql.ENABLE_TEAM.get(), "ENABLE_TEAM", "Enabled Select AI agent team");
    }

    /** {@inheritDoc} */
    @Override
    public boolean disable() throws SelectAIException {
        return executeNameOnly("disable", AgentSql.DISABLE_TEAM.get(), "DISABLE_TEAM", "Disabled Select AI agent team");
    }

    /** {@inheritDoc} */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        if (!force) {
            AgentJdbc.requireCreated(created, "team", "drop");
        }
        LOGGER.debug("Dropping Select AI agent team {}", teamName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.DROP_TEAM.get())) {
                    stmt.setString(1, teamName);
                    AgentJdbc.bindPlsqlBoolean(stmt, 2, force);
                    stmt.execute();
                    created = false;
                    return true;
                }
            });
            LOGGER.info("Dropped Select AI agent team {}", teamName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.DROP_TEAM failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.DROP_TEAM", e);
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
    public boolean setAttributes(TeamAttributes teamAttributes) throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "setAttributes");
        if (teamAttributes == null) {
            throw new IllegalArgumentException("teamAttributes must not be null");
        }
        LOGGER.debug("Setting attributes for Select AI agent team {}", teamName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement statement = connection.prepareCall(AgentSql.SET_TEAM_ATTRIBUTES.get())) {
                    statement.setString(1, teamName);
                    statement.setString(2, AgentObjectType.TEAM.name());
                    AgentJdbc.bindClob(statement, 3, teamAttributes.toJson());
                    statement.execute();
                    this.teamAttributes = AgentJdbc.fetchTeamAttributes(connection, teamName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean grantAccess(String userOrRoleName) throws SelectAIException {
        return updateTeamAccess(true, userOrRoleName);
    }

    /** {@inheritDoc} */
    @Override
    public boolean revokeAccess(String userOrRoleName) throws SelectAIException {
        return updateTeamAccess(false, userOrRoleName);
    }

    private boolean updateTeamAccess(boolean grant, String userOrRoleName)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", grant ? "grantAccess" : "revokeAccess");
        String operation = grant ? "GRANT_TEAM_ACCESS" : "REVOKE_TEAM_ACCESS";
        LOGGER.debug("{} for Select AI agent team {} and grantee {}",
                operation, teamName, userOrRoleName);
        try {
            boolean updated = AgentJdbc.updateTeamAccess(
                    connectionProvider, grant, teamName, userOrRoleName);
            LOGGER.info("{} completed for Select AI agent team {} and grantee {}",
                    operation, teamName, userOrRoleName);
            return updated;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.{} failed for team {} and grantee {}",
                    operation, teamName, userOrRoleName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT." + operation, e);
        }
    }

    private boolean setAttributeInternal(String attributeName, AttributeBinder binder)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "setAttribute");
        AgentJdbc.requireText(attributeName, "attributeName");
        LOGGER.debug("Setting attribute {} for Select AI agent team {}", attributeName, teamName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement statement = connection.prepareCall(AgentSql.SET_TEAM_ATTRIBUTE.get())) {
                    statement.setString(1, teamName);
                    statement.setString(2, AgentObjectType.TEAM.name());
                    statement.setString(3, attributeName);
                    binder.bind(statement);
                    statement.execute();
                    this.teamAttributes = AgentJdbc.fetchTeamAttributes(connection, teamName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE failed for team {} attribute {}",
                    teamName, attributeName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE", e);
        }
    }

    @FunctionalInterface
    private interface AttributeBinder {
        void bind(CallableStatement statement) throws SQLException;
    }

    /** {@inheritDoc} */
    @Override
    public TeamRunResult run(RunTeamRequest request) throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "run");
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
    public TeamDescription describe() throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "describe");
        String response = clobOrStringFunction(
                AgentSql.DESCRIBE_TEAM.get(), "DESCRIBE_TEAM", true, null);
        try {
            return TeamDescription.fromRawJson(response);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Failed to map Select AI team description: " + teamName, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public TeamExecutionState getStateByConversationId(String conversationId)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "getStateByConversationId");
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
    public boolean setExecutionState(ExecutionStateRequest request) throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "setExecutionState");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        LOGGER.debug("Setting Select AI agent team execution state for {}", teamName);
        try {
            AgentJdbc.setTeamExecutionState(connectionProvider, teamName, request);
            LOGGER.info("Updated Select AI agent team execution state for {}", teamName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.SET_EXECUTION_STATE failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.SET_EXECUTION_STATE", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String exportDefinition() throws SelectAIException {
        return exportDefinition((ExportTeamParams) null);
    }

    /** {@inheritDoc} */
    @Override
    public String exportDefinition(ExportTeamParams params) throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "exportDefinition");
        LOGGER.debug("Exporting Select AI agent team definition for {}", teamName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.EXPORT_TEAM_FUNCTION.get())) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    stmt.setString(2, teamName);
                    AgentJdbc.bindClob(stmt, 3, params == null ? null : params.toParamsJson());
                    stmt.execute();
                    return AgentJdbc.readClob(stmt.getClob(1));
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.EXPORT_TEAM failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.EXPORT_TEAM", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean exportDefinition(ExportTeamRequest request) throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", "exportDefinition");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        LOGGER.debug("Exporting Select AI agent team {} to configured location", teamName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(AgentSql.EXPORT_TEAM_PROCEDURE.get())) {
                    stmt.setString(1, teamName);
                    AgentJdbc.bindString(stmt, 2, request.getObjectStorageCredentialName());
                    stmt.setString(3, request.getLocation());
                    AgentJdbc.bindClob(stmt, 4, request.toParamsJson());
                    stmt.execute();
                    return true;
                }
            });
            LOGGER.info("Exported Select AI agent team {}", teamName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.EXPORT_TEAM failed for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT.EXPORT_TEAM", e);
        }
    }

    private String clobOrStringFunction(String sql, String dbmsName, boolean bindTeam, String clobInput)
            throws SelectAIException {
        LOGGER.debug("Executing DBMS_CLOUD_AI_AGENT.{} for team {}", dbmsName, teamName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(sql)) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    if (bindTeam) {
                        stmt.setString(2, teamName);
                    }
                    if (clobInput != null) {
                        AgentJdbc.bindClob(stmt, 3, clobInput);
                    }
                    stmt.execute();
                    return AgentJdbc.readClob(stmt.getClob(1));
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.{} failed for team {}", dbmsName, teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT." + dbmsName, e);
        }
    }

    private boolean executeNameOnly(String operation, String sql, String dbmsName, String success)
            throws SelectAIException {
        AgentJdbc.requireCreated(created, "team", operation);
        LOGGER.debug("{} Select AI agent team {}", operation, teamName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(sql)) {
                    stmt.setString(1, teamName);
                    stmt.execute();
                    return true;
                }
            });
            LOGGER.info("{} {}", success, teamName);
            return true;
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI_AGENT.{} failed for team {}", dbmsName, teamName, e);
            throw AgentJdbc.toException("Failed to execute DBMS_CLOUD_AI_AGENT." + dbmsName, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getTeamName() {
        return teamName;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return description;
    }

    /** {@inheritDoc} */
    @Override
    public TeamStatus getStatus() throws SelectAIException {
        if (!databaseBacked) {
            return status;
        }

        LOGGER.debug("Fetching current status for team {}", teamName);
        String value = AgentJdbc.fetchStatus(connectionProvider, AgentSql.GET_TEAM_STATUS, teamName, "team");
        try {
            status = TeamStatus.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new SelectAIException("Unsupported Select AI team status: " + value, e);
        }
        return status;
    }

    private static ExistingTeamMetadata getTeamMetadata(ConnectionProvider connectionProvider, String teamName)
            throws SelectAIException {
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(AgentSql.GET_TEAM.get())) {
                    statement.setString(1, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SelectAIException("Select AI team not found: " + teamName);
                        }
                        String value = resultSet.getString("status");
                        TeamStatus currentStatus;
                        try {
                            currentStatus = TeamStatus.fromValue(value);
                        } catch (IllegalArgumentException e) {
                            throw new SelectAIException("Unsupported Select AI team status: " + value, e);
                        }
                        return new ExistingTeamMetadata(resultSet.getString("agent_team_name"), currentStatus,
                                resultSet.getString("description"));
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to load metadata for team {}", teamName, e);
            throw AgentJdbc.toException("Failed to load Select AI team metadata: " + teamName, e);
        }
    }

    private record ExistingTeamMetadata(String teamName, TeamStatus status, String description) { }

    /** {@inheritDoc} */
    @Override
    public TeamAttributes getTeamAttributes() throws SelectAIException {
        if (!databaseBacked) {
            LOGGER.debug("Returning configured team attributes for pending team {}", teamName);
            return teamAttributes;
        }

        LOGGER.debug("Fetching current team attributes for team {}", teamName);
        teamAttributes = AgentJdbc.fetchTeamAttributes(connectionProvider, teamName);
        return teamAttributes;
    }

}
