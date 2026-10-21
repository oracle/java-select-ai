/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

/**
 * Internal JDBC implementations of the Select AI agent interfaces.
 * <p>
 * Classes in this package are implementation details, not supported public API,
 * and may change without notice. Application code should use the interfaces and
 * factory methods in {@link com.oracle.database.selectai.agent.SelectAIAgent}.
 * {@link SelectAIAgentFactory} exists only to bridge the public interface to
 * package-private implementations; it is not an application entry point.
 * <p>
 * {@link DefaultSelectAIAgent}, {@link DefaultAgent}, {@link DefaultTask},
 * {@link DefaultTool}, and {@link DefaultTeam} implement resource lifecycle,
 * attribute updates, team sharing, execution, state, discovery, runtime-variable,
 * and definition operations. {@link AgentSql} centralizes SQL and PL/SQL call
 * templates. {@link AgentJdbc} handles bind variables, Oracle PL/SQL BOOLEAN
 * binding, CLOB input/output, JSON conversion, and typed metadata mapping.
 * <p>
 * Resource loading and owner-scoped listing use user metadata views. Available
 * team discovery uses {@code DBMS_CLOUD_AI_AGENT.LIST_TEAMS} and produces
 * read-only discovery models. Owner-qualified team execution and state calls
 * do not first load an owner-only team handle. Database authorization remains
 * authoritative for every operation.
 * <p>
 * Successful attribute updates reload the database attributes using the same
 * operation connection. Empty attribute-query results trigger a resource
 * existence check so deleted resources are not returned as empty configurations.
 * Status reads refresh status only; they do not change the description snapshot.
 * <p>
 * Shared connection providers apply
 * {@link com.oracle.database.selectai.model.SelectAIOptions} and handle the
 * SDK-owned single-connection or caller-owned {@link javax.sql.DataSource}
 * lifecycle. Statements and result sets are closed after use. Returned CLOB
 * streams are closed and their JDBC locators are freed, including on read
 * failure; cleanup failures are retained alongside the original failure. Data-source
 * connections are released at the end of each operation. The implementation
 * does not explicitly commit or roll back, and does not automatically retry
 * failed operations, including failures while refreshing attributes after an
 * update. JDBC failures are translated to
 * {@link com.oracle.database.selectai.model.SelectAIException} while preserving
 * their cause, Oracle error code, and SQL state.
 *
 * @see com.oracle.database.selectai.agent
 * @see com.oracle.database.selectai.model.agent
 * @since 1.1.0
 */
package com.oracle.database.selectai.impl.agent;
