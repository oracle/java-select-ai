/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class SelectAIExceptionTest {

    /**
     * Test: Construct an exception with only a message.
     * Expected: The message is retained and cause, error code, and SQL state are null.
     */
    @Test
    void messageOnlyConstructorKeepsMessageAndLeavesMetadataEmpty() {
        SelectAIException exception = new SelectAIException("profile not found");

        assertThat(exception).hasMessage("profile not found");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getErrorCode()).isNull();
        assertThat(exception.getSqlState()).isNull();
    }

    /**
     * Test: Construct an exception with a message and root cause.
     * Expected: Both message and cause are retained and database metadata remains null.
     */
    @Test
    void messageAndCauseConstructorKeepsRootCause() {
        IllegalStateException cause = new IllegalStateException("jdbc failed");
        SelectAIException exception = new SelectAIException("failed to load profile", cause);

        assertThat(exception).hasMessage("failed to load profile");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getErrorCode()).isNull();
        assertThat(exception.getSqlState()).isNull();
    }

    /**
     * Test: Construct an exception with database error metadata.
     * Expected: Message, cause, error code, and SQL state are exposed unchanged.
     */
    @Test
    void metadataConstructorExposesErrorCodeAndSqlState() {
        SQLException cause = new SQLException("database rejected request", "42000", 20047);
        SelectAIException exception = new SelectAIException(
                "Failed to execute DBMS_CLOUD_AI.CREATE_PROFILE",
                cause,
                20047,
                "42000");

        assertThat(exception).hasMessage("Failed to execute DBMS_CLOUD_AI.CREATE_PROFILE");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getErrorCode()).isEqualTo(20047);
        assertThat(exception.getSqlState()).isEqualTo("42000");
    }
}
