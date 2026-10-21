/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

/** Sample for importing a DBMS_CLOUD_AI_AGENT team definition. */
public final class ImportTeamSample {

    private ImportTeamSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            ImportTeamRequest request = ImportTeamRequest.builder(requiredEnv("SELECTAI_PROFILE_NAME"), teamName)
                    .specification(env("SELECTAI_AGENT_TEAM_SPECIFICATION_JSON"))
                    .location(env("SELECTAI_AGENT_TEAM_IMPORT_LOCATION"))
                    .objectStorageCredentialName(env("SELECTAI_AGENT_OBJECT_STORAGE_CREDENTIAL_NAME"))
                    .force(optionalBoolean("SELECTAI_AGENT_TEAM_IMPORT_FORCE"))
                    .build();
            boolean imported = selectAIAgent.importTeam(request);
            System.out.printf("Import team '%s' completed: %s%n", teamName, imported);
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

    private static Boolean optionalBoolean(String name) {
        String value = env(name);
        return value == null ? null : Boolean.valueOf(value);
    }
}
