/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.ConversationPrompt;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link Conversation} implementation backed by
 * {@code DBMS_CLOUD_AI} conversation procedures and metadata views.
 * <p>
 * Instances initialized with attributes become database-backed after
 * {@link #create()} succeeds. Instances opened by conversation ID are loaded
 * from database metadata during construction.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI conversation reference</a>
 */
final class DefaultConversation implements Conversation {
    /** Logger for conversation lifecycle and metadata operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConversation.class);

    /** Provider that controls connection lifecycle for conversation operations. */
    private final ConnectionProvider connectionProvider;
    /** Configuration used when a conversation is initialized before creation. */
    private ConversationConfig conversationConfig;
    /** Conversation ID bound to this instance, or {@code null} before creation. */
    private String conversationId;
    /** Current conversation attributes snapshot. */
    private ConversationAttributes conversationAttributes;
    /** Whether this object is backed by a conversation persisted in the database. */
    private boolean databaseBacked;

    /**
     * Creates an internal empty conversation instance used when materializing
     * already-loaded metadata.
     *
     * @param connectionProvider provider used for conversation operations
     */
    DefaultConversation(ConnectionProvider connectionProvider) {
        validateConnectionProvider(connectionProvider);
        this.connectionProvider = connectionProvider;
        LOGGER.debug("Initialized empty Conversation metadata instance");
    }

    /**
     * Loads an existing conversation by ID.
     *
     * @param connectionProvider provider used for conversation operations
     * @param conversationId conversation identifier
     * @throws SelectAIException when initialization fails
     */
    DefaultConversation(ConnectionProvider connectionProvider,
                        String conversationId) throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (conversationId == null || conversationId.isBlank()) {
            LOGGER.error("conversationId must not be null or blank when loading conversation");
            throw new IllegalArgumentException("conversationId must not be null or blank");
        }
        this.connectionProvider = connectionProvider;
        this.conversationId = conversationId;
        LOGGER.debug("Loading conversation {}", this.conversationId);
        ExistingConversationMetadata existingConversationMetadata =
                getConversation(connectionProvider, conversationId);
        this.conversationAttributes = buildConversationAttributes(existingConversationMetadata);
        this.databaseBacked = true;
        LOGGER.debug("Loaded existing conversation {} with {}",
                this.conversationId, describeConversationAttributesForLog(this.conversationAttributes));
    }

    /**
     * Initializes a new conversation object with attributes for later creation.
     *
     * @param connectionProvider provider used for conversation operations
     * @param conversationAttributes conversation attributes payload
     * @throws SelectAIException when initialization fails
     */
    DefaultConversation(ConnectionProvider connectionProvider,
                        ConversationAttributes conversationAttributes)
            throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (conversationAttributes == null) {
            LOGGER.error("conversationAttributes must not be null when creating conversation");
            throw new IllegalArgumentException("conversationAttributes must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.conversationAttributes = conversationAttributes;
        this.conversationConfig = new ConversationConfig(null, conversationAttributes);
        this.databaseBacked = false;
    }

    /**
     * Creates a conversation object from metadata that has already been loaded.
     *
     * @param connectionProvider provider used for conversation operations
     * @param conversationId conversation identifier
     * @param conversationAttributes loaded conversation attributes
     * @throws SelectAIException when initialization fails
     */
    DefaultConversation(ConnectionProvider connectionProvider,
                        String conversationId,
                        ConversationAttributes conversationAttributes)
            throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        this.connectionProvider = connectionProvider;
        this.conversationId = conversationId;
        this.conversationAttributes = conversationAttributes;
        this.databaseBacked = true;
        LOGGER.debug("Created existing conversation {} with {}",
                this.conversationId, describeConversationAttributesForLog(this.conversationAttributes));
    }

    /**
     * Drops this conversation via DBMS_CLOUD_AI.DROP_CONVERSATION.
     *
     * @param force whether to force the drop operation
     * @return {@code true} when the conversation is dropped successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        requireCreatedConversation("drop");

        final String plsql = Sql.DROP_CONVERSATION.get();

        LOGGER.debug("Dropping SelectAI conversation {} with force={}", this.conversationId, force);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.conversationId);
                    stmt.setInt(2, force ? 1 : 0);
                    stmt.execute();
                    LOGGER.info("Successfully dropped conversation {}", this.conversationId);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.DROP_CONVERSATION failed for conversation {}", this.conversationId, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.DROP_CONVERSATION", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Creates the configured conversation, stores the returned ID locally, and
     * marks this instance as database-backed.
     *
     * @return conversation ID returned by {@code DBMS_CLOUD_AI.CREATE_CONVERSATION}
     * @throws SelectAIException when the create operation fails
     */
    @Override
    public String create() throws SelectAIException {
        LOGGER.debug("Creating conversation from attributes");
        this.conversationId = create(conversationAttributes);
        this.databaseBacked = true;
        LOGGER.info("Created conversation {} with {}",
                this.conversationId, describeConversationAttributesForLog(this.conversationAttributes));
        return this.conversationId;
    }

    /**
     * Returns current conversation attributes.
     * <p>
     * For a configured conversation that has not been created yet, this returns
     * the in-memory attributes supplied by the caller. For a conversation bound
     * to a database conversation ID, this refreshes metadata from the database
     * before returning attributes so changes made through another SDK object or
     * database session are visible.
     *
     * @return conversation attributes currently associated with this instance
     * @throws SelectAIException when bound conversation metadata cannot be fetched
     */
    @Override
    public ConversationAttributes getConversationAttributes() throws SelectAIException {
        if (this.conversationId == null || this.conversationId.isBlank()) {
            return this.conversationAttributes;
        }
        LOGGER.debug("Refreshing attributes for conversation {}", this.conversationId);
        ExistingConversationMetadata existingConversationMetadata =
                getConversation(requireConnectionProvider(), this.conversationId);
        this.conversationAttributes = buildConversationAttributes(existingConversationMetadata);
        return this.conversationAttributes;
    }

    /**
     * Updates conversation attributes in the database.
     *
     * @param conversationAttributes attributes to persist
     * @return {@code true} when the update succeeds
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean setAttributes(ConversationAttributes conversationAttributes) throws SelectAIException {
        requireCreatedConversation("setAttributes");
        if (conversationAttributes == null) {
            LOGGER.error("conversationAttributes must not be null for conversation {}", this.conversationId);
            throw new IllegalArgumentException("conversationAttributes must not be null");
        }

        final String plsql = Sql.UPDATE_CONVERSATION.get();

        LOGGER.debug("Updating attributes for conversation {}", this.conversationId);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.conversationId);
                    setClobOrNull(stmt, 2, conversationAttributes.toJson());
                    stmt.execute();
                    this.conversationAttributes = conversationAttributes;
                    LOGGER.info("Successfully updated attributes for conversation {}", this.conversationId);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.UPDATE_CONVERSATION failed for conversation {}", this.conversationId, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.UPDATE_CONVERSATION", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Lists prompt metadata rows for this conversation.
     *
     * @return conversation prompt rows ordered by creation time
     * @throws SelectAIException when prompt metadata cannot be fetched
     */
    @Override
    public List<ConversationPrompt> listPrompts() throws SelectAIException {
        requireCreatedConversation("list conversation prompts");
        final String sql = Sql.LIST_CONVERSATION_PROMPTS.get();

        LOGGER.debug("Listing prompts for conversation {}", this.conversationId);
        try {
            return requireConnectionProvider().withConnection(connection -> {
                List<ConversationPrompt> prompts = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, this.conversationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            prompts.add(mapConversationPrompt(rs));
                        }
                    }
                }
                LOGGER.debug("Listed {} prompt(s) for conversation {}", prompts.size(), this.conversationId);
                return prompts;
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to list prompts for conversation {}", this.conversationId, e);
            throw new SelectAIException("Failed to list conversation prompts", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Deletes a conversation prompt without force.
     *
     * @param conversationPromptId conversation prompt identifier
     * @return {@code true} when delete succeeds
     * @throws SelectAIException when prompt delete fails
     */
    @Override
    public boolean deletePrompt(String conversationPromptId) throws SelectAIException {
        return deletePrompt(conversationPromptId, false);
    }

    /**
     * Deletes a conversation prompt.
     *
     * @param conversationPromptId conversation prompt identifier
     * @param force whether to force delete
     * @return {@code true} when delete succeeds
     * @throws SelectAIException when prompt delete fails
     */
    @Override
    public boolean deletePrompt(String conversationPromptId, boolean force) throws SelectAIException {
        requireCreatedConversation("delete conversation prompt");
        if (conversationPromptId == null || conversationPromptId.isBlank()) {
            LOGGER.error("conversationPromptId must not be null or blank for conversation {}", this.conversationId);
            throw new IllegalArgumentException("conversationPromptId must not be null or blank");
        }
        final String plsql = Sql.DELETE_CONVERSATION_PROMPT.get();

        LOGGER.debug("Deleting prompt {} for conversation {} with force={}",
                conversationPromptId, this.conversationId, force);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, conversationPromptId);
                    stmt.setInt(2, force ? 1 : 0);
                    stmt.execute();
                    LOGGER.info("Successfully deleted prompt {} for conversation {}",
                            conversationPromptId, this.conversationId);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.DELETE_CONVERSATION_PROMPT failed for prompt {}",
                    conversationPromptId, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.DELETE_CONVERSATION_PROMPT", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }


    /**
     * Returns the configuration wrapper currently bound to this instance.
     *
     * @return conversation configuration, or {@code null} when not initialized
     */
    public ConversationConfig getConversationConfig() {
        return conversationConfig;
    }

    /**
     * Returns the conversation identifier.
     *
     * @return conversation ID for this instance, or {@code null} when not yet created/loaded
     */
    public String getConversationId() {
        return conversationId;
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

    private static void setClobOrNull(CallableStatement stmt, int parameterIndex, String value)
            throws SQLException {
        if (value != null) {
            stmt.setCharacterStream(parameterIndex, new StringReader(value), value.length());
        } else {
            stmt.setNull(parameterIndex, Types.CLOB);
        }
    }

    private void requireConversationId(String operation) {
        if (this.conversationId == null || this.conversationId.isBlank()) {
            LOGGER.error("Cannot {} because conversationId is null or blank", operation);
            throw new IllegalStateException(
                    operation + " requires a conversation-bound Conversation instance. " +
                            "Open a conversation with SelectAI.conversation(conversationId) or create one before calling this method."
            );
        }
    }

    private void requireCreatedConversation(String operation) {
        if (!this.databaseBacked && this.conversationAttributes != null) {
            LOGGER.error("{} requires a created conversation; call create() first", operation);
            throw new IllegalStateException(operation + " requires a created conversation; call create() first");
        }
        requireConversationId(operation);
        if (!this.databaseBacked) {
            LOGGER.error("{} requires a created conversation; call create() first", operation);
            throw new IllegalStateException(operation + " requires a created conversation; call create() first");
        }
    }

    /**
     * Creates a conversation via DBMS_CLOUD_AI.CREATE_CONVERSATION and returns conversation ID.
     *
     * @param conversationAttributes attributes used to create the conversation
     * @return conversation ID returned by DBMS_CLOUD_AI.CREATE_CONVERSATION
     * @throws SelectAIException when operation fails
     */
    private String create(ConversationAttributes conversationAttributes)
            throws SelectAIException {
        if (conversationAttributes == null) {
            LOGGER.error("conversationAttributes must not be null when creating conversation");
            throw new IllegalArgumentException("conversationConfig must not be null");
        }
        final String plsql = Sql.CREATE_CONVERSATION.get();

        LOGGER.debug("Creating SelectAI conversation");
        try {
            return requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.registerOutParameter(1, Types.VARCHAR);
                    setClobOrNull(stmt, 2, conversationAttributes.toJson());
                    stmt.execute();
                    String conversationId = stmt.getString(1);
                    LOGGER.info("Successfully created conversation with id {}", conversationId);
                    return  conversationId;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.CREATE_CONVERSATION failed", e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.CREATE_CONVERSATION", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Fetches conversation metadata by ID from USER_CLOUD_AI_CONVERSATIONS.
     *
     * @param connectionProvider provider used for the metadata query
     * @param conversationId conversation ID to fetch
     * @return metadata row mapped as {@link ExistingConversationMetadata}
     * @throws SelectAIException when operation fails
     */
    private static ExistingConversationMetadata getConversation(ConnectionProvider connectionProvider, String conversationId) throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (conversationId == null || conversationId.isBlank()) {
            LOGGER.error("conversationId must not be null or blank when fetching conversation");
            throw new IllegalArgumentException("conversationId must not be null or blank");
        }
        final String sql = Sql.GET_CONVERSATION.get();
        LOGGER.debug("Fetching SelectAI conversation: {}", conversationId);
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, conversationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            LOGGER.warn("SelectAI conversation not found: {}", conversationId);
                            throw new SelectAIException("SelectAI conversation not found: " + conversationId);
                        }
                        return new ExistingConversationMetadata(
                                rs.getString("conversation_id"),
                                rs.getString("conversation_title"),
                                rs.getString("description"),
                                getNullableInteger(rs, "retention_days"),
                                getNullableInteger(rs, "conversation_length")
                        );
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch conversation {}", conversationId, e);
            throw new SelectAIException(
                    "Failed to fetch SelectAI conversation: " + conversationId,
                    e,
                    e.getErrorCode(),
                    e.getSQLState()
            );
        }
    }

    /**
     * Builds ConversationAttributes from fetched metadata.
     *
     * @param existingConversationMetadata metadata row to convert
     * @return mapped {@link ConversationAttributes}; returns {@code null} when input metadata is null
     */
    private ConversationAttributes buildConversationAttributes(ExistingConversationMetadata existingConversationMetadata) {
        if (existingConversationMetadata == null) {
            LOGGER.warn("ExistingConversationMetadata is null; returning null ConversationAttributes");
            return null;
        }
        return ConversationAttributes.builder()
                .title(existingConversationMetadata.getConversationTitle())
                .description(existingConversationMetadata.getDescription())
                .retentionDays(existingConversationMetadata.getRetentionDays())
                .conversationLength(existingConversationMetadata.getConversationLength())
                .build();
    }

    private static Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private static Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private static ConversationPrompt mapConversationPrompt(ResultSet rs) throws SQLException {
        return ConversationPrompt.builder()
                .conversationPromptId(rs.getString("conversation_prompt_id"))
                .conversationId(rs.getString("conversation_id"))
                .conversationTitle(rs.getString("conversation_title"))
                .profileName(rs.getString("profile_name"))
                .promptAction(rs.getString("prompt_action"))
                .prompt(rs.getString("prompt"))
                .promptResponse(rs.getString("prompt_response"))
                .created(getTimestamp(rs, "created"))
                .modified(getTimestamp(rs, "modified"))
                .clientIdentifier(rs.getString("client_identifier"))
                .clientIp(rs.getString("client_ip"))
                .sid(getNullableLong(rs, "sid"))
                .serialNumber(getNullableLong(rs, "serial#"))
                .build();
    }

    private static Timestamp getTimestamp(ResultSet rs, String columnName) throws SQLException {
        return rs.getTimestamp(columnName);
    }

    /**
     * Returns a non-sensitive attribute summary for logs.
     * <p>
     * Conversation title and description are caller-supplied and may contain
     * personal, customer, or business-sensitive information. Do not log their
     * values.
     *
     * @param attributes conversation attributes to summarize
     * @return log-safe attribute summary
     */
    static String describeConversationAttributesForLog(ConversationAttributes attributes) {
        if (attributes == null) {
            return "attributesPresent=false";
        }
        return "attributesPresent=true"
                + ", titlePresent=" + hasText(attributes.getTitle())
                + ", descriptionPresent=" + hasText(attributes.getDescription())
                + ", retentionDaysPresent=" + (attributes.getRetentionDays() != null)
                + ", conversationLengthPresent=" + (attributes.getConversationLength() != null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Metadata row from {@code USER_CLOUD_AI_CONVERSATIONS}.
     */
    static class ExistingConversationMetadata {
        /** Conversation ID from the metadata view. */
        private final String conversationId;
        /** Conversation title from the metadata view. */
        private final String conversationTitle;
        /** Conversation description from the metadata view. */
        private final String description;
        /** Retention period from the metadata view. */
        private final Integer retentionDays;
        /** Conversation history length from the metadata view. */
        private final Integer conversationLength;

        ExistingConversationMetadata(String conversationId, String conversationTitle, String description,
                                     Integer retentionDays, Integer conversationLength) {
            this.conversationId = conversationId;
            this.conversationTitle = conversationTitle;
            this.description = description;
            this.retentionDays = retentionDays;
            this.conversationLength = conversationLength;
        }

        /**
         * Returns the conversation identifier.
         * @return conversation ID from metadata row
         */
        public String getConversationId() {
            return conversationId;
        }

        /**
         * Returns the conversation title.
         * @return title from metadata row
         */
        public String getConversationTitle() {
            return conversationTitle;
        }

        /**
         * Returns the description.
         * @return description from metadata row
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns configured retention days.
         * @return retention days from metadata row
         */
        public Integer getRetentionDays() {
            return retentionDays;
        }

        /**
         * Returns configured conversation length.
         * @return conversation length from metadata row
         */
        public Integer getConversationLength() {
            return conversationLength;
        }
    }

    /**
     * In-memory configuration used before or after conversation creation.
     */
    static class ConversationConfig {
        /** Optional conversation ID. */
        private final String conversationId;
        /** Conversation attributes bound to this configuration. */
        private final ConversationAttributes conversationAttributes;

        ConversationConfig(String conversationId, ConversationAttributes conversationAttributes) {
            this.conversationId = conversationId;
            this.conversationAttributes = conversationAttributes;
        }

        /**
         * Returns the conversation identifier.
         * @return configured conversation ID
         */
        public String getConversationId() {
            return conversationId;
        }

        /**
         * Returns current conversation attributes.
         * @return configured conversation attributes
         */
        public ConversationAttributes getConversationAttributes() {
            return conversationAttributes;
        }
    }
}
