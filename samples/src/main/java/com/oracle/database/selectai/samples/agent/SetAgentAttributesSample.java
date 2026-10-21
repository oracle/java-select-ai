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

/** Sample for updating multiple DBMS_CLOUD_AI_AGENT attributes. */
public final class SetAgentAttributesSample {

    private SetAgentAttributesSample() {
    }

    /**
     * Runs the sample using required profile and role attributes.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String agentName = requiredEnv("SELECTAI_AGENT_NAME");
        AgentAttributes attributes = AgentAttributes.builder()
                .profileName(requiredEnv("SELECTAI_PROFILE_NAME"))
                .role(requiredEnv("SELECTAI_AGENT_ROLE"))
                .enableHumanTool(optionalBoolean("SELECTAI_AGENT_ENABLE_HUMAN_TOOL"))
                .shortTermMemoryLength(optionalInteger("SELECTAI_AGENT_SHORT_TERM_MEMORY_LENGTH"))
                .supervisor(optionalBoolean("SELECTAI_AGENT_SUPERVISOR"))
                .build();

        try (SelectAIAgent selectAIAgent = SelectAIAgent.create(DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build())) {
            Agent agent = selectAIAgent.agent(agentName);
            boolean updated = agent.setAttributes(attributes);
            System.out.printf("Set attributes on agent '%s' completed: %s%n", agentName, updated);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static Boolean optionalBoolean(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Boolean.valueOf(value);
    }

    private static Integer optionalInteger(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }
}
