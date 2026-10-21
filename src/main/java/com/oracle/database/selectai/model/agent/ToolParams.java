/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * Configuration parameters for a built-in Select AI Agent tool.
 *
 * <p>Use the immutable, tool-specific builders for known parameters or
 * {@link GenericToolParams} for database-defined parameters. These configure
 * a tool; they are not the runtime input passed to
 * {@link com.oracle.database.selectai.agent.Tool#run(String)}. The database
 * validates required fields and tool-specific rules, including partial updates.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
 *      ToolParamsSample source</a>
 * @since 1.1.0
 */
public interface ToolParams {
    /**
     * Returns parameters using database JSON field names.
     *
     * <p>SDK implementations return immutable maps, including nested values.
     * Additional parameters appear alongside known parameters, without an
     * extra wrapper property. Implementations supplied by callers are
     * snapshotted by {@link ToolAttributes.Builder#toolParams(ToolParams)}.</p>
     *
     * @return parameter map; never null
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/LoadToolSample.html">
     *      LoadToolSample source</a>
     */
    @JsonValue
    Map<String, Object> toMap();

    /**
     * Snapshots parameters and selects a model for the supplied tool type.
     *
     * <p>Unknown fields are retained. Unknown types, notification variants,
     * or changed types of known fields use {@link GenericToolParams}. Missing
     * required creation fields do not prevent loading existing metadata.</p>
     *
     * @param toolType tool type, or null when unspecified
     * @param values JSON-compatible parameter object, or null when absent
     * @return immutable parameters, or null when values is null
     * @throws IllegalArgumentException when values cannot be represented as JSON
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateToolSample.html">
     *      CreateToolSample source</a>
     */
    static ToolParams fromMap(ToolType toolType, Map<String, ?> values) {
        return ToolParamsSupport.fromMap(toolType, values);
    }
}
