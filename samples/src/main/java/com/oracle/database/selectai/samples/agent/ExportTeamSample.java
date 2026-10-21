/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

/** Sample for exporting a DBMS_CLOUD_AI_AGENT team definition. */
public final class ExportTeamSample {

    private ExportTeamSample() {
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
            Team team = selectAIAgent.team(teamName);
            String location = env("SELECTAI_AGENT_TEAM_EXPORT_LOCATION");
            if (location == null) {
                ExportTeamParams params = ExportTeamParams.builder()
                        .overwrite(optionalBoolean("SELECTAI_AGENT_TEAM_EXPORT_OVERWRITE"))
                        .build();
                System.out.println(team.exportDefinition(params));
                return;
            }
            ExportTeamRequest request = ExportTeamRequest.builder(location)
                    .objectStorageCredentialName(env("SELECTAI_AGENT_OBJECT_STORAGE_CREDENTIAL_NAME"))
                    .overwrite(optionalBoolean("SELECTAI_AGENT_TEAM_EXPORT_OVERWRITE"))
                    .build();
            boolean exported = team.exportDefinition(request);
            System.out.printf("Export team '%s' completed: %s%n", teamName, exported);
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
