/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

import java.util.Locale;

/** Sample for updating one DBMS_CLOUD_AI_AGENT task attribute. */
public final class SetTaskAttributeSample {

    private SetTaskAttributeSample() {
    }

    /**
     * Runs the sample using the configured database task and selected attribute overload.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String taskName = requiredEnv("SELECTAI_AGENT_TASK_NAME");
        String attributeName = requiredEnv("SELECTAI_AGENT_ATTRIBUTE_NAME");
        String attributeValue = requiredEnv("SELECTAI_AGENT_ATTRIBUTE_VALUE");
        String attributeType = envOrDefault("SELECTAI_AGENT_ATTRIBUTE_TYPE", "string")
                .toLowerCase(Locale.ROOT);

        try (SelectAIAgent selectAIAgent = createClient()) {
            Task task = selectAIAgent.task(taskName);
            boolean updated = switch (attributeType) {
                case "string" -> task.setAttribute(attributeName, attributeValue);
                case "boolean" -> task.setAttribute(attributeName, parseBoolean(attributeValue));
                case "integer" -> task.setAttribute(attributeName, Integer.valueOf(attributeValue));
                case "float" -> task.setAttribute(attributeName, Float.valueOf(attributeValue));
                default -> throw new IllegalArgumentException(
                        "Unsupported task attribute type: " + attributeType);
            };
            System.out.printf("Set attribute '%s' on task '%s' completed: %s%n",
                    attributeName, taskName, updated);
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
