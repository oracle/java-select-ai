/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SummaryParams;
import com.oracle.database.selectai.model.SyntheticDataBatchRequest;
import com.oracle.database.selectai.model.SyntheticDataSingleRequest;

/**
 * Contract for Select AI profile lifecycle, prompt execution, and attribute
 * management.
 * <p>
 * An AI profile is the database-side configuration that tells Select AI which
 * provider and AI model to use, which credential authorizes provider calls, and which
 * schema metadata or vector index should be used when answering natural
 * language prompts. A {@code Profile} instance may represent a profile created
 * by this Java SDK, PL/SQL, Python, SQL tools, or any other client, as long as
 * that profile is visible in the current schema.
 * <p>
 * A {@code Profile} object can be configured for creation or database-backed.
 * A configured profile has a profile name, attributes, description, and status
 * in memory, but it is not persisted until {@link #create()} succeeds. A
 * database-backed profile is opened from the database, listed from metadata, or
 * successfully created through this SDK.
 * <p>
 * Metadata getters can return configured values before creation. Operations
 * that execute against an existing database profile, such as prompt
 * generation, attribute updates, enable, disable, drop, feedback, summarize,
 * translate, and synthetic-data generation, require a database-backed profile.
 * The SDK rejects those operations with {@link IllegalStateException} when the
 * object is not bound to a profile name or is configured for creation but has
 * not been created yet.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Manage AI profiles</a>
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-about.html">
 *      About Select AI</a>
 */
public interface Profile {
    /**
     * Creates this configured profile in the database.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/CreateProfileSample.html">
     * CreateProfileSample source</a>.
     *
     * @return {@code true} when create succeeds
     * @throws IllegalStateException when this Profile instance is not configured for creation
     * @throws SelectAIException when create fails
     */
    boolean create() throws SelectAIException;

    /**
     * Drops this profile from the database.
     * <p>
     * Use this when the provider and AI model configuration is no longer needed. The
     * operation affects the database profile object, not only the Java object.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/DropProfileSample.html">
     * DropProfileSample source</a>.
     *
     * @param force when {@code true}, force drop is attempted
     * @return {@code true} when drop succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when drop fails
     */
    boolean drop(boolean force) throws SelectAIException;

    /**
     * Enables this profile so it can be used for Select AI operations.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/EnableProfileSample.html">
     * EnableProfileSample source</a>.
     *
     * @return {@code true} when enable succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when enable fails
     */
    boolean enable() throws SelectAIException;

    /**
     * Disables this profile so callers cannot use it for Select AI operations
     * until it is enabled again.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/DisableProfileSample.html">
     * DisableProfileSample source</a>.
     *
     * @return {@code true} when disable succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when disable fails
     */
    boolean disable() throws SelectAIException;

    /**
     * Sends a natural-language prompt to Select AI using this profile.
     * <p>
     * The selected action determines whether Select AI runs SQL, returns SQL,
     * explains SQL, narrates query results, chats, generates embeddings, or
     * shows the provider-facing prompt.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GenerateProfileSample.html">
     * GenerateProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateAction action type (runsql, showsql, chat, embedding, etc.)
     * @return generated response text
     * @throws IllegalArgumentException when {@code prompt} is null/blank or
     *         {@code generateAction} is null
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     */
    String generate(String prompt, GenerateAction generateAction) throws SelectAIException;

    /**
     * Sends a natural-language prompt to Select AI using this profile and
     * request-level profile attributes.
     * <p>
     * The supplied {@code profileAttributes} are passed only for this generate
     * request. They do not update the stored profile definition.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GenerateWithProfileAttributesSample.html">
     * GenerateWithProfileAttributesSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateAction action type (runsql, showsql, chat, embedding, etc.)
     * @param profileAttributes optional request-level profile attributes
     * @return generated response text
     * @throws IllegalArgumentException when {@code prompt} is null/blank or
     *         {@code generateAction} is null
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     */
    String generate(String prompt, GenerateAction generateAction, ProfileAttributes profileAttributes) throws SelectAIException;

    /**
     * Sends a natural-language prompt to Select AI using this profile and
     * optional generate parameters.
     * <p>
     * Generate parameters are serialized to the {@code params} payload accepted
     * by {@code DBMS_CLOUD_AI.GENERATE}. For example, callers can pass a
     * conversation identifier for context-aware chat requests.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GenerateWithGenerateParamsSample.html">
     * GenerateWithGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateAction action type (runsql, showsql, chat, embedding, etc.)
     * @param generateParams optional generate parameters
     * @return generated response text
     * @throws IllegalArgumentException when {@code prompt} is null/blank or
     *         {@code generateAction} is null
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     */
    String generate(String prompt, GenerateAction generateAction, GenerateParams generateParams) throws SelectAIException;

