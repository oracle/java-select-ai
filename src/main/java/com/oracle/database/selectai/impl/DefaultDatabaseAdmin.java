/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Default {@link DatabaseAdmin} implementation for privileged Select AI
 * database setup operations.
 * <p>
 * This implementation executes administrative DBMS_CLOUD_AI and
 * DBMS_NETWORK_ACL_ADMIN operations such as package privilege grants,
 * data-access changes, and network ACL updates. Use it only from setup or
 * administrative workflows with appropriately privileged database users.
 */
final class DefaultDatabaseAdmin implements DatabaseAdmin {
    /** Logger for administrative database operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAdmin.class);

    /** Database connection wrapper, available only for DbConnectionConfig mode. */
    private final DbConnection dbConnection;
    /** Provider that controls connection acquisition and release for admin operations. */
    private final ConnectionProvider connectionProvider;

    /**
     * Creates a DatabaseAdmin instance from database connection configuration.
     *
     * @param dbConnectionConfig database connection configuration
     * @throws SelectAIException when initialization fails
     */
    DefaultDatabaseAdmin(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        this(dbConnectionConfig, SelectAIOptions.defaults());
    }

    /**
     * Creates a DatabaseAdmin instance from database connection configuration
     * and SDK execution options.
     *
     * @param dbConnectionConfig database connection configuration
     * @param options SDK execution options
     * @throws SelectAIException when initialization fails
     */
    DefaultDatabaseAdmin(DbConnectionConfig dbConnectionConfig, SelectAIOptions options)
            throws SelectAIException {
        this(createDbConnection(dbConnectionConfig), options);
    }

    /**
     * Creates a DataSource-backed DatabaseAdmin instance.
     *
     * @param dataSource DataSource used to obtain per-operation connections
     */
    DefaultDatabaseAdmin(DataSource dataSource) {
        this(dataSource, SelectAIOptions.defaults());
    }

    /**
     * Creates a DataSource-backed DatabaseAdmin instance with SDK execution options.
     *
     * @param dataSource DataSource used to obtain per-operation connections
     * @param options SDK execution options
     */
    DefaultDatabaseAdmin(DataSource dataSource, SelectAIOptions options) {
        this(null, new DataSourceConnectionProvider(dataSource), options);
    }

    DefaultDatabaseAdmin(DbConnection dbConnection, SelectAIOptions options) {
        this(dbConnection, new SingleConnectionProvider(dbConnection), options);
    }

    DefaultDatabaseAdmin(ConnectionProvider connectionProvider) {
        this(null, connectionProvider, SelectAIOptions.defaults());
    }

    DefaultDatabaseAdmin(ConnectionProvider connectionProvider, SelectAIOptions options) {
        this(null, connectionProvider, options);
    }

