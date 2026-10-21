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
import com.oracle.database.selectai.model.agent.ToolInput;

import java.util.List;

/**
 * Updates an existing tool's input metadata with a typed {@link ToolInput}.
 * <p>
 * Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_TOOL_NAME}, and
 * {@code SELECTAI_AGENT_TOOL_INPUT_NAME}. Optionally set
 * {@code SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION}. Use a tool whose configured
 * function accepts the named input. The database validates compatibility.
 * <p>
 * This sample submits a one-entry {@code tool_inputs} array. Include all
 * entries that must remain configured; do not assume that the database merges
 * array entries. The update is persistent. The sample does not run the tool,
 * create resources, or restore the previous input metadata.
 *
 * @see ToolInputSample
 * @see CreateToolSample
 * @see CreateToolWithInputsSample
 * @see LoadToolSample
 */
public final class SetToolInputsSample {

    private SetToolInputsSample() {
    }

    /**
     * Updates input metadata without invoking the tool's function.
     *
     * @param args unused command-line arguments
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String toolName = requiredEnv("SELECTAI_AGENT_TOOL_NAME");
        ToolInput input = ToolInput.builder()
                .name(requiredEnv("SELECTAI_AGENT_TOOL_INPUT_NAME"))
                .description(env("SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION"))
                .build();
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();
        try (SelectAIAgent client = SelectAIAgent.create(config)) {
            Tool tool = client.tool(toolName);
            boolean updated = tool.setAttributes(ToolAttributes.builder()
                    .toolInputs(List.of(input))
                    .build());
            System.out.printf("Set inputs on tool '%s' completed: %s%n", toolName, updated);
        }
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
