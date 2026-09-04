/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticDataObjectListTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Verifies that owner-only descriptors are accepted while blank owners are rejected.
     * Expected: A blank owner is rejected, while a null name is accepted and omitted from JSON.
     */
    @Test
    void ownerIsRequiredAndNameIsOptional() {
        assertThatThrownBy(() -> SyntheticDataObjectList.builder(" ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");

        SyntheticDataObjectList schemaDescriptor = SyntheticDataObjectList.builder("HR", null)
                .build();

        assertThat(schemaDescriptor.getOwner()).isEqualTo("HR");
        assertThat(schemaDescriptor.getName()).isNull();
        assertThat(SyntheticDataObjectList.builder("HR", null).toJson())
                .isEqualTo("{\"owner\":\"HR\"}");
    }

    /**
     * Test: Retains the configured object descriptor fields.
     * Expected: The built descriptor exposes the supplied values unchanged.
     */
    @Test
    void builderKeepsConfiguredDescriptorFields() {
        SyntheticDataObjectList object = SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                .recordCount(10)
                .userPrompt("Include realistic departments")
                .build();

        assertThat(object.getOwner()).isEqualTo("HR");
        assertThat(object.getName()).isEqualTo("EMPLOYEES");
        assertThat(object.getRecordCount()).isEqualTo(10);
        assertThat(object.getRecordCountPercentage()).isNull();
        assertThat(object.getUserPrompt()).isEqualTo("Include realistic departments");
    }

    /**
     * Test: Verifies owner-only descriptor serialization for full-schema synthetic-data generation.
     * Expected: The batch object list contains the owner and omits the optional name.
     */
    @Test
    void ownerOnlyDescriptorSerializesForFullSchemaGeneration() throws Exception {
        SyntheticDataObjectList schemaDescriptor = SyntheticDataObjectList.builder("HR", null)
                .build();
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(schemaDescriptor)
                .build();

        JsonNode objectList = MAPPER.readTree(request.getObjectListJson());

        assertThat(objectList).hasSize(1);
        assertThat(objectList.get(0).get("owner").asText()).isEqualTo("HR");
        assertThat(objectList.get(0).has("name")).isFalse();
    }

    /**
     * Test: Validates exact and percentage record counts.
     * Expected: Supplied exact counts and percentages must be positive.
     */
    @Test
    void recordCountsMustBePositiveWhenProvided() {
        assertThatThrownBy(() -> SyntheticDataObjectList.builder("HR", "EMPLOYEES").recordCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCount");

        assertThatThrownBy(() -> SyntheticDataObjectList.builder("HR", "EMPLOYEES").recordCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCount");

        assertThatThrownBy(() -> SyntheticDataObjectList.builder("HR", "EMPLOYEES").recordCountPercentage(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCountPercentage");

        assertThatThrownBy(() -> SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                .recordCountPercentage(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCountPercentage");
    }

    /**
     * Test: Allows a record-count percentage above one hundred.
     * Expected: The configured percentage is retained without an upper-bound rejection.
     */
    @Test
    void recordCountPercentageAllowsValuesGreaterThanOneHundred() {
        assertThat(SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                .recordCountPercentage(101)
                .build()
                .getRecordCountPercentage())
                .isEqualTo(101);
    }

    /**
     * Test: Verifies conflicting record-count options are deferred to database validation.
     * Expected: The SDK preserves both values and lets DBMS_CLOUD_AI validate the combination.
     */
    @Test
    void conflictingRecordCountsAreDeferredToDatabaseValidation() {
        SyntheticDataObjectList object = SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                .recordCount(1)
                .recordCountPercentage(10)
                .build();

        assertThat(object.getRecordCount()).isEqualTo(1);
        assertThat(object.getRecordCountPercentage()).isEqualTo(10);
    }

    /**
     * Test: Serializes an object descriptor using database field names.
     * Expected: JSON uses snake_case keys and omits null values.
     */
    @Test
    void builderToJsonSerializesSnakeCaseAndOmitsNullValues() throws Exception {
        String jsonText = SyntheticDataObjectList.builder("HR", "DEPARTMENTS")
                .recordCountPercentage(50)
                .toJson();

        JsonNode json = MAPPER.readTree(jsonText);

        assertThat(json.get("owner").asText()).isEqualTo("HR");
        assertThat(json.get("name").asText()).isEqualTo("DEPARTMENTS");
        assertThat(json.get("record_count_percentage").asInt()).isEqualTo(50);
        assertThat(json.has("record_count")).isFalse();
        assertThat(json.has("user_prompt")).isFalse();
    }
}
