/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.credential;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import oracle.jdbc.pool.OracleDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live credential lifecycle and configuration integration coverage.
 *
 * <p>The Java SDK exposes synchronous {@link Credential#create()},
 * {@link Credential#drop()}, and {@link Credential#drop(boolean)} operations.
 * Credential names are unique per test. The shared fixture reads
 * {@code SELECT_AI_IT_*} environment variables and provides JDBC setup. Optional
 * credential-specific keys include {@code SELECT_AI_IT_CRED_USERNAME},
 * {@code SELECT_AI_IT_CRED_PASSWORD}, {@code SELECT_AI_IT_OCI_USER_OCID},
 * {@code SELECT_AI_IT_OCI_TENANCY_OCID}, {@code SELECT_AI_IT_OCI_PRIVATE_KEY},
 * and {@code SELECT_AI_IT_OCI_FINGERPRINT}. Local-user scenarios use the
 * configured database password for the temporary user.</p>
 */
abstract class CredentialIntegrationFixture extends IntegrationTestFixture {

    protected static final String DEFAULT_CREDENTIAL_USERNAME = "OCI credential username";
    protected static final String DEFAULT_CREDENTIAL_PASSWORD = "OCI credential password";
    protected static final String DEFAULT_INVALID_PASSWORD = "invalid_password";
    protected static final String DEFAULT_OCI_USER_OCID = "user ocid";
    protected static final String DEFAULT_OCI_TENANCY_OCID = "tenancy ocid";
    protected static final String DEFAULT_OCI_PRIVATE_KEY = "private key";
    protected static final String DEFAULT_OCI_FINGERPRINT = "fingerprint";

    protected final List<Credential> credentialsToCleanUp = new ArrayList<>();

    @Override
    protected boolean requiresProfile() {
        return false;
    }

    @AfterEach
    void cleanUpCredentials() {
        for (Credential credential : credentialsToCleanUp) {
            try {
                credential.drop();
            } catch (Exception ignored) {
                // Preserve the primary test result; names are unique per test.
            }
        }
        credentialsToCleanUp.clear();
    }


    protected void assertIncompleteOciCredential(CredentialConfig config) {
        Credential credential = selectAI.credential(config);

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userOcid, tenancyOcid, privateKey, and fingerprint");
    }

    protected Credential track(CredentialConfig config) {
        Credential credential = selectAI.credential(config);
        credentialsToCleanUp.add(credential);
        return credential;
    }

    protected CredentialConfig usernameCredential(String credentialName) {
        return CredentialConfig.builder(credentialName)
                .username(envOrDefault("SELECT_AI_IT_CRED_USERNAME", DEFAULT_CREDENTIAL_USERNAME))
                .password(envOrDefault("SELECT_AI_IT_CRED_PASSWORD", DEFAULT_CREDENTIAL_PASSWORD))
                .build();
    }

    protected CredentialConfig ociCredential(String credentialName) {
        return CredentialConfig.builder(credentialName)
                .userOcid(envOrDefault("SELECT_AI_IT_OCI_USER_OCID", DEFAULT_OCI_USER_OCID))
                .tenancyOcid(envOrDefault("SELECT_AI_IT_OCI_TENANCY_OCID", DEFAULT_OCI_TENANCY_OCID))
                .privateKey(envOrDefault("SELECT_AI_IT_OCI_PRIVATE_KEY", DEFAULT_OCI_PRIVATE_KEY))
                .fingerprint(envOrDefault("SELECT_AI_IT_OCI_FINGERPRINT", DEFAULT_OCI_FINGERPRINT))
                .build();
    }

    protected void runLocalUserCredentialRoundTrip() throws Exception {
        String adminUser = dbConfig.getDbUser();
        String adminPassword = dbConfig.getDbPassword();
        String localPassword = dbConfig.getDbPassword();
        String localUser = uniqueIdentifier("JSAI_IT_USER_");
        String credentialName = uniqueCredentialName();
        SelectAI adminClient = null;
        SelectAI localClient = null;
        boolean userCreated = false;

        try {
            adminClient = SelectAI.create(connectionConfigFor(adminUser, adminPassword));
            try {
                createLocalUser(adminClient.getConnection(), localUser, localPassword);
                userCreated = true;
            } catch (SQLException exception) {
                assumeTrue(false, "Database user creation privileges are unavailable: "
                        + exception.getMessage());
            }

            localClient = SelectAI.create(connectionConfigFor(localUser, localPassword));
            Credential localCredential = localClient.credential(usernameCredential(credentialName));
            assertThat(localCredential.create()).isTrue();
            assertThat(localCredential.drop()).isTrue();
        } finally {
            closeConnection(localClient);
            if (userCreated && adminClient != null) {
                dropLocalUser(adminClient.getConnection(), localUser);
            }
            closeConnection(adminClient);
        }
    }

    protected static void createLocalUser(Connection connection, String username, String password)
            throws SQLException {
        String escapedPassword = password.replace("\"", "\"\"");
        boolean created = false;
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE USER " + username + " IDENTIFIED BY \""
                    + escapedPassword + "\"");
            created = true;
            statement.execute("GRANT CREATE SESSION, CREATE TABLE, UNLIMITED TABLESPACE TO " + username);
            statement.execute("GRANT EXECUTE ON DBMS_CLOUD TO " + username);
        } catch (SQLException exception) {
            if (created) {
                dropLocalUser(connection, username);
            }
            throw exception;
        }
    }

    protected static void dropLocalUser(Connection connection, String username) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP USER " + username + " CASCADE");
        } catch (SQLException ignored) {
            // Preserve the primary test result; the user name is unique.
        }
    }

    protected static void closeConnection(SelectAI client) throws SelectAIException {
        if (client != null) {
            client.close();
        }
    }

    protected static void assertOracleError(String expectedCode,
                                          org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining(expectedCode + ":"));
    }

    protected static void assertClosedConnectionFailure(
            String expectedCode,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining(expectedCode + ":"));
    }

    protected static String uniqueCredentialName() {
        return uniqueIdentifier("JSAI_IT_CRED_");
    }

    protected static String uniqueIdentifier(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT);
    }
}
