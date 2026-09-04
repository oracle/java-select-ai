/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Default {@link Credential} implementation backed by {@code DBMS_CLOUD}
 * credential procedures.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Select AI prerequisites and credentials</a>
 */
final class DefaultCredential implements Credential {
    /** Logger for credential lifecycle operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(Credential.class);
    /** PL/SQL block for username/password credential creation. */
    private static final String CREATE_USERNAME_PASSWORD_CREDENTIAL_SQL =
            "BEGIN " +
                    "  DBMS_CLOUD.CREATE_CREDENTIAL(?, ?, ?); " +
                    "END;";
    /** PL/SQL block for OCI signing-key credential creation. */
    private static final String CREATE_OCI_KEY_CREDENTIAL_SQL =
            "BEGIN " +
                    "  DBMS_CLOUD.CREATE_CREDENTIAL(?, ?, ?, ?, ?); " +
                    "END;";
    /** PL/SQL block for dropping a credential. */
    private static final String DROP_CREDENTIAL_SQL =
            "BEGIN " +
                    "  DBMS_CLOUD.DROP_CREDENTIAL(?); " +
                    "END;";
    /** SQL used to detect whether a credential exists before forced drop. */
    private static final String CREDENTIAL_EXISTS_SQL =
            "SELECT COUNT(*) FROM USER_CREDENTIALS WHERE CREDENTIAL_NAME = UPPER(?)";
    /** Provider used to obtain connections for credential operations. */
    private final ConnectionProvider connectionProvider;
    /** Credential payload bound to this instance. */
    private final CredentialConfig credentialConfig;

