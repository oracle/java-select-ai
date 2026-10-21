/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.configuration;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT team attribute mutation operations. */
class TeamConfigurationIT extends AgentIntegrationFixture {
    /**
     * Test: Set a team process, long-term memory length, and two agent-to-task member mappings
     * individually.
     * Expected: Each setter succeeds, and the process, memory length, agent names, and task
     * mappings match on both handles.
     */
    @Test
    void test33053SetTeamAttributesPersistThroughFreshHandle() throws Exception {
        Agent firstAgent = agentClient.agent(uniqueName("SET_ATTRIBUTE_FIRST_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        firstAgent.create();
        managedAgents.add(firstAgent);
        Task firstTask = agentClient.task(uniqueName("SET_ATTRIBUTE_FIRST_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        firstTask.create();
        managedTasks.add(firstTask);
        Team team = createTeam(uniqueName("SET_ATTRIBUTE_TEAM"), firstAgent.getAgentName(),
                firstTask.getTaskName());
        Agent secondAgent = agentClient.agent(uniqueName("SET_ATTRIBUTE_SECOND_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        secondAgent.create();
        managedAgents.add(secondAgent);
        Task secondTask = agentClient.task(uniqueName("SET_ATTRIBUTE_SECOND_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        secondTask.create();
        managedTasks.add(secondTask);
        List<TeamMember> members = List.of(
                member(firstAgent, firstTask), member(secondAgent, secondTask));

        assertThat(team.setAttribute("process", "sequential")).isTrue();
        assertThat(team.setAttribute("long_term_memory_length", 24)).isTrue();
        assertThat(team.setAttribute("agents", membersJson(members))).isTrue();

        assertTeamAttributes(team, members, 24);
        assertTeamAttributes(agentClient.team(team.getTeamName()), members, 24);
    }

    /**
     * Test: Replace team attributes with a sequential process, memory length of 24, and two
     * agent-task mappings.
     * Expected: The update succeeds, and both the original and reloaded team expose the requested
     * process, memory, and mappings.
     */
    @Test
    void test33054SetTeamAttributesPersistThroughFreshHandle() throws Exception {
        Agent firstAgent = agentClient.agent(uniqueName("SET_ATTRIBUTES_FIRST_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        firstAgent.create();
        managedAgents.add(firstAgent);
        Task firstTask = agentClient.task(uniqueName("SET_ATTRIBUTES_FIRST_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        firstTask.create();
        managedTasks.add(firstTask);
        Team team = createTeam(uniqueName("SET_ATTRIBUTES_TEAM"), firstAgent.getAgentName(),
                firstTask.getTaskName());
        Agent secondAgent = agentClient.agent(uniqueName("SET_ATTRIBUTES_SECOND_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        secondAgent.create();
        managedAgents.add(secondAgent);
        Task secondTask = agentClient.task(uniqueName("SET_ATTRIBUTES_SECOND_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        secondTask.create();
        managedTasks.add(secondTask);
        List<TeamMember> members = List.of(
                member(firstAgent, firstTask), member(secondAgent, secondTask));
        TeamAttributes updatedAttributes = TeamAttributes.builder()
                .agents(members)
                .process("sequential")
                .longTermMemoryLength(24)
                .build();

        assertThat(team.setAttributes(updatedAttributes)).isTrue();

        assertTeamAttributes(team, members, 24);
        assertTeamAttributes(agentClient.team(team.getTeamName()), members, 24);
    }

    /**
     * Test: Set an unrecognized attribute on a created team.
     * Expected: The SDK surfaces a SelectAIException whose cause is the database SQLException.
     */
    @Test
    void test33055UnknownTeamAttributeIsRejectedByDatabase() throws Exception {
        Team team = createSimpleTeam("UNKNOWN_ATTRIBUTE");

        assertDatabaseRejection(() -> team.setAttribute("SDK_UNKNOWN_TEAM_ATTRIBUTE", "value"));
    }

    /**
     * Test: Clear the required agents attribute on a created team.
     * Expected: The database rejects the update and its SQLException is wrapped in
     * SelectAIException.
     */
    @Test
    void test33056ClearingMandatoryTeamAgentsIsRejectedByDatabase() throws Exception {
        Team team = createSimpleTeam("CLEAR_AGENTS");

        assertDatabaseRejection(() -> team.setAttribute("agents", (String) null));
    }

    /**
     * Test: Set a created team process to an unsupported value.
     * Expected: The database rejects the update and its SQLException is wrapped in
     * SelectAIException.
     */
    @Test
    void test33057UnsupportedTeamProcessIsRejectedByDatabase() throws Exception {
        Team team = createSimpleTeam("INVALID_PROCESS");

        assertDatabaseRejection(() -> team.setAttribute("process", "not-a-supported-process"));
    }

    /**
     * Test: Assign a task to a nonexistent agent, then run the team.
     * Expected: Setting the member list succeeds, but the run fails with a database error naming
     * the missing agent and stating it does not exist.
     */
    @Test
    void test33058NonexistentAgentInTeamMembersIsRejectedAtRunTime() throws Exception {
        Task task = agentClient.task(uniqueName("MISSING_AGENT_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createSimpleTeam("MISSING_AGENT");
        String missingAgent = uniqueName("MISSING_AGENT");
        String agentsJson = "[{\"name\":\"" + missingAgent + "\",\"task\":\""
                + task.getTaskName() + "\"}]";

        assertThat(team.setAttribute("agents", agentsJson)).isTrue();
        Conversation conversation = createConversation();

        assertThatThrownBy(() -> team.run(RunTeamRequest.builder()
                .userPrompt("verify runtime validation")
                .conversationId(conversation.getConversationId())
                .build()))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getErrorCode()).isNotNull();
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class)
                            .hasMessageContaining("Agent")
                            .hasMessageContaining(missingAgent)
                            .hasMessageContaining("does not exist");
                });
    }

    private Team createSimpleTeam(String stem) throws Exception {
        Agent agent = agentClient.agent(uniqueName(stem + "_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName(stem + "_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        return createTeam(uniqueName(stem + "_TEAM"), agent.getAgentName(), task.getTaskName());
    }

    private static TeamMember member(Agent agent, Task task) {
        return TeamMember.builder(agent.getAgentName()).task(task.getTaskName()).build();
    }

    private static void assertTeamAttributes(Team team, List<TeamMember> members,
                                             int longTermMemoryLength)
            throws SelectAIException {
        TeamAttributes attributes = team.getTeamAttributes();
        assertThat(attributes.getProcess()).isEqualTo("sequential");
        assertThat(attributes.getLongTermMemoryLength()).isEqualTo(longTermMemoryLength);
        assertThat(attributes.getAgents()).extracting(TeamMember::getName)
                .containsExactlyElementsOf(members.stream().map(TeamMember::getName).toList());
        assertThat(attributes.getAgents()).extracting(TeamMember::getTask)
                .containsExactlyElementsOf(members.stream().map(TeamMember::getTask).toList());
    }

    private static String membersJson(List<TeamMember> members) {
        return members.stream()
                .map(member -> "{\"name\":\"" + member.getName() + "\",\"task\":\""
                        + member.getTask() + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
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
