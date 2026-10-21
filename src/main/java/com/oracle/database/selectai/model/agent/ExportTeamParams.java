/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Typed parameters for exporting an agent team definition to a destination.
 *
 * <p>{@code overwrite} is the export parameter currently supported by
 * {@code DBMS_CLOUD_AI_AGENT.EXPORT_TEAM}. A null value omits the optional
 * database parameter object.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ExportTeamSample.html">
 *      ExportTeamSample source</a>
 * @since 1.1.0
 */
public final class ExportTeamParams {
    /** JSON mapper used to serialize export parameters. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Whether an existing destination may be overwritten. */
    private final Boolean overwrite;

    private ExportTeamParams(Builder builder) {
        this.overwrite = builder.overwrite;
    }

    /**
     * Creates a builder for export parameters.
     *
     * @return mutable builder for export parameters
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether an existing destination should be overwritten.
     *
     * @return overwrite setting, or {@code null} when omitted
     */
    public Boolean getOverwrite() {
        return overwrite;
    }

    /**
     * Serializes these parameters for the database package.
     *
     * @return JSON object containing the supplied parameters, or {@code null}
     *         when no optional parameter was supplied
     * @throws IllegalStateException when the parameters cannot be serialized
     */
    public String toParamsJson() {
        if (overwrite == null) {
            return null;
        }
        try {
            return MAPPER.createObjectNode()
                    .put("overwrite", overwrite)
                    .toString();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to serialize ExportTeamParams", e);
        }
    }

    /** Builder for immutable export parameters. */
    public static final class Builder {
        /** Whether an existing destination may be overwritten. */
        private Boolean overwrite;

        private Builder() {
        }

        /**
         * Sets whether an existing destination should be overwritten.
         *
         * @param overwrite whether an existing destination may be replaced
         * @return this builder
         */
        public Builder overwrite(Boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        /**
         * Builds immutable export parameters.
         *
         * @return immutable export parameters
         */
        public ExportTeamParams build() {
            return new ExportTeamParams(this);
        }
    }
}
