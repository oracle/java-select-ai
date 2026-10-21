/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentModelTest {
    @Test
    void agentAttributesSerializeUsingSnakeCase() {
        AgentAttributes attributes = AgentAttributes.builder()
                .profileName("GENAI")
                .role("Answer support questions")
                .enableHumanTool(false)
                .tools(List.of("SQL"))
                .shortTermMemoryLength(5)
                .supervisor(true)
                .build();

        assertThat(attributes.toJson())
                .contains("\"profile_name\":\"GENAI\"")
                .contains("\"enable_human_tool\":false")
                .contains("\"short_term_memory_length\":5")
                .contains("\"supervisor\":true");
    }

    @Test
    void agentAttributesLeaveRequiredFieldsToDatabase() {
        assertThat(AgentAttributes.builder().build().toJson()).isEqualTo("{}");
        assertThat(AgentAttributes.builder().profileName("GENAI").build().toJson())
                .isEqualTo("{\"profile_name\":\"GENAI\"}");
        assertThat(AgentAttributes.builder().role("Role").build().toJson())
                .isEqualTo("{\"role\":\"Role\"}");
        assertThat(AgentAttributes.builder().profileName(" ").role("").build().toJson())
                .contains("\"profile_name\":\" \"")
                .contains("\"role\":\"\"");
    }

    @Test
    void agentAttributesCopyToolsAndRejectNegativeMemoryLength() {
        List<String> tools = new ArrayList<>(List.of("SQL"));
        AgentAttributes attributes = AgentAttributes.builder()
                .profileName("GENAI").role("Assist").tools(tools).build();
        tools.add("RAG");

        assertThat(attributes.getTools()).containsExactly("SQL");
        assertThatThrownBy(() -> attributes.getTools().add("WEBSEARCH"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> AgentAttributes.builder().shortTermMemoryLength(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resourceStatusEnumsParseDatabaseValues() {
        assertThat(AgentStatus.fromValue(" enabled ")).isEqualTo(AgentStatus.ENABLED);
        assertThat(TaskStatus.fromValue(" enabled ")).isEqualTo(TaskStatus.ENABLED);
        assertThat(ToolStatus.fromValue("DISABLED")).isEqualTo(ToolStatus.DISABLED);
        assertThat(TeamStatus.fromValue("ENABLED").getValue()).isEqualTo("ENABLED");
        assertThat(AgentStatus.fromValue(null)).isNull();
        assertThat(TaskStatus.fromValue(null)).isNull();
        assertThat(ToolStatus.fromValue(" ")).isNull();
        assertThat(TeamStatus.fromValue("\t")).isNull();
        assertThatThrownBy(() -> ToolStatus.fromValue("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentStatus.fromValue("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskStatus.fromValue("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TeamStatus.fromValue("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taskAttributesSerializeOptionalLists() {
        TaskAttributes attributes = TaskAttributes.builder()
                .instruction("Handle {query}")
                .tools(List.of("SQL", "RAG"))
                .input(List.of("TASK1"))
                .enableHumanTool(true)
                .build();

        assertThat(attributes.toJson())
                .contains("\"instruction\":\"Handle {query}\"")
                .contains("\"tools\":[\"SQL\",\"RAG\"]")
                .contains("\"input\":[\"TASK1\"]")
                .contains("\"enable_human_tool\":true");
    }

    @Test
    void taskAttributesCopyInputAndToolLists() {
        List<String> tools = new ArrayList<>(List.of("SQL"));
        List<String> inputs = new ArrayList<>(List.of("FIRST"));
        TaskAttributes attributes = TaskAttributes.builder().tools(tools).input(inputs).build();
        tools.add("RAG");
        inputs.add("SECOND");

        assertThat(attributes.getTools()).containsExactly("SQL");
        assertThat(attributes.getInput()).containsExactly("FIRST");
        assertThatThrownBy(() -> attributes.getTools().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> attributes.getInput().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toolAttributesSupportBuiltInToolParams() {
        ToolAttributes attributes = ToolAttributes.builder()
                .toolType(ToolType.SQL)
                .toolParams(SqlToolParams.builder().profileName("GENAI").build())
                .build();

        assertThat(attributes.toJson())
                .contains("\"tool_type\":\"SQL\"")
                .contains("\"tool_params\":{\"profile_name\":\"GENAI\"}");
    }

    @Test
    void toolTypePreservesKnownAndFutureDatabaseValues() {
        assertThat(ToolType.of(" sql ")).isEqualTo(ToolType.SQL);
        assertThat(ToolType.SQL.isKnown()).isTrue();

        ToolType futureType = ToolType.of("FUTURE_TOOL");
        assertThat(futureType.getValue()).isEqualTo("FUTURE_TOOL");
        assertThat(futureType.isKnown()).isFalse();

        ToolAttributes attributes = ToolAttributes.builder()
                .toolTypeValue("FUTURE_TOOL")
                .build();
        assertThat(attributes.getToolType()).isEqualTo(futureType);
        assertThat(attributes.getToolTypeValue()).isEqualTo("FUTURE_TOOL");
        assertThat(attributes.toJson())
                .contains("\"tool_type\":\"FUTURE_TOOL\"")
                .doesNotContain("tool_type_value");
    }

    @Test
    void toolTypeRejectsBlankValues() {
        assertThatThrownBy(() -> ToolType.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolAttributes.builder().toolTypeValue(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void httpIsNotAKnownToolTypeAndRetainsItsOriginalCase() {
        for (String value : List.of("HTTP", "http", "Http")) {
            ToolType type = ToolType.of(value);
            assertThat(type.isKnown()).isFalse();
            assertThat(type.getValue()).isEqualTo(value);
        }
        for (ToolType type : List.of(ToolType.SQL, ToolType.RAG, ToolType.WEBSEARCH,
                ToolType.NOTIFICATION, ToolType.HUMAN)) {
            assertThat(type.isKnown()).isTrue();
            assertThat(ToolType.of(type.getValue().toLowerCase(java.util.Locale.ROOT))).isEqualTo(type);
        }
    }

    @Test
    void toolRunResultPreservesRawDatabaseValue() {
        ToolRunResult result = ToolRunResult.fromRawValue("{\"result\":1}");

        assertThat(result.getRawValue()).isEqualTo("{\"result\":1}");
        assertThat(ToolRunResult.fromRawValue(null)).isNull();
    }

    @Test
    void toolAttributesRetainTypedParamsAndDatabaseFieldNames() {
        ToolAttributes attributes = ToolAttributes.builder()
                .toolParams(SqlToolParams.builder().profileName("GENAI").build())
                .build();

        assertThat(attributes.getToolParams()).isInstanceOf(SqlToolParams.class);
        assertThat(((SqlToolParams) attributes.getToolParams()).getProfileName()).isEqualTo("GENAI");
        assertThat(attributes.toJson()).contains("\"tool_params\":{\"profile_name\":\"GENAI\"}");
    }

    @Test
    void toolAttributesSnapshotNestedParamsAndInputs() {
        List<Object> options = new ArrayList<>(List.of("original"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("options", options);
        params.put("optional", null);
        ToolInput.Builder input = ToolInput.builder().name("sku");
        List<ToolInput> inputs = new ArrayList<>(List.of(input.build()));

        ToolAttributes.Builder builder = ToolAttributes.builder()
                .toolParams(GenericToolParams.fromMap(params))
                .toolInputs(inputs);
        options.set(0, "changed");
        params.put("added", true);
        input.name("changed");
        inputs.add(ToolInput.builder().name("extra").build());

        ToolAttributes attributes = builder.build();
        assertThat(attributes.toJson())
                .contains("\"tool_params\":{\"options\":[\"original\"],\"optional\":null}")
                .contains("\"tool_inputs\":[{\"name\":\"sku\"}]")
                .doesNotContain("changed", "added", "extra");
        Map<?, ?> storedParams = attributes.getToolParams().toMap();
        assertThat(storedParams.containsKey("optional")).isTrue();
        assertThat(storedParams.get("optional")).isNull();
    }

    @Test
    void toolAttributesDoNotExposeMutableJsonValues() {
        ToolAttributes attributes = ToolAttributes.builder()
                .toolParams(GenericToolParams.builder().parameter("options", List.of("original")).build())
                .toolInputs(List.of(ToolInput.builder().name("sku").build()))
                .build();

        Map<?, ?> params = attributes.getToolParams().toMap();
        List<?> options = (List<?>) params.get("options");
        List<ToolInput> inputs = attributes.getToolInputs();

        assertThatThrownBy(params::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(options::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(inputs::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(inputs.get(0).getName()).isEqualTo("sku");
        assertThat(attributes.toJson())
                .contains("\"options\":[\"original\"]")
                .contains("\"tool_inputs\":[{\"name\":\"sku\"}]");
    }

    @Test
    void teamAttributesAllowDatabaseToValidateRequiredAgents() {
        TeamAttributes attributes = TeamAttributes.builder().build();

        assertThat(attributes.getAgents()).isNull();
    }

    @Test
    void teamAttributesSerializeDetailedMembers() {
        TeamAttributes attributes = TeamAttributes.builder()
                .agents(List.of(TeamAttributes.TeamMember.builder("AGENT")
                        .task("TASK")
                        .build()))
                .process("sequential")
                .longTermMemoryLength(30)
                .supervisorAgent("SUPERVISOR")
                .build();

        assertThat(attributes.toJson())
                .contains("\"agents\":[{\"name\":\"AGENT\",\"task\":\"TASK\"}]")
                .contains("\"process\":\"sequential\"")
                .contains("\"long_term_memory_length\":30")
                .contains("\"supervisor_agent\":\"SUPERVISOR\"");
    }

    @Test
    void teamAttributesCopyMembersAndRejectNegativeMemoryLength() {
        List<TeamAttributes.TeamMember> members = new ArrayList<>(List.of(
                TeamAttributes.TeamMember.builder("AGENT").task("TASK").build()));
        TeamAttributes attributes = TeamAttributes.builder().agents(members).build();
        members.clear();

        assertThat(attributes.getAgents()).hasSize(1);
        assertThatThrownBy(() -> attributes.getAgents().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> TeamAttributes.builder().longTermMemoryLength(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void teamMemberAllowsDatabaseToValidateRequiredFields() {
        TeamAttributes.TeamMember member = TeamAttributes.TeamMember.builder(null)
                .task(null)
                .build();

        assertThat(member.getName()).isNull();
        assertThat(member.getTask()).isNull();
    }

    @Test
    void runTeamRequestOmitsUnsetPromptAndParams() {
        RunTeamRequest request = RunTeamRequest.builder().build();

        assertThat(request.getUserPrompt()).isNull();
        assertThat(request.toParamsJson()).isNull();
        assertThat(RunTeamRequest.builder().userPrompt("Help me").build().toParamsJson()).isNull();
    }

    @Test
    void runTeamRequestSerializesDocumentedRuntimeParameters() {
        RunTeamRequest request = RunTeamRequest.builder()
                .userPrompt("Help me")
                .conversationId("C1")
                .instructionVariables(Map.of("audience", "support"))
                .attributeVariables(Map.of("llm_profile", "GENAI"))
                .stateVariables(Map.of("request_context", "ORDER-1"))
                .build();

        assertThat(request.getUserPrompt()).isEqualTo("Help me");
        assertThat(request.getConversationId()).isEqualTo("C1");
        assertThat(request.toParamsJson()).isEqualTo("{\"conversation_id\":\"C1\","
                + "\"instruction_variables\":{\"audience\":\"support\"},"
                + "\"attribute_variables\":{\"llm_profile\":\"GENAI\"},"
                + "\"state_variables\":{\"request_context\":\"ORDER-1\"}}");
    }

    @Test
    void runTeamRequestSnapshotsNestedRuntimeVariables() {
        List<Object> values = new ArrayList<>(List.of("original"));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("values", values);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("context", nested);
        RunTeamRequest.Builder builder = RunTeamRequest.builder().stateVariables(variables);
        values.add("changed");
        nested.put("extra", "changed");

        RunTeamRequest request = builder.build();
        assertThat(request.toParamsJson()).isEqualTo(
                "{\"state_variables\":{\"context\":{\"values\":[\"original\"]}}}");
        Map<?, ?> context = (Map<?, ?>) request.getStateVariables().get("context");
        List<?> storedValues = (List<?>) context.get("values");
        assertThatThrownBy(request.getStateVariables()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(storedValues::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void runTeamRequestEscapesConversationId() {
        RunTeamRequest request = RunTeamRequest.builder().conversationId("C\"1").build();

        assertThat(request.toParamsJson()).isEqualTo("{\"conversation_id\":\"C\\\"1\"}");
    }

    @Test
    void runTeamParamsSerializeDocumentedRuntimeParameters() {
        RunTeamParams params = RunTeamParams.builder()
                .conversationId("C1")
                .instructionVariables(Map.of("audience", "support"))
                .attributeVariables(Map.of("llm_profile", "GENAI"))
                .stateVariables(Map.of("request_context", "ORDER-1"))
                .build();

        assertThat(params.toParamsJson()).isEqualTo("{\"conversation_id\":\"C1\","
                + "\"instruction_variables\":{\"audience\":\"support\"},"
                + "\"attribute_variables\":{\"llm_profile\":\"GENAI\"},"
                + "\"state_variables\":{\"request_context\":\"ORDER-1\"}}");
    }

    @Test
    void runTeamRequestAcceptsTypedParams() {
        RunTeamParams params = RunTeamParams.forConversationId("C1");
        RunTeamRequest request = RunTeamRequest.builder()
                .userPrompt("Help me")
                .params(params)
                .build();

        assertThat(request.getParams()).isSameAs(params);
        assertThat(request.getConversationId()).isEqualTo("C1");
        assertThat(request.toParamsJson()).isEqualTo("{\"conversation_id\":\"C1\"}");
    }

    @Test
    void runTeamRequestRejectsMixedTypedAndConvenienceParams() {
        assertThatThrownBy(() -> RunTeamRequest.builder()
                .params(RunTeamParams.forConversationId("C1"))
                .stateVariables(Map.of("key", "value"))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("params cannot be combined with individual runtime parameter fields");
    }

    @Test
    void runTeamRequestBuilderCanBeReusedWithoutMutatingEarlierRequests() {
        RunTeamRequest.Builder builder = RunTeamRequest.builder()
                .conversationId("C1").userPrompt("First")
                .stateVariables(Map.of("step", 1));
        RunTeamRequest first = builder.build();
        RunTeamRequest repeated = builder.build();
        RunTeamRequest next = builder.userPrompt("Next").conversationId("C2")
                .stateVariables(Map.of("step", 2)).build();

        assertThat(repeated.toParamsJson()).isEqualTo(first.toParamsJson());
        assertThat(first.getConversationId()).isEqualTo("C1");
        assertThat(first.getUserPrompt()).isEqualTo("First");
        assertThat(first.getStateVariables()).containsEntry("step", 1);
        assertThat(next.getConversationId()).isEqualTo("C2");
        assertThat(next.getUserPrompt()).isEqualTo("Next");
        assertThat(next.getStateVariables()).containsEntry("step", 2);
    }

    @Test
    void runTeamRequestBuilderRemainsReusableWithTypedOrAbsentParameters() {
        RunTeamRequest.Builder builder = RunTeamRequest.builder();
        assertThat(builder.build().getParams()).isNull();
        assertThat(builder.build().getParams()).isNull();
        RunTeamParams params = RunTeamParams.forConversationId("C1");
        builder.params(params);
        assertThat(builder.build().getParams()).isSameAs(params);
        assertThat(builder.userPrompt("Next").build().getParams()).isSameAs(params);
        assertThatThrownBy(() -> builder.conversationId("C2").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runtimeParameterSnapshotsPreserveDecimalsInEveryVariableGroup() {
        BigDecimal decimal = new BigDecimal("1.234567890123456789");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("amount", decimal);
        nested.put("entries", List.of(Map.of("amount", decimal)));
        RunTeamParams params = RunTeamParams.builder()
                .instructionVariables(nested).attributeVariables(nested)
                .stateVariables(nested).build();
        RunTeamRequest request = RunTeamRequest.builder()
                .instructionVariables(nested).attributeVariables(nested)
                .stateVariables(nested).build();
        nested.put("amount", BigDecimal.ZERO);

        for (Map<String, Object> variables : List.of(params.getInstructionVariables(),
                params.getAttributeVariables(), params.getStateVariables(),
                request.getInstructionVariables(), request.getAttributeVariables(),
                request.getStateVariables())) {
            assertThat(variables).containsEntry("amount", decimal);
            Map<?, ?> entry = (Map<?, ?>) ((List<?>) variables.get("entries")).get(0);
            assertThat(entry.get("amount")).isEqualTo(decimal);
            assertThatThrownBy(variables::clear).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(params.toParamsJson()).contains(decimal.toPlainString());
        assertThat(request.toParamsJson()).isEqualTo(params.toParamsJson());
    }

    @Test
    void executionStateParamsOnlyContainConversationId() {
        ExecutionStateRequest request = ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED)
                .conversationId("CONV")
                .build();

        assertThat(request.toParamsJson())
                .isEqualTo("{\"conversation_id\":\"CONV\"}");
        assertThat(request.getParams().getConversationId()).isEqualTo("CONV");
    }

    @Test
    void executionStateSupportsTypedParams() {
        ExecutionStateParams params = ExecutionStateParams.builder()
                .conversationId("CONV")
                .build();

        ExecutionStateRequest request = ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED)
                .params(params)
                .build();

        assertThat(request.getParams()).isSameAs(params);
        assertThat(request.getConversationId()).isEqualTo("CONV");
        assertThat(request.toParamsJson())
                .isEqualTo("{\"conversation_id\":\"CONV\"}");
    }

    @Test
    void executionStateValidatesStateAndRequiresTargetParameters() {
        assertThatThrownBy(() -> ExecutionStateRequest.builder(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExecutionStateRequest.builder(TeamExecutionState.UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId or params");
        assertThatThrownBy(() -> ExecutionStateParams.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
        assertThatThrownBy(() -> ExecutionStateRequest.builder(TeamExecutionState.SUCCEEDED).params(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportTeamParamsOnlyContainOverwrite() {
        ExportTeamParams params = ExportTeamParams.builder()
                .overwrite(true)
                .build();
        ExportTeamRequest request = ExportTeamRequest.builder("MY_DIR:team.json")
                .objectStorageCredentialName("OBJ_CRED")
                .params(params)
                .build();

        assertThat(request.getParams()).isSameAs(params);
        assertThat(request.toParamsJson())
                .isEqualTo("{\"overwrite\":true}");
    }

    @Test
    void exportTeamSupportsTypedParamsAndConvenienceOverwrite() {
        ExportTeamParams params = ExportTeamParams.builder()
                .overwrite(true)
                .build();
        ExportTeamRequest request = ExportTeamRequest.builder("MY_DIR:team.json")
                .params(params)
                .build();

        assertThat(request.toParamsJson())
                .isEqualTo("{\"overwrite\":true}");
        assertThat(ExportTeamRequest.builder("MY_DIR:team.json")
                .overwrite(false)
                .build()
                .getParams()
                .getOverwrite())
                .isFalse();
        assertThatThrownBy(() -> ExportTeamRequest.builder("MY_DIR:team.json")
                .params(params)
                .overwrite(true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exportTeamValidatesLocationAndOmitsAbsentOverwrite() {
        assertThatThrownBy(() -> ExportTeamRequest.builder(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExportTeamRequest.builder(" "))
                .isInstanceOf(IllegalArgumentException.class);
        ExportTeamRequest request = ExportTeamRequest.builder("DIR:team.json").build();
        assertThat(request.getLocation()).isEqualTo("DIR:team.json");
        assertThat(request.getObjectStorageCredentialName()).isNull();
        assertThat(request.getParams()).isNull();
        assertThat(request.toParamsJson()).isNull();
        assertThat(ExportTeamRequest.builder("DIR:team.json").overwrite(false)
                .build().toParamsJson()).isEqualTo("{\"overwrite\":false}");
    }

    @Test
    void importTeamRequestRequiresExactlyOneSource() {
        assertThatThrownBy(() -> ImportTeamRequest.builder("GENAI", "TEAM").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> ImportTeamRequest.builder("GENAI", "TEAM")
                .location("MY_DIR:team.json")
                .specification("{}")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void importTeamRequestValidatesNamesAndRetainsSelectedSource() {
        assertThatThrownBy(() -> ImportTeamRequest.builder(null, "TEAM"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportTeamRequest.builder("PROFILE", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportTeamRequest.builder("PROFILE", "TEAM")
                .location(" ").build())
                .isInstanceOf(IllegalArgumentException.class);

        ImportTeamRequest request = ImportTeamRequest.builder("PROFILE", "TEAM")
                .specification("{\"agents\":[]}")
                .force(false)
                .build();
        assertThat(request.getProfileName()).isEqualTo("PROFILE");
        assertThat(request.getTeamName()).isEqualTo("TEAM");
        assertThat(request.getSpecification()).isEqualTo("{\"agents\":[]}");
        assertThat(request.getLocation()).isNull();
        assertThat(request.getForce()).isFalse();
    }

    @Test
    void importTeamAcceptsStructuredSpecification() {
        Map<String, Object> specification = new LinkedHashMap<>();
        specification.put("agents", List.of(Map.of("name", "AGENT", "task", "TASK")));

        ImportTeamRequest request = ImportTeamRequest.builder("PROFILE", "TEAM")
                .specification(specification)
                .build();

        assertThat(request.getSpecification())
                .contains("\"agents\":[")
                .contains("\"name\":\"AGENT\"")
                .contains("\"task\":\"TASK\"");
    }

    @Test
    void teamExecutionStateParsesKnownAndFutureStates() {
        TeamExecutionState waiting = TeamExecutionState.of("WAITING_FOR_HUMAN");
        assertThat(waiting).isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);

        TeamExecutionState future = TeamExecutionState.of("PAUSED_BY_POLICY");
        assertThat(future).isEqualTo(TeamExecutionState.UNKNOWN);
        assertThat(future.getValue()).isEqualTo("UNKNOWN");
        assertThat(future.isKnown()).isFalse();
    }

    @Test
    void teamDescriptionExposesSkillsJson() {
        TeamRunResult runResult = TeamRunResult.fromRawValue("Completed");
        TeamDescription description = TeamDescription.fromRawJson(
                "{\"name\":\"SUPPORT\",\"description\":\"Support team\","
                        + "\"version\":\"1.0\",\"skills\":[{"
                        + "\"id\":\"search\",\"name\":\"Search\","
                        + "\"description\":\"Search records\","
                        + "\"tags\":[\"support\"],\"examples\":[\"Find order\"],"
                        + "\"future_field\":true}]}");

        assertThat(runResult.getRawValue()).isEqualTo("Completed");
        assertThat(description.getTeamName()).isEqualTo("SUPPORT");
        assertThat(description.getDescription()).isEqualTo("Support team");
        assertThat(description.getVersion()).isEqualTo("1.0");
        assertThat(description.getSkills()).isEqualTo("[{\"id\":\"search\",\"name\":\"Search\","
                + "\"description\":\"Search records\",\"tags\":[\"support\"],"
                + "\"examples\":[\"Find order\"],\"future_field\":true}]");
    }

    @Test
    void teamDescriptionHandlesMissingAndAdditionalDatabaseFields() {
        assertThat(TeamDescription.fromRawJson(null)).isNull();
        TeamDescription empty = new TeamDescription("{}");
        assertThat(empty.getTeamName()).isNull();
        assertThat(empty.getDescription()).isNull();
        assertThat(empty.getVersion()).isNull();
        assertThat(empty.getSkills()).isNull();
        assertThat(new TeamDescription(null).getTeamName()).isNull();
        TeamDescription future = new TeamDescription("{\"name\":\"TEAM\",\"future\":{\"key\":true}}");
        assertThat(future.getTeamName()).isEqualTo("TEAM");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-json", "null", "[]", "42", "true", "\"text\"",
            "{\"name\":", "{} {}", "{} trailing"})
    void teamDescriptionRejectsMalformedOrNonObjectResponses(String response) {
        assertThatThrownBy(() -> TeamDescription.fromRawJson(response))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void teamExecutionStateNormalizesKnownValuesAndRejectsBlankValues() {
        TeamExecutionState state = TeamExecutionState.of(" waiting_for_human ");

        assertThat(state).isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);
        assertThat(state.getValue()).isEqualTo("WAITING_FOR_HUMAN");
        assertThat(state.isKnown()).isTrue();
        assertThat(state.toString()).isEqualTo("WAITING_FOR_HUMAN");
        assertThatThrownBy(() -> TeamExecutionState.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void teamRunResultPreservesNullDatabaseValue() {
        assertThat(TeamRunResult.fromRawValue(null)).isNull();
        assertThat(new TeamRunResult(null).getRawValue()).isNull();
    }

    @Test
    void responseModelsInheritObjectToStringWithoutExposingContent() throws Exception {
        List<Object> responses = List.of(
                new TeamRunResult("private-team-response"),
                new ToolRunResult("private-tool-response"),
                new TeamDescription("{\"name\":\"private-team-name\","
                        + "\"description\":\"private-description\","
                        + "\"version\":\"private-version\","
                        + "\"skills\":[{\"description\":\"private-skill\"}]}"));

        for (Object response : responses) {
            assertThat(response.getClass().getMethod("toString").getDeclaringClass())
                    .isEqualTo(Object.class);
            assertThat(String.valueOf(response)).doesNotContain("private-team-response",
                    "private-tool-response", "private-team-name", "private-description",
                    "private-version", "private-skill");
        }
    }

    @Test
    void executionStateParamsSerializeConversationIdUsingJsonEscaping() {
        ExecutionStateParams params = ExecutionStateParams.forConversationId("C\"1");

        assertThat(params.toParamsJson()).isEqualTo("{\"conversation_id\":\"C\\\"1\"}");
    }
}
