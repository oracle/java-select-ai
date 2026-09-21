/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Default {@link VectorIndex} implementation backed by
 * {@code DBMS_CLOUD_AI} vector index procedures.
 * <p>
 * Instances initialized from configuration become database-backed after
 * {@link #create()} succeeds. Instances opened by index name are loaded from
 * database metadata during construction.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-retrieval-augmented-generation.html">
 *      Select AI with Retrieval Augmented Generation</a>
 */
final class DefaultVectorIndex implements VectorIndex {
    /** Logger for vector index lifecycle and attribute operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultVectorIndex.class);

    /** Provider that controls connection lifecycle for vector-index operations. */
    private final ConnectionProvider connectionProvider;
    /** Vector index name bound to this instance. */
    private String indexName;
    /** Vector index description loaded from metadata or config. */
    private String description;
    /** Vector index status loaded from metadata or config. */
    private String status;
    /** Current vector index attributes snapshot. */
    private VectorIndexAttributes vectorIndexAttributes;
    /** Create-time vector index configuration. */
    private VectorIndexConfig vectorIndexConfig;
    /** Whether this object is backed by a vector index persisted in the database. */
    private boolean databaseBacked;

    /**
     * Creates an internal empty vector-index instance used when materializing
     * already-loaded metadata.
     *
     * @param connectionProvider provider used for vector-index operations
     */
    DefaultVectorIndex(ConnectionProvider connectionProvider) {
        validateConnectionProvider(connectionProvider);
        this.connectionProvider = connectionProvider;
        LOGGER.debug("Initialized VectorIndex helper with provider {}", connectionProvider.getClass().getSimpleName());
    }

    /**
     * Initializes a vector-index object from create-time configuration.
     *
     * @param connectionProvider provider used for vector-index operations
     * @param vectorIndexConfig vector index configuration
     * @throws SelectAIException when initialization fails
     */
    DefaultVectorIndex(ConnectionProvider connectionProvider,
                       VectorIndexConfig vectorIndexConfig)
            throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (vectorIndexConfig == null) {
            LOGGER.error("vectorIndexConfig must not be null when creating VectorIndex");
            throw new IllegalArgumentException("vectorIndexConfig must not be null");
        }
        this.connectionProvider = connectionProvider;
        this.indexName = vectorIndexConfig.getIndexName();
        this.description = vectorIndexConfig.getDescription();
        this.status = vectorIndexConfig.getStatusValue();
        this.vectorIndexAttributes = vectorIndexConfig.getVectorIndexAttributes();
        this.vectorIndexConfig = vectorIndexConfig;
        this.databaseBacked = false;
        LOGGER.debug("Initialized VectorIndex for index={} from config", this.indexName);
    }

    /**
     * Opens a vector-index object for an existing vector index in the current schema.
     *
     * @param connectionProvider provider used for vector-index operations
     * @param indexName vector index name
     * @throws SelectAIException when initialization fails
     */
    DefaultVectorIndex(ConnectionProvider connectionProvider,
                       String indexName) throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        if (indexName == null || indexName.isBlank()) {
            LOGGER.error("indexName must not be null or blank when loading vector index");
            throw new IllegalArgumentException("indexName must not be null or blank");
        }
        this.connectionProvider = connectionProvider;
        this.indexName = indexName;
        LOGGER.debug("Loading VectorIndex metadata for index={}", this.indexName);
        ExistingVectorIndexMetadata existingVectorIndexMetadata = get(connectionProvider, this.indexName);
        if (existingVectorIndexMetadata == null) {
            LOGGER.warn("Vector index {} was not found in USER_CLOUD_VECTOR_INDEXES", this.indexName);
            throw new SelectAIException("Vector index not found: " + this.indexName);
        }
        this.indexName = existingVectorIndexMetadata.getIndexName();
        this.description = existingVectorIndexMetadata.getDescription();
        this.status = existingVectorIndexMetadata.getStatus();
        this.vectorIndexAttributes = fetchVectorIndexAttributes(connectionProvider, this.indexName);
        this.databaseBacked = true;
        LOGGER.debug("Loaded VectorIndex metadata index={}, status={}, descriptionPresent={}, attributesPresent={}",
                this.indexName, this.status, this.description != null && !this.description.isBlank(),
                this.vectorIndexAttributes != null);
    }

    DefaultVectorIndex(ConnectionProvider connectionProvider,
                       String indexName,
                       String description,
                       String status,
                       VectorIndexAttributes vectorIndexAttributes) {
        validateConnectionProvider(connectionProvider);
        this.connectionProvider = connectionProvider;
        this.indexName = indexName;
        this.description = description;
        this.status = status;
        this.vectorIndexAttributes = vectorIndexAttributes;
        this.databaseBacked = true;
    }

    /**
     * Returns vector index name.
     *
     * @return index name currently bound to this instance
     */
    @Override
    public String getIndexName() {
        return this.indexName;
    }

    /**
     * Returns vector index description.
     *
     * @return vector index description loaded from metadata or configuration
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Fetches and returns current vector index status from the database.
     *
     * @return current vector index status
     * @throws SelectAIException when current vector index metadata cannot be fetched
     */
    @Override
    public String getStatus() throws SelectAIException {
        requireBoundIndex("getStatus");
        if (!databaseBacked) {
            LOGGER.debug("Returning configured vector index status for pending index {}", this.indexName);
            return this.status;
        }

        LOGGER.debug("Fetching current vector index status for index {}", this.getIndexName());
        try {
            refreshVectorIndexMetadata();
            LOGGER.debug("Fetched current vector index status for index {}: {}", this.getIndexName(), this.status);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to fetch current vector index status for index {}", this.getIndexName(), e);
            throw e;
        }
        return status;
    }

    /**
     * Fetches current vector index attributes.
     *
     * @return attributes loaded from current metadata or configuration
     * @throws SelectAIException when attribute metadata cannot be fetched
     */
    @Override
    public VectorIndexAttributes getVectorIndexAttributes() throws SelectAIException {
        if (!databaseBacked) {
            LOGGER.debug("Returning configured vector index attributes for index {}", this.indexName);
            return vectorIndexAttributes;
        }
        requireBoundIndex("getVectorIndexAttributes");
        LOGGER.debug("Fetching current vector index attributes for index {}", this.indexName);
        this.vectorIndexAttributes = fetchVectorIndexAttributes(requireConnectionProvider(), this.indexName);
        return vectorIndexAttributes;
    }

    /**
     * Returns whether wait-for-completion is enabled.
     *
     * @return configured wait-for-completion flag, or {@code null} when not available
     */
    @Override
    public Boolean isWaitForCompletion() {
        return vectorIndexConfig == null ? null : vectorIndexConfig.isWaitForCompletion();
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

    private void requireBoundIndex(String operationName) {
        if (this.indexName == null || this.indexName.isBlank()) {
            LOGGER.error("{} requires an index-bound VectorIndex instance", operationName);
            throw new IllegalStateException(
                    operationName + " requires an index-bound VectorIndex instance. " +
                            "Open an index with SelectAI.vectorIndex(indexName) or configure one before calling this method."
            );
        }
    }

    private void requireCreatedIndex(String operationName) {
        requireBoundIndex(operationName);
        if (!this.databaseBacked) {
            LOGGER.error("{} requires a created vector index; call create() first", operationName);
            throw new IllegalStateException(
                    operationName + " requires a created vector index; call create() first"
            );
        }
    }

    /**
     * Creates this vector index via DBMS_CLOUD_AI.CREATE_VECTOR_INDEX.
     *
     * @return {@code true} when create succeeds
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean create() throws SelectAIException {
        LOGGER.debug("create() called for index={}", this.indexName);
        if (this.vectorIndexConfig == null) {
            LOGGER.error("vectorIndexConfig must not be null when creating vector index {}", this.indexName);
            throw new IllegalStateException("vectorIndexConfig must not be null");
        }
        if (this.indexName == null || this.indexName.isBlank()) {
            LOGGER.error("indexName must not be null or blank when creating vector index");
            throw new IllegalStateException("indexName must not be null or blank");
        }

        boolean created = this.create(this.vectorIndexConfig);
        this.databaseBacked = created;
        return created;
    }

    /**
     * Drops this vector index via DBMS_CLOUD_AI.DROP_VECTOR_INDEX.
     *
     * @param force whether to force the drop operation
     * @return {@code true} when drop call executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        return drop(true, force);
    }

    /**
     * Drops this vector index via DBMS_CLOUD_AI.DROP_VECTOR_INDEX.
     *
     * @param includeData whether to drop backing vector data with index metadata
     * @param force whether to force the drop operation
     * @return {@code true} when drop call executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean drop(boolean includeData, boolean force) throws SelectAIException {
        requireCreatedIndex("drop");
        final String plsql = Sql.DROP_VECTOR_INDEX.get();

        LOGGER.debug("Dropping vector index {} with include_data={} and force={}", this.indexName, includeData, force);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.indexName);
                    stmt.setInt(2, includeData ? 1 : 0);
                    stmt.setInt(3, force ? 1 : 0);
                    stmt.execute();
                    LOGGER.info("Successfully dropped vector index {}", this.indexName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.DROP_VECTOR_INDEX failed for {}", this.indexName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.DROP_VECTOR_INDEX", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Enables this vector index.
     * @return {@code true} when enable call executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean enable() throws SelectAIException {
        requireCreatedIndex("enable");
        final String plsql = Sql.ENABLE_VECTOR_INDEX.get();

        LOGGER.debug("Enabling vector index {}", this.indexName);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.indexName);
                    stmt.execute();
                    this.status = VectorIndexConfig.Status.ENABLED.getValue();
                    LOGGER.info("Successfully enabled vector index {}", this.indexName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.ENABLE_VECTOR_INDEX failed for {}", this.indexName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.ENABLE_VECTOR_INDEX", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Disables this vector index.
     * @return {@code true} when disable call executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean disable() throws SelectAIException {
        requireCreatedIndex("disable");
        final String plsql = Sql.DISABLE_VECTOR_INDEX.get();

        LOGGER.debug("Disabling vector index {}", this.indexName);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.indexName);
                    stmt.execute();
                    this.status = VectorIndexConfig.Status.DISABLED.getValue();
                    LOGGER.info("Successfully disabled vector index {}", this.indexName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.DISABLE_VECTOR_INDEX failed for {}", this.indexName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.DISABLE_VECTOR_INDEX", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Updates vector index attributes using DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX.
     *
     * @param vectorIndexAttributes attributes payload to persist
     * @return {@code true} when attribute update call executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean update(VectorIndexAttributes vectorIndexAttributes) throws SelectAIException {
        requireCreatedIndex("update");
        if (vectorIndexAttributes == null) {
            LOGGER.error("vectorIndexAttributes must not be null when updating vector index {}", this.indexName);
            throw new IllegalArgumentException("vectorIndexAttributes must not be null");
        }
        final String plsql = Sql.UPDATE_VECTOR_INDEX_ATTRIBUTES.get();

        LOGGER.debug("Updating vector index {}", this.indexName);
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.indexName);
                    setClobOrNull(stmt, 2, vectorIndexAttributes.toJson());
                    stmt.execute();
                    this.vectorIndexAttributes = vectorIndexAttributes;
                    LOGGER.info("Successfully updated vector index {}", this.indexName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX failed for {}", this.indexName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Updates vector index attributes using DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX.
     *
     * @param attributeName attribute name to update
     * @param attributeValue attribute value to persist
     * @return {@code true} when delegated update succeeds
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean update(String attributeName, String attributeValue) throws SelectAIException {
        return update(attributeName, attributeValue, false);
    }

    /**
     * Updates vector index attributes using DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX.
     *
     * @param attributeName attribute name to update
     * @param attributeValue attribute value to persist
     * @param useClob whether to send the value as a character stream
     * @return {@code true} when attribute update call executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean update(String attributeName, String attributeValue, boolean useClob) throws SelectAIException {
        requireCreatedIndex("update");
        String normalizedAttributeName = normalizeAttributeName(attributeName);
        if (normalizedAttributeName == null) {
            LOGGER.error("attributeName must not be null or blank for vector index {}", this.indexName);
            throw new IllegalArgumentException("attributeName must not be null or blank");
        }
        if (attributeValue == null) {
            LOGGER.error("attributeValue must not be null for vector index {}", this.indexName);
            throw new IllegalArgumentException("attributeValue must not be null");
        }
        final String plsql = Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get();

        LOGGER.debug("Updating vector index {} attribute {} using {} overload",
                this.indexName, normalizedAttributeName, useClob ? "CLOB" : "VARCHAR2");
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, this.indexName);
                    stmt.setString(2, normalizedAttributeName);
                    if (useClob) {
                        setClobOrNull(stmt, 3, attributeValue);
                    } else {
                        stmt.setString(3, attributeValue);
                    }
                    stmt.execute();
                    LOGGER.info("Successfully updated vector index {} attribute {}", this.indexName, normalizedAttributeName);
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX(attribute) failed for {}.{}",
                    this.indexName, normalizedAttributeName, e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX(attribute)", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Normalizes attribute names to expected canonical form.
     *
     * @param attributeName caller-supplied attribute name
     * @return lowercase trimmed attribute name, or {@code null} for null/blank input
     */
    private static String normalizeAttributeName(String attributeName) {
        if (attributeName == null) {
            return null;
        }
        String trimmed = attributeName.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Creates this vector index via DBMS_CLOUD_AI.CREATE_VECTOR_INDEX.
     *
     * @param vectorIndexConfig validated create-time configuration
     * @return {@code true} when create call executes successfully
     * @throws SelectAIException when create operation fails
     */
    private boolean create(VectorIndexConfig vectorIndexConfig)
            throws SelectAIException {
        if (vectorIndexConfig == null) {
            LOGGER.error("vectorIndexConfig must not be null when creating vector index");
            throw new IllegalArgumentException("vectorIndexConfig must not be null when creating vector index");
        }
        final String plsql = Sql.CREATE_VECTOR_INDEX.get();

        LOGGER.debug("Creating vector index {}", vectorIndexConfig.getIndexName());
        LOGGER.debug("Create payload summary index={}, status={}, hasDescription={}, waitForCompletion={}",
                vectorIndexConfig.getIndexName(),
                vectorIndexConfig.getStatusValue(),
                vectorIndexConfig.getDescription() != null,
                vectorIndexConfig.isWaitForCompletion());
        try {
            requireConnectionProvider().withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.setString(1, vectorIndexConfig.getIndexName());

                    VectorIndexAttributes attributes = vectorIndexConfig.getVectorIndexAttributes();
                    String attributesJson = attributes == null ? null : attributes.toJson();
                    if (attributesJson == null) {
                        stmt.setNull(2, Types.CLOB);
                    } else {
                        setClobOrNull(stmt, 2, attributesJson);
                    }

                    if (vectorIndexConfig.getStatus() == null) {
                        stmt.setNull(3, Types.VARCHAR);
                    } else {
                        stmt.setString(3, vectorIndexConfig.getStatusValue());
                    }

                    if (vectorIndexConfig.getDescription() == null) {
                        stmt.setNull(4, Types.CLOB);
                    } else {
                        String description = vectorIndexConfig.getDescription();
                        setClobOrNull(stmt, 4, description);
                    }

                    stmt.setInt(5, Boolean.TRUE.equals(vectorIndexConfig.isWaitForCompletion()) ? 1 : 0);
                    stmt.execute();
                    LOGGER.info("Successfully created vector index {}", vectorIndexConfig.getIndexName());
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.CREATE_VECTOR_INDEX failed for {}", vectorIndexConfig.getIndexName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.CREATE_VECTOR_INDEX", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Fetches vector index metadata by name from USER_CLOUD_AI_VECTOR_INDEXES.
     *
     * @param connectionProvider provider used for the metadata query
     * @param indexName vector index name to fetch
     * @return matching metadata row, or {@code null} when index is not found
     * @throws SelectAIException when metadata lookup fails
     */
    private static ExistingVectorIndexMetadata get(ConnectionProvider connectionProvider, String indexName)
            throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        // Exact lookup must treat caller-supplied names literally. Do not use
        // the regex-based list query here, because names may contain metacharacters.
        final String sql = Sql.GET_VECTOR_INDEX.get();

        LOGGER.debug("Fetching vector index {}", indexName);
        try {
            return connectionProvider.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, indexName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        return new ExistingVectorIndexMetadata(
                                rs.getString("index_name"),
                                rs.getString("status"),
                                rs.getString("description"));
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch vector index {}", indexName, e);
            throw new SelectAIException("Failed to fetch vector index: " + indexName, e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    static VectorIndexAttributes fetchVectorIndexAttributes(ConnectionProvider connectionProvider,
                                                            String indexName)
            throws SelectAIException {
        validateConnectionProvider(connectionProvider);
        final String sql = Sql.GET_VECTOR_INDEX_ATTRIBUTES.get();
        Map<String, String> attributes = new LinkedHashMap<>();
        try {
            connectionProvider.withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, indexName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            attributes.put(rs.getString("attribute_name"), rs.getString("attribute_value"));
                        }
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch vector index attributes for {}", indexName, e);
            throw new SelectAIException("Failed to fetch vector index attributes: " + indexName, e,
                    e.getErrorCode(), e.getSQLState());
        }
        if (attributes.isEmpty()) {
            return null;
        }
        return toVectorIndexAttributes(attributes);
    }

    private static VectorIndexAttributes toVectorIndexAttributes(Map<String, String> attributes) {
        VectorIndexAttributes.Builder builder = VectorIndexAttributes.updateBuilder();
        boolean[] applied = {false};
        attributes.forEach((attributeName, attributeValue) -> {
            if (attributeName == null || attributeValue == null) {
                return;
            }
            switch (attributeName.toLowerCase(Locale.ROOT)) {
                case "chunk_size" -> { builder.chunkSize(parseInteger(attributeValue)); applied[0] = true; }
                case "chunk_overlap" -> { builder.chunkOverlap(parseInteger(attributeValue)); applied[0] = true; }
                case "enable_sources" -> { builder.enableSources(parseBoolean(attributeValue)); applied[0] = true; }
                case "location" -> { builder.location(attributeValue); applied[0] = true; }
                case "match_limit" -> { builder.matchLimit(parseInteger(attributeValue)); applied[0] = true; }
                case "object_storage_credential_name" -> {
                    builder.objectStorageCredentialName(attributeValue);
                    applied[0] = true;
                }
                case "pipeline_name" -> { builder.pipelineName(attributeValue); applied[0] = true; }
                case "profile_name" -> { builder.profileName(attributeValue); applied[0] = true; }
                case "refresh_rate" -> { builder.refreshRate(parseInteger(attributeValue)); applied[0] = true; }
                case "similarity_threshold" -> {
                    builder.similarityThreshold(parseDouble(attributeValue));
                    applied[0] = true;
                }
                case "vector_distance_metric" -> { builder.vectorDistanceMetric(attributeValue); applied[0] = true; }
                case "vector_db_provider" -> { builder.vectorDbProvider(attributeValue); applied[0] = true; }
                case "vector_dimension" -> { builder.vectorDimension(parseInteger(attributeValue)); applied[0] = true; }
                case "vector_table_name" -> { builder.vectorTableName(attributeValue); applied[0] = true; }
                default -> LOGGER.debug("Ignoring unsupported vector index attribute {}", attributeName);
            }
        });
        return applied[0] ? builder.build() : null;
    }

    private static Integer parseInteger(String value) {
        return value == null ? null : Integer.valueOf(value.trim());
    }

    private static Double parseDouble(String value) {
        return value == null ? null : Double.valueOf(value.trim());
    }

    /**
     * Refreshes local vector index metadata from {@code USER_CLOUD_VECTOR_INDEXES}.
     *
     * @throws SelectAIException when metadata cannot be fetched
     */
    private void refreshVectorIndexMetadata() throws SelectAIException {
        ExistingVectorIndexMetadata metadata = get(requireConnectionProvider(), this.indexName);
        if (metadata == null) {
            throw new SelectAIException("Vector index not found: " + this.indexName);
        }
        this.indexName = metadata.getIndexName();
        this.status = metadata.getStatus();
        this.description = metadata.getDescription();
    }

    private static Boolean parseBoolean(String value) {
        return value == null ? null : Boolean.valueOf(value.trim());
    }

    /**
     * Metadata row for a vector index.
     */
    static class ExistingVectorIndexMetadata {
        /** Vector index name from the metadata view. */
        private final String indexName;
        /** Vector index description from the metadata view. */
        private final String description;
        /** Vector index status from the metadata view. */
        private final String status;

        ExistingVectorIndexMetadata(String indexName, String status, String description) {
            this.indexName = indexName;
            this.status = status;
            this.description = description;
        }

        /**
         * Returns vector index name.
         * @return vector index name from metadata row
         */
        public String getIndexName() {
            return indexName;
        }

        /**
         * Returns vector index description.
         * @return vector index description from metadata row
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns vector index status.
         * @return vector index status from metadata row
         */
        public String getStatus() {
            return status;
        }
    }
}
