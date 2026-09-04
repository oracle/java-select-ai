/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.privilege;

import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.SelectAIException;
import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live integration coverage for the Java privilege and network-ACL API.
 *
 * <p>The shared database configuration must identify a user with the privileges
 * required by these tests. The DatabaseAdmin client performs network ACL and
 * package-privilege changes, while the shared Select AI connection is used for
 * SQL verification and temporary-user cleanup.</p>
 */
class PrivilegeIT extends IntegrationTestFixture {

    private static final int TEST_PORT = 587;
    private static final List<String> TEST_PRIVILEGES = List.of("connect", "smtp");
    private static final List<String> SELECT_AI_PACKAGES = List.of(
            "DBMS_CLOUD", "DBMS_CLOUD_AI", "DBMS_CLOUD_AI_AGENT", "DBMS_CLOUD_PIPELINE");
    private DatabaseAdmin databaseAdmin;

    @Override
    protected boolean requiresProfile() {
        return false;
    }

    @BeforeEach
    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        databaseAdmin = DatabaseAdmin.create(dbConfig);
    }

    @AfterEach
    void closeDatabaseAdmin() throws SelectAIException {
        if (databaseAdmin != null) {
            databaseAdmin.close();
            databaseAdmin = null;
        }
    }

    /**
     * Test: Generates a unique host name and grants the configured database user
     * the {@code CONNECT} and {@code SMTP} privileges on that host for port 587
     * through 587. It then queries {@code DBA_HOST_ACES} for the uppercase target
     * principal, exact host, privilege name, and lower port.
     * Expected: {@code grantNetworkAccess} returns {@code true}, and
     * {@code DBA_HOST_ACES} contains one matching CONNECT ACE and one matching
     * SMTP ACE. If the grant completed, the test revokes the same host privileges
     * in {@code finally}.
     */
    @Test
    void test11500GrantNetworkAccess() throws Exception {
        String host = uniqueHost();
        String principal = targetPrincipal();
        boolean granted = false;
        try {
            assertThat(databaseAdmin.grantNetworkAccess(
                    List.of(targetUser()), host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT))
                    .isTrue();
            granted = true;

            assertThat(networkAceExists(host, principal, "CONNECT", TEST_PORT)).isTrue();
            assertThat(networkAceExists(host, principal, "SMTP", TEST_PORT)).isTrue();
        } finally {
            if (granted) {
                cleanupNetworkAccess(host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT);
            }
        }
    }

    /**
     * Test: Grants the configured database user {@code CONNECT} and {@code SMTP}
     * access to a unique host on port 587, then revokes the identical host ACL
     * entries. After the revoke, it checks {@code DBA_HOST_ACES} for the target
     * principal and both privilege names.
     * Expected: Both grant and revoke return {@code true}; the database query
     * finds neither the CONNECT ACE nor the SMTP ACE after revocation. If revoke
     * fails, cleanup retries the revoke so the unique host entries are removed.
     */
    @Test
    void test11501RevokeNetworkAccess() throws Exception {
        String host = uniqueHost();
        String principal = targetPrincipal();
        boolean granted = false;
        boolean revoked = false;
        try {
            assertThat(databaseAdmin.grantNetworkAccess(
                    List.of(targetUser()), host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT))
                    .isTrue();
            granted = true;

            assertThat(databaseAdmin.revokeNetworkAccess(
                    List.of(targetUser()), host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT))
                    .isTrue();
            revoked = true;

            assertThat(networkAceExists(host, principal, "CONNECT", TEST_PORT)).isFalse();
            assertThat(networkAceExists(host, principal, "SMTP", TEST_PORT)).isFalse();
        } finally {
            if (granted && !revoked) {
                cleanupNetworkAccess(host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT);
            }
        }
    }

    /**
     * Test: Uses the convenience methods for a unique host: grants HTTP access to
     * the configured database user, verifies the HTTP ACE in {@code DBA_HOST_ACES}
     * with no port restriction, then revokes HTTP access and queries the same ACE
     * again.
     * Expected: Both convenience calls return {@code true}; the HTTP ACE exists
     * after {@code grantHttpAccess} and does not exist after
     * {@code revokeHttpAccess}. A failed revoke is retried during cleanup.
     */
    @Test
    void test11502JavaHttpAccessConvenienceMethodsRoundTripNetworkAcl() throws Exception {
        String host = uniqueHost();
        String principal = targetPrincipal();
        boolean granted = false;
        boolean revoked = false;
        try {
            assertThat(databaseAdmin.grantHttpAccess(List.of(targetUser()), host)).isTrue();
            granted = true;
            assertThat(networkAceExists(host, principal, "HTTP", null)).isTrue();

            assertThat(databaseAdmin.revokeHttpAccess(List.of(targetUser()), host)).isTrue();
            revoked = true;
            assertThat(networkAceExists(host, principal, "HTTP", null)).isFalse();
        } finally {
            if (granted && !revoked) {
                cleanupNetworkAccess(host, List.of("http"), null, null);
            }
        }
    }

    /**
     * Test: Creates two unique database users, each initially with no EXECUTE
     * grant on {@code DBMS_CLOUD}, {@code DBMS_CLOUD_AI},
     * {@code DBMS_CLOUD_AI_AGENT}, or {@code DBMS_CLOUD_PIPELINE}. It grants the
     * Select AI package privileges to both users, verifies each package through
     * the {@code DBA_TAB_PRIVS}/{@code DBA_SYNONYMS} lookup, revokes privileges
     * only from the first user, and repeats the lookup for both users.
     * Expected: The grant returns {@code true} and all four packages are visible
     * for both users; the revoke returns {@code true}, removes all four package
     * grants from the first user, and leaves all four grants for the second user.
     * Finally, both users' privileges are revoked and both users are dropped with
     * {@code CASCADE}.
     */
    @Test
    void test11503GrantAndRevokeSelectAIPackagePrivilegesForIndependentUsers() throws Exception {
        String firstUser = uniqueDatabaseUser();
        String secondUser = uniqueDatabaseUser();
        try {
            createDatabaseUser(firstUser);
            createDatabaseUser(secondUser);

            for (String packageName : SELECT_AI_PACKAGES) {
                assertThat(hasExecuteGrant(firstUser, packageName)).isFalse();
                assertThat(hasExecuteGrant(secondUser, packageName)).isFalse();
            }

            assertThat(databaseAdmin.grantPrivileges(List.of(firstUser, secondUser))).isTrue();
            for (String packageName : SELECT_AI_PACKAGES) {
                assertThat(hasExecuteGrant(firstUser, packageName)).isTrue();
                assertThat(hasExecuteGrant(secondUser, packageName)).isTrue();
            }

            assertThat(databaseAdmin.revokePrivileges(List.of(firstUser))).isTrue();
            for (String packageName : SELECT_AI_PACKAGES) {
                assertThat(hasExecuteGrant(firstUser, packageName)).isFalse();
                assertThat(hasExecuteGrant(secondUser, packageName)).isTrue();
            }
        } finally {
            try {
                databaseAdmin.revokePrivileges(List.of(firstUser, secondUser));
            } catch (Exception ignored) {
                // Preserve the primary test failure; user cleanup follows.
            }
            dropDatabaseUser(firstUser);
            dropDatabaseUser(secondUser);
        }
    }

    /**
     * Test: Creates a unique database user and a {@link OracleDataSource} from the
     * shared JDBC URL and credentials, then creates a separate
     * {@code DatabaseAdmin} backed by that data source. Through that client it
     * grants and revokes all four Select AI package EXECUTE privileges, grants
     * and revokes CONNECT/SMTP access for a unique host on port 587, and grants
     * and revokes unrestricted HTTP access. Each database change is checked with
     * the package-grant query or the {@code DBA_HOST_ACES} query.
     * Expected: Every grant and revoke returns {@code true}; all four package
     * grants appear after granting and disappear after revoking; CONNECT and
     * SMTP ACEs appear after the network grant and CONNECT is absent after the
     * network revoke; and the HTTP ACE appears after the HTTP grant and is absent
     * after the HTTP revoke. Any partially completed state is cleaned up before
     * the temporary user is dropped.
     */
    @Test
    void test11504DataSourceBackedAdminSupportsPrivilegeAndAclOperations() throws Exception {
        String user = uniqueDatabaseUser();
        String host = uniqueHost();
        OracleDataSource dataSource = oracleDataSource();
        boolean userCreated = false;
        boolean packagePrivilegesGranted = false;
        boolean networkAccessGranted = false;
        boolean httpAccessGranted = false;

        try (DatabaseAdmin dataSourceAdmin = DatabaseAdmin.create(dataSource)) {
            createDatabaseUser(user);
            userCreated = true;
            try {
                assertThat(dataSourceAdmin.grantPrivileges(List.of(user))).isTrue();
                packagePrivilegesGranted = true;
                for (String packageName : SELECT_AI_PACKAGES) {
                    assertThat(hasExecuteGrant(user, packageName)).isTrue();
                }

                assertThat(dataSourceAdmin.revokePrivileges(List.of(user))).isTrue();
                packagePrivilegesGranted = false;
                for (String packageName : SELECT_AI_PACKAGES) {
                    assertThat(hasExecuteGrant(user, packageName)).isFalse();
                }

                assertThat(dataSourceAdmin.grantNetworkAccess(
                        List.of(user), host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT))
                        .isTrue();
                networkAccessGranted = true;
                assertThat(networkAceExists(host, user.toUpperCase(Locale.ROOT),
                        "CONNECT", TEST_PORT)).isTrue();
                assertThat(networkAceExists(host, user.toUpperCase(Locale.ROOT),
                        "SMTP", TEST_PORT)).isTrue();

                assertThat(dataSourceAdmin.revokeNetworkAccess(
                        List.of(user), host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT))
                        .isTrue();
                networkAccessGranted = false;
                assertThat(networkAceExists(host, user.toUpperCase(Locale.ROOT),
                        "CONNECT", TEST_PORT)).isFalse();

                assertThat(dataSourceAdmin.grantHttpAccess(List.of(user), host)).isTrue();
                httpAccessGranted = true;
                assertThat(networkAceExists(host, user.toUpperCase(Locale.ROOT),
                        "HTTP", null)).isTrue();

                assertThat(dataSourceAdmin.revokeHttpAccess(List.of(user), host)).isTrue();
                httpAccessGranted = false;
                assertThat(networkAceExists(host, user.toUpperCase(Locale.ROOT),
                        "HTTP", null)).isFalse();
            } finally {
                if (httpAccessGranted) {
                    try {
                        dataSourceAdmin.revokeHttpAccess(List.of(user), host);
                    } catch (Exception ignored) {
                        // Preserve the primary test failure.
                    }
                }
                if (networkAccessGranted) {
                    try {
                        dataSourceAdmin.revokeNetworkAccess(
                                List.of(user), host, TEST_PRIVILEGES, TEST_PORT, TEST_PORT);
                    } catch (Exception ignored) {
                        // Preserve the primary test failure.
                    }
                }
                if (packagePrivilegesGranted) {
                    try {
                        dataSourceAdmin.revokePrivileges(List.of(user));
                    } catch (Exception ignored) {
                        // Preserve the primary test failure.
                    }
                }
                if (userCreated) {
                    dropDatabaseUser(user);
                }
            }
        }
    }

    /**
     * Test: Creates a temporary user, grants it only the normal Select AI package
     * privileges, and opens a {@code DatabaseAdmin} connection as that user. It
     * attempts database-wide enable/disable operations, package grant/revoke,
     * network grant/revoke, and HTTP grant/revoke using a unique host and port
     * 587. Each attempt is checked for its JDBC-backed exception code.
     * Expected: All eight administrative calls fail with {@code SelectAIException}:
     * enable/disable data access produce a SQL cause containing {@code ORA-20000},
     * package grant/revoke produce {@code ORA-01749}, and network or HTTP
     * grant/revoke produce {@code ORA-06550}. The restricted client is closed,
     * any unexpected ACL or package changes are revoked through the admin client,
     * the temporary user is dropped, and database-wide data access is re-enabled
     * during cleanup.
     */
    @Test
    void test11505NonAdminWithSelectAIPackagePrivilegesCannotUseDatabaseAdminOperations() throws Exception {
        String restrictedUser = uniqueDatabaseUser();
        String restrictedPassword = null;
        String host = uniqueHost();
        DatabaseAdmin restrictedAdmin = null;
        boolean userCreated = false;
        boolean packagePrivilegesGranted = false;

        try {
            restrictedPassword = createDatabaseUser(restrictedUser);
            userCreated = true;

            // Grant only the normal Select AI package privileges. This must not
            // give the user authority to perform DatabaseAdmin operations.
            assertThat(databaseAdmin.grantPrivileges(List.of(restrictedUser))).isTrue();
            packagePrivilegesGranted = true;

            final DatabaseAdmin restrictedClient = DatabaseAdmin.create(
                    connectionConfigFor(restrictedUser, restrictedPassword));
            restrictedAdmin = restrictedClient;

            assertThatThrownBy(restrictedClient::enableDataAccess)
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-20000:"));
            assertThatThrownBy(restrictedClient::disableDataAccess)
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-20000:"));
            assertThatThrownBy(() -> restrictedClient.grantPrivileges(List.of(restrictedUser)))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-01749:"));
            assertThatThrownBy(() -> restrictedClient.revokePrivileges(List.of(restrictedUser)))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-01749:"));
            assertThatThrownBy(() -> restrictedClient.grantNetworkAccess(
                    List.of(restrictedUser), host, List.of("connect"), TEST_PORT, TEST_PORT))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-06550:"));
            assertThatThrownBy(() -> restrictedClient.revokeNetworkAccess(
                    List.of(restrictedUser), host, List.of("connect"), TEST_PORT, TEST_PORT))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-06550:"));
            assertThatThrownBy(() -> restrictedClient.grantHttpAccess(
                    List.of(restrictedUser), host))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-06550:"));
            assertThatThrownBy(() -> restrictedClient.revokeHttpAccess(
                    List.of(restrictedUser), host))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-06550:"));
        } finally {
            if (restrictedAdmin != null) {
                try {
                    restrictedAdmin.close();
                } catch (Exception ignored) {
                    // Preserve the primary test failure; cleanup continues.
                }
            }
            try {
                // These are safe cleanup calls for the unique test host and
                // also restore state if a restricted operation unexpectedly succeeds.
                databaseAdmin.revokeHttpAccess(List.of(restrictedUser), host);
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            }
            try {
                databaseAdmin.revokeNetworkAccess(
                        List.of(restrictedUser), host, List.of("connect"), TEST_PORT, TEST_PORT);
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            }
            if (packagePrivilegesGranted) {
                try {
                    databaseAdmin.revokePrivileges(List.of(restrictedUser));
                } catch (Exception ignored) {
                    // Preserve the primary test failure; cleanup is best effort.
                }
            }
            if (userCreated) {
                dropDatabaseUser(restrictedUser);
            }
            try {
                // ENABLE_DATA_ACCESS is database-wide. Restore the normal state
                // if a negative assertion exposes unexpected permission.
                databaseAdmin.enableDataAccess();
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            }
        }
    }

    private String targetUser() {
        assumeTrue(dbConfig != null, "The shared database configuration is not initialized.");
        return dbConfig.getDbUser();
    }

    private String targetPrincipal() {
        return targetUser().toUpperCase(Locale.ROOT);
    }

    private static String uniqueHost() {
        return "jsai-" + UUID.randomUUID().toString().replace("-", "") + ".example.com";
    }

    private static String uniqueDatabaseUser() {
        return "JSAI_PRIV_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private OracleDataSource oracleDataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(dbConfig.getJdbcUrl());
        dataSource.setUser(dbConfig.getDbUser());
        dataSource.setPassword(dbConfig.getDbPassword());
        return dataSource;
    }

    private String createDatabaseUser(String username) throws SQLException {
        String password = "JsaI" + UUID.randomUUID().toString().replace("-", "") + "Aa1";
        try (Statement statement = jdbcConnection().createStatement()) {
            statement.executeUpdate(
                    "CREATE USER " + username + " IDENTIFIED BY \"" + password + "\"");
            statement.executeUpdate("GRANT CREATE SESSION TO " + username);
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1031) {
                assumeTrue(false,
                        "Privilege integration test requires CREATE USER privilege: "
                                + exception.getMessage());
            }
            throw exception;
        }
        return password;
    }

    private void dropDatabaseUser(String username) {
        try (Statement statement = jdbcConnection().createStatement()) {
            statement.executeUpdate("DROP USER " + username + " CASCADE");
        } catch (SQLException ignored) {
            // Preserve the primary test failure; cleanup is best effort.
        }
    }

    private boolean hasExecuteGrant(String username, String packageName) throws SQLException {
        try (PreparedStatement statement = jdbcConnection().prepareStatement(
                "SELECT GRANTEE, OWNER, TABLE_NAME, PRIVILEGE "
                        + "FROM DBA_TAB_PRIVS "
                        + "WHERE GRANTEE = ? "
                        + "AND PRIVILEGE = 'EXECUTE' "
                        + "AND (UPPER(TABLE_NAME) = UPPER(?) "
                        + "OR EXISTS ("
                        + "SELECT 1 FROM DBA_SYNONYMS s "
                        + "WHERE UPPER(s.SYNONYM_NAME) = UPPER(?) "
                        + "AND s.TABLE_OWNER = DBA_TAB_PRIVS.OWNER "
                        + "AND s.TABLE_NAME = DBA_TAB_PRIVS.TABLE_NAME)) "
                        + "ORDER BY GRANTEE, OWNER, TABLE_NAME, PRIVILEGE")) {
            statement.setString(1, username);
            statement.setString(2, packageName);
            statement.setString(3, packageName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean networkAceExists(String host, String principal,
                                     String privilege, Integer lowerPort) throws SQLException {
        String sql = "SELECT COUNT(*) FROM DBA_HOST_ACES "
                + "WHERE host = ? AND principal = ? AND privilege = ?"
                + (lowerPort == null ? " AND lower_port IS NULL" : " AND lower_port = ?");
        try (PreparedStatement statement = jdbcConnection().prepareStatement(sql)) {
            statement.setString(1, host);
            statement.setString(2, principal);
            statement.setString(3, privilege);
            if (lowerPort != null) {
                statement.setInt(4, lowerPort);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void cleanupNetworkAccess(String host, List<String> privileges,
                                      Integer lowerPort, Integer upperPort) {
        try {
            databaseAdmin.revokeNetworkAccess(
                List.of(targetUser()), host, privileges, lowerPort, upperPort);
        } catch (SelectAIException | RuntimeException ignored) {
            // Preserve the primary test failure; the host is unique to this test.
        }
    }

}
