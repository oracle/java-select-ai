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
import com.oracle.database.selectai.model.agent.TeamAttributes;

/** Sample for updating multiple DBMS_CLOUD_AI_AGENT team attributes. */
public final class SetTeamAttributesSample {

    private SetTeamAttributesSample() {
    }

    /**
     * Runs the sample using the configured team process.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        TeamAttributes attributes = TeamAttributes.builder()
                .process(requiredEnv("SELECTAI_AGENT_TEAM_PROCESS"))
                .build();

        try (SelectAIAgent selectAIAgent = createClient()) {
            Team team = selectAIAgent.team(teamName);
            boolean updated = team.setAttributes(attributes);
            System.out.printf("Set attributes on team '%s' completed: %s%n", teamName, updated);
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
