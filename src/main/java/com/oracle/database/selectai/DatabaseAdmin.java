/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.impl.DatabaseAdminFactory;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;

import javax.sql.DataSource;
import java.util.List;

/**
 * Administrative API contract for privileged database setup operations used by
 * Select AI.
 * <p>
 * Normal application code should use {@link SelectAI}. Use
 * {@code DatabaseAdmin} only from setup or administrative workflows that run
 * with database users authorized to change package privileges, data-access
 * settings, or network ACL entries.
 */
public interface DatabaseAdmin extends AutoCloseable {
    /**
     * Creates a DatabaseAdmin client from database connection configuration.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/GrantPrivilegesSample.html">
     * GrantPrivilegesSample source</a>.
     *
     * @param dbConnectionConfig database connection configuration
     * @return DatabaseAdmin client
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the database connection cannot be initialized
     */
    static DatabaseAdmin create(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        return DatabaseAdminFactory.create(dbConnectionConfig);
    }

    /**
     * Creates a DatabaseAdmin client from database connection configuration and
     * SDK execution options.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/CreateDatabaseAdminWithOptionsSample.html">
     * CreateDatabaseAdminWithOptionsSample source</a>.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options; {@code null} uses {@link SelectAIOptions#defaults()}
     * @return DatabaseAdmin client
     * @throws IllegalArgumentException when {@code dbConnectionConfig} is null
     * @throws SelectAIException when the database connection cannot be initialized
     */
    static DatabaseAdmin create(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        return DatabaseAdminFactory.create(dbConnectionConfig, options);
    }

    /**
     * Creates a DataSource-backed DatabaseAdmin client.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/CreateDatabaseAdminDataSourceSample.html">
     * CreateDatabaseAdminDataSourceSample source</a>.
     *
     * @param dataSource DataSource used to obtain JDBC connections
     * @return DatabaseAdmin client
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    static DatabaseAdmin create(DataSource dataSource) {
        return DatabaseAdminFactory.create(dataSource);
    }

    /**
     * Creates a DataSource-backed DatabaseAdmin client with SDK execution options.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/CreateDatabaseAdminDataSourceWithOptionsSample.html">
     * CreateDatabaseAdminDataSourceWithOptionsSample source</a>.
     *
     * @param dataSource DataSource used to obtain JDBC connections
     * @param options SDK execution options; {@code null} uses {@link SelectAIOptions#defaults()}
     * @return DatabaseAdmin client
     * @throws IllegalArgumentException when {@code dataSource} is null
     */
    static DatabaseAdmin create(DataSource dataSource, SelectAIOptions options) {
        return DatabaseAdminFactory.create(dataSource, options);
    }

    /**
     * Allows Select AI features to send table data or vector-search document
     * content to the model when a feature needs that data.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/EnableSelectAIDataAccessSample.html">
     * EnableSelectAIDataAccessSample source</a>.
     *
     * @return {@code true} when the database accepts the enable request
     * @throws SelectAIException when the database rejects or cannot execute the enable request
     */
    boolean enableDataAccess() throws SelectAIException;

    /**
     * Prevents Select AI features from sending table data or vector-search
     * document content to the model when those features would otherwise use it.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/DisableSelectAIDataAccessSample.html">
     * DisableSelectAIDataAccessSample source</a>.
     *
     * @return {@code true} when the database accepts the disable request
     * @throws SelectAIException when the database rejects or cannot execute the disable request
     */
    boolean disableDataAccess() throws SelectAIException;

    /**
     * Grants required Select AI package privileges to the specified database users.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/GrantPrivilegesSample.html">
     * GrantPrivilegesSample source</a>.
     *
     * @param selectAIUsers database users whose package privileges are granted
     * @return {@code false} when the user list is null or empty; otherwise
     *         {@code true} when the grant requests complete
     * @throws SelectAIException when package grants cannot be applied
     */
    boolean grantPrivileges(List<String> selectAIUsers) throws SelectAIException;

