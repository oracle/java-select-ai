/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.connection;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live connection and JDBC integration coverage.
 *
 * <p>The public Java surface is the synchronous JDBC connection exposed by
 * {@link SelectAI#getConnection()}. The shared fixture reads
 * {@code SELECT_AI_IT_*} environment variables and skips this suite when live
 * configuration is absent.</p>
 *
 * <p>{@code test10100} opens its own wallet-derived JDBC URL from
 * {@code SELECT_AI_IT_DB_NAME} and {@code SELECT_AI_IT_WALLET_LOCATION}.
 * Privileged scenarios and temporary local-user scenarios use
 * {@code SELECT_AI_IT_DB_USER} and {@code SELECT_AI_IT_DB_PASSWORD}.</p>
 */
class ConnectionIT extends IntegrationTestFixture {

    private static final String INVALID_JDBC_URL = "jdbc:oracle:thin:@invalid_dsn";

    @Override
    protected boolean requiresProfile() {
        return false;
    }

    /**
     * Test: Builds a wallet-derived configuration from the configured database name and wallet
     * location, opens the connection, validates it for five seconds, and executes
     * {@code SELECT 1 FROM DUAL}.
     * Expected: The connection is open and valid, the query returns one row whose integer value
     * is {@code 1}, and the wallet-backed client is closed in the {@code finally} block.
     */
    @Test
    void test10100ConnectionSuccessWithWallet() throws Exception {
        SelectAI walletClient = null;
        try {
            walletClient = SelectAI.create(walletConnectionConfig());
            Connection connection = walletClient.getConnection();

            assertThat(connection.isClosed()).isFalse();
            assertThat(connection.isValid(5)).isTrue();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1 FROM DUAL")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        } finally {
            closeConnection(walletClient);
        }
    }

    /**
     * Test: Builds a walletless configuration from {@code SELECT_AI_IT_DB_URL}, opens the
     * configured user connection, and checks its JDBC state.
     * Expected: {@code isValid(5)} returns {@code true} and {@code isClosed()} returns
     * {@code false}; the client is closed in the {@code finally} block.
     */
    @Test
    void test10101ConnectionWithoutWallet() throws Exception {
        SelectAI noWalletClient = null;
        try {
            noWalletClient = SelectAI.create(noWalletConnectionConfig());
            Connection connection = noWalletClient.getConnection();
            assertThat(connection.isValid(5)).isTrue();
            assertThat(connection.isClosed()).isFalse();
        } finally {
            closeConnection(noWalletClient);
        }
    }

    /**
     * Test: Obtains the fixture JDBC connection, records its open state, closes it, and checks
     * the state again.
     * Expected: {@code isClosed()} is {@code false} before {@code close()} and {@code true}
     * afterward.
     */
    @Test
    void test10102ConnectionStateIsBoolean() throws Exception {
        Connection connection = jdbcConnection();

        assertThat(connection.isClosed()).isFalse();
        connection.close();
        assertThat(connection.isClosed()).isTrue();
    }

    /**
     * Test: Creates a client for the configured database user with the literal password
     * {@code wrong_pass}.
     * Expected: Client creation throws {@link SelectAIException} with a {@link SQLException}
     * cause containing {@code ORA-01017:}, indicating that the database rejected the password.
     */
    @Test
    void test10103ConnectionRejectsWrongPassword() {
        assertThatThrownBy(() -> SelectAI.create(connectionConfigWithCredentials(
                dbConfig.getDbUser(), "wrong_pass")))
                .isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("ORA-01017:"));
    }

    /**
     * Test: Creates a client with the deliberately invalid URL
     * {@code jdbc:oracle:thin:@invalid_dsn} and the configured username and password.
     * Expected: Client creation throws {@link SelectAIException} with a {@link SQLException}
     * cause containing {@code ORA-17868:}.
     */
    @Test
    void test10104ConnectionRejectsBadJdbcUrl() {
        assertThatThrownBy(() -> SelectAI.create(connectionConfigWithUrl(INVALID_JDBC_URL)))
                .isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("ORA-17868:"));
    }

    /**
     * Test: Creates a client for the configured database user with the configured password plus
     * one extra {@code X} character.
     * Expected: Client creation throws {@link SelectAIException} with a {@link SQLException}
     * cause containing {@code ORA-01017:}.
     */
    @Test
    void test10105ConnectionRejectsBadPassword() {
        assertThatThrownBy(() -> SelectAI.create(connectionConfigWithCredentials(
                dbConfig.getDbUser(), dbConfig.getDbPassword() + "X")))
                .isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("ORA-01017:"));
    }

    /**
     * Test: Creates a statement on the fixture connection and executes
     * {@code SELECT 1 FROM DUAL}.
     * Expected: The result set contains a row and its first column is the integer {@code 1}.
     */
    @Test
    void test10106ExecutesSimpleQuery() throws Exception {
        try (Statement statement = jdbcConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1 FROM DUAL")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }

    /**
     * Test: Prepares {@code SELECT ? FROM DUAL}, binds the integer {@code 42} to parameter one,
     * and executes the statement.
     * Expected: The result set contains a row whose first column is the bound integer
     * {@code 42}.
     */
    @Test
    void test10107ExecutesParameterizedQuery() throws Exception {
        try (PreparedStatement statement = jdbcConnection().prepareStatement("SELECT ? FROM DUAL")) {
            statement.setInt(1, 42);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(42);
            }
        }
    }

    /**
     * Test: Executes {@code SELECT LEVEL FROM DUAL CONNECT BY LEVEL <= 5} and counts rows while
     * iterating through the result set.
     * Expected: Exactly five rows are visited.
     */
    @Test
    void test10108FetchesAllRows() throws Exception {
        int rows = 0;
        try (Statement statement = jdbcConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT LEVEL FROM DUAL CONNECT BY LEVEL <= 5")) {
            while (resultSet.next()) {
                rows++;
            }
        }
        assertThat(rows).isEqualTo(5);
    }

    /**
     * Test: Executes {@code SELECT * FROM NON_EXISTENT_TABLE} on a live JDBC statement.
     * Expected: JDBC throws {@link SQLException} because the referenced table does not exist.
     */
    @Test
    void test10109RejectsInvalidQuery() {
        assertThatThrownBy(() -> {
            try (Statement statement = jdbcConnection().createStatement()) {
                statement.executeQuery("SELECT * FROM NON_EXISTENT_TABLE");
            }
        }).isInstanceOf(SQLException.class);
    }

    /**
     * Test: Creates a uniquely named {@code (id NUMBER)} table, disables auto-commit, truncates
     * it, inserts {@code id = 1}, and rolls the transaction back before counting rows.
     * Expected: The post-rollback count is {@code 0}; auto-commit is restored and the unique
     * table is dropped in cleanup.
     */
    @Test
    void test10110CommitsAndRollsBack() throws Exception {
        String tableName = uniqueIdentifier("JSAI_IT_CR_");
        Connection connection = jdbcConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + tableName + " (id NUMBER)");
            connection.setAutoCommit(false);
            statement.execute("TRUNCATE TABLE " + tableName);
            statement.execute("INSERT INTO " + tableName + " (id) VALUES (1)");
            connection.rollback();
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT COUNT(*) FROM " + tableName)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }
        } finally {
            connection.setAutoCommit(true);
            dropTable(connection, tableName);
        }
    }

    /**
     * Test: Closes the fixture JDBC connection and then calls {@code createStatement()} on that
     * closed connection.
     * Expected: JDBC throws {@link SQLException} instead of creating a statement.
     */
    @Test
    void test10111RejectsUseOfClosedConnection() throws Exception {
        Connection connection = jdbcConnection();
        connection.close();

        assertThatThrownBy(connection::createStatement)
                .isInstanceOf(SQLException.class);
    }

    /**
     * Test: Calls {@code close()} twice on the same fixture JDBC connection and then reads its
     * state.
     * Expected: The second close does not throw and {@code isClosed()} remains {@code true}.
     */
    @Test
    void test10112AllowsRepeatedClose() throws Exception {
        Connection connection = jdbcConnection();
        connection.close();
        connection.close();

        assertThat(connection.isClosed()).isTrue();
    }

    /**
     * Test: Enables {@code DBMS_OUTPUT}, sends the exact text
     * {@code Testing DBMS_OUTPUT package} with {@code DBMS_OUTPUT.PUT_LINE}, and retrieves one
     * line with {@code DBMS_OUTPUT.GET_LINE}.
     * Expected: The returned status is {@code 0} and the returned line exactly equals
     * {@code Testing DBMS_OUTPUT package}.
     */
    @Test
    void test10113UsesDbmsOutput() throws Exception {
        String expected = "Testing DBMS_OUTPUT package";
        Connection connection = jdbcConnection();
        try (CallableStatement enable = connection.prepareCall(
                "BEGIN DBMS_OUTPUT.ENABLE(NULL); END;")) {
            enable.execute();
        }
        try (CallableStatement putLine = connection.prepareCall(
                "BEGIN DBMS_OUTPUT.PUT_LINE(?); END;")) {
            putLine.setString(1, expected);
            putLine.execute();
        }
        try (CallableStatement getLine = connection.prepareCall(
                "BEGIN DBMS_OUTPUT.GET_LINE(?, ?); END;")) {
            getLine.registerOutParameter(1, Types.VARCHAR);
            getLine.registerOutParameter(2, Types.INTEGER);
            getLine.execute();

            assertThat(getLine.getInt(2)).isZero();
            assertThat(getLine.getString(1)).isEqualTo(expected);
        }
    }

    /**
     * Test: Executes {@code SELECT UPPER(SYS_CONTEXT('USERENV', 'INSTANCE_NAME')) FROM DUAL}.
     * Expected: The query returns a row whose instance-name value is not blank.
     */
    @Test
    void test10114RetrievesInstanceName() throws Exception {
        try (Statement statement = jdbcConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT UPPER(SYS_CONTEXT('USERENV', 'INSTANCE_NAME')) FROM DUAL")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isNotBlank();
        }
    }

    /**
     * Test: Reconnects with the configured admin credentials and queries
     * {@code V$PARAMETER} for the {@code open_cursors} value.
     * Expected: A matching row exists and the returned maximum-open-cursors value is greater
     * than {@code 0}.
     */
    @Test
    void test10115RetrievesMaxOpenCursors() throws Exception {
        switchToAdminConnection();
        try (Statement statement = jdbcConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT VALUE FROM V$PARAMETER WHERE NAME = 'open_cursors'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isGreaterThan(0);
        }
    }

    /**
     * Test: Executes {@code SELECT SYS_CONTEXT('USERENV', 'SERVICE_NAME') FROM DUAL}.
     * Expected: The query returns a row whose service-name value is not blank.
     */
    @Test
    void test10116RetrievesServiceName() throws Exception {
        try (Statement statement = jdbcConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT SYS_CONTEXT('USERENV', 'SERVICE_NAME') FROM DUAL")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isNotBlank();
        }
    }

    /**
     * Test: Uses the configured admin account to create a unique user with
     * {@code CREATE SESSION}, {@code CREATE TABLE}, and {@code UNLIMITED TABLESPACE}, connects
     * as that user, creates a unique {@code (id NUMBER)} table, inserts {@code 100}, and reads
     * it back.
     * Expected: The new user can connect, create the table, and read back {@code id = 100}; the
     * table and user are dropped in the {@code finally} block.
     */
    @Test
    void test10117CreatesUserAndTable() throws Exception {
        String adminUser = dbConfig.getDbUser();
        String adminPassword = dbConfig.getDbPassword();
        String localPassword = dbConfig.getDbPassword();
        String username = uniqueIdentifier("JSAI_IT_USER_");
        String tableName = uniqueIdentifier("JSAI_IT_TBL_");

        SelectAI adminClient = null;
        SelectAI localClient = null;
        boolean userCreated = false;

        try {
            closeConnection(selectAI);

            adminClient = SelectAI.create(
                    connectionConfigWithCredentials(adminUser, adminPassword));

            createUser(
                    adminClient.getConnection(),
                    username,
                    localPassword);
            userCreated = true;

            localClient = SelectAI.create(
                    connectionConfigWithCredentials(username, localPassword));

            try (Statement statement =
                         localClient.getConnection().createStatement()) {
                statement.execute("CREATE TABLE " + tableName + " (id NUMBER)");
                statement.execute("INSERT INTO " + tableName + " (id) VALUES (100)");

                try (ResultSet resultSet =
                             statement.executeQuery("SELECT id FROM " + tableName)) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(100);
                }
            }
        } finally {
            if (localClient != null) {
                dropTable(localClient.getConnection(), tableName);
            }

            closeConnection(localClient);

            if (userCreated && adminClient != null) {
                dropUser(adminClient.getConnection(), username);
            }

            closeConnection(adminClient);
        }
    }

    /**
     * Test: Builds a {@link SelectAI} client from an {@link OracleDataSource}, verifies
     * that the direct connection accessor is unavailable without a {@code DbConnectionConfig},
     * and then lists profiles through the DataSource-backed client.
     * Expected: {@code getConnection()} throws {@link IllegalStateException} mentioning
     * {@code DbConnectionConfig}, while {@code listProfiles()} returns a non-null list.
     */
    @Test
    void test10118DataSourceBackedClientUsesDatabase() throws Exception {
        OracleDataSource dataSource = oracleDataSource();
        SelectAI dataSourceClient = SelectAI.create(dataSource);

        assertThatThrownBy(dataSourceClient::getConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DbConnectionConfig");
        assertThat(dataSourceClient.listProfiles()).isNotNull();
    }

    /**
     * Test: Configures an {@link OracleDataSource} with the real JDBC URL and username but a
     * password formed by appending {@code X} to the configured password, then builds a
     * DataSource-backed client.
     * Expected: {@code getConnection()} throws {@link IllegalStateException} mentioning
     * {@code DbConnectionConfig}; the first database operation, {@code listProfiles()}, throws
     * {@link SelectAIException} whose SQL cause contains {@code ORA-01017:}.
     */
    @Test
    void test10119DataSourceConnectionFailureIsDeferred() throws Exception {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(dbConfig.getJdbcUrl());
        dataSource.setUser(dbConfig.getDbUser());
        dataSource.setPassword(dbConfig.getDbPassword() + "X");
        SelectAI dataSourceClient = SelectAI.create(dataSource);

        assertThatThrownBy(dataSourceClient::getConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DbConnectionConfig");
        assertThatThrownBy(dataSourceClient::listProfiles)
                .isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("ORA-01017:"));
    }

    /**
     * Test: Reads {@code SELECT_AI_IT_DB_URL}, builds {@link DbConnectionConfig} with that exact
     * URL and the configured credentials, and executes {@code SELECT 1 FROM DUAL} through the
     * resulting client.
     * Expected: The config retains the exact URL, the query returns integer {@code 1}, and the
     * client is closed in the {@code finally} block.
     */
    @Test
    void test10120ExplicitJdbcUrlIsUsed() throws Exception {
        String explicitUrl = requiredValue(
                "SELECT_AI_IT_DB_URL",
                "Explicit JDBC URL requires SELECT_AI_IT_DB_URL in the environment.");
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser(dbConfig.getDbUser())
                .dbPassword(dbConfig.getDbPassword())
                .jdbcUrl(explicitUrl)
                .build();
        SelectAI client = null;
        try {
            assertThat(config.getJdbcUrl()).isEqualTo(explicitUrl);
            client = SelectAI.create(config);
            try (Statement statement = client.getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1 FROM DUAL")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        } finally {
            closeConnection(client);
        }
    }

    /**
     * Test: Executes {@code SELECT 1 FROM DUAL} inside a try-with-resources block containing a
     * JDBC {@link Statement} and {@link ResultSet}, while retaining references to both objects
     * after the block.
     * Expected: The query returns a row and both retained JDBC objects report
     * {@code isClosed() == true} after scope exit.
     */
    @Test
    void test10121TryWithResourcesClosesJdbcResources() throws Exception {
        Statement statement;
        ResultSet resultSet;

        try (Statement resource = jdbcConnection().createStatement();
             ResultSet rows = resource.executeQuery("SELECT 1 FROM DUAL")) {
            statement = resource;
            resultSet = rows;
            assertThat(rows.next()).isTrue();
        }

        assertThat(statement.isClosed()).isTrue();
        assertThat(resultSet.isClosed()).isTrue();
    }

    /**
     * Test: Reads the configured wallet password, builds a wallet configuration containing that
     * password, opens the wallet-backed connection, and validates it for five seconds.
     * Expected: The connection remains open and {@code isValid(5)} returns {@code true}; the
     * wallet-backed client is closed in the {@code finally} block.
     */
    @Test
    void test10122PasswordProtectedWalletConnection() throws Exception {
        String walletPassword = requiredFeatureValue(
                "SELECT_AI_IT_WALLET_PASSWORD",
                "Password-protected wallet coverage requires a wallet password");
        SelectAI walletClient = null;
        try {
            walletClient = SelectAI.create(passwordProtectedWalletConnectionConfig(walletPassword));
            Connection connection = walletClient.getConnection();

            assertThat(connection.isClosed()).isFalse();
            assertThat(connection.isValid(5)).isTrue();
        } finally {
            closeConnection(walletClient);
        }
    }

    /**
     * Test: Uses an admin {@link OracleDataSource} to create a unique local user, connects to a
     * second DataSource as that user, creates a unique {@code (id NUMBER)} table, inserts
     * {@code 100}, and selects it back.
     * Expected: The DataSource-backed admin client rejects direct {@code getConnection()} with
     * an {@link IllegalStateException} mentioning {@code DbConnectionConfig}, but
     * {@code listProfiles()} succeeds; the local user reads {@code id = 100}, and the table and
     * user connections/resources are cleaned up.
     */
    @Test
    void test10123DataSourceCreatesUserAndTable() throws Exception {
        String localPassword = dbConfig.getDbPassword();
        String username = uniqueIdentifier("JSAI_IT_DS_USER_");
        String tableName = uniqueIdentifier("JSAI_IT_DS_TBL_");

        OracleDataSource adminDataSource = oracleDataSource();
        SelectAI dataSourceClient = SelectAI.create(adminDataSource);
        Connection adminConnection = null;
        Connection localConnection = null;
        boolean userCreated = false;

        try {
            assertThatThrownBy(dataSourceClient::getConnection)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DbConnectionConfig");
            assertThat(dataSourceClient.listProfiles()).isNotNull();

            adminConnection = adminDataSource.getConnection();
            createUser(adminConnection, username, localPassword);
            userCreated = true;

            OracleDataSource localDataSource = new OracleDataSource();
            localDataSource.setURL(dbConfig.getJdbcUrl());
            localDataSource.setUser(username);
            localDataSource.setPassword(localPassword);
            localConnection = localDataSource.getConnection();

            try (Statement statement = localConnection.createStatement()) {
                statement.execute("CREATE TABLE " + tableName + " (id NUMBER)");
                statement.execute("INSERT INTO " + tableName + " (id) VALUES (100)");

                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT id FROM " + tableName)) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(100);
                }
            }
        } finally {
            if (localConnection != null) {
                dropTable(localConnection, tableName);
                localConnection.close();
            }
            if (userCreated && adminConnection != null) {
                dropUser(adminConnection, username);
            }
            if (adminConnection != null) {
                adminConnection.close();
            }
        }
    }



    /**
     * Test: Obtains and retains the client connection, closes the {@link SelectAI} client,
     * retrieves the connection again, attempts {@code SELECT 1 FROM DUAL}, and then calls
     * {@code listProfiles()}.
     * Expected: The original connection is closed, the accessor returns that same closed object,
     * the SQL attempt throws {@link SQLException}, and {@code listProfiles()} throws
     * {@link SelectAIException} with a cause containing {@code ORA-17008:}; closing does not
     * reopen the connection.
     */
    @Test
    void test10124CloseClosesAndDoesNotReopenRetainedConnection() throws Exception {
        SelectAI client = SelectAI.create(dbConfig);
        Connection connection = client.getConnection();

        assertThat(connection.isClosed()).isFalse();

        client.close();

        assertThat(connection.isClosed()).isTrue();
        assertThat(client.getConnection()).isSameAs(connection);
        assertThatThrownBy(() -> {
            try (Statement statement = client.getConnection().createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1 FROM DUAL")) {
                assertThat(resultSet.next()).isTrue();
            }
        }).isInstanceOf(SQLException.class);
        assertThat(connection.isClosed()).isTrue();
        assertThatThrownBy(client::listProfiles)
                .isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("ORA-17008:"));
    }

    /**
     * Test: Opens a {@link SelectAI} client in try-with-resources, obtains its connection,
     * and checks the connection both inside and after the resource scope.
     * Expected: The connection is open inside the scope and reports {@code isClosed() == true}
     * after the client is automatically closed.
     */
    @Test
    void test10125TryWithResourcesClosesSelectAIConnection() throws Exception {
        Connection connection;

        try (SelectAI client = SelectAI.create(dbConfig)) {
            connection = client.getConnection();
            assertThat(connection.isClosed()).isFalse();
        }

        assertThat(connection.isClosed()).isTrue();
    }

    // /**
    //  * Test: Attempts to open the configured wallet with the configured wallet password plus
    //  * {@code _INVALID}.
    //  * Expected: Construction should throw {@link SelectAIException} with a {@link SQLException}
    //  * cause rather than returning a usable connection.
    //  *
    //  * This remains commented because auto-login wallets (cwallet.sso) ignore the wallet
    //  * password, so the test is environment-dependent and does not fail consistently.
    //  */
    // @Test
    // void test10126PasswordProtectedWalletRejectsWrongPassword() {
    //     String walletPassword = requiredFeatureValue(
    //             "SELECT_AI_IT_WALLET_PASSWORD",
    //             "Password-protected wallet coverage requires a wallet password");
    //
    //     assertThatThrownBy(() -> SelectAI.create(
    //             passwordProtectedWalletConnectionConfig(walletPassword + "_INVALID")))
    //             .isInstanceOf(SelectAIException.class)
    //             .hasCauseInstanceOf(SQLException.class);
    // }


    private OracleDataSource oracleDataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(dbConfig.getJdbcUrl());
        dataSource.setUser(dbConfig.getDbUser());
        dataSource.setPassword(dbConfig.getDbPassword());
        return dataSource;
    }

    private DbConnectionConfig connectionConfigWithCredentials(String user, String password) {
        return DbConnectionConfig.builder()
                .dbUser(user)
                .dbPassword(password)
                .jdbcUrl(dbConfig.getJdbcUrl())
                .build();
    }

    private DbConnectionConfig connectionConfigWithUrl(String url) {
        return DbConnectionConfig.builder()
                .dbUser(dbConfig.getDbUser())
                .dbPassword(dbConfig.getDbPassword())
                .jdbcUrl(url)
                .build();
    }

    private DbConnectionConfig walletConnectionConfig() {
        String walletLocation = requiredWalletValue("SELECT_AI_IT_WALLET_LOCATION");
        String dbName = requiredWalletValue("SELECT_AI_IT_DB_NAME");
        return DbConnectionConfig.builder()
                .dbUser(dbConfig.getDbUser())
                .dbPassword(dbConfig.getDbPassword())
                .jdbcUrl(walletJdbcUrl(dbName, walletLocation))
                .build();
    }

    private DbConnectionConfig passwordProtectedWalletConnectionConfig(String walletPassword) {
        String walletLocation = requiredWalletValue("SELECT_AI_IT_WALLET_LOCATION");
        String dbName = requiredWalletValue("SELECT_AI_IT_DB_NAME");
        return DbConnectionConfig.builder()
                .dbUser(dbConfig.getDbUser())
                .dbPassword(dbConfig.getDbPassword())
                .walletPassword(walletPassword)
                .jdbcUrl(walletJdbcUrl(dbName, walletLocation))
                .build();
    }

    private DbConnectionConfig noWalletConnectionConfig() {
        String jdbcUrl = requiredValue("SELECT_AI_IT_DB_URL",
                "No-wallet connection requires SELECT_AI_IT_DB_URL in the environment.");
        if (jdbcUrl.contains("TNS_ADMIN=")) {
            throw new IllegalStateException(
                    "SELECT_AI_IT_DB_URL must be a walletless JDBC URL for the no-wallet scenario.");
        }
        return DbConnectionConfig.builder()
                .dbUser(dbConfig.getDbUser())
                .dbPassword(dbConfig.getDbPassword())
                .jdbcUrl(jdbcUrl)
                .build();
    }

    private static String walletJdbcUrl(String dbName, String walletLocation) {
        return "jdbc:oracle:thin:@" + dbName + "_high?TNS_ADMIN=" + walletLocation;
    }

    private String requiredWalletValue(String name) {
        String value = env(name);
        if (value == null) {
            throw new IllegalStateException(
                    "Wallet-based connection requires " + name + " in the environment.");
        }
        return value;
    }

    private String requiredValue(String name, String message) {
        String value = env(name);
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private void switchToAdminConnection() throws SelectAIException, SQLException {
        String adminUser = dbConfig.getDbUser();
        String adminPassword = dbConfig.getDbPassword();
        closeConnection(selectAI);
        selectAI = SelectAI.create(connectionConfigWithCredentials(adminUser, adminPassword));
    }

    private static void createUser(Connection connection, String username, String password) throws SQLException {
        String escapedPassword = password.replace("\"", "\"\"");
        boolean created = false;
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE USER " + username + " IDENTIFIED BY \"" + escapedPassword + "\"");
            created = true;
            statement.execute("GRANT CREATE SESSION, CREATE TABLE, UNLIMITED TABLESPACE TO " + username);
        } catch (SQLException exception) {
            if (created) {
                dropUser(connection, username);
            }
            throw exception;
        }
    }

    private static void dropUser(Connection connection, String username) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP USER " + username + " CASCADE");
        } catch (SQLException ignored) {
            // Preserve the primary failure; the user name is unique to this test.
        }
    }

    private static void dropTable(Connection connection, String tableName) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + tableName + " PURGE");
        } catch (SQLException ignored) {
            // Preserve the primary failure; the table name is unique to this test.
        }
    }

    private static void closeConnection(SelectAI client) throws SelectAIException {
        if (client != null) {
            client.close();
        }
    }

    private static String uniqueIdentifier(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase();
    }
}
