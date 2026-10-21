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
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TeamRunResult;

/**
 * Runs an existing Select AI Agent team.
 *
 * <p>Set {@code SELECTAI_AGENT_TEAM_NAME},
 * {@code SELECTAI_AGENT_TEAM_CONVERSATION_ID}, and the common database
 * connection variables before running this sample. The conversation ID must
 * identify an existing database conversation created with
 * {@link com.oracle.database.selectai.Conversation#create()}, for example by
 * {@code CreateConversationSample}; an arbitrary UUID is not sufficient.
 * Optionally set {@code SELECTAI_AGENT_TEAM_PROMPT}. Reuse the same ID when
 * responding to a team run waiting for human input, including when the next
 * call uses a different database connection.</p>
 */
public final class RunTeamSample {

    private RunTeamSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        String conversationId = requiredEnv("SELECTAI_AGENT_TEAM_CONVERSATION_ID");
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            Team team = selectAIAgent.team(teamName);
            RunTeamRequest request = RunTeamRequest.builder()
                    .userPrompt(envOrDefault("SELECTAI_AGENT_TEAM_PROMPT",
                            "Summarize the current team capability."))
                    .conversationId(conversationId)
                    .build();
            TeamRunResult result = team.run(request);
            System.out.println(result.getRawValue());
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

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

}
