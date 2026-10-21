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

import java.util.Locale;

/** Sample for updating one DBMS_CLOUD_AI_AGENT agent attribute. */
public final class SetAgentAttributeSample {

    private SetAgentAttributeSample() {
    }

    /**
     * Runs the sample using the configured database agent and selected attribute overload.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String agentName = requiredEnv("SELECTAI_AGENT_NAME");
        String attributeName = requiredEnv("SELECTAI_AGENT_ATTRIBUTE_NAME");
        String attributeValue = requiredEnv("SELECTAI_AGENT_ATTRIBUTE_VALUE");
        String attributeType = envOrDefault("SELECTAI_AGENT_ATTRIBUTE_TYPE", "string")
                .toLowerCase(Locale.ROOT);

        try (SelectAIAgent selectAIAgent = SelectAIAgent.create(DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build())) {
            Agent agent = selectAIAgent.agent(agentName);
            boolean updated = switch (attributeType) {
                case "string" -> agent.setAttribute(attributeName, attributeValue);
                case "boolean" -> agent.setAttribute(attributeName, parseBoolean(attributeValue));
                case "integer" -> agent.setAttribute(attributeName, Integer.valueOf(attributeValue));
                case "float" -> agent.setAttribute(attributeName, Float.valueOf(attributeValue));
                default -> throw new IllegalArgumentException(
                        "Unsupported agent attribute type: " + attributeType);
            };
            System.out.printf("Set attribute '%s' on agent '%s' completed: %s%n",
                    attributeName, agentName, updated);
        }
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

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Boolean attribute value must be true or false");
    }
}
