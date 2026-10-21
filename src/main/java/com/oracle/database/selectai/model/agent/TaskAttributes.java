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
 * Attributes used to create or update a Select AI Agent task.
 * <p>
 * Values are serialized with the snake-case names expected by
 * {@code DBMS_CLOUD_AI_AGENT}. Lists are copied into immutable snapshots.
 * The SDK preserves nullable optional values and delegates semantic validation,
 * including relationships to tools and other tasks, to the database.
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateTaskSample.html">
 *      CreateTaskSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTaskAttributesSample.html">
 *      SetTaskAttributesSample source</a>
 * @since 1.1.0
 */
public final class TaskAttributes {
    /** Configured once and never mutated; shared across attribute serializations. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    /** Task instruction sent to the model. */
    private final String instruction;
    /** Tool names available to the task. */
    private final List<String> tools;
    /** Upstream task names used as input. */
    private final List<String> input;
    /** Whether this task can request human input. */
    private final Boolean enableHumanTool;

    private TaskAttributes(Builder builder) {
        this.instruction = builder.instruction;
        this.tools = builder.tools == null ? null : List.copyOf(builder.tools);
        this.input = builder.input == null ? null : List.copyOf(builder.input);
        this.enableHumanTool = builder.enableHumanTool;
    }

    /**
     * Returns the task instruction.
     *
     * @return task instruction, or {@code null} when unset
     */
    public String getInstruction() {
        return instruction;
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
     * Returns configured upstream task inputs.
     *
     * @return immutable upstream task names, or {@code null} when unset
     */
    public List<String> getInput() {
        return input == null ? null : Collections.unmodifiableList(input);
    }

    /**
     * Returns whether human-tool use is enabled.
     *
     * @return whether human-tool use is enabled, or {@code null} when unset
     */
    public Boolean getEnableHumanTool() {
        return enableHumanTool;
    }

    /**
     * Creates a builder for task attributes.
     *
     * @return mutable builder for task attributes
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
            throw new IllegalStateException("Failed to serialize TaskAttributes", e);
        }
    }

    /** Builder for {@link TaskAttributes}. */
    public static final class Builder {
        /** Task instruction sent to the model. */
        private String instruction;
        /** Tool names available to the task. */
        private List<String> tools;
        /** Upstream task names used as input. */
        private List<String> input;
        /** Whether human-tool use is enabled. */
        private Boolean enableHumanTool;

        /**
         * Sets the task instruction.
         *
         * @param instruction task instruction
         * @return this builder
         */
        public Builder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        /**
         * Sets the names of tools available to the task.
         *
         * @param tools tool names; the list is copied by the builder
         * @return this builder
         */
        public Builder tools(List<String> tools) {
            this.tools = tools == null ? null : new ArrayList<>(tools);
            return this;
        }

        /**
         * Sets upstream task names used as input.
         *
         * @param input upstream task names; the list is copied by the builder
         * @return this builder
         */
        public Builder input(List<String> input) {
            this.input = input == null ? null : new ArrayList<>(input);
            return this;
        }

        /**
         * Sets whether this task can request human input.
         *
         * @param enableHumanTool whether human-tool requests are enabled
         * @return this builder
         */
        public Builder enableHumanTool(Boolean enableHumanTool) {
            this.enableHumanTool = enableHumanTool;
            return this;
        }

        /** Builds immutable task attributes. */
        public TaskAttributes build() {
            return new TaskAttributes(this);
        }
    }
}
