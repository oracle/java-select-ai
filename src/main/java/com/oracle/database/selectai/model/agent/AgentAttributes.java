/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Attributes used to create or update a Select AI Agent.
 *
 * <p>The database requires {@code profile_name} and {@code role} when the
 * agent is created. The builder permits these values to be unset or blank;
 * {@code DBMS_CLOUD_AI_AGENT} validates the required creation values and the
 * supplied attribute names and values when an update is attempted.
 * The builder checks the documented {@code shortTermMemoryLength} range in
 * Java.</p>
 *
 * <p>To create a supervisor-led team, designate its supervisor with
 * {@link Builder#supervisor(Boolean) supervisor(true)} at agent creation and
 * reference that agent with {@link TeamAttributes.Builder#supervisorAgent(String)}.
 * The supervisor flag cannot be changed after creation. Worker agents remain
 * separate from the supervisor in the team's agent-task mappings.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateAgentSample.html">
 *      CreateAgentSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetAgentAttributesSample.html">
 *      SetAgentAttributesSample source</a>
 * @since 1.1.0
 */
public final class AgentAttributes {
    /** Configured once and never mutated; shared across attribute serializations. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    /** AI profile used by the agent to call the model. */
    private final String profileName;
    /** Agent role sent to the model. */
    private final String role;
    /** Whether the agent can request human input. */
    private final Boolean enableHumanTool;
    /** Tool names available to the agent. */
    private final List<String> tools;
    /** Maximum number of conversation turns included in model calls. */
    private final Integer shortTermMemoryLength;
    /** Whether this agent acts as a supervisor. */
    private final Boolean supervisor;

    private AgentAttributes(Builder builder) {
        this.profileName = builder.profileName;
        this.role = builder.role;
        this.enableHumanTool = builder.enableHumanTool;
        this.tools = builder.tools == null ? null : List.copyOf(builder.tools);
        this.shortTermMemoryLength = builder.shortTermMemoryLength;
        this.supervisor = builder.supervisor;
    }

    /**
     * Returns the AI profile name.
     *
     * @return AI profile name, or {@code null} when unset
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Returns the agent role.
     *
     * @return agent role, or {@code null} when unset
     */
    public String getRole() {
        return role;
    }

    /**
     * Returns whether the human tool is enabled.
     *
     * @return whether the human tool is enabled, or {@code null} when unset
     */
    public Boolean getEnableHumanTool() {
        return enableHumanTool;
    }

    /**
     * Returns configured tool names.
     *
     * @return immutable tool names, or {@code null} when unset
     */
    public List<String> getTools() {
        return tools == null ? null : Collections.unmodifiableList(tools);
    }

    /**
     * Returns the short-term memory length.
     *
     * @return short-term memory length, or {@code null} when unset
     */
    public Integer getShortTermMemoryLength() {
        return shortTermMemoryLength;
    }

    /**
     * Returns whether the agent is a supervisor.
     *
     * @return whether the agent is a supervisor, or {@code null} when unset
     */
    public Boolean getSupervisor() {
        return supervisor;
    }

    /**
     * Creates a builder for agent attributes.
     *
     * @return mutable builder for agent attributes
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Serializes attributes using the snake-case names expected by
     * {@code DBMS_CLOUD_AI_AGENT}.
     *
     * @return JSON object containing non-null attributes
     * @throws IllegalStateException when the attributes cannot be serialized
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize AgentAttributes", e);
        }
    }

    /** Builder for {@link AgentAttributes}. */
    public static final class Builder {
        /** AI profile used by the agent. */
        private String profileName;
        /** Agent role sent to the model. */
        private String role;
        /** Whether human-tool use is enabled. */
        private Boolean enableHumanTool;
        /** Tool names available to the agent. */
        private List<String> tools;
        /** Maximum short-term memory length. */
        private Integer shortTermMemoryLength;
        /** Whether the agent is a supervisor. */
        private Boolean supervisor;

        /**
         * Sets the AI profile name.
         *
         * @param profileName AI profile name
         * @return this builder
         */
        public Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        /**
         * Sets the agent role.
         *
         * @param role role assigned to the agent
         * @return this builder
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * Sets whether the agent can request human input.
         *
         * @param enableHumanTool whether human-tool requests are enabled
         * @return this builder
         */
        public Builder enableHumanTool(Boolean enableHumanTool) {
            this.enableHumanTool = enableHumanTool;
            return this;
        }

        /**
         * Sets the names of tools available to the agent.
         *
         * @param tools tool names; the list is copied by the builder
         * @return this builder
         */
        public Builder tools(List<String> tools) {
            this.tools = tools == null ? null : new ArrayList<>(tools);
            return this;
        }

        /**
         * Sets the short-term memory length.
         *
         * @param shortTermMemoryLength non-negative memory length, or
         *        {@code null} to use the database default
         * @return this builder
         * @throws IllegalArgumentException when the value is negative
         */
        public Builder shortTermMemoryLength(Integer shortTermMemoryLength) {
            if (shortTermMemoryLength != null && shortTermMemoryLength < 0) {
                throw new IllegalArgumentException("shortTermMemoryLength must be non-negative");
            }
            this.shortTermMemoryLength = shortTermMemoryLength;
            return this;
        }

        /**
         * Sets whether the agent is a supervisor at creation time. The database
         * does not allow changing this flag after creation. Assign the created
         * supervisor to a team using
         * {@link TeamAttributes.Builder#supervisorAgent(String)}; do not include
         * it in that team's worker agent-task mappings.
         *
         * <p>For a runnable example, see
         * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SupervisorTeamSample.html">
         * SupervisorTeamSample source</a>.</p>
         *
         * @param supervisor whether the agent supervises other agents
         * @return this builder
         */
        public Builder supervisor(Boolean supervisor) {
            this.supervisor = supervisor;
            return this;
        }

        /**
         * Builds agent attributes without validating required database fields.
         * Required-field and semantic validation is performed by
         * {@code DBMS_CLOUD_AI_AGENT} when the agent is created.
         *
         * @return immutable agent attributes
         */
        public AgentAttributes build() {
            return new AgentAttributes(this);
        }
    }
}
