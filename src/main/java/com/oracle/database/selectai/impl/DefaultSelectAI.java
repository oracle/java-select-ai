/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import com.oracle.database.selectai.impl.ProfileAttributeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link SelectAI} implementation that coordinates credential,
 * profile, conversation, and vector index objects over a configured connection
 * provider.
 * <p>
 * Public application code should prefer {@link SelectAI#create(DataSource)}
 * or {@link SelectAI#create(DbConnectionConfig)}. Use {@link DataSource} for
 * applications that need a connection per SDK operation, such as
 * multi-threaded services. {@link DbConnectionConfig} supports
 * single-connection behavior for simple usage where the caller does not share
 * one SDK client concurrently across threads.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-about.html">
 *      About Select AI</a>
 */
final class DefaultSelectAI implements SelectAI {
    /** Logger for top-level Select AI operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSelectAI.class);

    /** Connection configuration supplied by the caller. */
    private final DbConnectionConfig dbConnectionConfig;
    /** Database connection wrapper, available only for DbConnectionConfig mode. */
    private final DbConnection dbConnection;
    /** Provider that controls connection acquisition and release for SDK operations. */
    private final ConnectionProvider connectionProvider;

    /**
     * Creates a SelectAI instance.
     *
     * @param dbConnectionConfig database connection configuration
     * @throws SelectAIException when initialization fails
     */
    DefaultSelectAI(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        this(dbConnectionConfig, SelectAIOptions.defaults());
    }

    /**
     * Creates a SelectAI instance with SDK execution options.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options
     * @throws SelectAIException when initialization fails
     */
    DefaultSelectAI(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        this(dbConnectionConfig, createDbConnection(dbConnectionConfig), options);
    }

    /**
     * Creates a SelectAI instance backed by a DataSource.
     * <p>
     * Each SDK operation borrows a connection from the DataSource and closes it
     * when the operation completes. If the DataSource is pooled, close normally
     * returns the connection to the pool.
     *
     * @param dataSource DataSource used to obtain per-operation connections
     */
    DefaultSelectAI(DataSource dataSource) {
        this(dataSource, SelectAIOptions.defaults());
    }

    /**
     * Creates a DataSource-backed SelectAI instance with SDK execution options.
     *
     * @param dataSource DataSource used to obtain per-operation connections
     * @param options SDK execution options
     */
    DefaultSelectAI(DataSource dataSource, SelectAIOptions options) {
        this(null, null, new DataSourceConnectionProvider(dataSource), options);
    }

    DefaultSelectAI(DbConnectionConfig dbConnectionConfig, DbConnection dbConnection) {
        this(dbConnectionConfig, dbConnection, SelectAIOptions.defaults());
    }

    DefaultSelectAI(DbConnectionConfig dbConnectionConfig, DbConnection dbConnection,
                    SelectAIOptions options) {
        this(dbConnectionConfig, dbConnection, new SingleConnectionProvider(dbConnection), options);
    }

    DefaultSelectAI(ConnectionProvider connectionProvider) {
        this(connectionProvider, SelectAIOptions.defaults());
    }

    DefaultSelectAI(ConnectionProvider connectionProvider, SelectAIOptions options) {
        this(null, null, connectionProvider, options);
    }

    private DefaultSelectAI(DbConnectionConfig dbConnectionConfig,
                            DbConnection dbConnection,
                            ConnectionProvider connectionProvider,
                            SelectAIOptions options) {
        if (connectionProvider == null) {
            LOGGER.error("ConnectionProvider cannot be null when initializing SelectAI");
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (dbConnectionConfig == null && dbConnection != null) {
            LOGGER.error("DbConnectionConfig cannot be null when DbConnection is supplied");
            throw new IllegalArgumentException("dbConnectionConfig must not be null when dbConnection is supplied");
        }
        if (dbConnectionConfig != null && dbConnection == null) {
            LOGGER.error("DbConnection cannot be null when DbConnectionConfig is supplied");
            throw new IllegalArgumentException("dbConnection must not be null when dbConnectionConfig is supplied");
        }
        if (dbConnectionConfig == null) {
            LOGGER.info("SelectAI initialized with DataSource-backed connection provider");
        } else {
            LOGGER.info("SelectAI initialized with SDK-owned JDBC connection");
        }
        this.dbConnectionConfig = dbConnectionConfig;
        this.dbConnection = dbConnection;
        this.connectionProvider = new JdbcExecutionConnectionProvider(connectionProvider, options);
    }

    private static DbConnection createDbConnection(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        if (dbConnectionConfig == null) {
            LOGGER.error("DbConnectionConfig cannot be null when initializing SelectAI");
            throw new IllegalArgumentException("dbConnectionConfig must not be null");
        }
        LOGGER.debug("Initializing SelectAI with user {}", dbConnectionConfig.getDbUser());
        return DefaultDbConnection.builder().dbUser(dbConnectionConfig.getDbUser())
                .dbPassword(dbConnectionConfig.getDbPassword())
                .walletPassword(dbConnectionConfig.getWalletPassword())
                .jdbcUrl(dbConnectionConfig.getJdbcUrl())
                .jdbcProperties(dbConnectionConfig.getJdbcProperties()).build();
    }

    private ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    /**
     * Releases resources owned by this SelectAI client.
     * <p>
     * DbConnectionConfig mode owns one JDBC connection and closes it here.
     * DataSource mode does not own the DataSource and therefore has no shared
     * resource to close; operation-level connections are already closed after
     * each SDK operation.
     *
     * @throws SelectAIException when an owned JDBC connection cannot be closed
     */
    @Override
    public void close() throws SelectAIException {
        if (dbConnection != null) {
            dbConnection.close();
        }
    }

    /**
     * Returns the retained JDBC connection for DbConnectionConfig mode.
     * <p>
     * DataSource mode does not retain one connection, so callers should use
     * their DataSource directly for custom JDBC work.
     *
     * @return retained JDBC connection
     * @throws IllegalStateException when this SelectAI client is DataSource-backed
     */
    @Override
    public Connection getConnection() {
        if (dbConnection == null) {
            throw new IllegalStateException("getConnection is available only for DbConnectionConfig mode");
        }
        return dbConnection.getConnection();
    }

    /**
     * Creates a credential object for database-stored provider or storage
     * authentication material.
     *
     * @param credentialConfig credential definition used by create/drop calls
     * @return credential object bound to this SelectAI connection context
     */
    @Override
    public Credential credential(CredentialConfig credentialConfig) {
        if (credentialConfig == null) {
            LOGGER.error("credentialConfig must not be null when creating credentials");
            throw new IllegalArgumentException("credentialConfig must not be null");
        }
        LOGGER.debug("Creating Credential instance for credential {}", credentialConfig.getCredentialName());
        return new DefaultCredential(this.getConnectionProvider(), credentialConfig);
    }


    /**
     * Opens a profile object for an existing AI profile in the current schema.
     * <p>
     * The profile may have been created by Java, PL/SQL, Python, or another
     * client.
     *
     * @param profileName existing profile name
     * @return profile object bound to the existing database profile
     * @throws SelectAIException when profile metadata or attributes cannot be loaded
     */
    public Profile profile(String profileName) throws SelectAIException {
        if (profileName == null || profileName.isBlank()) {
            LOGGER.error("profileName must not be null or blank when loading profile");
            throw new IllegalArgumentException("profileName must not be null or blank");
        }
        LOGGER.debug("Loading profile {}", profileName);
        return new DefaultProfile(this.getConnectionProvider(), profileName);
    }

    /**
     * Creates a profile object initialized with create-time metadata.
     *
     * @param profileName profile name
     * @param profileAttributes profile attribute payload used for creation
     * @param description optional profile description
     * @param status optional initial profile status
     * @return profile object initialized with supplied create details
     * @throws SelectAIException when profile object initialization fails
     */
    @Override
    public Profile profile(String profileName,
                           ProfileAttributes profileAttributes,
                           String description,
                           ProfileStatus status)
            throws SelectAIException {
        if (profileName == null || profileName.isBlank()) {
            LOGGER.error("profileName must not be null or blank when creating profile");
            throw new IllegalArgumentException("profileName must not be null or blank");
        }
        LOGGER.debug("Initializing profile {} for explicit create with status={}", profileName, status);
        return new DefaultProfile(getConnectionProvider(), profileName, profileAttributes, description, status);
    }

    /**
     * Lists AI profiles visible to the current schema.
     *
     * @return list of profile objects hydrated from USER_CLOUD_AI_PROFILES
     * @throws SelectAIException when operation fails
     */
    @Override
    public List<Profile> listProfiles() throws SelectAIException {
        LOGGER.debug("Listing SelectAI profiles");
        return listProfiles(Sql.LIST_PROFILES.get(), null);
    }

    /**
     * Lists AI profiles visible to the current schema whose names match a database
     * regular-expression pattern.
     *
     * @param profileNamePattern database regular-expression pattern matched against profile names
     * @return list of matching profile objects hydrated from USER_CLOUD_AI_PROFILES
     * @throws SelectAIException when operation fails
     */
    @Override
    public List<Profile> listProfiles(String profileNamePattern) throws SelectAIException {
        if (profileNamePattern == null || profileNamePattern.isBlank()) {
            LOGGER.error("profileNamePattern must not be null or blank when listing profiles");
            throw new IllegalArgumentException("profileNamePattern must not be null or blank");
        }
        LOGGER.debug("Listing SelectAI profiles with pattern {}", profileNamePattern);
        return listProfiles(Sql.LIST_PROFILES_BY_PATTERN.get(), profileNamePattern);
    }

    private List<Profile> listProfiles(String sql, String profileNamePattern) throws SelectAIException {
        List<Profile> profiles = new ArrayList<>();
        try {
            this.getConnectionProvider().withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    if (profileNamePattern != null) {
                        ps.setString(1, profileNamePattern);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String profileName = rs.getString("profile_name");
                            profiles.add(DefaultProfile.fromMetadata(this.getConnectionProvider(), profileName,
                                    ProfileAttributeUtils.fetchProfileAttributes(connection, profileName),
                                    rs.getString("description"), rs.getString("status")));
                        }
                        return profiles;
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("list of user_cloud_ai_profiles failed", e);
            throw new SelectAIException("Failed to execute user_cloud_ai_profiles", e,
                    e.getErrorCode(), e.getSQLState());
        }
        LOGGER.debug("Loaded {} SelectAI profiles", profiles.size());
        return profiles;
    }

    /**
     * Creates a vector-index object initialized from configuration.
     *
     * @param vectorIndexConfig vector index create-time configuration
     * @return vector-index object initialized from provided configuration
     * @throws SelectAIException when the vector-index object cannot be initialized
     */
    @Override
    public VectorIndex vectorIndex(VectorIndexConfig vectorIndexConfig)
            throws SelectAIException {
        if (vectorIndexConfig == null) {
            LOGGER.error("vectorIndexConfig must not be null when creating vector index");
            throw new IllegalArgumentException("vectorIndexConfig must not be null");
        }
        LOGGER.debug("Creating VectorIndex for index {} using config", vectorIndexConfig.getIndexName());
        return new DefaultVectorIndex(this.getConnectionProvider(), vectorIndexConfig);
    }

    /**
     * Lists vector indexes visible to the current schema.
     *
     * @return list of vector index objects hydrated from USER_CLOUD_VECTOR_INDEXES
     * @throws SelectAIException when operation fails
     */
    @Override
    public List<VectorIndex> listVectorIndexes() throws SelectAIException {
        return listVectorIndexes(".*");
    }

    /**
     * Lists vector indexes visible to the current schema whose names match a
     * database regular-expression pattern.
     *
     * @param indexNamePattern regular-expression pattern matched against vector-index names
     * @return matching vector index objects
     * @throws SelectAIException when operation fails
     */
    @Override
    public List<VectorIndex> listVectorIndexes(String indexNamePattern) throws SelectAIException {
        if (indexNamePattern == null || indexNamePattern.isBlank()) {
            throw new IllegalArgumentException("indexNamePattern must not be null or blank");
        }
        final String sql = Sql.LIST_VECTOR_INDEXES.get();
        List<VectorIndex> vectorIndexes = new ArrayList<>();
        LOGGER.debug("Listing SelectAI vector indexes with pattern {}", indexNamePattern);
        try {
            this.getConnectionProvider().withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, indexNamePattern);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String indexName = rs.getString("index_name");
                            VectorIndexAttributes vectorIndexAttributes =
                                    DefaultVectorIndex.fetchVectorIndexAttributes(this.getConnectionProvider(), indexName);
                            VectorIndex vectorIndex = new DefaultVectorIndex(this.getConnectionProvider(),
                                    indexName,
                                    rs.getString("description"),
                                    rs.getString("status"),
                                    vectorIndexAttributes);
                            vectorIndexes.add(vectorIndex);
                            LOGGER.debug("Discovered vector index {}", vectorIndex.getIndexName());
                        }
                    }
                    return vectorIndexes;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to list vector indexes with pattern {}", indexNamePattern, e);
            throw new SelectAIException("Failed to list vector indexes", e,
                    e.getErrorCode(), e.getSQLState());
        }
        LOGGER.debug("Completed listing vector indexes; count={}", vectorIndexes.size());
        return vectorIndexes;
    }

    /**
     * Opens a vector-index object for an existing vector index in the current schema.
     * <p>
     * The vector index may have been created by Java, PL/SQL, Python, or
     * another client.
     *
     * @param indexName existing vector index name
     * @return vector-index object bound to the existing database vector index
     * @throws SelectAIException when vector-index metadata cannot be loaded
     */
    @Override
    public VectorIndex vectorIndex(String indexName) throws SelectAIException {
        if (indexName == null || indexName.isBlank()) {
            LOGGER.error("indexName must not be null or blank when loading vector index");
            throw new IllegalArgumentException("indexName must not be null or blank");
        }
        LOGGER.debug("Loading VectorIndex instance for index {}", indexName);
        return new DefaultVectorIndex(this.getConnectionProvider(), indexName);
    }

    /**
     * Lists conversations visible to the current schema.
     *
     * @return list of conversation objects hydrated from USER_CLOUD_AI_CONVERSATIONS
     * @throws SelectAIException when operation fails
     */
    @Override
    public List<Conversation> listConversations() throws SelectAIException {
        List<Conversation> conversations = new ArrayList<>();
        String sql = Sql.LIST_CONVERSATIONS.get();

        LOGGER.debug("Listing SelectAI conversations");
        try {
            getConnectionProvider().withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ConversationAttributes conversationAttributes = ConversationAttributes.builder()
                                .title(rs.getString("conversation_title"))
                                .description(rs.getString("description"))
                                .retentionDays(getNullableInteger(rs, "retention_days"))
                                .conversationLength(getNullableInteger(rs, "conversation_length"))
                                .build();
                        conversations.add(new DefaultConversation(this.getConnectionProvider(),
                                rs.getString("conversation_id"), conversationAttributes));
                    }
                    return conversations;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("list of USER_CLOUD_AI_CONVERSATIONS failed", e);
            throw new SelectAIException("Failed to execute USER_CLOUD_AI_CONVERSATIONS", e,
                    e.getErrorCode(), e.getSQLState());
        }
        LOGGER.debug("Listed {} conversations", conversations.size());
        return conversations;
    }

    /**
     * Creates a conversation object initialized with attributes.
     *
     * @param conversationAttributes attributes used later by {@link Conversation#create()}
     * @return conversation object created with provided attributes
     * @throws SelectAIException when conversation object initialization fails
     */
    @Override
    public Conversation conversation(ConversationAttributes conversationAttributes) throws SelectAIException {
        if (conversationAttributes == null) {
            LOGGER.error("conversationAttributes must not be null when creating conversation");
            throw new IllegalArgumentException("conversationAttributes must not be null");
        }
        LOGGER.debug("Creating conversation with provided attributes");
        return new DefaultConversation(this.getConnectionProvider(), conversationAttributes);
    }

    /**
     * Opens a conversation object for an existing conversation by ID.
     *
     * @param conversationId existing conversation ID
     * @return conversation object bound to the existing database conversation
     * @throws SelectAIException when conversation metadata cannot be loaded
     */
    @Override
    public Conversation conversation(String conversationId) throws SelectAIException {
        if (conversationId == null || conversationId.isBlank()) {
            LOGGER.error("conversationId must not be null or blank when loading conversation");
            throw new IllegalArgumentException("conversationId must not be null or blank");
        }
        LOGGER.debug("Loading conversation {}", conversationId);
        return new DefaultConversation(this.getConnectionProvider(), conversationId);
    }

    private static Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }
}
