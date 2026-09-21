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

/** Integration coverage for conversation ConversationReferences. */
class ConversationReferencesIT extends ConversationIntegrationFixture {

    /**
     * Test: Creates a conversation titled with the "DELETE" suffix, records its ID, calls
     * drop(true), and then calls selectAI.conversation(id) using the deleted ID.
     * Expected: drop(true) returns true and the subsequent lookup throws SelectAIException with
     * the exact message "SelectAI conversation not found: <deleted ID>".
     */
    @Test
    void test14200DeleteConversation() throws Exception {
        Conversation conversation = createConversation(title("DELETE"), null);
        String id = conversation.getConversationId();
        assertThat(conversation.drop(true)).isTrue();

        assertThatThrownBy(() -> selectAI.conversation(id))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("SelectAI conversation not found: " + id);
    }

    /**
     * Test: Creates a conversation titled with "DELETE_TWICE", calls drop(true), and calls
     * drop(false) again on the same object.
     * Expected: The first drop returns true; the second call throws SelectAIException whose cause
     * contains the database error prefix "ORA-20050: Conversation id=".
     */
    @Test
    void test14201DeleteTwice() throws Exception {
        Conversation conversation = createConversation(title("DELETE_TWICE"), null);
        assertThat(conversation.drop(true)).isTrue();
        assertThatThrownBy(() -> conversation.drop(false))
                .isInstanceOf(SelectAIException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("ORA-20050: Conversation id="));
    }

    /**
     * Test: Creates a conversation titled with "LIST", calls selectAI.listConversations(), and
     * extracts each returned conversation ID.
     * Expected: The ID assigned to the newly created conversation is present in the database-backed
     * list returned by the SDK.
     */
    @Test
    void test14202ListContainsCreatedConversation() throws Exception {
        Conversation conversation = createConversation(title("LIST"), null);
        assertThat(selectAI.listConversations())
                .extracting(Conversation::getConversationId)
                .contains(conversation.getConversationId());
    }

    /**
     * Test: Creates three conversations with generated titles ending in "AI", "DB", and "MATH",
     * then collects their three conversation IDs.
     * Expected: The ID collection contains no duplicates, so each create operation produced a
     * distinct database resource.
     */
    @Test
    void test14203MultipleConversationsHaveUniqueIds() throws Exception {
        Conversation first = createConversation(title("AI"), null);
        Conversation second = createConversation(title("DB"), null);
        Conversation third = createConversation(title("MATH"), null);

        assertThat(List.of(first.getConversationId(), second.getConversationId(),
                third.getConversationId())).doesNotHaveDuplicates();
    }

    /**
     * Test: Creates a conversation titled with "LIST_NEW", calls listConversations(), and extracts
     * the IDs from the returned Conversation objects.
     * Expected: The newly created conversation's ID appears in the returned collection.
     */
    @Test
    void test14204ListContainsNewConversation() throws Exception {
        Conversation conversation = createConversation(title("LIST_NEW"), null);
        assertThat(selectAI.listConversations())
                .extracting(Conversation::getConversationId)
                .contains(conversation.getConversationId());
    }

    /**
     * Test: Creates a conversation titled with "LIST_TYPE" and calls selectAI.listConversations().
     * Expected: Every element returned by the SDK is an instance of Conversation, including the
     * newly created database row.
     */
    @Test
    void test14205ListReturnsConversationInstances() throws Exception {
        createConversation(title("LIST_TYPE"), null);
        assertThat(selectAI.listConversations())
                .allSatisfy(item -> assertThat(item).isInstanceOf(Conversation.class));
    }

    /**
     * Test: Creates one conversation, obtains a second reference by ID, updates the first reference
     * to a generated "SHARED_UPDATED" title, description "Updated shared conversation description",
     * retentionDays 30, and conversationLength 5, then reads both references.
     * Expected: The second reference and a fresh reload both return all four updated database values,
     * rather than the original title, description, retention, or length.
     */
    @Test
    void test14206SecondReferenceSeesUpdatedAttributesAfterOtherReferenceUpdates() throws Exception {
        String initialTitle = title("SHARED_INITIAL");
        String initialDescription = "Initial shared conversation description";
        Conversation first = createConversation(initialTitle, initialDescription);
        Conversation second = selectAI.conversation(first.getConversationId());
        ConversationAttributes updated = ConversationAttributes.builder()
                .title(title("SHARED_UPDATED"))
                .description("Updated shared conversation description")
                .retentionDays(30)
                .conversationLength(5)
                .build();

        assertThat(first.setAttributes(updated)).isTrue();

        ConversationAttributes secondAttributes = second.getConversationAttributes();
        assertThat(secondAttributes.getTitle()).isEqualTo(updated.getTitle());
        assertThat(secondAttributes.getDescription()).isEqualTo(updated.getDescription());
        assertThat(secondAttributes.getRetentionDays()).isEqualTo(updated.getRetentionDays());
        assertThat(secondAttributes.getConversationLength())
                .isEqualTo(updated.getConversationLength());

        Conversation refreshed = reload(first);
        assertThat(refreshed.getConversationAttributes().getTitle())
                .isEqualTo(updated.getTitle());
        assertThat(refreshed.getConversationAttributes().getDescription())
                .isEqualTo(updated.getDescription());
        assertThat(refreshed.getConversationAttributes().getRetentionDays())
                .isEqualTo(updated.getRetentionDays());
        assertThat(refreshed.getConversationAttributes().getConversationLength())
                .isEqualTo(updated.getConversationLength());
    }

    /**
     * Test: Creates one conversation, obtains a second reference by ID, drops the first reference
     * with force=true, and calls setAttributes() on the stale reference with title "AFTER_DROP".
     * Expected: The stale update throws SelectAIException and its cause contains
     * "ORA-20050: Conversation id=" because the database row was already removed.
     */
    @Test
    void test14207DatabaseOperationFailsOnSecondReferenceAfterFirstDrops() throws Exception {
        Conversation first = createConversation(title("SHARED_DROP"), null);
        Conversation second = selectAI.conversation(first.getConversationId());

        assertThat(first.drop(true)).isTrue();

        assertThatThrownBy(() -> second.setAttributes(
                ConversationAttributes.builder()
                        .title(title("AFTER_DROP"))
                .description("Should fail after the conversation is dropped")
                        .build()))
                .isInstanceOf(SelectAIException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("ORA-20050: Conversation id="));
    }
}
