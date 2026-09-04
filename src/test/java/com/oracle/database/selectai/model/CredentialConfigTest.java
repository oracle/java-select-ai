/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialConfigTest {

    /**
     * Test: Inspect the credential configuration class contract.
     * Expected: CredentialConfig is final because it is an immutable value object.
     */
    @Test
    void credentialConfigIsFinal() {
        assertThat(Modifier.isFinal(CredentialConfig.class.getModifiers())).isTrue();
    }

    /**
     * Test: Reject a credential configuration without a name.
     * Expected: Null and blank names throw IllegalArgumentException.
     */
    @Test
    void builderRequiresCredentialName() {
        assertThatThrownBy(() -> CredentialConfig.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialName");

        assertThatThrownBy(() -> CredentialConfig.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialName");
    }

    /**
     * Test: Preserve configured credential fields.
     * Expected: The credential name and all supplied OCI fields are retained.
     */
    @Test
    void builderKeepsConfiguredCredentialFields() {
        CredentialConfig config = CredentialConfig.builder("OCI_CRED")
                .userOcid("ocid1.user.oc1..example")
                .tenancyOcid("ocid1.tenancy.oc1..example")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build();

        assertThat(config.getCredentialName()).isEqualTo("OCI_CRED");
        assertThat(config.getUserOcid()).isEqualTo("ocid1.user.oc1..example");
        assertThat(config.getTenancyOcid()).isEqualTo("ocid1.tenancy.oc1..example");
        assertThat(config.getPrivateKey()).isEqualTo("private-key");
        assertThat(config.getFingerprint()).isEqualTo("fingerprint");
    }

    /**
     * Test: Retains username/password credential fields.
     * Expected: Both supplied values are available through their getters.
     */
    @Test
    void builderKeepsUsernameAndPasswordFields() {
        CredentialConfig config = CredentialConfig.builder("BASIC_CRED")
                .username("service-user")
                .password("service-password")
                .build();

        assertThat(config.getUsername()).isEqualTo("service-user");
        assertThat(config.getPassword()).isEqualTo("service-password");
    }

    /**
     * Test: Build username/password credential configuration through the factory method.
     * Expected: The credential name, username, and password are retained.
     */
    @Test
    void usernamePasswordFactoryBuildsUsernamePasswordCredentialConfig() {
        CredentialConfig config = CredentialConfig.usernamePassword(
                "BASIC_CRED", "service-user", "service-password");

        assertThat(config.getCredentialName()).isEqualTo("BASIC_CRED");
        assertThat(config.getUsername()).isEqualTo("service-user");
        assertThat(config.getPassword()).isEqualTo("service-password");
        assertThat(config.getUserOcid()).isNull();
        assertThat(config.getTenancyOcid()).isNull();
        assertThat(config.getPrivateKey()).isNull();
        assertThat(config.getFingerprint()).isNull();
    }

    /**
     * Test: Build OCI signing-key credential configuration through the factory method.
     * Expected: The credential name and all OCI signing-key fields are retained.
     */
    @Test
    void ociSigningKeyFactoryBuildsOciCredentialConfig() {
        CredentialConfig config = CredentialConfig.ociSigningKey(
                "OCI_CRED",
                "ocid1.user.oc1..example",
                "ocid1.tenancy.oc1..example",
                "private-key",
                "fingerprint");

        assertThat(config.getCredentialName()).isEqualTo("OCI_CRED");
        assertThat(config.getUsername()).isNull();
        assertThat(config.getPassword()).isNull();
        assertThat(config.getUserOcid()).isEqualTo("ocid1.user.oc1..example");
        assertThat(config.getTenancyOcid()).isEqualTo("ocid1.tenancy.oc1..example");
        assertThat(config.getPrivateKey()).isEqualTo("private-key");
        assertThat(config.getFingerprint()).isEqualTo("fingerprint");
    }

    /**
     * Test: Build a credential configuration with optional fields unset.
     * Expected: The credential name is retained and optional OCI fields remain null.
     */
    @Test
    void builderAllowsOptionalOciFieldsToRemainUnset() {
        CredentialConfig config = CredentialConfig.builder("OCI_CRED").build();

        assertThat(config.getCredentialName()).isEqualTo("OCI_CRED");
        assertThat(config.getUserOcid()).isNull();
        assertThat(config.getTenancyOcid()).isNull();
        assertThat(config.getPrivateKey()).isNull();
        assertThat(config.getFingerprint()).isNull();
    }

}
