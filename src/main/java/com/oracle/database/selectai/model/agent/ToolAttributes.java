/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.List;

/**
 * Attributes used to create or update a Select AI Agent tool.
 * <p>
 * {@code toolParams} uses immutable {@link ToolParams} models, such as
 * {@link SqlToolParams}. {@code toolInputs} uses an immutable list of
 * {@link ToolInput} entries containing input names and descriptions.
 * The builder snapshots both when supplied, so later changes to caller-owned
 * collections cannot change the configuration or JSON sent to the database.
 * Additional tool parameters are preserved when loading database metadata;
 * unrecognized parameter schemas use {@link GenericToolParams}.
 * The database validates whether the attributes are semantically valid for the
 * selected tool type and database release.
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
 *      CreateToolSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolAttributesSample.html">
 *      SetToolAttributesSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
 *      ToolParamsSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
 *      ToolInputSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
 *      CreateToolWithInputsSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolInputsSample.html">
 *      SetToolInputsSample source</a>
 * @since 1.1.0
 */
public final class ToolAttributes {
    /** Configured once and never mutated; retains explicit nulls within parameter maps. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setDefaultPropertyInclusion(JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));

    /** Tool instruction sent to the model. */
    private final String instruction;
    /** PL/SQL function or procedure used by a custom tool. */
    private final String function;
    /** Built-in or database-defined tool type. */
    private final ToolType toolType;
    /** Immutable typed built-in tool parameters. */
    private final ToolParams toolParams;
    /** Immutable input metadata, or null when not supplied. */
    private final List<ToolInput> toolInputs;

    private ToolAttributes(Builder builder) {
        this.instruction = builder.instruction;
        this.function = builder.functionName;
        this.toolType = builder.toolType;
        this.toolParams = builder.toolParams;
        this.toolInputs = builder.toolInputs;
    }

    /**
     * Returns the tool instruction.
     *
     * @return tool instruction, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     *      LoadToolSample source</a>
     */
    public String getInstruction() {
        return instruction;
    }

    /**
     * Returns the custom PL/SQL function or procedure name.
     *
     * @return function or procedure name, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     *      LoadToolSample source</a>
     */
    public String getFunction() {
        return function;
    }

    /**
     * Returns the built-in or database-defined tool type.
     * <p>
     * The returned {@link ToolType} preserves a value introduced by a newer
     * database release, even when this SDK does not yet define a named constant
     * for it.
     *
     * @return tool type, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     *      LoadToolSample source</a>
     */
    public ToolType getToolType() {
        return toolType;
    }

    /**
     * Returns the raw database tool type value.
     *
     * @return database tool type value, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     *      LoadToolSample source</a>
     */
    @JsonIgnore
    public String getToolTypeValue() {
        return toolType == null ? null : toolType.getValue();
    }

    /**
     * Returns typed built-in tool parameters, including additional database fields.
     * Use {@link ToolParams#toMap()} for a complete immutable parameter map.
     *
     * @return immutable tool parameters, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     *      LoadToolSample source</a>
     */
    public ToolParams getToolParams() {
        return toolParams;
    }

    /**
     * Returns immutable tool input metadata in the supplied or database order.
     *
     * @return immutable tool input metadata, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     *      LoadToolSample source</a>
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
     *      ToolInputSample source</a>
     */
    public List<ToolInput> getToolInputs() {
        return toolInputs;
    }

    /**
     * Creates a builder for tool attributes.
     *
     * @return mutable builder for tool attributes
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
     *      CreateToolWithInputsSample source</a>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Serializes attributes using the snake-case names expected by
     * {@code DBMS_CLOUD_AI_AGENT}.
     *
     * Null attributes and unset input fields are omitted; explicit null values
     * inside parameter maps are retained.
     *
     * @return JSON object containing non-null attributes
     * @throws IllegalStateException when the attributes cannot be serialized
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
     *      ToolInputSample source</a>
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ToolAttributes", e);
        }
    }

    /**
     * Builder for {@link ToolAttributes}.
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
     *      CreateToolWithInputsSample source</a>
     */
    public static final class Builder {
        /** Tool instruction sent to the model. */
        private String instruction;
        /** Custom PL/SQL function or procedure name. */
        private String functionName;
        /** Built-in or database-defined tool type. */
        private ToolType toolType;
        /** Immutable typed built-in tool parameters. */
        private ToolParams toolParams;
        /** Immutable snapshot of tool input metadata. */
        private List<ToolInput> toolInputs;

        /**
         * Sets the tool instruction.
         *
         * @param instruction tool instruction
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
         *      CreateToolWithInputsSample source</a>
         */
        public Builder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        /**
         * Sets the custom PL/SQL function or procedure name.
         *
         * @param functionName function or procedure name
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
         *      CreateToolWithInputsSample source</a>
         */
        public Builder function(String functionName) {
            this.functionName = functionName;
            return this;
        }

        /**
         * Sets a known or database-defined tool type.
         *
         * @param toolType tool type value object
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSqlToolSample.html">
         *      CreateSqlToolSample source</a>
         */
        public Builder toolType(ToolType toolType) {
            this.toolType = toolType;
            return this;
        }

        /**
         * Sets a tool type directly from its database value.
         *
         * @param toolType database tool type value
         * @return this builder
         * @throws IllegalArgumentException when {@code toolType} is blank
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
         *      CreateToolSample source</a>
         */
        public Builder toolTypeValue(String toolType) {
            this.toolType = toolType == null ? null : ToolType.of(toolType);
            return this;
        }

        /**
         * Snapshots typed built-in tool parameters. Use a tool-specific builder
         * for known fields or {@link GenericToolParams} for an extensible
         * parameter object. Required fields are validated by the database.
         *
         * @param toolParams tool parameters, or null to omit them
         * @return this builder
         * @throws IllegalArgumentException when the value is not JSON-compatible
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSqlToolSample.html">
         *      CreateSqlToolSample source</a>
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetSqlToolParamsSample.html">
         *      SetSqlToolParamsSample source</a>
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
         *      ToolParamsSample source</a>
         */
        public Builder toolParams(ToolParams toolParams) {
            this.toolParams = ToolParamsSupport.copy(toolParams);
            return this;
        }

        /**
         * Copies tool input metadata into an immutable list. Each entry supplies
         * a name and optional description; the database validates their meaning.
         * An empty list is serialized as an empty {@code tool_inputs} array.
         *
         * @param toolInputs input metadata, or {@code null} to omit it
         * @return this builder
         * @throws IllegalArgumentException when the list contains a null entry
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
         *      ToolInputSample source</a>
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
         *      CreateToolSample source</a>
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
         *      CreateToolWithInputsSample source</a>
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolInputsSample.html">
         *      SetToolInputsSample source</a>
         */
        public Builder toolInputs(List<ToolInput> toolInputs) {
            if (toolInputs == null) {
                this.toolInputs = null;
            } else {
                try {
                    this.toolInputs = List.copyOf(toolInputs);
                } catch (NullPointerException e) {
                    throw new IllegalArgumentException("toolInputs must not contain null entries", e);
                }
            }
            return this;
        }

        /**
         * Builds immutable tool attributes.
         *
         * @return immutable tool attributes
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
         *      CreateToolWithInputsSample source</a>
         */
        public ToolAttributes build() {
            return new ToolAttributes(this);
        }
    }
}
