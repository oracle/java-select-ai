/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

/**
 * Read-only discovery metadata for an agent team available to the current
 * database user.
 * <p>
 * An available team may be owned by another schema and therefore cannot
 * necessarily be opened as a mutable {@link com.oracle.database.selectai.agent.Team}
 * handle. Use its team name, including the owner qualification when returned
 * by the database, with
 * {@link com.oracle.database.selectai.agent.SelectAIAgent#runTeam(String,
 * com.oracle.database.selectai.model.agent.RunTeamRequest)}.
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListAvailableTeamsSample.html">
 *      ListAvailableTeamsSample source</a>
 * @since 1.1.0
 */
public final class AvailableTeam {
    /** Team name returned by the database discovery operation. */
    private final String teamName;
    /** Team description returned by the database, when available. */
    private final String description;
    /** Original JSON object retained for forward-compatible fields. */
    private final String rawJson;

    /**
     * Creates a discovery snapshot.
     *
     * @param teamName team name returned by the database
     * @param description team description, if provided
     * @param rawJson original JSON object returned by the database, if provided
     * @throws IllegalArgumentException when {@code teamName} is null or blank
     */
    public AvailableTeam(String teamName, String description, String rawJson) {
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("teamName must not be null or blank");
        }
        this.teamName = teamName;
        this.description = description;
        this.rawJson = rawJson;
    }

    /**
     * Returns the team name reported by the database.
     *
     * @return team name
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * Returns the team description reported by the database.
     *
     * @return description, or {@code null} when none was returned
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the original JSON object for forward-compatible access to
     * discovery fields introduced by a newer database release.
     *
     * @return raw JSON object, or {@code null} when no JSON representation was retained
     */
    public String getRawJson() {
        return rawJson;
    }
}
