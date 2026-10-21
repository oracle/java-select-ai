/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

import java.util.Locale;

/** Sample for getting a canonical DBMS_CLOUD_AI_AGENT object definition. */
public final class GetDefinitionSample {

    private GetDefinitionSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String objectName = requiredEnv("SELECTAI_AGENT_OBJECT_NAME");
        AgentObjectType objectType = AgentObjectType.valueOf(requiredEnv("SELECTAI_AGENT_OBJECT_TYPE")
                .trim().toUpperCase(Locale.ROOT));
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            boolean includeDependentObjects = Boolean.parseBoolean(
                    envOrDefault("SELECTAI_AGENT_INCLUDE_DEPENDENT_OBJECTS", "false"));
            System.out.println(selectAIAgent.getDefinition(objectType, objectName, includeDependentObjects));
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
