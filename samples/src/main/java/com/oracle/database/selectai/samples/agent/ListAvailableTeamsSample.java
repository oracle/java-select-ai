/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AvailableTeam;

import java.util.List;

/**
 * Discovers teams available to the connected database user.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD}, and
 * {@code SELECTAI_JDBC_URL} before running this sample. The database package
 * may return teams owned by another schema when access has been granted. The
 * returned {@link AvailableTeam} values are read-only discovery snapshots;
 * use an owner-qualified team name with
 * {@link SelectAIAgent#runTeam(String,
 * com.oracle.database.selectai.model.agent.RunTeamRequest)} to run a shared
 * team.</p>
 */
public final class ListAvailableTeamsSample {

    private ListAvailableTeamsSample() {
    }

    /**
     * Lists and prints teams available to the current database user.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        try (SelectAIAgent client = createClient()) {
            List<AvailableTeam> teams = client.listAvailableTeams();
            System.out.printf("Found %d available team(s).%n", teams.size());
            for (AvailableTeam team : teams) {
                System.out.printf("Team: %s; description: %s%n",
                        team.getTeamName(), team.getDescription());
                System.out.printf("Raw discovery JSON: %s%n", team.getRawJson());
            }
        }
    }

    private static SelectAIAgent createClient() throws SelectAIException {
        return SelectAIAgent.create(DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
