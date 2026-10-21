/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

/**
 * Result returned by a Select AI Agent tool run.
 *
 * <p>Tool output is defined by the configured tool and may be text or JSON.
 * This model therefore preserves the database value without imposing a
 * tool-specific schema.</p>
 *
 * <p>This class inherits {@link Object#toString()} so ordinary object logging
 * does not include response content. Use {@link #getRawValue()} explicitly
 * when the response is needed, and review it before logging.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunToolSample.html">
 *      RunToolSample source</a>
 * @since 1.1.0
 */
public final class ToolRunResult {
    /** Exact value returned by {@code DBMS_CLOUD_AI_AGENT.RUN_TOOL}. */
    private final String rawValue;

    /**
     * Creates a tool-run result.
     *
     * @param rawValue value returned by the database, possibly {@code null}
     */
    public ToolRunResult(String rawValue) {
        this.rawValue = rawValue;
    }

    /**
     * Creates a result from a database value.
     *
     * @param rawValue value returned by the database
     * @return result, or {@code null} when the database returned no value
     */
    public static ToolRunResult fromRawValue(String rawValue) {
        return rawValue == null ? null : new ToolRunResult(rawValue);
    }

    /**
     * Returns the exact value returned by the database without parsing or modification.
     *
     * @return tool output, or {@code null} when the database returned no value
     */
    public String getRawValue() {
        return rawValue;
    }
}
