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
import com.oracle.database.selectai.model.agent.ToolAttributes;

/**
 * Sample for updating multiple DBMS_CLOUD_AI_AGENT tool attributes.
 *
 * <p>For a typed {@code tool_params} update on an existing SQL tool, see
 * {@link SetSqlToolParamsSample}.</p>
 * <p>For typed input names and descriptions, see {@link SetToolInputsSample}.</p>
 */
public final class SetToolAttributesSample {

    private SetToolAttributesSample() {
    }

    /**
     * Runs the sample using the configured tool type.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String toolName = requiredEnv("SELECTAI_AGENT_TOOL_NAME");
        ToolAttributes attributes = ToolAttributes.builder()
                .toolTypeValue(requiredEnv("SELECTAI_AGENT_TOOL_TYPE"))
                .build();

        try (SelectAIAgent selectAIAgent = createClient()) {
            Tool tool = selectAIAgent.tool(toolName);
            boolean updated = tool.setAttributes(attributes);
            System.out.printf("Set attributes on tool '%s' completed: %s%n", toolName, updated);
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
