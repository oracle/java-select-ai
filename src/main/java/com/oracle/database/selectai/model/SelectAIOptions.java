/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

/**
 * SDK execution settings for {@link com.oracle.database.selectai.SelectAI} and
 * {@link com.oracle.database.selectai.DatabaseAdmin} clients.
 * <p>
 * These options control SDK-managed JDBC execution behavior. They do not
 * configure database identity, JDBC URL, passwords, wallet settings, or
 * connection-pool settings.
 */
public final class SelectAIOptions {
    /** Default options with no SDK-level query timeout. */
    private static final SelectAIOptions DEFAULTS = new Builder().build();

    /** Query timeout, in seconds, applied to JDBC statements created by SDK operations. */
    private final Integer queryTimeoutSeconds;

    private SelectAIOptions(Builder builder) {
        this.queryTimeoutSeconds = builder.queryTimeoutSeconds;
    }

    /**
     * Returns default SelectAI options.
     *
     * @return default options with no SDK-level query timeout
     */
    public static SelectAIOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Creates a builder for SelectAI execution options.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the configured JDBC statement query timeout.
     *
     * @return timeout in seconds, {@code 0} for JDBC no-timeout behavior, or
     *         {@code null} when the SDK does not call
     *         {@link java.sql.Statement#setQueryTimeout(int)}
     */
    public Integer getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    /**
     * Builder for {@link SelectAIOptions}.
     */
    public static final class Builder {
        /** Query timeout being assembled, in seconds. */
        private Integer queryTimeoutSeconds;

        private Builder() {
        }

        /**
         * Sets the JDBC statement query timeout applied to SDK operations.
         * <p>
         * A {@code null} value means the SDK does not call
         * {@link java.sql.Statement#setQueryTimeout(int)}. A value of {@code 0}
         * uses JDBC's no-timeout behavior. Negative values are rejected.
         *
         * @param queryTimeoutSeconds timeout in seconds, or {@code null} for no SDK timeout
         * @return this builder instance
         */
        public Builder queryTimeoutSeconds(Integer queryTimeoutSeconds) {
            if (queryTimeoutSeconds != null && queryTimeoutSeconds < 0) {
                throw new IllegalArgumentException("queryTimeoutSeconds must be greater than or equal to 0");
            }
            this.queryTimeoutSeconds = queryTimeoutSeconds;
            return this;
        }

        /**
         * Builds immutable SelectAI options.
         *
         * @return SelectAI options
         */
        public SelectAIOptions build() {
            return new SelectAIOptions(this);
        }
    }
}
