/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSessionTest {

    @Mock
    private Profile profile;
    @Mock
    private Conversation conversation;

    /**
     * Test: Verifies every Session operation delegates through Profile.generate with the
     * session conversation ID.
     * Expected: Each public operation uses the matching GenerateAction and passes a
     * GenerateParams payload containing CONV-123.
     */
    @Test
    void operationsDelegateWithConversationIdParams() throws Exception {
        when(conversation.getConversationId()).thenReturn("CONV-123");
        when(profile.generate(eq("chat prompt"), eq(GenerateAction.chat), any(GenerateParams.class)))
                .thenReturn("chat response");
        when(profile.generate(eq("narrate prompt"), eq(GenerateAction.narrate), any(GenerateParams.class)))
                .thenReturn("narrate response");
        when(profile.generate(eq("runsql prompt"), eq(GenerateAction.runsql), any(GenerateParams.class)))
                .thenReturn("runsql response");
        when(profile.generate(eq("explainsql prompt"), eq(GenerateAction.explainsql), any(GenerateParams.class)))
                .thenReturn("explainsql response");
        when(profile.generate(eq("showsql prompt"), eq(GenerateAction.showsql), any(GenerateParams.class)))
                .thenReturn("showsql response");
        when(profile.generate(eq("showprompt prompt"), eq(GenerateAction.showprompt), any(GenerateParams.class)))
                .thenReturn("showprompt response");
        DefaultSession session = new DefaultSession(profile, conversation, false);

        assertThat(session.chat("chat prompt")).isEqualTo("chat response");
        assertThat(session.narrate("narrate prompt")).isEqualTo("narrate response");
        assertThat(session.runsql("runsql prompt")).isEqualTo("runsql response");
        assertThat(session.explainsql("explainsql prompt")).isEqualTo("explainsql response");
        assertThat(session.showsql("showsql prompt")).isEqualTo("showsql response");
        assertThat(session.showprompt("showprompt prompt")).isEqualTo("showprompt response");

        ArgumentCaptor<GenerateParams> paramsCaptor = ArgumentCaptor.forClass(GenerateParams.class);
        verify(profile).generate(eq("chat prompt"), eq(GenerateAction.chat), paramsCaptor.capture());
        verify(profile).generate(eq("narrate prompt"), eq(GenerateAction.narrate), paramsCaptor.capture());
        verify(profile).generate(eq("runsql prompt"), eq(GenerateAction.runsql), paramsCaptor.capture());
        verify(profile).generate(eq("explainsql prompt"), eq(GenerateAction.explainsql), paramsCaptor.capture());
        verify(profile).generate(eq("showsql prompt"), eq(GenerateAction.showsql), paramsCaptor.capture());
        verify(profile).generate(eq("showprompt prompt"), eq(GenerateAction.showprompt), paramsCaptor.capture());
        assertThat(paramsCaptor.getAllValues())
                .extracting(GenerateParams::getConversationId)
                .containsOnly("CONV-123");
    }

    /**
     * Test: Verifies constructor validation for mandatory session collaborators.
     * Expected: Missing profile, conversation, or conversation ID is rejected before any
     * generation call can be made.
     */
    @Test
    void constructorRejectsMissingProfileConversationOrConversationId() {
        assertThatThrownBy(() -> new DefaultSession(null, conversation, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profile");
        assertThatThrownBy(() -> new DefaultSession(profile, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversation");

        Conversation missingId = mock(Conversation.class);
        when(missingId.getConversationId()).thenReturn(null);
        assertThatThrownBy(() -> new DefaultSession(profile, missingId, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");

        Conversation blankId = mock(Conversation.class);
        when(blankId.getConversationId()).thenReturn(" ");
        assertThatThrownBy(() -> new DefaultSession(profile, blankId, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
    }

    /**
     * Test: Verifies close without delete-on-close only closes the Session wrapper.
     * Expected: Closing twice is harmless, the conversation is not dropped, and every later
     * session operation fails before Profile.generate is called.
     */
    @Test
    void closeWithoutDeleteDoesNotDropConversationAndRejectsAllLaterOperations() throws Exception {
        when(conversation.getConversationId()).thenReturn("CONV-123");
        DefaultSession session = new DefaultSession(profile, conversation, false);

        session.close();
        session.close();

        verify(conversation, never()).drop(true);
        verify(conversation, never()).drop(false);
        assertThatThrownBy(() -> session.chat("after close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session is already closed");
        assertThatThrownBy(() -> session.narrate("after close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session is already closed");
        assertThatThrownBy(() -> session.runsql("after close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session is already closed");
        assertThatThrownBy(() -> session.explainsql("after close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session is already closed");
        assertThatThrownBy(() -> session.showsql("after close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session is already closed");
        assertThatThrownBy(() -> session.showprompt("after close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session is already closed");
        verifyNoInteractions(profile);
    }

    /**
     * Test: Verifies delete-on-close cleanup.
     * Expected: close() drops the conversation with force=true exactly once even when close is
     * called repeatedly.
     */
    @Test
    void closeWithDeleteDropsConversationOnce() throws Exception {
        when(conversation.getConversationId()).thenReturn("CONV-123");
        DefaultSession session = new DefaultSession(profile, conversation, true);

        session.close();
        session.close();

        verify(conversation).drop(true);
    }

    /**
     * Test: Verifies delete-on-close failures are surfaced to the caller.
     * Expected: The SelectAIException from Conversation.drop(true) is propagated unchanged.
     */
    @Test
    void closePropagatesDeleteFailure() throws Exception {
        when(conversation.getConversationId()).thenReturn("CONV-123");
        SelectAIException failure = new SelectAIException("drop failed");
        doThrow(failure).when(conversation).drop(true);
        DefaultSession session = new DefaultSession(profile, conversation, true);

        assertThatThrownBy(session::close).isSameAs(failure);
    }

    /**
     * Test: Verifies generation failures from the bound profile are not swallowed by Session.
     * Expected: The SelectAIException from Profile.generate is propagated unchanged.
     */
    @Test
    void operationPropagatesGenerateFailure() throws Exception {
        when(conversation.getConversationId()).thenReturn("CONV-123");
        SelectAIException failure = new SelectAIException("generate failed");
        when(profile.generate(eq("fail prompt"), eq(GenerateAction.chat), any(GenerateParams.class)))
                .thenThrow(failure);
        DefaultSession session = new DefaultSession(profile, conversation, false);

        assertThatThrownBy(() -> session.chat("fail prompt")).isSameAs(failure);
    }
}
