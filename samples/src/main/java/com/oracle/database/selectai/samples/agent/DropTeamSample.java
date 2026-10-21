/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

/**
 * Drops an existing Select AI agent team owned by the connected database user.
 *
 * <p>This sample changes database state. Set {@code SELECTAI_DB_USER},
 * {@code SELECTAI_DB_PASSWORD}, {@code SELECTAI_JDBC_URL}, and
 * {@code SELECTAI_AGENT_TEAM_NAME}. Confirm the team name before running it.
 * Optionally set {@code SELECTAI_AGENT_TEAM_DROP_FORCE=true} to request a force
 * drop; the default is {@code false}. Granted teams owned by another database
 * user cannot be dropped through this owner-only handle. See
 * {@code samples/README.md} for build and run commands.</p>
 */
public final class DropTeamSample {

    private DropTeamSample() {
    }

    /**
     * Loads and drops the named team.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        boolean force = Boolean.parseBoolean(envOrDefault("SELECTAI_AGENT_TEAM_DROP_FORCE", "false"));
        try (SelectAIAgent client = createClient()) {
            Team team = client.team(teamName);
            System.out.printf("Drop team '%s' completed: %s%n", teamName, team.drop(force));
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

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
