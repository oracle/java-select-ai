/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationPromptTest {

    /**
     * Test: Retains every conversation-prompt metadata field supplied to the builder.
     * Expected: Each getter returns the corresponding configured value.
     */
    @Test
    void builderRetainsAllConversationPromptFields() {
        Timestamp created = Timestamp.valueOf("2026-08-24 10:15:30");
        Timestamp modified = Timestamp.valueOf("2026-08-24 10:16:30");

        ConversationPrompt prompt = ConversationPrompt.builder()
                .conversationPromptId("PROMPT_1")
                .conversationId("CONVERSATION_1")
                .conversationTitle("Gymnastics")
                .profileName("SELECT_AI_PROFILE")
                .promptAction("chat")
                .prompt("How many gymnasts are there?")
                .promptResponse("There are five gymnasts.")
                .created(created)
                .modified(modified)
                .clientIdentifier("CLIENT_1")
                .clientIp("192.0.2.10")
                .sid(42L)
                .serialNumber(7L)
                .build();

        assertThat(prompt.getConversationPromptId()).isEqualTo("PROMPT_1");
        assertThat(prompt.getConversationId()).isEqualTo("CONVERSATION_1");
        assertThat(prompt.getConversationTitle()).isEqualTo("Gymnastics");
        assertThat(prompt.getProfileName()).isEqualTo("SELECT_AI_PROFILE");
        assertThat(prompt.getPromptAction()).isEqualTo("chat");
        assertThat(prompt.getPrompt()).isEqualTo("How many gymnasts are there?");
        assertThat(prompt.getPromptResponse()).isEqualTo("There are five gymnasts.");
        assertThat(prompt.getCreated()).isEqualTo(created);
        assertThat(prompt.getModified()).isEqualTo(modified);
        assertThat(prompt.getClientIdentifier()).isEqualTo("CLIENT_1");
        assertThat(prompt.getClientIp()).isEqualTo("192.0.2.10");
        assertThat(prompt.getSid()).isEqualTo(42L);
        assertThat(prompt.getSerialNumber()).isEqualTo(7L);
    }
}
