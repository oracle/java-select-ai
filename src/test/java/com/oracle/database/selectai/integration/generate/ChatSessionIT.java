/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.generate;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.Session;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.ConversationPrompt;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live integration coverage for public conversation-bound profile chat sessions.
 */
class ChatSessionIT extends GenerateIntegrationFixture {

    @Override
    protected String profileObjectList() {
        return objectListFor("people", "gymnast");
    }

    @BeforeEach
    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        profile.setAttribute("model", GENERATE_MODEL);
    }

    /**
     * Test: Create a conversation first, then open Profile.chatSession(conversation) and send
     * two chat prompts through the public Session API.
     * Expected: Both responses are non-blank, prompt metadata is recorded under the supplied
     * conversation ID, default close leaves the conversation in place, and the profile remains
     * usable after the Session is closed.
     */
    @Test
    void test16300ChatSessionUsesExistingConversationContext() throws Exception {
        Conversation conversation = newConversation("EXISTING");
        String conversationId = null;
        try {
            conversationId = conversation.create();

            try (Session session = profile.chatSession(conversation)) {
                assertThat(session.chat("What is 4 + 4 ?")).isNotBlank();
                assertThat(session.chat("In one short sentence, what is a database?"))
                        .isNotBlank();
            }

            String createdConversationId = conversationId;
            assertThat(conversation.listPrompts())
                    .hasSizeGreaterThanOrEqualTo(2)
                    .allSatisfy(prompt -> assertPromptConversationId(prompt, createdConversationId));
            assertThat(selectAI.conversation(conversationId).getConversationId())
                    .isEqualTo(conversationId);
            assertThat(profile.getStatus()).isNotBlank();
        } finally {
            dropIfCreated(conversation);
        }
    }

    /**
     * Test: Pass an uncreated configured conversation to Profile.chatSession(conversation).
     * Expected: chatSession creates the conversation, Session.chat records prompt metadata under
     * the generated ID, and closing the default session leaves the conversation available for
     * caller-managed cleanup.
     */
    @Test
    void test16301ChatSessionCreatesConfiguredConversation() throws Exception {
        Conversation conversation = newConversation("AUTO_CREATE");
        try {
            try (Session session = profile.chatSession(conversation)) {
                assertThat(conversation.getConversationId()).isNotBlank();
                assertThat(session.chat("What is 3 + 5 ?")).isNotBlank();
            }

            String conversationId = conversation.getConversationId();
            assertThat(conversation.listPrompts())
                    .isNotEmpty()
                    .allSatisfy(prompt -> assertPromptConversationId(prompt, conversationId));
            assertThat(selectAI.conversation(conversationId).getConversationId())
                    .isEqualTo(conversationId);
        } finally {
            dropIfCreated(conversation);
        }
    }

    /**
     * Test: Open a chat session with deleteOnClose=true for an existing conversation.
     * Expected: Session.close() drops only the conversation; the dropped conversation cannot be
     * reloaded, while the profile remains usable for later generation calls.
     */
    @Test
    void test16302ChatSessionDeleteOnCloseDropsConversationOnly() throws Exception {
        Conversation conversation = newConversation("DELETE_ON_CLOSE");
        String conversationId = null;
        try {
            conversationId = conversation.create();

            try (Session session = profile.chatSession(conversation, true)) {
                assertThat(session.chat("What is 5 + 3 ?")).isNotBlank();
            }

            String droppedConversationId = conversationId;
            assertThatThrownBy(() -> selectAI.conversation(droppedConversationId))
                    .isInstanceOf(SelectAIException.class)
                    .hasMessage("SelectAI conversation not found: " + droppedConversationId);
            assertThat(profile.getStatus()).isNotBlank();
        } finally {
            dropIfCreated(conversation);
        }
    }

    /**
     * Test: Open two separate Session instances against the same existing conversation.
     * Expected: Each session returns a non-blank response, and both prompts are recorded in the
     * same conversation history after the first session has been closed.
     */
    @Test
    void test16303ChatSessionReopensSameConversationContext() throws Exception {
        Conversation conversation = newConversation("REOPEN");
        String firstPrompt = "JSAI reopen first prompt "
                + UUID.randomUUID().toString().replace("-", "");
        String secondPrompt = "JSAI reopen second prompt "
                + UUID.randomUUID().toString().replace("-", "");
        String conversationId = null;
        try {
            conversationId = conversation.create();

            try (Session session = profile.chatSession(conversation)) {
                assertThat(session.chat(firstPrompt)).isNotBlank();
            }
            try (Session session = profile.chatSession(conversation)) {
                assertThat(session.chat(secondPrompt)).isNotBlank();
            }

            String createdConversationId = conversationId;
            assertThat(conversation.listPrompts())
                    .hasSizeGreaterThanOrEqualTo(2)
                    .allSatisfy(prompt -> assertPromptConversationId(prompt, createdConversationId))
                    .extracting(ConversationPrompt::getPrompt)
                    .contains(firstPrompt, secondPrompt);
        } finally {
            dropIfCreated(conversation);
        }
    }

    /**
     * Test: Send one chat prompt through Profile.chatSession and read prompt history through the
     * public Conversation API.
     * Expected: The stored prompt row preserves the prompt text, conversation ID/title, profile
     * name, chat action, prompt ID, and non-blank response.
     */
    @Test
    void test16304ChatSessionPromptHistoryMapsFields() throws Exception {
        String conversationTitle = conversationTitle("PROMPT_FIELDS");
        Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                .title(conversationTitle)
                .build());
        String promptText = "JSAI prompt-history field check "
                + UUID.randomUUID().toString().replace("-", "");
        String conversationId = null;
        try {
            conversationId = conversation.create();
            assertThat(conversation.listPrompts()).as("new conversation prompt history").isEmpty();

            try (Session session = profile.chatSession(conversation)) {
                assertThat(session.chat(promptText)).isNotBlank();
            }

            ConversationPrompt storedPrompt = findPrompt(conversation.listPrompts(), promptText);
            assertThat(storedPrompt.getConversationPromptId()).isNotBlank();
            assertThat(storedPrompt.getConversationId()).isEqualTo(conversationId);
            assertThat(storedPrompt.getConversationTitle()).isEqualTo(conversationTitle);
            assertThat(storedPrompt.getProfileName()).isEqualTo(profileName);
            assertThat(storedPrompt.getPromptAction()).isEqualToIgnoringCase("chat");
            assertThat(storedPrompt.getPrompt()).isEqualTo(promptText);
            assertThat(storedPrompt.getPromptResponse()).isNotBlank();
        } finally {
            dropIfCreated(conversation);
        }
    }

    private Conversation newConversation(String suffix) throws Exception {
        return selectAI.conversation(ConversationAttributes.builder()
                .title(conversationTitle(suffix))
                .build());
    }

    private static String conversationTitle(String suffix) {
        return "JSAI_CHAT_SESSION_" + suffix + "_"
                + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase();
    }

    private static void assertPromptConversationId(ConversationPrompt prompt, String conversationId) {
        assertThat(prompt.getConversationId()).isEqualTo(conversationId);
    }

    private static ConversationPrompt findPrompt(List<ConversationPrompt> prompts, String promptText) {
        return prompts.stream()
                .filter(prompt -> promptText.equals(prompt.getPrompt()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Prompt history did not contain: " + promptText));
    }

    private static void dropIfCreated(Conversation conversation) {
        if (conversation == null || conversation.getConversationId() == null) {
            return;
        }
        try {
            conversation.drop(true);
        } catch (Exception ignored) {
            // Preserve the primary test failure; cleanup is best effort.
        }
    }
}
