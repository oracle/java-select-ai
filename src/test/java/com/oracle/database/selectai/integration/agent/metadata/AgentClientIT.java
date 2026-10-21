/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.metadata;

import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.AvailableTeam;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live client-level coverage for DBMS_CLOUD_AI_AGENT collection operations. */
class AgentClientIT extends AgentIntegrationFixture {
    /**
     * Test: Create a tool, task, agent, and team, then retrieve each resource definition.
     * Expected: Each definition names its CREATE procedure and resource; the team definition also
     * references its agent, task, and tool dependencies.
     */
    @Test
    void test39001DefinitionsCoverAllResourceTypesAndDependencies() throws Exception {
        Tool tool = agentClient.tool(uniqueName("DEFINITION_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("DEFINITION_TASK"), taskAttributes(tool.getToolName()), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("DEFINITION_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("DEFINITION_TEAM"), agent.getAgentName(), task.getTaskName());

        assertDefinition(AgentObjectType.TOOL, tool.getToolName(), "CREATE_TOOL");
        assertDefinition(AgentObjectType.TASK, task.getTaskName(), "CREATE_TASK");
        assertDefinition(AgentObjectType.AGENT, agent.getAgentName(), "CREATE_AGENT");

        String teamDefinition = agentClient.getDefinition(AgentObjectType.TEAM, team.getTeamName(), true);
        assertThat(teamDefinition).containsIgnoringCase("DBMS_CLOUD_AI_AGENT.CREATE_TEAM")
                .containsIgnoringCase(team.getTeamName())
                .containsIgnoringCase(agent.getAgentName())
                .containsIgnoringCase(task.getTaskName())
                .containsIgnoringCase(tool.getToolName());
    }

    /**
     * Test: List agents, tasks, tools, and teams with a unique pattern that matches no resource.
     * Expected: Each list operation returns an empty collection.
     */
    @Test
    void test39002ListPatternsWithNoMatchesReturnEmptyCollections() throws Exception {
        String noMatchPattern = "^" + uniqueName("NO_MATCH") + "$";

        assertThat(agentClient.listAgents(noMatchPattern)).isEmpty();
        assertThat(agentClient.listTasks(noMatchPattern)).isEmpty();
        assertThat(agentClient.listTools(noMatchPattern)).isEmpty();
        assertThat(agentClient.listTeams(noMatchPattern)).isEmpty();
    }

    /**
     * Test: Pass an unclosed regular-expression character class to the agent and team list APIs.
     * Expected: Each call throws SelectAIException whose SQL cause contains ORA-12726.
     */
    @Test
    void test39003InvalidPatternsAreRejectedForSharedListApis() throws Exception {
        Agent agent = agentClient.agent(uniqueName("INVALID_REGEX_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("INVALID_REGEX_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        createTeam(uniqueName("INVALID_REGEX_TEAM"), agent.getAgentName(), task.getTaskName());

        assertThatThrownBy(() -> agentClient.listAgents("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
        assertThatThrownBy(() -> agentClient.listTeams("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
    }

    /**
     * Test: Load second handles for a tool, task, agent, and team, then disable and re-enable each
     * resource through its original handle.
     * Expected: Each loaded handle reports DISABLED after the first change and ENABLED after the
     * second.
     */
    @Test
    void test39004LoadedResourcesRefreshStatusAfterAnotherHandleChangesIt() throws Exception {
        Tool tool = agentClient.tool(uniqueName("REFRESH_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("REFRESH_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("REFRESH_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("REFRESH_TEAM"), agent.getAgentName(), task.getTaskName());

        Tool loadedTool = agentClient.tool(tool.getToolName());
        Task loadedTask = agentClient.task(task.getTaskName());
        Agent loadedAgent = agentClient.agent(agent.getAgentName());
        Team loadedTeam = agentClient.team(team.getTeamName());

        assertThat(tool.disable()).isTrue();
        assertThat(loadedTool.getStatus()).isEqualTo(ToolStatus.DISABLED);
        assertThat(task.disable()).isTrue();
        assertThat(loadedTask.getStatus()).isEqualTo(TaskStatus.DISABLED);
        assertThat(agent.disable()).isTrue();
        assertThat(loadedAgent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(team.disable()).isTrue();
        assertThat(loadedTeam.getStatus()).isEqualTo(TeamStatus.DISABLED);

        assertThat(tool.enable()).isTrue();
        assertThat(loadedTool.getStatus()).isEqualTo(ToolStatus.ENABLED);
        assertThat(task.enable()).isTrue();
        assertThat(loadedTask.getStatus()).isEqualTo(TaskStatus.ENABLED);
        assertThat(agent.enable()).isTrue();
        assertThat(loadedAgent.getStatus()).isEqualTo(AgentStatus.ENABLED);
        assertThat(team.enable()).isTrue();
        assertThat(loadedTeam.getStatus()).isEqualTo(TeamStatus.ENABLED);
    }

    /**
     * Test: List available teams after creating an owned single-agent team and an owned
     * multi-agent team.
     * Expected: The single-agent team is returned with its description, while the multi-agent team
     * is absent.
     */
    @Test
    void test39005ListAvailableTeamsIncludesOnlyOwnedSingleAgentTeams() throws Exception {
        Agent singleAgent = agentClient.agent(uniqueName("AVAILABLE_SINGLE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        singleAgent.create();
        managedAgents.add(singleAgent);
        Task singleTask = agentClient.task(uniqueName("AVAILABLE_SINGLE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        singleTask.create();
        managedTasks.add(singleTask);
        Team singleAgentTeam = createTeam(uniqueName("AVAILABLE_SINGLE_TEAM"),
                singleAgent.getAgentName(), singleTask.getTaskName());

        Agent firstMultiAgent = agentClient.agent(uniqueName("AVAILABLE_MULTI_AGENT_ONE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        firstMultiAgent.create();
        managedAgents.add(firstMultiAgent);
        Agent secondMultiAgent = agentClient.agent(uniqueName("AVAILABLE_MULTI_AGENT_TWO"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        secondMultiAgent.create();
        managedAgents.add(secondMultiAgent);
        Task firstMultiTask = agentClient.task(uniqueName("AVAILABLE_MULTI_TASK_ONE"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        firstMultiTask.create();
        managedTasks.add(firstMultiTask);
        Task secondMultiTask = agentClient.task(uniqueName("AVAILABLE_MULTI_TASK_TWO"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        secondMultiTask.create();
        managedTasks.add(secondMultiTask);
        Team multiAgentTeam = agentClient.team(uniqueName("AVAILABLE_MULTI_TEAM"),
                TeamAttributes.builder()
                        .agents(List.of(
                                TeamMember.builder(firstMultiAgent.getAgentName())
                                        .task(firstMultiTask.getTaskName()).build(),
                                TeamMember.builder(secondMultiAgent.getAgentName())
                                        .task(secondMultiTask.getTaskName()).build()))
                        .process("sequential")
                        .build(),
                "Multiple-agent team", TeamStatus.ENABLED);
        assertThat(multiAgentTeam.create()).isTrue();
        managedTeams.add(multiAgentTeam);

        List<AvailableTeam> availableTeams = agentClient.listAvailableTeams();
        assertThat(availableTeams)
                .filteredOn(team -> team.getTeamName().equalsIgnoreCase(singleAgentTeam.getTeamName()))
                .singleElement()
                .satisfies(team -> assertThat(team.getDescription())
                        .isEqualTo(singleAgentTeam.getDescription()));
        assertThat(availableTeams)
                .noneMatch(team -> team.getTeamName().equalsIgnoreCase(multiAgentTeam.getTeamName()));
    }

    private void assertDefinition(AgentObjectType objectType, String objectName, String createProcedure)
            throws Exception {
        String definition = agentClient.getDefinition(objectType, objectName, false);
        assertThat(definition).containsIgnoringCase("DBMS_CLOUD_AI_AGENT." + createProcedure)
                .containsIgnoringCase(objectName);
    }
}
