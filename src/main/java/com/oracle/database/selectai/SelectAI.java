/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.impl.SelectAIFactory;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.VectorIndexConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * Top-level API contract for Oracle Select AI operations.
 * <p>
 * Select AI lets applications use natural-language prompts for SQL generation,
 * SQL execution, SQL explanation, chat, summarization, synthetic data
 * generation, and retrieval augmented generation (RAG) with vector indexes.
 * This interface is the SDK entry point for creating or opening SDK objects for
 * the database resources that support those features: credentials, profiles,
 * conversations, and vector indexes.
 * <p>
 * {@code SelectAI} extends {@link AutoCloseable}. A {@code SelectAI} client may
 * own SDK-managed JDBC resources depending on how it is created.
 * <p>
 * For clients created with {@link #create(DbConnectionConfig)}, the SDK
 * creates and owns a single JDBC connection. Call {@link #close()} when the
 * client is no longer needed to release that connection and its database
 * session.
 * <p>
 * For clients created with {@link #create(DataSource)}, the application owns
 * the {@code DataSource}. The SDK obtains a connection from the
 * {@code DataSource} for each operation and closes that operation connection
 * after use. Calling {@link #close()} does not close the caller-owned
 * {@code DataSource}.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-about.html">
 *      About Select AI</a>
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI package reference</a>
 */
public interface SelectAI extends AutoCloseable {
    /**
     * Creates a SelectAI client from database connection configuration.
     * <p>
     * This is the recommended public entry point for applications that want the
     * SDK to create and use a single JDBC connection from {@link DbConnectionConfig}.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ListProfilesSample.html">
     * ListProfilesSample source</a>.
     *
     * @param dbConnectionConfig database connection configuration
     * @return SelectAI client
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the database connection cannot be initialized
     */
    static SelectAI create(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        return SelectAIFactory.create(dbConnectionConfig);
    }

    /**
     * Creates a SelectAI client from database connection configuration and SDK
     * execution options.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/CreateSelectAIWithOptionsSample.html">
     * CreateSelectAIWithOptionsSample source</a>.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options; {@code null} uses {@link SelectAIOptions#defaults()}
     * @return SelectAI client
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the database connection cannot be initialized
     */
    static SelectAI create(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        return SelectAIFactory.create(dbConnectionConfig, options);
    }

    /**
     * Creates a SelectAI client backed by a {@link DataSource}.
     * <p>
     * This is the recommended public entry point for applications that manage
     * JDBC connections through a DataSource or connection pool. Each SDK
     * operation obtains a connection from the DataSource and closes it when the
     * operation completes.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/datasource/ListProfilesWithDataSourceSample.html">
     * ListProfilesWithDataSourceSample source</a>.
     *
     * @param dataSource DataSource used to obtain JDBC connections
     * @return SelectAI client
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    static SelectAI create(DataSource dataSource) {
        return SelectAIFactory.create(dataSource);
    }

    /**
     * Creates a SelectAI client backed by a {@link DataSource} and SDK execution
     * options.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/CreateSelectAIDataSourceWithOptionsSample.html">
     * CreateSelectAIDataSourceWithOptionsSample source</a>.
     *
     * @param dataSource DataSource used to obtain JDBC connections
     * @param options SDK execution options; {@code null} uses {@link SelectAIOptions#defaults()}
     * @return SelectAI client
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    static SelectAI create(DataSource dataSource, SelectAIOptions options) {
        return SelectAIFactory.create(dataSource, options);
    }

    /**
     * Releases resources owned by this SelectAI client.
     * <p>
     * For clients created with {@link #create(DbConnectionConfig)}, this closes
     * the SDK-owned JDBC connection. For clients created with
     * {@link #create(DataSource)}, this does not close the caller-owned
     * DataSource; individual operation connections are already closed after
     * each operation.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/CloseSelectAISample.html">
     * CloseSelectAISample source</a>.
     *
     * @throws SelectAIException when an owned JDBC connection cannot be closed
     */
    @Override
    void close() throws SelectAIException;

    /**
     * Returns the JDBC connection retained by this SelectAI client.
     * <p>
     * This method is available only for clients created with
     * {@link #create(DbConnectionConfig)}, where the SDK owns and reuses one
     * JDBC connection. Use it when application code needs to execute custom SQL
     * or PL/SQL in the same database session used by SDK operations.
     * <p>
     * For clients created with {@link #create(DataSource)}, there is no single
     * retained connection. In that mode, this method throws
     * {@link IllegalStateException}; callers should obtain custom JDBC
     * connections from their own DataSource directly.
     * <p>
     * The returned connection is owned by this SelectAI client. Close the
     * SelectAI client when finished; closing the returned connection directly
     * also closes the SDK connection and can make later SDK operations fail.
     * Do not use this method to share one SDK-owned JDBC connection across
     * multiple application threads.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/GetConnectionSample.html">
     * GetConnectionSample source</a>.
     *
     * @return retained JDBC connection for DbConnectionConfig mode
     * @throws IllegalStateException when this client is DataSource-backed
     */
    Connection getConnection();

    /**
     * Creates a {@link Credential} instance from database credential details.
     * <p>
     * A credential is a database object that stores the secret material needed
     * by Autonomous Database to call an AI provider or object storage service.
     * The returned object can create or drop that credential in the current
     * schema.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/CreateCredentialObjectSample.html">
     * CreateCredentialObjectSample source</a>.
     *
     * @param credentialConfig credential definition used for create/drop operations
     * @return credential object initialized with the supplied credential details
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
     *      Select AI prerequisites and credentials</a>
     */
    Credential credential(CredentialConfig credentialConfig);

    /**
     * Opens a {@link Profile} object for an existing AI profile in the current schema.
     * <p>
     * An AI profile stores the provider and AI model configuration and database object
     * metadata that Select AI uses for natural-language SQL, chat, and RAG.
     * "Existing" is not limited to profiles created by this Java SDK; profiles
     * created through PL/SQL, Python, SQL tools, or another application are
     * available when they exist in the same schema and privileges allow access.
     * The returned object can run prompts, inspect metadata, update
     * attributes, enable/disable the profile, or drop it.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/OpenProfileSample.html">
     * OpenProfileSample source</a>.
     *
     * @param profileName profile name
     * @return profile object bound to the specified database profile
     * @throws SelectAIException when the profile cannot be loaded
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
     *      Manage AI profiles</a>
     */
    Profile profile(String profileName) throws SelectAIException;

    /**
     * Creates a {@link Profile} object initialized with profile creation
     * details.
     * <p>
     * This call does not create the profile in the database. Invoke
     * {@link Profile#create()} on the returned object to persist the profile.
     * Passing {@code null} for status keeps the database default behavior
     * during creation.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/CreateProfileWithSelectAISample.html">
     * CreateProfileWithSelectAISample source</a>.
     *
     * @param profileName profile name
     * @param profileAttributes profile attributes payload
     * @param description profile description
     * @param status optional initial profile status
     * @return profile object initialized with the supplied create details
     * @throws SelectAIException when the profile object cannot be initialized
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
     *      Create and set an AI profile</a>
     */
    Profile profile(String profileName, ProfileAttributes profileAttributes,
                    String description, ProfileStatus status)
            throws SelectAIException;

    /**
     * Lists AI profiles visible in the current schema.
     * <p>
     * Listing profiles is a collection-level operation, so it is exposed on the
     * SelectAI client instead of requiring an unbound {@link Profile} object.
     * Each returned object can inspect profile metadata, run prompts, update
     * attributes, enable/disable the profile, or drop the profile.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ListProfilesSample.html">
     * ListProfilesSample source</a>.
     *
     * @return list of profile objects hydrated from database metadata
     * @throws SelectAIException when listing profiles fails
     */
    List<Profile> listProfiles() throws SelectAIException;

    /**
     * Lists AI profiles visible in the current schema whose names match the
     * supplied database regular-expression pattern.
     * <p>
     * Listing profiles is a collection-level operation, so it is exposed on the
     * SelectAI client. The pattern is evaluated by the database metadata query.
     * Use {@link #profile(String)} when opening one literal profile name.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/ListProfilesByPatternSample.html">
     * ListProfilesByPatternSample source</a>.
     *
     * @param profileNamePattern regular-expression pattern matched against profile names
     * @return list of matching profile objects hydrated from database metadata
     * @throws IllegalArgumentException when {@code profileNamePattern} is null or blank
     * @throws SelectAIException when listing profiles fails
     */
    List<Profile> listProfiles(String profileNamePattern) throws SelectAIException;

    /**
     * Opens a {@link VectorIndex} object for an existing vector index in the current schema.
     * <p>
     * A vector index stores embeddings for content that Select AI can retrieve
     * for RAG. The index may have been created by Java, PL/SQL, Python, or
     * another tool; this method only binds a Java object to the database object
     * so it can be inspected, enabled, disabled, updated, or dropped.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/OpenVectorIndexSample.html">
     * OpenVectorIndexSample source</a>.
     *
     * @param indexName vector index name
     * @return vector index object bound to the specified database index
     * @throws SelectAIException when loading the index fails
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-retrieval-augmented-generation.html">
     *      Select AI with Retrieval Augmented Generation</a>
     */
    VectorIndex vectorIndex(String indexName) throws SelectAIException;

    /**
     * Creates a {@link VectorIndex} object initialized from configuration.
     * <p>
     * This call does not create/persist the vector index in the database. Invoke
     * {@link VectorIndex#create()} on the returned object to create it.
     * The configuration describes where source content is located, which
     * credential can read it, and which profile/embedding settings Select AI
     * should use to populate the vector store.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/ConfigureVectorIndexSample.html">
     * ConfigureVectorIndexSample source</a>.
     *
     * @param vectorIndexConfig vector index configuration
     * @return vector index object initialized with the given configuration
     * @throws SelectAIException when vector index object initialization fails
     * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-retrieval-augmented-generation.html">
     *      Select AI RAG and vector indexes</a>
     */
    VectorIndex vectorIndex(VectorIndexConfig vectorIndexConfig) throws SelectAIException;

    /**
     * Lists vector indexes visible in the current schema.
     * <p>
     * Listing vector indexes is a collection-level operation, so it is exposed
     * on the SelectAI client. This method is equivalent to
     * {@link #listVectorIndexes(String)} with {@code .*}.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/ListVectorIndexesSample.html">
     * ListVectorIndexesSample source</a>.
     *
     * @return list of vector index objects hydrated from database metadata
     * @throws SelectAIException when listing vector indexes fails
     */
    List<VectorIndex> listVectorIndexes() throws SelectAIException;

    /**
     * Lists vector indexes visible in the current schema whose names match the
     * supplied database regular-expression pattern.
     * <p>
     * The pattern is evaluated by the database metadata query. Use
     * {@link #vectorIndex(String)} when opening one literal vector-index name.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/vectorindex/ListVectorIndexesSample.html">
     * ListVectorIndexesSample source</a>.
     *
     * @param indexNamePattern regular-expression pattern matched against vector-index names
     * @return list of matching vector index objects hydrated from database metadata
     * @throws SelectAIException when listing vector indexes fails
     */
    List<VectorIndex> listVectorIndexes(String indexNamePattern) throws SelectAIException;

    /**
     * Lists conversations visible in the current schema.
     * <p>
     * Listing conversations is a collection-level operation, so it is exposed
     * on the SelectAI client instead of requiring an unbound
     * {@link Conversation} object.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/ListConversationsSample.html">
     * ListConversationsSample source</a>.
     *
     * @return list of conversation objects hydrated from database metadata
     * @throws SelectAIException when listing conversations fails
     */
    List<Conversation> listConversations() throws SelectAIException;

    /**
     * Creates a {@link Conversation} object initialized with supplied attributes.
     * <p>
     * This call does not persist/create the conversation in the database. Invoke
     * {@link Conversation#create()} on the returned object to create it.
     * Conversations keep chat-style context so later prompts can refer to prior
     * turns.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/ConfigureConversationSample.html">
     * ConfigureConversationSample source</a>.
     *
     * @param conversationAttributes conversation attributes
     * @return conversation object initialized with the given attributes
     * @throws SelectAIException when conversation object initialization fails
     */
    Conversation conversation(ConversationAttributes conversationAttributes) throws SelectAIException;

    /**
     * Opens a {@link Conversation} object for an existing conversation by ID.
     * <p>
     * The returned object can inspect attributes, update retention/title
     * metadata, or drop the conversation.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/selectai/OpenConversationSample.html">
     * OpenConversationSample source</a>.
     *
     * @param conversationId conversation identifier
     * @return conversation object bound to the given ID
     * @throws SelectAIException when the conversation metadata cannot be loaded
     */
    Conversation conversation(String conversationId) throws SelectAIException;
}
