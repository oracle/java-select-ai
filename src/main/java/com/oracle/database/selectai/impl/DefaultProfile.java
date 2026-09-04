/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.Session;
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.ObjectListMode;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SummaryParams;
import com.oracle.database.selectai.model.SyntheticDataBatchRequest;
import com.oracle.database.selectai.model.SyntheticDataSingleRequest;
import com.oracle.database.selectai.impl.ProfileAttributeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.io.StringReader;

/**
 * Default {@link Profile} implementation backed by {@code DBMS_CLOUD_AI}
 * profile lifecycle and generation procedures.
 * <p>
 * Instances initialized for creation become database-backed after
 * {@link #create()} succeeds. Instances opened by profile name are loaded from
 * database metadata during construction.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Manage AI profiles</a>
 */
final class DefaultProfile implements Profile {
    /** Logger for profile lifecycle, generation, and attribute operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(Profile.class);
    /** Shared JSON parser used to validate optional JSON payloads. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** Provider that controls connection lifecycle for profile operations. */
    private final ConnectionProvider connectionProvider;
    /** Profile name bound to this instance. */
    private String profileName;
    /** Profile status loaded from {@code USER_CLOUD_AI_PROFILES}. */
    private String status;
    /** Profile description loaded from metadata or supplied at create time. */
    private String description;
    /** Current profile attributes snapshot. */
    private ProfileAttributes profileAttributes;
    /** Mutable attribute map used to keep the local snapshot in sync after updates. */
    private Map<String, String> profileAttributesState;
    /** Whether this object is backed by a profile persisted in the database. */
    private boolean databaseBacked;

    /**
     * Creates an internal empty profile instance used when materializing
     * already-loaded metadata.
     *
     * @param connectionProvider provider used for profile operations
     */
    DefaultProfile(ConnectionProvider connectionProvider) {
        validateConnectionProvider(connectionProvider);
        this.connectionProvider = connectionProvider;
        this.profileName = null;
        this.status = null;
        this.description = null;
        this.profileAttributes = null;
        this.profileAttributesState = null;
    }

    /**
     * Creates a profile instance by loading an existing Select AI profile from the database.
     * <p>
     * Constructor flow:
     * <ol>
     *   <li>Validates required inputs ({@code connectionProvider}, {@code profileName}).</li>
     *   <li>Fetches profile metadata (status and description) from
     *       {@code USER_CLOUD_AI_PROFILES}.</li>
     *   <li>Loads profile attributes via {@link ProfileAttributeUtils}.</li>
     *   <li>Builds local in-memory attribute state cache for subsequent updates.</li>
     * </ol>
     *
     * @param connectionProvider provider used to fetch profile metadata and attributes
     * @param profileName existing profile name to load
     * @throws IllegalArgumentException when required inputs are invalid
     * @throws SelectAIException when profile lookup or attribute fetch fails
     */
    DefaultProfile(ConnectionProvider connectionProvider,
                   String profileName) throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (profileName == null || profileName.isBlank()) {
            LOGGER.error("profileName must not be null or blank when loading profile");
            throw new IllegalArgumentException("profileName must not be null or blank");
        }

