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

class SummaryParamsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Serializes all typed summary parameters using database field names.
     * Expected: JSON contains each configured parameter in snake_case.
     */
    @Test
    void builderSerializesAllTypedSummaryParametersAsSnakeCase() throws Exception {
        SummaryParams params = SummaryParams.builder()
                .minWords(20)
                .maxWords(80)
                .summaryStyle(SummaryParams.Style.LIST)
                .chunkProcessingMethod(SummaryParams.ChunkProcessingMethod.MAP_REDUCE)
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.LOW)
                .build();

        JsonNode json = MAPPER.readTree(params.toJson());

        assertThat(json.get("min_words").asInt()).isEqualTo(20);
        assertThat(json.get("max_words").asInt()).isEqualTo(80);
        assertThat(json.get("summary_style").asText()).isEqualTo("list");
        assertThat(json.get("chunk_processing_method").asText()).isEqualTo("map_reduce");
        assertThat(json.get("extractiveness_level").asText()).isEqualTo("low");
    }

    /**
     * Test: Validates summary word bounds and required parameters.
     * Expected: Invalid bounds and an empty parameter set are rejected.
     */
    @Test
    void builderValidatesWordBoundsAndRequiresAtLeastOneParameter() {
        assertThatThrownBy(() -> SummaryParams.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> SummaryParams.builder().minWords(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minWords");
        assertThatThrownBy(() -> SummaryParams.builder().maxWords(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxWords");
        assertThatThrownBy(() -> SummaryParams.builder().minWords(81).maxWords(80).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minWords");
    }

    /**
     * Test: Preserve valid word-count boundaries and the remaining enum wire values.
     * Expected: Zero and one are accepted, and all configured values are exposed and serialized as documented.
     */
    @Test
    void builderAcceptsBoundaryCountsAndSerializesRemainingEnumValues() throws Exception {
        SummaryParams params = SummaryParams.builder()
                .minWords(0)
                .maxWords(1)
                .summaryStyle(SummaryParams.Style.PARAGRAPH)
                .chunkProcessingMethod(SummaryParams.ChunkProcessingMethod.ITERATIVE_REFINEMENT)
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.HIGH)
                .build();

        assertThat(params.getMinWords()).isZero();
        assertThat(params.getMaxWords()).isOne();
        assertThat(params.getSummaryStyle()).isEqualTo("paragraph");
        assertThat(params.getSummaryStyleEnum()).isEqualTo(SummaryParams.Style.PARAGRAPH);
        assertThat(params.getChunkProcessingMethod()).isEqualTo("iterative_refinement");
        assertThat(params.getChunkProcessingMethodEnum())
                .isEqualTo(SummaryParams.ChunkProcessingMethod.ITERATIVE_REFINEMENT);
        assertThat(params.getExtractivenessLevel()).isEqualTo("high");
        assertThat(params.getExtractivenessLevelEnum()).isEqualTo(SummaryParams.ExtractivenessLevel.HIGH);

        JsonNode json = MAPPER.readTree(params.toJson());
        assertThat(json.size()).isEqualTo(5);
        assertThat(json.get("min_words").asInt()).isZero();
        assertThat(json.get("max_words").asInt()).isOne();
        assertThat(json.get("summary_style").asText()).isEqualTo("paragraph");
        assertThat(json.get("chunk_processing_method").asText()).isEqualTo("iterative_refinement");
        assertThat(json.get("extractiveness_level").asText()).isEqualTo("high");
    }

    /**
     * Test: Serializes the medium extractiveness level using its database wire value.
     * Expected: The getter and JSON field contain {@code medium}, with no other attributes
     * injected.
     */
    @Test
    void builderSerializesMediumExtractivenessLevelWithoutInjectingOtherAttributes()
            throws Exception {
        SummaryParams params = SummaryParams.builder()
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.MEDIUM)
                .build();

        assertThat(params.getExtractivenessLevel()).isEqualTo("medium");
        assertThat(params.getExtractivenessLevelEnum()).isEqualTo(SummaryParams.ExtractivenessLevel.MEDIUM);

        JsonNode json = MAPPER.readTree(params.toJson());
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get("extractiveness_level").asText()).isEqualTo("medium");
    }
}
