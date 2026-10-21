/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.state;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.ExecutionStateParams;
import com.oracle.database.selectai.model.agent.RunTeamParams;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for client-level DBMS_CLOUD_AI_AGENT team state operations. */
class ClientTeamStateIT extends AgentIntegrationFixture {
    /**
     * Test: Run a team in a conversation and retrieve its state by team name and conversation ID.
     * Expected: The run returns a nonblank value and the retrieved state is SUCCEEDED.
     */
    @Test
    void test39010ClientGetsCompletedStateByConversationId() throws Exception {
        Team team = createRunnableTeam("CLIENT_STATE");
        Conversation conversation = createConversation();
        String conversationId = conversation.getConversationId();

        TeamRunResult result = agentClient.runTeam(team.getTeamName(), runRequest(conversationId));
        assertThat(result).isNotNull();
        assertThat(result.getRawValue()).isNotBlank();
        assertThat(agentClient.getTeamStateByConversationId(team.getTeamName(), conversationId))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
    }

    /**
     * Test: Run the same team in two separate conversations and retrieve each conversation's
     * state independently.
     * Expected: Both runs return nonblank values, and each conversation reports SUCCEEDED.
     */
    @Test
    void test39011ClientStateIsIsolatedByConversationId() throws Exception {
        Team team = createRunnableTeam("CLIENT_STATE_ISOLATION");
        Conversation first = createConversation();
        Conversation second = createConversation();

        assertThat(agentClient.runTeam(team.getTeamName(), runRequest(first.getConversationId()))
                .getRawValue()).isNotBlank();
        assertThat(agentClient.runTeam(team.getTeamName(), runRequest(second.getConversationId()))
                .getRawValue()).isNotBlank();

        assertThat(agentClient.getTeamStateByConversationId(team.getTeamName(), first.getConversationId()))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
        assertThat(agentClient.getTeamStateByConversationId(team.getTeamName(), second.getConversationId()))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
    }

    /**
     * Test: Request a team's state for a conversation in which the team has not run.
     * Expected: getTeamStateByConversationId throws SelectAIException.
     */
    @Test
    void test39012ClientStateBeforeAnyRunFails() throws Exception {
        Team team = createRunnableTeam("CLIENT_STATE_BEFORE_RUN");
        Conversation conversation = createConversation();

        assertThatThrownBy(() -> agentClient.getTeamStateByConversationId(
                team.getTeamName(), conversation.getConversationId()))
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Run a human-enabled team until it waits for confirmation, then set that conversation's
     * execution state to SUCCEEDED.
     * Expected: The initial state is WAITING_FOR_HUMAN, the UNKNOWN state is rejected with
     * IllegalArgumentException, and setting SUCCEEDED returns true and updates the stored state.
     */
    @Test
    void test39015ClientSetExecutionStateCompletesWaitingHumanRun() throws Exception {
        Agent agent = agentClient.agent(uniqueName("CLIENT_WAITING_HUMAN_AGENT"),
                AgentAttributes.builder()
                        .profileName(profileName)
                        .role("Ask the user for confirmation before completing the task.")
                        .enableHumanTool(true)
                        .build(),
                "Human interaction agent", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);

        Task task = agentClient.task(uniqueName("CLIENT_WAITING_HUMAN_TASK"),
                TaskAttributes.builder()
                        .instruction("""
                                Before completing this task, ask the user for explicit confirmation
                                using the human tool. Do not infer or invent confirmation from the
                                initial request.
                                """)
                        .enableHumanTool(true)
                        .build(),
                "Human confirmation task", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);

        Team team = createTeam(uniqueName("CLIENT_WAITING_HUMAN_TEAM"),
                agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();
        String conversationId = conversation.getConversationId();

        assertThat(agentClient.runTeam(team.getTeamName(), RunTeamRequest.builder()
                .userPrompt("Please start processing a return for order ORD-123.")
                .params(RunTeamParams.forConversationId(conversationId))
                .build()).getRawValue()).isNotBlank();
        assertThat(agentClient.getTeamStateByConversationId(team.getTeamName(), conversationId))
                .isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);

        assertThatThrownBy(() -> ExecutionStateRequest.builder(TeamExecutionState.UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(agentClient.setTeamExecutionState(team.getTeamName(),
                executionState(TeamExecutionState.SUCCEEDED, conversationId))).isTrue();

        assertThat(agentClient.getTeamStateByConversationId(team.getTeamName(), conversationId))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
    }

    /**
     * Test: Drop a team, then request and set its execution state for a conversation.
     * Expected: Both state operations throw SelectAIException for the dropped team.
     */
    @Test
    void test39016ClientStateOperationsRejectDroppedTeam() throws Exception {
        Team team = createRunnableTeam("CLIENT_DROPPED_STATE");
        Conversation conversation = createConversation();
        String teamName = team.getTeamName();
        String conversationId = conversation.getConversationId();

        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);

        assertThatThrownBy(() -> agentClient.getTeamStateByConversationId(teamName, conversationId))
                .isInstanceOf(SelectAIException.class);
        assertThatThrownBy(() -> agentClient.setTeamExecutionState(teamName,
                executionState(TeamExecutionState.SUCCEEDED, conversationId)))
                .isInstanceOf(SelectAIException.class);
    }

    private Team createRunnableTeam(String stem) throws Exception {
        Agent agent = agentClient.agent(uniqueName(stem + "_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName(stem + "_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        return createTeam(uniqueName(stem + "_TEAM"), agent.getAgentName(), task.getTaskName());
    }

    private static RunTeamRequest runRequest(String conversationId) {
        return RunTeamRequest.builder().userPrompt("hey there")
                .params(RunTeamParams.forConversationId(conversationId))
                .build();
    }

    private static ExecutionStateRequest executionState(TeamExecutionState state,
                                                        String conversationId) {
        return ExecutionStateRequest.builder(state)
                .params(ExecutionStateParams.forConversationId(conversationId))
                .build();
    }
}