    /**
     * Sends a natural-language prompt to Select AI using this profile,
     * request-level profile attributes, and optional generate parameters.
     * <p>
     * The supplied {@code profileAttributes} are passed only for this generate
     * request and do not update the stored profile definition.
     * {@code generateParams} are serialized to the {@code params} payload
     * accepted by {@code DBMS_CLOUD_AI.GENERATE}.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GenerateWithProfileAttributesAndGenerateParamsSample.html">
     * GenerateWithProfileAttributesAndGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateAction action type (runsql, showsql, chat, embedding, etc.)
     * @param profileAttributes optional request-level profile attributes
     * @param generateParams optional generate parameters
     * @return generated response text
     * @throws IllegalArgumentException when {@code prompt} is null/blank or
     *         {@code generateAction} is null
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     */
    String generate(String prompt, GenerateAction generateAction, ProfileAttributes profileAttributes,
                    GenerateParams generateParams) throws SelectAIException;

    /**
     * Generates SQL from the prompt and runs it.
     * <p>
     * Use this for NL2SQL workflows where the application wants the database
     * result rather than only the generated SQL text.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/RunSqlProfileSample.html">
     * RunSqlProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @return generated SQL execution result/content
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction)
     */
    String runsql(String prompt) throws SelectAIException;

    /**
     * Generates SQL from the prompt and runs it using optional generate
     * parameters.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/RunSqlWithGenerateParamsSample.html">
     * RunSqlWithGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateParams optional generate parameters, such as conversation ID
     * @return generated SQL execution result/content
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction, GenerateParams)
     */
    String runsql(String prompt, GenerateParams generateParams) throws SelectAIException;

    /**
     * Generates SQL from the prompt without running it.
     * <p>
     * Use this when the application wants to review, log, or approve SQL before
     * execution.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ShowSqlProfileSample.html">
     * ShowSqlProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @return generated SQL text
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction)
     */
    String showsql(String prompt) throws SelectAIException;

    /**
     * Generates SQL from the prompt without running it using optional generate
     * parameters.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ShowSqlWithGenerateParamsSample.html">
     * ShowSqlWithGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateParams optional generate parameters, such as conversation ID
     * @return generated SQL text
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction, GenerateParams)
     */
    String showsql(String prompt, GenerateParams generateParams) throws SelectAIException;

    /**
     * Generates SQL and returns a natural-language explanation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ExplainSqlProfileSample.html">
     * ExplainSqlProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @return explanation of generated SQL
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction)
     */
    String explainsql(String prompt) throws SelectAIException;

    /**
     * Generates SQL and returns a natural-language explanation using optional
     * generate parameters.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ExplainSqlWithGenerateParamsSample.html">
     * ExplainSqlWithGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateParams optional generate parameters, such as conversation ID
     * @return explanation of generated SQL
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction, GenerateParams)
     */
    String explainsql(String prompt, GenerateParams generateParams) throws SelectAIException;

    /**
     * Generates and runs SQL, then asks the model to narrate the result.
     * <p>
     * This action can require data access because the model may receive query
     * result values to produce the narrative response.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/NarrateProfileSample.html">
     * NarrateProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @return narrative response
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction)
     */
    String narrate(String prompt) throws SelectAIException;

    /**
     * Generates and runs SQL, then asks the model to narrate the result using
     * optional generate parameters.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/NarrateWithGenerateParamsSample.html">
     * NarrateWithGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateParams optional generate parameters, such as conversation ID
     * @return narrative response
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction, GenerateParams)
     */
    String narrate(String prompt, GenerateParams generateParams) throws SelectAIException;

    /**
     * Returns the augmented prompt that Select AI would send to the provider.
     * <p>
     * Use this for troubleshooting profile metadata, object selection, and
     * prompt augmentation behavior.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ShowPromptProfileSample.html">
     * ShowPromptProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @return rewritten/provider-facing prompt
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction)
     */
    String showprompt(String prompt) throws SelectAIException;

    /**
     * Returns the augmented prompt that Select AI would send to the provider
     * using optional generate parameters.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ShowPromptWithGenerateParamsSample.html">
     * ShowPromptWithGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateParams optional generate parameters, such as conversation ID
     * @return rewritten/provider-facing prompt
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction, GenerateParams)
     */
    String showprompt(String prompt, GenerateParams generateParams) throws SelectAIException;

    /**
     * Sends the prompt as a chat-style request using this profile.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ChatProfileSample.html">
     * ChatProfileSample source</a>.
     *
     * @param prompt natural language prompt
     * @return chat response
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction)
     */
    String chat(String prompt) throws SelectAIException;

