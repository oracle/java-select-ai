/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.Session;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default conversation-bound session implementation.
 * <p>
 * The session reuses a {@link GenerateParams} payload containing the
 * conversation ID for each generation call. It does not own JDBC resources;
 * {@link #close()} marks the session closed and optionally drops the
 * conversation when delete-on-close is enabled.
 */
final class DefaultSession implements Session {
    /** Logger for session lifecycle operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSession.class);

    /** Profile used to execute generation calls. */
    private final Profile profile;
    /** Conversation whose ID is attached to each generation call. */
    private final Conversation conversation;
    /** Generate parameters persisted across session calls. */
    private final GenerateParams generateParams;
    /** Whether closing the session should drop the conversation. */
    private final boolean deleteOnClose;
    /** Whether the session has been closed. */
    private boolean closed;

    DefaultSession(Profile profile, Conversation conversation, boolean deleteOnClose) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (conversation == null) {
            throw new IllegalArgumentException("conversation must not be null");
        }
        String conversationId = conversation.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must be provided");
        }
        this.profile = profile;
        this.conversation = conversation;
        this.deleteOnClose = deleteOnClose;
        this.generateParams = GenerateParams.builder()
                .conversationId(conversationId)
                .build();
    }

    @Override
    public String chat(String prompt) throws SelectAIException {
        requireOpen();
        return profile.generate(prompt, GenerateAction.chat, generateParams);
    }

    @Override
    public String narrate(String prompt) throws SelectAIException {
        requireOpen();
        return profile.generate(prompt, GenerateAction.narrate, generateParams);
    }

    @Override
    public String runsql(String prompt) throws SelectAIException {
        requireOpen();
        return profile.generate(prompt, GenerateAction.runsql, generateParams);
    }

    @Override
    public String explainsql(String prompt) throws SelectAIException {
        requireOpen();
        return profile.generate(prompt, GenerateAction.explainsql, generateParams);
    }

    @Override
    public String showsql(String prompt) throws SelectAIException {
        requireOpen();
        return profile.generate(prompt, GenerateAction.showsql, generateParams);
    }

    @Override
    public String showprompt(String prompt) throws SelectAIException {
        requireOpen();
        return profile.generate(prompt, GenerateAction.showprompt, generateParams);
    }

    @Override
    public void close() throws SelectAIException {
        if (closed) {
            return;
        }
        closed = true;
        if (deleteOnClose) {
            LOGGER.debug("Dropping conversation {} on session close", conversation.getConversationId());
            conversation.drop(true);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Session is already closed");
        }
    }
}
