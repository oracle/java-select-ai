/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.util.Locale;
import java.util.Properties;

/**
 * Immutable configuration for creating an SDK-owned Oracle JDBC connection.
 * <p>
 * Use this configuration with {@link com.oracle.database.selectai.SelectAI#create(DbConnectionConfig)}
 * or {@link com.oracle.database.selectai.DatabaseAdmin#create(DbConnectionConfig)} when the
 * SDK should open and own one JDBC connection. For pooled or multi-threaded
 * applications, prefer the {@link javax.sql.DataSource}-based factory methods.
 * <p>
 * Password fields are retained in memory as {@link String} values so they can
 * be passed to Oracle JDBC. Do not log, persist, or expose this configuration
 * in diagnostics.
 */
public final class DbConnectionConfig {

    /** Database username used to connect to Autonomous Database. */
    private final String dbUser;
    /** Database password used to connect to Autonomous Database. */
    private final String dbPassword;
    /** Optional password for password-protected wallets. */
    private final String walletPassword;
    /** Full JDBC URL used when opening the SDK-owned JDBC connection. */
    private final String jdbcUrl;
    /** Additional JDBC connection properties passed to DriverManager. */
    private final Properties jdbcProperties;

    private DbConnectionConfig(Builder builder) {
        this.dbUser = builder.dbUser;
        this.dbPassword = builder.dbPassword;
        this.walletPassword = builder.walletPassword;
        this.jdbcUrl = builder.jdbcUrl;
        this.jdbcProperties = copyProperties(builder.jdbcProperties);
    }

    /**
     * Returns the database username.
     *
     * @return configured database username
     */
    public String getDbUser() {
        return dbUser;
    }

    /**
     * Returns the database password.
     * <p>
     * This value is sensitive. Do not log, persist, or expose it in diagnostics.
     *
     * @return configured database password
     */
    public String getDbPassword() {
        return dbPassword;
    }

    /**
     * Returns the password for a password-protected wallet.
     * <p>
     * This value is sensitive. Do not log, persist, or expose it in diagnostics.
     *
     * @return configured wallet password, or {@code null} when the wallet is not password-protected
     */
    public String getWalletPassword() {
        return walletPassword;
    }

    /**
     * Returns the JDBC URL.
     *
     * @return configured JDBC URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }


    /**
     * Returns additional JDBC connection properties.
     * <p>
     * The returned {@link Properties} object is a defensive copy. Changes to it
     * do not affect this connection configuration. Property values may contain
     * sensitive data, so callers should not log or expose the returned object.
     *
     * @return configured JDBC properties; never {@code null}
     */
    public Properties getJdbcProperties() {
        return copyProperties(jdbcProperties);
    }

    /**
     * Creates a builder for database connection configuration.
     * <p>
     * The builder does not provide default credentials, wallet locations, or
     * database names. Callers must supply connection values explicitly.
     *
     * @return new builder with no connection values populated
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DbConnectionConfig}.
     */
    public static final class Builder {

        /** Database username to place in the final connection configuration. */
        private String dbUser;
        /** Database password to place in the final connection configuration. */
        private String dbPassword;
        /** Optional password for password-protected wallets. */
        private String walletPassword;
        /** JDBC URL used by the final connection configuration. */
        private String jdbcUrl;
        /** Additional JDBC connection properties passed to DriverManager. */
        private Properties jdbcProperties = new Properties();


        private Builder() {
        }

        /**
         * Sets the database username.
         *
         * @param dbUser database username
         * @return this builder instance
         */
        public Builder dbUser(String dbUser) {
            this.dbUser = dbUser;
            return this;
        }

        /**
         * Sets the database password.
         *
         * @param dbPassword database password
         * @return this builder instance
         */
        public Builder dbPassword(String dbPassword) {
            this.dbPassword = dbPassword;
            return this;
        }

        /**
         * Sets the password for a password-protected wallet.
         * <p>
         * The SDK passes this value to Oracle JDBC as
         * {@code oracle.net.wallet_password}; it is not appended to the JDBC URL.
         *
         * @param walletPassword wallet password, required only for password-protected wallets
         * @return this builder instance
         */
        public Builder walletPassword(String walletPassword) {
            this.walletPassword = walletPassword;
            return this;
        }

