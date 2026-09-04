/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.profile;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.Session;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for calling Profile#chatSession(Conversation, boolean).
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a
 * {@link Profile}, configure a {@link Conversation}, start a context-aware
 * {@link Session}, and run two chat prompts that share the same conversation
 * ID.</p>
 *
 * <p>This sample changes database state by creating a conversation. Set
 * {@code SELECTAI_SESSION_DELETE_ON_CLOSE=true} to drop the conversation when
 * the session closes.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_CONVERSATION_TITLE - defaults to a sample title.</li>
 *   <li>SELECTAI_CONVERSATION_DESCRIPTION - defaults to a sample description.</li>
 *   <li>SELECTAI_CONVERSATION_RETENTION_DAYS - defaults to {@code 7}.</li>
 *   <li>SELECTAI_CONVERSATION_LENGTH - defaults to {@code 20}.</li>
 *   <li>SELECTAI_SESSION_PROMPT_1 - defaults to a sample first prompt.</li>
 *   <li>SELECTAI_SESSION_PROMPT_2 - defaults to a sample follow-up prompt.</li>
 *   <li>SELECTAI_SESSION_DELETE_ON_CLOSE - defaults to {@code false}.</li>
 * </ul>
 *
 * <p>Example {@code SELECTAI_JDBC_URL}:</p>
 * <pre>{@code
 * jdbc:oracle:thin:@mydb_high?TNS_ADMIN=/path/to/wallet
 * }</pre>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/ChatSessionProfileSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.ChatSessionProfileSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ChatSessionProfileSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSessionProfileSample.class);

    private ChatSessionProfileSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String firstPrompt = envOrDefault("SELECTAI_SESSION_PROMPT_1",
                "What is the importance of history of science?");
        String secondPrompt = envOrDefault("SELECTAI_SESSION_PROMPT_2",
                "Elaborate more on learning from past mistakes.");
        boolean deleteOnClose = Boolean.parseBoolean(envOrDefault("SELECTAI_SESSION_DELETE_ON_CLOSE", "false"));

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            Conversation conversation = selectAI.conversation(buildConversationAttributes());

            try (Session session = profile.chatSession(conversation, deleteOnClose)) {
                System.out.printf("Conversation ID: %s%n", valueOrEmpty(conversation.getConversationId()));
                System.out.printf("First chat response:%n%s%n", valueOrEmpty(session.chat(firstPrompt)));
                System.out.printf("Follow-up chat response:%n%s%n", valueOrEmpty(session.chat(secondPrompt)));
            }
        } catch (SelectAIException e) {
            LOGGER.error("Failed to run chat session for Select AI profile '{}'.", profileName, e);
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

    private static ConversationAttributes buildConversationAttributes() {
        return ConversationAttributes.builder()
                .title(envOrDefault("SELECTAI_CONVERSATION_TITLE", "Java SDK Chat Session"))
                .description(envOrDefault("SELECTAI_CONVERSATION_DESCRIPTION",
                        "Conversation created by the Java Select AI SDK chat session sample."))
                .retentionDays(Integer.valueOf(envOrDefault("SELECTAI_CONVERSATION_RETENTION_DAYS", "7")))
                .conversationLength(Integer.valueOf(envOrDefault("SELECTAI_CONVERSATION_LENGTH", "20")))
                .build();
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

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
