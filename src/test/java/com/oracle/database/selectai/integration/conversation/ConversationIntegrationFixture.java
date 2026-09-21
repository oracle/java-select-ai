/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.conversation;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.ConversationPrompt;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live conversation lifecycle integration coverage.
 */
abstract class ConversationIntegrationFixture extends IntegrationTestFixture {

    protected static final String GENERATE_CHAT_WITH_PARAMS =
            "BEGIN ? := DBMS_CLOUD_AI.GENERATE("
                    + "prompt => ?, profile_name => ?, action => ?, "
                    + "attributes => NULL, params => ?); END;";

    protected final List<Conversation> managedConversations = new ArrayList<>();

    @BeforeEach
    final void setUpConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
    }

    @Override
    protected String profileObjectList() {
        return null;
    }

    @AfterEach
    void dropManagedConversations() throws Exception {
        try {
            for (Conversation conversation : managedConversations) {
                try {
                    if (conversation.getConversationId() != null) {
                        conversation.drop(true);
                    }
                } catch (Exception ignored) {
                    // Preserve the primary test failure; cleanup is best effort.
                }
            }
            managedConversations.clear();
        } finally {
            closeIsolatedConnection();
        }
    }


    protected void setClientIdentifier(String clientIdentifier) throws Exception {
        try (CallableStatement statement = jdbcConnection().prepareCall(
                "BEGIN DBMS_SESSION.SET_IDENTIFIER(?); END;")) {
            statement.setString(1, clientIdentifier);
            statement.execute();
        }
    }

    /**
     * Seeds prompt history directly through DBMS_CLOUD_AI.GENERATE for conversation suites that
     * need prompt metadata without depending on the profile chat-session API under test elsewhere.
     */
    protected void createPrompt(Conversation conversation, String prompt) throws Exception {
        String params = "{\"conversation_id\":\"" + conversation.getConversationId() + "\"}";
        try (CallableStatement statement = jdbcConnection().prepareCall(GENERATE_CHAT_WITH_PARAMS)) {
            statement.registerOutParameter(1, Types.CLOB);
            statement.setString(2, prompt);
            statement.setString(3, profileName);
            statement.setString(4, "chat");
            statement.setString(5, params);
            statement.execute();
        }
    }

    protected ConversationPrompt onlyPrompt(Conversation conversation) throws SelectAIException {
        List<ConversationPrompt> prompts = conversation.listPrompts();
        assertThat(prompts).hasSize(1);
        return prompts.get(0);
    }

    protected static String uniquePromptId() {
        return "JSAI_PROMPT_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    protected Conversation createConversation(String title, String description) throws Exception {
        ConversationAttributes.Builder builder = ConversationAttributes.builder();
        if (title != null) {
            builder.title(title);
        }
        builder.description(description);
        Conversation conversation = selectAI.conversation(builder.build());
        conversation.create();
        managedConversations.add(conversation);
        return conversation;
    }

    protected Conversation reload(Conversation conversation) throws Exception {
        return selectAI.conversation(conversation.getConversationId());
    }

    protected static String title(String suffix) {
        return "JSAI_1400_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    protected static String largeClobValue(String marker) {
        return marker + ":" + "x".repeat(40_000 - marker.length() - 1);
    }
}
