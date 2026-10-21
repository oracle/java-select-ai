/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.execution;

import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.RunTeamParams;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamDescription;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end resource-graph coverage for Select AI Agent. */
class AgentEndToEndIT extends AgentIntegrationFixture {
    /**
     * Test: Create a SQL tool, task, agent, and team, then run the team in a conversation and
     * inspect its definition.
     * Expected: The run returns nonblank text, describe reports the team name, and the team
     * definition contains that name.
     */
    @Test
    void test38000AgentEndToEnd() throws Exception {
        Tool tool = agentClient.tool(uniqueName("E2E_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("E2E_TASK"), taskAttributes(tool.getToolName()), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("E2E_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("E2E_TEAM"), agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();
        TeamRunResult response = team.run(RunTeamRequest.builder()
                .userPrompt("I want to return an office chair")
                .params(RunTeamParams.forConversationId(conversation.getConversationId()))
                .build());
        assertThat(response.getRawValue()).isNotBlank();
        TeamDescription description = team.describe();
        assertThat(description.getTeamName()).isEqualTo(team.getTeamName());
        assertThat(agentClient.getDefinition(AgentObjectType.TEAM, team.getTeamName(), true))
                .contains(team.getTeamName());
    }

    /**
     * Test: Run a SQL-backed team with a conversation ID and a test_case state variable.
     * Expected: The team run returns nonblank raw text.
     */
    @Test
    void test39000SqlTeamRuns() throws Exception {
        Tool tool = agentClient.tool(uniqueName("SQL_E2E_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("SQL_E2E_TASK"), taskAttributes(tool.getToolName()), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("SQL_E2E_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("SQL_E2E_TEAM"), agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();
        TeamRunResult response = agentClient.runTeam(team.getTeamName(), RunTeamRequest.builder()
                .userPrompt("List tables in the SH schema?")
                .params(RunTeamParams.builder()
                        .conversationId(conversation.getConversationId())
                        .stateVariables(java.util.Map.of("test_case", "3900"))
                        .build())
                .build());
        assertThat(response.getRawValue()).isNotBlank();
    }

    /**
     * Test: Run a SQL-backed team with an audience instruction variable and an LLM profile
     * attribute variable.
     * Expected: The team run returns nonblank raw text.
     */
    @Test
    void test39001SqlTeamRunsWithRuntimeVariables() throws Exception {
        Tool tool = agentClient.tool(uniqueName("RUNTIME_VARIABLE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("RUNTIME_VARIABLE_TASK"),
                TaskAttributes.builder()
                        .instruction("Answer this question for the {audience}: {query}")
                        .tools(List.of(tool.getToolName()))
                        .enableHumanTool(false)
                        .build(),
                "Java runtime-variable task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("RUNTIME_VARIABLE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("RUNTIME_VARIABLE_TEAM"),
                agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();

        TeamRunResult response = agentClient.runTeam(team.getTeamName(), RunTeamRequest.builder()
                .userPrompt("List tables in the SH schema?")
                .params(RunTeamParams.builder()
                        .conversationId(conversation.getConversationId())
                        .instructionVariables(Map.of("audience", "support analyst"))
                        .attributeVariables(Map.of("llm_profile", profileName))
                        .build())
                .build());

        assertThat(response.getRawValue()).isNotBlank();
    }

    /**
     * Test: Run a sequential team with one worker task and a configured supervisor agent.
     * Expected: The team reports the supervisor and worker membership, and the run response
     * contains 4.
     */
    @Test
    void test39003SupervisorTeamWithOneWorkerRuns() throws Exception {
        Agent worker = agentClient.agent(uniqueName("SUPERVISOR_WORKER"), AgentAttributes.builder()
                        .profileName(profileName)
                        .role("Answer simple questions.")
                        .enableHumanTool(false)
                        .build(),
                "Simple supervisor-team worker", AgentStatus.ENABLED);
        worker.create();
        managedAgents.add(worker);

        Task task = agentClient.task(uniqueName("SUPERVISOR_WORKER_TASK"),
                TaskAttributes.builder()
                        .instruction("Answer this question briefly: {query}")
                        .enableHumanTool(false)
                        .build(),
                "Simple supervisor-team task", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);

        Agent supervisor = agentClient.agent(uniqueName("SUPERVISOR"), AgentAttributes.builder()
                        .profileName(profileName)
                        .role("Send the user's question to the worker and return the answer.")
                        .supervisor(true)
                        .enableHumanTool(false)
                        .build(),
                "Simple supervisor", AgentStatus.ENABLED);
        supervisor.create();
        managedAgents.add(supervisor);

        Team team = agentClient.team(uniqueName("SUPERVISOR_TEAM"), TeamAttributes.builder()
                        .agents(List.of(TeamMember.builder(worker.getAgentName())
                                .task(task.getTaskName()).build()))
                        .process("sequential")
                        .supervisorAgent(supervisor.getAgentName())
                        .build(),
                "One-worker supervisor team", TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);

        assertThat(team.getTeamAttributes().getSupervisorAgent())
                .isEqualTo(supervisor.getAgentName());
        assertThat(team.getTeamAttributes().getAgents()).extracting(TeamMember::getName)
                .containsExactly(worker.getAgentName());

        Conversation conversation = createConversation();
        TeamRunResult response = team.run(RunTeamRequest.builder()
                .userPrompt("What is 2 + 2?")
                .params(RunTeamParams.forConversationId(conversation.getConversationId()))
                .build());

        assertThat(response.getRawValue()).contains("4");
    }

}
