/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCredentialTest {

    private static final String CREATE_USERNAME_PASSWORD_CREDENTIAL_SQL = "BEGIN " +
            "  DBMS_CLOUD.CREATE_CREDENTIAL(?, ?, ?); " +
            "END;";
    private static final String CREATE_CREDENTIAL_SQL = "BEGIN " +
            "  DBMS_CLOUD.CREATE_CREDENTIAL(?, ?, ?, ?, ?); " +
            "END;";
    private static final String DROP_CREDENTIAL_SQL = "BEGIN " +
            "  DBMS_CLOUD.DROP_CREDENTIAL(?); " +
            "END;";
    private static final String CREDENTIAL_EXISTS_SQL =
            "SELECT COUNT(*) FROM USER_CREDENTIALS WHERE CREDENTIAL_NAME = UPPER(?)";

    @Mock
    private DbConnection dbConnection;
    @Mock
    private Connection connection;
    @Mock
    private CallableStatement statement;

    private CredentialConfig credentialConfig;

    @BeforeEach
    void setUp() {
        credentialConfig = CredentialConfig.builder("OCI_CRED")
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build();
    }

    /**
     * Test: Verifies constructor validates required dependencies.
     * Expected: Null connection-provider and credential-config inputs each raise
     * IllegalArgumentException, and no database interaction occurs.
     */
    @Test
    void constructorValidatesRequiredDependencies() {
        assertThatThrownBy(() -> new DefaultCredential(null, credentialConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionProvider");

        assertThatThrownBy(() -> new DefaultCredential(new SingleConnectionProvider(dbConnection), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialConfig");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies create binds credential fields and executes procedure.
     * Expected: create() returns true, binds OCI_CRED and all four OCI credential fields,
     * executes the procedure, and closes the statement.
     */
    @Test
    void createBindsCredentialFieldsAndExecutesProcedure() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(CREATE_CREDENTIAL_SQL)).thenReturn(statement);

        DefaultCredential credential = new DefaultCredential(new SingleConnectionProvider(dbConnection), credentialConfig);

        assertThat(credential.create()).isTrue();

        verify(connection).prepareCall(CREATE_CREDENTIAL_SQL);
        verify(statement).setString(1, "OCI_CRED");
        verify(statement).setString(2, "user-ocid");
        verify(statement).setString(3, "tenancy-ocid");
        verify(statement).setString(4, "private-key");
        verify(statement).setString(5, "fingerprint");
        verify(statement).execute();
        verify(statement).close();
    }

    /**
     * Test: Verifies create binds username and password credential fields.
     * Expected: create() returns true, binds USERPASS_CRED with the username and password,
     * executes the procedure, and closes the statement.
     */
    @Test
    void createBindsUsernameAndPasswordCredentialFields() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(CREATE_USERNAME_PASSWORD_CREDENTIAL_SQL)).thenReturn(statement);

        CredentialConfig config = CredentialConfig.builder("USERPASS_CRED")
                .username("username")
                .password("password")
                .build();
        DefaultCredential credential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), config);

        assertThat(credential.create()).isTrue();

        verify(connection).prepareCall(CREATE_USERNAME_PASSWORD_CREDENTIAL_SQL);
        verify(statement).setString(1, "USERPASS_CRED");
        verify(statement).setString(2, "username");
        verify(statement).setString(3, "password");
        verify(statement).execute();
        verify(statement).close();
    }

    /**
     * Test: Verifies create allows username credentials without a password.
     * Expected: create() binds the username and a null password, executes the procedure,
     * and closes the statement.
     */
    @Test
    void createAllowsUsernameCredentialWithoutPassword() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(CREATE_USERNAME_PASSWORD_CREDENTIAL_SQL)).thenReturn(statement);

        CredentialConfig config = CredentialConfig.builder("USERNAME_ONLY_CRED")
                .username("cloud-user")
                .build();
        DefaultCredential credential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), config);

        assertThat(credential.create()).isTrue();

        verify(connection).prepareCall(CREATE_USERNAME_PASSWORD_CREDENTIAL_SQL);
        verify(statement).setString(1, "USERNAME_ONLY_CRED");
        verify(statement).setString(2, "cloud-user");
        verify(statement).setString(3, (String) null);
        verify(statement).execute();
        verify(statement).close();
    }

    /**
     * Test: Verifies create rejects blank username even when password is provided before database call.
     * Expected: create() throws IllegalArgumentException requiring a username and does not
     * access the database.
     */
    @Test
    void createRejectsBlankUsernameEvenWhenPasswordIsProvidedBeforeDatabaseCall() {
        CredentialConfig config = CredentialConfig.builder("USERPASS_CRED")
                .username(" ")
                .password("password")
                .build();
        DefaultCredential credential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), config);

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires username");
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies create rejects password only configuration before database call.
     * Expected: create() throws IllegalArgumentException requiring a username and does not
     * access the database.
     */
    @Test
    void createRejectsPasswordOnlyConfigurationBeforeDatabaseCall() {
        CredentialConfig config = CredentialConfig.builder("USERPASS_CRED")
                .password("password")
                .build();
        DefaultCredential credential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), config);

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires username");
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies create rejects password mixed with oci credential fields before database call.
     * Expected: create() throws IllegalArgumentException rejecting mixed username/password and
     * OCI credential fields without accessing the database.
     */
    @Test
    void createRejectsPasswordMixedWithOciCredentialFieldsBeforeDatabaseCall() {
        CredentialConfig config = CredentialConfig.builder("OCI_CRED")
                .password("password")
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build();

        DefaultCredential credential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), config);

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either username/password or OCI key fields");
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies create rejects password with partial oci credential fields before database call.
     * Expected: create() throws IllegalArgumentException rejecting incomplete mixed credential
     * fields without accessing the database.
     */
    @Test
    void createRejectsPasswordWithPartialOciCredentialFieldsBeforeDatabaseCall() {
        CredentialConfig config = CredentialConfig.builder("OCI_CRED")
                .password("password")
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .build();

        DefaultCredential credential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), config);

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either username/password or OCI key fields");
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies incomplete OCI key configuration is rejected before JDBC execution.
     * Expected: Supplying only one OCI key field raises IllegalArgumentException requiring
     * all OCI key fields.
     */
    @Test
    void createRejectsIncompleteOciKeyConfigurationBeforeDatabaseCall() {
        CredentialConfig config = CredentialConfig.builder("OCI_CRED")
                .userOcid("user-ocid")
                .build();

        DefaultCredential credential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), config);

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userOcid");
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies drop binds credential name and executes procedure.
     * Expected: drop() returns true, binds OCI_CRED, executes the drop procedure, and closes
     * the statement.
     */
    @Test
    void dropBindsCredentialNameAndExecutesProcedure() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(DROP_CREDENTIAL_SQL)).thenReturn(statement);

        DefaultCredential credential = new DefaultCredential(new SingleConnectionProvider(dbConnection), credentialConfig);

        assertThat(credential.drop()).isTrue();

        verify(connection).prepareCall(DROP_CREDENTIAL_SQL);
        verify(statement).setString(1, "OCI_CRED");
        verify(statement).execute();
        verify(statement).close();
    }

    /**
     * Test: Verifies create wraps sql exception.
     * Expected: create() throws SelectAIException naming CREATE_CREDENTIAL and preserving the
     * SQL cause.
     */
    @Test
    void createWrapsSqlException() throws Exception {
        SQLException sqlException = new SQLException("database rejected create", "42000", 942);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(CREATE_CREDENTIAL_SQL)).thenReturn(statement);
        when(statement.execute()).thenThrow(sqlException);

        DefaultCredential credential = new DefaultCredential(new SingleConnectionProvider(dbConnection), credentialConfig);

        assertThatThrownBy(credential::create)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("CREATE_CREDENTIAL")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies drop wraps sql exception.
     * Expected: drop() throws SelectAIException naming DROP_CREDENTIAL and preserving the SQL
     * cause.
     */
    @Test
    void dropWrapsSqlException() throws Exception {
        SQLException sqlException = new SQLException("database rejected drop", "42000", 942);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(DROP_CREDENTIAL_SQL)).thenReturn(statement);
        when(statement.execute()).thenThrow(sqlException);

        DefaultCredential credential = new DefaultCredential(new SingleConnectionProvider(dbConnection), credentialConfig);

        assertThatThrownBy(credential::drop)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("DROP_CREDENTIAL")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies forced drop handles both absent and present credentials.
     * Expected: A missing credential is treated as success without calling DROP_CREDENTIAL;
     * an existing credential is dropped with its name bound normally.
     */
    @Test
    void forcedDropHandlesMissingAndExistingCredentials() throws Exception {
        PreparedStatement missingExistsStatement = org.mockito.Mockito.mock(PreparedStatement.class);
        PreparedStatement existingExistsStatement = org.mockito.Mockito.mock(PreparedStatement.class);
        ResultSet missingResultSet = org.mockito.Mockito.mock(ResultSet.class);
        ResultSet existingResultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(CREDENTIAL_EXISTS_SQL))
                .thenReturn(missingExistsStatement, existingExistsStatement);
        when(missingExistsStatement.executeQuery()).thenReturn(missingResultSet);
        when(missingResultSet.next()).thenReturn(false);
        when(existingExistsStatement.executeQuery()).thenReturn(existingResultSet);
        when(existingResultSet.next()).thenReturn(true);
        when(existingResultSet.getInt(1)).thenReturn(1);
        when(connection.prepareCall(DROP_CREDENTIAL_SQL)).thenReturn(statement);

        DefaultCredential missingCredential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), credentialConfig);
        assertThat(missingCredential.drop(true)).isTrue();
        verify(connection, org.mockito.Mockito.never()).prepareCall(DROP_CREDENTIAL_SQL);

        DefaultCredential existingCredential =
                new DefaultCredential(new SingleConnectionProvider(dbConnection), credentialConfig);
        assertThat(existingCredential.drop(true)).isTrue();
        verify(statement).setString(1, "OCI_CRED");
        verify(statement).execute();
    }

    /**
     * Test: Verifies public credential resource api does not expose bound configuration.
     * Expected: DefaultCredential exposes no public getCredentialConfig() method.
     */
    @Test
    void publicCredentialResourceApiDoesNotExposeBoundConfiguration() {
        assertThat(DefaultCredential.class.getMethods())
                .noneMatch(method -> method.getName().equals("getCredentialConfig"));
    }

    /**
     * Test: Verifies public credential resource api does not expose database connection.
     * Expected: DefaultCredential exposes no public getDbConnection() method.
     */
    @Test
    void publicCredentialResourceApiDoesNotExposeDatabaseConnection() {
        assertThat(DefaultCredential.class.getMethods())
                .noneMatch(method -> method.getName().equals("getDbConnection"));
    }
}
