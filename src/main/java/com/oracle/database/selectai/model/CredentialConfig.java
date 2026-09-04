/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

/**
 * Configuration used to create an Oracle {@code DBMS_CLOUD} credential.
 * <p>
 * Select AI profiles reference credentials so the database can authenticate to
 * an AI provider or object storage service. This configuration supports both
 * username/password credentials and OCI API signing key credentials. Secret
 * values are passed to the database credential creation call; they are not used
 * directly by Java after the credential has been created.
 * <p>
 * Secret values are retained in memory as {@link String} values so they can be
 * bound to {@code DBMS_CLOUD.CREATE_CREDENTIAL}. The SDK does not log password
 * or private-key values. Applications should not log, persist, or expose this
 * configuration in diagnostics.
 * <p>
 * A name-only configuration can be used to reference or drop an existing
 * credential. Credential creation validates that the configuration contains
 * either username/password fields or complete OCI signing-key fields, but not
 * both.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Select AI prerequisites and credentials</a>
 */
public final class CredentialConfig {

    /** Database credential name passed to {@code DBMS_CLOUD.CREATE_CREDENTIAL}. */
    private final String credentialName;
    /** Username used for username/password cloud service credentials. */
    private final String username;
    /** Password used for username/password cloud service credentials. */
    private final String password;
    /** OCI user OCID used as the credential username. */
    private final String userOcid;
    /** OCI tenancy OCID associated with the signing key. */
    private final String tenancyOcid;
    /** Private key material used by Oracle Database to sign OCI requests. */
    private final String privateKey;
    /** Fingerprint for the public key uploaded to OCI. */
    private final String fingerprint;

    private CredentialConfig(Builder builder) {
        this.credentialName = builder.credentialName;
        this.username = builder.username;
        this.password = builder.password;
        this.userOcid = builder.userOcid;
        this.tenancyOcid = builder.tenancyOcid;
        this.privateKey = builder.privateKey;
        this.fingerprint = builder.fingerprint;
    }

    /**
     * Creates a builder for a credential with the required database credential name.
     *
     * @param credentialName database credential name
     * @return builder initialized with mandatory credential name
     * @throws IllegalArgumentException when {@code credentialName} is null or blank
     */
    public static Builder builder(String credentialName) {
        return new Builder(credentialName);
    }

    /**
     * Creates a username/password credential configuration.
     *
     * @param credentialName database credential name
     * @param username cloud service username
     * @param password cloud service password, or {@code null} when the target
     *                 service does not require one
     * @return credential configuration for username/password credential creation
     * @throws IllegalArgumentException when {@code credentialName} is null or blank
     */
    public static CredentialConfig usernamePassword(String credentialName,
                                                    String username,
                                                    String password) {
        return builder(credentialName)
                .username(username)
                .password(password)
                .build();
    }

    /**
     * Creates an OCI signing-key credential configuration.
     *
     * @param credentialName database credential name
     * @param userOcid OCI user OCID
     * @param tenancyOcid OCI tenancy OCID
     * @param privateKey PEM private key material
     * @param fingerprint OCI API key fingerprint
     * @return credential configuration for OCI signing-key credential creation
     * @throws IllegalArgumentException when {@code credentialName} is null or blank
     */
    public static CredentialConfig ociSigningKey(String credentialName,
                                                 String userOcid,
                                                 String tenancyOcid,
                                                 String privateKey,
                                                 String fingerprint) {
        return builder(credentialName)
                .userOcid(userOcid)
                .tenancyOcid(tenancyOcid)
                .privateKey(privateKey)
                .fingerprint(fingerprint)
                .build();
    }

    /**
     * Returns the database credential name.
     *
     * @return credential name
     */
    public String getCredentialName() {
        return credentialName;
    }

    /**
     * Returns the username used for username/password cloud service credentials.
     *
     * @return username, or {@code null} when not set
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password.
     * <p>
     * This value is sensitive. Do not log, persist, or expose it in diagnostics.
     * The SDK uses it only to bind DBMS_CLOUD.CREATE_CREDENTIAL parameters.
     *
     * @return password, or {@code null} when not set.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the OCI user OCID stored in the credential.
     *
     * @return OCI user OCID, or {@code null} when not set
     */
    public String getUserOcid() {
        return userOcid;
    }

