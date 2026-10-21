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
import com.oracle.database.selectai.model.agent.TeamDescription;

/**
 * Describes a team, retaining skills as JSON. Invalid database response JSON
 * is reported through SelectAIException and causes a nonzero process exit.
 */
public final class DescribeTeamSample {

    private DescribeTeamSample() {
    }

    /**
     * Loads an existing team and prints its database-generated definition.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            TeamDescription description = selectAIAgent.team(teamName).describe();
            if (description == null) {
                System.out.println("No team description returned.");
                return;
            }
            System.out.println("Team name: " + description.getTeamName());
            System.out.println("Description: " + description.getDescription());
            System.out.println("Version: " + description.getVersion());
            System.out.println("Skills JSON: " + description.getSkills());
        }
    }

    private static SelectAIAgent createSelectAIAgent() throws SelectAIException {
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
