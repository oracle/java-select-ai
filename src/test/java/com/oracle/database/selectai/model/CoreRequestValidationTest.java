/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreRequestValidationTest {

    /**
     * Test: Requires a credential name and preserves credential configuration values.
     * Expected: Valid values are retained and a missing name is rejected.
     */
    @Test
    void credentialConfigRequiresNameAndKeepsConfiguredValues() {
        CredentialConfig config = CredentialConfig.builder("OCI_CRED")
                .username("cloud-user")
                .password("cloud-password")
                .userOcid("ocid1.user.oc1..example")
                .tenancyOcid("ocid1.tenancy.oc1..example")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build();

        assertThat(config.getCredentialName()).isEqualTo("OCI_CRED");
        assertThat(config.getUsername()).isEqualTo("cloud-user");
        assertThat(config.getPassword()).isEqualTo("cloud-password");
        assertThat(config.getUserOcid()).isEqualTo("ocid1.user.oc1..example");
        assertThat(config.getTenancyOcid()).isEqualTo("ocid1.tenancy.oc1..example");
        assertThat(config.getPrivateKey()).isEqualTo("private-key");
        assertThat(config.getFingerprint()).isEqualTo("fingerprint");
        assertThat(config.toString()).doesNotContain("cloud-password", "private-key");

        assertThatThrownBy(() -> CredentialConfig.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialName");
    }

    /**
     * Test: Parses profile status values without locale-sensitive behavior.
     * Expected: Status parsing remains correct under locale changes.
     */
    @Test
    void profileStatusParsesValuesWithLocaleRoot() {
        assertThat(ProfileStatus.fromValue(null)).isNull();
        assertThat(ProfileStatus.fromValue(" ")).isNull();
        assertThat(ProfileStatus.fromValue(" enabled ")).isEqualTo(ProfileStatus.ENABLED);
        assertThat(ProfileStatus.fromValue(" EnAbLeD ")).isEqualTo(ProfileStatus.ENABLED);
        assertThat(ProfileStatus.fromValue("DISABLED")).isEqualTo(ProfileStatus.DISABLED);
        assertThat(ProfileStatus.fromValue(" dIsAbLeD ")).isEqualTo(ProfileStatus.DISABLED);
        assertThat(ProfileStatus.DISABLED.getValue()).isEqualTo("DISABLED");

        assertThatThrownBy(() -> ProfileStatus.fromValue("paused"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENABLED or DISABLED");
    }

    /**
     * Test: Validates required database connection fields and explicit JDBC URL use.
     * Expected: Valid configuration succeeds and missing or invalid fields are rejected.
     */
    @Test
    void dbConnectionConfigRequiresExplicitJdbcUrlAndValidatesRequiredFields() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .walletPassword("wallet-secret")
                .jdbcUrl("jdbc:oracle:thin:@example_high?TNS_ADMIN=/wallet")
                .build();

        assertThat(config.getDbUser()).isEqualTo("selectai");
        assertThat(config.getDbPassword()).isEqualTo("secret");
        assertThat(config.getWalletPassword()).isEqualTo("wallet-secret");
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:oracle:thin:@example_high?TNS_ADMIN=/wallet");

        assertThatThrownBy(() -> DbConnectionConfig.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser(" ")
                .dbPassword("secret")
                .jdbcUrl("jdbc:oracle:thin:@example_high?TNS_ADMIN=/wallet")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword(null)
                .jdbcUrl("jdbc:oracle:thin:@example_high?TNS_ADMIN=/wallet")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbPassword");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword(" ")
                .jdbcUrl("jdbc:oracle:thin:@example_high?TNS_ADMIN=/wallet")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbPassword");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .walletPassword(" ")
                .jdbcUrl("jdbc:oracle:thin:@example_high?TNS_ADMIN=/wallet")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("walletPassword");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");
    }

    /**
     * Test: Accepts supported Oracle thin JDBC URL forms.
     * Expected: Each supported URL form is accepted.
     */
    @Test
    void dbConnectionConfigAcceptsSupportedOracleThinJdbcUrlForms() {
        assertThat(validConfig("jdbc:oracle:thin:@myhost.example.com:1521/service").getJdbcUrl())
                .isEqualTo("jdbc:oracle:thin:@myhost.example.com:1521/service");

        assertThat(validConfig("jdbc:oracle:thin:@myhost.example.com:1521/service?wallet_location=/wallet").getJdbcUrl())
                .isEqualTo("jdbc:oracle:thin:@myhost.example.com:1521/service?wallet_location=/wallet");

        assertThat(validConfig("jdbc:oracle:thin:@myadb_high?TNS_ADMIN=/path/to/wallet").getJdbcUrl())
                .isEqualTo("jdbc:oracle:thin:@myadb_high?TNS_ADMIN=/path/to/wallet");

        assertThat(validConfig("jdbc:oracle:thin:@MY_TNS_ALIAS").getJdbcUrl())
                .isEqualTo("jdbc:oracle:thin:@MY_TNS_ALIAS");

        assertThat(validConfig("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)"
                + "(HOST=myhost.example.com)(PORT=1522))(CONNECT_DATA=(SERVICE_NAME=myservice)))").getJdbcUrl())
                .contains("PROTOCOL=TCPS");
    }

    /**
     * Test: Rejects unsupported JDBC URL forms.
     * Expected: Each unsupported URL form is rejected.
     */
    @Test
    void dbConnectionConfigRejectsUnsupportedJdbcUrlForms() {
        assertThatThrownBy(() -> validConfig("jdbc:mysql://localhost:3306/test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc:oracle:thin:@");

        assertThatThrownBy(() -> validConfig("jdbc:oracle:oci:@MY_TNS_ALIAS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc:oracle:thin:@");

        assertThatThrownBy(() -> validConfig("jdbc:oracle:thin:@ "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connect target");
    }

    private static DbConnectionConfig validConfig(String jdbcUrl) {
        return DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .jdbcUrl(jdbcUrl)
                .build();
    }

    /**
     * Test: Preserves feedback operation omission and validates SQL identity rules.
     * Expected: Valid feedback is retained and invalid identity combinations are rejected.
     */
    @Test
    void feedbackPreservesOmittedOperationAndValidatesSqlIdentityRules() {
        Feedback feedback = Feedback.builder()
                .sqlText("select * from employees")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .feedbackContent("Looks right")
                .build();

        assertThat(feedback.getOperation()).isNull();
        assertThat(feedback.getOperationValue()).isNull();
        assertThat(feedback.getFeedbackType()).isEqualTo(Feedback.FeedbackType.POSITIVE);
        assertThat(feedback.getFeedbackTypeValue()).isEqualTo("positive");
        assertThat(feedback.getSqlText()).isEqualTo("select * from employees");
        assertThat(feedback.getSqlId()).isNull();

        Feedback delete = Feedback.builder()
                .sqlId("abc123")
                .operation(" delete ")
                .build();

        assertThat(delete.getOperation()).isEqualTo(Feedback.Operation.DELETE);
        assertThat(delete.getOperationValue()).isEqualTo("delete");
    }

    /**
     * Test: Rejects ambiguous or incomplete feedback requests.
     * Expected: Requests missing required or mutually exclusive fields fail validation.
     */
    @Test
    void feedbackRejectsAmbiguousAndIncompleteRequests() {
        assertThatThrownBy(() -> Feedback.builder()
                .feedbackType("positive")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either sqlId or sqlText");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .sqlText("select 1 from dual")
                .feedbackType("positive")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only one");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlText("select 1 from dual")
                .feedbackType("negative")
                .operation("add")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("response is required");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlText("select 1 from dual")
                .feedbackType("neutral")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive or negative");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .operation("archive")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add or delete");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .feedbackType("positive")
                .operation("delete")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELETE feedback");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlText("select 1 from dual")
                .feedbackContent("remove this")
                .operation("delete")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELETE feedback");
    }

}
