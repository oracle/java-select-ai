/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.credential;

import com.oracle.database.selectai.Credential;
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
 * Credential names are unique per test. The common integration support reads
 * {@code SELECT_AI_IT_*} environment variables and provides JDBC setup. Optional
 * credential-specific keys include {@code SELECT_AI_IT_CRED_USERNAME},
 * {@code SELECT_AI_IT_CRED_PASS}, {@code SELECT_AI_IT_OCI_USER_OCID},
 * {@code SELECT_AI_IT_OCI_TENANCY_OCID}, {@code SELECT_AI_IT_OCI_PRIVATE_KEY},
 * and {@code SELECT_AI_IT_OCI_FINGERPRINT}. Local-user scenarios use the
 * configured database password for the temporary user.</p>
 */

/** Integration coverage for CreateCredential. */
class CreateCredentialIT extends CredentialIntegrationFixture {

    /**
     * Test: Builds a uniquely named credential with the configured username and password and
     * calls {@code Credential.create()}.
     * Expected: The database creates the username/password credential and the call returns
     * {@code true}; cleanup can subsequently remove the tracked credential.
     */
    @Test
    void test22000BasicCredentialCreation() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
    }

    /**
     * Test: Creates one username/password credential and calls {@code create()} a second time on
     * the same object without changing its name.
     * Expected: The first call returns {@code true}; the second call throws {@code SelectAIException}
     * whose database cause contains {@code ORA-20022}, the duplicate-credential error.
     */
    @Test
    void test22001DuplicateCredentialCreationFails() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
        assertOracleError("ORA-20022", credential::create);
    }

    /**
     * Test: Builds a uniquely named OCI credential with user OCID, tenancy OCID, private key, and
     * fingerprint values from the integration configuration and calls {@code create()}.
     * Expected: The database creates the signing-key credential and returns {@code true}.
     */
    @Test
    void test22002OciSigningKeyCredentialCreation() throws Exception {
        Credential credential = track(ociCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
    }

    /**
     * Test: Creates one OCI signing-key credential and invokes {@code create()} again with the
     * same credential name and key fields.
     * Expected: The first call returns {@code true}; the duplicate call throws a
     * {@code SelectAIException} whose database error is {@code ORA-20022}.
     */
    @Test
    void test22003DuplicateOciSigningKeyCreationFails() throws Exception {
        Credential credential = track(ociCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
        assertOracleError("ORA-20022", credential::create);
    }

    /**
     * Test: Passes both an empty string and {@code null} to {@code CredentialConfig.builder(...)}
     * while supplying username/password fields for the empty-name case.
     * Expected: Each builder call throws {@code IllegalArgumentException} containing
     * {@code "credentialName"}; no credential object is created.
     */
    @Test
    void test22004MissingCredentialNameRejectedByConfig() {
        assertThatThrownBy(() -> CredentialConfig.builder("")
                .username("username")
                .password("password")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialName");
        assertThatThrownBy(() -> CredentialConfig.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialName");
    }

    /**
     * Test: Creates a credential with username {@code "invalid_username"} and the configured or
     * fallback credential password, then sends it to the database.
     * Expected: The SDK passes the username value through and the database creation call returns
     * {@code true}; the test does not impose Java-side username-format validation.
     */
    @Test
    void test22005CredentialCreationAcceptsInvalidUsername() throws Exception {
        Credential credential = track(CredentialConfig.builder(uniqueCredentialName())
                .username("invalid_username")
                .password(envOrDefault("SELECT_AI_IT_CRED_PASS", DEFAULT_INVALID_CRED_PASS))
                .build());

        assertThat(credential.create()).isTrue();
    }

    /**
     * Test: Creates a credential with the configured or fallback username and an invalid
     * credential password, then calls {@code create()}.
     * Expected: The supplied password is sent to the database without Java-side password-format
     * rejection and credential creation returns {@code true}.
     */
    @Test
    void test22006CredentialCreationAcceptsInvalidPassword() throws Exception {
        Credential credential = track(CredentialConfig.builder(uniqueCredentialName())
                .username(envOrDefault("SELECT_AI_IT_CRED_USERNAME", DEFAULT_CREDENTIAL_USERNAME))
                .password(DEFAULT_INVALID_CRED_PASS)
                .build());

        assertThat(credential.create()).isTrue();
    }

    /**
     * Test: Creates a credential handle, closes the JDBC connection used by the client, and then
     * calls {@code create()}.
     * Expected: Creation throws the closed-connection failure identified by
     * {@code ORA-17008}; the database create call is not completed.
     */
    @Test
    void test22007CredentialCreationOnClosedConnectionFails() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));
        jdbcConnection().close();

        assertClosedConnectionFailure("ORA-17008", credential::create);
    }

    /**
     * Test: Creates a temporary local database user, creates a username/password credential for
     * that user, and drops the credential through the local-user path.
     * Expected: The local user's {@code Credential.create()} call returns {@code true}, the
     * corresponding {@code drop()} call returns {@code true}, and the test completes without a
     * credential-related exception before the temporary user is removed.
     */
    @Test
    void test22008LocalUserCredentialCreationAndDrop() throws Exception {
        runLocalUserCredentialRoundTrip();
    }

    /**
     * Test: Calls {@code create()} for a username/password credential named
     * {@code "GENAI_CRED!@#"}.
     * Expected: The database rejects the invalid identifier and the helper observes
     * {@code ORA-20010}.
     */
    @Test
    void test22009InvalidCredentialNameRejectedByDatabase() throws Exception {
        Credential credential = track(usernameCredential("GENAI_CRED!@#"));

        assertOracleError("ORA-20010", credential::create);
    }

    /**
     * Test: Calls {@code create()} for a credential name composed of {@code GENAI_CRED_} plus
     * 118 {@code 'a'} characters, exceeding the database name limit.
     * Expected: The database rejects the name and the helper observes {@code ORA-20008}.
     */
    @Test
    void test22010CredentialNameOver128CharactersRejected() throws Exception {
        String longName = "GENAI_CRED_" + "a".repeat(118);
        Credential credential = track(usernameCredential(longName));

        assertOracleError("ORA-20008", credential::create);
    }
}
