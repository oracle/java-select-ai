/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.SelectAIAgent;

import java.util.Map;

/**
 * Input and optional runtime parameters for {@link Team#run(RunTeamRequest)}
 * and {@link SelectAIAgent#runTeam(String, RunTeamRequest)}.
 *
 * <p>For a run without established database session context, supply a
 * conversation identifier returned by {@link Conversation#create()} or
 * {@code DBMS_CLOUD_AI.CREATE_CONVERSATION}; an arbitrary client-generated ID
 * is not sufficient. Reuse the same identifier for an initial run and a later
 * call that resumes a team waiting for human input, including across pooled
 * database connections. The caller must retain the identifier between calls.
 * Use {@link RunTeamParams} to provide the complete typed runtime-parameter
 * object. The convenience builder methods on this class are equivalent.
 * Runtime maps are copied when set and are not included in diagnostic output
 * by this class.</p>
 *
 * <p>For a runnable example, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunTeamSample.html">
 * RunTeamSample source</a>.</p>
 *
 * @since 1.1.0
 */
public final class RunTeamRequest {
    /** Optional user prompt or response to a request for human input. */
    private final String userPrompt;
    /** Typed runtime parameters, or null when none were supplied. */
    private final RunTeamParams params;

    private RunTeamRequest(Builder builder, RunTeamParams params) {
        this.userPrompt = builder.userPrompt;
        this.params = params;
    }

    /**
     * Returns the optional prompt or response supplied to the team.
     *
     * @return user prompt or human response, or {@code null} when unset
     */
    public String getUserPrompt() {
        return userPrompt;
    }

    /**
     * Returns the optional conversation identifier.
     *
     * @return conversation identifier, or {@code null} when unset
     */
    public String getConversationId() {
        return params == null ? null : params.getConversationId();
    }

    /**
     * Returns immutable instruction variables, or {@code null} when unset.
     *
     * @return immutable instruction variables, or {@code null}
     */
    public Map<String, Object> getInstructionVariables() {
        return params == null ? null : params.getInstructionVariables();
    }

    /**
     * Returns immutable attribute variables, or {@code null} when unset.
     *
     * @return immutable attribute variables, or {@code null}
     */
    public Map<String, Object> getAttributeVariables() {
        return params == null ? null : params.getAttributeVariables();
    }

    /**
     * Returns immutable state variables, or {@code null} when unset.
     *
     * @return immutable state variables, or {@code null}
     */
    public Map<String, Object> getStateVariables() {
        return params == null ? null : params.getStateVariables();
    }

    /**
     * Returns the typed runtime parameters, or {@code null} when unset.
     *
     * @return immutable runtime parameters, or {@code null}
     */
    public RunTeamParams getParams() {
        return params;
    }

    /**
     * Returns the database params JSON, or {@code null} when no runtime
     * parameters were set.
     *
     * @return serialized runtime parameters, or {@code null}
     */
    public String toParamsJson() {
        return params == null ? null : params.toParamsJson();
    }

    /**
     * Creates a builder for a team run.
     *
     * @return mutable builder for a team-run request
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link RunTeamRequest}. */
    public static final class Builder {
        /** Optional user prompt or response to a request for human input. */
        private String userPrompt;
        /** Typed runtime parameters. */
        private RunTeamParams params;
        /** Optional identifier used by the convenience builder methods. */
        private String conversationId;
        /** Optional instruction variables used by the convenience builder methods. */
        private Map<String, Object> instructionVariables;
        /** Optional attribute variables used by the convenience builder methods. */
        private Map<String, Object> attributeVariables;
        /** Optional state variables used by the convenience builder methods. */
        private Map<String, Object> stateVariables;

        private Builder() {
        }

        /**
         * Sets the optional team prompt or human response.
         *
         * @param userPrompt user query or response to a human-input request
         * @return this builder
         */
        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        /**
         * Sets typed runtime parameters for the team run.
         *
         * @param params immutable runtime parameters
         * @return this builder
         */
        public Builder params(RunTeamParams params) {
            this.params = params;
            return this;
        }

        /**
         * Sets an application-managed conversation identifier for cross-session
         * runs.
         *
         * @param conversationId database-created conversation identifier
         * @return this builder
         */
        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        /**
         * Sets instruction variables as a snapshot of a JSON-compatible map.
         *
         * @param instructionVariables values substituted into task instructions
         * @return this builder
         */
        public Builder instructionVariables(Map<String, ?> instructionVariables) {
            this.instructionVariables = snapshot(instructionVariables);
            return this;
        }

        /**
         * Sets attribute variables as a snapshot of a JSON-compatible map.
         *
         * @param attributeVariables values that override configurable attributes
         * @return this builder
         */
        public Builder attributeVariables(Map<String, ?> attributeVariables) {
            this.attributeVariables = snapshot(attributeVariables);
            return this;
        }

        /**
         * Sets state variables as a snapshot of a JSON-compatible map.
         *
         * @param stateVariables values shared between tools during a run
         * @return this builder
         */
        public Builder stateVariables(Map<String, ?> stateVariables) {
            this.stateVariables = snapshot(stateVariables);
            return this;
        }

        /**
         * Builds an immutable request; a prompt and runtime parameters are
         * optional. Building does not modify this builder; it may be reused
         * to create further requests without changing previously built ones.
         *
         * <p>For a runnable example, see
         * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunTeamParamsSample.html">
         * RunTeamParamsSample source</a>.</p>
         *
         * @return immutable team-run request
         * @throws IllegalArgumentException when typed and convenience runtime
         *                                  parameters are mixed
         */
        public RunTeamRequest build() {
            if (params != null && (conversationId != null
                    || instructionVariables != null || attributeVariables != null
                    || stateVariables != null)) {
                throw new IllegalArgumentException(
                        "params cannot be combined with individual runtime parameter fields");
            }
            RunTeamParams resolvedParams = params;
            if (resolvedParams == null && (conversationId != null
                    || instructionVariables != null || attributeVariables != null
                    || stateVariables != null)) {
                RunTeamParams.Builder paramsBuilder = RunTeamParams.builder();
                if (conversationId != null) {
                    paramsBuilder.conversationId(conversationId);
                }
                if (instructionVariables != null) {
                    paramsBuilder.instructionVariables(instructionVariables);
                }
                if (attributeVariables != null) {
                    paramsBuilder.attributeVariables(attributeVariables);
                }
                if (stateVariables != null) {
                    paramsBuilder.stateVariables(stateVariables);
                }
                resolvedParams = paramsBuilder.build();
            }
            return new RunTeamRequest(this, resolvedParams);
        }

        private static Map<String, Object> snapshot(Map<String, ?> values) {
            if (values == null) {
                return null;
            }
            return RunTeamParams.builder().instructionVariables(values).build()
                    .getInstructionVariables();
        }
    }
}
