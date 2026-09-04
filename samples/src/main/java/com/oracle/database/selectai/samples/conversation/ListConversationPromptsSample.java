/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.conversation;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.ConversationPrompt;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Sample for listing prompts recorded for a Select AI conversation.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, open an
 * existing {@link Conversation}, call {@link Conversation#listPrompts()}, and
 * print prompt metadata.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_CONVERSATION_ID</li>
 * </ul>
 *
 * <p>Before compiling or running this sample, build the SDK jar and copy runtime
 * dependencies. Run the dependency copy command after {@code mvn clean install},
 * because Maven {@code clean} removes the {@code target/} directory.</p>
 * <pre>{@code
 * mvn clean install
 * mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies
 * }</pre>
 *
 * <p>Compile this sample:</p>
 * <pre>{@code
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   -d samples/out \
 *   samples/src/main/java/com/oracle/database/selectai/samples/conversation/ListConversationPromptsSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.conversation.ListConversationPromptsSample
 * }</pre>
 */
public final class ListConversationPromptsSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListConversationPromptsSample.class);

    private ListConversationPromptsSample() {
    }

    public static void main(String[] args) {
        String conversationId = requiredEnv("SELECTAI_CONVERSATION_ID");

        try {
            SelectAI selectAI = createSelectAI();
            Conversation conversation = selectAI.conversation(conversationId);
            List<ConversationPrompt> prompts = conversation.listPrompts();

            System.out.printf("Conversation ID : %s%n", conversation.getConversationId());
            System.out.printf("Prompt count    : %d%n", prompts.size());
            for (ConversationPrompt prompt : prompts) {
                printPrompt(prompt);
            }
        } catch (SelectAIException e) {
            LOGGER.error("Failed to list prompts for Select AI conversation '{}'.", conversationId, e);
        }
    }

    private static SelectAI createSelectAI() throws SelectAIException {
        DbConnectionConfig dbConnectionConfig = DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();

        return SelectAI.create(dbConnectionConfig);
    }

    private static void printPrompt(ConversationPrompt prompt) {
        System.out.println();
        System.out.printf("Prompt ID       : %s%n", valueOrEmpty(prompt.getConversationPromptId()));
        System.out.printf("Profile name    : %s%n", valueOrEmpty(prompt.getProfileName()));
        System.out.printf("Prompt action   : %s%n", valueOrEmpty(prompt.getPromptAction()));
        System.out.printf("Prompt          : %s%n", valueOrEmpty(prompt.getPrompt()));
        System.out.printf("Prompt response : %s%n", valueOrEmpty(prompt.getPromptResponse()));
        System.out.printf("Created         : %s%n", valueOrEmpty(prompt.getCreated()));
        System.out.printf("Modified        : %s%n", valueOrEmpty(prompt.getModified()));
        System.out.printf("Client ID       : %s%n", valueOrEmpty(prompt.getClientIdentifier()));
        System.out.printf("Client IP       : %s%n", valueOrEmpty(prompt.getClientIp()));
        System.out.printf("SID             : %s%n", valueOrEmpty(prompt.getSid()));
        System.out.printf("Serial#         : %s%n", valueOrEmpty(prompt.getSerialNumber()));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
