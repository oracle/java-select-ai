/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression coverage for the shared attribute serializers and their configurations. */
class AgentAttributeSerializationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test
    void repeatedSerializationPreservesDatabaseFieldsAndNullHandling() throws Exception {
        List<SerializationCase> cases = serializationCases();
        for (int repetition = 0; repetition < 10; repetition++) {
            for (SerializationCase testCase : cases) {
                assertJson(testCase, testCase.serialize().get());
            }
        }
    }

    @Test
    void concurrentSerializationPreservesEachModelsConfiguration() throws Exception {
        List<SerializationCase> cases = serializationCases();
        List<Callable<String>> operations = new ArrayList<>();
        for (int repetition = 0; repetition < 10; repetition++) {
            for (SerializationCase testCase : cases) {
                operations.add(testCase.serialize()::get);
            }
        }

        var executor = Executors.newFixedThreadPool(8);
        try {
            var results = executor.invokeAll(operations, 20, TimeUnit.SECONDS);
            for (int index = 0; index < results.size(); index++) {
                assertJson(cases.get(index % cases.size()), results.get(index).get());
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void assertJson(SerializationCase testCase, String actual) throws Exception {
        assertThat(MAPPER.readTree(actual)).as(testCase.name())
                .isEqualTo(MAPPER.readTree(testCase.expectedJson()));
    }

    private static List<SerializationCase> serializationCases() {
        AgentAttributes agent = AgentAttributes.builder().profileName("GENAI")
                .enableHumanTool(false).tools(List.of()).shortTermMemoryLength(0)
                .supervisor(true).build();
        TaskAttributes task = TaskAttributes.builder().instruction("Handle {query}")
                .tools(List.of("SQL")).input(List.of()).enableHumanTool(false).build();
        TeamAttributes team = TeamAttributes.builder().agents(List.of(
                        TeamAttributes.TeamMember.builder("AGENT").task("TASK").build(),
                        TeamAttributes.TeamMember.builder("SECOND").build()))
                .process("sequential").longTermMemoryLength(0).build();
        ToolAttributes tool = ToolAttributes.builder().toolType(ToolType.SQL)
                .toolParams(SqlToolParams.builder().profileName("GENAI")
                        .additionalParameter("nested", Collections.singletonMap("optional", null))
                        .additionalParameter("amount", new BigDecimal("1.234567890123456789"))
                        .build())
                .toolInputs(List.of(ToolInput.builder().name("query").build())).build();

        return List.of(
                new SerializationCase("agent", agent::toJson,
                        "{\"profile_name\":\"GENAI\",\"enable_human_tool\":false,\"tools\":[],"
                                + "\"short_term_memory_length\":0,\"supervisor\":true}"),
                new SerializationCase("task", task::toJson,
                        "{\"instruction\":\"Handle {query}\",\"tools\":[\"SQL\"],\"input\":[],"
                                + "\"enable_human_tool\":false}"),
                new SerializationCase("team", team::toJson,
                        "{\"agents\":[{\"name\":\"AGENT\",\"task\":\"TASK\"},{\"name\":\"SECOND\"}],"
                                + "\"process\":\"sequential\",\"long_term_memory_length\":0}"),
                new SerializationCase("tool", tool::toJson,
                        "{\"tool_type\":\"SQL\",\"tool_params\":{\"profile_name\":\"GENAI\","
                                + "\"nested\":{\"optional\":null},\"amount\":1.234567890123456789},"
                                + "\"tool_inputs\":[{\"name\":\"query\"}]}"),
                new SerializationCase("empty agent", AgentAttributes.builder().build()::toJson, "{}"),
                new SerializationCase("empty task", TaskAttributes.builder().build()::toJson, "{}"),
                new SerializationCase("empty team", TeamAttributes.builder().build()::toJson, "{}"),
                new SerializationCase("empty tool", ToolAttributes.builder().build()::toJson, "{}"));
    }

    private record SerializationCase(String name, Supplier<String> serialize, String expectedJson) { }
}