    /**
     * Returns the OCI tenancy OCID stored in the credential.
     *
     * @return OCI tenancy OCID, or {@code null} when not set
     */
    public String getTenancyOcid() {
        return tenancyOcid;
    }

    /**
     * Returns the private key material stored in the credential request.
     * <p>
     * This value is sensitive. Do not log, persist, or expose it in diagnostics.
     * The SDK uses it only to bind DBMS_CLOUD.CREATE_CREDENTIAL parameters.
     *
     * @return private key material, or {@code null} when not set
     */
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * Returns the fingerprint for the OCI API signing key.
     *
     * @return key fingerprint, or {@code null} when not set
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Builder for {@link CredentialConfig}.
     */
    public static final class Builder {

        /** Required database credential name. */
        private final String credentialName;
        /** Optional username for username/password credentials. */
        private String username;
        /** Optional password for username/password credentials. */
        private String password;
        /** Optional OCI user OCID. */
        private String userOcid;
        /** Optional OCI tenancy OCID. */
        private String tenancyOcid;
        /** Optional private key material. */
        private String privateKey;
        /** Optional key fingerprint. */
        private String fingerprint;

        private Builder(String credentialName) {
            if (credentialName == null || credentialName.isBlank()) {
                throw new IllegalArgumentException("credentialName must not be null or blank");
            }
            this.credentialName = credentialName;
        }

        /**
         * Sets the username for username/password cloud service credentials.
         * <p>
         * A blank username is invalid when this configuration is used to create
         * a username/password credential.
         *
         * @param username cloud service username
         * @return this builder instance
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the password for username/password cloud service credentials.
         * <p>
         * The password is optional for {@code DBMS_CLOUD.CREATE_CREDENTIAL};
         * when not supplied, the SDK passes {@code null} to the database.
         * Supplying a password without a username is invalid when this
         * configuration is used to create a credential.
         *
         * @param password cloud service password
         * @return this builder instance
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the OCI user OCID.
         * <p>
         * When this configuration is used to create an OCI signing-key
         * credential, user OCID, tenancy OCID, private key, and fingerprint must
         * all be supplied.
         *
         * @param userOcid OCI user OCID
         * @return this builder instance
         */
        public Builder userOcid(String userOcid) {
            this.userOcid = userOcid;
            return this;
        }

        /**
         * Sets the OCI tenancy OCID.
         * <p>
         * When this configuration is used to create an OCI signing-key
         * credential, user OCID, tenancy OCID, private key, and fingerprint must
         * all be supplied.
         *
         * @param tenancyOcid OCI tenancy OCID
         * @return this builder instance
         */
        public Builder tenancyOcid(String tenancyOcid) {
            this.tenancyOcid = tenancyOcid;
            return this;
        }

        /**
         * Sets the PEM private key material.
         * <p>
         * This value is sensitive. Do not log, persist, or expose it in
         * diagnostics. When this configuration is used to create an OCI
         * signing-key credential, user OCID, tenancy OCID, private key, and
         * fingerprint must all be supplied.
         *
         * @param privateKey private key material
         * @return this builder instance
         */
        public Builder privateKey(String privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Sets the OCI public key fingerprint.
         * <p>
         * When this configuration is used to create an OCI signing-key
         * credential, user OCID, tenancy OCID, private key, and fingerprint must
         * all be supplied.
         *
         * @param fingerprint OCI API key fingerprint
         * @return this builder instance
         */
        public Builder fingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        /**
         * Builds the immutable credential configuration.
         * <p>
         * This method validates only the credential name supplied to
         * {@link #builder(String)}. Complete create-time validation is performed
         * by {@code Credential.create()} so name-only configurations remain
         * usable for credential references and drop operations.
         *
         * @return immutable CredentialConfig assembled from current builder state
         */
        public CredentialConfig build() {
            return new CredentialConfig(this);
        }
    }

}
