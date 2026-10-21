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

/**
 * Drops an existing Select AI agent owned by the connected database user.
 *
 * <p>This sample changes database state. Set {@code SELECTAI_DB_USER},
 * {@code SELECTAI_DB_PASSWORD}, {@code SELECTAI_JDBC_URL}, and
 * {@code SELECTAI_AGENT_NAME}. Confirm the agent name before running it.
 * Optionally set {@code SELECTAI_AGENT_DROP_FORCE=true} to request a force
 * drop; the default is {@code false}. See {@code samples/README.md} for build
 * and run commands.</p>
 */
public final class DropAgentSample {

    private DropAgentSample() {
    }

    /**
     * Loads and drops the named agent.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String agentName = requiredEnv("SELECTAI_AGENT_NAME");
        boolean force = Boolean.parseBoolean(envOrDefault("SELECTAI_AGENT_DROP_FORCE", "false"));
        try (SelectAIAgent client = createClient()) {
            Agent agent = client.agent(agentName);
            System.out.printf("Drop agent '%s' completed: %s%n", agentName, agent.drop(force));
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
