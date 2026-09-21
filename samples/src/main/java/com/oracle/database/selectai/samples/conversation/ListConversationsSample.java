/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.conversation;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Sample for listing Select AI conversations.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, call
 * {@link SelectAI#listConversations()}, and print conversation metadata
 * returned by the SDK.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
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
 * javac --release 17 -cp "target/select-ai-1.0.0.jar:target/dependency/*" \
 *   -d samples/out \
 *   samples/src/main/java/com/oracle/database/selectai/samples/conversation/ListConversationsSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.conversation.ListConversationsSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ListConversationsSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListConversationsSample.class);

    private ListConversationsSample() {
    }

    public static void main(String[] args) {
        try {
            SelectAI selectAI = createSelectAI();
            List<Conversation> conversations = selectAI.listConversations();

            if (conversations.isEmpty()) {
                System.out.println("No Select AI conversations found.");
                return;
            }

            System.out.printf("Found %d Select AI conversation(s):%n", conversations.size());
            for (Conversation conversation : conversations) {
                ConversationAttributes attributes = conversation.getConversationAttributes();
                System.out.println("----------------------------------------");
                System.out.printf("Conversation ID : %s%n", valueOrEmpty(conversation.getConversationId()));
                printAttributes(attributes);
            }
        } catch (SelectAIException e) {
            LOGGER.error("Failed to list Select AI conversations.", e);
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

    private static void printAttributes(ConversationAttributes attributes) {
        if (attributes == null) {
            System.out.println("Attributes      : ");
            return;
        }
        System.out.printf("Title           : %s%n", valueOrEmpty(attributes.getTitle()));
        System.out.printf("Description     : %s%n", valueOrEmpty(attributes.getDescription()));
        System.out.printf("Retention days  : %s%n", valueOrEmpty(attributes.getRetentionDays()));
        System.out.printf("Context length  : %s%n", valueOrEmpty(attributes.getConversationLength()));
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
