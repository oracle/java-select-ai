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

/** Demonstrates revoking run access from an owner-managed team through the client API. */
public final class RevokeTeamAccessSample {

    private RevokeTeamAccessSample() {
    }

    /**
     * Revokes the configured database user or role permission to run a team.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_SHARED_TEAM_NAME");
        String grantee = requiredEnv("SELECTAI_AGENT_SHARED_TEAM_GRANTEE");
        try (SelectAIAgent client = createClient()) {
            boolean revoked = client.revokeTeamAccess(teamName, grantee);
            System.out.printf("Team access revoke completed: %s%n", revoked);
            System.out.printf("Team: %s%n", teamName);
            System.out.printf("Grantee: %s%n", grantee);
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
