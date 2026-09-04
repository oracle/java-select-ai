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

/** Integration coverage for DropCredential. */
class DropCredentialIT extends CredentialIntegrationFixture {

    /**
     * Test: Creates a tracked username/password credential and calls {@code drop(true)} on it.
     * Expected: The forced drop removes the database credential and returns {@code true}.
     */
    @Test
    void test23000DropExistingCredentialWithForce() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
        assertThat(credential.drop(true)).isTrue();
    }

    /**
     * Test: Creates a credential, calls {@code drop(true)} once, and calls the same forced drop
     * again on the now-missing credential.
     * Expected: Both calls return normally and return {@code true}; force mode makes the missing
     * second drop non-failing.
     */
    @Test
    void test23001DropCredentialTwiceWithForce() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
        assertThat(credential.drop(true)).isTrue();
        assertThat(credential.drop(true)).isTrue();
    }

    /**
     * Test: Creates a credential, removes it with {@code drop(false)}, and repeats
     * {@code drop(false)} after the database row is gone.
     * Expected: The first drop returns {@code true}; the second call throws a database-backed
     * {@code SelectAIException} containing {@code ORA-20004}.
     */
    @Test
    void test23002DropTwiceWithForceFalseFails() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
        assertThat(credential.drop(false)).isTrue();
        assertOracleError("ORA-20004", () -> credential.drop(false));
    }

    /**
     * Test: Creates two credential handles for the same name, drops the credential through the
     * first handle, and invokes non-forced drop through the second handle.
     * Expected: The first drop returns {@code true}; the second handle receives the missing
     * credential database error {@code ORA-20004}.
     */
    @Test
    void test23003SecondCredentialReferenceFailsAfterFirstDrops() throws Exception {
        String credentialName = uniqueCredentialName();
        Credential first = track(usernameCredential(credentialName));
        Credential second = track(usernameCredential(credentialName));

        assertThat(first.create()).isTrue();
        assertThat(first.drop(true)).isTrue();

        assertOracleError("ORA-20004", () -> second.drop(false));
    }

    /**
     * Test: Builds a uniquely named credential that has never been created and calls the default
     * non-forced {@code drop()} operation.
     * Expected: The call throws the database-backed missing-credential error {@code ORA-20004}.
     */
    @Test
    void test23004DropMissingCredentialFails() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertOracleError("ORA-20004", credential::drop);
    }

    /**
     * Test: Builds a credential for a name that is absent from the database and explicitly calls
     * {@code drop(false)}.
     * Expected: The non-forced operation throws {@code SelectAIException} containing
     * {@code ORA-20004}; no credential is removed.
     */
    @Test
    void test23005DropMissingCredentialWithForceFalseFails() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertOracleError("ORA-20004", () -> credential.drop(false));
    }

    /**
     * Test: Builds a credential for a name that is absent from the database and calls
     * {@code drop(true)}.
     * Expected: Force mode treats the absent row as a successful no-op and returns {@code true}.
     */
    @Test
    void test23006DropMissingCredentialWithForceSucceeds() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertThat(credential.drop(true)).isTrue();
    }

    /**
     * Test: Executes the local-user setup, credential creation, and credential drop flow through
     * the temporary database user used by the helper.
     * Expected: The local user can complete both credential operations without an exception.
     */
    @Test
    void test23007DropCredentialAsLocalUser() throws Exception {
        runLocalUserCredentialRoundTrip();
    }

    /**
     * Test: Calls {@code drop()} for the invalid credential name {@code "invalid!@#"}.
     * Expected: The database rejects the invalid name and the helper observes {@code ORA-20010}.
     */
    @Test
    void test23008InvalidCredentialNameRejectedByDatabase() throws Exception {
        Credential credential = track(usernameCredential("invalid!@#"));

        assertOracleError("ORA-20010", credential::drop);
    }

    /**
     * Test: Creates a credential handle, closes its JDBC connection, and invokes {@code drop()}.
     * Expected: The operation throws the closed-connection error identified by
     * {@code ORA-17008}.
     */
    @Test
    void test23009DropCredentialOnClosedConnectionFails() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));
        jdbcConnection().close();

        assertClosedConnectionFailure("ORA-17008", credential::drop);
    }

    /**
     * Test: Calls {@code drop()} for a name containing {@code GENAI_CRED_} plus 120
     * {@code 'a'} characters.
     * Expected: The database rejects the overlong identifier and the helper observes
     * {@code ORA-20008}.
     */
    @Test
    void test23010CredentialNameOver128CharactersRejectedOnDrop() throws Exception {
        String longName = "GENAI_CRED_" + "a".repeat(120);
        Credential credential = track(usernameCredential(longName));

        assertOracleError("ORA-20008", credential::drop);
    }

    /**
     * Test: Creates a credential with an uppercase generated name and drops it through a second
     * handle whose configured name is the lowercase form of that name.
     * Expected: Creation returns {@code true} and the lowercase-name drop also returns
     * {@code true}, confirming database name matching for this API path.
     */
    @Test
    void test23011DropCredentialWithLowercaseName() throws Exception {
        String credentialName = uniqueCredentialName();
        Credential creator = track(usernameCredential(credentialName));
        Credential lowerCaseDropper = track(usernameCredential(
                credentialName.toLowerCase(Locale.ROOT)));

        assertThat(creator.create()).isTrue();
        assertThat(lowerCaseDropper.drop()).isTrue();
    }

    /**
     * Test: Builds drop configurations with both an empty credential name and a {@code null}
     * credential name.
     * Expected: Each builder call throws {@code IllegalArgumentException} containing
     * {@code "credentialName"}; no drop request is sent.
     */
    @Test
    void test23012MissingCredentialNameRejectedByConfigOnDrop() {
        assertThatThrownBy(() -> CredentialConfig.builder("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialName");
        assertThatThrownBy(() -> CredentialConfig.builder(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialName");
    }

    /**
     * Test: Creates a credential with full username/password fields, creates a second handle from
     * a name-only {@code CredentialConfig}, and calls {@code drop()} on the second handle.
     * Expected: The first create returns {@code true} and the name-only drop returns {@code true},
     * removing the same database credential without requiring secret fields.
     */
    @Test
    void test23013JavaApiDropsCredentialWithNameOnlyConfig() throws Exception {
        String credentialName = uniqueCredentialName();
        Credential creator = track(usernameCredential(credentialName));
        Credential dropper = track(CredentialConfig.builder(credentialName).build());

        assertThat(creator.create()).isTrue();
        assertThat(dropper.drop()).isTrue();
    }

    /**
     * Test: Supplies username/password fields together with all four OCI signing-key fields and
     * calls {@code create()}.
     * Expected: The Java API throws {@code IllegalArgumentException} containing
     * {@code "either username/password or OCI key fields"}; no database create is attempted.
     */
    @Test
    void test23014JavaApiRejectsMixedUsernameAndOciCredentialFields() {
        Credential credential = track(CredentialConfig.builder(uniqueCredentialName())
                .username("username")
                .password("password")
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build());

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either username/password or OCI key fields");
    }

    /**
     * Test: Supplies OCI user OCID, tenancy OCID, and private key but omits the fingerprint, then
     * calls {@code create()}.
     * Expected: The Java API throws {@code IllegalArgumentException} stating that all four OCI
     * fields are required before JDBC execution.
     */
    @Test
    void test23015JavaApiRejectsOciCredentialWithoutFingerprint() {
        Credential credential = selectAI.credential(CredentialConfig.builder(uniqueCredentialName())
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .build());

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userOcid, tenancyOcid, privateKey, and fingerprint");
    }

    /**
     * Test: Supplies tenancy OCID, private key, and fingerprint but omits the user OCID.
     * Expected: The shared incomplete-OCI assertion observes {@code IllegalArgumentException}
     * with the message naming the required OCI fields.
     */
    @Test
    void test23016JavaApiRejectsOciCredentialWithoutUserOcid() {
        assertIncompleteOciCredential(CredentialConfig.builder(uniqueCredentialName())
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build());
    }

    /**
     * Test: Supplies user OCID, private key, and fingerprint but omits the tenancy OCID.
     * Expected: Credential creation is rejected by the Java API with the incomplete-OCI required
     * fields message before a database call.
     */
    @Test
    void test23017JavaApiRejectsOciCredentialWithoutTenancyOcid() {
        assertIncompleteOciCredential(CredentialConfig.builder(uniqueCredentialName())
                .userOcid("user-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build());
    }

    /**
     * Test: Supplies user OCID, tenancy OCID, and fingerprint but omits the private key.
     * Expected: Credential creation throws the same {@code IllegalArgumentException} identifying
     * the required OCI signing-key fields.
     */
    @Test
    void test23018JavaApiRejectsOciCredentialWithoutPrivateKey() {
        assertIncompleteOciCredential(CredentialConfig.builder(uniqueCredentialName())
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .fingerprint("fingerprint")
                .build());
    }

    /**
     * Test: Builds a username/password credential with username consisting of one blank and then
     * calls {@code create()}.
     * Expected: The Java API throws {@code IllegalArgumentException} containing
     * {@code "CredentialConfig requires username for username/password credential"} before JDBC.
     */
    @Test
    void test23019JavaApiRejectsBlankUsernameBeforeDatabaseCall() {
        Credential credential = selectAI.credential(CredentialConfig.builder(uniqueCredentialName())
                .username(" ")
                .password("password")
                .build());

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CredentialConfig requires username for username/password credential");
    }

    /**
     * Test: Builds an OCI credential with a blank user OCID and nonblank tenancy OCID, private key,
     * and fingerprint.
     * Expected: The Java API rejects creation with {@code IllegalArgumentException} for the
     * incomplete required OCI fields before JDBC execution.
     */
    @Test
    void test23020JavaApiRejectsBlankOciFieldBeforeDatabaseCall() {
        assertIncompleteOciCredential(CredentialConfig.builder(uniqueCredentialName())
                .userOcid(" ")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build());
    }

    /**
     * Test: Passes {@code null} instead of a {@code CredentialConfig} to
     * {@code selectAI.credential(null)}.
     * Expected: The factory throws {@code IllegalArgumentException} containing
     * {@code "credentialConfig"} and does not create a credential handle.
     */
    @Test
    void test23021JavaApiRejectsNullCredentialConfig() {
        assertThatThrownBy(() -> selectAI.credential(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialConfig");
    }

    /**
     * Test: Supplies a blank username, password, and all OCI signing-key fields in the same
     * configuration, then calls {@code create()}.
     * Expected: The Java API throws {@code IllegalArgumentException} containing the mutually
     * exclusive credential-mode message rather than sending mixed fields to the database.
     */
    @Test
    void test23022JavaApiRejectsBlankUsernameAndPasswordWithOciFields() {
        Credential credential = selectAI.credential(CredentialConfig.builder(uniqueCredentialName())
                .username(" ")
                .password("password")
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build());

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either username/password or OCI key fields");
    }

    /**
     * Test: Creates a username/password credential with the configured username but no password
     * and captures the complete exception chain from {@code create()}.
     * Expected: The top-level exception is {@code SelectAIException} for
     * {@code DBMS_CLOUD.CREATE_CREDENTIAL}; its {@code SQLException} cause begins with
     * {@code ORA-20020: Missing credential attribute - password}.
     */
    @Test
    void test23023JavaApiRejectsUsernameCredentialWithoutPassword() {
        Credential credential = track(CredentialConfig.builder(uniqueCredentialName())
                .username(envOrDefault("SELECT_AI_IT_CRED_USERNAME", DEFAULT_CREDENTIAL_USERNAME))
                .build());

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(credential::create);

        assertThat(thrown)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Failed to execute DBMS_CLOUD.CREATE_CREDENTIAL");
        assertThat(thrown.getCause()).isInstanceOf(SQLException.class);
        assertThat(thrown.getCause().getMessage())
                .startsWith("ORA-20020: Missing credential attribute - password");
    }

    /**
     * Test: Creates a credential, drops it, creates it again with the same object and name, and
     * drops the recreated credential.
     * Expected: Both create calls and both drop calls return {@code true}; deletion releases the
     * name so it can be reused immediately.
     */
    @Test
    void test23024JavaApiRecreatesCredentialAfterDrop() throws Exception {
        Credential credential = track(usernameCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
        assertThat(credential.drop()).isTrue();
        assertThat(credential.create()).isTrue();
        assertThat(credential.drop()).isTrue();
    }

    /**
     * Test: Builds an {@code OracleDataSource} from the configured JDBC URL, database user, and
     * password, creates a {@code SelectAI} client from it, and runs credential create/drop.
     * Expected: The DataSource-backed client returns {@code true} for both lifecycle operations.
     */
    @Test
    void test23025JavaApiCreatesAndDropsCredentialUsingDataSource() throws Exception {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(dbConfig.getJdbcUrl());
        dataSource.setUser(dbConfig.getDbUser());
        dataSource.setPassword(dbConfig.getDbPassword());

        SelectAI dataSourceClient = SelectAI.create(dataSource);
        Credential credential = dataSourceClient.credential(usernameCredential(uniqueCredentialName()));

        assertThat(credential.create()).isTrue();
        assertThat(credential.drop()).isTrue();
    }

    /**
     * Test: Builds a credential containing a password but no username and calls {@code create()}.
     * Expected: The Java API throws {@code IllegalArgumentException} containing
     * {@code "CredentialConfig requires username for username/password credential"}.
     */
    @Test
    void test23026JavaApiRejectsPasswordOnlyCredential() {
        Credential credential = selectAI.credential(CredentialConfig.builder(uniqueCredentialName())
                .password("password")
                .build());

        assertThatThrownBy(credential::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CredentialConfig requires username for username/password credential");
    }
}
