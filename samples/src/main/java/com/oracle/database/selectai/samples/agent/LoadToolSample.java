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
import com.oracle.database.selectai.model.agent.EmailNotificationToolParams;
import com.oracle.database.selectai.model.agent.RagToolParams;
import com.oracle.database.selectai.model.agent.SlackNotificationToolParams;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolParams;
import com.oracle.database.selectai.model.agent.WebSearchToolParams;

/**
 * Loads an existing agent tool and reads its current metadata and attributes.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, and {@code SELECTAI_AGENT_TOOL_NAME}. The named
 * tool must already exist and be visible to the connected user. See
 * {@code samples/README.md} for build and run commands.</p>
 *
 * <p>Known built-in parameters are read through their typed getters. Unknown
 * parameter models are inspected through {@link ToolParams#toMap()} without
 * printing arbitrary values, which may contain sensitive configuration.</p>
 * <p>Input metadata is returned as immutable {@link ToolInput} entries. This
 * sample prints names and whether descriptions are present, not their text.</p>
 * <p>Instructions and custom-function configuration are inspected without
 * printing their values. The raw tool type is shown only for types introduced
 * by a database release that this SDK does not recognize.</p>
 *
 * @see CreateToolWithInputsSample
 * @see SetToolInputsSample
 */
public final class LoadToolSample {

    private LoadToolSample() {
    }

    /**
     * Loads tool metadata and inspects its typed parameters.
     *
     * @param args unused command-line arguments
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String toolName = requiredEnv("SELECTAI_AGENT_TOOL_NAME");
        try (SelectAIAgent client = createClient()) {
            Tool tool = client.tool(toolName);
            System.out.printf("Tool: %s%n", tool.getToolName());
            System.out.printf("Status: %s%n", tool.getStatus());
            System.out.printf("Description: %s%n", tool.getDescription());
            ToolAttributes attributes = tool.getToolAttributes();
            System.out.printf("Tool type: %s%n", attributes.getToolType());
            if (attributes.getToolType() != null && !attributes.getToolType().isKnown()) {
                System.out.printf("Database-defined tool type: %s%n", attributes.getToolTypeValue());
            }
            System.out.printf("Instruction configured: %s%n", attributes.getInstruction() != null);
            System.out.printf("Custom function configured: %s%n", attributes.getFunction() != null);
            printParams(attributes.getToolParams());
            if (attributes.getToolInputs() == null) {
                System.out.println("Tool inputs: not configured");
            } else {
                System.out.printf("Tool inputs: %d%n", attributes.getToolInputs().size());
                for (ToolInput input : attributes.getToolInputs()) {
                    System.out.printf("Input: %s; description configured: %s%n",
                            input.getName(), input.getDescription() != null);
                }
            }
        }
    }

    private static void printParams(ToolParams params) {
        if (params == null) {
            System.out.println("Tool parameters: not configured");
        } else if (params instanceof SqlToolParams sql) {
            System.out.printf("SQL profile: %s%n", sql.getProfileName());
        } else if (params instanceof RagToolParams rag) {
            System.out.printf("RAG profile: %s%n", rag.getProfileName());
        } else if (params instanceof WebSearchToolParams webSearch) {
            System.out.printf("Web-search credential: %s%n", webSearch.getCredentialName());
        } else if (params instanceof SlackNotificationToolParams slack) {
            System.out.printf("Notification type: %s; channel configured: %s%n",
                    slack.getNotificationType(), slack.getChannel() != null);
        } else if (params instanceof EmailNotificationToolParams email) {
            System.out.printf("Notification type: %s; recipient configured: %s%n",
                    email.getNotificationType(), email.getRecipient() != null);
        } else {
            System.out.printf("Database-defined parameter names: %s%n", params.toMap().keySet());
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
