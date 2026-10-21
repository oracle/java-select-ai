/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.lifecycle;

import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cross-resource lifecycle coverage modeled after the core Select AI suites. */
class AgentLifecycleIT extends AgentIntegrationFixture {
    /**
     * Test: Construct disabled agent, task, tool, and team handles without creating their
     * database resources.
     * Expected: Each handle retains its description, attributes, and DISABLED status; lifecycle
     * operations fail locally.
     */
    @Test
    void test39005PendingResourcesRetainConfigurationAndRejectDatabaseOperations() throws Exception {
        Agent agent = agentClient.agent(uniqueName("PENDING_AGENT"), agentAttributes(),
                "Pending agent", AgentStatus.DISABLED);
        Task task = agentClient.task(uniqueName("PENDING_TASK"), taskAttributes(),
                "Pending task", TaskStatus.DISABLED);
        Tool tool = agentClient.tool(uniqueName("PENDING_TOOL"), sqlToolAttributes(),
                "Pending tool", ToolStatus.DISABLED);
        Team team = agentClient.team(uniqueName("PENDING_TEAM"), TeamAttributes.builder()
                .process("sequential").build(), "Pending team", TeamStatus.DISABLED);

        assertThat(agent.getDescription()).isEqualTo("Pending agent");
        assertThat(agent.getAgentAttributes().getProfileName()).isEqualTo(profileName);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(task.getDescription()).isEqualTo("Pending task");
        assertThat(task.getTaskAttributes().getInstruction()).contains("{query}");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(tool.getDescription()).isEqualTo("Pending tool");
        assertThat(tool.getToolAttributes().getToolParams()).isNotNull();
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.DISABLED);
        assertThat(team.getDescription()).isEqualTo("Pending team");
        assertThat(team.getTeamAttributes().getProcess()).isEqualTo("sequential");
        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISABLED);

        assertThatThrownBy(agent::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(task::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(tool::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(team::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(agent::disable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(task::disable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(tool::disable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(team::disable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agent.drop(false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> task.drop(false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tool.drop(false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> team.drop(false)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test: Create enabled tool, task, agent, and team resources, then call create again through
     * loaded handles.
     * Expected: Each second create call throws IllegalStateException because the handle already
     * represents a created resource.
     */
    @Test
    void test39006LoadedResourcesCannotBeCreatedAgain() throws Exception {
        Tool tool = agentClient.tool(uniqueName("RECREATE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("RECREATE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("RECREATE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("RECREATE_TEAM"), agent.getAgentName(), task.getTaskName());

        assertThatThrownBy(() -> agentClient.tool(tool.getToolName()).create())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agentClient.task(task.getTaskName()).create())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agentClient.agent(agent.getAgentName()).create())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> agentClient.team(team.getTeamName()).create())
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test: Load separate handles for each resource type, remove resources through their original
     * handles, then query the loaded handles.
     * Expected: Removal succeeds, and status, attributes, or team state reads on each stale handle
     * throw an exception.
     */
    @Test
    void test39007SecondHandlesDetectResourceRemoval() throws Exception {
        Tool tool = agentClient.tool(uniqueName("REMOVED_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("REMOVED_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("REMOVED_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("REMOVED_TEAM"), agent.getAgentName(), task.getTaskName());

        Tool toolReference = agentClient.tool(tool.getToolName());
        Task taskReference = agentClient.task(task.getTaskName());
        Agent agentReference = agentClient.agent(agent.getAgentName());
        Team teamReference = agentClient.team(team.getTeamName());

        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(teamReference::getStatus).isInstanceOf(Exception.class);
        assertThatThrownBy(teamReference::getTeamAttributes).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> teamReference.getStateByConversationId(
                UUID.randomUUID().toString()))
                .isInstanceOf(Exception.class);
        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(agentReference::getStatus).isInstanceOf(Exception.class);
        assertThatThrownBy(agentReference::getAgentAttributes).isInstanceOf(Exception.class);
        assertThat(task.drop(true)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(taskReference::getStatus).isInstanceOf(Exception.class);
        assertThatThrownBy(taskReference::getTaskAttributes).isInstanceOf(Exception.class);
        assertThat(tool.drop(true)).isTrue();
        managedTools.remove(tool);
        assertThatThrownBy(toolReference::getStatus).isInstanceOf(Exception.class);
        assertThatThrownBy(toolReference::getToolAttributes).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create two resources of each type and list them using lower-case prefixes anchored at
     * the start.
     * Expected: Tool, task, agent, and team list calls each return exactly the two matching names,
     * ignoring case.
     */
    @Test
    void test39008ListPatternsReturnMultipleCaseInsensitiveMatchesForEachResource() throws Exception {
        String toolPrefix = uniqueName("PATTERN_TOOL");
        Tool firstTool = agentClient.tool(toolPrefix + "_A", sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        firstTool.create();
        managedTools.add(firstTool);
        Tool secondTool = agentClient.tool(toolPrefix + "_B", sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        secondTool.create();
        managedTools.add(secondTool);
        String taskPrefix = uniqueName("PATTERN_TASK");
        Task firstTask = agentClient.task(taskPrefix + "_A", taskAttributes(firstTool.getToolName()), "Java task test", TaskStatus.ENABLED);
        firstTask.create();
        managedTasks.add(firstTask);
        Task secondTask = agentClient.task(taskPrefix + "_B", taskAttributes(secondTool.getToolName()), "Java task test", TaskStatus.ENABLED);
        secondTask.create();
        managedTasks.add(secondTask);
        String agentPrefix = uniqueName("PATTERN_AGENT");
        Agent firstAgent = agentClient.agent(agentPrefix + "_A", agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        firstAgent.create();
        managedAgents.add(firstAgent);
        Agent secondAgent = agentClient.agent(agentPrefix + "_B", agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        secondAgent.create();
        managedAgents.add(secondAgent);
        String teamPrefix = uniqueName("PATTERN_TEAM");
        Team firstTeam = createTeam(teamPrefix + "_A", firstAgent.getAgentName(), firstTask.getTaskName());
        Team secondTeam = createTeam(teamPrefix + "_B", secondAgent.getAgentName(), secondTask.getTaskName());

        assertThat(agentClient.listTools("^" + toolPrefix.toLowerCase(Locale.ROOT)))
                .extracting(Tool::getToolName)
                .containsExactlyInAnyOrder(firstTool.getToolName(), secondTool.getToolName());
        assertThat(agentClient.listTasks("^" + taskPrefix.toLowerCase(Locale.ROOT)))
                .extracting(Task::getTaskName)
                .containsExactlyInAnyOrder(firstTask.getTaskName(), secondTask.getTaskName());
        assertThat(agentClient.listAgents("^" + agentPrefix.toLowerCase(Locale.ROOT)))
                .extracting(Agent::getAgentName)
                .containsExactlyInAnyOrder(firstAgent.getAgentName(), secondAgent.getAgentName());
        assertThat(agentClient.listTeams("^" + teamPrefix.toLowerCase(Locale.ROOT)))
                .extracting(Team::getTeamName)
                .containsExactlyInAnyOrder(firstTeam.getTeamName(), secondTeam.getTeamName());
    }

    /**
     * Test: Load second handles for a tool, task, agent, and team, then remove each resource
     * through its original handle.
     * Expected: Removal succeeds, while enable and disable calls on every stale handle throw
     * SelectAIException.
     */
    @Test
    void test39009StaleHandlesRejectLifecycleOperationsAfterRemoval() throws Exception {
        Tool tool = agentClient.tool(uniqueName("STALE_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("STALE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("STALE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("STALE_TEAM"), agent.getAgentName(), task.getTaskName());

        Tool secondTool = agentClient.tool(tool.getToolName());
        Task secondTask = agentClient.task(task.getTaskName());
        Agent secondAgent = agentClient.agent(agent.getAgentName());
        Team secondTeam = agentClient.team(team.getTeamName());

        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(secondTeam::enable).isInstanceOf(SelectAIException.class);
        assertThatThrownBy(secondTeam::disable).isInstanceOf(SelectAIException.class);

        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(secondAgent::enable).isInstanceOf(SelectAIException.class);
        assertThatThrownBy(secondAgent::disable).isInstanceOf(SelectAIException.class);

        assertThat(task.drop(true)).isTrue();
        managedTasks.remove(task);
        assertThatThrownBy(secondTask::enable).isInstanceOf(SelectAIException.class);
        assertThatThrownBy(secondTask::disable).isInstanceOf(SelectAIException.class);

        assertThat(tool.drop(true)).isTrue();
        managedTools.remove(tool);
        assertThatThrownBy(secondTool::enable).isInstanceOf(SelectAIException.class);
        assertThatThrownBy(secondTool::disable).isInstanceOf(SelectAIException.class);
    }
}
