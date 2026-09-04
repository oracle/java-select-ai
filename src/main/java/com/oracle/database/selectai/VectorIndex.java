/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.VectorIndexAttributes;

/**
 * Contract for Select AI vector-index lifecycle and configuration operations.
 * <p>
 * A vector index stores embeddings for source content so Select AI can retrieve
 * relevant chunks and include them in model prompts for retrieval augmented
 * generation (RAG). A {@code VectorIndex} instance can represent a vector index created by Java,
 * PL/SQL, Python, SQL tools, or another application when the index is visible
 * in the current schema.
 * <p>
 * A {@code VectorIndex} object can be configured for creation or
 * database-backed. A configured vector index has an index name, attributes,
 * description, status, and create options in memory, but it is not persisted
 * until {@link #create()} succeeds. A database-backed vector index is opened
 * from the database, listed from metadata, or successfully created through this
 * SDK.
 * <p>
 * Metadata getters can return configured values before creation. Operations
 * that execute against an existing database vector index, such as update,
 * enable, disable, and drop, require a database-backed vector index. The SDK
 * rejects those operations with {@link IllegalStateException} when the object
 * is not bound to an index name or is configured for creation but has not been
 * created yet.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-retrieval-augmented-generation.html">
 *      Select AI with Retrieval Augmented Generation</a>
 */
public interface VectorIndex {
    /**
     * Returns vector index name.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/GetVectorIndexNameSample.html">
     * GetVectorIndexNameSample source</a>.
     *
     * @return vector index name
     */
    String getIndexName();

    /**
     * Returns vector index description.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/GetVectorIndexDescriptionSample.html">
     * GetVectorIndexDescriptionSample source</a>.
     *
     * @return vector index description
     */
    String getDescription();

    /**
     * Returns vector index status.
     * <p>
     * For a configured index that has not been created yet, returns the
     * caller-supplied create status. For an index opened from or listed from the
     * database, refreshes metadata from the database before returning the
     * status.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/GetVectorIndexStatusSample.html">
     * GetVectorIndexStatusSample source</a>.
     *
     * @return vector index status
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     * @throws SelectAIException when current vector index metadata cannot be fetched
     */
    String getStatus() throws SelectAIException;

    /**
     * Fetches and returns current vector index attributes.
     * <p>
     * For indexes opened from or listed from the database, this fetches current
     * attributes from database metadata. For newly configured indexes that have
     * not been created yet, this returns the caller-supplied create
     * payload.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/GetVectorIndexAttributesSample.html">
     * GetVectorIndexAttributesSample source</a>.
     *
     * @return vector index attributes, or {@code null} when attributes are not available
     * @throws SelectAIException when current vector index attributes cannot be fetched
     */
    VectorIndexAttributes getVectorIndexAttributes() throws SelectAIException;

    /**
     * Indicates whether create operations wait for completion.
     * <p>
     * This is a create-time execution option, not current database metadata.
     * Loaded/listed indexes return {@code null}; configured indexes return the
     * caller-supplied create option.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/IsVectorIndexWaitForCompletionSample.html">
     * IsVectorIndexWaitForCompletionSample source</a>.
     *
     * @return wait-for-completion flag
     */
    Boolean isWaitForCompletion();

    /**
     * Creates the vector index in the database.
     * <p>
     * Creation reads source content using the configured credential, generates
     * embeddings using the associated profile/provider, and stores the resulting
     * vectors for later RAG prompts.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/CreateVectorIndexSample.html">
     * CreateVectorIndexSample source</a>.
     *
     * @return {@code true} when create succeeds
     * @throws IllegalStateException when this VectorIndex instance is not configured for creation
     * @throws SelectAIException when create fails
     */
    boolean create() throws SelectAIException;

    /**
     * Drops the vector index.
     * <p>
     * This shorthand drops both vector-index metadata and backing vector data.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/DropVectorIndexSample.html">
     * DropVectorIndexSample source</a>.
     *
     * @param force when {@code true}, performs force drop if supported
     * @return {@code true} when drop succeeds
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when drop fails
     */
    boolean drop(boolean force) throws SelectAIException;

    /**
     * Drops the vector index and controls whether backing vector data is also
     * removed.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/DropVectorIndexSample.html">
     * DropVectorIndexSample source</a>.
     *
     * @param includeData when {@code true}, drops backing vector data with the index metadata;
     *        when {@code false}, drops only vector-index metadata
     * @param force when {@code true}, performs force drop if supported
     * @return {@code true} when drop succeeds
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when drop fails
     */
    boolean drop(boolean includeData, boolean force) throws SelectAIException;

    /**
     * Enables the vector index so Select AI can use it during RAG operations.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/EnableVectorIndexSample.html">
     * EnableVectorIndexSample source</a>.
     *
     * @return {@code true} when enable succeeds
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when enable fails
     */
    boolean enable() throws SelectAIException;

    /**
     * Disables the vector index so Select AI will not use it for retrieval until
     * it is enabled again.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/DisableVectorIndexSample.html">
     * DisableVectorIndexSample source</a>.
     *
     * @return {@code true} when disable succeeds
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when disable fails
     */
    boolean disable() throws SelectAIException;

    /**
     * Updates vector index attributes in bulk.
     * <p>
     * Use this with {@link VectorIndexAttributes#updateBuilder()} when building
     * an update-only payload. The SDK validates required Java inputs, then
     * delegates attribute mutability rules to {@code DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX}
     * so database-version changes are honored without SDK code changes.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/UpdateVectorIndexAttributesSample.html">
     * UpdateVectorIndexAttributesSample source</a>.
     *
     * @param vectorIndexAttributes attributes payload
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean update(VectorIndexAttributes vectorIndexAttributes) throws SelectAIException;

    /**
     * Updates a single vector-index attribute using normal string binding.
     * <p>
     * The SDK validates required Java inputs, then delegates attribute mutability
     * rules to {@code DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX}.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/UpdateVectorIndexAttributeSample.html">
     * UpdateVectorIndexAttributeSample source</a>.
     *
     * @param attributeName attribute name
     * @param attributeValue attribute value
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean update(String attributeName, String attributeValue) throws SelectAIException;

    /**
     * Updates a single vector-index attribute and optionally sends large values
     * as character data.
     * <p>
     * The SDK validates required Java inputs, then delegates attribute mutability
     * rules to {@code DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX}.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/UpdateVectorIndexAttributeWithClobSample.html">
     * UpdateVectorIndexAttributeWithClobSample source</a>.
     *
     * @param attributeName attribute name
     * @param attributeValue attribute value
     * @param useClob when {@code true}, sends value as CLOB
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this VectorIndex instance is not bound to an index name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean update(String attributeName, String attributeValue, boolean useClob) throws SelectAIException;

}
