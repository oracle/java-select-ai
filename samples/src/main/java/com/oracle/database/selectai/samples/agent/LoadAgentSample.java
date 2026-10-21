/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;

/**
 * Loads an existing agent and reads its current metadata and attributes.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, and {@code SELECTAI_AGENT_NAME}. The named agent
 * must already exist and be visible to the connected user. See
 * {@code samples/README.md} for build and run commands.</p>
 */
public final class LoadAgentSample {

    private LoadAgentSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String agentName = requiredEnv("SELECTAI_AGENT_NAME");
        try (SelectAIAgent client = createClient()) {
            Agent agent = client.agent(agentName);
            System.out.printf("Agent: %s%n", agent.getAgentName());
            System.out.printf("Status: %s%n", agent.getStatus());
            System.out.printf("Description: %s%n", agent.getDescription());
            AgentAttributes attributes = agent.getAgentAttributes();
            System.out.printf("Profile: %s%n", attributes.getProfileName());
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
