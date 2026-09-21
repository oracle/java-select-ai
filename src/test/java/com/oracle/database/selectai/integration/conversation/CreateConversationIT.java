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
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.GenerateParams;
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

/** Integration coverage for CreateConversation. */
class CreateConversationIT extends ConversationIntegrationFixture {

    /**
     * Test: Calls selectAI.conversation() with a generated title and a null description, then
     * invokes create() and reads the assigned conversation ID.
     * Expected: create() succeeds and getConversationId() returns a non-blank database-generated
     * ID; the @AfterEach cleanup drops the created conversation.
     */
    @Test
    void test14000CreateWithTitle() throws Exception {
        Conversation conversation = createConversation(title("ACTIVE"), null);
        assertThat(conversation.getConversationId()).isNotBlank();
    }

    /**
     * Test: Creates a conversation with a generated title and the description
     * "LLM's understanding of history of science", reloads it by ID, and reads
     * getConversationAttributes().
     * Expected: The reloaded title equals the generated title and the reloaded description equals
     * the supplied text, proving both values were persisted by the database.
     */
    @Test
    void test14001CreateWithDescription() throws Exception {
        String title = title("HISTORY");
        Conversation conversation = createConversation(
                title, "LLM's understanding of history of science");
        ConversationAttributes attributes = reload(conversation).getConversationAttributes();

        assertThat(attributes.getTitle()).isEqualTo(title);
        assertThat(attributes.getDescription())
                .isEqualTo("LLM's understanding of history of science");
    }

    /**
     * Test: Builds and creates a conversation with all optional attributes omitted, then reloads
     * it by the created ID.
     * Expected: The database applies title "New Conversation" and retention-days 7, while
     * description and conversation length remain null.
     */
    @Test
    void test14002CreateWithoutTitle() throws Exception {
        Conversation conversation = createConversation(null, null);
        ConversationAttributes attributes = reload(conversation).getConversationAttributes();
        assertThat(attributes.getTitle()).isEqualTo("New Conversation");
        assertThat(attributes.getDescription()).isNull();
        assertThat(attributes.getRetentionDays()).isEqualTo(7);
        assertThat(attributes.getConversationLength()).isNull();
    }

