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

/**
 * Sets a runtime variable and optionally clears it from the current database
 * session.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_VARIABLE_NAMESPACE},
 * {@code SELECTAI_AGENT_VARIABLE_NAME}, and
 * {@code SELECTAI_AGENT_VARIABLE_VALUE}. Optionally set
 * {@code SELECTAI_AGENT_VARIABLE_SCOPE} to use the explicit-scope overload.
 * When it is omitted, the default-scope overload is used.
 * Set {@code SELECTAI_AGENT_VARIABLE_CLEAR=true} to clear the variable after
 * writing it.</p>
 */
public final class SetVariableSample {

    private SetVariableSample() {
    }

    /**
     * Sets, reads, and optionally clears the requested runtime variable.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String namespace = requiredEnv("SELECTAI_AGENT_VARIABLE_NAMESPACE");
        String name = requiredEnv("SELECTAI_AGENT_VARIABLE_NAME");
        String value = requiredEnv("SELECTAI_AGENT_VARIABLE_VALUE");
        String scope = optionalEnv("SELECTAI_AGENT_VARIABLE_SCOPE");
        boolean clear = Boolean.parseBoolean(envOrDefault("SELECTAI_AGENT_VARIABLE_CLEAR", "false"));
        try (SelectAIAgent client = createClient()) {
            if (scope == null) {
                client.setVariable(namespace, name, value);
            } else {
                client.setVariable(namespace, name, value, scope);
            }
            System.out.printf("Set variable %s%s.%n", namespace,
                    scope == null ? "/" + name : "/" + name + " (" + scope + ")");
            String storedValue = scope == null
                    ? client.getVariable(namespace, name)
                    : client.getVariable(namespace, name, scope);
            System.out.printf("Stored value: %s%n", storedValue);
            if (clear) {
                if (scope == null) {
                    client.setVariable(namespace, name, null);
                } else {
                    client.setVariable(namespace, name, null, scope);
                }
                System.out.printf("Cleared variable %s%s.%n", namespace,
                        scope == null ? "/" + name : "/" + name + " (" + scope + ")");
            }
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

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
