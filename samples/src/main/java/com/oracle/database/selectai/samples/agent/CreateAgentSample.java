/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

import java.util.Arrays;
import java.util.List;

/** Sample for creating a DBMS_CLOUD_AI_AGENT agent. */
public final class CreateAgentSample {

    private CreateAgentSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String agentName = requiredEnv("SELECTAI_AGENT_NAME");
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            AgentAttributes attributes = AgentAttributes.builder()
                    .profileName(requiredEnv("SELECTAI_PROFILE_NAME"))
                    .role(requiredEnv("SELECTAI_AGENT_ROLE"))
                    .tools(optionalList("SELECTAI_AGENT_TOOLS"))
                    .enableHumanTool(optionalBoolean("SELECTAI_AGENT_ENABLE_HUMAN_TOOL"))
                    .shortTermMemoryLength(optionalInteger("SELECTAI_AGENT_SHORT_TERM_MEMORY_LENGTH"))
                    .supervisor(optionalBoolean("SELECTAI_AGENT_SUPERVISOR"))
                    .build();
            Agent agent = selectAIAgent.agent(agentName, attributes,
                    env("SELECTAI_AGENT_DESCRIPTION"), status("SELECTAI_AGENT_STATUS"));

            boolean created = agent.create();
            System.out.printf("Create agent '%s' completed: %s%n", agent.getAgentName(), created);
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

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> optionalList(String name) {
        String value = env(name);
        return value == null ? null : Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static Boolean optionalBoolean(String name) {
        String value = env(name);
        return value == null ? null : Boolean.valueOf(value);
    }

    private static Integer optionalInteger(String name) {
        String value = env(name);
        return value == null ? null : Integer.valueOf(value);
    }

    private static AgentStatus status(String name) {
        String value = env(name);
        return value == null ? null : AgentStatus.fromValue(value);
    }
}
