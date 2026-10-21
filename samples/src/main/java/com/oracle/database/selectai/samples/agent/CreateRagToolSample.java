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
import com.oracle.database.selectai.model.agent.RagToolParams;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolType;

/**
 * Creates a persistent RAG tool using typed {@link RagToolParams}.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_TOOL_NAME}, and
 * {@code SELECTAI_PROFILE_NAME}.
 * The named Select AI profile must already exist and be configured for RAG with its required vector index.</p>
 *
 * <p>Optionally set {@code SELECTAI_AGENT_TOOL_INSTRUCTION} and
 * {@code SELECTAI_AGENT_TOOL_DESCRIPTION}. This sample creates a tool but
 * does not run it or invoke its external service. Closing the client does not
 * drop the tool. Run {@link DropToolSample} with the same tool name and database
 * connection settings to remove it. Use a new tool name to avoid replacing
 * an existing resource.</p>
 *
 * @see ToolParamsSample
 */
public final class CreateRagToolSample {

    private CreateRagToolSample() {
    }

    /**
     * Creates the configured tool without executing it.
     *
     * @param args unused command-line arguments
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String toolName = requiredEnv("SELECTAI_AGENT_TOOL_NAME");
        RagToolParams params = RagToolParams.builder()
                .profileName(requiredEnv("SELECTAI_PROFILE_NAME"))
                .build();
        ToolAttributes attributes = ToolAttributes.builder()
                .toolType(ToolType.RAG)
                .toolParams(params)
                .instruction(env("SELECTAI_AGENT_TOOL_INSTRUCTION"))
                .build();

        try (SelectAIAgent client = createClient()) {
            Tool tool = client.tool(toolName, attributes,
                    env("SELECTAI_AGENT_TOOL_DESCRIPTION"), ToolStatus.ENABLED);
            System.out.printf("Create tool '%s' completed: %s%n", toolName, tool.create());
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
        String value = env(name);
        if (value == null) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
