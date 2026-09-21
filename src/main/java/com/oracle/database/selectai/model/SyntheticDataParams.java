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

import java.util.Locale;

/**
 * Parameters for {@code DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA}.
 * <p>
 * These parameters control how Select AI samples source table data, uses table
 * statistics, and schedules parallel requests when generating synthetic data.
 *
 * <p>
 * The SDK performs basic, deterministic validation for values that can be
 * validated independently of Oracle Database. In particular,
 * {@code sampleRows} must be between 0 and 100, inclusive, when specified,
 * and {@code priority} must be one of {@code HIGH}, {@code MEDIUM}, or
 * {@code LOW}. Invalid values detected by the SDK result in an
 * {@link IllegalArgumentException}.
 *
 * <p>
 * Database-specific, database-version-specific, and other semantic validation
 * is delegated to {@code DBMS_CLOUD_AI} and Oracle Database. Therefore, a
 * parameter value that passes SDK validation may still be rejected by the
 * database based on the requested synthetic-data operation or other
 * database-side requirements.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html#optional-parameters">
 *      DBMS_CLOUD_AI synthetic data parameters</a>
 */
public final class SyntheticDataParams {

    /** Number of table rows sampled to guide synthetic data generation. */
    private final Integer sampleRows;

    /** Whether table statistics are used during synthetic data generation. */
    private final Boolean tableStatistics;

    /**
     * Priority used to determine the parallelism of synthetic-data generation
     * requests. Supported values are {@code HIGH}, {@code MEDIUM}, and {@code LOW}.
     */
    private final String priority;

    /** Whether table comments are used as context during synthetic data generation. */
    private final Boolean comments;

    private SyntheticDataParams(Builder builder) {
        this.sampleRows = builder.sampleRows;
        this.tableStatistics = builder.tableStatistics;
        this.priority = builder.priority;
        this.comments = builder.comments;
    }

    /**
     * Returns the number of table rows sampled to guide synthetic data generation.
     *
     * @return sample-rows value, or {@code null} when unset
     */
    public Integer getSampleRows() {
        return sampleRows;
    }

    /**
     * Returns whether table statistics are used during synthetic data generation.
     *
     * @return table-statistics flag, or {@code null} when unset
     */
    public Boolean getTableStatistics() {
        return tableStatistics;
    }

    /**
     * Returns the priority used for synthetic data generation.
     *
     * @return priority value ({@code HIGH}, {@code MEDIUM}, or {@code LOW}),
     *         or {@code null} when unset
     */
    public String getPriority() {
        return priority;
    }

    /**
     * Returns whether table comments are used as generation context.
     *
     * @return comments flag, or {@code null} when unset
     */
    public Boolean getComments() {
        return comments;
    }

    /**
     * Serializes the parameter payload using the snake_case names expected by
     * {@code DBMS_CLOUD_AI}.
     *
     * @return JSON representation of this {@code SyntheticDataParams}
     * @throws IllegalStateException if serialization fails
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize SyntheticDataParams", e);
        }
    }

    /**
     * Creates a builder for synthetic data generation parameters.
     *
     * @return new builder for constructing {@link SyntheticDataParams}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Priority values accepted by synthetic data generation parameters.
     */
    public enum Priority {
        /** High priority synthetic-data generation. */
        HIGH,
        /** Medium priority synthetic-data generation. */
        MEDIUM,
        /** Low priority synthetic-data generation. */
        LOW
    }

    /**
     * Builder for {@link SyntheticDataParams}.
     */
    public static final class Builder {
        /** Creates an empty builder. */
        public Builder() {
        }

        /** Sample rows value being assembled. */
        private Integer sampleRows;
        /** Table statistics flag being assembled. */
        private Boolean tableStatistics;
        /** Priority value being assembled. */
        private String priority;
        /** Comments flag being assembled. */
        private Boolean comments;

        /**
         * Sets the number of table rows sampled to guide synthetic data generation.
         *
         * <p>The value must be between 0 and 100, inclusive, when specified.
         * A value of 0 indicates that no sample rows are used.</p>
         *
         * @param sampleRows sample-row value, from 0 through 100, or {@code null}
         *                   when unset
         * @return this builder instance
         * @throws IllegalArgumentException if {@code sampleRows} is less than 0
         *         or greater than 100
         */
        public Builder sampleRows(Integer sampleRows) {
            if (sampleRows != null && (sampleRows < 0 || sampleRows > 100)) {
                throw new IllegalArgumentException("sampleRows must be between 0 and 100");
            }
            this.sampleRows = sampleRows;
            return this;
        }

        /**
         * Sets whether table statistics should be used during synthetic data
         * generation.
         *
         * @param tableStatistics whether table statistics should be used, or
         *                        {@code null} when unset
         * @return this builder instance
         */
        public Builder tableStatistics(Boolean tableStatistics) {
            this.tableStatistics = tableStatistics;
            return this;
        }

        /**
         * Sets the priority used for synthetic data generation.
         *
         * <p>The supported values are {@code HIGH}, {@code MEDIUM}, and
         * {@code LOW}. The comparison is case-insensitive; the value stored by the
         * builder is normalized to upper case.</p>
         *
         * @param priority priority value ({@code HIGH}, {@code MEDIUM}, or
         *                 {@code LOW}), or {@code null} when unset
         * @return this builder instance
         * @throws IllegalArgumentException if {@code priority} is not one of the
         *         supported values
         */
        public Builder priority(String priority) {
            if (priority != null) {
                String p = priority.trim().toUpperCase(Locale.ROOT);
                if (!p.equals("HIGH") && !p.equals("MEDIUM") && !p.equals("LOW")) {
                    throw new IllegalArgumentException("priority must be HIGH, MEDIUM, or LOW");
                }
                this.priority = p;
            } else {
                this.priority = null;
            }
            return this;
        }

        /**
         * Sets the priority used for synthetic data generation.
         *
         * @param priority priority value, or {@code null} when unset
         * @return this builder instance
         */
        public Builder priority(Priority priority) {
            this.priority = priority == null ? null : priority.name();
            return this;
        }

        /**
         * Sets whether table comments should be used as context during synthetic
         * data generation.
         *
         * @param comments whether comments should be used, or {@code null} when unset
         * @return this builder instance
         */
        public Builder comments(Boolean comments) {
            this.comments = comments;
            return this;
        }

        /**
         * Builds the synthetic data parameter payload.
         *
         * <p>SDK-level validation is applied when individual values are set.
         * Database-specific and semantic validation remains delegated to
         * {@code DBMS_CLOUD_AI} and Oracle Database.</p>
         *
         * @return immutable {@link SyntheticDataParams} instance built from the
         *         current builder state
         */
        public SyntheticDataParams build() {
            return new SyntheticDataParams(this);
        }
    }
}
