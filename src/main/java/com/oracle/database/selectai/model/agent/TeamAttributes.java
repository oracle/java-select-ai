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
 * Attributes used to create a Select AI Agent team.
 *
 * <p>The JSON representation follows the {@code CREATE_TEAM} attributes
 * accepted by {@code DBMS_CLOUD_AI_AGENT}. The database requires the
 * {@code agents} attribute and uses a default long-term memory length of 30
 * when {@code longTermMemoryLength} is not specified. Required-field and
 * other database-specific validation is delegated to Oracle Database; the
 * SDK performs only the documented Java range validation.</p>
 *
 * <p>For a supervisor-led team, {@code supervisorAgent} references an agent
 * created with {@link AgentAttributes.Builder#supervisor(Boolean) supervisor(true)}.
 * The {@code agents} mappings contain workers only, not the selected supervisor.
 * Use {@code process("sequential")} for one-at-a-time worker delegation.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTeamSample.html">
 *      CreateTeamSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamAttributesSample.html">
 *      SetTeamAttributesSample source</a>
 * @since 1.1.0
 */
public final class TeamAttributes {
    /** Configured once and never mutated; shared across attribute serializations. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    /** Agent and task mappings included in the team. */
    private final List<TeamMember> agents;
    /** Team task execution process, such as {@code sequential}. */
    private final String process;
    /** Maximum number of prior task summaries retained as team memory. */
    private final Integer longTermMemoryLength;
    /** Name of an existing agent created with the supervisor flag enabled. */
    private final String supervisorAgent;

    private TeamAttributes(Builder builder) {
        this.agents = builder.agents == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(builder.agents));
        this.process = builder.process;
        this.longTermMemoryLength = builder.longTermMemoryLength;
        this.supervisorAgent = builder.supervisorAgent;
    }

    /**
     * Returns the team agent and task mappings, or {@code null} when unset.
     *
     * @return team agent and task mappings, or {@code null}
     */
    public List<TeamMember> getAgents() {
        return agents;
    }

    /**
     * Returns the team task execution process.
     *
     * @return team task execution process, or {@code null} when unset
     */
    public String getProcess() {
        return process;
    }

    /**
     * Returns the configured long-term memory length, or {@code null} when the
     * database default applies.
     *
     * @return long-term memory length, or {@code null} when unset
     */
    public Integer getLongTermMemoryLength() {
        return longTermMemoryLength;
    }

    /**
     * Returns the supervisor agent name, or {@code null} for a regular team.
     *
     * @return supervisor agent name, or {@code null}
     */
    public String getSupervisorAgent() {
        return supervisorAgent;
    }

    /**
     * Creates a builder for team attributes.
     *
     * @return mutable builder for team attributes
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
            throw new IllegalStateException("Failed to serialize TeamAttributes", e);
        }
    }

    /** Builder for {@link TeamAttributes}. */
    public static final class Builder {
        /** Agent and task mappings included in the team. */
        private List<TeamMember> agents;
        /** Team task execution process. */
        private String process;
        /** Maximum number of prior task summaries retained as team memory. */
        private Integer longTermMemoryLength;
        /** Name of an existing agent created with the supervisor flag enabled. */
        private String supervisorAgent;

        /**
         * Sets the team agent and task mappings.
         *
         * @param agents team members; the list is copied by the builder
         * @return this builder
         */
        public Builder agents(List<TeamMember> agents) {
            this.agents = agents == null ? null : new ArrayList<>(agents);
            return this;
        }

        /**
         * Sets the team task execution process, such as {@code sequential}.
         *
         * @param process database-defined team process
         * @return this builder
         */
        public Builder process(String process) {
            this.process = process;
            return this;
        }

        /**
         * Sets the maximum number of prior task summaries retained as team
         * memory. A {@code null} value uses the database default of 30.
         *
         * @param longTermMemoryLength memory length; must not be negative
         * @return this builder
         * @throws IllegalArgumentException when the value is negative
         */
        public Builder longTermMemoryLength(Integer longTermMemoryLength) {
            if (longTermMemoryLength != null && longTermMemoryLength < 0) {
                throw new IllegalArgumentException("longTermMemoryLength must be non-negative");
            }
            this.longTermMemoryLength = longTermMemoryLength;
            return this;
        }

        /**
         * Assigns an existing supervisor agent to this team. Create that agent
         * with {@link AgentAttributes.Builder#supervisor(Boolean) supervisor(true)}
         * first, and keep it out of {@link #agents(List)}. Use
         * {@code process("sequential")} for worker delegation.
         *
         * <p>For a runnable example, see
         * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SupervisorTeamSample.html">
         * SupervisorTeamSample source</a>.</p>
         *
         * @param supervisorAgent supervisor agent name
         * @return this builder
         */
        public Builder supervisorAgent(String supervisorAgent) {
            this.supervisorAgent = supervisorAgent;
            return this;
        }

        /**
         * Builds team attributes without validating required database fields.
         * Required-field and semantic validation is performed by
         * {@code DBMS_CLOUD_AI_AGENT} when the team is created.
         *
         * @return immutable team attributes
         */
        public TeamAttributes build() {
            return new TeamAttributes(this);
        }
    }

    /** Team member definition used in agent-team attributes. */
    public static final class TeamMember {
        /** Agent name. */
        private final String name;
        /** Task routed to the agent. */
        private final String task;

        private TeamMember(MemberBuilder builder) {
            this.name = builder.name;
            this.task = builder.task;
        }

        /**
         * Creates a builder for a team member definition.
         *
         * @param name agent name, validated by the database when the team is created
         * @return team member builder
         */
        public static MemberBuilder builder(String name) {
            return new MemberBuilder(name);
        }

        /**
         * Returns the agent name.
         *
         * @return agent name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the task routed to the agent.
         *
         * @return task name
         */
        public String getTask() {
            return task;
        }

        /** Builder for {@link TeamMember}. */
        public static final class MemberBuilder {
            /** Agent or task name. */
            private final String name;
            /** Task routed to the agent. */
            private String task;

            private MemberBuilder(String name) {
                this.name = name;
            }

            /**
             * Sets the task routed to the agent.
             *
             * @param task task name, validated by the database when the team is created
             * @return this member builder
             */
            public MemberBuilder task(String task) {
                this.task = task;
                return this;
            }

            /**
             * Builds a team member definition without validating required
             * database fields. Required-field validation is performed by
             * {@code DBMS_CLOUD_AI_AGENT} when the team is created.
             *
             * @return immutable team member definition
             */
            public TeamMember build() {
                return new TeamMember(this);
            }
        }
    }
}
