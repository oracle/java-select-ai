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
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TeamRunResult;

/**
 * Runs a team granted to the current database user by another team owner.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD}, and
 * {@code SELECTAI_JDBC_URL} for the consumer account. Set
 * {@code SELECTAI_AGENT_SHARED_TEAM_NAME} to an owner-qualified name such as
 * {@code TEAM_OWNER.SHARED_SUPPORT_TEAM}, and set
 * {@code SELECTAI_AGENT_TEAM_PROMPT} to a prompt appropriate for that team.
 * The team owner must already have granted the consumer run access using
 * {@code DBMS_CLOUD_AI_AGENT.GRANT_TEAM_ACCESS}. The consumer also needs
 * privileges and access to resources used by the workflow.</p>
 *
 * <p>Set {@code SELECTAI_AGENT_TEAM_CONVERSATION_ID} to the ID of an existing
 * database conversation created with
 * {@link com.oracle.database.selectai.Conversation#create()}; an arbitrary
 * UUID is not sufficient. Reuse it when responding to a team run waiting for
 * human input, even when the next call uses another connection.
 * This example runs the team directly without loading a {@code Team} handle
 * from the owner's metadata view.</p>
 */
public final class RunSharedTeamSample {

    private RunSharedTeamSample() {
    }

    /**
     * Runs a granted team and prints its returned result.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_SHARED_TEAM_NAME");
        RunTeamRequest request = RunTeamRequest.builder()
                .userPrompt(requiredEnv("SELECTAI_AGENT_TEAM_PROMPT"))
                .conversationId(requiredEnv("SELECTAI_AGENT_TEAM_CONVERSATION_ID"))
                .build();
        try (SelectAIAgent client = SelectAIAgent.create(DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build())) {
            TeamRunResult result = client.runTeam(teamName, request);
            System.out.println(result.getRawValue());
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

}
