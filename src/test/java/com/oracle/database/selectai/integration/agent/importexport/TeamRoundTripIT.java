/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.importexport;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.agent.RunTeamParams;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that an exported agent graph can be imported and run under a new team name. */
class TeamRoundTripIT extends TeamExportIntegrationFixture {
    /**
     * Test: Run a tool-backed team, export it to a CLOB or file, drop its resources, then import
     * it under a new team name.
     * Expected: The imported team restores member-task and task-tool mappings; a new conversation
     * run succeeds, returns the probe result, and invokes the database verification function.
     */
    @ParameterizedTest
    @EnumSource(ExportDestination.class)
    void test33092WorkingTeamRunsAfterExportAndImport(ExportDestination destination) throws Exception {
        ExportTarget target = destination == ExportDestination.CLOB ? null : createExportTarget(destination);
        ExecutionProbe probe = createExecutionProbe();
        Tool tool = agentClient.tool(uniqueName("EXP_RUN_TOOL"), ToolAttributes.builder()
                .function(probe.functionName())
                .instruction("Looks up the current status of a customer service request. "
                        + "Use p_request for the request reference supplied by the customer.")
                .toolInputs(List.of(ToolInput.builder()
                        .name("p_request")
                        .description("Customer service request reference.")
                        .build()))
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("EXP_RUN_TASK"), TaskAttributes.builder()
                .instruction("For a customer request-status question, use the configured request-status "
                        + "lookup tool with the exact request reference from the customer. Return the "
                        + "tool result exactly as provided. {query}")
                .tools(List.of(tool.getToolName())).enableHumanTool(false).build(),
                "Runnable export task", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("EXP_RUN_AGENT"), AgentAttributes.builder()
                .profileName(profileName)
                .role("You are a customer-support specialist who handles request-status inquiries. "
                        + "Use the available request-status lookup tool to answer questions about a "
                        + "specific customer request.")
                .enableHumanTool(false).build(), "Runnable export agent", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team source = createTeam(uniqueName("EXP_RUN_TEAM"), agent.getAgentName(), task.getTaskName());

        Conversation originalConversation = createConversation();
        assertSuccessfulProbeRun(source, probe, originalConversation);

        String importedName = uniqueName("EXP_RUN_IMPORTED");
        ImportTeamRequest.Builder request = ImportTeamRequest.builder(profileName, importedName).force(false);
        if (target == null) {
            String specification = source.exportDefinition();
            assertThat(specification).isNotBlank();
            request.specification(specification);
        } else {
            assertThat(source.exportDefinition(target.request(null))).isTrue();
            // Exercise the actual file import overload, not an inline import of read-back JSON.
            request.location(target.location()).objectStorageCredentialName(target.credentialName());
        }

        assertThat(source.drop(true)).isTrue();
        managedTeams.remove(source);
        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThat(task.drop(true)).isTrue();
        managedTasks.remove(task);
        assertThat(tool.drop(true)).isTrue();
        managedTools.remove(tool);
        assertThat(agentClient.listTeams("^" + source.getTeamName() + "$")).isEmpty();
        assertThat(agentClient.listAgents("^" + agent.getAgentName() + "$")).isEmpty();
        assertThat(agentClient.listTasks("^" + task.getTaskName() + "$")).isEmpty();
        assertThat(agentClient.listTools("^" + tool.getToolName() + "$")).isEmpty();

        // Keep only external dependencies (profile/credential and probe function/table).
        // Register component names before import to cover partially completed imports.
        trackImportedComponents(agent.getAgentName(), task.getTaskName(), tool.getToolName());
        Team imported = importExportedDefinition(request.build());
        assertThat(imported.getStatus()).isEqualTo(TeamStatus.ENABLED);
        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getName)
                .containsExactly(agent.getAgentName());
        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getTask)
                .containsExactly(task.getTaskName());
        assertThat(agentClient.task(task.getTaskName()).getTaskAttributes().getTools())
                .containsExactly(tool.getToolName());
        assertThat(agentClient.tool(tool.getToolName()).getStatus()).isEqualTo(ToolStatus.ENABLED);

        Conversation importedConversation = createConversation();
        assertThat(importedConversation.getConversationId()).isNotEqualTo(originalConversation.getConversationId());
        assertSuccessfulProbeRun(imported, probe, importedConversation);
    }

    private void assertSuccessfulProbeRun(Team team, ExecutionProbe probe, Conversation conversation)
            throws Exception {
        long callsBefore = executionProbeCalls(probe);
        TeamRunResult response = team.run(RunTeamRequest.builder()
                .userPrompt("Could you check the current status of my request roundtrip-check?")
                .params(RunTeamParams.forConversationId(conversation.getConversationId()))
                .build());
        assertThat(team.getStateByConversationId(conversation.getConversationId()))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
        assertThat(response.getRawValue()).contains(probe.expectedResult());
        assertThat(executionProbeCalls(probe)).as("team run invoked the database verification tool")
                .isGreaterThan(callsBefore);
    }
}
