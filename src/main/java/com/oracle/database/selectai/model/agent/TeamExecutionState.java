/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Execution states supported by a Select AI Agent team.
 *
 * <p>{@link #UNKNOWN} represents a state introduced by a newer database
 * release. It allows state retrieval to remain forward-compatible while
 * preventing an unknown state from being submitted by an execution-state
 * request.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GetTeamStateSample.html">
 *      GetTeamStateSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/SetTeamExecutionStateSample.html">
 *      SetTeamExecutionStateSample source</a>
 * @since 1.1.0
 */
public enum TeamExecutionState {
    /** The team workflow is running. */
    RUNNING("RUNNING"),
    /** The workflow is waiting for human input. */
    WAITING_FOR_HUMAN("WAITING_FOR_HUMAN"),
    /** The workflow completed successfully. */
    SUCCEEDED("SUCCEEDED"),
    /** The workflow failed. */
    FAILED("FAILED"),
    /** A state introduced by a newer database release. */
    UNKNOWN("UNKNOWN");

    /** Database state value. */
    private final String value;

    TeamExecutionState(String value) {
        this.value = value;
    }

    /**
     * Parses a database state value.
     *
     * @param value database state value
     * @return matching state, or {@link #UNKNOWN} for a newer value
     * @throws IllegalArgumentException when {@code value} is null or blank
     */
    public static TeamExecutionState of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("state must not be null or blank");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> RUNNING;
            case "WAITING_FOR_HUMAN" -> WAITING_FOR_HUMAN;
            case "SUCCEEDED" -> SUCCEEDED;
            case "FAILED" -> FAILED;
            default -> UNKNOWN;
        };
    }

    /**
     * Returns the database state value.
     *
     * @return canonical database state value
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Indicates whether this state is supported by this SDK release.
     *
     * @return {@code false} only for {@link #UNKNOWN}
     */
    public boolean isKnown() {
        return this != UNKNOWN;
    }
}
