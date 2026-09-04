/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import oracle.jdbc.OracleConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDbConnectionTest {

    @Mock
    private Connection connection;
    private static final String VALID_TEST_JDBC_URL = "jdbc:oracle:thin:@test";

    /**
     * Test: Verifies the implementation builder remains package-internal.
     * Expected: DefaultDbConnection has no public builder() method and its Builder type is
     * not public.
     */
    @Test
    void builderIsInternalImplementationApi() {
        assertThat(Modifier.isPublic(DefaultDbConnection.class.getModifiers())).isFalse();
        assertThatThrownBy(() -> DefaultDbConnection.class.getMethod("builder"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThat(Modifier.isPublic(DefaultDbConnection.Builder.class.getModifiers())).isFalse();
    }

    /**
     * Test: Build a database connection through the public DbConnection factory.
     * Expected: The factory returns an initialized connection without exposing
     * DefaultDbConnection as public API.
     */
    @Test
    void publicFactoryCreatesConnection() throws Exception {
        TestDriver driver = new TestDriver(connection, null);
        registerDriver(driver);
        try {
            DbConnection dbConnection = DbConnection.create(DbConnectionConfig.builder()
                    .jdbcUrl(VALID_TEST_JDBC_URL)
                    .dbUser("admin")
                    .dbPassword("secret")
                    .build());

            assertThat(dbConnection.getJdbcUrl()).isEqualTo(VALID_TEST_JDBC_URL);
            assertThat(dbConnection.getDbUser()).isEqualTo("admin");
            assertThat(dbConnection.getConnection()).isSameAs(connection);
            assertThat(driver.connectionCalls).isEqualTo(1);
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Call the public DbConnection factory with a null config.
     * Expected: IllegalArgumentException identifies the missing configuration.
     */
    @Test
    void publicFactoryRejectsNullConfig() {
        assertThatThrownBy(() -> DbConnection.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbConnectionConfig");
    }

    /**
     * Test: Build a database connection and expose its configured non-secret values.
     * Expected: The JDBC URL, user, and created connection are available to callers.
     */
    @Test
    void builderCreatesConnectionAndExposesConfiguredValues() throws Exception {
        TestDriver driver = new TestDriver(connection, null);
        registerDriver(driver);
        try {
            DbConnection dbConnection = DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("secret")
                    .build();

            assertThat(dbConnection.getJdbcUrl()).isEqualTo("jdbc:test");
            assertThat(dbConnection.getDbUser()).isEqualTo("admin");
            assertThat(dbConnection.getConnection()).isSameAs(connection);
            assertThat(driver.connectionCalls).isEqualTo(1);
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Validate required URL and user fields before opening JDBC.
     * Expected: Missing required fields raise IllegalArgumentException.
     */
    @Test
    void builderValidatesRequiredFieldsBeforeOpeningJdbcConnection() {
        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .dbUser("admin")
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");

        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl("jdbc:test")
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");
    }

    /**
     * Test: Reject null or blank database usernames.
     * Expected: Invalid usernames raise IllegalArgumentException before JDBC access.
     */
    @Test
    void builderRejectsNullOrBlankUsernameBeforeOpeningJdbcConnection() {
        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl("jdbc:test")
                .dbUser(null)
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");

        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl("jdbc:test")
                .dbUser(" ")
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");
    }

    /**
     * Test: Reject null or blank JDBC URLs.
     * Expected: Invalid URLs raise IllegalArgumentException before JDBC access.
     */
    @Test
    void builderRejectsNullOrBlankJdbcUrlBeforeOpeningJdbcConnection() {
        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl(null)
                .dbUser("admin")
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");

        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl(" ")
                .dbUser("admin")
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");
    }

    /**
     * Test: Reject missing or blank database passwords.
     * Expected: Invalid passwords raise IllegalArgumentException before JDBC access.
     */
    @Test
    void builderRejectsMissingOrBlankPasswordBeforeOpeningJdbcConnection() {
        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl("jdbc:test")
                .dbUser("admin")
                .dbPassword(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbPassword");

        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl("jdbc:test")
                .dbUser("admin")
                .dbPassword(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbPassword");
    }

    /**
     * Test: Reject a blank optional wallet password before opening JDBC.
     * Expected: IllegalArgumentException identifies walletPassword.
     */
    @Test
    void builderRejectsBlankWalletPasswordBeforeOpeningJdbcConnection() {
        assertThatThrownBy(() -> DefaultDbConnection.builder()
                .jdbcUrl("jdbc:test")
                .dbUser("admin")
                .dbPassword("secret")
                .walletPassword(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("walletPassword");
    }

    /**
     * Test: Pass the wallet password through to the JDBC connection properties.
     * Expected: The driver receives the configured wallet password property.
     */
    @Test
    void builderPassesWalletPasswordAsJdbcConnectionProperty() throws Exception {
        TestDriver driver = new TestDriver(connection, null);
        registerDriver(driver);
        try {
            DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("secret")
                    .walletPassword("wallet-secret")
                    .build();

            assertThat(driver.lastProperties).isNotNull();
            assertThat(driver.lastProperties.getProperty(
                    OracleConnection.CONNECTION_PROPERTY_WALLET_PASSWORD))
                    .isEqualTo("wallet-secret");
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Wrap a DriverManager SQL failure during connection creation.
     * Expected: A SelectAIException contains the connection context and original cause.
     */
    @Test
    void builderWrapsDriverManagerSqlException() throws Exception {
        SQLException sqlException = new SQLException("listener refused connection", "08006", 12514);
        TestDriver driver = new TestDriver(null, sqlException);
        registerDriver(driver);
        try {
            assertThatThrownBy(() -> DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("secret")
                    .build())
                    .isInstanceOf(SelectAIException.class)
                    .hasMessageContaining("database connection")
                    .hasCause(sqlException);
            assertThat(driver.connectionCalls).isEqualTo(1);
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Keep the public database-connection API free of password getters.
     * Expected: DbConnection exposes no getDbPassword() method.
     */
    @Test
    void publicDbConnectionApiDoesNotExposeDatabasePassword() {
        assertThat(DbConnection.class.getMethods())
                .noneMatch(method -> method.getName().equals("getDbPassword"));
    }

    /**
     * Test: Keep the database password out of the connection string representation.
     * Expected: toString() does not contain the configured password.
     */
    @Test
    void dbConnectionToStringDoesNotExposeDatabasePassword() throws Exception {
        TestDriver driver = new TestDriver(connection, null);
        registerDriver(driver);
        try {
            DbConnection dbConnection = DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("database-secret")
                    .build();

            assertThat(dbConnection.toString())
                    .doesNotContain("database-secret");
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Forward caller-supplied JDBC properties to the driver.
     * Expected: Custom properties reach DriverManager without being dropped.
     */
    @Test
    void builderPassesJdbcPropertiesToDriver() throws Exception {
        TestDriver driver = new TestDriver(connection, null);
        Properties properties = new Properties();
        properties.setProperty("custom.property", "custom-value");
        registerDriver(driver);
        try {
            DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("secret")
                    .jdbcProperties(properties)
                    .build();

            assertThat(driver.lastProperties).isNotNull();
            assertThat(driver.lastProperties.getProperty("custom.property"))
                    .isEqualTo("custom-value");
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Close an open JDBC connection.
     * Expected: close() checks the state and closes the connection once.
     */
    @Test
    void closeClosesOpenConnection() throws Exception {
        TestDriver driver = new TestDriver(connection, null);
        when(connection.isClosed()).thenReturn(false);
        registerDriver(driver);
        try {
            DbConnection dbConnection = DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("secret")
                    .build();

            dbConnection.close();

            verify(connection).isClosed();
            verify(connection).close();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Preserve an already-closed JDBC connection.
     * Expected: close() does not issue a second JDBC close call.
     */
    @Test
    void closeDoesNotCloseAlreadyClosedConnection() throws Exception {
        TestDriver driver = new TestDriver(connection, null);
        when(connection.isClosed()).thenReturn(true);
        registerDriver(driver);
        try {
            DbConnection dbConnection = DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("secret")
                    .build();

            dbConnection.close();

            verify(connection).isClosed();
            verify(connection, never()).close();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Wrap a failure while closing the JDBC connection.
     * Expected: SelectAIException preserves the SQL exception and its metadata.
     */
    @Test
    void closeWrapsSqlException() throws Exception {
        SQLException closeFailure = new SQLException("close failed", "08006", 17002);
        TestDriver driver = new TestDriver(connection, null);
        when(connection.isClosed()).thenThrow(closeFailure);
        registerDriver(driver);
        try {
            DbConnection dbConnection = DefaultDbConnection.builder()
                    .jdbcUrl("jdbc:test")
                    .dbUser("admin")
                    .dbPassword("secret")
                    .build();

            assertThatThrownBy(dbConnection::close)
                    .isInstanceOf(SelectAIException.class)
                    .hasMessageContaining("close")
                    .hasCause(closeFailure);
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    private static void registerDriver(TestDriver driver) throws SQLException {
        DriverManager.registerDriver(driver);
    }

    private static final class TestDriver implements Driver {
        private final Connection connectionToReturn;
        private final SQLException exceptionToThrow;
        private int connectionCalls;
        private Properties lastProperties;

        private TestDriver(Connection connectionToReturn, SQLException exceptionToThrow) {
            this.connectionToReturn = connectionToReturn;
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            connectionCalls++;
            lastProperties = info;
            if (!acceptsURL(url)) {
                return null;
            }
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return connectionToReturn;
        }

        @Override
        public boolean acceptsURL(String url) {
            return "jdbc:test".equals(url) || VALID_TEST_JDBC_URL.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Not supported in test driver");
        }
    }
}