        /**
         * Sets an explicit JDBC URL.
         * <p>
         * Supported URL forms include Oracle JDBC Thin Easy Connect/Easy
         * Connect Plus, TNS aliases, full connection descriptors, TCPS
         * configurations, and wallet/TNS_ADMIN based Autonomous Database URLs.
         * This builder validates only the SDK-owned minimum contract: the URL
         * must be an Oracle Thin JDBC URL with a non-blank connect target after
         * {@code @}. Driver-level network, wallet, TLS, Kerberos, RADIUS, and
         * token-authentication requirements are validated by Oracle JDBC and
         * the database environment.
         *
         * @param jdbcUrl full JDBC URL
         * @return this builder instance
         */
        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = normalize(jdbcUrl);
            return this;
        }

        /**
         * Sets additional JDBC properties to use when creating the database
         * connection through {@code DriverManager}.
         *
         * <p>The supplied properties are validated and defensively copied so that
         * subsequent modifications to the caller's {@link Properties} instance do
         * not affect this configuration. If {@code jdbcProperties} is {@code null},
         * an empty set of JDBC properties is used.</p>
         *
         * <p>The SDK owns the {@code user} and {@code password} properties from
         * {@link #dbUser(String)} and {@link #dbPassword(String)}. Supplying those
         * keys through this method is rejected. Other driver-specific property
         * names and values are passed through to Oracle JDBC for validation. Do not
         * log properties that contain sensitive values.</p>
         *
         * @param jdbcProperties additional JDBC connection properties, or
         *                       {@code null} to use no additional properties
         * @return this builder instance
         * @throws IllegalArgumentException if the supplied properties contain
         *                                  blank property names or SDK-owned
         *                                  {@code user} or {@code password} keys
         */
        public Builder jdbcProperties(Properties jdbcProperties) {
            if (jdbcProperties == null) {
                this.jdbcProperties = new Properties();
                return this;
            }
            validateJdbcProperties(jdbcProperties);
            this.jdbcProperties = copyProperties(jdbcProperties);
            return this;
        }

        /**
         * Validates required connection fields and builds the immutable config.
         *
         * @return immutable DbConnectionConfig built from validated builder state
         */
        public DbConnectionConfig build() {
            if (dbUser == null || dbUser.isBlank()) {
                throw new IllegalArgumentException("dbUser must not be null or blank");
            }
            if (dbPassword == null || dbPassword.isBlank()) {
                throw new IllegalArgumentException("dbPassword must not be null or blank");
            }
            if (walletPassword != null && walletPassword.isBlank()) {
                throw new IllegalArgumentException("walletPassword must not be blank");
            }
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("jdbcUrl must not be null or blank");
            }
            validateJdbcUrl(jdbcUrl);
            return new DbConnectionConfig(this);
        }

        private static String normalize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private static void validateJdbcUrl(String jdbcUrl) {
            String normalized = normalize(jdbcUrl);
            if (normalized == null) {
                throw new IllegalArgumentException("jdbcUrl must not be null or blank");
            }
            String lowerCaseUrl = normalized.toLowerCase(Locale.ROOT);
            String prefix = "jdbc:oracle:thin:@";
            if (!lowerCaseUrl.startsWith(prefix)) {
                throw new IllegalArgumentException("jdbcUrl must start with jdbc:oracle:thin:@");
            }
            String connectTarget = normalized.substring(prefix.length()).trim();
            if (connectTarget.isEmpty()) {
                throw new IllegalArgumentException("jdbcUrl must include a connect target after jdbc:oracle:thin:@");
            }
        }

        private static void validateJdbcProperties(Properties properties) {
            if (properties == null) {
                return;
            }
            for (Object key : properties.keySet()) {
                String normalizedKey = String.valueOf(key).trim().toLowerCase(Locale.ROOT);
                if (normalizedKey.isBlank()) {
                    throw new IllegalArgumentException("JDBC property names must not be blank");
                }
                if ("user".equals(normalizedKey) || "password".equals(normalizedKey)) {
                    throw new IllegalArgumentException(
                            "JDBC properties must not contain user or password");
                }
            }
        }
    }
    private static Properties copyProperties(Properties source) {
        Properties copy = new Properties();
        if (source != null) {
            copy.putAll(source);
        }
        return copy;
    }
}
