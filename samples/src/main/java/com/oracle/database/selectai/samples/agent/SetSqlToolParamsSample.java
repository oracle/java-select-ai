/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolType;

/**
 * Updates an existing SQL tool's profile through typed tool parameters.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_TOOL_NAME}, and
 * {@code SELECTAI_PROFILE_NAME}. Both the SQL tool and the new profile must
 * already exist. This sample changes the stored tool configuration; it does
 * not run the tool or restore its previous parameters.</p>
 *
 * <p>The submitted {@code tool_params} contains only {@code profile_name}.
 * Do not assume the database merges nested parameter objects: include any
 * other parameters that must remain configured for your tool.</p>
 *
 * @see CreateSqlToolSample
 * @see LoadToolSample
 */
public final class SetSqlToolParamsSample {

    private SetSqlToolParamsSample() {
    }

    /**
     * Changes the configured profile without executing the SQL tool.
     *
     * @param args unused command-line arguments
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String toolName = requiredEnv("SELECTAI_AGENT_TOOL_NAME");
        SqlToolParams params = SqlToolParams.builder()
                .profileName(requiredEnv("SELECTAI_PROFILE_NAME"))
                .build();
        try (SelectAIAgent client = createClient()) {
            Tool tool = client.tool(toolName);
            if (!ToolType.SQL.equals(tool.getToolAttributes().getToolType())) {
                throw new IllegalArgumentException("The selected tool must have type SQL");
            }
            boolean updated = tool.setAttributes(ToolAttributes.builder().toolParams(params).build());
            System.out.printf("Set parameters on SQL tool '%s' completed: %s%n", toolName, updated);
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
}
