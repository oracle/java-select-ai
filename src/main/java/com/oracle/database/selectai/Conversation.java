/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.ConversationPrompt;
import com.oracle.database.selectai.model.SelectAIException;

import java.util.List;

/**
 * Contract for Select AI conversation lifecycle and metadata management.
 * <p>
 * A conversation stores chat context so a later prompt can refer to earlier
 * turns. The {@code Conversation} object manages the database conversation and its metadata,
 * such as title, description, retention period, and context length.
 * <p>
 * A {@code Conversation} object can be configured for creation or
 * database-backed. A configured conversation has attributes in memory, but it
 * does not have a database conversation ID until {@link #create()} succeeds. A
 * database-backed conversation is opened from the database, listed from
 * metadata, or successfully created through this SDK.
 * <p>
 * Metadata getters can return configured values before creation. Operations
 * that execute against an existing database conversation, such as drop,
 * metadata update, prompt listing, and prompt deletion, require a
 * database-backed conversation. The SDK rejects those operations with
 * {@link IllegalStateException} when the object is not bound to a conversation
 * ID or is configured for creation but has not been created yet.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI conversation reference</a>
 */
public interface Conversation {
    /**
     * Returns the database identifier for this conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/GetConversationIdSample.html">
     * GetConversationIdSample source</a>.
     *
     * @return conversation ID
     */
    String getConversationId();

    /**
     * Drops this conversation from the database.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/DropConversationSample.html">
     * DropConversationSample source</a>.
     *
     * @param force when {@code true}, performs force drop if supported
     * @return {@code true} when drop succeeds
     * @throws IllegalStateException when this Conversation instance is not bound to a conversation ID
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when the database conversation cannot be dropped
     */
    boolean drop(boolean force) throws SelectAIException;

    /**
     * Creates the configured conversation in the database and returns its
     * generated conversation identifier.
     * <p>
     * Call this after obtaining a conversation object from
     * {@link SelectAI#conversation(ConversationAttributes)}.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/CreateConversationSample.html">
     * CreateConversationSample source</a>.
     *
     * @return conversation ID returned by {@code DBMS_CLOUD_AI.CREATE_CONVERSATION}
     * @throws SelectAIException when the conversation cannot be created
     */
    String create() throws SelectAIException;

    /**
     * Returns conversation metadata.
     * <p>
     * For a configured conversation that has not been created yet, returns the
     * attributes supplied by the caller. For a conversation bound to a
     * conversation ID, refreshes metadata from the database before returning
     * attributes.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/GetConversationAttributesSample.html">
     * GetConversationAttributesSample source</a>.
     *
     * @return conversation attributes
     * @throws SelectAIException when bound conversation metadata cannot be fetched
     */
    ConversationAttributes getConversationAttributes() throws SelectAIException;

    /**
     * Updates conversation metadata such as title, description, retention, or
     * context length.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/SetConversationAttributesSample.html">
     * SetConversationAttributesSample source</a>.
     *
     * @param conversationAttributes attributes to set
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this Conversation instance is not bound to a conversation ID
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean setAttributes(ConversationAttributes conversationAttributes) throws SelectAIException;

    /**
     * Lists prompts recorded for this conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/ListConversationPromptsSample.html">
     * ListConversationPromptsSample source</a>.
     *
     * @return prompt rows for this conversation ordered by creation time
     * @throws IllegalStateException when this Conversation instance is not bound to a conversation ID
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when the prompt metadata cannot be fetched
     */
    List<ConversationPrompt> listPrompts() throws SelectAIException;

    /**
     * Deletes a conversation prompt.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/DeleteConversationPromptSample.html">
     * DeleteConversationPromptSample source</a>.
     *
     * @param conversationPromptId conversation prompt identifier
     * @return {@code true} when delete succeeds
     * @throws IllegalStateException when this Conversation instance is not bound to a conversation ID
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when the prompt cannot be deleted
     */
    boolean deletePrompt(String conversationPromptId) throws SelectAIException;

    /**
     * Deletes a conversation prompt.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/DeleteConversationPromptSample.html">
     * DeleteConversationPromptSample source</a>.
     *
     * @param conversationPromptId conversation prompt identifier
     * @param force when {@code true}, performs force delete if supported
     * @return {@code true} when delete succeeds
     * @throws IllegalStateException when this Conversation instance is not bound to a conversation ID
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when the prompt cannot be deleted
     */
    boolean deletePrompt(String conversationPromptId, boolean force) throws SelectAIException;
}
