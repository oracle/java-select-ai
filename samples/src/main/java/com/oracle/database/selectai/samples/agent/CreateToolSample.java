/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolParams;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolType;

import java.util.List;
import java.util.Map;

/**
 * Sample for creating a DBMS_CLOUD_AI_AGENT tool.
 *
 * <p>Set {@code SELECTAI_AGENT_TOOL_PARAMS_JSON} to a JSON object when the
 * selected built-in tool requires parameters. For example, a SQL tool needs
 * {@code {"profile_name":"MY_PROFILE"}}. The SDK maps the supplied object to
 * a typed {@link ToolParams} model. For builder-based configuration without
 * JSON, see {@link ToolParamsSample}. Database-backed typed creation examples
 * are {@link CreateSqlToolSample}, {@link CreateRagToolSample},
 * {@link CreateWebSearchToolSample},
 * {@link CreateSlackNotificationToolSample}, and
 * {@link CreateEmailNotificationToolSample}.</p>
 *
 * <p>For a tool with input metadata, set {@code SELECTAI_AGENT_TOOL_INPUT_NAME}
 * and optionally {@code SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION}. This adds one
 * typed {@link ToolInput}; the database validates it against the configured
 * tool. See {@link ToolInputSample} for an offline example and
 * {@link CreateToolWithInputsSample} for a focused custom-function example. Creation persists
 * the tool until it is dropped; use {@link DropToolSample} with the same tool
 * name and connection settings for cleanup.</p>
 */
public final class CreateToolSample {

    private CreateToolSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String toolName = requiredEnv("SELECTAI_AGENT_TOOL_NAME");
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            ToolAttributes.Builder builder = ToolAttributes.builder()
                    .instruction(env("SELECTAI_AGENT_TOOL_INSTRUCTION"))
                    .function(env("SELECTAI_AGENT_TOOL_FUNCTION"));
            String toolType = env("SELECTAI_AGENT_TOOL_TYPE");
            if (toolType != null) {
                builder.toolTypeValue(toolType);
            }
            String toolParamsJson = env("SELECTAI_AGENT_TOOL_PARAMS_JSON");
            if (toolParamsJson != null) {
                builder.toolParams(parseToolParams(toolType, toolParamsJson));
            }
            String inputName = env("SELECTAI_AGENT_TOOL_INPUT_NAME");
            if (inputName != null) {
                builder.toolInputs(List.of(ToolInput.builder()
                        .name(inputName)
                        .description(env("SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION"))
                        .build()));
            }
            Tool tool = selectAIAgent.tool(toolName, builder.build(),
                    env("SELECTAI_AGENT_TOOL_DESCRIPTION"), status("SELECTAI_AGENT_TOOL_STATUS"));

            boolean created = tool.create();
            System.out.printf("Create tool '%s' completed: %s%n", tool.getToolName(), created);
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

    private static ToolParams parseToolParams(String toolType, String json) {
        try {
            Map<String, Object> values = new ObjectMapper()
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(json, new TypeReference<Map<String, Object>>() { });
            if (values == null) {
                throw new IllegalArgumentException("SELECTAI_AGENT_TOOL_PARAMS_JSON must be a JSON object");
            }
            return ToolParams.fromMap(toolType == null ? null : ToolType.of(toolType), values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("SELECTAI_AGENT_TOOL_PARAMS_JSON must be a valid JSON object", e);
        }
    }

    private static ToolStatus status(String name) {
        String value = env(name);
        return value == null ? null : ToolStatus.fromValue(value);
    }
}