        LOGGER.debug("Loading existing profile {}", profileName);
        try {
            ExistingProfileMetadata existingProfileMetadata = getProfile(connectionProvider, profileName);
            this.connectionProvider = connectionProvider;
            this.profileName = profileName;
            this.status = existingProfileMetadata.getStatus();
            this.description = existingProfileMetadata.getDescription();
            this.profileAttributes = connectionProvider.withConnection(connection ->
                    ProfileAttributeUtils.fetchProfileAttributes(connection, this.profileName));
            this.profileAttributesState = new HashMap<>(this.profileAttributes.toAttributeMap());
            this.databaseBacked = true;
            LOGGER.debug("Loaded existing profile {} with status {}", this.profileName, this.status);
        } catch (SelectAIException e) {
            LOGGER.error("Unable to load profile {}", profileName, e);
            throw e;
        } catch (SQLException e) {
            LOGGER.error("Unable to load profile attributes for {}", profileName, e);
            throw new SelectAIException("Failed to load SelectAI profile attributes: " + profileName, e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Creates a profile instance initialized with create-time metadata.
     * <p>
     * This constructor does not create the database profile. Call
     * {@link #create()} to persist it.
     *
     * @param connectionProvider provider used for profile operations
     * @param profileName profile name
     * @param profileAttributes profile attributes payload used by DBMS_CLOUD_AI.CREATE_PROFILE
     * @param description optional profile description
     * @param status optional initial profile status; {@code null} uses the database default
     * @throws IllegalArgumentException when required inputs are invalid
     */
    DefaultProfile(ConnectionProvider connectionProvider,
                   String profileName,
                   ProfileAttributes profileAttributes,
                   String description,
                   ProfileStatus status) {
        validateConnectionProvider(connectionProvider);
        if (profileName == null || profileName.isBlank()) {
            LOGGER.error("profileName must not be null or blank when creating profile");
            throw new IllegalArgumentException("profileName must not be null or blank");
        }
        validateCreateMandatoryAttributes(profileAttributes);
        this.connectionProvider = connectionProvider;
        this.profileName = profileName;
        this.status = status == null ? null : status.getValue();
        this.description = description;
        this.profileAttributes = profileAttributes;
        this.profileAttributesState = new HashMap<>(profileAttributes.toAttributeMap());
        this.databaseBacked = false;
        LOGGER.debug("Initialized profile {} for explicit create with status={}", profileName, status);
    }

    static DefaultProfile fromMetadata(ConnectionProvider connectionProvider,
                                       String profileName,
                                       ProfileAttributes profileAttributes,
                                       String description,
                                       String status) {
        validateConnectionProvider(connectionProvider);
        DefaultProfile profile = new DefaultProfile(connectionProvider);
        profile.profileName = profileName;
        profile.status = status;
        profile.description = description;
        profile.profileAttributes = profileAttributes;
        profile.profileAttributesState = new HashMap<>(profile.profileAttributes.toAttributeMap());
        profile.databaseBacked = true;
        return profile;
    }

    /**
     * Creates this configured profile in the database.
     *
     * @return {@code true} when create succeeds
     * @throws IllegalStateException when this profile is not configured for creation
     * @throws SelectAIException when create fails
     */
    @Override
    public boolean create() throws SelectAIException {
        requireBoundProfile("create");
        validateCreateMandatoryAttributes(this.profileAttributes);
        LOGGER.debug("Creating profile {} with status={}", this.profileName, this.status);

        boolean created = create(this.connectionProvider, this.profileName, this.description,
                this.status, this.profileAttributes);
        this.databaseBacked = created;
        LOGGER.info("Create profile {} completed", this.profileName);
        return created;
    }

    private ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    private static void validateConnectionProvider(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            LOGGER.error("connectionProvider must not be null");
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
    }

    private ConnectionProvider requireConnectionProvider() {
        validateConnectionProvider(connectionProvider);
        return connectionProvider;
    }

    /**
     * Validates mandatory create-time attributes for profile creation.
     *
     * @param profileAttributes profile attributes supplied to create profile
     */
    private static void validateCreateMandatoryAttributes(ProfileAttributes profileAttributes) {
        if (profileAttributes == null) {
            throw new IllegalArgumentException("profileAttributes must not be null for create profile flow");
        }
        if (profileAttributes.getProvider() == null || profileAttributes.getProvider().isBlank()) {
            throw new IllegalArgumentException("provider is mandatory for create profile flow");
        }
    }

    /**
     * Drops this Select AI profile using DBMS_CLOUD_AI.DROP_PROFILE.
     *
     * @param force whether to force the drop operation
     * @return {@code true} when the drop operation completes
     * @throws SelectAIException when {@code DBMS_CLOUD_AI.DROP_PROFILE} fails
     */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        requireCreatedProfile("drop");
        String plsql = Sql.DROP_PROFILE.get();

        LOGGER.debug("Dropping SelectAI profile {} with force={} ", this.getProfileName(), force);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.setInt(2, force ? 1 : 0);
                    stmt.execute();
                    LOGGER.info("Successfully dropped profile {}", this.getProfileName());
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.DROP_PROFILE failed for profile {}", this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.DROP_PROFILE", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Enables this Select AI profile using DBMS_CLOUD_AI.ENABLE_PROFILE.
     *
     * @return {@code true} when the enable operation completes
     * @throws SelectAIException when {@code DBMS_CLOUD_AI.ENABLE_PROFILE} fails
     */
    @Override
    public boolean enable() throws SelectAIException {
        requireCreatedProfile("enable");
        String plsql = Sql.ENABLE_PROFILE.get();
        LOGGER.debug("Enabling SelectAI profile {}", this.getProfileName());
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.execute();
                    LOGGER.info("Successfully enabled profile {}", this.getProfileName());
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.ENABLE_PROFILE failed for profile {}", this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.ENABLE_PROFILE", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Disables this Select AI profile using DBMS_CLOUD_AI.DISABLE_PROFILE.
     *
     * @return {@code true} when the disable operation completes
     * @throws SelectAIException when {@code DBMS_CLOUD_AI.DISABLE_PROFILE} fails
     */
    @Override
    public boolean disable() throws SelectAIException {
        requireCreatedProfile("disable");
        String plsql = Sql.DISABLE_PROFILE.get();
        LOGGER.debug("Disabling SelectAI profile {}", this.getProfileName());
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.execute();
                    LOGGER.info("Successfully disabled profile {}", this.getProfileName());
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.DISABLE_PROFILE failed for profile {}", this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.DISABLE_PROFILE", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Calls DBMS_CLOUD_AI.GENERATE for the provided prompt and action.
     *
     * @param prompt natural language prompt
     * @param generateAction Select AI generation action
     * @return CLOB response returned by {@code DBMS_CLOUD_AI.GENERATE}
     * @throws SelectAIException when {@code DBMS_CLOUD_AI.GENERATE} fails
     */
    @Override
    public String generate(String prompt, GenerateAction generateAction) throws SelectAIException {
        return generate(prompt, generateAction, null, null);
    }

    @Override
    public String generate(String prompt, GenerateAction generateAction, ProfileAttributes profileAttributes) throws SelectAIException {
        return generate(prompt, generateAction, profileAttributes, null);
    }

    @Override
    public String generate(String prompt, GenerateAction generateAction, GenerateParams generateParams) throws SelectAIException {
        return generate(prompt, generateAction, null, generateParams);
    }

    @Override
    public String generate(String prompt, GenerateAction generateAction, ProfileAttributes profileAttributes, GenerateParams generateParams) throws SelectAIException {
        requireCreatedProfile("generate");
        if (prompt == null || prompt.isBlank()) {
            LOGGER.error("prompt must not be null or blank for profile {}", this.getProfileName());
            throw new IllegalArgumentException("prompt must not be null or blank");
        }
        if (generateAction == null) {
            LOGGER.error("generateAction must not be null for profile {}", this.getProfileName());
            throw new IllegalArgumentException("generateAction must not be null");
        }
        String attributesJson = profileAttributes == null ? null : profileAttributes.toJson();
        String paramsJson = generateParams == null ? null : generateParams.toJson();
        String plsql = Sql.GENERATE.get();
        LOGGER.debug("Executing DBMS_CLOUD_AI.GENERATE for profile {} with action {}", this.getProfileName(), generateAction);
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    setClobOrNull(stmt, 2, prompt);
                    stmt.setString(3, this.getProfileName());
                    stmt.setString(4, generateAction.name());
                    setClobOrNull(stmt, 5, attributesJson);
                    setClobOrNull(stmt, 6, paramsJson);
                    stmt.execute();
                    Clob clob = stmt.getClob(1);
                    String generateResponse = clob != null ? clob.getSubString(1, (int) clob.length()) : null;
                    LOGGER.debug("DBMS_CLOUD_AI.GENERATE completed for profile {} action {}", this.getProfileName(), generateAction);
                    return generateResponse;
                }
            });
        } catch (SQLException e) {
            String profileName = this.getProfileName();
            LOGGER.error("DBMS_CLOUD_AI.GENERATE failed: profile='{}', action='{}'",
                    profileName, generateAction, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.GENERATE", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Shortcut for generate(prompt, GenerateAction.runsql).
     *
     * @param prompt natural language prompt
     * @return generated SQL execution result/content
     * @throws SelectAIException when operation fails
     */
    @Override
    public String runsql(String prompt) throws SelectAIException {
        LOGGER.debug("Running runsql action for profile {}", this.getProfileName());
        return generate(prompt, GenerateAction.runsql);
    }

    @Override
    public String runsql(String prompt, GenerateParams generateParams) throws SelectAIException {
        LOGGER.debug("Running runsql action for profile {} with generate params", this.getProfileName());
        return generate(prompt, GenerateAction.runsql, generateParams);
    }

    /**
     * Shortcut for generate(prompt, GenerateAction.showsql).
     *
     * @param prompt natural language prompt
     * @return generated SQL text
     * @throws SelectAIException when operation fails
     */
    @Override
    public String showsql(String prompt) throws SelectAIException {
        LOGGER.debug("Running showsql action for profile {}", this.getProfileName());
        return generate(prompt, GenerateAction.showsql);
    }

    @Override
    public String showsql(String prompt, GenerateParams generateParams) throws SelectAIException {
        LOGGER.debug("Running showsql action for profile {} with generate params", this.getProfileName());
        return generate(prompt, GenerateAction.showsql, generateParams);
    }

    /**
     * Shortcut for generate(prompt, GenerateAction.explainsql).
     *
     * @param prompt natural language prompt
     * @return explanation of generated SQL
     * @throws SelectAIException when operation fails
     */
    @Override
    public String explainsql(String prompt) throws SelectAIException {
        LOGGER.debug("Running explainsql action for profile {}", this.getProfileName());
        return generate(prompt, GenerateAction.explainsql);
    }

    @Override
    public String explainsql(String prompt, GenerateParams generateParams) throws SelectAIException {
        LOGGER.debug("Running explainsql action for profile {} with generate params", this.getProfileName());
        return generate(prompt, GenerateAction.explainsql, generateParams);
    }

    /**
     * Shortcut for generate(prompt, GenerateAction.narrate).
     *
     * @param prompt natural language prompt
     * @return narrated response
     * @throws SelectAIException when operation fails
     */
    @Override
    public String narrate(String prompt) throws SelectAIException {
        LOGGER.debug("Running narrate action for profile {}", this.getProfileName());
        return generate(prompt, GenerateAction.narrate);
    }

    @Override
    public String narrate(String prompt, GenerateParams generateParams) throws SelectAIException {
        LOGGER.debug("Running narrate action for profile {} with generate params", this.getProfileName());
        return generate(prompt, GenerateAction.narrate, generateParams);
    }

    /**
     * Shortcut for generate(prompt, GenerateAction.showprompt).
     *
     * @param prompt natural language prompt
     * @return provider-facing prompt assembled by Select AI
     * @throws SelectAIException when operation fails
     */
    @Override
    public String showprompt(String prompt) throws SelectAIException {
        LOGGER.debug("Running showprompt action for profile {}", this.getProfileName());
        return generate(prompt, GenerateAction.showprompt);
    }

    @Override
    public String showprompt(String prompt, GenerateParams generateParams) throws SelectAIException {
        LOGGER.debug("Running showprompt action for profile {} with generate params", this.getProfileName());
        return generate(prompt, GenerateAction.showprompt, generateParams);
    }

    /**
     * Shortcut for generate(prompt, GenerateAction.chat).
     *
     * @param prompt natural language prompt
     * @return chat response
     * @throws SelectAIException when operation fails
     */
    @Override
    public String chat(String prompt) throws SelectAIException {
        LOGGER.debug("Running chat action for profile {}", this.getProfileName());
        return generate(prompt, GenerateAction.chat);
    }

    @Override
    public String chat(String prompt, GenerateParams generateParams) throws SelectAIException {
        LOGGER.debug("Running chat action for profile {} with generate params", this.getProfileName());
        return generate(prompt, GenerateAction.chat, generateParams);
    }

    @Override
    public Session chatSession(Conversation conversation) throws SelectAIException {
        return chatSession(conversation, false);
    }

    @Override
    public Session chatSession(Conversation conversation, boolean deleteOnClose) throws SelectAIException {
        requireCreatedProfile("chatSession");
        if (conversation == null) {
            LOGGER.error("conversation must not be null for profile chat session");
            throw new IllegalArgumentException("conversation must not be null");
        }

        String conversationId = conversation.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            LOGGER.debug("Creating conversation for profile {} chat session", this.getProfileName());
            conversationId = conversation.create();
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new SelectAIException("Conversation create did not return a conversation ID");
        }

        LOGGER.debug("Starting chat session for profile {} and conversation {}",
                this.getProfileName(), conversationId);
        return new DefaultSession(this, conversation, deleteOnClose);
    }

    /**
     * Sets a single profile attribute in the database and syncs local cached attributes.
     *
     * @param attributeName DBMS_CLOUD_AI profile attribute name
     * @param attributeValue string attribute value; {@code null} clears where supported
     * @return {@code true} when the attribute update completes
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException {
        requireCreatedProfile("setAttribute");
        if (attributeName == null || attributeName.isBlank()) {
            LOGGER.error("attributeName must not be null or blank for profile {}", this.getProfileName());
            throw new IllegalArgumentException("attributeName must not be null or blank");
        }
        final String plsql = Sql.SET_ATTRIBUTE.get();

        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.setString(2, attributeName);

                    setClobOrNull(stmt, 3, attributeValue);
                    stmt.execute();
                    syncLocalProfileAttributes(attributeName, attributeValue);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.SET_ATTRIBUTE failed for profile {} attribute {}",
                    this.getProfileName(), attributeName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.SET_ATTRIBUTE", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }


    /**
     * Sets a single profile attribute in the database and syncs local cached attributes.
     *
     * @param attributeName DBMS_CLOUD_AI profile attribute name
     * @param attributeValue boolean attribute value
     * @return {@code true} when the attribute update completes
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException {
        requireCreatedProfile("setAttribute");
        if (attributeName == null || attributeName.isBlank()) {
            LOGGER.error("attributeName must not be null or blank for profile {}", this.getProfileName());
            throw new IllegalArgumentException("attributeName must not be null or blank");
        }
        final String plsql = Sql.SET_ATTRIBUTE.get();

        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.setString(2, attributeName);
                    stmt.setBoolean(3, attributeValue);
                    stmt.execute();
                    syncLocalProfileAttributes(attributeName, String.valueOf(attributeValue));
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.SET_ATTRIBUTE failed for profile {} attribute {}",
                    this.getProfileName(), attributeName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.SET_ATTRIBUTE", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Sets a single profile attribute in the database and syncs local cached attributes.
     *
     * @param attributeName DBMS_CLOUD_AI profile attribute name
     * @param attributeValue integer attribute value
     * @return {@code true} when the attribute update completes
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException {
        requireCreatedProfile("setAttribute");
        if (attributeName == null || attributeName.isBlank()) {
            LOGGER.error("attributeName must not be null or blank for profile {}", this.getProfileName());
            throw new IllegalArgumentException("attributeName must not be null or blank");
        }
        if (attributeValue == null) {
            LOGGER.error("attributeValue must not be null for integer attribute {} on profile {}",
                    attributeName, this.getProfileName());
            throw new IllegalArgumentException("attributeValue must not be null");
        }
        final String plsql = Sql.SET_ATTRIBUTE.get();

        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.setString(2, attributeName);
                    stmt.setInt(3, attributeValue);
                    stmt.execute();
                    syncLocalProfileAttributes(attributeName, String.valueOf(attributeValue));
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.SET_ATTRIBUTE failed for profile {} attribute {}",
                    this.getProfileName(), attributeName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.SET_ATTRIBUTE", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Sets a single profile attribute in the database and syncs local cached attributes.
     *
     * @param attributeName DBMS_CLOUD_AI profile attribute name
     * @param attributeValue floating-point attribute value
     * @return {@code true} when the attribute update completes
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException {
        requireCreatedProfile("setAttribute");
        if (attributeName == null || attributeName.isBlank()) {
            LOGGER.error("attributeName must not be null or blank for profile {}", this.getProfileName());
            throw new IllegalArgumentException("attributeName must not be null or blank");
        }
        if (attributeValue == null) {
            LOGGER.error("attributeValue must not be null for float attribute {} on profile {}",
                    attributeName, this.getProfileName());
            throw new IllegalArgumentException("attributeValue must not be null");
        }
        final String plsql = Sql.SET_ATTRIBUTE.get();

        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.setString(2, attributeName);
                    stmt.setFloat(3, attributeValue);
                    stmt.execute();
                    syncLocalProfileAttributes(attributeName, String.valueOf(attributeValue));
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.SET_ATTRIBUTE failed for profile {} attribute {}",
                    this.getProfileName(), attributeName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.SET_ATTRIBUTE", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Sets profile attributes in bulk using DBMS_CLOUD_AI.SET_ATTRIBUTES.
     *
     * @param profileAttributes bulk profile attributes payload
     * @return {@code true} when the bulk update completes
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean setAttributes(ProfileAttributes profileAttributes) throws SelectAIException {
        requireCreatedProfile("setAttributes");
        LOGGER.debug("Setting profile attributes for profile {}", this.getProfileName());
        final String plsql = Sql.SET_ATTRIBUTES.get();

        String attributesJson = profileAttributes != null ? profileAttributes.toJson() : null;
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    setClobOrNull(stmt, 2, attributesJson);
                    stmt.execute();
                    this.profileAttributes = profileAttributes;
                    this.profileAttributesState.clear();
                    if (profileAttributes != null) {
                        this.profileAttributesState.putAll(profileAttributes.toAttributeMap());
                    }
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.SET_ATTRIBUTES failed for profile {}",
                    this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.SET_ATTRIBUTES", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Stores or removes guidance about generated SQL for this profile.
     * <p>
     * Positive feedback marks SQL as useful; negative feedback can include an
     * expected response and comments to guide later SQL generation. This
     * feedback is intended for generated SQL rather than general chat or RAG
     * responses.
     *
     * @param feedbackRequest feedback request payload
     * @return {@code true} when feedback submission executes successfully
     * @throws SelectAIException when {@code DBMS_CLOUD_AI.FEEDBACK} fails
     */
    @Override
    public boolean feedback(Feedback feedbackRequest) throws SelectAIException {
        requireCreatedProfile("feedback");
        if (feedbackRequest == null) {
            LOGGER.error("feedbackRequest must not be null");
            throw new IllegalArgumentException("feedbackRequest must not be null");
        }
        final boolean useSqlId = hasText(feedbackRequest.getSqlId());
        final boolean useSqlText = hasText(feedbackRequest.getSqlText());

        if (!useSqlId && !useSqlText) {
            LOGGER.error("Either sqlId or sqlText must be provided for feedback on profile {}",
                    this.getProfileName());
            throw new IllegalArgumentException("Either sqlId or sqlText must be provided");
        }

        final String plsql = useSqlId ? Sql.FEEDBACK_SQL_ID.get() : Sql.FEEDBACK_SQL_TEXT.get();
        LOGGER.debug("Executing DBMS_CLOUD_AI.FEEDBACK for profile {} using {}",
                this.getProfileName(), useSqlId ? "sqlId" : "sqlText");
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    if (useSqlId) {
                        stmt.setString(2, feedbackRequest.getSqlId());
                    } else {
                        setClobOrNull(stmt, 2, feedbackRequest.getSqlText());
                    }
                    stmt.setString(3, feedbackRequest.getFeedbackTypeValue());
                    setClobOrNull(stmt, 4, feedbackRequest.getResponse());
                    setClobOrNull(stmt, 5, feedbackRequest.getFeedbackContent());
                    if (feedbackRequest.getOperationValue() == null) {
                        stmt.setNull(6, Types.VARCHAR);
                    } else {
                        stmt.setString(6, feedbackRequest.getOperationValue());
                    }

                    stmt.execute();
                    LOGGER.info("Successfully submitted feedback for profile {}", this.getProfileName());
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.FEEDBACK failed for profile {}", this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.FEEDBACK", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Submits a single-object synthetic data generation request using this profile.
     *
     * @param request single-object request payload
     * @return {@code true} when underlying synthetic-data generation call succeeds
     * @throws SelectAIException when single-object generation cannot be executed
     */
    @Override
    public boolean generateSyntheticData(SyntheticDataSingleRequest request) throws SelectAIException {
        if (request == null) {
            LOGGER.error("SyntheticDataSingleRequest must not be null");
            throw new IllegalArgumentException("request must not be null");
        }
        return generateSyntheticDataSingle(
                request.getObjectName(),
                request.getOwnerName(),
                request.getRecordCount(),
                request.getUserPrompt(),
                request.getParams() == null ? null : request.getParams().toJson());
    }

    /**
     * Submits a batch synthetic data generation request using this profile.
     *
     * @param request batch request payload
     * @return {@code true} when underlying synthetic-data generation call succeeds
     * @throws SelectAIException when batch generation cannot be executed
     */
    @Override
    public boolean generateSyntheticData(SyntheticDataBatchRequest request) throws SelectAIException {
        if (request == null) {
            LOGGER.error("SyntheticDataBatchRequest must not be null");
            throw new IllegalArgumentException("request must not be null");
        }
        return generateSyntheticDataFromJson(request.getObjectListJson(), request.getParamsJson());
    }

    /**
     * Runs the single-object overload of {@code GENERATE_SYNTHETIC_DATA}.
     *
     * @param objectName target object name
     * @param ownerName optional owner/schema name
     * @param recordCount optional positive number of records to generate
     * @param userPrompt optional user prompt to guide generation
     * @param paramsJson optional generation parameters JSON
     * @return {@code true} when {@code DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA} executes successfully
     * @throws SelectAIException when generation fails
     */
    private boolean generateSyntheticDataSingle(String objectName,
                                                String ownerName,
                                                Integer recordCount,
                                                String userPrompt,
        String paramsJson) throws SelectAIException {
        requireCreatedProfile("generateSyntheticData");
        if (!hasText(objectName)) {
            LOGGER.error("objectName must not be null or blank for synthetic data generation");
            throw new IllegalArgumentException("objectName must not be null or blank");
        }
        if (recordCount != null && recordCount <= 0) {
            LOGGER.error("recordCount must be greater than 0 when provided for object {}", objectName);
            throw new IllegalArgumentException("recordCount must be greater than 0");
        }
        validateJsonIfPresent("params", paramsJson);

        final String plsql = Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get();
        LOGGER.debug("Generating synthetic data for object {} using profile {}", objectName, this.getProfileName());
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    stmt.setString(2, objectName);
                    if (ownerName == null) {
                        stmt.setNull(3, Types.VARCHAR);
                    } else {
                        stmt.setString(3, ownerName);
                    }
                    if (recordCount == null) {
                        stmt.setNull(4, Types.INTEGER);
                    } else {
                        stmt.setInt(4, recordCount);
                    }
                    setClobOrNull(stmt, 5, userPrompt);
                    setClobOrNull(stmt, 6, paramsJson);
                    stmt.execute();
                    LOGGER.info("Successfully generated synthetic data for object {}", objectName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA (single) failed for object {}", objectName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA (single)", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Runs batch synthetic-data generation with a JSON array of target object descriptors.
     *
     * @param objectList JSON array of target object descriptors
     * @param params optional params JSON
     * @return {@code true} when the PL/SQL call succeeds
     * @throws SelectAIException when generation fails
     */
    private boolean generateSyntheticDataFromJson(String objectList,
                                                  String params) throws SelectAIException {
        requireCreatedProfile("generateSyntheticData");
        if (!hasText(objectList)) {
            LOGGER.error("objectList must not be null or blank for synthetic data generation");
            throw new IllegalArgumentException("objectList must not be null or blank");
        }
        validateJsonIfPresent("objectList", objectList);
        validateJsonIfPresent("params", params);

        final String plsql = Sql.GENERATE_SYNTHETIC_DATA_MULTI.get();
        LOGGER.debug("Generating synthetic data for object list using profile {}", this.getProfileName());
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.getProfileName());
                    setClobOrNull(stmt, 2, objectList);
                    setClobOrNull(stmt, 3, params);
                    stmt.execute();
                    LOGGER.info("Successfully generated synthetic data for object list");
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA (multi) failed for profile {}",
                    this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA (multi)", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Calls DBMS_CLOUD_AI.SUMMARIZE for this profile.
     *
     * @param content inline content to summarize
     * @param credential_name credential used for location access
     * @param location_uri external content URI
     * @param userPrompt summary instruction
     * @param params typed summarize parameters
     * @return summary text returned by {@code DBMS_CLOUD_AI.SUMMARIZE}
     * @throws SelectAIException when summarization fails
     */
    @Override
    public String summarize(String content, String credential_name,
                            String location_uri, String userPrompt,
                            SummaryParams params)
            throws SelectAIException {
        requireCreatedProfile("summarize");
        if (!hasText(content) && !hasText(location_uri)) {
            LOGGER.error("Either content or location_uri must be provided for profile {}", this.getProfileName());
            throw new IllegalArgumentException("Either content or location_uri must be provided");
        }
        if (hasText(content) && hasText(location_uri)) {
            LOGGER.error("Either content or location_uri must be provided, not both, for profile {}",
                    this.getProfileName());
            throw new IllegalArgumentException("Either content or location_uri must be provided, not both");
        }
        if (hasText(location_uri) && !hasText(credential_name)) {
            LOGGER.error("credential_name must be provided when location_uri is used for profile {}",
                    this.getProfileName());
            throw new IllegalArgumentException("credential_name must be provided when location_uri is used");
        }

        boolean useLocation = hasText(location_uri);
        String plsql = useLocation ? Sql.SUMMARIZE_LOCATION.get() : Sql.SUMMARIZE.get();
        String paramsJson = params == null ? null : params.toJson();
        LOGGER.debug("Executing DBMS_CLOUD_AI.SUMMARIZE for profile {} using {} input",
                this.getProfileName(), useLocation ? "location_uri" : "content");
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    if (useLocation) {
                        stmt.setString(2, location_uri);
                        stmt.setString(3, credential_name);
                        stmt.setString(4, this.getProfileName());
                        setClobOrNull(stmt, 5, userPrompt);
                        setClobOrNull(stmt, 6, paramsJson);
                    } else {
                        setClobOrNull(stmt, 2, content);
                        stmt.setString(3, this.getProfileName());
                        setClobOrNull(stmt, 4, userPrompt);
                        setClobOrNull(stmt, 5, paramsJson);
                    }
                    stmt.execute();
                    Clob clob = stmt.getClob(1);
                    LOGGER.debug("DBMS_CLOUD_AI.SUMMARIZE completed for profile {}", this.getProfileName());
                    return clob == null ? null : clob.getSubString(1, (int) clob.length());
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.SUMMARIZE failed for profile {}", this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.SUMMARIZE", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Calls DBMS_CLOUD_AI.TRANSLATE for this profile.
     *
     * @param text source text
     * @return translated text returned by {@code DBMS_CLOUD_AI.TRANSLATE}
     * @throws SelectAIException when operation fails
     */
    @Override
    public String translate(String text) throws SelectAIException {
        return translate(text, null, null);
    }

    /**
     * Calls DBMS_CLOUD_AI.TRANSLATE for this profile with an explicit target language.
     *
     * @param text source text
     * @param targetLanguage target language name or code
     * @return translated text returned by {@code DBMS_CLOUD_AI.TRANSLATE}
     * @throws SelectAIException when operation fails
     */
    @Override
    public String translate(String text, String targetLanguage) throws SelectAIException {
        return translate(text, null, targetLanguage);
    }

    /**
     * Calls DBMS_CLOUD_AI.TRANSLATE for this profile.
     *
     * @param text source text
     * @param sourceLanguage source language name or code
     * @param targetLanguage target language name or code
     * @return translated text returned by {@code DBMS_CLOUD_AI.TRANSLATE}
     * @throws SelectAIException when operation fails
     */
    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage)
            throws SelectAIException {
        requireCreatedProfile("translate");
        if (!hasText(text)) {
            throw new IllegalArgumentException("text must not be null or blank");
        }
        String plsql = Sql.TRANSLATE.get();
        LOGGER.debug("Executing DBMS_CLOUD_AI.TRANSLATE for profile {}", this.getProfileName());
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.registerOutParameter(1, Types.CLOB);
                    stmt.setString(2, this.getProfileName());
                    setClobOrNull(stmt, 3, text);
                    stmt.setString(4, sourceLanguage);
                    stmt.setString(5, targetLanguage);
                    stmt.execute();
                    Clob clob = stmt.getClob(1);
                    String translated = clob != null ? clob.getSubString(1, (int) clob.length()) : null;
                    LOGGER.debug("DBMS_CLOUD_AI.TRANSLATE completed for profile {}", this.getProfileName());
                    return translated;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.TRANSLATE failed: profile='{}'", this.getProfileName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.TRANSLATE", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Returns the profile name.
     *
     * @return profile name bound to this instance
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Fetches and returns the current profile status from the database.
     *
     * @return current profile status
     * @throws SelectAIException when current profile metadata cannot be fetched
     */
    public String getStatus() throws SelectAIException {
        requireBoundProfile("getStatus");
        if (!this.databaseBacked) {
            LOGGER.debug("Returning configured profile status for pending profile {}", this.profileName);
            return this.status;
        }

        LOGGER.debug("Fetching current profile status for profile {}", this.getProfileName());
        try {
            refreshProfileMetadata();
            LOGGER.debug("Fetched current profile status for profile {}: {}", this.getProfileName(), this.status);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to fetch current profile status for profile {}", this.getProfileName(), e);
            throw e;
        }
        return status;
    }

    /**
     * Returns the profile description.
     *
     * @return profile description, or {@code null} when not loaded or unset
     */
    public String getDescription() {
        return description;
    }

    /**
     * Fetches current profile attributes from the database and refreshes the
     * local attribute state used by update helpers.
     *
     * @return current profile attributes
     * @throws SelectAIException when current attributes cannot be fetched
     */
    public ProfileAttributes getProfileAttributes() throws SelectAIException {
        requireBoundProfile("getProfileAttributes");
        if (!this.databaseBacked) {
            LOGGER.debug("Returning configured profile attributes for pending profile {}", this.profileName);
            return this.profileAttributes;
        }

        LOGGER.debug("Fetching current profile attributes for profile {}", this.getProfileName());
        try {
            this.profileAttributes = requireConnectionProvider().withConnection(
                    connection -> ProfileAttributeUtils.fetchProfileAttributes(connection, this.profileName)
            );
            this.profileAttributesState = new HashMap<>(this.profileAttributes.toAttributeMap());
            LOGGER.debug("Fetched current profile attributes for profile {}", this.getProfileName());
            return this.profileAttributes;
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch current profile attributes for profile {}", this.getProfileName(), e);
            throw new SelectAIException("Failed to fetch SelectAI profile attributes: " + this.profileName,
                    e, e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Refreshes local profile metadata from {@code USER_CLOUD_AI_PROFILES}.
     *
     * @throws SelectAIException when metadata cannot be fetched
     */
    private void refreshProfileMetadata() throws SelectAIException {
        ExistingProfileMetadata metadata = getProfile(requireConnectionProvider(), this.profileName);
        this.profileName = metadata.getProfileName();
        this.status = metadata.getStatus();
        this.description = metadata.getDescription();
    }

    private static void setClobOrNull(CallableStatement stmt, int parameterIndex, String value)
            throws SQLException {
        if (value != null) {
            stmt.setCharacterStream(parameterIndex, new StringReader(value), value.length());
        } else {
            stmt.setNull(parameterIndex, Types.CLOB);
        }
    }

    /**
     * Validates JSON input when optional JSON fields are supplied.
     *
     * @param fieldName logical field name used in error messages
     * @param jsonPayload JSON payload to validate
     */
    private static void validateJsonIfPresent(String fieldName, String jsonPayload) {
        if (!hasText(jsonPayload)) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(jsonPayload);
        } catch (Exception e) {
            LOGGER.error("Invalid JSON payload supplied for field {}", fieldName, e);
            throw new IllegalArgumentException(fieldName + " must be a valid JSON string", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Ensures profile-specific operations are invoked only on a profile-bound
     * instance.
     *
     * @param operation operation being invoked
     */
    private void requireBoundProfile(String operation) {
        if (!hasText(this.profileName)) {
            LOGGER.error("{} requires a profile-bound Profile instance", operation);
            throw new IllegalStateException(operation + " requires a profile-bound Profile instance");
        }
    }

    /**
     * Ensures profile operations that require a persisted database profile are
     * not invoked on configured-but-not-created profile objects.
     *
     * @param operation operation being invoked
     */
    private void requireCreatedProfile(String operation) {
        requireBoundProfile(operation);
        if (!this.databaseBacked) {
            LOGGER.error("{} requires a created profile; call create() first", operation);
            throw new IllegalStateException(operation + " requires a created profile; call create() first");
        }
    }

    /**
     * Updates local attribute map and rebuilds ProfileAttributes after attribute changes.
     *
     * @param attributeName DBMS_CLOUD_AI attribute name
     * @param attributeValue attribute value to cache; {@code null} removes the cached value
     */
    private void syncLocalProfileAttributes(String attributeName, String attributeValue) {
        if (attributeName == null || attributeName.isBlank()) {
            return;
        }
        String normalizedName = attributeName.trim().toLowerCase(Locale.ROOT);
        if (attributeValue == null) {
            profileAttributesState.remove(normalizedName);
        } else {
            profileAttributesState.put(normalizedName, attributeValue);
        }
        try {
            this.profileAttributes = ProfileAttributes.fromAttributeMap(profileAttributesState);
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to rebuild local ProfileAttributes for attribute {}; valuePresent={} after setAttribute",
                    normalizedName, attributeValue != null, e);
        }
    }

    /**
     * Creates a profile by invoking {@code DBMS_CLOUD_AI.CREATE_PROFILE}.
     *
     * @param connectionProvider provider used for the PL/SQL call
     * @param profileName profile name to create
     * @param description optional profile description
     * @param status optional initial status
     * @param profileAttributes profile attributes payload
     * @return {@code true} when the create procedure executes successfully
     * @throws SelectAIException when profile creation fails
     */
    private static boolean create(ConnectionProvider connectionProvider, String profileName,
                                  String description,
                                  String status,
                                  ProfileAttributes profileAttributes) throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (profileName == null || profileName.isBlank()) {
            LOGGER.error("profileName must not be null or blank when creating profile");
            throw new IllegalArgumentException("profileName must not be null or blank");
        }
        validateCreateMandatoryAttributes(profileAttributes);
        String plsql = Sql.CREATE_PROFILE.get();
        LOGGER.debug("Creating SelectAI profile {}", profileName);
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, profileName);
                    setClobOrNull(stmt, 2, profileAttributes != null ? profileAttributes.toJson() : null);
                    stmt.setString(3, status);
                    setClobOrNull(stmt, 4, description);
                    stmt.execute();
                    LOGGER.info("Successfully created profile {}", profileName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.CREATE_PROFILE failed for profile {}", profileName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.CREATE_PROFILE", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }


    /**
     * Fetches profile metadata from USER_CLOUD_AI_PROFILES.
     *
     * @param connectionProvider provider used for the metadata query
     * @param profileName profile name to fetch
     * @return profile metadata row
     * @throws SelectAIException when operation fails
     */
    private static ExistingProfileMetadata getProfile(ConnectionProvider connectionProvider, String profileName)
            throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (profileName == null || profileName.isBlank()) {
            LOGGER.error("profileName must not be null or blank when fetching profile");
            throw new IllegalArgumentException("profileName must not be null or blank");
        }
        final String sql = Sql.GET_PROFILE.get();
        LOGGER.debug("Fetching SelectAI profile: {}", profileName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, profileName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SelectAIException("SelectAI profile not found: " + profileName);
                        }
                        return new ExistingProfileMetadata(rs.getString("profile_name"),
                                rs.getString("status"), rs.getString("description"));
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch profile {}", profileName, e);
            throw new SelectAIException(
                    "Failed to fetch SelectAI profile: " + profileName,
                    e,
                    e.getErrorCode(),
                    e.getSQLState()
            );
        }
    }

    /**
     * Metadata row from {@code USER_CLOUD_AI_PROFILES}.
     */
    static class ExistingProfileMetadata {
        /** Profile name from the metadata view. */
        private String profileName;
        /** Profile status from the metadata view. */
        private String status;
        /** Profile description from the metadata view. */
        private String description;

        /**
         * Creates immutable metadata snapshot for an existing profile row.
         *
         * @param profileName profile name from USER_CLOUD_AI_PROFILES
         * @param status profile status value
         * @param description profile description value
         */
        public ExistingProfileMetadata(String profileName, String status, String description) {
            this.profileName = profileName;
            this.status = status;
            this.description = description;
        }

        /**
         * Returns the profile name.
         *
         * @return profile name from the metadata row
         */
        public String getProfileName() {
            return profileName;
        }

        /**
         * Returns the profile status.
         *
         * @return profile status from the metadata row
         */
        public String getStatus() {
            return status;
        }

        /**
         * Returns the profile description.
         *
         * @return profile description from the metadata row
         */
        public String getDescription() {
            return description;
        }
    }
}
