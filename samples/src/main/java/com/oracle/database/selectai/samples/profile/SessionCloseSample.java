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
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for calling {@link Session#close()}.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, open a
 * {@link Profile}, start a conversation-backed {@link Session}, call
 * {@link Session#close()} explicitly, and print the conversation identifier
 * associated with the closed session.</p>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/SessionCloseSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.SessionCloseSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class SessionCloseSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCloseSample.class);

    private SessionCloseSample() {
    }

    public static void main(String[] args) {
        String profileName = SessionNarrateSample.requiredEnv("SELECTAI_PROFILE_NAME");

        try {
            SelectAI selectAI = SessionNarrateSample.createSelectAI();
            Profile profile = selectAI.profile(profileName);
            Conversation conversation = selectAI.conversation(SessionNarrateSample.conversationAttributes());
            Session session = profile.chatSession(conversation, true);
            String conversationId = conversation.getConversationId();

            session.close();
            System.out.printf("Closed session for conversation: %s%n",
                    SessionNarrateSample.valueOrEmpty(conversationId));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to close Select AI session for profile '{}'.", profileName, e);
        }
    }
}
