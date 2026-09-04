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
 * Sample for calling {@link Profile#chatSession(Conversation)}.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a
 * {@link Profile}, configure a conversation, start a conversation-backed
 * {@link Session}, call {@link Session#chat(String)}, and leave the created
 * conversation in the database when the session is closed. The conversation is
 * not deleted because this sample uses the default
 * {@link Profile#chatSession(Conversation)} behavior.</p>
 *
 * <p>This sample changes database state by creating a conversation. Drop the
 * conversation after the sample completes if it is no longer required.</p>
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
 *   <li>SELECTAI_SESSION_PROMPT_1 - chat prompt. Defaults to a sample prompt.</li>
 *   <li>SELECTAI_CONVERSATION_TITLE - conversation title.</li>
 *   <li>SELECTAI_CONVERSATION_DESCRIPTION - conversation description.</li>
 *   <li>SELECTAI_CONVERSATION_RETENTION_DAYS - retention period. Defaults to {@code 7}.</li>
 *   <li>SELECTAI_CONVERSATION_LENGTH - conversation length. Defaults to {@code 20}.</li>
 * </ul>
 *
 * <p>Example {@code SELECTAI_JDBC_URL}:</p>
 * <pre>{@code
 * jdbc:oracle:thin:@mydb_high?TNS_ADMIN=/path/to/wallet
 * }</pre>
 *
 * <p>Before compiling or running this sample, build the SDK JAR and copy runtime
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/ChatSessionWithoutDeleteSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.ChatSessionWithoutDeleteSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ChatSessionWithoutDeleteSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSessionWithoutDeleteSample.class);

    private ChatSessionWithoutDeleteSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String prompt = envOrDefault("SELECTAI_SESSION_PROMPT_1",
                "What is the importance of history of science?");

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                    .title(envOrDefault("SELECTAI_CONVERSATION_TITLE", "Java SDK Chat Session"))
                    .description(envOrDefault("SELECTAI_CONVERSATION_DESCRIPTION",
                            "Conversation created by the Java Select AI SDK chat session sample."))
                    .retentionDays(Integer.valueOf(envOrDefault("SELECTAI_CONVERSATION_RETENTION_DAYS", "7")))
                    .conversationLength(Integer.valueOf(envOrDefault("SELECTAI_CONVERSATION_LENGTH", "20")))
                    .build());

            try (Session session = profile.chatSession(conversation)) {
                System.out.printf("Conversation ID: %s%n", valueOrEmpty(conversation.getConversationId()));
                System.out.printf("Chat response:%n%s%n", valueOrEmpty(session.chat(prompt)));
            }
        } catch (SelectAIException e) {
            LOGGER.error("Failed to run chat session for Select AI profile '{}'.", profileName, e);
        }
    }

    private static SelectAI createSelectAI() throws SelectAIException {
        return SelectAI.create(DbConnectionConfig.builder()
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

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