    /**
     * Revokes required Select AI package privileges from the specified database users.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/RevokePrivilegesSample.html">
     * RevokePrivilegesSample source</a>.
     *
     * @param selectAIUsers database users whose package privileges are revoked
     * @return {@code false} when the user list is null or empty; otherwise
     *         {@code true} when the revoke requests complete
     * @throws SelectAIException when package grants cannot be revoked
     */
    boolean revokePrivileges(List<String> selectAIUsers) throws SelectAIException;

    /**
     * Grants HTTP network ACL access for the specified database users.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/GrantHttpAccessSample.html">
     * GrantHttpAccessSample source</a>.
     *
     * @param selectAIUsers database users whose HTTP access is granted
     * @param host host name or host pattern to add to the ACL
     * @return {@code false} when the user list is null or empty; otherwise
     *         {@code true} when the grant requests complete
     * @throws IllegalArgumentException when {@code host} is null or blank
     * @throws SelectAIException when network ACL updates cannot be applied
     */
    boolean grantHttpAccess(List<String> selectAIUsers, String host) throws SelectAIException;

    /**
     * Revokes HTTP network ACL access for the specified database users.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/RevokeHttpAccessSample.html">
     * RevokeHttpAccessSample source</a>.
     *
     * @param selectAIUsers database users whose HTTP access is revoked
     * @param host host name or host pattern to remove from the ACL
     * @return {@code false} when the user list is null or empty; otherwise
     *         {@code true} when the revoke requests complete
     * @throws IllegalArgumentException when {@code host} is null or blank
     * @throws SelectAIException when network ACL updates cannot be revoked
     */
    boolean revokeHttpAccess(List<String> selectAIUsers, String host) throws SelectAIException;

    /**
     * Grants network ACL privileges to the specified database users.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/GrantNetworkAccessSample.html">
     * GrantNetworkAccessSample source</a>.
     *
     * @param selectAIUsers database users whose network access is granted
     * @param host host name or host pattern to add to the ACL
     * @param privileges network ACL privileges to grant, such as {@code http} or {@code connect}
     * @param lowerPort optional lower port; pass {@code null} when not needed
     * @param upperPort optional upper port; pass {@code null} when not needed
     * @return {@code false} when the user list is null or empty; otherwise
     *         {@code true} when the grant requests complete
     * @throws IllegalArgumentException when {@code host} is null or blank,
     *         {@code privileges} has no non-blank values, a port is outside
     *         {@code 0..65535}, or {@code lowerPort} is greater than {@code upperPort}
     * @throws SelectAIException when network ACL updates cannot be applied
     */
    boolean grantNetworkAccess(List<String> selectAIUsers, String host,
                               List<String> privileges, Integer lowerPort,
                               Integer upperPort) throws SelectAIException;

    /**
     * Revokes network ACL privileges for the specified database users.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/RevokeNetworkAccessSample.html">
     * RevokeNetworkAccessSample source</a>.
     *
     * @param selectAIUsers database users whose network access is revoked
     * @param host host name or host pattern to remove from the ACL
     * @param privileges network ACL privileges to revoke, such as {@code http} or {@code connect}
     * @param lowerPort optional lower port; pass {@code null} when not needed
     * @param upperPort optional upper port; pass {@code null} when not needed
     * @return {@code false} when the user list is null or empty; otherwise
     *         {@code true} when the revoke requests complete
     * @throws IllegalArgumentException when {@code host} is null or blank,
     *         {@code privileges} has no non-blank values, a port is outside
     *         {@code 0..65535}, or {@code lowerPort} is greater than {@code upperPort}
     * @throws SelectAIException when network ACL updates cannot be revoked
     */
    boolean revokeNetworkAccess(List<String> selectAIUsers, String host,
                                List<String> privileges, Integer lowerPort,
                                Integer upperPort) throws SelectAIException;

    /**
     * Releases resources owned by this DatabaseAdmin client.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/databaseadmin/CloseDatabaseAdminSample.html">
     * CloseDatabaseAdminSample source</a>.
     *
     * @throws SelectAIException when an owned JDBC connection cannot be closed
     */
    @Override
    void close() throws SelectAIException;
}
