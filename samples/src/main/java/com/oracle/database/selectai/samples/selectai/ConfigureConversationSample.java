/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.selectai;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for configuring a Select AI conversation object through {@link SelectAI}.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a
 * {@link ConversationAttributes} payload, and call
 * {@link SelectAI#conversation(ConversationAttributes)}. It does not call
 * {@link Conversation#create()}.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_CONVERSATION_TITLE - defaults to a sample title.</li>
 *   <li>SELECTAI_CONVERSATION_DESCRIPTION - defaults to a sample description.</li>
 *   <li>SELECTAI_CONVERSATION_RETENTION_DAYS - defaults to {@code 7}.</li>
 *   <li>SELECTAI_CONVERSATION_LENGTH - defaults to {@code 20}.</li>
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
 * <p>Compile and run this sample:</p>
 * <pre>{@code
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/selectai/ConfigureConversationSample.java
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.selectai.ConfigureConversationSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ConfigureConversationSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigureConversationSample.class);

    private ConfigureConversationSample() {
    }

    public static void main(String[] args) {
        try {
            SelectAI selectAI = createSelectAI();
            ConversationAttributes attributes = buildConversationAttributes();
            Conversation conversation = selectAI.conversation(attributes);

            System.out.printf("Conversation object configured: %s%n", conversation.getClass().getName());
            System.out.printf("Conversation ID before create: %s%n", valueOrEmpty(conversation.getConversationId()));
            System.out.printf("Attributes: %s%n", attributes.toJson());
        } catch (SelectAIException e) {
            LOGGER.error("Failed to configure Select AI conversation object.", e);
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
                .title(envOrDefault("SELECTAI_CONVERSATION_TITLE", "Java SDK Sample Conversation"))
                .description(envOrDefault("SELECTAI_CONVERSATION_DESCRIPTION",
                        "Conversation configured by the Java Select AI SDK sample."))
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
