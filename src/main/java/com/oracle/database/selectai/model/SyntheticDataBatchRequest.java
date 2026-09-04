/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Request object for generating synthetic data for multiple database objects.
 * <p>
 * A batch request groups multiple {@link SyntheticDataObjectList} descriptors
 * together with shared {@link SyntheticDataParams} used for the synthetic data
 * generation operation.
 * <p>
 * This model represents the batch form of
 * {@code DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA}, where target objects are sent
 * through the {@code object_list} CLOB parameter and shared generation options
 * are sent through the {@code params} CLOB parameter.
 *
 * <p>
 * The SDK performs basic, deterministic validation where the constraint can
 * be evaluated independently of Oracle Database. Each object descriptor must
 * be non-null, and the batch must contain at least one object descriptor.
 * Invalid values detected by the SDK result in an
 * {@link IllegalArgumentException}.
 *
 * <p>
 * Database-specific, database-version-specific, and other semantic validation
 * is delegated to {@code DBMS_CLOUD_AI} and Oracle Database. Therefore, a batch
 * request that passes SDK validation may still be rejected by the database
 * based on the selected profile, object descriptors, generation parameters,
 * or other database-side requirements.
 *
 * @see SyntheticDataObjectList
 * @see SyntheticDataParams
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA reference</a>
 */
public final class SyntheticDataBatchRequest {

    /** Ordered collection of target database object descriptors. */
    private final List<SyntheticDataObjectList> objectList;

    /** Synthetic data generation parameters shared by the batch. */
    private final SyntheticDataParams params;

    private SyntheticDataBatchRequest(Builder builder) {
        this.objectList = List.copyOf(builder.objectList);
        this.params = builder.params;
    }

    /**
     * Returns the object descriptors serialized as a JSON array for
     * {@code DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA}.
     *
     * @return JSON array representing the batch object list
     * @throws IllegalStateException if the object list cannot be serialized
     */
    public String getObjectListJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(objectList);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize SyntheticDataBatchRequest.objectList", e);
        }
    }

    /**
     * Returns the shared synthetic data generation parameters serialized as JSON.
     *
     * @return JSON representation of {@link SyntheticDataParams}, or {@code null}
     *         when no parameters are configured
     */
    public String getParamsJson() {
        return params == null ? null : params.toJson();
    }

    /**
     * Returns the synthetic data generation parameters shared by the batch.
     *
     * @return generation parameters, or {@code null} when not set
     */
    public SyntheticDataParams getParams() {
        return params;
    }

    /**
     * Returns the target database object descriptors.
     *
     * @return immutable list of target object descriptors
     */
    public List<SyntheticDataObjectList> getObjectList() {
        return objectList;
    }

    /**
     * Creates a builder for a batch synthetic-data request.
     *
     * @return new builder for constructing a batch request
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SyntheticDataBatchRequest}.
     */
    public static final class Builder {
        /** Target object descriptors being assembled for the batch. */
        private final List<SyntheticDataObjectList> objectList =
                new ArrayList<>();

        /** Shared synthetic data generation parameters being assembled. */
        private SyntheticDataParams params;

        private Builder() {
        }

        /**
         * Adds a target object descriptor to the batch.
         *
         * @param object target object descriptor
         * @return this builder instance
         * @throws IllegalArgumentException if {@code object} is null
         */
        public Builder addObject(SyntheticDataObjectList object) {
            if (object == null) {
                throw new IllegalArgumentException("object must not be null");
            }
            this.objectList.add(object);
            return this;
        }

        /**
         * Sets the synthetic data generation parameters shared by the batch.
         *
         * @param params synthetic data generation parameters
         * @return this builder instance
         */
        public Builder params(SyntheticDataParams params) {
            this.params = params;
            return this;
        }

        /**
         * Builds the batch synthetic-data request.
         *
         * <p>The SDK requires at least one target object descriptor in the batch.
         * Database-specific validation of the object descriptors and generation
         * parameters is delegated to {@code DBMS_CLOUD_AI} and Oracle Database.</p>
         *
         * @return immutable {@link SyntheticDataBatchRequest}
         * @throws IllegalArgumentException if no target object descriptors were added
         */
        public SyntheticDataBatchRequest build() {
            if (objectList.isEmpty()) {
                throw new IllegalArgumentException("At least one object must be provided in objectList");
            }
            return new SyntheticDataBatchRequest(this);
        }
    }
}
