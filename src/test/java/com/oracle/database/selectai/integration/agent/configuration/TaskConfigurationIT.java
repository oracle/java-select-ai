/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.configuration;

import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT task attribute mutation operations. */
class TaskConfigurationIT extends AgentIntegrationFixture {
    /**
     * Test: Set a task instruction, human-tool flag, tool reference, and upstream task input
     * individually.
     * Expected: Each setter succeeds, and the instruction, flag, tool, and input match on the
     * original and reloaded task.
     */
    @Test
    void test31026SetTaskAttributesPersistThroughFreshHandle() throws Exception {
        Tool tool = agentClient.tool(uniqueName("SET_ATTRIBUTE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task upstream = agentClient.task(uniqueName("SET_ATTRIBUTE_UPSTREAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        upstream.create();
        managedTasks.add(upstream);
        Task task = agentClient.task(uniqueName("SET_ATTRIBUTE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String updatedInstruction = "Use the configured inputs to answer: {query}";

        assertThat(task.setAttribute("instruction", updatedInstruction)).isTrue();
        assertThat(task.setAttribute("enable_human_tool", true)).isTrue();
        assertThat(task.setAttribute("tools", jsonArray(tool.getToolName()))).isTrue();
        assertThat(task.setAttribute("input", jsonArray(upstream.getTaskName()))).isTrue();

        assertTaskAttributes(task, updatedInstruction, tool.getToolName(), upstream.getTaskName(), true);
        assertTaskAttributes(agentClient.task(task.getTaskName()), updatedInstruction,
                tool.getToolName(), upstream.getTaskName(), true);
    }

    /**
     * Test: Replace task attributes with a new instruction, tool, upstream input, and human-tool
     * setting.
     * Expected: The update succeeds, and both the original and reloaded task expose all four
     * requested values.
     */
    @Test
    void test31027SetTaskAttributesPersistThroughFreshHandle() throws Exception {
        Tool tool = agentClient.tool(uniqueName("SET_ATTRIBUTES_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task upstream = agentClient.task(uniqueName("SET_ATTRIBUTES_UPSTREAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        upstream.create();
        managedTasks.add(upstream);
        Task task = agentClient.task(uniqueName("SET_ATTRIBUTES_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String updatedInstruction = "Summarize the upstream result for: {query}";
        TaskAttributes updatedAttributes = TaskAttributes.builder()
                .instruction(updatedInstruction)
                .tools(List.of(tool.getToolName()))
                .input(List.of(upstream.getTaskName()))
                .enableHumanTool(true)
                .build();

        assertThat(task.setAttributes(updatedAttributes)).isTrue();

        assertTaskAttributes(task, updatedInstruction, tool.getToolName(), upstream.getTaskName(), true);
        assertTaskAttributes(agentClient.task(task.getTaskName()), updatedInstruction,
                tool.getToolName(), upstream.getTaskName(), true);
    }

    /**
     * Test: Set an unrecognized attribute on a created task.
     * Expected: The SDK surfaces a SelectAIException whose cause is the database SQLException.
     */
    @Test
    void test31028UnknownTaskAttributeIsRejectedByDatabase() throws Exception {
        Task task = agentClient.task(uniqueName("UNKNOWN_ATTRIBUTE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);

        assertDatabaseRejection(() -> task.setAttribute("SDK_UNKNOWN_TASK_ATTRIBUTE", "value"));
    }

    /**
     * Test: Clear the required instruction attribute on a created task.
     * Expected: The database rejects the update and its SQLException is wrapped in
     * SelectAIException.
     */
    @Test
    void test31029ClearingMandatoryTaskInstructionIsRejectedByDatabase() throws Exception {
        Task task = agentClient.task(uniqueName("CLEAR_INSTRUCTION_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);

        assertDatabaseRejection(() -> task.setAttribute("instruction", (String) null));
    }

    private static void assertTaskAttributes(Task task, String instruction, String toolName,
                                             String upstreamTaskName, boolean enableHumanTool)
            throws SelectAIException {
        TaskAttributes attributes = task.getTaskAttributes();
        assertThat(attributes.getInstruction()).isEqualTo(instruction);
        assertThat(attributes.getTools()).containsExactly(toolName);
        assertThat(attributes.getInput()).containsExactly(upstreamTaskName);
        assertThat(attributes.getEnableHumanTool()).isEqualTo(enableHumanTool);
    }

    private static String jsonArray(String value) {
        return "[\"" + value + "\"]";
    }

    private static void assertDatabaseRejection(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class));
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
