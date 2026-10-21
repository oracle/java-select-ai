/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.conversation;

import com.oracle.database.selectai.Conversation;
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

/** Integration coverage for conversation ConversationAttributes. */
class ConversationAttributesIT extends ConversationIntegrationFixture {

    /**
     * Test: Creates a conversation with a generated "ACTIVE" title and null description, reloads
     * it from the database, and reads both title and description.
     * Expected: The title equals the supplied generated value and getDescription() remains null
     * because no description was supplied.
     */
    @Test
    void test14100GetAttributes() throws Exception {
        String conversationTitle = title("ACTIVE");
        Conversation conversation = createConversation(conversationTitle, null);
        ConversationAttributes attributes = reload(conversation).getConversationAttributes();
        assertThat(attributes.getTitle()).isEqualTo(conversationTitle);
        assertThat(attributes.getDescription()).isNull();
    }

    /**
     * Test: Creates a conversation, calls setAttributes() with a generated title, description
     * "Updated Description", retentionDays 30, and conversationLength 5, then reloads it.
     * Expected: setAttributes() returns true and the reloaded title, description, retention days,
     * and conversation length exactly equal all four supplied update values.
     */
    @Test
    void test14101SetAttributes() throws Exception {
        Conversation conversation = createConversation(title("ACTIVE"), null);
        String updatedTitle = title("UPDATED");
        ConversationAttributes updated = ConversationAttributes.builder()
                .title(updatedTitle)
                .description("Updated Description")
                .retentionDays(30)
                .conversationLength(5)
                .build();

        assertThat(conversation.setAttributes(updated)).isTrue();
        ConversationAttributes attributes = reload(conversation).getConversationAttributes();
        assertThat(attributes.getTitle()).isEqualTo(updatedTitle);
        assertThat(attributes.getDescription()).isEqualTo("Updated Description");
        assertThat(attributes.getRetentionDays()).isEqualTo(30);
        assertThat(attributes.getConversationLength()).isEqualTo(5);
    }

    /**
     * Test: Creates a conversation and calls setAttributes(null) on the created object.
     * Expected: The Java API throws IllegalArgumentException before attempting a database update.
     */
    @Test
    void test14102SetAttributesWithNone() throws Exception {
        Conversation conversation = createConversation(title("ACTIVE"), null);
        assertThatThrownBy(() -> conversation.setAttributes(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Attempts create() with a 255-character title and a 1000-character description.
     * Expected: The database rejects the request through {@code SelectAIException}.
     * Oracle error codes can vary by environment, so the test accepts the applicable expected code.
     */
    @Test
    void test14103CreateWithLongValues() throws Exception {
        Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                .title("A".repeat(255))
                .description("B".repeat(1000))
                .build());

        assertThatThrownBy(conversation::create)
                .isInstanceOf(SelectAIException.class)
                .satisfies(exception -> assertThat(exception.getCause().getMessage())
                        .containsAnyOf(
                                "ORA-20050: title value length (255) exceeds the maximum length (128)",
                                "ORA-20050: Value is too long for conversation attribute - title"));
    }

    /**
     * Test: Calls selectAI.conversation("invalid") for an ID that does not identify a database
     * conversation.
     * Expected: The SDK throws SelectAIException with the exact message
     * "SelectAI conversation not found: invalid".
     */
    @Test
    void test14104GetAttributesWithInvalidId() {
        assertThatThrownBy(() -> selectAI.conversation("invalid"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("SelectAI conversation not found: invalid");
    }

    /**
     * Test: Creates a conversation titled "TO_DELETE", records its ID, drops it with force=true,
     * and calls selectAI.conversation(id) afterward.
     * Expected: The post-drop lookup throws SelectAIException with the exact not-found message for
     * the deleted ID.
     */
    @Test
    void test14105GetAttributesForDeletedConversation() throws Exception {
        Conversation conversation = createConversation(title("TO_DELETE"), null);
        String id = conversation.getConversationId();
        conversation.drop(true);

        assertThatThrownBy(() -> selectAI.conversation(id))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("SelectAI conversation not found: " + id);
    }

    /**
     * Test: Creates and reloads a conversation with a generated "NO_DESC" title and null
     * description, then reads its ConversationAttributes.
     * Expected: The generated title is preserved and getDescription() returns null.
     */
    @Test
    void test14106GetAttributesWithoutDescription() throws Exception {
        String conversationTitle = title("NO_DESC");
        Conversation conversation = createConversation(conversationTitle, null);
        ConversationAttributes attributes = reload(conversation).getConversationAttributes();
        assertThat(attributes.getTitle()).isEqualTo(conversationTitle);
        assertThat(attributes.getDescription()).isNull();
    }

    /**
     * Test: Creates a conversation titled with "NONE_DESC" while explicitly passing a null
     * description, then reloads its attributes.
     * Expected: The database persists no description and the reloaded getDescription() is null.
     */
    @Test
    void test14107CreateWithDescriptionNone() throws Exception {
        Conversation conversation = createConversation(title("NONE_DESC"), null);
        assertThat(reload(conversation).getConversationAttributes().getDescription()).isNull();
    }

    /**
     * Test: Builds a conversation titled with "CONVERSATION_LENGTH", description
     * "Conversation length persistence regression", and conversationLength 12, calls create(),
     * reloads the ID, and reads the attributes.
     * Expected: The reloaded getConversationLength() equals 12, confirming the configured length
     * was persisted.
     */
    @Test
    void test14108ConversationLengthPersistsWhenReloaded() throws Exception {
        int expectedConversationLength = 12;
        Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                .title(title("CONVERSATION_LENGTH"))
                .description("Conversation length persistence regression")
                .conversationLength(expectedConversationLength)
                .build());
        conversation.create();
        managedConversations.add(conversation);

        Conversation reloaded = selectAI.conversation(conversation.getConversationId());

        assertThat(reloaded.getConversationAttributes().getConversationLength())
                .isEqualTo(expectedConversationLength);
    }

    /**
     * Test: Attempts to create a conversation with a conversation length above the database
     * maximum after the SDK accepts the positive value.
     * Expected: The database rejects the create request with a SelectAIException.
     */
    @Test
    void test14109DatabaseRejectsConversationLengthAboveMaximum() throws Exception {
        Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                .title(title("LENGTH_TOO_LARGE"))
                .conversationLength(1001)
                .build());

        assertThatThrownBy(conversation::create)
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Verifies that a disabled large-CLOB attribute update remains available without
     * interrupting the numbered active tests in this file.
     */
    @Test
    void test14110SetAttributesWithLargeClobValue() throws Exception {
        Conversation conversation = createConversation(title("LARGE_ATTRIBUTE"), null);
        String description = largeClobValue("conversation-attributes");

        assertThat(conversation.setAttributes(ConversationAttributes.builder()
                .description(description)
                .build())).isTrue();
        assertThat(reload(conversation).getConversationAttributes().getDescription())
                .isEqualTo(description);
    }
}
