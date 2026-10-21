/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Typed parameters for a {@code SET_EXECUTION_STATE} operation.
 *
 * <p>The database identifies the target run with {@code conversation_id}.
 * This model requires that identifier explicitly, so the SDK never relies on
 * the current database session conversation.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamExecutionStateSample.html">
 *      SetTeamExecutionStateSample source</a>
 * @since 1.1.0
 */
public final class ExecutionStateParams {
    /** JSON mapper used to serialize the typed conversation parameter. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Explicit database conversation identifier. */
    private final String conversationId;

    private ExecutionStateParams(Builder builder) {
        this.conversationId = builder.conversationId;
    }

    /**
     * Creates a builder for execution-state parameters.
     *
     * @return mutable builder for execution-state parameters
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates parameters for one database conversation.
     *
     * @param conversationId database-created conversation identifier
     * @return immutable execution-state parameters
     * @throws IllegalArgumentException when the identifier is null or blank
     */
    public static ExecutionStateParams forConversationId(String conversationId) {
        return builder().conversationId(conversationId).build();
    }

    /**
     * Returns the database conversation identifier.
     *
     * @return database conversation identifier
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Returns the JSON object passed to the database package.
     *
     * @return JSON object containing {@code conversation_id}
     */
    public String toParamsJson() {
        return MAPPER.createObjectNode()
                .put("conversation_id", conversationId)
                .toString();
    }

    /** Builder for immutable execution-state parameters. */
    public static final class Builder {
        /** Explicit database conversation identifier. */
        private String conversationId;

        private Builder() {
        }

        /**
         * Sets the database conversation identifier.
         *
         * @param conversationId database-created conversation identifier
         * @return this builder
         * @throws IllegalArgumentException when the identifier is null or blank
         */
        public Builder conversationId(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                throw new IllegalArgumentException("conversationId must not be null or blank");
            }
            this.conversationId = conversationId;
            return this;
        }

        /**
         * Builds immutable execution-state parameters.
         *
         * @return immutable execution-state parameters
         * @throws IllegalArgumentException when no conversation identifier was supplied
         */
        public ExecutionStateParams build() {
            if (conversationId == null) {
                throw new IllegalArgumentException("conversationId must be provided");
            }
            return new ExecutionStateParams(this);
        }
    }
}
