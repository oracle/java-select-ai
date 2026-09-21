/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Object descriptor used in synthetic data generation object lists.
 * <p>
 * Each descriptor carries owner/schema and object-name values for one
 * synthetic-data object-list entry. It can also specify an exact record count,
 * a percentage-based record count, and user guidance for synthetic data
 * generation.
 *
 * <p>
 * The SDK performs basic, deterministic validation where the constraint can
 * be evaluated independently of Oracle Database. The {@code owner} value must
 * not be null or blank. The {@code name} value is preserved as supplied so
 * database-side object-list validation remains authoritative. {@code recordCount} and
 * {@code recordCountPercentage}, when specified, must be greater than zero.
 * Invalid values detected by the SDK result in an
 * {@link IllegalArgumentException}.
 *
 * <p>
 * Database-specific and database-version-specific semantic validation,
 * including validation of attribute combinations, is delegated to
 * {@code DBMS_CLOUD_AI} and Oracle Database. Therefore, an object descriptor
 * that passes SDK validation may still be rejected by the database based on
 * the requested operation or other database-side requirements.
 *
 * <p>
 * See the {@code DBMS_CLOUD_AI} documentation for the complete and current
 * definition of synthetic-data {@code object_list} parameters and their
 * supported behavior.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html#object_list-parameters">
 *      DBMS_CLOUD_AI object_list Parameters</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class SyntheticDataObjectList {

    /** Database user/schema that owns the target database object. */
    private final String owner;

    /** Target database object name. */
    private final String name;

    /** Exact number of records to generate. */
    private final Integer recordCount;

    /** Percentage-based record count for generation. */
    private final Integer recordCountPercentage;

    /** Object-specific guidance for synthetic data generation. */
    private final String userPrompt;

    private SyntheticDataObjectList(Builder builder) {
        this.owner = builder.owner;
        this.name = builder.name;
        this.recordCount = builder.recordCount;
        this.recordCountPercentage = builder.recordCountPercentage;
        this.userPrompt = builder.userPrompt;
    }

    /**
     * Returns the target object owner/schema.
     *
     * @return target object owner/schema
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Returns the target object name.
     *
     * @return target object name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the exact requested record count.
     *
     * @return exact requested record count, or {@code null} when unset
     */
    public Integer getRecordCount() {
        return recordCount;
    }

    /**
     * Returns the percentage-based requested record count.
     *
     * @return percentage requested record count, or {@code null} when unset
     */
    public Integer getRecordCountPercentage() {
        return recordCountPercentage;
    }

    /**
     * Returns object-specific generation guidance.
     *
     * @return user prompt, or {@code null} when unset
     */
    public String getUserPrompt() {
        return userPrompt;
    }


    /**
     * Creates a builder for a synthetic-data object descriptor.
     *
     * <p>The SDK validates that {@code owner} is not null or blank. The
     * {@code name} value is preserved as supplied. Other database-specific
     * semantics are validated by
     * {@code DBMS_CLOUD_AI} and Oracle Database.</p>
     *
     * @param owner database user/schema that owns the target object
     * @param name target object name, or {@code null} when the database should
     *        apply object-list semantics for the supplied owner
     * @return builder initialized with the owner and object name
     * @throws IllegalArgumentException if {@code owner} is null or blank
     */
    public static Builder builder(String owner, String name) {
        return new Builder(owner, name);
    }

    /**
     * Builder for {@link SyntheticDataObjectList}.
     */
    public static final class Builder {
        /** Required target object owner/schema. */
        private final String owner;
        /** Target object name being assembled. */
        private final String name;
        /** Exact record count being assembled. */
        private Integer recordCount;
        /** Percentage record count being assembled. */
        private Integer recordCountPercentage;
        /** User prompt being assembled. */
        private String userPrompt;

        private Builder(String owner, String name) {
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("owner must not be null or blank");
            }
            this.owner = owner;
            this.name = name;
        }

        /**
         * Sets the exact number of records to generate.
         *
         * @param recordCount positive record count
         * @return this builder instance
         */
        public Builder recordCount(Integer recordCount) {
            if (recordCount != null && recordCount <= 0) {
                throw new IllegalArgumentException("recordCount must be greater than 0");
            }
            this.recordCount = recordCount;
            return this;
        }

        /**
         * Sets the percentage-based record count.
         *
         * @param recordCountPercentage positive percentage value
         * @return this builder instance
         */
        public Builder recordCountPercentage(Integer recordCountPercentage) {
            if (recordCountPercentage != null && recordCountPercentage <= 0) {
                throw new IllegalArgumentException("recordCountPercentage must be greater than 0");
            }
            this.recordCountPercentage = recordCountPercentage;
            return this;
        }

        /**
         * Sets object-specific guidance for synthetic data generation.
         * <p>
         * Null is omitted from the generated JSON. Non-null values, including
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
         * Builds the object descriptor.
         * <p>
         * Count-related values are passed through as supplied after basic
         * positive-number validation. Database-specific combinations are
         * delegated to {@code DBMS_CLOUD_AI}.
         *
         * @return immutable SyntheticDataObjectList
         */
        public SyntheticDataObjectList build() {
            return new SyntheticDataObjectList(this);
        }

        /**
         * Serializes the current builder state as a JSON object.
         *
         * @return JSON representation of current builder state
         */
        public String toJson() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
                return mapper.writeValueAsString(build());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize SyntheticDataObjectList", e);
            }
        }
    }
}