    /**
     * Test: Calls selectAI.conversation((ConversationAttributes) null) without sending a create
     * request to the database.
     * Expected: The Java API immediately throws IllegalArgumentException because the attributes
     * object itself is null.
     */
    @Test
    void test14003CreateWithMissingAttributes() {
        assertThatThrownBy(() -> selectAI.conversation((ConversationAttributes) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Builds a pending conversation titled "JSAI_1400_PENDING_CREATE_..." and calls
     * setAttributes(), listPrompts(), deletePrompt() with a generated prompt ID, and drop(true)
     * before create().
     * Expected: getConversationId() is null and each database operation throws IllegalStateException
     * with the operation-specific message requiring create() first; no pending operation reaches
     * the database.
     */
    @Test
    void test14004ConversationRejectsDatabaseOperationsBeforeCreate() throws Exception {
        Conversation pendingConversation = selectAI.conversation(
                ConversationAttributes.builder().title(title("PENDING_CREATE")).build());

        assertThat(pendingConversation.getConversationId()).isNull();
        assertThatThrownBy(() -> pendingConversation.setAttributes(
                ConversationAttributes.builder().title(title("UPDATED_PENDING")).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("setAttributes requires a created conversation; call create() first");
        assertThatThrownBy(pendingConversation::listPrompts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("list conversation prompts requires a created conversation; call create() first");
        assertThatThrownBy(() -> pendingConversation.deletePrompt(uniquePromptId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delete conversation prompt requires a created conversation; call create() first");
        assertThatThrownBy(() -> pendingConversation.drop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created conversation; call create() first");
    }

    /**
     * Test: Builds a pending conversation with a generated "PENDING_GETTERS" title, description
     * "Pending conversation getter regression", retentionDays 30, and conversationLength 12;
     * reads getConversationAttributes() before create(), then creates and reloads it.
     * Expected: Both the pending and database-backed attribute objects contain the same title,
     * description, retention days, and conversation length supplied to the builder.
     */
    @Test
    void test14005PendingConversationAttributesPreserveConfigurationBeforeCreate() throws Exception {
        ConversationAttributes expected = ConversationAttributes.builder()
                .title(title("PENDING_GETTERS"))
                .description("Pending conversation getter regression")
                .retentionDays(30)
                .conversationLength(12)
                .build();
        Conversation pending = selectAI.conversation(expected);
        managedConversations.add(pending);

        ConversationAttributes beforeCreate = pending.getConversationAttributes();
        assertThat(beforeCreate.getTitle()).isEqualTo(expected.getTitle());
        assertThat(beforeCreate.getDescription()).isEqualTo(expected.getDescription());
        assertThat(beforeCreate.getRetentionDays()).isEqualTo(expected.getRetentionDays());
        assertThat(beforeCreate.getConversationLength()).isEqualTo(expected.getConversationLength());

        assertThat(pending.create()).isNotBlank();
        Conversation reloaded = reload(pending);
        ConversationAttributes afterCreate = reloaded.getConversationAttributes();
        assertThat(afterCreate.getTitle()).isEqualTo(expected.getTitle());
        assertThat(afterCreate.getDescription()).isEqualTo(expected.getDescription());
        assertThat(afterCreate.getRetentionDays()).isEqualTo(expected.getRetentionDays());
        assertThat(afterCreate.getConversationLength()).isEqualTo(expected.getConversationLength());
    }

    /**
     * Test: Creates a conversation using the documented upper and lower attribute boundaries.
     * Expected: The database persists the maximum title length, zero retention days, and the
     * maximum accepted conversation length without changing the supplied values.
     */
    @Test
    void test14006CreateWithConversationAttributeBoundaryValues() throws Exception {
        String conversationTitle = "T".repeat(128);
        String description = "D".repeat(128);
        Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                .title(conversationTitle)
                .description(description)
                .retentionDays(0)
                .conversationLength(999)
                .build());
        conversation.create();
        managedConversations.add(conversation);

        ConversationAttributes attributes = reload(conversation).getConversationAttributes();
        assertThat(attributes.getTitle()).isEqualTo(conversationTitle);
        assertThat(attributes.getDescription()).isEqualTo(description);
        assertThat(attributes.getRetentionDays()).isZero();
        assertThat(attributes.getConversationLength()).isEqualTo(999);
    }

    /**
     * Test: Attempts to create a conversation with conversation_length 1000 after the SDK accepts
     * the positive caller-supplied value.
     * Expected: DBMS_CLOUD_AI rejects the database-owned upper-bound value and the SDK surfaces
     * the database failure as SelectAIException.
     */
    @Test
    void test14007ConversationLength1000SurfacesDatabaseValidation() throws Exception {
        Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                .title(title("LENGTH_1000"))
                .conversationLength(1000)
                .build());

        assertThatThrownBy(conversation::create)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("CREATE_CONVERSATION")
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("conversation_length")
                        .hasMessageContaining("1000"));
    }

    /**
     * Test: Creates a conversation without a title or description, then sends its first chat
     * request through the profile generate API using the conversation ID.
     * Expected: The database fills in the conversation title and description on first use.
     */
    @Test
    void test14008DatabaseGeneratesTitleAndDescriptionOnFirstUse() throws Exception {
        Conversation conversation = createConversation(null, null);
        GenerateParams params = GenerateParams.builder()
                .conversationId(conversation.getConversationId())
                .build();

        assertThat(profile.generate("What is a database?", GenerateAction.chat, params))
                .isNotBlank();

        ConversationAttributes attributes = reload(conversation).getConversationAttributes();
        assertThat(attributes.getTitle()).isNotBlank();
        assertThat(attributes.getDescription()).isNotBlank();
    }

    /**
     * Test: Creates a conversation with a description larger than the PL/SQL VARCHAR2 bind limit.
     * Expected: CREATE_CONVERSATION persists and returns the complete CLOB description.
     */
    @Test
    void test14009CreateWithLargeClobDescription() throws Exception {
        String description = largeClobValue("conversation-description");
        Conversation conversation = createConversation(title("LARGE_DESCRIPTION"), description);

        assertThat(reload(conversation).getConversationAttributes().getDescription())
                .isEqualTo(description);
    }
}
