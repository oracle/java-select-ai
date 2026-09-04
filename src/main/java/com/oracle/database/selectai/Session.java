/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.model.SelectAIException;

/**
 * Represents a Select AI chat session that reuses conversation context across
 * multiple profile generation calls.
 * <p>
 * A session is created from {@link Profile#chatSession(Conversation)} or
 * {@link Profile#chatSession(Conversation, boolean)}. Each session call sends
 * the same conversation ID to {@code DBMS_CLOUD_AI.GENERATE} so later prompts
 * can use prior conversation context.
 * <p>
 * Closing a session does not close the {@link SelectAI} client, the
 * {@link Profile}, or the underlying JDBC connection. If the session was
 * created with {@code deleteOnClose=true}, closing the session drops the
 * associated conversation.
 */
public interface Session extends AutoCloseable {

    /**
     * Sends the prompt as a chat-style request using the session conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ChatSessionProfileSample.html">
     * ChatSessionProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @return chat response
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Session is already closed
     * @throws SelectAIException when generation fails
     */
    String chat(String prompt) throws SelectAIException;

    /**
     * Generates and runs SQL, then asks the model to narrate the result using
     * the session conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SessionNarrateSample.html">
     * SessionNarrateSample source</a>.
     *
     * @param prompt natural language prompt
     * @return narrative response
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Session is already closed
     * @throws SelectAIException when generation fails
     */
    String narrate(String prompt) throws SelectAIException;

    /**
     * Generates SQL from the prompt and runs it using the session conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SessionRunSqlSample.html">
     * SessionRunSqlSample source</a>.
     *
     * @param prompt natural language prompt
     * @return generated SQL execution result/content
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Session is already closed
     * @throws SelectAIException when generation fails
     */
    String runsql(String prompt) throws SelectAIException;

    /**
     * Generates SQL and returns a natural-language explanation using the
     * session conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SessionExplainSqlSample.html">
     * SessionExplainSqlSample source</a>.
     *
     * @param prompt natural language prompt
     * @return explanation of generated SQL
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Session is already closed
     * @throws SelectAIException when generation fails
     */
    String explainsql(String prompt) throws SelectAIException;

    /**
     * Generates SQL from the prompt without running it using the session
     * conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SessionShowSqlSample.html">
     * SessionShowSqlSample source</a>.
     *
     * @param prompt natural language prompt
     * @return generated SQL text
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Session is already closed
     * @throws SelectAIException when generation fails
     */
    String showsql(String prompt) throws SelectAIException;

    /**
     * Returns the augmented prompt that Select AI would send to the provider
     * using the session conversation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SessionShowPromptSample.html">
     * SessionShowPromptSample source</a>.
     *
     * @param prompt natural language prompt
     * @return rewritten/provider-facing prompt
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Session is already closed
     * @throws SelectAIException when generation fails
     */
    String showprompt(String prompt) throws SelectAIException;

    /**
     * Closes this session.
     * <p>
     * This does not close the SDK client or JDBC connection. When the session
     * was created with {@code deleteOnClose=true}, this method drops the
     * associated conversation. Calling this method more than once has no
     * additional effect.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SessionCloseSample.html">
     * SessionCloseSample source</a>.
     *
     * @throws SelectAIException when delete-on-close conversation cleanup fails
     */
    @Override
    void close() throws SelectAIException;
}
