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
 * Sample for calling {@link Session#narrate(String)}.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a
 * {@link Profile}, configure a conversation, start a temporary conversation
 * {@link Session}, call {@link Session#narrate(String)}, and delete the
 * conversation when the session closes.</p>
 *
 * <p>This sample changes database state by creating and deleting a conversation.
 * The conversation is deleted because the sample uses
 * {@link Profile#chatSession(Conversation, boolean)} with
 * {@code deleteOnClose=true}.</p>
 *
 * <p>To run this sample with prompts such as {@code how many customers}, ensure
 * the profile object list contains tables that match the prompt. For example,
 * if the profile object list references the Oracle sample schema objects
 * {@code SH.CUSTOMERS} and {@code SH.COUNTRIES}, ensure the {@code SH} schema
 * is installed and visible to {@code SELECTAI_DB_USER}. The {@code SH} schema
 * is not guaranteed to exist in every Oracle Autonomous AI Database or Oracle
 * Database instance.</p>
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
 *   <li>SELECTAI_SESSION_PROMPT_1 - session prompt. Defaults to {@code how many customers}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/SessionNarrateSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.SessionNarrateSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class SessionNarrateSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionNarrateSample.class);

    private SessionNarrateSample() {
    }

    public static void main(String[] args) {
        run("narrate");
    }

    static void run(String operation) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String prompt = envOrDefault("SELECTAI_SESSION_PROMPT_1", "how many customers");

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            Conversation conversation = selectAI.conversation(conversationAttributes());

            try (Session session = profile.chatSession(conversation, true)) {
                String response = switch (operation) {
                    case "runsql" -> session.runsql(prompt);
                    case "explainsql" -> session.explainsql(prompt);
                    case "showsql" -> session.showsql(prompt);
                    case "showprompt" -> session.showprompt(prompt);
                    default -> session.narrate(prompt);
                };
                System.out.printf("Session %s response:%n%s%n", operation, valueOrEmpty(response));
            }
        } catch (SelectAIException e) {
            LOGGER.error("Failed to run session {} for Select AI profile '{}'.", operation, profileName, e);
        }
    }

    static SelectAI createSelectAI() throws SelectAIException {
        return SelectAI.create(DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build());
    }

    static ConversationAttributes conversationAttributes() {
        return ConversationAttributes.builder()
                .title(envOrDefault("SELECTAI_CONVERSATION_TITLE", "Java SDK Session Sample"))
                .description(envOrDefault("SELECTAI_CONVERSATION_DESCRIPTION",
                        "Conversation created by a Java Select AI SDK session sample."))
                .retentionDays(Integer.valueOf(envOrDefault("SELECTAI_CONVERSATION_RETENTION_DAYS", "7")))
                .conversationLength(Integer.valueOf(envOrDefault("SELECTAI_CONVERSATION_LENGTH", "20")))
                .build();
    }

    static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
