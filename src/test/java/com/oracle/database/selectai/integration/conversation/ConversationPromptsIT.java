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

/** Integration coverage for conversation ConversationPrompts. */
class ConversationPromptsIT extends ConversationIntegrationFixture {

    /**
     * Test: Creates a conversation titled "MISSING_PROMPT", calls deletePrompt() with a generated
     * nonexistent prompt ID and force=true, then lists prompts.
     * Expected: The database treats deletion of the missing prompt as successful (true), and the
     * conversation's prompt list remains empty.
     */
    @Test
    void test14300DeleteMissingPromptWithForce() throws Exception {
        Conversation conversation = createConversation(title("MISSING_PROMPT"), null);

        assertThat(conversation.deletePrompt(uniquePromptId(), true)).isTrue();
        assertThat(conversation.listPrompts()).isEmpty();
    }

    /**
     * Test: Attempts to delete a prompt ID that is not present in a conversation with force=false.
     * Expected: The database reports the missing prompt as a SelectAIException.
     */
    @Test
    void test14301DeleteMissingPromptWithoutForceFails() throws Exception {
        Conversation conversation = createConversation(title("MISSING_PROMPT_NO_FORCE"), null);

        assertThatThrownBy(() -> conversation.deletePrompt(uniquePromptId(), false))
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Creates a conversation titled "EMPTY_PROMPTS" without inserting any prompt rows and
     * calls listPrompts().
     * Expected: listPrompts() returns an empty list for the new conversation.
     */
    @Test
    void test14302ListPromptsForNewConversation() throws Exception {
        Conversation conversation = createConversation(title("EMPTY_PROMPTS"), null);

        assertThat(conversation.listPrompts()).isEmpty();
    }

    /**
     * Test: Creates a conversation, inserts one chat prompt through DBMS_CLOUD_AI.GENERATE with
     * params containing that conversation_id, deletes the prompt with force=false, and reloads the
     * conversation.
     * Expected: deletePrompt() returns true, the conversation retains its original ID, and the
     * reloaded listPrompts() is empty.
     */
    @Test
    void test14303DeletePromptKeepsConversation() throws Exception {
        Conversation conversation = createConversation(title("DELETE_PROMPT"), null);
        createPrompt(conversation, "Delete this prompt and keep the conversation");
        String conversationId = conversation.getConversationId();
        String promptId = onlyPrompt(conversation).getConversationPromptId();

        assertThat(conversation.deletePrompt(promptId, false)).isTrue();

        Conversation reloaded = reload(conversation);
        assertThat(reloaded.getConversationId()).isEqualTo(conversationId);
        assertThat(reloaded.listPrompts()).isEmpty();
    }

    /**
     * Test: Creates a conversation, inserts prompts "First prompt to delete" and
     * "Second prompt to preserve" through DBMS_CLOUD_AI.GENERATE, deletes the first returned prompt
     * ID with force=true, and lists the remaining prompts.
     * Expected: Exactly one prompt remains and its ID is the second prompt's original ID; the
     * selected prompt is the only row removed.
     */
    @Test
    void test14304DeleteOnePromptPreservesOthers() throws Exception {
        Conversation conversation = createConversation(title("DELETE_ONE"), null);
        createPrompt(conversation, "First prompt to delete");
        createPrompt(conversation, "Second prompt to preserve");
        List<ConversationPrompt> prompts = conversation.listPrompts();
        String deletedPromptId = prompts.get(0).getConversationPromptId();
        String preservedPromptId = prompts.get(1).getConversationPromptId();

        assertThat(conversation.deletePrompt(deletedPromptId, true)).isTrue();

        List<ConversationPrompt> remaining = conversation.listPrompts();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getConversationPromptId()).isEqualTo(preservedPromptId);
    }

    /**
     * Test: Creates a conversation, inserts one prompt with text
     * "Delete this prompt with the default force value", and calls deletePrompt(promptId) without
     * the force argument.
     * Expected: The default-force deletion returns true and listPrompts() returns no remaining
     * prompt rows.
     */
    @Test
    void test14305DeletePromptWithDefaultForce() throws Exception {
        Conversation conversation = createConversation(title("DEFAULT_FORCE"), null);
        createPrompt(conversation, "Delete this prompt with the default force value");
        String promptId = onlyPrompt(conversation).getConversationPromptId();

        assertThat(conversation.deletePrompt(promptId)).isTrue();
        assertThat(conversation.listPrompts()).isEmpty();
    }

    /**
     * Test: Creates a conversation, inserts one prompt with text "Delete this prompt with force",
     * and calls deletePrompt(promptId, true).
     * Expected: The forced deletion returns true and the conversation's prompt list is empty.
     */
    @Test
    void test14306DeletePromptWithForce() throws Exception {
        Conversation conversation = createConversation(title("FORCE_DELETE"), null);
        createPrompt(conversation, "Delete this prompt with force");
        String promptId = onlyPrompt(conversation).getConversationPromptId();

        assertThat(conversation.deletePrompt(promptId, true)).isTrue();
        assertThat(conversation.listPrompts()).isEmpty();
    }

    /**
     * Test: Creates a conversation titled with "PROMPT_FIELDS", verifies its prompt history is
     * initially empty, sets DBMS_SESSION client_identifier to a generated JSAI_PROMPT_FIELDS value,
     * inserts a chat prompt containing "Return the number of active customers ..." through
     * DBMS_CLOUD_AI.GENERATE(params => {conversation_id}), and calls listPrompts().
     * Expected: Exactly one ConversationPrompt is returned. Its prompt ID and response are
     * non-blank, conversation ID/title match the created conversation, profile name matches the
     * fixture profile, action is chat ignoring case, prompt text matches the generated input,
     * client identifier matches the session value, client IP is non-null, and SID and serial
     * number are positive. Cleanup drops the managed conversation.
     */
    @Test
    void test14307ListPromptMapsAllFields() throws Exception {
        String conversationTitle = title("PROMPT_FIELDS");
        String promptText = "Return the number of active customers "
                + UUID.randomUUID().toString().replace("-", "");
        String clientIdentifier = "JSAI_PROMPT_FIELDS_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Conversation conversation = createConversation(conversationTitle, null);

        // Confirm this isolated conversation has no pre-existing prompt rows.
        assertThat(conversation.listPrompts()).as("new conversation prompt history").isEmpty();

        setClientIdentifier(clientIdentifier);
        createPrompt(conversation, promptText);

        List<ConversationPrompt> prompts = conversation.listPrompts();
        assertThat(prompts).hasSize(1);
        ConversationPrompt storedPrompt = prompts.get(0);

        assertThat(storedPrompt.getConversationPromptId()).isNotBlank();
        assertThat(storedPrompt.getConversationId()).isEqualTo(conversation.getConversationId());
        assertThat(storedPrompt.getConversationTitle()).isEqualTo(conversationTitle);
        assertThat(storedPrompt.getProfileName()).isEqualTo(profileName);
        assertThat(storedPrompt.getPromptAction()).isEqualToIgnoringCase("chat");
        assertThat(storedPrompt.getPrompt()).isEqualTo(promptText);
        assertThat(storedPrompt.getPromptResponse()).isNotBlank();
        assertThat(storedPrompt.getClientIdentifier()).isEqualTo(clientIdentifier);
        assertThat(storedPrompt.getClientIp()).isNotNull();
        assertThat(storedPrompt.getSid()).isPositive();
        assertThat(storedPrompt.getSerialNumber()).isPositive();
    }
}
