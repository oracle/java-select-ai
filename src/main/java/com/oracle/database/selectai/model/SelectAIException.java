/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

/**
 * Checked exception used by the SDK for database, JDBC, and Select AI
 * operation failures.
 * <p>
 * When the failure originates from {@link java.sql.SQLException}, the SDK
 * preserves the original exception as the cause and copies the JDBC error code
 * and SQLState when available.
 */
public class SelectAIException extends Exception {

    /** Serialization version for the checked exception type. */
    private static final long serialVersionUID = 1L;

    /** JDBC error code copied from {@link java.sql.SQLException}. */
    private final Integer errorCode;
    /** SQLState copied from {@link java.sql.SQLException}. */
    private final String sqlState;

    /**
     * Creates an exception with only a message.
     *
     * @param message error message
     */
    public SelectAIException(String message) {
        this(message, null, null, null);
    }

    /**
     * Creates an exception with a message and root cause.
     *
     * @param message error message
     * @param cause root cause
     */
    public SelectAIException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    /**
     * Creates an exception with database error metadata.
     *
     * @param message error message
     * @param cause root cause
     * @param errorCode JDBC error code
     * @param sqlState SQLState value
     */
    public SelectAIException(String message, Throwable cause, Integer errorCode, String sqlState) {
        super(message, cause);
        this.errorCode = errorCode;
        this.sqlState = sqlState;
    }

    /**
     * Returns the JDBC error code when the failure came from JDBC.
     *
     * @return JDBC error code, or {@code null} when unavailable
     */
    public Integer getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the SQLState when the failure came from JDBC.
     *
     * @return SQLState string, or {@code null} when unavailable
     */
    public String getSqlState() {
        return sqlState;
    }
}