    /**
     * Sends the prompt as a chat-style request using optional generate
     * parameters.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ChatWithGenerateParamsSample.html">
     * ChatWithGenerateParamsSample source</a>.
     *
     * @param prompt natural language prompt
     * @param generateParams optional generate parameters, such as conversation ID
     * @return chat response
     * @throws IllegalArgumentException when {@code prompt} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when generation fails
     * @see #generate(String, GenerateAction, GenerateParams)
     */
    String chat(String prompt, GenerateParams generateParams) throws SelectAIException;

    /**
     * Starts a Select AI chat session using the supplied conversation.
     * <p>
     * If the conversation does not yet have a conversation ID, this method
     * calls {@link Conversation#create()} before creating the session. The
     * resulting conversation ID is sent with each session generation request.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ChatSessionWithoutDeleteSample.html">
     * ChatSessionWithoutDeleteSample source</a>.
     *
     * @param conversation conversation to use for context-aware generation
     * @return session bound to the conversation ID
     * @throws IllegalArgumentException when {@code conversation} is null
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when conversation creation or session initialization fails
     */
    Session chatSession(Conversation conversation) throws SelectAIException;

    /**
     * Starts a Select AI chat session using the supplied conversation.
     * <p>
     * When {@code deleteOnClose} is true, {@link Session#close()} drops the
     * conversation. Closing the session does not close this profile, the
     * {@link SelectAI} client, or the underlying JDBC connection.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ChatSessionProfileSample.html">
     * ChatSessionProfileSample source</a>.
     *
     * @param conversation conversation to use for context-aware generation
     * @param deleteOnClose whether to drop the conversation when the session closes
     * @return session bound to the conversation ID
     * @throws IllegalArgumentException when {@code conversation} is null
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when conversation creation, drop, or session initialization fails
     */
    Session chatSession(Conversation conversation, boolean deleteOnClose) throws SelectAIException;

    /**
     * Updates one profile attribute using a string value.
     * <p>
     * Profile attributes tune provider settings, object selection, AI model
     * behavior, RAG configuration, and other Select AI behavior.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SetProfileStringAttributeSample.html">
     * SetProfileStringAttributeSample source</a>.
     *
     * @param attributeName attribute name
     * @param attributeValue attribute value (null clears attribute where supported)
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean setAttribute(String attributeName, String attributeValue) throws SelectAIException;

    /**
     * Updates one profile attribute using a boolean value.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SetProfileBooleanAttributeSample.html">
     * SetProfileBooleanAttributeSample source</a>.
     *
     * @param attributeName attribute name
     * @param attributeValue boolean value
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean setAttribute(String attributeName, boolean attributeValue) throws SelectAIException;

    /**
     * Updates one profile attribute using an integer value.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SetProfileIntegerAttributeSample.html">
     * SetProfileIntegerAttributeSample source</a>.
     *
     * @param attributeName attribute name
     * @param attributeValue integer value
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean setAttribute(String attributeName, Integer attributeValue) throws SelectAIException;

    /**
     * Updates one profile attribute using a floating-point value.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SetProfileFloatAttributeSample.html">
     * SetProfileFloatAttributeSample source</a>.
     *
     * @param attributeName attribute name
     * @param attributeValue float value
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean setAttribute(String attributeName, Float attributeValue) throws SelectAIException;

    /**
     * Replaces or updates multiple profile attributes in one operation.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SetProfileAttributesSample.html">
     * SetProfileAttributesSample source</a>.
     *
     * @param profileAttributes attributes payload
     * @return {@code true} when update succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when update fails
     */
    boolean setAttributes(ProfileAttributes profileAttributes) throws SelectAIException;

    /**
     * Stores or removes feedback about generated SQL for this profile.
     * <p>
     * Feedback is profile-specific guidance for NL2SQL behavior, not a general
     * rating for chat or RAG answers. Positive feedback confirms that generated
     * SQL is useful; negative feedback can include the expected response and
     * explanatory comments so future prompts can be guided toward a better SQL
     * shape. The target profile is this {@code Profile} instance.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SubmitFeedbackProfileSample.html">
     * SubmitFeedbackProfileSample source</a>.
     *
     * @param feedbackRequest feedback payload
     * @return {@code true} when feedback is successfully submitted
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when submission fails
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
     *      DBMS_CLOUD_AI feedback reference</a>
     */
    boolean feedback(Feedback feedbackRequest) throws SelectAIException;

    /**
     * Generates synthetic data using this profile and a single-object request payload.
     * <p>
     * The target AI profile is this {@code Profile} instance. The request object
     * keeps the object name, owner, row count, prompt guidance, and optional
     * generation parameters together.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GenerateSyntheticDataSingleRequestSample.html">
     * GenerateSyntheticDataSingleRequestSample source</a>.
     *
     * @param request single request payload
     * @return {@code true} when generation request is accepted/succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when single-request synthetic data generation fails
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
     *      DBMS_CLOUD_AI synthetic data reference</a>
     */
    boolean generateSyntheticData(SyntheticDataSingleRequest request) throws SelectAIException;

