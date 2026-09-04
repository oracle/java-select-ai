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

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticDataModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Serializes synthetic-data parameters and validates their bounds.
     * Expected: Valid values use snake_case JSON and invalid bounds are rejected.
     */
    @Test
    void syntheticDataParamsSerializeSnakeCaseAndValidateBounds() throws Exception {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(25)
                .tableStatistics(true)
                .priority(" low ")
                .comments(false)
                .build();

        assertThat(params.getPriority()).isEqualTo("LOW");
        JsonNode json = MAPPER.readTree(params.toJson());
        assertThat(json.get("sample_rows").asInt()).isEqualTo(25);
        assertThat(json.get("table_statistics").asBoolean()).isTrue();
        assertThat(json.get("priority").asText()).isEqualTo("LOW");
        assertThat(json.get("comments").asBoolean()).isFalse();

        assertThatThrownBy(() -> SyntheticDataParams.builder().sampleRows(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleRows");

        assertThatThrownBy(() -> SyntheticDataParams.builder().priority("urgent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }

    /**
     * Test: Normalizes synthetic-data priority with the root locale.
     * Expected: Priority normalization is stable across locale settings.
     */
    @Test
    void syntheticDataPriorityUsesLocaleRoot() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            SyntheticDataParams params = SyntheticDataParams.builder()
                    .priority("MeDiUm")
                    .build();

            assertThat(params.getPriority()).isEqualTo("MEDIUM");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Test: Validates object-list counts and serializes batch-request objects.
     * Expected: Valid descriptors are serialized with their configured counts.
     */
    @Test
    void objectListValidatesCountsAndSerializesForBatchRequests() throws Exception {
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

        assertThat(request.getObjectList()).containsExactly(employees, departments);
        assertThatThrownBy(() -> request.getObjectList().add(employees))
                .isInstanceOf(UnsupportedOperationException.class);

        JsonNode objects = MAPPER.readTree(request.getObjectListJson());
        assertThat(objects).hasSize(2);
        assertThat(objects.get(0).get("owner").asText()).isEqualTo("HR");
        assertThat(objects.get(0).get("name").asText()).isEqualTo("EMPLOYEES");
        assertThat(objects.get(0).get("record_count").asInt()).isEqualTo(10);
        assertThat(objects.get(0).get("user_prompt").asText()).isEqualTo("Include realistic departments");
        assertThat(objects.get(1).get("record_count_percentage").asInt()).isEqualTo(50);
    }

    /**
     * Test: Preserves explicitly blank object names and database-specific count combinations.
     * Expected: Blank name, record_count, and record_count_percentage are retained in JSON.
     */
    @Test
    void objectListPreservesBlankNameAndCombinedCounts() throws Exception {
        SyntheticDataObjectList object = SyntheticDataObjectList.builder("HR", " ")
                .recordCount(10)
                .recordCountPercentage(50)
                .build();
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(object)
                .build();

        JsonNode json = MAPPER.readTree(request.getObjectListJson()).get(0);

        assertThat(object.getName()).isEqualTo(" ");
        assertThat(object.getRecordCount()).isEqualTo(10);
        assertThat(object.getRecordCountPercentage()).isEqualTo(50);
        assertThat(json.get("name").asText()).isEqualTo(" ");
        assertThat(json.get("record_count").asInt()).isEqualTo(10);
        assertThat(json.get("record_count_percentage").asInt()).isEqualTo(50);
    }

    /**
     * Test: Verifies object-list validation matches the database contract.
     * Expected: Invalid owners and non-positive counts are rejected, while optional names and
     * database-specific count combinations are preserved for DBMS_CLOUD_AI validation.
     */
    @Test
    void objectListValidatesOwnerAndCountBoundsOnly() {
        assertThatThrownBy(() -> SyntheticDataObjectList.builder(" ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");

        SyntheticDataObjectList schemaDescriptor = SyntheticDataObjectList.builder("HR", null)
                .build();
        assertThat(SyntheticDataObjectList.builder("HR", null).toJson())
                .isEqualTo("{\"owner\":\"HR\"}");

        assertThatThrownBy(() -> SyntheticDataObjectList.builder("HR", "EMPLOYEES").recordCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCount");

        SyntheticDataObjectList conflictingCounts = SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                .recordCount(1)
                .recordCountPercentage(10)
                .build();
        assertThat(conflictingCounts.getRecordCount()).isEqualTo(1);
        assertThat(conflictingCounts.getRecordCountPercentage()).isEqualTo(10);
    }

    /**
     * Test: Validates required fields of a batch synthetic-data request.
     * Expected: A profile and at least one object are required.
     */
    @Test
    void batchRequestRequiresProfileAndAtLeastOneObject() {
        SyntheticDataObjectList object = SyntheticDataObjectList.builder("HR", "EMPLOYEES").build();

        SyntheticDataParams params = SyntheticDataParams.builder().comments(true).build();
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(object)
                .params(params)
                .build();

        assertThat(request.getParamsJson()).isEqualTo(params.toJson());

        assertThatThrownBy(() -> SyntheticDataBatchRequest.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one object");

        assertThatThrownBy(() -> SyntheticDataBatchRequest.builder().addObject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("object");
    }

    /**
     * Test: Carries optional parameters JSON in a single synthetic-data request.
     * Expected: The configured parameters are available on the request.
     */
    @Test
    void singleRequestCarriesOptionalParamsJson() {
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

        assertThatThrownBy(() -> SyntheticDataSingleRequest.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectName");

        assertThatThrownBy(() -> SyntheticDataSingleRequest.builder("EMPLOYEES").recordCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordCount");
    }

}
