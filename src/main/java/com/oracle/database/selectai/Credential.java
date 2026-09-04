/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai;

import com.oracle.database.selectai.model.SelectAIException;

/**
 * Contract for managing database credentials used by Select AI.
 * <p>
 * A credential is stored in the database so PL/SQL packages can authenticate to
 * an external service without the Java application sending secrets on every
 * call. Select AI profiles commonly reference credentials for AI provider API
 * access, and vector-index or summarization flows may reference credentials for
 * object storage access.
 * <p>
 * A {@code Credential} object is initialized from credential configuration and
 * is associated with the configured database credential name. Create and drop
 * operations require a valid credential name and, for creation, a complete
 * supported credential configuration. The SDK rejects invalid create
 * configuration with {@link IllegalArgumentException} before JDBC execution.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Select AI prerequisites and credentials</a>
 */
public interface Credential {
    /**
     * Creates the database credential represented by this instance.
     * <p>
     * Use this before creating profiles or vector indexes that reference the
     * credential for provider or object-storage access.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/credential/CreateCredentialSample.html">
     * CreateCredentialSample source</a>.
     *
     * @return {@code true} when credential is created successfully
     * @throws IllegalArgumentException when credential configuration is invalid,
     *         such as missing credential material, mixed credential modes, or
     *         incomplete OCI key material
     * @throws SelectAIException when credential creation fails
     */
    boolean create() throws SelectAIException;

    /**
     * Drops the database credential represented by this instance.
     * <p>
     * Dropping a credential can break profiles or indexes that still reference
     * it, so callers should remove or update dependent resources first.
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/credential/DropCredentialSample.html">
     * DropCredentialSample source</a>.
     *
     * @return {@code true} when credential is dropped successfully
     * @throws SelectAIException when credential drop fails
     */
    boolean drop() throws SelectAIException;

    /**
     * Drops the database credential represented by this instance, optionally
     * treating an already absent credential as success.
     * <p>
     * For a complete runnable sample source, see
     * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/credential/DropCredentialSample.html">
     * DropCredentialSample source</a>.
     *
     * @param force when {@code true}, return success if the credential does not exist
     * @return {@code true} when credential is dropped successfully, or when
     *         {@code force} is {@code true} and the credential is already absent
     * @throws SelectAIException when credential drop fails
     */
    boolean drop(boolean force) throws SelectAIException;
}
