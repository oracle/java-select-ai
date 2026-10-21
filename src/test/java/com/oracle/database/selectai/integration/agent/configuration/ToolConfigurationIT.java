/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.configuration;

import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolType;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT tool attribute mutation operations. */
class ToolConfigurationIT extends AgentIntegrationFixture {
    private static final List<ToolInput> UPDATED_TOOL_INPUTS = List.of(
            toolInput("p_birth_date", "Updated DATE input"));
    private static final String UPDATED_TOOL_INPUTS_JSON =
            "[{\"name\":\"p_birth_date\",\"description\":\"Updated DATE input\"}]";

    /**
     * Test: Update a custom tool instruction, PL/SQL function, and input definition using
     * individual setters.
     * Expected: Each setter succeeds, and the updated instruction, function, and input are
     * visible on both handles.
     */
    @Test
    void test30039SetCustomToolAttributesPersistThroughFreshHandle() throws Exception {
        String originalFunction = createPythonPlsqlFunction();
        String updatedFunction = createPythonPlsqlFunction();
        Tool tool = agentClient.tool(uniqueName("SET_ATTRIBUTE_CUSTOM_TOOL"), ToolAttributes.builder()
                .instruction("Return the age for a birth-date input.")
                .function(originalFunction)
                .toolInputs(List.of(toolInput("p_birth_date", "DATE input")))
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        String updatedInstruction = "Return the customer's age for the supplied birth date.";

        assertThat(tool.setAttribute("instruction", updatedInstruction)).isTrue();
        assertThat(tool.setAttribute("function", updatedFunction)).isTrue();
        assertThat(tool.setAttribute("tool_inputs", UPDATED_TOOL_INPUTS_JSON)).isTrue();

        assertCustomToolAttributes(tool, updatedInstruction, updatedFunction);
        assertCustomToolAttributes(agentClient.tool(tool.getToolName()), updatedInstruction,
                updatedFunction);
    }

    /**
     * Test: Replace custom tool attributes with a new instruction, PL/SQL function, and input
     * definition.
     * Expected: The update succeeds, and both the original and reloaded tool expose the new
     * instruction, function, and input.
     */
    @Test
    void test30040SetCustomToolAttributesPersistThroughFreshHandle() throws Exception {
        String originalFunction = createPythonPlsqlFunction();
        String updatedFunction = createPythonPlsqlFunction();
        Tool tool = agentClient.tool(uniqueName("SET_ATTRIBUTES_CUSTOM_TOOL"), ToolAttributes.builder()
                .instruction("Return the age for a birth-date input.")
                .function(originalFunction)
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        String updatedInstruction = "Calculate the customer's age from the birth date.";
        ToolAttributes updatedAttributes = ToolAttributes.builder()
                .instruction(updatedInstruction)
                .function(updatedFunction)
                .toolInputs(UPDATED_TOOL_INPUTS)
                .build();

        assertThat(tool.setAttributes(updatedAttributes)).isTrue();

        assertCustomToolAttributes(tool, updatedInstruction, updatedFunction);
        assertCustomToolAttributes(agentClient.tool(tool.getToolName()), updatedInstruction,
                updatedFunction);
    }

    /**
     * Test: Replace a SQL tool instruction and query input while retaining SQL as its tool type.
     * Expected: The update succeeds, and both handles report the new instruction, SQL type, and
     * query input.
     */
    @Test
    void test30041SetSqlToolAttributesPersistThroughFreshHandle() throws Exception {
        Tool tool = agentClient.tool(uniqueName("SET_ATTRIBUTES_SQL_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        String updatedInstruction = "Use relational data to answer the customer question.";
        List<ToolInput> updatedInputs = List.of(
                toolInput("query", "Question to answer"));
        ToolAttributes updatedAttributes = ToolAttributes.builder()
                .instruction(updatedInstruction)
                .toolType(ToolType.SQL)
                .toolInputs(updatedInputs)
                .build();

        assertThat(tool.setAttributes(updatedAttributes)).isTrue();

        assertSqlToolAttributes(tool, updatedInstruction, updatedInputs);
        assertSqlToolAttributes(agentClient.tool(tool.getToolName()), updatedInstruction, updatedInputs);
    }

    /**
     * Test: Set an unrecognized attribute on a created SQL tool.
     * Expected: The SDK surfaces a SelectAIException whose cause is the database SQLException.
     */
    @Test
    void test30042UnknownToolAttributeIsRejectedByDatabase() throws Exception {
        Tool tool = agentClient.tool(uniqueName("UNKNOWN_ATTRIBUTE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);

        assertDatabaseRejection(() -> tool.setAttribute("SDK_UNKNOWN_TOOL_ATTRIBUTE", "value"));
    }

    /**
     * Test: Clear the required function attribute on a created custom tool.
     * Expected: The database rejects the update and its SQLException is wrapped in
     * SelectAIException.
     */
    @Test
    void test30043ClearingCustomToolFunctionIsRejectedByDatabase() throws Exception {
        Tool tool = agentClient.tool(uniqueName("CLEAR_FUNCTION_TOOL"), ToolAttributes.builder()
                .instruction("Return the age for a birth-date input.")
                .function(createPythonPlsqlFunction())
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);

        assertDatabaseRejection(() -> tool.setAttribute("function", (String) null));
    }

    private static void assertCustomToolAttributes(Tool tool, String instruction, String function)
            throws SelectAIException {
        ToolAttributes attributes = tool.getToolAttributes();
        assertThat(attributes.getInstruction()).isEqualTo(instruction);
        assertThat(attributes.getFunction()).isEqualTo(function);
        assertToolInputs(attributes.getToolInputs(), "p_birth_date", "Updated DATE input");
    }

    private void assertSqlToolAttributes(Tool tool, String instruction,
                                         List<ToolInput> toolInputs)
            throws SelectAIException {
        ToolAttributes attributes = tool.getToolAttributes();
        assertThat(attributes.getInstruction()).isEqualTo(instruction);
        assertThat(attributes.getToolType()).isEqualTo(ToolType.SQL);
        assertThat(toolInputs).hasSize(1);
        ToolInput expectedInput = toolInputs.get(0);
        assertToolInputs(attributes.getToolInputs(), expectedInput.getName(),
                expectedInput.getDescription());
    }

    private static void assertDatabaseRejection(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class));
    }

    private static ToolInput toolInput(String name, String description) {
        return ToolInput.builder().name(name).description(description).build();
    }

    private static void assertToolInputs(List<ToolInput> inputs, String name, String description) {
        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0).getName()).isEqualTo(name);
        assertThat(inputs.get(0).getDescription()).isEqualTo(description);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