    /**
     * Generates synthetic data using this profile for multiple objects in one request.
     * <p>
     * Batch generation is useful for related tables where referential or
     * domain consistency across generated data matters. The target AI profile is
     * this {@code Profile} instance.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GenerateSyntheticDataBatchRequestSample.html">
     * GenerateSyntheticDataBatchRequestSample source</a>.
     *
     * @param request batch request payload
     * @return {@code true} when generation request is accepted/succeeds
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when batch synthetic data generation fails
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
     *      DBMS_CLOUD_AI synthetic data reference</a>
     */
    boolean generateSyntheticData(SyntheticDataBatchRequest request) throws SelectAIException;

    /**
     * Summarizes inline text or content read from an external location.
     * <p>
     * Use this operation when the application wants a concise natural-language
     * summary rather than generated SQL. The request may provide text directly
     * or reference content through a location and credential.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SummarizeProfileSample.html">
     * SummarizeProfileSample source</a>.
     *
     * @param content inline content
     * @param credential_name credential name for external source access
     * @param location_uri source URI
     * @param userPrompt summarization prompt/instructions
     * @param params typed summarization parameters
     * @return summary text
     * @throws IllegalArgumentException when neither or both of {@code content} and {@code location_uri} are provided
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when the database summarization call fails
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
             DBMS_CLOUD_AI summarization reference</a>
     */
    String summarize(String content,
                     String credential_name,
                     String location_uri,
                     String userPrompt,
                     SummaryParams params) throws SelectAIException;

    /**
     * Translates text using the provider configured by this profile.
     * <p>
     * Source and target languages are omitted. The database can use profile
     * language attributes or source-language detection according to
     * {@code DBMS_CLOUD_AI.TRANSLATE} behavior.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/TranslateProfileSample.html">
     * TranslateProfileSample source</a>.
     *
     * @param text source text
     * @return translated text
     * @throws IllegalArgumentException when {@code text} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when translation fails
     */
    String translate(String text) throws SelectAIException;

    /**
     * Translates text using the provider configured by this profile and an
     * explicit target language.
     * <p>
     * The source language is omitted. The database can use profile language
     * attributes or source-language detection according to
     * {@code DBMS_CLOUD_AI.TRANSLATE} behavior.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/TranslateProfileSample.html">
     * TranslateProfileSample source</a>.
     *
     * @param text source text
     * @param targetLanguage target language/code
     * @return translated text
     * @throws IllegalArgumentException when {@code text} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when translation fails
     */
    String translate(String text,
                     String targetLanguage) throws SelectAIException;

    /**
     * Translates text using the provider configured by this profile.
     * <p>
     * Source and target language values are optional and may be {@code null}.
     * When omitted, the database can use profile language attributes or
     * source-language detection according to {@code DBMS_CLOUD_AI.TRANSLATE}
     * behavior.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/TranslateProfileSample.html">
     * TranslateProfileSample source</a>.
     *
     * @param text source text
     * @param sourceLanguage source language/code
     * @param targetLanguage target language/code
     * @return translated text
     * @throws IllegalArgumentException when {@code text} is null or blank
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     *         or is configured for creation but has not been created yet
     * @throws SelectAIException when translation fails
     */
    String translate(String text,
                     String sourceLanguage,
                     String targetLanguage) throws SelectAIException;

    /**
     * Returns profile name.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GetProfileNameSample.html">
     * GetProfileNameSample source</a>.
     *
     * @return profile name
     */
    String getProfileName();

    /**
     * Returns profile status.
     * <p>
     * For a configured profile that has not been created yet, returns the
     * caller-supplied create status. For a profile bound to an existing
     * database profile, refreshes metadata from the database before returning
     * the status.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GetProfileStatusSample.html">
     * GetProfileStatusSample source</a>.
     *
     * @return profile status
     * @throws SelectAIException when current profile metadata cannot be fetched
     */
    String getStatus() throws SelectAIException;

    /**
     * Returns profile description.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GetProfileDescriptionSample.html">
     * GetProfileDescriptionSample source</a>.
     *
     * @return profile description
     */
    String getDescription();

    /**
     * Returns profile attributes.
     * <p>
     * For a configured profile that has not been created yet, returns the
     * caller-supplied create attributes. For a profile bound to an existing
     * database profile, refreshes attributes from the database before returning
     * them.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/GetProfileAttributesSample.html">
     * GetProfileAttributesSample source</a>.
     *
     * @return profile attributes
     * @throws IllegalStateException when this Profile instance is not bound to a profile name
     * @throws SelectAIException when current profile attributes cannot be fetched
     */
    ProfileAttributes getProfileAttributes() throws SelectAIException;
}
