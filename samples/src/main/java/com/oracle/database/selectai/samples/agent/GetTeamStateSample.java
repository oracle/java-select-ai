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
import com.oracle.database.selectai.model.agent.TeamExecutionState;

/**
 * Reads a team execution state using a database conversation identifier.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_TEAM_NAME}, and
 * {@code SELECTAI_AGENT_TEAM_CONVERSATION_ID}. The team name may be
 * owner-qualified for a shared team. The conversation must already exist in
 * the database. The conversation-ID convenience method is used so the SDK
 * always sends an explicit {@code conversation_id}.</p>
 */
public final class GetTeamStateSample {

    private GetTeamStateSample() {
    }

    /**
     * Reads and prints the current state for a team conversation.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        String conversationId = requiredEnv("SELECTAI_AGENT_TEAM_CONVERSATION_ID");
        try (SelectAIAgent client = createClient()) {
            TeamExecutionState state = client.getTeamStateByConversationId(teamName, conversationId);
            System.out.printf("Team %s state for conversation %s: %s%n",
                    teamName, conversationId, state);
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
