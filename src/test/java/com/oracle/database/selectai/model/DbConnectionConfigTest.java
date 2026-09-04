/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DbConnectionConfigTest {

    /**
     * Test: Stores an explicit JDBC URL and wallet password.
     * Expected: Both configured connection values are retained.
     */
    @Test
    void storesExplicitJdbcUrlAndWalletPassword() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .walletPassword("wallet-secret")
                .jdbcUrl("jdbc:oracle:thin:@MYADB_high?TNS_ADMIN=/wallet")
                .build();

        assertThat(config.getDbUser()).isEqualTo("selectai");
        assertThat(config.getDbPassword()).isEqualTo("secret");
        assertThat(config.getWalletPassword()).isEqualTo("wallet-secret");
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:oracle:thin:@MYADB_high?TNS_ADMIN=/wallet");
    }

    /**
     * Test: Stores an explicitly supplied JDBC URL.
     * Expected: The built configuration returns the supplied URL.
     */
    @Test
    void explicitJdbcUrlIsStored() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("password")
                .jdbcUrl("jdbc:oracle:thin:@//dbhost.example:1521/service")
                .build();

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:oracle:thin:@//dbhost.example:1521/service");
    }

    /**
     * Test: Stores a trimmed JDBC URL.
     * Expected: Leading and trailing whitespace is removed before validation.
     */
    @Test
    void explicitJdbcUrlIsTrimmedBeforeStorage() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("password")
                .jdbcUrl(" jdbc:oracle:thin:@example_high ")
                .build();

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:oracle:thin:@example_high");
    }

    /**
     * Test: Applies the JDBC URL regardless of setter order.
     * Expected: The final configuration retains the explicit URL.
     */
    @Test
    void explicitJdbcUrlIsStoredRegardlessOfSetterOrder() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .jdbcUrl("jdbc:oracle:thin:@//dbhost.example:1521/service")
                .dbUser("admin")
                .dbPassword("password")
                .build();

        assertThat(config.getJdbcUrl())
                .isEqualTo("jdbc:oracle:thin:@//dbhost.example:1521/service");
    }

    /**
     * Test: Builds a configuration without required connection fields.
     * Expected: Missing required values are rejected.
     */
    @Test
    void buildRejectsMissingRequiredConnectionFields() {
        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbPassword("secret")
                .jdbcUrl("jdbc:oracle:thin:@example_high")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("admin")
                .jdbcUrl("jdbc:oracle:thin:@example_high")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbPassword");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("secret")
                .jdbcUrl(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");
    }

    /**
     * Test: Validates null and blank database usernames.
     * Expected: Invalid usernames are rejected.
     */
    @Test
    void configRejectsNullOrBlankUsername() {
        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser(null)
                .dbPassword("secret")
                .jdbcUrl("jdbc:oracle:thin:@example_high")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser(" ")
                .dbPassword("secret")
                .jdbcUrl("jdbc:oracle:thin:@example_high")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbUser");
    }

    /**
     * Test: Validates null and blank database passwords.
     * Expected: Invalid passwords are rejected.
     */
    @Test
    void configRejectsNullOrBlankDatabasePassword() {
        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword(null)
                .jdbcUrl("jdbc:oracle:thin:@example_high")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbPassword");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword(" ")
                .jdbcUrl("jdbc:oracle:thin:@example_high")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbPassword");
    }

    /**
     * Test: Validates null and blank JDBC URLs.
     * Expected: Invalid URLs are rejected.
     */
    @Test
    void configRejectsNullOrBlankJdbcUrl() {
        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("secret")
                .jdbcUrl(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");

        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("secret")
                .jdbcUrl(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");
    }

    /**
     * Test: Builds explicit wallet JDBC URLs with either password option.
     * Expected: The URL is accepted whether the wallet password is supplied or omitted.
     */
    @Test
    void explicitWalletJdbcUrlCanBeUsedWithOrWithoutWalletPassword() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("secret")
                .jdbcUrl("jdbc:oracle:thin:@MYADB_high?TNS_ADMIN=/wallet")
                .build();

        assertThat(config.getJdbcUrl())
                .isEqualTo("jdbc:oracle:thin:@MYADB_high?TNS_ADMIN=/wallet");
        assertThat(config.getWalletPassword()).isNull();
    }

    /**
     * Test: Validates a supplied wallet password.
     * Expected: A blank wallet password is rejected.
     */
    @Test
    void buildRejectsBlankWalletPassword() {
        assertThatThrownBy(() -> DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .walletPassword(" ")
                .jdbcUrl("jdbc:oracle:thin:@//dbhost.example:1521/service")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("walletPassword");
    }

    /**
     * Test: Builds a configuration without a wallet password.
     * Expected: An unset wallet password is accepted.
     */
    @Test
    void buildAllowsUnsetWalletPassword() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .jdbcUrl("jdbc:oracle:thin:@//dbhost.example:1521/service")
                .build();

        assertThat(config.getWalletPassword()).isNull();
    }

    /**
     * Test: Keeps JDBC properties isolated from caller mutations.
     * Expected: The configuration stores a copy and each getter returns another copy.
     */
    @Test
    void jdbcPropertiesAreDefensivelyCopied() {
        Properties supplied = new Properties();
        supplied.setProperty("oracle.net.wallet_password", "wallet-secret");

        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("secret")
                .jdbcUrl("jdbc:oracle:thin:@//dbhost.example:1521/service")
                .jdbcProperties(supplied)
                .build();

        supplied.setProperty("oracle.net.wallet_password", "changed");
        assertThat(config.getJdbcProperties())
                .containsEntry("oracle.net.wallet_password", "wallet-secret");

        Properties returned = config.getJdbcProperties();
        returned.setProperty("new.property", "changed");
        assertThat(config.getJdbcProperties()).doesNotContainKey("new.property");
    }

    /**
     * Test: Prevents JDBC properties from overriding SDK-owned credentials.
     * Expected: User and password properties are rejected case-insensitively.
     */
    @Test
    void jdbcPropertiesRejectSdkOwnedCredentials() {
        Properties userProperty = new Properties();
        userProperty.setProperty(" User ", "other-user");

        assertThatThrownBy(() -> DbConnectionConfig.builder().jdbcProperties(userProperty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user or password");

        Properties passwordProperty = new Properties();
        passwordProperty.setProperty("PASSWORD", "other-password");

        assertThatThrownBy(() -> DbConnectionConfig.builder().jdbcProperties(passwordProperty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user or password");
    }

    /**
     * Test: Rejects blank JDBC property names.
     * Expected: A blank property key fails before configuration is built.
     */
    @Test
    void jdbcPropertiesRejectBlankPropertyNames() {
        Properties blankPropertyName = new Properties();
        blankPropertyName.setProperty(" ", "value");

        assertThatThrownBy(() -> DbConnectionConfig.builder().jdbcProperties(blankPropertyName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDBC property names must not be blank");
    }

    /**
     * Test: Protects the database password in the configuration string form.
     * Expected: toString does not expose the password.
     */
    @Test
    void dbConnectionConfigToStringDoesNotExposeDatabasePassword() {
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser("selectai")
                .dbPassword("database-secret")
                .jdbcUrl("jdbc:oracle:thin:@//dbhost.example:1521/service")
                .build();

        assertThat(config.toString())
                .doesNotContain("database-secret");
    }

}
