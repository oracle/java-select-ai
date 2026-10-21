/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.lifecycle;

import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.RagToolParams;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolType;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT tool operations. */
class ToolIT extends AgentIntegrationFixture {
    /**
     * Test: Create an enabled SQL tool using the fixture profile and inspect its loaded attributes
     * and database description.
     * Expected: The tool reports SQL type and the profile parameter; its description has an id,
     * the supplied description, SQL attributes, and a function-argument array.
     */
    @Test
    void test30000SqlToolCreated() throws Exception {
        Tool tool = agentClient.tool(uniqueName("SQL_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolName()).isNotBlank();
        assertThat(tool.getToolAttributes().getToolType()).isEqualTo(ToolType.SQL);
        assertSqlToolProfile(tool.getToolAttributes(), profileName);
        JsonNode description = describeJson(tool);
        assertThat(description.path("id").isIntegralNumber()).isTrue();
        assertThat(description.path("description").asText()).isEqualTo("Java SQL tool test");
        assertThat(description.path("attributes").path("tool_type").asText()).isEqualTo("SQL");
        assertThat(description.path("function_args").isArray()).isTrue();
    }

    /**
     * Test: Create an enabled RAG tool using a newly created compatible profile and inspect its
     * description.
     * Expected: The loaded tool and database description both identify the type as RAG, and the
     * description includes an id, supplied text, and a function-argument array.
     */
    @Test
    void test30001RagToolCreated() throws Exception {
        String ragProfile = uniqueName("RAG_PROFILE");
        createManagedProfile(ragProfile, isolatedProfileAttributes(), "Python-compatible RAG profile");
        Tool tool = agentClient.tool(uniqueName("RAG_TOOL"), ToolAttributes.builder()
                .toolType(ToolType.RAG)
                .toolParams(RagToolParams.builder().profileName(ragProfile).build())
                .build(),
                "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getToolType()).isEqualTo(ToolType.RAG);
        JsonNode description = describeJson(tool);
        assertThat(description.path("id").isIntegralNumber()).isTrue();
        assertThat(description.path("description").asText()).isEqualTo("Java tool test");
        assertThat(description.path("attributes").path("tool_type").asText()).isEqualTo("RAG");
        assertThat(description.path("function_args").isArray()).isTrue();
    }

    /**
     * Test: Create a custom tool backed by a generated PL/SQL function and a supplied instruction.
     * Expected: Reading the created tool's attributes returns the same function name.
     */
    @Test
    void test30002PlsqlToolCreated() throws Exception {
        String function = createPythonPlsqlFunction();
        Tool tool = agentClient.tool(uniqueName("PLSQL_TOOL"), ToolAttributes.builder()
                .instruction("Return the requested value.").function(function).build(),
                "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getFunction()).isEqualTo(function);
    }

    /**
     * Test: Create an enabled SQL tool and list tools without a name filter.
     * Expected: The returned tool names include the newly created tool.
     */
    @Test
    void test30003ListTools() throws Exception {
        Tool tool = agentClient.tool(uniqueName("LIST_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(agentClient.listTools().stream().map(Tool::getToolName)).contains(tool.getToolName());
    }

    /**
     * Test: Create an enabled SQL tool and list with a regex anchored to its full name.
     * Expected: The result contains exactly that tool name.
     */
    @Test
    void test30004ListToolsByRegex() throws Exception {
        Tool tool = agentClient.tool(uniqueName("REGEX_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(agentClient.listTools("^" + tool.getToolName() + "$"))
                .extracting(Tool::getToolName).containsExactly(tool.getToolName());
    }

    /**
     * Test: Create a SQL tool, then load a second handle by its name.
     * Expected: The loaded handle has the same name and tool type as the created handle.
     */
    @Test
    void test30005FetchTool() throws Exception {
        Tool created = agentClient.tool(uniqueName("FETCH_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        created.create();
        managedTools.add(created);
        Tool fetched = agentClient.tool(created.getToolName());
        assertThat(fetched.getToolName()).isEqualTo(created.getToolName());
        assertThat(fetched.getToolAttributes().getToolType())
                .isEqualTo(created.getToolAttributes().getToolType());
    }

    /**
     * Test: Create an enabled SQL tool, disable it, and then enable it again.
     * Expected: Both lifecycle calls return true and the reported status changes to DISABLED,
     * then ENABLED.
     */
    @Test
    void test30006EnableDisableTool() throws Exception {
        Tool tool = agentClient.tool(uniqueName("STATUS_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.disable()).isTrue();
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.DISABLED);
        assertThat(tool.enable()).isTrue();
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);
    }

    /**
     * Test: Create custom-function and built-in SQL tools with instructions and input metadata,
     * then reload both by name.
     * Expected: The custom tool retains its function and inputs, with null type and parameters; the
     * SQL tool retains its type, parameters, and inputs, with no function. Both retain the expected
     * description, status, and instruction.
     */
    @Test
    void test30007AllToolAttributesRoundTrip() throws Exception {
        String function = createPythonPlsqlFunction();
        ToolAttributes customAttributes = ToolAttributes.builder()
                .instruction("Return the age for a birth-date input")
                .function(function)
                .toolInputs(List.of(toolInput("p_birth_date", "DATE input")))
                .build();
        Tool custom = agentClient.tool(uniqueName("ROUND_TRIP_CUSTOM_TOOL"), customAttributes,
                "Java tool test", ToolStatus.ENABLED);
        custom.create();
        managedTools.add(custom);

        ToolAttributes sqlAttributes = ToolAttributes.builder()
                .instruction("Use SQL actions to answer the question")
                .toolType(ToolType.SQL)
                .toolParams(SqlToolParams.builder().profileName(profileName).build())
                .toolInputs(List.of(toolInput("query", "Question to answer")))
                .build();
        Tool sql = agentClient.tool(uniqueName("ROUND_TRIP_SQL_TOOL"), sqlAttributes,
                "Java tool test", ToolStatus.ENABLED);
        sql.create();
        managedTools.add(sql);

        Tool fetchedCustom = agentClient.tool(custom.getToolName());
        assertThat(fetchedCustom.getToolName()).isEqualTo(custom.getToolName());
        assertThat(fetchedCustom.getDescription()).isEqualTo("Java tool test");
        assertThat(fetchedCustom.getStatus()).isEqualTo(ToolStatus.ENABLED);
        assertThat(fetchedCustom.getToolAttributes().getInstruction())
                .isEqualTo(customAttributes.getInstruction());
        assertThat(fetchedCustom.getToolAttributes().getFunction())
                .isEqualTo(customAttributes.getFunction());
        assertThat(fetchedCustom.getToolAttributes().getToolType()).isNull();
        assertThat(fetchedCustom.getToolAttributes().getToolParams()).isNull();
        assertToolInputs(fetchedCustom.getToolAttributes().getToolInputs(), "p_birth_date",
                "DATE input");

        Tool fetchedSql = agentClient.tool(sql.getToolName());
        assertThat(fetchedSql.getToolName()).isEqualTo(sql.getToolName());
        assertThat(fetchedSql.getDescription()).isEqualTo("Java tool test");
        assertThat(fetchedSql.getStatus()).isEqualTo(ToolStatus.ENABLED);
        assertThat(fetchedSql.getToolAttributes().getInstruction())
                .contains(sqlAttributes.getInstruction());
        assertThat(fetchedSql.getToolAttributes().getFunction()).isNull();
        assertThat(fetchedSql.getToolAttributes().getToolType())
                .isEqualTo(sqlAttributes.getToolType());
        assertSqlToolProfile(fetchedSql.getToolAttributes(), profileName);
        assertToolInputs(fetchedSql.getToolAttributes().getToolInputs(), "query",
                "Question to answer");
    }

    /**
     * Test: Call enable on a tool that was created in the ENABLED state.
     * Expected: The call throws SelectAIException with ORA-20052 in its cause, and the status
     * remains ENABLED.
     */
    @Test
    void test30026EnableAlreadyEnabledToolFails() throws Exception {
        Tool tool = agentClient.tool(uniqueName("ENABLE_TWICE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);

        assertThatThrownBy(tool::enable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).hasMessageContaining("ORA-20052:"));
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);
    }

    /**
     * Test: Call disable on a tool that was created in the DISABLED state.
     * Expected: The call throws SelectAIException with ORA-20052 in its cause, and the status
     * remains DISABLED.
     */
    @Test
    void test30027DisableAlreadyDisabledToolFails() throws Exception {
        Tool tool = agentClient.tool(uniqueName("DISABLE_TWICE_TOOL"), sqlToolAttributes(), null,
                ToolStatus.DISABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.DISABLED);

        assertThatThrownBy(tool::disable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).hasMessageContaining("ORA-20052:"));
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.DISABLED);
    }

    /**
     * Test: Force-drop an enabled tool, then try to disable the same handle.
     * Expected: Drop returns true and the subsequent disable call throws an exception.
     */
    @Test
    void test30028DisableAfterDeleteFails() throws Exception {
        Tool tool = agentClient.tool(uniqueName("DELETED_DISABLE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.drop(true)).isTrue();
        managedTools.remove(tool);
        assertThatThrownBy(tool::disable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Force-drop an enabled tool, then try to enable the same handle.
     * Expected: Drop returns true and the subsequent enable call throws an exception.
     */
    @Test
    void test30029EnableAfterDeleteFails() throws Exception {
        Tool tool = agentClient.tool(uniqueName("DELETED_ENABLE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.drop(true)).isTrue();
        managedTools.remove(tool);
        assertThatThrownBy(tool::enable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create and reload a custom-function tool with an instruction and declared function
     * input.
     * Expected: The loaded tool retains its description and function, its instruction contains
     * "birth-date", and its input metadata is returned as a list.
     */
    @Test
    void test30010CustomToolAttributesRoundTrip() throws Exception {
        String function = createPythonPlsqlFunction();
        ToolAttributes attributes = ToolAttributes.builder().function(function)
                .instruction("Return the age for a birth-date input")
                .toolInputs(List.of(toolInput("p_birth_date", "DATE input")))
                .build();
        Tool created = agentClient.tool(uniqueName("CUSTOM_TOOL"), attributes, "Java tool test", ToolStatus.ENABLED);
        created.create();
        managedTools.add(created);
        Tool fetched = agentClient.tool(created.getToolName());
        assertThat(fetched.getDescription()).isEqualTo("Java tool test");
        assertThat(fetched.getToolAttributes().getFunction()).isEqualTo(function);
        assertThat(fetched.getToolAttributes().getInstruction()).contains("birth-date");
        assertToolInputs(fetched.getToolAttributes().getToolInputs(), "p_birth_date", "DATE input");
    }

    /**
     * Test: Create a custom-function tool without assigning a built-in tool type.
     * Expected: Its loaded attributes have a null tool type.
     */
    @Test
    void test30011CustomToolWithoutType() throws Exception {
        String function = createPythonPlsqlFunction();
        Tool tool = agentClient.tool(uniqueName("CUSTOM_NO_TYPE"), ToolAttributes.builder()
                .instruction("Return the requested value.").function(function).build(),
                "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getToolType()).isNull();
    }

    /**
     * Test: Create an SQL tool without explicitly setting an instruction.
     * Expected: The loaded attributes contain a nonblank instruction supplied by the SQL tool
     * defaults.
     */
    @Test
    void test30012SqlToolWithoutInstruction() throws Exception {
        Tool tool = agentClient.tool(uniqueName("SQL_DEFAULT_INSTRUCTION"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getInstruction()).isNotBlank();
    }

    /**
     * Test: Create an SQL tool with a custom instruction about using relational data.
     * Expected: The loaded attributes' instruction contains the supplied relational-data text.
     */
    @Test
    void test30013SqlToolWithInstruction() throws Exception {
        Tool tool = agentClient.tool(uniqueName("SQL_INSTRUCTION"), ToolAttributes.builder()
                .toolType(ToolType.SQL)
                .toolParams(SqlToolParams.builder().profileName(profileName).build())
                .instruction("Use relational data to answer the question").build(),
                "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getInstruction()).contains("relational");
    }

    /**
     * Test: Create an SQL tool whose profile name does not exist, then load the tool by name.
     * Expected: Creation succeeds and the tool remains retrievable by its assigned name.
     */
    @Test
    void test30014InvalidProfileToolIsPersisted() throws Exception {
        Tool tool = agentClient.tool(uniqueName("INVALID_PROFILE"), ToolAttributes.builder()
                .toolType(ToolType.SQL)
                .toolParams(SqlToolParams.builder()
                        .profileName(uniqueName("MISSING_PROFILE")).build())
                .build(),
                "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(agentClient.tool(tool.getToolName()).getToolName()).isEqualTo(tool.getToolName());
    }

    /**
     * Test: Attempt to create an enabled tool with empty attributes and no description.
     * Expected: Creation throws SelectAIException with a nonnull error code and an ORA error in
     * its SQL cause.
     */
    @Test
    void test30035MissingToolDefinitionRejectedByDatabase() {
        Tool tool = agentClient.tool(uniqueName("MISSING_TOOL_DEFINITION"),
                ToolAttributes.builder().build(), null, ToolStatus.ENABLED);
        assertThatThrownBy(tool::create)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).isInstanceOf(java.sql.SQLException.class)
                                    .hasMessageContaining("ORA-");
                        });
    }

    /**
     * Test: Load a tool handle for a generated name that has no database resource.
     * Expected: The lookup throws an exception.
     */
    @Test
    void test30017FetchMissingToolFails() {
        assertThatThrownBy(() -> agentClient.tool(uniqueName("MISSING_TOOL")))
                .isInstanceOf(Exception.class);
    }

    /**
     * Test: Create a tool, then list tools with an unclosed regex character class.
     * Expected: The list call throws SelectAIException whose SQL cause contains ORA-12726.
     */
    @Test
    void test30018InvalidToolRegexFails() throws Exception {
        Tool tool = agentClient.tool(uniqueName("INVALID_REGEX_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);

        assertThatThrownBy(() -> agentClient.listTools("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
    }

    /**
     * Test: Create a disabled SQL tool with custom description, then retrieve it through a
     * name-filtered list call.
     * Expected: The single listed tool has the expected name, description, DISABLED status, SQL
     * type, and profile parameter.
     */
    @Test
    void test30019ListToolsHydratesMetadataAndAttributes() throws Exception {
        String name = uniqueName("LIST_METADATA");
        Tool tool = agentClient.tool(name, sqlToolAttributes(), "Tool list metadata", ToolStatus.DISABLED);
        tool.create();
        managedTools.add(tool);

        List<Tool> listedTools = agentClient.listTools("^" + name + "$");
        assertThat(listedTools).hasSize(1);
        Tool listed = listedTools.get(0);
        assertThat(listed.getToolName()).isEqualTo(name);
        assertThat(listed.getDescription()).isEqualTo("Tool list metadata");
        assertThat(listed.getStatus()).isEqualTo(ToolStatus.DISABLED);
        assertThat(listed.getToolAttributes().getToolType()).isEqualTo(ToolType.SQL);
        assertSqlToolProfile(listed.getToolAttributes(), profileName);
    }

    /**
     * Test: Create five tools sharing a prefix and one tool with a different prefix, then list by
     * the shared prefix.
     * Expected: The result contains exactly the five names with the shared prefix.
     */
    @Test
    void test30039ListToolsMultipleMatchesUsingPattern() throws Exception {
        List<String> names = createToolListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listToolNames("^" + prefix)).containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create tools with a shared prefix and list using that prefix in lower case.
     * Expected: The case-insensitive pattern returns exactly the five names with the original
     * mixed-case prefix.
     */
    @Test
    void test30040ListToolsCaseInsensitivePattern() throws Exception {
        List<String> names = createToolListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listToolNames("^" + prefix.toLowerCase(Locale.ROOT)))
                .containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create two groups of tools with different prefixes and list with a regex alternation
     * matching either prefix.
     * Expected: The result contains all six fixture names and no other names.
     */
    @Test
    void test30041ListToolsAlternationPattern() throws Exception {
        List<String> names = createToolListFixture();
        String firstPrefix = listPrefix(names.get(0));
        String secondPrefix = listPrefix(names.get(5));

        assertThat(listToolNames("^(" + firstPrefix + "|" + secondPrefix + ")"))
                .containsExactlyInAnyOrderElementsOf(names);
    }

    /**
     * Test: List tools with an anchored pattern built from a unique name that has not been created.
     * Expected: The list call returns an empty collection.
     */
    @Test
    void test30042ListToolsNonMatchingPatternReturnsEmptyList() throws Exception {
        String pattern = "^" + uniqueName("NO_MATCH") + "$";

        assertThat(agentClient.listTools(pattern)).isEmpty();
    }

    /**
     * Test: List tools with a unique literal name that has not been created.
     * Expected: The list call returns an empty collection.
     */
    @Test
    void test30043ListToolsLiteralNonMatchingPatternReturnsEmptyList() throws Exception {
        assertThat(agentClient.listTools(uniqueName("NO_MATCH_LITERAL"))).isEmpty();
    }

    /**
     * Test: Create the list fixture and list with the first tool's full name followed by an end
     * anchor.
     * Expected: Only the tool whose name ends with that exact name is returned.
     */
    @Test
    void test30044ListToolsEndAnchorPattern() throws Exception {
        List<String> names = createToolListFixture();
        String exact = names.get(0);
        String pattern = exact + "$";

        assertThat(listToolNames(pattern)).containsExactly(exact);
    }

    /**
     * Test: Create a tool, then query the list API with a regex far longer than the supported
     * pattern size.
     * Expected: The list call throws SelectAIException whose SQL cause contains ORA-12733.
     */
    @Test
    void test30045ListToolsOverlongPatternIsRejectedByDatabase() throws Exception {
        Tool tool = agentClient.tool(uniqueName("OVERLONG_REGEX_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        String pattern = "^" + "a".repeat(1000) + "$";

        assertThatThrownBy(() -> agentClient.listTools(pattern))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12733:"));
    }

    /**
     * Test: Create three tools in B, A, C order, then list them with a pattern matching all three
     * names.
     * Expected: The returned names are ordered lexically as A, B, C.
     */
    @Test
    void test30046ListToolsAreOrderedByName() throws Exception {
        String prefix = uniqueName("LIST_ORDER");
        String first = prefix + "_A";
        String second = prefix + "_B";
        String third = prefix + "_C";
        Tool secondTool = agentClient.tool(second, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        secondTool.create();
        managedTools.add(secondTool);
        Tool firstTool = agentClient.tool(first, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        firstTool.create();
        managedTools.add(firstTool);
        Tool thirdTool = agentClient.tool(third, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        thirdTool.create();
        managedTools.add(thirdTool);

        assertThat(listToolNames("^" + prefix + "_[ABC]$"))
                .containsExactly(first, second, third);
    }

    /**
     * Test: Create a SQL tool without specifying a status.
     * Expected: The loaded tool reports ENABLED as its default status.
     */
    @Test
    void test30020DefaultStatusIsEnabled() throws Exception {
        String name = uniqueName("DEFAULT_ENABLED");
        Tool tool = agentClient.tool(name, sqlToolAttributes(), null, null);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ENABLED);
    }

    /**
     * Test: Create a SQL tool with its status explicitly set to DISABLED.
     * Expected: The loaded tool reports DISABLED.
     */
    @Test
    void test30021CreateDisabledTool() throws Exception {
        String name = uniqueName("DEFAULT_DISABLED");
        Tool tool = agentClient.tool(name, sqlToolAttributes(), null, ToolStatus.DISABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.DISABLED);
    }

    /**
     * Test: Describe a custom-function tool with an instruction and a declared DATE input.
     * Expected: The JSON includes its id, description, instruction, function, and input metadata;
     * tool_type and tool_params are absent or null.
     */
    @Test
    void test30024DescribeTool() throws Exception {
        String function = createPythonPlsqlFunction();
        String instruction = "Return the age for a birth-date input";
        ToolAttributes attributes = ToolAttributes.builder()
                .instruction(instruction)
                .function(function)
                .toolInputs(List.of(toolInput("p_birth_date", "DATE input")))
                .build();
        Tool tool = agentClient.tool(uniqueName("DESCRIBE_TOOL"), attributes, "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);

        JsonNode description = describeJson(tool);
        assertThat(description.path("id").isIntegralNumber()).isTrue();
        assertThat(description.path("description").asText()).isEqualTo("Java tool test");
        assertThat(description.findValues("instruction"))
                .anySatisfy(value -> assertThat(value.asText()).isEqualTo(instruction));
        assertThat(description.findValues("function"))
                .anySatisfy(value -> assertThat(value.asText()).isEqualTo(function));
        assertThat(description.toString()).contains("p_birth_date", "DATE input");
        assertThat(description.findValue("tool_type") == null
                || description.findValue("tool_type").isNull()).isTrue();
        assertThat(description.findValue("tool_params") == null
                || description.findValue("tool_params").isNull()).isTrue();
    }

    /**
     * Test: Describe an SQL tool configured with an instruction, profile, and query input.
     * Expected: The JSON includes the SQL tool attributes, profile parameter, QUERY argument, and
     * built-in SQL function.
     */
    @Test
    void test30038DescribeBuiltInToolAttributes() throws Exception {
        String instruction = "Use relational data to answer the question";
        ToolAttributes attributes = ToolAttributes.builder()
                .instruction(instruction)
                .toolType(ToolType.SQL)
                .toolParams(SqlToolParams.builder().profileName(profileName).build())
                .toolInputs(List.of(toolInput("query", "Question to answer")))
                .build();
        Tool tool = agentClient.tool(uniqueName("DESCRIBE_SQL_TOOL"), attributes, "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);

        JsonNode description = describeJson(tool);
        assertThat(description.path("id").isIntegralNumber()).isTrue();
        assertThat(description.path("description").asText()).isEqualTo("Java tool test");
        JsonNode describedAttributes = description.path("attributes");
        assertThat(describedAttributes.path("instruction").asText()).contains(instruction);
        assertThat(describedAttributes.path("tool_type").asText()).isEqualTo("SQL");
        JsonNode toolParams = new ObjectMapper().readTree(
                describedAttributes.path("tool_params").asText());
        assertThat(toolParams.path("profile_name").asText())
                .isEqualTo(profileName);
        assertThat(description.findValuesAsText("argument_name")).contains("QUERY");
        assertThat(describedAttributes.path("function").asText())
                .isEqualTo("dbms_cloud_ai_agent.sql_tool");
    }

    /**
     * Test: Create a tool, load a second handle, delete through the first handle, then describe
     * through the stale handle.
     * Expected: Describe throws SelectAIException with an error code and an ORA cause naming the
     * deleted tool.
     */
    @Test
    void test30036DescribeAfterExternalDeletePreservesDatabaseError() throws Exception {
        String name = uniqueName("DESCRIBE_DELETED_TOOL");
        Tool first = agentClient.tool(name, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        first.create();
        managedTools.add(first);
        Tool second = agentClient.tool(name);

        assertThat(first.drop(true)).isTrue();
        managedTools.remove(first);

        assertThatThrownBy(second::describe)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).hasMessageContaining("ORA-")
                                    .hasMessageContaining(name);
                        });
    }

    /**
     * Test: Call the database DESCRIBE_TOOL procedure directly for a name with no existing tool.
     * Expected: The call throws SQLException whose message contains an ORA error and the requested
     * name.
     */
    @Test
    void test30037DescribeMissingToolRejectedByDatabase() {
        String name = uniqueName("MISSING_DESCRIBE_TOOL");

        assertThatThrownBy(() -> {
            try (CallableStatement statement = jdbcConnection().prepareCall(
                    "BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DESCRIBE_TOOL(?); END;")) {
                statement.registerOutParameter(1, Types.CLOB);
                statement.setString(2, name);
                statement.execute();
            }
        }).isInstanceOfSatisfying(SQLException.class,
                exception -> assertThat((Throwable) exception).hasMessageContaining("ORA-")
                        .hasMessageContaining(name));
    }

    /**
     * Test: Run a created custom-function tool with a JSON birth-date argument.
     * Expected: The tool call returns a nonblank result.
     */
    @Test
    void test30025RunTool() throws Exception {
        String function = createPythonPlsqlFunction();
        Tool tool = agentClient.tool(uniqueName("RUN_TOOL"), ToolAttributes.builder()
                .instruction("Return the age for the supplied birth date.").function(function).build(),
                "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        String input = "{\"p_birth_date\":\"2000-01-01\"}";
        assertThat(tool.run(input).getRawValue()).isNotBlank();
    }

    /**
     * Test: Create a tool, force-drop it, then recreate it through the same handle.
     * Expected: The old name is initially unavailable after deletion, recreation succeeds, and the
     * tool can be loaded again.
     */
    @Test
    void test30030DeleteAndRecreateTool() throws Exception {
        String name = uniqueName("RECREATE_TOOL");
        Tool tool = agentClient.tool(name, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.drop(true)).isTrue();
        managedTools.remove(tool);
        assertThatThrownBy(() -> agentClient.tool(name)).isInstanceOf(Exception.class);
        assertThat(tool.create()).isTrue();
        managedTools.add(tool);
        assertThat(agentClient.tool(name).getToolName()).isEqualTo(name);
    }

    /**
     * Test: Create a tool and drop it with force disabled.
     * Expected: Drop returns true, and a subsequent lookup throws SelectAIException with the
     * tool-not-found message.
     */
    @Test
    void test30031DropExistingToolWithoutForce() throws Exception {
        String name = uniqueName("DROP_NON_FORCE_TOOL");
        Tool tool = agentClient.tool(name, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.drop(false)).isTrue();
        managedTools.remove(tool);
        assertThatThrownBy(() -> agentClient.tool(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI tool not found: " + name);
    }

    /**
     * Bug <a href="https://bug.oraclecorp.com/ords/bug/bugui/bug-details?mode=edit&bugnumber=40126447">
     * 40126447</a> - DBMS_CLOUD_AI_AGENT Java force-drop APIs must delegate repeated drops to the
     * database.
     * Test: Force-drop a created tool twice through the same handle.
     * Expected: Both drop calls return true.
     */
    @Test
    void test30032RepeatedForceDropOnSameToolHandleSucceeds() throws Exception {
        Tool tool = agentClient.tool(uniqueName("DROP_REPEAT_FORCE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.drop(true)).isTrue();
        managedTools.remove(tool);
        assertThat(tool.drop(true)).isTrue();
    }

    /**
     * Test: Create a tool, load a second handle, and force-drop the resource through each handle in
     * turn.
     * Expected: Both force-drop calls return true, including the call through the handle whose
     * resource is already absent.
     */
    @Test
    void test30033ForceDropAlreadyMissingToolSucceeds() throws Exception {
        String name = uniqueName("DROP_MISSING_FORCE_TOOL");
        Tool first = agentClient.tool(name, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        first.create();
        managedTools.add(first);
        Tool second = agentClient.tool(name);

        assertThat(first.drop(true)).isTrue();
        managedTools.remove(first);
        assertThat(second.drop(true)).isTrue();
    }

    /**
     * Test: Create a tool, load a second handle, force-drop through the first, then use non-force
     * drop through the stale handle.
     * Expected: The second drop throws SelectAIException with an error code and a database cause
     * naming the absent resource.
     */
    @Test
    void test30034NonForceDropMissingToolPreservesDatabaseError() throws Exception {
        String name = uniqueName("DROP_MISSING_NON_FORCE_TOOL");
        Tool first = agentClient.tool(name, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        first.create();
        managedTools.add(first);
        Tool second = agentClient.tool(name);

        assertThat(first.drop(true)).isTrue();
        managedTools.remove(first);
        assertThatThrownBy(() -> second.drop(false))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).hasMessageContaining("ORA-")
                                    .hasMessageContaining(name)
                                    .hasMessageContaining("does not exist");
                        });
    }

    /**
     * Test: Create a tool, retrieve it from a filtered list, and drop it using the listed handle.
     * Expected: The drop succeeds and a subsequent name-filtered list is empty.
     */
    @Test
    void test30047DropToolLoadedFromList() throws Exception {
        Tool source = agentClient.tool(uniqueName("DROP_LISTED_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        source.create();
        managedTools.add(source);
        String name = source.getToolName();

        List<Tool> listedTools = agentClient.listTools("^" + name + "$");
        assertThat(listedTools).hasSize(1);
        assertThat(listedTools.get(0).drop(true)).isTrue();
        managedTools.remove(source);

        assertThat(listToolNames("^" + name + "$")).isEmpty();
    }

    /**
     * Test: Load a second handle, delete the tool through the first handle, then read status and
     * attributes through the stale handle.
     * Expected: Both reads throw SelectAIException with the tool-not-found message.
     */
    @Test
    void test30048StaleToolStatusAndAttributesFailAfterExternalDrop() throws Exception {
        Tool source = agentClient.tool(uniqueName("STALE_DROP_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        source.create();
        managedTools.add(source);
        String name = source.getToolName();
        Tool stale = agentClient.tool(name);

        assertThat(source.drop(true)).isTrue();
        managedTools.remove(source);

        assertThatThrownBy(stale::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI tool not found: " + name);
        assertThatThrownBy(stale::getToolAttributes)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI tool not found: " + name);
    }

    /**
     * Test: Force-drop a tool handle that was never created, then non-force-drop a separate
     * never-created handle.
     * Expected: The force drop returns true, while the non-force drop throws IllegalStateException.
     */
    @Test
    void test30049NeverCreatedToolForceDropDelegatesToDatabase() throws Exception {
        String forceName = uniqueName("UNCREATED_FORCE_DROP");
        Tool forceHandle = agentClient.tool(forceName, sqlToolAttributes(), null, ToolStatus.ENABLED);
        assertThat(forceHandle.drop(true)).isTrue();

        Tool nonForceHandle = agentClient.tool(uniqueName("UNCREATED_NON_FORCE_DROP"),
                sqlToolAttributes(), null, ToolStatus.ENABLED);
        assertThatThrownBy(() -> nonForceHandle.drop(false))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test: Drop a created tool without force, then call non-force drop again on the same handle.
     * Expected: The first drop succeeds, the repeated call throws IllegalStateException, and the
     * name is absent from listing.
     */
    @Test
    void test30050RepeatedNonForceDropOnSameToolHandleIsRejectedLocally() throws Exception {
        Tool tool = agentClient.tool(uniqueName("DROP_REPEAT_NON_FORCE"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        String name = tool.getToolName();

        assertThat(tool.drop(false)).isTrue();
        managedTools.remove(tool);
        assertThatThrownBy(() -> tool.drop(false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(listToolNames("^" + name + "$")).isEmpty();
    }

    private JsonNode describeJson(Tool tool) throws Exception {
        return new ObjectMapper().readTree(tool.describe());
    }

    private static ToolInput toolInput(String name, String description) {
        return ToolInput.builder().name(name).description(description).build();
    }

    private static void assertSqlToolProfile(ToolAttributes attributes, String profileName) {
        assertThat(attributes.getToolParams()).isInstanceOf(SqlToolParams.class);
        SqlToolParams params = (SqlToolParams) attributes.getToolParams();
        assertThat(params.getProfileName()).isEqualTo(profileName);
        assertThat(params.toMap()).isEqualTo(Map.of("profile_name", profileName));
    }

    private static void assertToolInputs(List<ToolInput> inputs, String name, String description) {
        assertThat(inputs).singleElement().satisfies(input -> {
            assertThat(input.getName()).isEqualTo(name);
            assertThat(input.getDescription()).isEqualTo(description);
        });
    }

    private List<String> createToolListFixture() throws Exception {
        String listPrefix = uniqueName("LIST_PATTERN");
        String otherPrefix = uniqueName("LIST_OTHER");
        List<String> names = List.of(
                listPrefix + "_1",
                listPrefix + "_2",
                listPrefix + "_3",
                listPrefix + "_4",
                listPrefix + "_5",
                otherPrefix + "_1");
        for (String name : names) {
            Tool tool = agentClient.tool(name, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
            tool.create();
            managedTools.add(tool);
        }
        return names;
    }

    private List<String> listToolNames(String pattern) throws Exception {
        return agentClient.listTools(pattern).stream().map(Tool::getToolName).toList();
    }

    private String listPrefix(String name) {
        return name.substring(0, name.lastIndexOf('_'));
    }
}
