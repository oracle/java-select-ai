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
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.TeamExecutionState;

/**
 * Updates a team execution state using a database conversation identifier.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_TEAM_NAME}, and
 * {@code SELECTAI_AGENT_TEAM_CONVERSATION_ID}. Optionally set
 * {@code SELECTAI_AGENT_TEAM_EXECUTION_STATE}; it defaults to
 * {@code SUCCEEDED}. The database validates whether the requested state is
 * valid for the targeted run.</p>
 */
public final class SetTeamExecutionStateSample {

    private SetTeamExecutionStateSample() {
    }

    /**
     * Updates and prints the result of the team state operation.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        String conversationId = requiredEnv("SELECTAI_AGENT_TEAM_CONVERSATION_ID");
        TeamExecutionState state = TeamExecutionState.of(
                envOrDefault("SELECTAI_AGENT_TEAM_EXECUTION_STATE", "SUCCEEDED"));
        try (SelectAIAgent client = createClient()) {
            boolean updated = client.setTeamExecutionState(teamName,
                    ExecutionStateRequest.builder(state)
                            .conversationId(conversationId)
                            .build());
            System.out.printf("Updated team %s execution state for conversation %s: %s%n",
                    teamName, conversationId, updated);
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
