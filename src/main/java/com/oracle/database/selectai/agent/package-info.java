/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

/**
 * Public synchronous interfaces for Oracle Select AI Agent resource management
 * and workflow execution.
 * <p>
 * Obtain a client through the factory methods on {@link SelectAIAgent}.
 * {@link Agent}, {@link Task}, {@link Tool}, and {@link Team} expose lifecycle
 * and attribute operations backed by {@code DBMS_CLOUD_AI_AGENT}. The client
 * also supports resource listing, team execution and state management,
 * runtime variables, object definitions, and team import. Tool execution and
 * team description and export operations are available on resource handles.
 * Profiles, credentials, and conversations are managed separately through
 * {@link com.oracle.database.selectai.SelectAI}; configuring an agent resource
 * does not create its referenced resources.
 *
 * <h2>Resource ownership and discovery</h2>
 * Name-only factories load existing resources owned by the connected database
 * user, including their metadata and attributes. Factories that accept
 * attributes configure a local handle; its {@code create()} method persists
 * the resource. Attribute updates reload the database attributes after the
 * update succeeds.
 * <p>
 * {@link SelectAIAgent#listTeams()} returns owner-scoped {@link Team} handles.
 * {@link SelectAIAgent#listAvailableTeams()} instead returns read-only
 * {@link com.oracle.database.selectai.model.agent.AvailableTeam} discovery
 * objects, which may include shared teams. Owners grant or revoke team access
 * through {@code Team.grantAccess}/{@code revokeAccess} or the corresponding
 * client methods. Consumers use owner-qualified names with the client's team
 * execution and state methods without opening an owner-only handle. Database
 * authorization still applies; execution access does not confer ownership.
 *
 * <h2>Connections and conversations</h2>
 * A client created from {@link com.oracle.database.selectai.model.DbConnectionConfig}
 * owns one JDBC connection and should be closed with try-with-resources.
 * A client created from {@link javax.sql.DataSource} borrows and closes a
 * connection for each database operation; it does not own the data source.
 * A synchronous team run can occupy that connection until the call completes.
 * Single-connection clients require serialized access. With a suitable
 * concurrent data source, independent operations can use separate connections;
 * concurrent mutation of the same resource handle requires caller-side
 * synchronization.
 * <p>
 * Preserve the identifier returned by
 * {@link com.oracle.database.selectai.Conversation#create()} or obtained from
 * an existing conversation, and reuse it for team runs, continuation, and state
 * operations. State queries and updates require an explicit non-blank
 * conversation ID. Pooled calls may use different sessions, so applications
 * must not assume that uncommitted state or session-scoped runtime variables
 * remain available to a later call.
 *
 * <h2>Validation and failures</h2>
 * Java argument checks use {@link java.lang.IllegalArgumentException}; operations
 * requiring a different resource lifecycle state use
 * {@link java.lang.IllegalStateException}. Database, JDBC, and metadata-mapping
 * failures are reported through
 * {@link com.oracle.database.selectai.model.SelectAIException}. Attribute
 * semantics and access privileges are validated by the database.
 *
 * @see SelectAIAgent
 * @see com.oracle.database.selectai.model.agent
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/AgentWorkflowSample.html">
 *      AgentWorkflowSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ListAvailableTeamsSample.html">
 *      ListAvailableTeamsSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/GrantTeamAccessSample.html">
 *      GrantTeamAccessSample source</a>
 * @since 1.1.0
 */
package com.oracle.database.selectai.agent;
