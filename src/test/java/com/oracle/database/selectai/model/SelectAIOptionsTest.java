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

class SelectAIOptionsTest {

    /**
     * Test: Represents the default and JDBC no-timeout settings.
     * Expected: Defaults and an explicit zero both expose the documented timeout values.
     */
    @Test
    void defaultsAndZeroTimeoutAreRepresented() {
        assertThat(SelectAIOptions.defaults().getQueryTimeoutSeconds()).isNull();
        assertThat(SelectAIOptions.builder().queryTimeoutSeconds(0).build().getQueryTimeoutSeconds())
                .isZero();
    }

    /**
     * Test: Retains a positive query timeout.
     * Expected: The configured number of seconds is returned unchanged.
     */
    @Test
    void builderRetainsPositiveQueryTimeout() {
        assertThat(SelectAIOptions.builder().queryTimeoutSeconds(4).build().getQueryTimeoutSeconds())
                .isEqualTo(4);
    }

    /**
     * Test: Rejects negative query timeouts.
     * Expected: Negative values throw IllegalArgumentException.
     */
    @Test
    void builderRejectsNegativeQueryTimeout() {
        assertThatThrownBy(() -> SelectAIOptions.builder().queryTimeoutSeconds(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queryTimeoutSeconds");
    }
}
