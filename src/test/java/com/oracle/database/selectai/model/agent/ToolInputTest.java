/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolInputTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serializesInputNamesAndDescriptionsWithoutExtraFields() throws Exception {
        String description = "Only supported values are \"Enabled\" and \"Disabled\".\nPath: C:\\data";
        ToolInput input = ToolInput.builder().name("data_guard").description(description).build();
        ToolAttributes attributes = ToolAttributes.builder().toolInputs(List.of(input)).build();

        assertThat(input.getName()).isEqualTo("data_guard");
        assertThat(input.getDescription()).isEqualTo(description);
        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.size()).isEqualTo(1);
        JsonNode entry = json.get("tool_inputs").get(0);
        assertThat(entry.size()).isEqualTo(2);
        assertThat(entry.get("name").textValue()).isEqualTo("data_guard");
        assertThat(entry.get("description").textValue()).isEqualTo(description);
    }

    @Test
    void optionalDescriptionIsOmittedAndSemanticValidationRemainsInDatabase() {
        ToolInput input = ToolInput.builder().name("query").build();
        assertThat(input.getDescription()).isNull();
        assertThat(ToolAttributes.builder().toolInputs(List.of(input)).build().toJson())
                .isEqualTo("{\"tool_inputs\":[{\"name\":\"query\"}]}");
        ToolInput unset = ToolInput.builder().build();
        assertThat(unset.getName()).isNull();
        assertThat(unset.getDescription()).isNull();
        assertThat(ToolAttributes.builder().toolInputs(List.of(unset)).build().toJson())
                .isEqualTo("{\"tool_inputs\":[{}]}");
    }

    @Test
    void distinguishesOmittedInputsFromAnExplicitEmptyArray() {
        ToolAttributes.Builder builder = ToolAttributes.builder();
        assertThat(builder.build().getToolInputs()).isNull();
        assertThat(builder.toolInputs(List.of()).build().toJson()).isEqualTo("{\"tool_inputs\":[]}");
        assertThat(builder.toolInputs(null).build().toJson()).isEqualTo("{}");
    }

    @Test
    void snapshotsListAndBuilderValuesAndPreservesOrder() {
        ToolInput.Builder inputBuilder = ToolInput.builder().name("first").description("Original");
        ToolInput first = inputBuilder.build();
        ToolInput second = ToolInput.builder().name("second").build();
        List<ToolInput> source = new ArrayList<>(List.of(first, second));
        ToolAttributes.Builder builder = ToolAttributes.builder().toolInputs(source);
        source.clear();
        inputBuilder.name("changed").description("Changed");

        ToolAttributes attributes = builder.build();
        builder.toolInputs(List.of());
        assertThat(attributes.getToolInputs()).containsExactly(first, second);
        assertThat(first.getName()).isEqualTo("first");
        assertThat(first.getDescription()).isEqualTo("Original");
        assertThatThrownBy(() -> attributes.getToolInputs().add(second))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> attributes.getToolInputs().set(0, second))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullEntriesWithoutReplacingPreviousInputs() {
        ToolInput input = ToolInput.builder().name("query").build();
        ToolAttributes.Builder builder = ToolAttributes.builder().toolInputs(List.of(input));
        assertThatThrownBy(() -> builder.toolInputs(Arrays.asList(input, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolInputs must not contain null entries");
        assertThat(builder.build().getToolInputs()).containsExactly(input);
    }
}
