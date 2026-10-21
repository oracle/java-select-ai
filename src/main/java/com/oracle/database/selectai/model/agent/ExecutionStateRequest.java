/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

/**
 * Request used to update an agent team execution state.
 *
 * <p>The request contains the state and typed {@link ExecutionStateParams}
 * identifying the conversation to update. The builder also exposes a
 * convenience method for the common conversation-ID case. Every request
 * requires an explicit conversation identifier; the SDK does not rely on a
 * current database session conversation.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamExecutionStateSample.html">
 *      SetTeamExecutionStateSample source</a>
 * @since 1.1.0
 */
public final class ExecutionStateRequest {
    /** Run state to set. */
    private final TeamExecutionState state;
    /** Parameters identifying the target execution. */
    private final ExecutionStateParams params;

    private ExecutionStateRequest(Builder builder) {
        this.state = builder.state;
        this.params = builder.params != null
                ? builder.params
                : ExecutionStateParams.forConversationId(builder.conversationId);
    }

    /**
     * Returns the run state to set.
     *
     * @return known execution state
     */
    public TeamExecutionState getState() {
        return state;
    }

    /**
     * Returns the typed execution-state parameters.
     *
     * @return immutable execution-state parameters
     */
    public ExecutionStateParams getParams() {
        return params;
    }

    /**
     * Returns the conversation identifier from the typed parameters.
     *
     * @return database-created conversation identifier
     */
    public String getConversationId() {
        return params.getConversationId();
    }

    /**
     * Serializes the parameters for {@code SET_EXECUTION_STATE}.
     *
     * @return JSON object containing the target conversation identifier
     */
    public String toParamsJson() {
        return params.toParamsJson();
    }

    /**
     * Creates a builder for an execution-state request.
     *
     * @param state known state accepted by the database operation
     * @return mutable builder for an execution-state request
     * @throws IllegalArgumentException when {@code state} is null or
     *                                  {@link TeamExecutionState#UNKNOWN}
     */
    public static Builder builder(TeamExecutionState state) {
        return new Builder(state);
    }

    /** Builder for {@link ExecutionStateRequest}. */
    public static final class Builder {
        /** Run state to set. */
        private final TeamExecutionState state;
        /** Optional convenience conversation identifier. */
        private String conversationId;
        /** Optional typed execution-state parameters. */
        private ExecutionStateParams params;

        private Builder(TeamExecutionState state) {
            if (state == null || state == TeamExecutionState.UNKNOWN) {
                throw new IllegalArgumentException("state must be a known execution state");
            }
            this.state = state;
        }

        /**
         * Sets the conversation identifier for the execution to update.
         *
         * @param conversationId database-created conversation identifier
         * @return this builder
         * @throws IllegalArgumentException when the identifier is null or blank
         * @throws IllegalStateException when typed parameters were already supplied
         */
        public Builder conversationId(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                throw new IllegalArgumentException("conversationId must not be null or blank");
            }
            requireNoTypedParameters("conversationId");
            this.conversationId = conversationId;
            return this;
        }

        /**
         * Sets typed parameters for the execution to update.
         *
         * @param params typed parameters containing a conversation identifier
         * @return this builder
         */
        public Builder params(ExecutionStateParams params) {
            if (params == null) {
                throw new IllegalArgumentException("params must not be null");
            }
            requireNoTypedParameters("params");
            this.params = params;
            return this;
        }

        /**
         * Builds an immutable execution-state request.
         *
         * @return immutable execution-state request
         * @throws IllegalArgumentException when no conversation identifier or
         *                                  typed parameters were supplied
         */
        public ExecutionStateRequest build() {
            if (params == null && conversationId == null) {
                throw new IllegalArgumentException(
                        "conversationId or params must be provided");
            }
            return new ExecutionStateRequest(this);
        }

        private void requireNoTypedParameters(String parameterName) {
            if (params != null || conversationId != null) {
                throw new IllegalStateException(
                        parameterName + " cannot be combined with another parameter form");
            }
        }
    }
}
