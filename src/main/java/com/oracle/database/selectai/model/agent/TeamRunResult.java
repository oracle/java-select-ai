/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

/**
 * Result returned by a Select AI Agent team run.
 *
 * <p>The database may return a final answer, status information, or a
 * database-defined JSON document. This model preserves the returned value
 * without imposing a database-version-specific schema.</p>
 *
 * <p>This class inherits {@link Object#toString()} so ordinary object logging
 * does not include response content. Use {@link #getRawValue()} explicitly
 * when the response is needed, and review it before logging.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunTeamSample.html">
 *      RunTeamSample source</a>
 * @since 1.1.0
 */
public final class TeamRunResult {
    /** Exact value returned by the database team run. */
    private final String rawValue;

    /**
     * Creates a team-run result.
     *
     * @param rawValue value returned by the database, possibly {@code null}
     */
    public TeamRunResult(String rawValue) {
        this.rawValue = rawValue;
    }

    /**
     * Creates a result from a database value.
     *
     * @param rawValue value returned by the database
     * @return result, or {@code null} when the database returned no value
     */
    public static TeamRunResult fromRawValue(String rawValue) {
        return rawValue == null ? null : new TeamRunResult(rawValue);
    }

    /**
     * Returns the exact value returned by the database.
     *
     * @return final answer, status text, or JSON returned by the database
     */
    public String getRawValue() {
        return rawValue;
    }
}
