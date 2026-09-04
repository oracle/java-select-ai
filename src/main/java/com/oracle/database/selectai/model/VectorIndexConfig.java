/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

/**
 * Create-time configuration for {@code DBMS_CLOUD_AI.CREATE_VECTOR_INDEX}.
 * <p>
 * This model represents the procedure parameters {@code index_name},
 * {@code attributes}, {@code status}, {@code description}, and
 * {@code wait_for_completion}. The {@code attributes} payload is represented
 * by {@link VectorIndexAttributes} and may be left unset so database-side
 * defaults or validation can apply.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html#GUID-CB37AB86-B625-4798-A3F4-8DD6DBA8B491">
 *      DBMS_CLOUD_AI.CREATE_VECTOR_INDEX procedure</a>
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-retrieval-augmented-generation.html">
 *      Select AI RAG and vector indexes</a>
 */
public final class VectorIndexConfig {

    /** Default create-time vector index status. */
    private static final Status DEFAULT_STATUS = Status.DISABLED;

    /** Vector index name to create or manage. */
    private final String indexName;
    /** Initial vector index status. */
    private final Status status;
    /** Vector index description. */
    private final String description;
    /** Whether create should wait for asynchronous population to complete. */
    private final Boolean waitForCompletion;
    /** Attribute payload used to create the vector index. */
    private final VectorIndexAttributes vectorIndexAttributes;


    private VectorIndexConfig(Builder builder) {
        this.indexName = builder.indexName;
        this.vectorIndexAttributes = builder.vectorIndexAttributes;
        this.status = builder.status;
        this.description = builder.description;
        this.waitForCompletion = builder.waitForCompletion;
    }

    /**
     * Returns the vector index name.
     *
     * @return configured vector index name
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * Returns the vector index attribute payload.
     *
     * @return vector index attributes payload, or {@code null} when unset
     */
    public VectorIndexAttributes getVectorIndexAttributes() {
        return vectorIndexAttributes;
    }

    /**
     * Returns the initial status enum.
     *
     * @return configured status enum
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the DBMS_CLOUD_AI status value.
     *
     * @return status display value, or {@code null} when status is null
     */
    public String getStatusValue() {
        return status == null ? null : status.getValue();
    }

    /**
     * Returns the vector index description.
     *
     * @return description, or {@code null} when unset
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns whether create should wait for completion.
     *
     * @return wait-for-completion flag
     */
    public Boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    /**
     * Creates a builder for the required vector index name.
     * <p>
     * The SDK trims the supplied name and validates only that it is present.
     * Database identifier length and naming rules are validated by
     * {@code DBMS_CLOUD_AI.CREATE_VECTOR_INDEX}.
     *
     * @param indexName vector index name
     * @return builder initialized with normalized index name
     */
    public static Builder builder(String indexName) {
        return new Builder(indexName);
    }

    /**
     * Builder for {@link VectorIndexConfig}.
     */
    public static final class Builder {
        /** Required vector index name. */
        private final String indexName;
        /** Vector index attributes being assembled. */
        private VectorIndexAttributes vectorIndexAttributes;
        /** Initial status being assembled. */
        private Status status = DEFAULT_STATUS;
        /** Description being assembled. */
        private String description;
        /** Wait-for-completion flag being assembled. */
        private Boolean waitForCompletion = Boolean.FALSE;

        private Builder(String indexName) {
            String normalizedIndexName = normalize(indexName);
            if (normalizedIndexName == null) {
                throw new IllegalArgumentException("indexName must not be null or blank");
            }
            this.indexName = normalizedIndexName;
        }

        /**
         * Sets the vector index attributes payload.
         *
         * @param vectorIndexAttributes vector index attributes, or {@code null}
         * @return this builder instance
         */
        public Builder vectorIndexAttributes(VectorIndexAttributes vectorIndexAttributes) {
            this.vectorIndexAttributes = vectorIndexAttributes;
            return this;
        }

        /**
         * Sets the initial status from text.
         *
         * @param status status text, enabled or disabled
         * @return this builder instance
         */
        public Builder status(String status) {
            this.status = Status.fromValue(status);
            return this;
        }

        /**
         * Sets the initial status.
         *
         * @param status vector index status
         * @return this builder instance
         */
        public Builder status(Status status) {
            this.status = status == null ? DEFAULT_STATUS : status;
            return this;
        }

        /**
         * Sets the vector index description.
         *
         * @param description vector index description
         * @return this builder instance
         */
        public Builder description(String description) {
            this.description = normalize(description);
            return this;
        }

        /**
         * Sets whether create should wait for index population completion.
         *
         * @param waitForCompletion wait-for-completion flag
         * @return this builder instance
         */
        public Builder waitForCompletion(Boolean waitForCompletion) {
            this.waitForCompletion = waitForCompletion == null ? Boolean.FALSE : waitForCompletion;
            return this;
        }

        /**
         * Builds immutable vector index configuration.
         *
         * @return immutable VectorIndexConfig built from current builder state
         */
        public VectorIndexConfig build() {
            return new VectorIndexConfig(this);
        }
    }

    /**
     * Normalizes blank input strings to {@code null}.
     *
     * @param value value to trim
     * @return trimmed value, or {@code null} when input is null/blank
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Vector index status values accepted by {@code CREATE_VECTOR_INDEX}.
     */
    public enum Status {
        /** Vector index is enabled after creation. */
        ENABLED("Enabled"),
        /** Vector index is disabled after creation. */
        DISABLED("Disabled");

        /** DBMS_CLOUD_AI display value for the status. */
        private final String value;

        Status(String value) {
            this.value = value;
        }

        /**
         * Returns the DBMS_CLOUD_AI status value.
         *
         * @return display value for this status enum constant
         */
        public String getValue() {
            return value;
        }

        /**
         * Parses a status value, defaulting to {@link #DISABLED}.
         *
         * @param status status text
         * @return matching status enum, or default status when input is null/blank
         */
        public static Status fromValue(String status) {
            String normalized = normalize(status);
            if (normalized == null) {
                return DEFAULT_STATUS;
            }
            if ("enabled".equalsIgnoreCase(normalized)) {
                return ENABLED;
            }
            if ("disabled".equalsIgnoreCase(normalized)) {
                return DISABLED;
            }
            throw new IllegalArgumentException("status must be either Enabled or Disabled");
        }
    }
}
