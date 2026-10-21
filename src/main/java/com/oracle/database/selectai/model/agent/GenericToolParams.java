/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable parameters for tools or parameter schemas not modeled by this SDK.
 *
 * <p>Use this model for new database parameters or a parameter-free tool such
 * as {@link ToolType#HUMAN}. Nested maps and lists are defensively copied and
 * immutable. Parameter names and values are validated by the database.</p>
 *
 * @see ToolParams#fromMap(ToolType, Map)
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
 *      ToolParamsSample source</a>
 * @since 1.1.0
 */
public final class GenericToolParams implements ToolParams {
    /** Immutable database parameters, including unknown fields. */
    private final Map<String, Object> values;

    GenericToolParams(Map<String, ?> values) {
        this.values = ToolParamsSupport.snapshot(values);
    }

    /**
     * Creates a generic parameter builder.
     *
     * @return new mutable builder
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Snapshots a database parameter object without selecting a typed model.
     *
     * @param values JSON-compatible parameters; must not be null
     * @return immutable parameters
     * @throws IllegalArgumentException when values is null or not JSON-compatible
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public static GenericToolParams fromMap(Map<String, ?> values) {
        return new GenericToolParams(values);
    }

    /**
     * {@inheritDoc}
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    @Override
    public Map<String, Object> toMap() {
        return values;
    }

    /**
     * Builder for {@link GenericToolParams}.
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public static final class Builder {
        /** Parameters assembled by this builder. */
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Snapshots a database parameter, replacing a previous value of that name.
         *
         * @param name database JSON parameter name
         * @param value JSON-compatible value, including null
         * @return this builder
         * @throws IllegalArgumentException when name is null or blank, or value
         *         is not JSON-compatible
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
         *      ToolParamsSample source</a>
         */
        public Builder parameter(String name, Object value) {
            ToolParamsSupport.additionalParameter(values, Set.of(), name, value);
            return this;
        }

        /**
         * Builds an immutable parameter snapshot.
         *
         * @return immutable parameters
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
         *      ToolParamsSample source</a>
         */
        public GenericToolParams build() {
            return new GenericToolParams(values);
        }
    }
}