    /**
     * Creates a credential object backed by the supplied connection provider and
     * credential configuration.
     * <p>
     * The connection provider controls how JDBC connections are obtained for
     * credential operations. The credential configuration supplies the database
     * credential name and secret material used when creating or dropping the
     * credential.
     *
     * @param connectionProvider provider used to execute credential database operations
     * @param credentialConfig credential configuration
     * @throws IllegalArgumentException when connectionProvider, credentialConfig,
     *         or credential name is missing
     */
    DefaultCredential(ConnectionProvider connectionProvider, CredentialConfig credentialConfig) {
        if (connectionProvider == null) {
            LOGGER.error("ConnectionProvider cannot be null when instantiating Credential");
            throw new IllegalArgumentException("connectionProvider must not be null");
        }
        if (credentialConfig == null) {
            LOGGER.error("CredentialConfig cannot be null when instantiating Credential");
            throw new IllegalArgumentException("credentialConfig must not be null");
        }
        if (credentialConfig.getCredentialName() == null || credentialConfig.getCredentialName().isBlank()) {
            LOGGER.error("CredentialConfig must contain a credential name");
            throw new IllegalArgumentException("credentialConfig requires credentialName");
        }
        this.connectionProvider = connectionProvider;
        this.credentialConfig = credentialConfig;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Credential created for name: {}", credentialConfig.getCredentialName());
        }
    }

    /**
     * Creates credential in database using DBMS_CLOUD.CREATE_CREDENTIAL.
     * <p>
     * When username or password credential fields are supplied, the SDK calls
     * the username/password overload. Otherwise, it calls the OCI signing-key
     * overload using user OCID, tenancy OCID, private key, and fingerprint.
     *
     * @return {@code true} when DBMS_CLOUD.CREATE_CREDENTIAL executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean create() throws SelectAIException {
        validateCredentialConfigForCreate();
        LOGGER.debug("Invoking DBMS_CLOUD.CREATE_CREDENTIAL for credential {}", getCredentialName());
        try {
            if (usesUsernamePasswordCredential()) {
                createUsernamePasswordCredential();
            } else {
                createOciKeyCredential();
            }
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD.CREATE_CREDENTIAL failed for credential {}", getCredentialName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD.CREATE_CREDENTIAL", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    /**
     * Drops credential from database using DBMS_CLOUD.DROP_CREDENTIAL.
     *
     * @return {@code true} when DBMS_CLOUD.DROP_CREDENTIAL executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean drop() throws SelectAIException {
        return drop(false);
    }

    /**
     * Drops credential from database using DBMS_CLOUD.DROP_CREDENTIAL.
     * <p>
     * When {@code force} is {@code true}, this method treats an already absent
     * credential as success.
     *
     * @param force whether missing credentials should be treated as success
     * @return {@code true} when DBMS_CLOUD.DROP_CREDENTIAL executes successfully
     * @throws SelectAIException when operation fails
     */
    @Override
    public boolean drop(boolean force) throws SelectAIException {
        LOGGER.debug("Invoking DBMS_CLOUD.DROP_CREDENTIAL for credential {}", getCredentialName());
        try {
            connectionProvider.withConnection(connection -> {
                if (force && !credentialExists(connection)) {
                    LOGGER.info("Credential {} does not exist; forced drop treated as successful",
                            getCredentialName());
                    return true;
                }
                try (CallableStatement stmt = connection.prepareCall(DROP_CREDENTIAL_SQL)) {
                    stmt.setString(1, getCredentialName());
                    stmt.execute();
                    LOGGER.info("Successfully dropped credential {}", getCredentialName());
                    return true;
                }
            });
        } catch (SQLException e) {
            LOGGER.error("DBMS_CLOUD.DROP_CREDENTIAL failed for credential {}", getCredentialName(), e);
            throw new SelectAIException("Failed to execute DBMS_CLOUD.DROP_CREDENTIAL", e,
                    e.getErrorCode(), e.getSQLState());
        }
        return true;
    }

    ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    private void createUsernamePasswordCredential() throws SQLException, SelectAIException {
        connectionProvider.withConnection(connection -> {
            try (CallableStatement stmt = connection.prepareCall(CREATE_USERNAME_PASSWORD_CREDENTIAL_SQL)) {
                stmt.setString(1, getCredentialName());
                stmt.setString(2, credentialConfig.getUsername());
                stmt.setString(3, credentialConfig.getPassword());
                stmt.execute();
                LOGGER.info("Successfully created username/password credential {}", getCredentialName());
                return true;
            }
        });
    }

    private void createOciKeyCredential() throws SQLException, SelectAIException {
        connectionProvider.withConnection(connection -> {
            try (CallableStatement stmt = connection.prepareCall(CREATE_OCI_KEY_CREDENTIAL_SQL)) {
                stmt.setString(1, getCredentialName());
                stmt.setString(2, credentialConfig.getUserOcid());
                stmt.setString(3, credentialConfig.getTenancyOcid());
                stmt.setString(4, credentialConfig.getPrivateKey());
                stmt.setString(5, credentialConfig.getFingerprint());
                stmt.execute();
                LOGGER.info("Successfully created OCI signing-key credential {}", getCredentialName());
                return true;
            }
        });
    }

    private boolean credentialExists(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(CREDENTIAL_EXISTS_SQL)) {
            stmt.setString(1, getCredentialName());
            try (ResultSet resultSet = stmt.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private void validateCredentialConfigForCreate() {
        boolean usernamePasswordCredential = usesUsernamePasswordCredential();
        boolean anyOciField = hasText(credentialConfig.getUserOcid())
                || hasText(credentialConfig.getTenancyOcid())
                || hasText(credentialConfig.getPrivateKey())
                || hasText(credentialConfig.getFingerprint());

        if (usernamePasswordCredential && anyOciField) {
            throw new IllegalArgumentException("CredentialConfig must use either username/password or OCI key fields, not both");
        }
        if (usernamePasswordCredential) {
            if (!hasText(credentialConfig.getUsername())) {
                throw new IllegalArgumentException(
                        "CredentialConfig requires username for username/password credential");
            }
            return;
        }
        if (!anyOciField) {
            throw new IllegalArgumentException(
                    "CredentialConfig must provide username or complete OCI key fields to create credential");
        }
        if (!hasText(credentialConfig.getUserOcid())
                || !hasText(credentialConfig.getTenancyOcid())
                || !hasText(credentialConfig.getPrivateKey())
                || !hasText(credentialConfig.getFingerprint())) {
            throw new IllegalArgumentException(
                    "CredentialConfig requires userOcid, tenancyOcid, privateKey, and fingerprint for OCI key credential");
        }
    }

    private boolean usesUsernamePasswordCredential() {
        return credentialConfig.getUsername() != null || credentialConfig.getPassword() != null;
    }

    private String getCredentialName() {
        return credentialConfig.getCredentialName();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
