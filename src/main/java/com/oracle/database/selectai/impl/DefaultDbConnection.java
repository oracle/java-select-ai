/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.SelectAIException;
import oracle.jdbc.OracleConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Default JDBC-backed {@link DbConnection} implementation used by the SDK.
 */
final class DefaultDbConnection implements DbConnection {
    /** Logger for JDBC connection initialization diagnostics. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDbConnection.class);
    /** JDBC URL used to create the connection. */
    private final String jdbcUrl;
    /** Database username used to create the connection. */
    private final String dbUser;
    /** Live JDBC connection opened during construction. */
    private final Connection connection;

    /**
     * Creates a DbConnection from validated builder configuration and initializes JDBC connection.
     *
     * @param builder builder carrying connection configuration
     * @throws SelectAIException when JDBC connection initialization fails
     */
    private DefaultDbConnection(Builder builder) throws SelectAIException {
        this.jdbcUrl = builder.jdbcUrl;
        this.dbUser = builder.dbUser;
        this.connection = initializeConnection(this.jdbcUrl, this.dbUser, builder.dbPassword,
                builder.walletPassword, builder.jdbcProperties);
    }

    /**
     * Opens a JDBC connection and wraps SQL failures with SDK-specific metadata.
     *
     * @param jdbcUrl JDBC URL
     * @param dbUser database username
     * @param dbPassword database password
     * @param walletPassword optional password for password-protected wallets
     * @param jdbcProperties additional JDBC connection properties
     * @return initialized JDBC connection
     * @throws SelectAIException when DriverManager cannot open the connection
     */
    private static Connection initializeConnection(String jdbcUrl, String dbUser, String dbPassword,
                                                   String walletPassword, Properties jdbcProperties)
            throws SelectAIException {
        try {
            Properties connectionProperties = new Properties();
            if (jdbcProperties != null) {
                connectionProperties.putAll(jdbcProperties);
            }
            connectionProperties.setProperty("user", dbUser);
            connectionProperties.setProperty("password", dbPassword);
            if (walletPassword != null && !walletPassword.isBlank()) {
                connectionProperties.setProperty(OracleConnection.CONNECTION_PROPERTY_WALLET_PASSWORD, walletPassword);
            }
            return DriverManager.getConnection(jdbcUrl, connectionProperties);
        } catch (SQLException e) {
            throw new SelectAIException("Failed to initialize database connection", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }
    /**
     * Creates an internal builder for DbConnection initialization.
     *
     * @return new {@link Builder} instance for fluent connection configuration
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Returns configured JDBC URL.
     *
     * @return JDBC URL used to initialize the connection
     */
    @Override
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Returns configured database username.
     *
     * @return database username used to initialize the connection
     */
    @Override
    public String getDbUser() {
        return dbUser;
    }

    /**
     * Returns initialized JDBC connection.
     *
     * @return live JDBC connection created during object construction
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Closes the JDBC connection owned by this DbConnection.
     * <p>
     * This method is safe to call more than once. If the connection is already
     * closed, no database call is made.
     *
     * @throws SelectAIException when the JDBC connection cannot be closed
     */
    @Override
    public void close() throws SelectAIException {
        try {
            if (!connection.isClosed()) {
                connection.close();
                LOGGER.debug("Closed SDK-owned database connection");
            } else {
                LOGGER.debug("SDK-owned database connection was already closed");
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to close database connection", e);
            throw new SelectAIException("Failed to close database connection", e,
                    e.getErrorCode(), e.getSQLState());
        }
    }

    /**
     * Internal builder used to construct {@link DbConnection} instances.
     */
    static class Builder {
        /** JDBC URL to use for the connection. */
        private String jdbcUrl;
        /** Database username to use for the connection. */
        private String dbUser;
        /** Database password to use for the connection. */
        private String dbPassword;
        /** Optional password for password-protected wallets. */
        private String walletPassword;
        /** Additional JDBC connection properties passed to DriverManager. */
        private Properties jdbcProperties = new Properties();
        /** Reserved for a caller-supplied connection in a future builder flow. */
        private Connection connection;

        /**
         * Creates an empty builder instance.
         */
        Builder() {
        }

        /**
         * Sets JDBC URL on builder.
         *
         * @param jdbcUrl JDBC URL
         * @return this builder instance
         */
        Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }

        /**
         * Sets database username on builder.
         *
         * @param dbUser database username
         * @return this builder instance
         */
        Builder dbUser(String dbUser) {
            this.dbUser = dbUser;
            return this;
        }

        /**
         * Sets database password on builder.
         *
         * @param dbPassword database password
         * @return this builder instance
         */
        Builder dbPassword(String dbPassword) {
            this.dbPassword = dbPassword;
            return this;
        }

        /**
         * Sets additional JDBC connection properties on builder.
         *
         * @param jdbcProperties additional JDBC connection properties
         * @return this builder instance
         */
        public Builder jdbcProperties(Properties jdbcProperties) {
            this.jdbcProperties = copyProperties(jdbcProperties);
            return this;
        }

        /**
         * Sets password for password-protected wallets.
         *
         * @param walletPassword wallet password
         * @return this builder instance
         */
        Builder walletPassword(String walletPassword) {
            this.walletPassword = walletPassword;
            return this;
        }

        /**
         * Builds DbConnection instance and validates required fields.
         *
         * @return initialized {@link DbConnection} with an active JDBC connection
         * @throws SelectAIException when the JDBC connection cannot be opened
         */
        DbConnection build() throws SelectAIException {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("jdbcUrl must not be null or blank");
            }
            if (dbUser == null || dbUser.isBlank()) {
                throw new IllegalArgumentException("dbUser must not be null or blank");
            }
            if (dbPassword == null || dbPassword.isBlank()) {
                throw new IllegalArgumentException("dbPassword must not be null or blank");
            }
            if (walletPassword != null && walletPassword.isBlank()) {
                throw new IllegalArgumentException("walletPassword must not be blank");
            }
            return new DefaultDbConnection(this);
        }

        private static Properties copyProperties(Properties source) {
            Properties copy = new Properties();
            if (source != null) {
                copy.putAll(source);
            }
            return copy;
        }
    }
}
