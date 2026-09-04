/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.conversation;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for deleting one prompt from a Select AI conversation.
 *
 * <p>This sample changes database state by calling
 * {@link Conversation#deletePrompt(String, boolean)} for the prompt identified
 * by {@code SELECTAI_CONVERSATION_PROMPT_ID}. Verify the prompt ID before
 * running it.</p>
 *
 * <p>To run this sample, provide an existing conversation prompt identifier
 * through {@code SELECTAI_CONVERSATION_PROMPT_ID}. The integration script skips
 * prompt deletion when no prompt ID is available.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_CONVERSATION_ID</li>
 *   <li>SELECTAI_CONVERSATION_PROMPT_ID</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_CONVERSATION_PROMPT_DELETE_FORCE - defaults to {@code false}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/conversation/DeleteConversationPromptSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.conversation.DeleteConversationPromptSample
 * }</pre>
 */
public final class DeleteConversationPromptSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteConversationPromptSample.class);

    private DeleteConversationPromptSample() {
    }

    public static void main(String[] args) {
        String conversationId = requiredEnv("SELECTAI_CONVERSATION_ID");
        String conversationPromptId = requiredEnv("SELECTAI_CONVERSATION_PROMPT_ID");
        boolean force = Boolean.parseBoolean(envOrDefault("SELECTAI_CONVERSATION_PROMPT_DELETE_FORCE", "false"));

        try {
            SelectAI selectAI = createSelectAI();
            Conversation conversation = selectAI.conversation(conversationId);
            boolean deleted = conversation.deletePrompt(conversationPromptId, force);

            System.out.printf("Conversation ID : %s%n", conversation.getConversationId());
            System.out.printf("Prompt ID       : %s%n", conversationPromptId);
            System.out.printf("Deleted         : %s%n", deleted);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to delete prompt '{}' for Select AI conversation '{}'.",
                    conversationPromptId, conversationId, e);
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

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
