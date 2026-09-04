/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

/**
 * Request object for generating synthetic data for a single database object.
 * <p>
 * This request groups the target database object, owner/schema, requested
 * record count, user guidance, and synthetic-data generation parameters into
 * one immutable request.
 *
 * <p>
 * The SDK performs basic, deterministic validation where the constraint can
 * be evaluated independently of Oracle Database. In particular, the
 * {@code objectName} must not be null or blank, and {@code recordCount}, when
 * specified, must be greater than zero. Invalid values detected by the SDK
 * result in an {@link IllegalArgumentException}.
 *
 * <p>
 * Database-specific, database-version-specific, and other semantic validation
 * is delegated to {@code DBMS_CLOUD_AI} and Oracle Database. Therefore, a
 * request that passes SDK validation may still be rejected by the database
 * based on the requested synthetic-data operation or other database-side
 * requirements.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html#parameters-18">
 *      DBMS_CLOUD_AI synthetic data parameters</a>
 */
public final class SyntheticDataSingleRequest {

    /** Target database object name. */
    private final String objectName;

    /** Database user/schema associated with the target object. */
    private final String ownerName;

    /** Number of records requested for generation. */
    private final Integer recordCount;

    /** User guidance for synthetic data generation. */
    private final String userPrompt;

    /** Parameters controlling synthetic data generation. */
    private final SyntheticDataParams params;

    private SyntheticDataSingleRequest(Builder builder) {
        this.objectName = builder.objectName;
        this.ownerName = builder.ownerName;
        this.recordCount = builder.recordCount;
        this.userPrompt = builder.userPrompt;
        this.params = builder.params;
    }

    /**
     * Returns the target database object name.
     *
     * @return target object name
     */
    public String getObjectName() {
        return objectName;
    }

    /**
     * Returns the database user/schema associated with the target object.
     *
     * @return owner/schema name, or {@code null} when not set
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Returns the requested number of records to generate.
     *
     * @return record count, or {@code null} when not set
     */
    public Integer getRecordCount() {
        return recordCount;
    }

    /**
     * Returns user guidance for synthetic data generation.
     *
     * @return user prompt, or {@code null} when not set
     */
    public String getUserPrompt() {
        return userPrompt;
    }

    /**
     * Returns the synthetic data generation parameters.
     *
     * @return generation parameters, or {@code null} when not set
     */
    public SyntheticDataParams getParams() {
        return params;
    }

    /**
     * Returns the synthetic data generation parameters serialized as JSON.
     *
     * @return JSON representation of the parameters, or {@code null} when
     *         parameters are not set
     */
    public String getParamsJson() {
        return params == null ? null : params.toJson();
    }

    /**
     * Creates a builder for a single-object synthetic data request.
     *
     * <p>The SDK validates the target object name when the builder is created.
     * Database-specific validation of the target object and generation semantics
     * is delegated to {@code DBMS_CLOUD_AI} and Oracle Database.</p>
     *
     * @param objectName target database object name
     * @return builder initialized with the target object name
     * @throws IllegalArgumentException if {@code objectName} is null or blank
     */
    public static Builder builder(String objectName) {
        return new Builder(objectName);
    }

    /**
     * Builder for {@link SyntheticDataSingleRequest}.
     */
    public static final class Builder {
        /** Required target object name. */
        private final String objectName;
        /** Target owner/schema being assembled. */
        private String ownerName;
        /** Exact record count being assembled. */
        private Integer recordCount;
        /** Generation prompt being assembled. */
        private String userPrompt;
        /** Structured generation parameters being assembled. */
        private SyntheticDataParams params;

        private Builder(String objectName) {
            if (objectName == null || objectName.isBlank()) {
                throw new IllegalArgumentException("objectName must not be null or blank");
            }
            this.objectName = objectName;
        }

        /**
         * Sets the database user/schema associated with the target object.
         * <p>
         * Null is sent to the database as SQL NULL. Non-null values, including
         * blank strings, are preserved as supplied by the caller.
         *
         * @param ownerName owner/schema name
         * @return this builder instance
         */
        public Builder ownerName(String ownerName) {
            this.ownerName = ownerName;
            return this;
        }

        /**
         * Sets the number of records to generate for the target object.
         *
         * <p>The value must be greater than zero when specified. Other
         * synthetic-data-specific semantics are validated by Oracle Database.</p>
         *
         * @param recordCount record count, or {@code null} when not set
         * @return this builder instance
         * @throws IllegalArgumentException if {@code recordCount} is less than or
         *         equal to zero
         */
        public Builder recordCount(Integer recordCount) {
            if (recordCount != null && recordCount <= 0) {
                throw new IllegalArgumentException("recordCount must be greater than 0");
            }
            this.recordCount = recordCount;
            return this;
        }

        /**
         * Sets user guidance for synthetic data generation.
         * <p>
         * Null is sent to the database as SQL NULL. Non-null values, including
         * blank strings, are preserved as supplied by the caller.
         *
         * @param userPrompt generation guidance
         * @return this builder instance
         */
        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        /**
         * Sets the synthetic data generation parameters.
         *
         * <p>The supplied {@link SyntheticDataParams} instance is expected to have
         * already applied its SDK-level validation. Database-specific validation is
         * performed by Oracle Database when the request is executed.</p>
         *
         * @param params synthetic data generation parameters
         * @return this builder instance
         */
        public Builder params(SyntheticDataParams params) {
            this.params = params;
            return this;
        }

        /**
         * Builds the single-object synthetic data request.
         *
         * <p>SDK-level validation is applied when the relevant values are set.
         * Database-specific and operation-specific validation remains delegated to
         * {@code DBMS_CLOUD_AI} and Oracle Database.</p>
         *
         * @return immutable {@link SyntheticDataSingleRequest} instance
         */
        public SyntheticDataSingleRequest build() {
            return new SyntheticDataSingleRequest(this);
        }
    }
}
