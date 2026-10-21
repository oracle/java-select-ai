/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Request used to import a Select AI Agent team definition.
 * <p>
 * The request identifies the target profile and team and accepts exactly one
 * source: an object-storage or database-directory {@code location}, or inline
 * team {@code specification} JSON. Inline specifications may be supplied as
 * a JSON string or a JSON-compatible Java map. An optional object-storage
 * credential and force flag are passed to the database package. The builder
 * rejects blank required names and rejects requests that provide neither or
 * both sources; the database validates the definition and its referenced
 * resources.
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ImportTeamSample.html">
 *      ImportTeamSample source</a>
 * @since 1.1.0
 */
public final class ImportTeamRequest {
    /** JSON mapper used to serialize native Java specifications. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI profile name used during import. */
    private final String profileName;
    /** Team name for the imported team. */
    private final String teamName;
    /** Optional object-storage credential name. */
    private final String objectStorageCredentialName;
    /** Optional source object-storage URI or database directory file location. */
    private final String location;
    /** Optional inline team specification JSON. */
    private final String specification;
    /** Whether conflicting imported components should be replaced. */
    private final Boolean force;

    private ImportTeamRequest(Builder builder) {
        this.profileName = builder.profileName;
        this.teamName = builder.teamName;
        this.objectStorageCredentialName = builder.objectStorageCredentialName;
        this.location = builder.location;
        this.specification = builder.specification;
        this.force = builder.force;
    }

    /**
     * Returns the AI profile name.
     *
     * @return AI profile name
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Returns the imported team name.
     *
     * @return imported team name
     */
    public String getTeamName() {
        return teamName;
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
     * Returns the import source location.
     *
     * @return import source location, or {@code null} when unset
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns the inline team specification JSON.
     *
     * @return inline team specification JSON, or {@code null} when unset
     */
    public String getSpecification() {
        return specification;
    }

    /**
     * Returns whether conflicting components are replaced.
     *
     * @return force setting, or {@code null} when unset
     */
    public Boolean getForce() {
        return force;
    }

    /**
     * Creates a builder for import requests.
     *
     * @param profileName AI profile used during import
     * @param teamName name assigned to the imported team
     * @return mutable builder for an import request
     * @throws IllegalArgumentException when either name is null or blank
     */
    public static Builder builder(String profileName, String teamName) {
        return new Builder(profileName, teamName);
    }

    /** Builder for {@link ImportTeamRequest}. */
    public static final class Builder {
        /** AI profile name used during import. */
        private final String profileName;
        /** Team name for the imported team. */
        private final String teamName;
        /** Optional object-storage credential name. */
        private String objectStorageCredentialName;
        /** Optional source location. */
        private String location;
        /** Optional inline team specification JSON. */
        private String specification;
        /** Whether conflicting components are replaced. */
        private Boolean force;

        private Builder(String profileName, String teamName) {
            if (profileName == null || profileName.isBlank()) {
                throw new IllegalArgumentException("profileName must not be null or blank");
            }
            if (teamName == null || teamName.isBlank()) {
                throw new IllegalArgumentException("teamName must not be null or blank");
            }
            this.profileName = profileName;
            this.teamName = teamName;
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
         * Sets the import source location.
         *
         * @param location object-storage URI or database directory file
         * @return this builder
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * Sets the inline team specification JSON.
         *
         * @param specification team definition JSON
         * @return this builder
         */
        public Builder specification(String specification) {
            this.specification = specification;
            return this;
        }

        /**
         * Sets the inline team specification from a JSON-compatible map.
         *
         * @param specification native Java representation of the team definition
         * @return this builder
         * @throws IllegalArgumentException when the value cannot be serialized as JSON
         * @since 1.1.0
         */
        public Builder specification(Map<String, ?> specification) {
            if (specification == null) {
                this.specification = null;
                return this;
            }
            try {
                this.specification = MAPPER.writeValueAsString(specification);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("specification must be JSON-compatible", e);
            }
            return this;
        }

        /**
         * Sets whether conflicting imported components should be replaced.
         *
         * @param force whether conflicting resources may be replaced
         * @return this builder
         */
        public Builder force(Boolean force) {
            this.force = force;
            return this;
        }

        /**
         * Builds immutable import request.
         *
         * @return immutable import request
         * @throws IllegalArgumentException when neither or both import sources
         *                                  are provided
         */
        public ImportTeamRequest build() {
            boolean hasLocation = location != null && !location.isBlank();
            boolean hasSpecification = specification != null && !specification.isBlank();
            if (hasLocation == hasSpecification) {
                throw new IllegalArgumentException("exactly one of location or specification must be provided");
            }
            return new ImportTeamRequest(this);
        }
    }
}
