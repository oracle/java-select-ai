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

class SyntheticDataSingleRequestTest {

    /**
     * Test: Rejects a missing or blank synthetic-data object name.
     * Expected: The builder throws an IllegalArgumentException identifying objectName.
     */
    @Test
    void builderRequiresObjectName() {
        assertThatThrownBy(() -> SyntheticDataSingleRequest.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectName");

        assertThatThrownBy(() -> SyntheticDataSingleRequest.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectName");
    }

    /**
     * Test: Retains the configured fields of a synthetic-data single request.
     * Expected: The built request exposes the supplied values unchanged.
     */
    @Test
    void builderKeepsConfiguredRequestFields() {
        SyntheticDataParams params = SyntheticDataParams.builder().sampleRows(10).build();

        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder("EMPLOYEES")
                .ownerName("HR")
                .recordCount(20)
                .userPrompt("Include salary bands")
                .params(params)
                .build();

        assertThat(request.getObjectName()).isEqualTo("EMPLOYEES");
        assertThat(request.getOwnerName()).isEqualTo("HR");
        assertThat(request.getRecordCount()).isEqualTo(20);
        assertThat(request.getUserPrompt()).isEqualTo("Include salary bands");
        assertThat(request.getParams()).isSameAs(params);
        assertThat(request.getParamsJson()).isEqualTo(params.toJson());
    }

    /**
     * Test: Builds a request without optional fields.
     * Expected: Unset optional values remain unset.
     */
    @Test
    void builderAllowsOptionalFieldsToRemainUnset() {
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder("EMPLOYEES").build();

        assertThat(request.getObjectName()).isEqualTo("EMPLOYEES");
        assertThat(request.getOwnerName()).isNull();
        assertThat(request.getRecordCount()).isNull();
        assertThat(request.getUserPrompt()).isNull();
        assertThat(request.getParams()).isNull();
        assertThat(request.getParamsJson()).isNull();
    }

    /**
     * Test: Validates an explicitly supplied record count.
     * Expected: Zero or negative record counts are rejected.
     */
    @Test
    void recordCountMustBePositiveWhenProvided() {
        assertThatThrownBy(() -> SyntheticDataSingleRequest.builder("EMPLOYEES").recordCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCount");

        assertThatThrownBy(() -> SyntheticDataSingleRequest.builder("EMPLOYEES").recordCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCount");
    }
}
