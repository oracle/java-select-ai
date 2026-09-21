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

/**
 * Sample for reading Select AI conversation attributes.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a
 * {@link Conversation} object for an existing conversation, call
 * {@link Conversation#getConversationAttributes()}, and print the metadata.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_CONVERSATION_ID</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/conversation/GetConversationAttributesSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.conversation.GetConversationAttributesSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class GetConversationAttributesSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetConversationAttributesSample.class);

    private GetConversationAttributesSample() {
    }

    public static void main(String[] args) {
        String conversationId = requiredEnv("SELECTAI_CONVERSATION_ID");

        try {
            SelectAI selectAI = createSelectAI();
            Conversation conversation = selectAI.conversation(conversationId);
            ConversationAttributes attributes = conversation.getConversationAttributes();

            System.out.printf("Conversation ID : %s%n", valueOrEmpty(conversation.getConversationId()));
            printAttributes(attributes);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to read attributes for Select AI conversation '{}'.", conversationId, e);
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
        System.out.printf("JSON            : %s%n", attributes.toJson());
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
