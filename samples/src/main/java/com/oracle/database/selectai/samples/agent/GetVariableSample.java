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
 * Reads a runtime variable from the current database session.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_VARIABLE_NAMESPACE}, and
 * {@code SELECTAI_AGENT_VARIABLE_NAME}. Optionally set
 * {@code SELECTAI_AGENT_VARIABLE_SCOPE} to use the explicit-scope overload.
 * When it is omitted, the default-scope overload is used.</p>
 */
public final class GetVariableSample {

    private GetVariableSample() {
    }

    /**
     * Reads and prints the requested runtime variable.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String namespace = requiredEnv("SELECTAI_AGENT_VARIABLE_NAMESPACE");
        String name = requiredEnv("SELECTAI_AGENT_VARIABLE_NAME");
        String scope = optionalEnv("SELECTAI_AGENT_VARIABLE_SCOPE");
        try (SelectAIAgent client = createClient()) {
            String value = scope == null
                    ? client.getVariable(namespace, name)
                    : client.getVariable(namespace, name, scope);
            System.out.printf("Variable %s%s: %s%n", namespace,
                    scope == null ? "/" + name : "/" + name + " (" + scope + ")", value);
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

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
