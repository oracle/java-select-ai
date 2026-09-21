/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * Request parameters for the {@code params} CLOB argument of
 * {@code DBMS_CLOUD_AI.GENERATE}.
 * <p>This model currently supports conversation-bound generation through
 * {@code conversation_id}. Instances are used by {@link com.oracle.database.selectai.Profile}
 * generate overloads and by {@link com.oracle.database.selectai.Session} to keep a
 * conversation identifier across related generate calls.</p>
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI.GENERATE reference</a>
 */
public final class GenerateParams {

    /** Conversation identifier passed to {@code DBMS_CLOUD_AI.GENERATE} params payload. */
    private final String conversationId;

    private GenerateParams(GenerateParams.Builder builder) {
        this.conversationId = builder.conversationId;
    }

    /**
     * Returns the conversation identifier.
     *
     * @return conversation ID
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Returns whether this instance contains no configured generate parameters.
     *
     * @return {@code true} when no generate parameters are configured
     */
    @JsonIgnore
    public boolean isEmpty() {
        return conversationId == null;
    }

    /**
     * Creates a builder for generate parameters.
     *
     * @return new builder for constructing GenerateParams
     */
    public static GenerateParams.Builder builder() {
        return new GenerateParams.Builder();
    }

    /**
     * Builder for {@link GenerateParams}.
     */
    public static final class Builder {
        /** Creates an empty builder. */
        public Builder() {
        }

        /** Conversation identifier passed to {@code DBMS_CLOUD_AI.GENERATE} params payload. */
        private String conversationId;

        /**
         * Sets the conversation identifier.
         *
         * @param conversationId conversation identifier
         * @return this builder instance
         */
        public GenerateParams.Builder conversationId(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                throw new IllegalArgumentException("conversationId must be provided");
            }
            this.conversationId = conversationId;
            return this;
        }

        /**
         * Builds immutable generate parameters.
         *
         * @return immutable GenerateParams built from current builder state
         */
        public GenerateParams build() {
            if (conversationId == null) {
                throw new IllegalArgumentException("at least one generate parameter must be set");
            }
            return new GenerateParams(this);
        }
    }


    /**
     * Serializes the parameter payload using DBMS_CLOUD_AI snake_case names.
     *
     * @return JSON representation of this GenerateParams instance
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize GenerateParams", e);
        }
    }
}
