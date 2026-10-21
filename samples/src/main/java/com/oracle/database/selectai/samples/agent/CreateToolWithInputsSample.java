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
import com.oracle.database.selectai.model.agent.ToolStatus;

import java.util.List;

/**
 * Creates a persistent custom tool with typed input names and descriptions.
 * <p>
 * Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, {@code SELECTAI_AGENT_TOOL_NAME},
 * {@code SELECTAI_AGENT_TOOL_FUNCTION}, {@code SELECTAI_AGENT_TOOL_INSTRUCTION},
 * and {@code SELECTAI_AGENT_TOOL_INPUT_NAME}. Optionally set
 * {@code SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION} and
 * {@code SELECTAI_AGENT_TOOL_DESCRIPTION}.
 * <p>
 * The named PL/SQL function must already exist, be accessible to the database
 * user, and accept the named input. The database validates the tool definition.
 * This sample does not create or execute the function and does not use
 * {@code SELECTAI_AGENT_TOOL_TYPE} or {@code SELECTAI_AGENT_TOOL_PARAMS_JSON}.
 * Add further {@link ToolInput} entries to the list when the function has
 * additional inputs that need metadata.
 * <p>
 * Use a new tool name. Closing the client does not drop the tool. Run
 * {@link DropToolSample} with the same tool name and connection settings to
 * remove the created tool; the existing function is not removed.
 *
 * @see ToolInputSample
 * @see SetToolInputsSample
 * @see LoadToolSample
 */
public final class CreateToolWithInputsSample {

    private CreateToolWithInputsSample() {
    }

    /**
     * Creates input metadata without invoking the tool's function.
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
        ToolAttributes attributes = ToolAttributes.builder()
                .function(requiredEnv("SELECTAI_AGENT_TOOL_FUNCTION"))
                .instruction(requiredEnv("SELECTAI_AGENT_TOOL_INSTRUCTION"))
                .toolInputs(List.of(input))
                .build();
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();

        try (SelectAIAgent client = SelectAIAgent.create(config)) {
            Tool tool = client.tool(toolName, attributes,
                    env("SELECTAI_AGENT_TOOL_DESCRIPTION"), ToolStatus.ENABLED);
            System.out.printf("Create tool '%s' completed: %s%n", toolName, tool.create());
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
