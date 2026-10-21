/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

/**
 * Request used to export a team definition to a file or object-storage URI.
 *
 * <p>The destination location is required. An object-storage credential may
 * be supplied when the destination requires it. Export parameters are modeled
 * by {@link ExportTeamParams}; the database validates the destination,
 * credential, parameters, and caller privileges.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ExportTeamSample.html">
 *      ExportTeamSample source</a>
 * @since 1.1.0
 */
public final class ExportTeamRequest {
    /** Credential name used for object storage, if applicable. */
    private final String objectStorageCredentialName;
    /** Target object-storage URI or database directory file location. */
    private final String location;
    /** Typed export parameters. */
    private final ExportTeamParams params;

    private ExportTeamRequest(Builder builder) {
        this.objectStorageCredentialName = builder.objectStorageCredentialName;
        this.location = builder.location;
        this.params = builder.params != null
                ? builder.params
                : builder.overwrite == null
                ? null
                : ExportTeamParams.builder().overwrite(builder.overwrite).build();
    }

    /**
     * Returns the object-storage credential name.
     *
     * @return object-storage credential name, or {@code null} when unset
     */
    public String getObjectStorageCredentialName() {
        return objectStorageCredentialName;
    }

    /**
     * Returns the target export location.
     *
     * @return target export location
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns the typed export parameters, or {@code null} when omitted.
     *
     * @return immutable export parameters, or {@code null}
     */
    public ExportTeamParams getParams() {
        return params;
    }

    /**
     * Returns whether existing target files should be overwritten.
     *
     * @return overwrite setting, or {@code null} when unset
     */
    public Boolean getOverwrite() {
        return params == null ? null : params.getOverwrite();
    }

    /**
     * Creates a builder for export requests.
     *
     * @param location target object-storage URI or database directory file
     * @return mutable builder for an export request
     * @throws IllegalArgumentException when the location is null or blank
     */
    public static Builder builder(String location) {
        return new Builder(location);
    }

    /**
     * Returns the typed export parameters as JSON for the database package.
     *
     * @return serialized export parameters, or {@code null} when omitted
     */
    public String toParamsJson() {
        return params == null ? null : params.toParamsJson();
    }

    /** Builder for {@link ExportTeamRequest}. */
    public static final class Builder {
        /** Credential name used for object storage, if applicable. */
        private String objectStorageCredentialName;
        /** Target location. */
        private final String location;
        /** Convenience overwrite value. */
        private Boolean overwrite;
        /** Typed export parameters. */
        private ExportTeamParams params;

        private Builder(String location) {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location must not be null or blank");
            }
            this.location = location;
        }

        /**
         * Sets the object-storage credential name.
         *
         * @param objectStorageCredentialName database credential name
         * @return this builder
         */
        public Builder objectStorageCredentialName(String objectStorageCredentialName) {
            this.objectStorageCredentialName = objectStorageCredentialName;
            return this;
        }

        /**
         * Sets typed export parameters.
         *
         * @param params immutable export parameters
         * @return this builder
         * @throws IllegalStateException when convenience overwrite was already set
         */
        public Builder params(ExportTeamParams params) {
            if (params == null) {
                this.params = null;
                return this;
            }
            if (overwrite != null) {
                throw new IllegalStateException("params and overwrite cannot both be set");
            }
            this.params = params;
            return this;
        }

        /**
         * Sets whether existing target files should be overwritten.
         *
         * @param overwrite whether an existing destination may be replaced
         * @return this builder
         * @throws IllegalStateException when typed parameters were already set
         */
        public Builder overwrite(Boolean overwrite) {
            if (overwrite != null && params != null) {
                throw new IllegalStateException("overwrite and params cannot both be set");
            }
            this.overwrite = overwrite;
            return this;
        }

        /**
         * Builds immutable export request.
         *
         * @return immutable export request
         */
        public ExportTeamRequest build() {
            return new ExportTeamRequest(this);
        }
    }
}
