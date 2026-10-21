/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

/**
 * Immutable metadata for one entry in a tool's {@code tool_inputs} array.
 * <p>
 * The name identifies the tool input; the optional description provides
 * instructions to the model. Describing supported values does not enforce
 * those values in Java. The database validates input names and their
 * compatibility with the configured tool. This model describes an input,
 * not the input value passed to {@code Tool.run(...)}.
 *
 * @see ToolAttributes
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
 *      ToolInputSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.html">
 *      CreateToolWithInputsSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetToolInputsSample.html">
 *      SetToolInputsSample source</a>
 * @since 1.1.0
 */
public final class ToolInput {
    /** Input name understood by the configured tool. */
    private final String name;
    /** Optional model-facing description of the input. */
    private final String description;

    private ToolInput(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
    }

    /**
     * Returns the input name without accessing the database.
     *
     * @return input name, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
     *      ToolInputSample source</a>
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the description supplied to the model, not a validation rule.
     *
     * @return input description, or {@code null} when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
     *      ToolInputSample source</a>
     */
    public String getDescription() {
        return description;
    }

    /**
     * Creates a builder for tool input metadata.
     *
     * @return mutable tool input builder
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
     *      ToolInputSample source</a>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ToolInput}; semantic validation is delegated to the database.
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
     *      ToolInputSample source</a>
     */
    public static final class Builder {
        /** Input name understood by the configured tool. */
        private String name;
        /** Optional model-facing description. */
        private String description;

        private Builder() {
        }

        /**
         * Sets the input name. The database validates required names.
         *
         * @param name input name
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
         *      ToolInputSample source</a>
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets optional instructions describing the input to the model.
         *
         * @param description input description, or {@code null} to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
         *      ToolInputSample source</a>
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Builds immutable input metadata without accessing the database.
         *
         * @return immutable tool input metadata
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolInputSample.html">
         *      ToolInputSample source</a>
         */
        public ToolInput build() {
            return new ToolInput(this);
        }
    }
}
