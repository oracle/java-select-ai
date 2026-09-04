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

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticDataParamsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Preserves configured synthetic-data parameters and normalizes priority.
     * Expected: Values are retained and priority is normalized consistently.
     */
    @Test
    void builderKeepsConfiguredValuesAndNormalizesPriority() {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(25)
                .tableStatistics(true)
                .priority(" low ")
                .comments(false)
                .build();

        assertThat(params.getSampleRows()).isEqualTo(25);
        assertThat(params.getTableStatistics()).isTrue();
        assertThat(params.getPriority()).isEqualTo("LOW");
        assertThat(params.getComments()).isFalse();
    }

    /**
     * Test: Builds synthetic-data parameters without optional values.
     * Expected: Unset optional values remain unset.
     */
    @Test
    void builderAllowsUnsetOptionalValues() {
        SyntheticDataParams params = SyntheticDataParams.builder().build();

        assertThat(params.getSampleRows()).isNull();
        assertThat(params.getTableStatistics()).isNull();
        assertThat(params.getPriority()).isNull();
        assertThat(params.getComments()).isNull();
    }

    /**
     * Test: Serializes unset parameters without adding SDK defaults.
     * Expected: Only explicitly configured values are represented.
     */
    @Test
    void unsetParametersAreOmittedFromJsonInsteadOfReceivingSdkDefaults() throws Exception {
        JsonNode json = MAPPER.readTree(SyntheticDataParams.builder().build().toJson());

        assertThat(json).isEmpty();
        assertThat(json.has("sample_rows")).isFalse();
        assertThat(json.has("table_statistics")).isFalse();
        assertThat(json.has("priority")).isFalse();
        assertThat(json.has("comments")).isFalse();
    }

    /**
     * Test: Serializes synthetic-data parameters using database field names.
     * Expected: JSON uses snake_case and omits null values.
     */
    @Test
    void toJsonSerializesSnakeCaseAndOmitsNullValues() throws Exception {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(25)
                .tableStatistics(true)
                .priority("MEDIUM")
                .build();

        JsonNode json = MAPPER.readTree(params.toJson());

        assertThat(json.get("sample_rows").asInt()).isEqualTo(25);
        assertThat(json.get("table_statistics").asBoolean()).isTrue();
        assertThat(json.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(json.has("comments")).isFalse();
    }

    /**
     * Test: Sets synthetic-data priority using the typed enum overload.
     * Expected: The enum value is retained and serialized using the database value.
     */
    @Test
    void priorityEnumSetsDatabasePriorityValue() throws Exception {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .priority(SyntheticDataParams.Priority.LOW)
                .build();

        JsonNode json = MAPPER.readTree(params.toJson());

        assertThat(params.getPriority()).isEqualTo("LOW");
        assertThat(json.get("priority").asText()).isEqualTo("LOW");
    }

    /**
     * Test: Serializes an explicitly disabled table-statistics flag.
     * Expected: The false value is retained in the JSON payload rather than omitted.
     */
    @Test
    void toJsonPreservesExplicitFalseTableStatistics() throws Exception {
        JsonNode json = MAPPER.readTree(SyntheticDataParams.builder()
                .tableStatistics(false)
                .build()
                .toJson());

        assertThat(json.get("table_statistics").asBoolean()).isFalse();
    }

    /**
     * Test: Validates the sample-row percentage bounds.
     * Expected: Values outside zero through one hundred are rejected.
     */
    @Test
    void sampleRowsMustBeBetweenZeroAndOneHundred() {
        assertThat(SyntheticDataParams.builder().sampleRows(0).build().getSampleRows()).isZero();
        assertThat(SyntheticDataParams.builder().sampleRows(100).build().getSampleRows()).isEqualTo(100);

        assertThatThrownBy(() -> SyntheticDataParams.builder().sampleRows(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleRows");

        assertThatThrownBy(() -> SyntheticDataParams.builder().sampleRows(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleRows");
    }

    /**
     * Test: Validates supported synthetic-data priority values.
     * Expected: Unsupported priority values are rejected.
     */
    @Test
    void priorityRejectsUnsupportedValues() {
        assertThatThrownBy(() -> SyntheticDataParams.builder().priority("urgent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");

        assertThatThrownBy(() -> SyntheticDataParams.builder().priority(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");

        assertThatThrownBy(() -> SyntheticDataParams.builder().priority("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }

    /**
     * Test: Normalizes priority independently of the default locale.
     * Expected: The same normalized priority is produced under a non-English locale.
     */
    @Test
    void priorityNormalizationIsIndependentOfDefaultLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            SyntheticDataParams params = SyntheticDataParams.builder()
                    .priority("HiGh")
                    .build();

            assertThat(params.getPriority()).isEqualTo("HIGH");
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