    private DefaultDatabaseAdmin(DbConnection dbConnection,
                                 ConnectionProvider connectionProvider,
                                 SelectAIOptions options) {
        if (connectionProvider == null) {
            LOGGER.error("ConnectionProvider cannot be null when initializing DatabaseAdmin");
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (dbConnection == null) {
            LOGGER.info("DatabaseAdmin initialized with DataSource-backed connection provider");
        } else {
            LOGGER.info("DatabaseAdmin initialized with SDK-owned JDBC connection");
        }
        this.dbConnection = dbConnection;
        this.connectionProvider = new JdbcExecutionConnectionProvider(connectionProvider, options);
    }

    private static DbConnection createDbConnection(DbConnectionConfig dbConnectionConfig) throws SelectAIException {
        if (dbConnectionConfig == null) {
            LOGGER.error("DbConnectionConfig cannot be null when initializing DatabaseAdmin");
            throw new IllegalArgumentException("dbConnectionConfig must not be null");
        }
        LOGGER.debug("Initializing DatabaseAdmin with user {}", dbConnectionConfig.getDbUser());
        return DefaultDbConnection.builder().dbUser(dbConnectionConfig.getDbUser())
                .dbPassword(dbConnectionConfig.getDbPassword())
                .walletPassword(dbConnectionConfig.getWalletPassword())
                .jdbcUrl(dbConnectionConfig.getJdbcUrl())
                .jdbcProperties(dbConnectionConfig.getJdbcProperties()).build();
    }

    /**
     * Releases resources owned by this DatabaseAdmin client.
     *
     * @throws SelectAIException when an owned JDBC connection cannot be closed
     */
    @Override
    public void close() throws SelectAIException {
        if (dbConnection != null) {
            dbConnection.close();
        }
    }

    /**
     * Allows Select AI operations that need table data or vector-search document
     * content to send that data to the model.
     *
     * @return {@code true} when enable call executes successfully
     * @throws SelectAIException when {@code DBMS_CLOUD_AI.ENABLE_DATA_ACCESS} fails
     */
    @Override
    public boolean enableDataAccess() throws SelectAIException {
        String plsql = Sql.ENABLE_DATA_ACCESS.get();
        LOGGER.debug("Enabling data access via DBMS_CLOUD_AI.ENABLE_DATA_ACCESS");
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.execute();
                    LOGGER.info("Successfully enabled data access");
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.ENABLE_DATA_ACCESS failed", e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.ENABLE_DATA_ACCESS", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Prevents Select AI operations from sending table data or vector-search
     * document content to the model.
     *
     * @return {@code true} when disable call executes successfully
     * @throws SelectAIException when {@code DBMS_CLOUD_AI.DISABLE_DATA_ACCESS} fails
     */
    @Override
    public boolean disableDataAccess() throws SelectAIException {
        String plsql = Sql.DISABLE_DATA_ACCESS.get();
        LOGGER.debug("Disabling data access via DBMS_CLOUD_AI.DISABLE_DATA_ACCESS");
        try {
            connectionProvider.withConnection(connection -> {
                try (CallableStatement stmt = connection.prepareCall(plsql)) {
                    stmt.execute();
                    LOGGER.info("Successfully disabled data access");
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD_AI.DISABLE_DATA_ACCESS failed", e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD_AI.DISABLE_DATA_ACCESS", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Revokes required Select AI package privileges from specified users.
     *
     * @param selectAIUsers database users whose package grants are revoked
     * @return {@code false} when user list is empty; otherwise {@code true}
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean revokePrivileges(List<String> selectAIUsers) throws SelectAIException {
        if (selectAIUsers == null || selectAIUsers.isEmpty()) {
            LOGGER.warn("revokePrivileges called with empty user list");
            return false;
        }

        final String revokeSql = Sql.REVOKE_SELECT_AI_PACKAGE_PRIVILEGES.get();

        try {
            int revokedCount = connectionProvider.withConnection(connection -> {
                int count = 0;
                for (String selectAIUser : selectAIUsers) {
                    if (selectAIUser == null || selectAIUser.isBlank()) {
                        LOGGER.warn("Skipping blank SelectAI user entry when revoking privileges");
                        continue;
                    }

                    try (CallableStatement revokeStmt = connection.prepareCall(revokeSql)) {
                        revokeStmt.setString("user", selectAIUser);
                        revokeStmt.execute();
                        count++;
                        LOGGER.debug("Revoked SelectAI package privileges for user {}", selectAIUser);
                    }
                }
                return count;
            });
            LOGGER.info("Revoked SelectAI package privileges for {} user(s)", revokedCount);
        } catch (SQLException e) {
            LOGGER.error("Failed to revoke Select AI privileges for {} user(s)", selectAIUsers.size(), e);
            throw new SelectAIException("Failed to revoke Select AI privileges", e, e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Grants required Select AI package privileges to specified users.
     *
     * @param selectAIUsers database users receiving package grants
     * @return {@code false} when user list is empty; otherwise {@code true}
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean grantPrivileges(List<String> selectAIUsers) throws SelectAIException {
        if (selectAIUsers == null || selectAIUsers.isEmpty()) {
            LOGGER.warn("grantPrivileges called with empty user list");
            return false;
        }

        final String grantSql = Sql.GRANT_SELECT_AI_PACKAGE_PRIVILEGES.get();

        try {
            int grantedCount = connectionProvider.withConnection(connection -> {
                int count = 0;
                for (String selectAIUser : selectAIUsers) {
                    if (selectAIUser == null || selectAIUser.isBlank()) {
                        LOGGER.warn("Skipping blank SelectAI user entry when granting privileges");
                        continue;
                    }

                    try (CallableStatement grantStmt = connection.prepareCall(grantSql)) {
                        grantStmt.setString("user", selectAIUser);
                        grantStmt.execute();
                        count++;
                        LOGGER.debug("Granted SelectAI package privileges for user {}", selectAIUser);
                    }
                }
                return count;
            });
            LOGGER.info("Granted SelectAI package privileges for {} user(s)", grantedCount);
        } catch (SQLException e) {
            LOGGER.error("Failed to grant Select AI privileges for {} user(s)", selectAIUsers.size(), e);
            throw new SelectAIException("Failed to grant Select AI privileges", e, e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Revokes network ACL privileges for specified users and host.
     *
     * @param selectAIUsers database users whose ACL entries are removed
     * @param host network host or wildcard host pattern
     * @param privileges ACL privilege names to revoke
     * @param lowerPort optional lower TCP port bound
     * @param upperPort optional upper TCP port bound
     * @return {@code false} when user list is empty; otherwise {@code true}
     * @throws IllegalArgumentException when host, privileges, or ports are invalid
     * @throws SelectAIException when the database ACL operation fails
     */
    @Override
    public boolean revokeNetworkAccess(List<String> selectAIUsers, String host,
                                       List<String> privileges, Integer lowerPort,
                                       Integer upperPort) throws SelectAIException {
        return updateNetworkAccess(false, selectAIUsers, host, privileges, lowerPort, upperPort);
    }

    /**
     * Grants network ACL privileges for specified users and host.
     *
     * @param selectAIUsers database users receiving ACL entries
     * @param host network host or wildcard host pattern
     * @param privileges ACL privilege names to grant
     * @param lowerPort optional lower TCP port bound
     * @param upperPort optional upper TCP port bound
     * @return {@code false} when user list is empty; otherwise {@code true}
     * @throws IllegalArgumentException when host, privileges, or ports are invalid
     * @throws SelectAIException when the database ACL operation fails
     */
    @Override
    public boolean grantNetworkAccess(List<String> selectAIUsers, String host,
                                      List<String> privileges, Integer lowerPort,
                                      Integer upperPort) throws SelectAIException {
        return updateNetworkAccess(true, selectAIUsers, host, privileges, lowerPort, upperPort);
    }

    private boolean updateNetworkAccess(boolean grant, List<String> selectAIUsers,
                                        String host, List<String> privileges,
                                        Integer lowerPort, Integer upperPort)
            throws SelectAIException {
        if (selectAIUsers == null || selectAIUsers.isEmpty()) {
            LOGGER.warn("{}NetworkAccess called with empty user list for host {}",
                    grant ? "grant" : "revoke", host);
            return false;
        }
        validateNetworkAccessRequest(host, privileges, lowerPort, upperPort);

        List<String> normalizedPrivileges = normalizePrivileges(privileges);
        final String aclSql = networkAccessSql(grant, normalizedPrivileges.size());
        final String action = grant ? "grant" : "revoke";
        final String pastAction = grant ? "Granted" : "Revoked";

        try {
            int updatedCount = connectionProvider.withConnection(connection -> {
                int count = 0;
                for (String selectAIUser : selectAIUsers) {
                    if (selectAIUser == null || selectAIUser.isBlank()) {
                        LOGGER.warn("Skipping blank SelectAI user entry when {}ing network access for {}", action, host);
                        continue;
                    }

                    try (CallableStatement aclStmt = connection.prepareCall(aclSql)) {
                        bindNetworkAccessStatement(aclStmt, selectAIUser.trim(), host.trim(),
                                normalizedPrivileges, lowerPort, upperPort);
                        aclStmt.execute();
                        count++;
                        LOGGER.debug("{} ACL host ACE for user {} on host {} with privileges {}",
                                pastAction, selectAIUser, host, normalizedPrivileges);
                    }
                }
                return count;
            });
            LOGGER.info("{} ACL host ACE for {} user(s) with {} privilege(s)",
                    pastAction, updatedCount, normalizedPrivileges.size());
        } catch (SQLException e) {
            LOGGER.error("Failed to {} network access for {} user(s)", action, selectAIUsers.size(), e);
            throw new SelectAIException("Failed to " + action + " network access",
                    e, e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    private static void validateNetworkAccessRequest(String host, List<String> privileges,
                                                     Integer lowerPort, Integer upperPort) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        if (privileges == null || privileges.isEmpty()) {
            throw new IllegalArgumentException("privileges must contain at least one value");
        }
        validatePort("lowerPort", lowerPort);
        validatePort("upperPort", upperPort);
        if (lowerPort != null && upperPort != null && lowerPort > upperPort) {
            throw new IllegalArgumentException("lowerPort must be less than or equal to upperPort");
        }
    }

    private static void validatePort(String name, Integer port) {
        if (port != null && (port < 0 || port > 65535)) {
            throw new IllegalArgumentException(name + " must be between 0 and 65535");
        }
    }

    private static List<String> normalizePrivileges(List<String> privileges) {
        List<String> normalizedPrivileges = privileges.stream()
                .filter(privilege -> privilege != null && !privilege.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(ArrayList::new));
        if (normalizedPrivileges.isEmpty()) {
            throw new IllegalArgumentException("privileges must contain at least one non-blank value");
        }
        return List.copyOf(normalizedPrivileges);
    }

    private static String networkAccessSql(boolean grant, int privilegeCount) {
        String privilegeBinds = IntStream.range(0, privilegeCount)
                .mapToObj(index -> "?")
                .collect(Collectors.joining(", "));
        return (grant ? Sql.GRANT_NETWORK_ACCESS_ACL : Sql.REVOKE_NETWORK_ACCESS_ACL).format(privilegeBinds);
    }

    private static void bindNetworkAccessStatement(CallableStatement statement,
                                                   String user,
                                                   String host,
                                                   List<String> privileges,
                                                   Integer lowerPort,
                                                   Integer upperPort)
            throws SQLException {
        int bindIndex = 1;
        statement.setString(bindIndex++, host);
        bindNullablePort(statement, bindIndex++, lowerPort);
        bindNullablePort(statement, bindIndex++, upperPort);
        for (String privilege : privileges) {
            statement.setString(bindIndex++, privilege);
        }
        statement.setString(bindIndex, user);
    }

    private static void bindNullablePort(CallableStatement statement, int bindIndex, Integer port)
            throws SQLException {
        if (port == null) {
            statement.setNull(bindIndex, Types.INTEGER);
        } else {
            statement.setInt(bindIndex, port);
        }
    }

    /**
     * Revokes HTTP network ACL access for specified users and host.
     *
     * @param selectAIUsers database users whose HTTP ACL entries are removed
     * @param host network host or wildcard host pattern
     * @return {@code false} when user list is empty; otherwise {@code true}
     * @throws IllegalArgumentException when host is invalid
     * @throws SelectAIException when the database ACL operation fails
     */
    @Override
    public boolean revokeHttpAccess(List<String> selectAIUsers, String host) throws SelectAIException {
        return revokeNetworkAccess(selectAIUsers, host, List.of("http"), null, null);
    }

    /**
     * Grants HTTP network ACL access for specified users and host.
     *
     * @param selectAIUsers database users receiving HTTP ACL entries
     * @param host network host or wildcard host pattern
     * @return {@code false} when user list is empty; otherwise {@code true}
     * @throws IllegalArgumentException when host is invalid
     * @throws SelectAIException when the database ACL operation fails
     */
    @Override
    public boolean grantHttpAccess(List<String> selectAIUsers, String host) throws SelectAIException {
        return grantNetworkAccess(selectAIUsers, host, List.of("http"), null, null);
    }
}
