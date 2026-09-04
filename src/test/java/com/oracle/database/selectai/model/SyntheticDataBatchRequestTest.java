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

class SyntheticDataBatchRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Builds a batch request with its required object list.
     * Expected: Building without an object is rejected.
     */
    @Test
    void builderRequiresAtLeastOneObject() {
        assertThatThrownBy(() -> SyntheticDataBatchRequest.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one object");
    }

    /**
     * Test: Rejects a null object added to a batch request.
     * Expected: addObject throws an IllegalArgumentException.
     */
    @Test
    void addObjectRejectsNullObjects() {
        assertThatThrownBy(() -> SyntheticDataBatchRequest.builder().addObject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("object");
    }

    /**
     * Test: Preserves batch object insertion order and list immutability.
     * Expected: Objects are returned in insertion order through an unmodifiable list.
     */
    @Test
    void builderKeepsObjectsInInsertionOrderAndExposesUnmodifiableList() {
        SyntheticDataObjectList employees = SyntheticDataObjectList.builder("HR", "EMPLOYEES").build();
        SyntheticDataObjectList departments = SyntheticDataObjectList.builder("HR", "DEPARTMENTS").build();

        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(employees)
                .addObject(departments)
                .build();

        assertThat(request.getObjectList()).containsExactly(employees, departments);
        assertThatThrownBy(() -> request.getObjectList().add(employees))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Test: Serializes configured batch objects to object-list JSON.
     * Expected: The JSON contains the configured objects and their fields.
     */
    @Test
    void getObjectListJsonSerializesConfiguredObjects() throws Exception {
        SyntheticDataObjectList employees = SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                .recordCount(10)
                .userPrompt("Include realistic departments")
                .build();
        SyntheticDataObjectList departments = SyntheticDataObjectList.builder("HR", "DEPARTMENTS")
                .recordCountPercentage(50)
                .build();

        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(employees)
                .addObject(departments)
                .build();

        JsonNode objects = MAPPER.readTree(request.getObjectListJson());

        assertThat(objects).hasSize(2);
        assertThat(objects.get(0).get("owner").asText()).isEqualTo("HR");
        assertThat(objects.get(0).get("name").asText()).isEqualTo("EMPLOYEES");
        assertThat(objects.get(0).get("record_count").asInt()).isEqualTo(10);
        assertThat(objects.get(0).get("user_prompt").asText()).isEqualTo("Include realistic departments");
        assertThat(objects.get(1).get("record_count_percentage").asInt()).isEqualTo(50);
    }

    /**
     * Test: Serializes a batch object without optional count fields.
     * Expected: JSON generation succeeds and omits unset count values.
     */
    @Test
    void getObjectListJsonAllowsObjectWithoutRecordCountOrPercentage() throws Exception {
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES").build())
                .build();

        JsonNode object = MAPPER.readTree(request.getObjectListJson()).get(0);

        assertThat(object.get("owner").asText()).isEqualTo("HR");
        assertThat(object.get("name").asText()).isEqualTo("EMPLOYEES");
        assertThat(object.has("record_count")).isFalse();
        assertThat(object.has("record_count_percentage")).isFalse();
    }

    /**
     * Test: Serializes supplied synthetic-data parameters.
     * Expected: getParamsJson returns the parameter JSON.
     */
    @Test
    void getParamsJsonReturnsParamsSerializationWhenProvided() {
        SyntheticDataParams params = SyntheticDataParams.builder().comments(true).build();

        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES").build())
                .params(params)
                .build();

        assertThat(request.getParams()).isSameAs(params);
        assertThat(request.getParamsJson()).isEqualTo(params.toJson());
    }

    /**
     * Test: Reads parameters from a batch request with no parameters.
     * Expected: getParamsJson returns null.
     */
    @Test
    void getParamsJsonReturnsNullWhenParamsAreUnset() {
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES").build())
                .build();

        assertThat(request.getParamsJson()).isNull();
        assertThat(request.getParams()).isNull();
    }
}
