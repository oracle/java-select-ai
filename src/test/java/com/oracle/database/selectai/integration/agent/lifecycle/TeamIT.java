/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.lifecycle;

import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.ExecutionStateParams;
import com.oracle.database.selectai.model.agent.RunTeamParams;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamDescription;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT team operations. */
class TeamIT extends AgentIntegrationFixture {
    /**
     * Test: Create a team from one enabled agent and its task, then inspect the returned handle.
     * Expected: The handle exposes its generated name, description, sequential process, and one
     * member.
     */
    @Test
    void test33000CreateAndIdentity() throws Exception {
        Agent agent = agentClient.agent(uniqueName("TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("TEAM"), agent.getAgentName(), task.getTaskName());
        assertThat(team.getTeamName()).isNotBlank();
        assertThat(team.getDescription()).isEqualTo("Java team test");
        assertThat(team.getTeamAttributes().getProcess()).isEqualTo("sequential");
        assertThat(team.getTeamAttributes().getAgents()).hasSize(1);
    }

    /**
     * Test: Create a team and query the collection both without a filter and with an exact-name
     * pattern.
     * Expected: Both results contain the created team, and the exact pattern returns only that
     * team.
     */
    @Test
    void test33001ListTeams() throws Exception {
        Agent agent = agentClient.agent(uniqueName("LIST_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("LIST_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("LIST_TEAM"), agent.getAgentName(), task.getTaskName());
        assertThat(agentClient.listTeams()).extracting(Team::getTeamName).contains(team.getTeamName());
        assertThat(agentClient.listTeams("^" + team.getTeamName() + "$"))
                .extracting(Team::getTeamName).containsExactly(team.getTeamName());
    }

    /**
     * Test: List one disabled team by exact name after creating it with a description and memory
     * setting.
     * Expected: The listed handle preserves its name, description, status, process, memory length,
     * agent, and task.
     */
    @Test
    void test33053ListTeamsHydratesMetadataAndAttributes() throws Exception {
        Agent agent = agentClient.agent(uniqueName("LIST_METADATA_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("LIST_METADATA_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        TeamMember member = TeamMember.builder(agent.getAgentName()).task(task.getTaskName()).build();
        String name = uniqueName("LIST_METADATA");
        TeamAttributes attributes = TeamAttributes.builder()
                .agents(List.of(member))
                .process("sequential")
                .longTermMemoryLength(7)
                .build();
        Team team = agentClient.team(name, attributes, "Team list metadata", TeamStatus.DISABLED);
        team.create();
        managedTeams.add(team);

        List<Team> listedTeams = agentClient.listTeams("^" + name + "$");
        assertThat(listedTeams).hasSize(1);
        Team listed = listedTeams.get(0);
        assertThat(listed.getTeamName()).isEqualTo(name);
        assertThat(listed.getDescription()).isEqualTo("Team list metadata");
        assertThat(listed.getStatus()).isEqualTo(TeamStatus.DISABLED);
        assertThat(listed.getTeamAttributes().getProcess()).isEqualTo("sequential");
        assertThat(listed.getTeamAttributes().getLongTermMemoryLength()).isEqualTo(7);
        assertThat(listed.getTeamAttributes().getAgents()).singleElement().satisfies(listedMember -> {
            assertThat(listedMember.getName()).isEqualTo(agent.getAgentName());
            assertThat(listedMember.getTask()).isEqualTo(task.getTaskName());
        });
    }

    /**
     * Test: Create five teams sharing a prefix and one team with a different prefix, then filter by
     * the first prefix.
     * Expected: The result contains exactly the five names with the selected prefix.
     */
    @Test
    void test33054ListTeamsMultipleMatchesUsingPattern() throws Exception {
        List<String> names = createTeamListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listTeamNames("^" + prefix)).containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create teams with a shared uppercase prefix and query using its lowercase form.
     * Expected: The case-insensitive pattern returns exactly the five matching team names.
     */
    @Test
    void test33055ListTeamsCaseInsensitivePattern() throws Exception {
        List<String> names = createTeamListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listTeamNames("^" + prefix.toLowerCase(Locale.ROOT)))
                .containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create two groups of teams and filter with a regular-expression alternation of their
     * prefixes.
     * Expected: The result contains all six created names from both groups.
     */
    @Test
    void test33056ListTeamsAlternationPattern() throws Exception {
        List<String> names = createTeamListFixture();
        String firstPrefix = listPrefix(names.get(0));
        String secondPrefix = listPrefix(names.get(5));

        assertThat(listTeamNames("^(" + firstPrefix + "|" + secondPrefix + ")"))
                .containsExactlyInAnyOrderElementsOf(names);
    }

    /**
     * Test: Query the team collection with an anchored pattern built from a unique absent name.
     * Expected: The list is empty.
     */
    @Test
    void test33057ListTeamsNonMatchingPatternReturnsEmptyList() throws Exception {
        String pattern = "^" + uniqueName("NO_MATCH") + "$";

        assertThat(agentClient.listTeams(pattern)).isEmpty();
    }

    /**
     * Test: Query the team collection with a unique literal name that was not created.
     * Expected: The list is empty.
     */
    @Test
    void test33058ListTeamsLiteralNonMatchingPatternReturnsEmptyList() throws Exception {
        assertThat(agentClient.listTeams(uniqueName("NO_MATCH_LITERAL"))).isEmpty();
    }

    /**
     * Test: Create a team, then pass an unclosed character class to the team listing API.
     * Expected: The call throws SelectAIException whose SQL cause contains ORA-12726.
     */
    @Test
    void test33059ListTeamsInvalidPatternIsRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("INVALID_REGEX_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("INVALID_REGEX_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        createTeam(uniqueName("INVALID_REGEX_TEAM"), agent.getAgentName(), task.getTaskName());

        assertThatThrownBy(() -> agentClient.listTeams("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
    }

    /**
     * Test: Create a team and list using its full name followed by a regular-expression end anchor.
     * Expected: The result contains exactly the team whose name ends at that match.
     */
    @Test
    void test33060ListTeamsEndAnchorPattern() throws Exception {
        List<String> names = createTeamListFixture();
        String exact = names.get(0);
        String pattern = exact + "$";

        assertThat(listTeamNames(pattern)).containsExactly(exact);
    }

    /**
     * Test: Create a team, then query with a 1,000-character regular-expression body.
     * Expected: The call throws SelectAIException whose SQL cause contains ORA-12733.
     */
    @Test
    void test33061ListTeamsOverlongPatternIsRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("OVERLONG_REGEX_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("OVERLONG_REGEX_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        createTeam(uniqueName("OVERLONG_REGEX_TEAM"), agent.getAgentName(), task.getTaskName());
        String pattern = "^" + "a".repeat(1000) + "$";

        assertThatThrownBy(() -> agentClient.listTeams(pattern))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12733:"));
    }

    /**
     * Test: Create three teams in reverse name order and list them using a pattern matching all
     * three.
     * Expected: The returned names are sorted alphabetically.
     */
    @Test
    void test33062ListTeamsAreOrderedByName() throws Exception {
        Agent agent = agentClient.agent(uniqueName("LIST_ORDER_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("LIST_ORDER_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String prefix = uniqueName("LIST_ORDER");
        String first = prefix + "_A";
        String second = prefix + "_B";
        String third = prefix + "_C";
        createTeam(second, agent.getAgentName(), task.getTaskName());
        createTeam(first, agent.getAgentName(), task.getTaskName());
        createTeam(third, agent.getAgentName(), task.getTaskName());

        assertThat(listTeamNames("^" + prefix + "_[ABC]$"))
                .containsExactly(first, second, third);
    }

    /**
     * Test: Create a team and load it again by name.
     * Expected: The fetched handle has the same team name and its member references the created
     * agent and task.
     */
    @Test
    void test33002FetchTeam() throws Exception {
        Agent agent = agentClient.agent(uniqueName("FETCH_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("FETCH_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team created = createTeam(uniqueName("FETCH_TEAM"), agent.getAgentName(), task.getTaskName());
        Team fetched = agentClient.team(created.getTeamName());
        assertThat(fetched.getTeamName()).isEqualTo(created.getTeamName());
        assertThat(fetched.getTeamAttributes().getAgents()).extracting(TeamMember::getName)
                .containsExactly(agent.getAgentName());
        assertThat(fetched.getTeamAttributes().getAgents()).extracting(TeamMember::getTask)
                .containsExactly(task.getTaskName());
    }

    /**
     * Test: Run a team with a prompt and a newly created conversation.
     * Expected: The run returns a nonblank response.
     */
    @Test
    void test33003RunTeam() throws Exception {
        Agent agent = agentClient.agent(uniqueName("RUN_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("RUN_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("RUN_TEAM"), agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();
        TeamRunResult result = team.run(RunTeamRequest.builder()
                .userPrompt("What is 2+2?")
                .params(RunTeamParams.forConversationId(conversation.getConversationId()))
                .build());
        assertThat(result.getRawValue()).isNotBlank();
    }

    /**
     * Test: Disable an enabled team and then enable it again.
     * Expected: Both operations return true, and the handle reports DISABLED then ENABLED.
     */
    @Test
    void test33004DisableEnableContract() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STATUS_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("STATUS_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("STATUS_TEAM"), agent.getAgentName(), task.getTaskName());
        assertThat(team.disable()).isTrue();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISABLED);
        assertThat(team.enable()).isTrue();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ENABLED);
    }

    /**
     * Test: Create a disabled team with a member, sequential process, memory length, and
     * supervisor, then fetch it.
     * Expected: The fetched team preserves its description, status, member references, process,
     * memory length, and supervisor.
     */
    @Test
    void test33007AllTeamAttributesRoundTrip() throws Exception {
        Agent worker = agentClient.agent(uniqueName("ROUND_TRIP_TEAM_WORKER"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        worker.create();
        managedAgents.add(worker);
        Task task = agentClient.task(uniqueName("ROUND_TRIP_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String supervisorName = uniqueName("ROUND_TRIP_TEAM_SUPERVISOR");
        AgentAttributes supervisorAttributes = AgentAttributes.builder()
                .profileName(profileName)
                .role("Coordinate the team members.")
                .supervisor(true)
                .enableHumanTool(false)
                .build();
        Agent supervisor = agentClient.agent(supervisorName, supervisorAttributes,
                "Round-trip supervisor", AgentStatus.ENABLED);
        supervisor.create();
        managedAgents.add(supervisor);

        TeamMember member = TeamMember.builder(worker.getAgentName())
                .task(task.getTaskName()).build();
        TeamAttributes attributes = TeamAttributes.builder()
                .agents(List.of(member))
                .process("sequential")
                .longTermMemoryLength(17)
                .supervisorAgent(supervisorName)
                .build();
        String name = uniqueName("ALL_TEAM_ATTRIBUTES");
        Team created = agentClient.team(name, attributes, "All attributes team", TeamStatus.DISABLED);
        created.create();
        managedTeams.add(created);

        Team fetched = agentClient.team(name);
        TeamAttributes fetchedAttributes = fetched.getTeamAttributes();
        assertThat(fetched.getTeamName()).isEqualTo(name);
        assertThat(fetched.getDescription()).isEqualTo("All attributes team");
        assertThat(fetched.getStatus()).isEqualTo(TeamStatus.DISABLED);
        assertThat(fetchedAttributes.getAgents()).hasSize(1);
        assertThat(fetchedAttributes.getAgents().get(0).getName()).isEqualTo(member.getName());
        assertThat(fetchedAttributes.getAgents().get(0).getTask()).isEqualTo(member.getTask());
        assertThat(fetchedAttributes.getProcess()).isEqualTo(attributes.getProcess());
        assertThat(fetchedAttributes.getLongTermMemoryLength())
                .isEqualTo(attributes.getLongTermMemoryLength());
        assertThat(fetchedAttributes.getSupervisorAgent())
                .isEqualTo(attributes.getSupervisorAgent());
    }

    /**
     * Test: Attempt to create a team whose attributes specify a process but no agents.
     * Expected: Creation throws SelectAIException with a SQL cause containing an ORA error.
     */
    @Test
    void test33031MissingTeamAgentsRejectedByDatabase() {
        Team team = agentClient.team(uniqueName("MISSING_TEAM_AGENTS"),
                TeamAttributes.builder().process("sequential").build(),
                null, TeamStatus.ENABLED);
        assertTeamCreationRejected(team);
    }

    /**
     * Test: Create a team member without assigning a task, then fetch the team and the agent-named
     * default task.
     * Expected: The member has no task reference, and the generated task instruction contains the
     * default response guidance.
     */
    @Test
    void test33032TeamMemberWithoutTaskCreatesDefaultTask() throws Exception {
        Agent agent = agentClient.agent(uniqueName("NO_TASK_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = agentClient.team(uniqueName("NO_TASK_TEAM"), TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(agent.getAgentName()).build()))
                .process("sequential")
                .build(), null, TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);

        Team fetched = agentClient.team(team.getTeamName());
        assertThat(fetched.getTeamAttributes().getAgents())
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getName()).isEqualTo(agent.getAgentName());
                    assertThat(member.getTask()).isNull();
                });

        Task generatedTask = agentClient.task(agent.getAgentName() + "$TASK");
        assertThat(generatedTask.getTaskAttributes().getInstruction())
                .contains("Respond to user requests.");
    }

    /**
     * Test: Create a team that references a nonexistent agent and attempt to run it.
     * Expected: The run throws SelectAIException with a SQL cause naming the missing agent and
     * stating it does not exist.
     */
    @Test
    void test33033NonexistentAgentReferenceRejectedAtRunTime() throws Exception {
        Task task = agentClient.task(uniqueName("MISSING_AGENT_REFERENCE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String missingAgentName = uniqueName("MISSING_AGENT_REFERENCE");
        Team team = agentClient.team(uniqueName("MISSING_AGENT_REFERENCE_TEAM"), TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(missingAgentName)
                        .task(task.getTaskName()).build()))
                .process("sequential")
                .build(), null, TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);
        Conversation conversation = createConversation();

        assertThatThrownBy(() -> team.run(RunTeamRequest.builder()
                .userPrompt("verify runtime validation")
                .params(RunTeamParams.forConversationId(conversation.getConversationId()))
                .build()))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getErrorCode()).isNotNull();
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class)
                            .hasMessageContaining("Agent")
                            .hasMessageContaining(missingAgentName)
                            .hasMessageContaining("does not exist");
                });
    }

    /**
     * Test: Create a team that references a nonexistent task and attempt to run it.
     * Expected: The run throws SelectAIException with a SQL cause naming the missing task and
     * stating it does not exist.
     */
    @Test
    void test33034NonexistentTaskReferenceRejectedAtRunTime() throws Exception {
        Agent agent = agentClient.agent(uniqueName("MISSING_TASK_REFERENCE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        String missingTaskName = uniqueName("MISSING_TASK_REFERENCE");
        Team team = agentClient.team(uniqueName("MISSING_TASK_REFERENCE_TEAM"), TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(agent.getAgentName())
                        .task(missingTaskName).build()))
                .process("sequential")
                .build(), null, TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);
        Conversation conversation = createConversation();

        assertThatThrownBy(() -> team.run(RunTeamRequest.builder()
                .userPrompt("verify runtime validation")
                .params(RunTeamParams.forConversationId(conversation.getConversationId()))
                .build()))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getErrorCode()).isNotNull();
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class)
                            .hasMessageContaining("Task")
                            .hasMessageContaining(missingTaskName)
                            .hasMessageContaining("does not exist");
                });
    }

    /**
     * Test: Attempt to create a team using an unsupported process value.
     * Expected: Creation throws SelectAIException with a SQL cause containing an ORA error.
     */
    @Test
    void test33035InvalidProcessRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("INVALID_PROCESS_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("INVALID_PROCESS_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = agentClient.team(uniqueName("INVALID_PROCESS_TEAM"), TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(agent.getAgentName())
                        .task(task.getTaskName()).build()))
                .process("not-a-supported-process")
                .build(), null, TeamStatus.ENABLED);
        assertTeamCreationRejected(team);
    }

    /**
     * Test: Configure the same supervisor agent as both the team supervisor and a team member.
     * Expected: Team creation throws SelectAIException with a SQL cause containing an ORA error.
     */
    @Test
    void test33036SupervisorListedAsWorkerRejectedByDatabase() throws Exception {
        String supervisorName = uniqueName("INVALID_SUPERVISOR_AGENT");
        Agent supervisor = agentClient.agent(supervisorName, AgentAttributes.builder()
                .profileName(profileName)
                .role("Coordinate the team.")
                .supervisor(true)
                .build(), null, AgentStatus.ENABLED);
        supervisor.create();
        managedAgents.add(supervisor);
        Task task = agentClient.task(uniqueName("INVALID_SUPERVISOR_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = agentClient.team(uniqueName("INVALID_SUPERVISOR_TEAM"), TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(supervisor.getAgentName())
                        .task(task.getTaskName()).build()))
                .process("sequential")
                .supervisorAgent(supervisorName)
                .build(), null, TeamStatus.ENABLED);
        assertTeamCreationRejected(team);
    }

    /**
     * Test: Create a team while leaving its status unset.
     * Expected: The created team reports ENABLED.
     */
    @Test
    void test33037NullStatusUsesDatabaseDefaultEnabled() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DEFAULT_STATUS_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DEFAULT_STATUS_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = agentClient.team(uniqueName("DEFAULT_STATUS_TEAM"), TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(agent.getAgentName())
                        .task(task.getTaskName()).build()))
                .process("sequential")
                .build(), null, null);
        team.create();
        managedTeams.add(team);
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ENABLED);
    }

    /**
     * Test: Call enable on a team that is already enabled.
     * Expected: The call throws SelectAIException with ORA-20053, and the status remains ENABLED.
     */
    @Test
    void test33005EnableAlreadyEnabledTeamFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("ENABLE_TWICE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("ENABLE_TWICE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("ENABLE_TWICE_TEAM"), agent.getAgentName(), task.getTaskName());
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ENABLED);

        assertThatThrownBy(team::enable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20053: Team is already in the desired status"));
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ENABLED);
    }

    /**
     * Test: Disable a team, then call disable again.
     * Expected: The second call throws SelectAIException with ORA-20053, and the status remains
     * DISABLED.
     */
    @Test
    void test33006DisableAlreadyDisabledTeamFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DISABLE_TWICE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DISABLE_TWICE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("DISABLE_TWICE_TEAM"), agent.getAgentName(), task.getTaskName());
        assertThat(team.disable()).isTrue();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISABLED);

        assertThatThrownBy(team::disable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20053: Team is already in the desired status"));
        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISABLED);
    }

    /**
     * Test: Load a team using a unique name that has not been created.
     * Expected: The lookup throws an exception.
     */
    @Test
    void test33008FetchMissingTeamFails() {
        assertThatThrownBy(() -> agentClient.team(uniqueName("MISSING_TEAM")))
                .isInstanceOf(Exception.class);
    }

    /**
     * Test: Drop a team, then call enable through the handle that created it.
     * Expected: The stale handle operation throws an exception.
     */
    @Test
    void test33015EnableAfterDeleteFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DELETE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DELETE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("DELETE_TEAM"), agent.getAgentName(), task.getTaskName());
        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(team::enable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Drop a team, then call disable through the handle that created it.
     * Expected: The stale handle operation throws an exception.
     */
    @Test
    void test33016DisableAfterDeleteFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DELETE_TEAM2_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DELETE_TEAM2_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("DELETE_TEAM2"), agent.getAgentName(), task.getTaskName());
        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(team::disable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Drop a team and then load it again by its former name.
     * Expected: The lookup throws an exception.
     */
    @Test
    void test33020FetchAfterDeleteFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("FETCH_DELETE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("FETCH_DELETE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("FETCH_DELETE_TEAM"), agent.getAgentName(), task.getTaskName());
        String name = team.getTeamName();
        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(() -> agentClient.team(name)).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create a team with a supervisor and a separate worker, then load the team again.
     * Expected: The fetched team attributes retain the supervisor agent name.
     */
    @Test
    void test33022SupervisorTeam() throws Exception {
        Agent worker = agentClient.agent(uniqueName("WORKER_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        worker.create();
        managedAgents.add(worker);
        Task task = agentClient.task(uniqueName("SUPERVISED_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String supervisorName = uniqueName("SUPERVISOR_AGENT");
        AgentAttributes supervisorAttributes = AgentAttributes.builder()
                .profileName(profileName).role("Coordinate specialist agents")
                .supervisor(true).enableHumanTool(false).build();
        Agent supervisor = agentClient.agent(supervisorName, supervisorAttributes, null, AgentStatus.ENABLED);
        supervisor.create();
        managedAgents.add(supervisor);
        TeamAttributes attributes = TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(worker.getAgentName()).task(task.getTaskName()).build()))
                .process("sequential").supervisorAgent(supervisorName).build();
        String teamName = uniqueName("SUPERVISED_TEAM");
        Team team = agentClient.team(teamName, attributes, null, TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);
        assertThat(agentClient.team(teamName).getTeamAttributes().getSupervisorAgent())
                .isEqualTo(supervisorName);
    }

    /**
     * Test: Describe a team containing one agent and task.
     * Expected: The returned description is nonblank and includes the team name.
     */
    @Test
    void test33023DescribeTeam() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DESCRIBE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DESCRIBE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("DESCRIBE_TEAM"), agent.getAgentName(), task.getTaskName());
        TeamDescription description = team.describe();
        assertThat(description).isNotNull();
        assertThat(description.getTeamName()).isEqualTo(team.getTeamName());
    }

    /**
     * Test: Describe a team whose task uses a SQL tool.
     * Expected: The JSON has the documented team metadata and one SQL skill with tool details and
     * typed function-argument fields.
     */
    @Test
    void test33038DescribeTeamReturnsDocumentedMetadataAndSqlSkill() throws Exception {
        Tool tool = agentClient.tool(uniqueName("DESCRIBE_SQL_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("DESCRIBE_SQL_TASK"), taskAttributes(tool.getToolName()), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("DESCRIBE_SQL_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("DESCRIBE_SQL_TEAM"), agent.getAgentName(), task.getTaskName());

        TeamDescription description = team.describe();
        assertDocumentedTeamMetadata(description, team, "Java team test");
        JsonNode skills = skillsJson(description);
        assertThat(skills.size()).isEqualTo(1);

        JsonNode skill = skills.get(0);
        assertThat(skill.path("id").isIntegralNumber()).isTrue();
        assertThat(skill.path("attributes").path("tool_type").asText()).isEqualTo("SQL");
        JsonNode rawToolParams = skill.path("attributes").path("tool_params");
        assertThat(rawToolParams.isTextual()).as("tool_params is database JSON text").isTrue();
        JsonNode toolParams = new ObjectMapper().readTree(rawToolParams.asText());
        assertThat(toolParams.path("profile_name").asText())
                .isEqualTo(profileName);
        assertThat(skill.path("description").asText()).isEqualTo("Java SQL tool test");
        assertThat(skill.path("tool_name").asText()).isEqualTo(tool.getToolName());
        assertThat(skill.path("function_args").isArray()).isTrue();
        assertThat(skill.path("function_args").size()).isGreaterThan(0);
        for (JsonNode argument : skill.path("function_args")) {
            assertThat(argument.has("argument_name")).isTrue();
            assertThat(argument.has("data_type")).isTrue();
            assertThat(argument.has("in_out")).isTrue();
            assertThat(argument.path("position").isIntegralNumber()).isTrue();
            assertThat(argument.has("defaulted")).isTrue();
        }
    }

    /**
     * Test: Describe a team whose task uses a custom PL/SQL function tool.
     * Expected: The skill identifies the function and includes P_BIRTH_DATE as a required DATE
     * input at position one.
     */
    @Test
    void test33039DescribeTeamWithCustomToolIncludesFunctionArguments() throws Exception {
        String function = createPythonPlsqlFunction();
        Tool tool = agentClient.tool(uniqueName("DESCRIBE_CUSTOM_TOOL"),
                ToolAttributes.builder()
                        .instruction("Custom age tool")
                        .function(function).build(),
                "Custom age tool", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("DESCRIBE_CUSTOM_TASK"), taskAttributes(tool.getToolName()), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("DESCRIBE_CUSTOM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("DESCRIBE_CUSTOM_TEAM"), agent.getAgentName(), task.getTaskName());

        TeamDescription description = team.describe();
        assertDocumentedTeamMetadata(description, team, "Java team test");
        JsonNode skill = skillsJson(description).get(0);
        assertThat(skill.path("tool_name").asText()).isEqualTo(tool.getToolName());
        assertThat(skill.path("description").asText()).isEqualTo("Custom age tool");
        assertThat(skill.path("attributes").path("function").asText()).isEqualTo(function);
        assertThat(skill.path("function_args").isArray()).isTrue();
        JsonNode argument = null;
        for (JsonNode candidate : skill.path("function_args")) {
            if ("P_BIRTH_DATE".equals(candidate.path("argument_name").asText())) {
                argument = candidate;
                break;
            }
        }
        assertThat(argument).isNotNull();
        assertThat(argument.path("argument_name").asText()).isEqualTo("P_BIRTH_DATE");
        assertThat(argument.path("data_type").asText()).isEqualTo("DATE");
        assertThat(argument.path("in_out").asText()).isEqualTo("IN");
        assertThat(argument.path("position").asInt()).isEqualTo(1);
        assertThat(argument.path("defaulted").asText()).isEqualTo("N");
    }

    /**
     * Test: Attach one SQL tool to both the agent and task, and a second tool to the task, then
     * describe the team.
     * Expected: The skill names contain both tools exactly once.
     */
    @Test
    void test33040DescribeTeamAggregatesAndDeduplicatesAgentAndTaskTools() throws Exception {
        Tool sqlTool = agentClient.tool(uniqueName("AGGREGATE_SQL_TOOL"), sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        sqlTool.create();
        managedTools.add(sqlTool);
        String function = createPythonPlsqlFunction();
        Tool customTool = agentClient.tool(uniqueName("AGGREGATE_CUSTOM_TOOL"),
                ToolAttributes.builder()
                        .instruction("Aggregate custom tool")
                        .function(function).build(),
                "Aggregate custom tool", ToolStatus.ENABLED);
        customTool.create();
        managedTools.add(customTool);

        Agent agent = agentClient.agent(uniqueName("AGGREGATE_AGENT"),
                AgentAttributes.builder()
                        .profileName(profileName)
                        .role("Use the configured tools.")
                        .enableHumanTool(false)
                        .tools(List.of(sqlTool.getToolName()))
                        .build(),
                "Aggregate agent", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("AGGREGATE_TASK"),
                taskAttributes(sqlTool.getToolName(), customTool.getToolName()), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("AGGREGATE_TEAM"), agent.getAgentName(), task.getTaskName());

        List<String> skillNames = skillNames(skillsJson(team.describe()));
        assertThat(skillNames)
                .containsExactlyInAnyOrder(sqlTool.getToolName(), customTool.getToolName())
                .doesNotHaveDuplicates();
    }

    /**
     * Test: Describe a team whose agent and task have no configured tools.
     * Expected: The description has the expected team metadata and an empty skills array.
     */
    @Test
    void test33041DescribeTeamWithoutToolsReturnsEmptySkills() throws Exception {
        Agent agent = agentClient.agent(uniqueName("EMPTY_SKILLS_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("EMPTY_SKILLS_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String teamName = uniqueName("EMPTY_SKILLS_TEAM");
        Team team = agentClient.team(teamName, TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(agent.getAgentName()).task(task.getTaskName()).build()))
                .process("sequential")
                .build(), null, TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);

        TeamDescription description = team.describe();
        assertDocumentedTeamMetadata(description, team, null);
        assertThat(skillsJson(description).size()).isZero();
    }

    /**
     * Test: Disable a team before requesting its description.
     * Expected: The call throws SelectAIException with a SQL cause identifying the team as
     * disabled.
     */
    @Test
    void test33042DescribeDisabledTeamRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DISABLED_DESCRIBE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DISABLED_DESCRIBE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("DISABLED_DESCRIBE_TEAM"),
                agent.getAgentName(), task.getTaskName());
        assertThat(team.disable()).isTrue();

        assertThatThrownBy(team::describe)
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getErrorCode()).isNotNull();
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class)
                            .hasMessageContaining("Team")
                            .hasMessageContaining(team.getTeamName())
                            .hasMessageContaining("is disabled");
                });
    }

    /**
     * Test: Load a second handle, drop the team through the owner handle, then describe through the
     * stale handle.
     * Expected: The stale handle call throws SelectAIException.
     */
    @Test
    void test33043StaleTeamHandleDescribeFailsAfterOtherHandleDropsTeam() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STALE_DESCRIBE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("STALE_DESCRIBE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team owner = createTeam(uniqueName("STALE_DESCRIBE_TEAM"),
                agent.getAgentName(), task.getTaskName());
        Team stale = agentClient.team(owner.getTeamName());

        assertThat(owner.drop(true)).isTrue();
        managedTeams.remove(owner);
        assertThatThrownBy(stale::describe).isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Drop a team directly through DBMS_CLOUD_AI_AGENT, then describe it through its existing
     * Java handle.
     * Expected: The SelectAIException retains a SQL cause containing an ORA error and the dropped
     * team name.
     */
    @Test
    void test33044DescribeTeamPreservesDatabaseErrorAfterExternalDrop() throws Exception {
        Agent agent = agentClient.agent(uniqueName("EXTERNAL_DROP_DESCRIBE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("EXTERNAL_DROP_DESCRIBE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("EXTERNAL_DROP_DESCRIBE_TEAM"),
                agent.getAgentName(), task.getTaskName());
        String teamName = team.getTeamName();

        try (CallableStatement statement = jdbcConnection().prepareCall(
                "BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DROP_TEAM(?, ?); END;")) {
            statement.setString(1, teamName);
            statement.setBoolean(2, true);
            statement.execute();
        }
        managedTeams.remove(team);

        assertThatThrownBy(team::describe)
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getErrorCode()).isNotNull();
                    assertThat(exception.getCause()).isInstanceOfSatisfying(SQLException.class,
                            cause -> assertThat((Throwable) cause).hasMessageContaining("ORA-")
                                    .hasMessageContaining(teamName));
                });
    }

    /**
     * Test: Run a team in a conversation and retrieve its execution state for that conversation.
     * Expected: The state is SUCCEEDED.
     */
    @Test
    void test33024TeamState() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STATE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("STATE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("STATE_TEAM"), agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();
        team.run(RunTeamRequest.builder().userPrompt("hey there")
                .params(RunTeamParams.forConversationId(conversation.getConversationId())).build());
        assertThat(team.getStateByConversationId(conversation.getConversationId()))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
    }

    /**
     * Test: Run the same team in two separate conversations and retrieve state using each
     * conversation ID.
     * Expected: Each conversation lookup returns SUCCEEDED.
     */
    @Test
    void test33045TeamStateIsolatedByConversationId() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STATE_ISOLATION_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("STATE_ISOLATION_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("STATE_ISOLATION_TEAM"), agent.getAgentName(), task.getTaskName());
        Conversation first = createConversation();
        Conversation second = createConversation();

        team.run(RunTeamRequest.builder().userPrompt("hey there from the first conversation")
                .params(RunTeamParams.forConversationId(first.getConversationId())).build());
        team.run(RunTeamRequest.builder().userPrompt("hey there from the second conversation")
                .params(RunTeamParams.forConversationId(second.getConversationId())).build());

        assertThat(team.getStateByConversationId(first.getConversationId()))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
        assertThat(team.getStateByConversationId(second.getConversationId()))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
    }

    /**
     * Test: Request team state for a conversation in which the team has not run.
     * Expected: The lookup throws SelectAIException.
     */
    @Test
    void test33046TeamStateBeforeAnyRunFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STATE_BEFORE_RUN_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("STATE_BEFORE_RUN_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("STATE_BEFORE_RUN_TEAM"), agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();

        assertThatThrownBy(() -> team.getStateByConversationId(conversation.getConversationId()))
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Request team state using an unrelated UUID as the conversation ID.
     * Expected: The lookup throws SelectAIException.
     */
    @Test
    void test33047InvalidConversationIdFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("INVALID_STATE_CONVERSATION_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("INVALID_STATE_CONVERSATION_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("INVALID_STATE_CONVERSATION_TEAM"),
                agent.getAgentName(), task.getTaskName());

        assertThatThrownBy(() -> team.getStateByConversationId(UUID.randomUUID().toString()))
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Set execution state for an unknown conversation ID, then query state for that ID.
     * Expected: The update call returns true, while the state lookup still throws
     * SelectAIException.
     */
    @Test
    void test33052SetExecutionStateWithoutMatchingRunIsNoOp() throws Exception {
        Agent agent = agentClient.agent(uniqueName("INVALID_SET_STATE_CONVERSATION_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("INVALID_SET_STATE_CONVERSATION_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("INVALID_SET_STATE_CONVERSATION_TEAM"),
                agent.getAgentName(), task.getTaskName());
        String unknownConversationId = UUID.randomUUID().toString();

        assertThat(team.setExecutionState(
                executionState(TeamExecutionState.SUCCEEDED, unknownConversationId)))
                .isTrue();
        assertThatThrownBy(() -> team.getStateByConversationId(unknownConversationId))
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Request team state with a blank conversation ID.
     * Expected: The SDK rejects the invalid identifier locally.
     */
    @Test
    void test33048BlankConversationIdIsRejectedLocally() throws Exception {
        Agent agent = agentClient.agent(uniqueName("MISSING_STATE_CONVERSATION_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("MISSING_STATE_CONVERSATION_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("MISSING_STATE_CONVERSATION_TEAM"),
                agent.getAgentName(), task.getTaskName());

        assertThatThrownBy(() -> team.getStateByConversationId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Run a human-enabled team until it waits for confirmation, then submit an invalid state
     * followed by SUCCEEDED.
     * Expected: The run first reports WAITING_FOR_HUMAN; the unknown state is rejected locally,
     * and the valid update changes state to SUCCEEDED.
     */
    @Test
    void test33050SetExecutionStateValidatesAndCompletesWaitingHumanRun() throws Exception {
        Agent agent = agentClient.agent(uniqueName("WAITING_HUMAN_AGENT"),
                AgentAttributes.builder()
                        .profileName(profileName)
                        .role("Ask the user for confirmation before completing the task.")
                        .enableHumanTool(true)
                        .build(),
                "Human interaction agent", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);

        Task task = agentClient.task(uniqueName("WAITING_HUMAN_TASK"),
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

        Team team = createTeam(uniqueName("WAITING_HUMAN_TEAM"),
                agent.getAgentName(), task.getTaskName());
        Conversation conversation = createConversation();
        String conversationId = conversation.getConversationId();

        team.run(RunTeamRequest.builder()
                .userPrompt("Please start processing a return for order ORD-123.")
                .params(RunTeamParams.forConversationId(conversationId))
                .build());

        assertThat(team.getStateByConversationId(conversationId))
                .isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);

        assertThatThrownBy(() -> ExecutionStateRequest.builder(TeamExecutionState.UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(team.setExecutionState(
                executionState(TeamExecutionState.SUCCEEDED, conversationId))).isTrue();

        assertThat(team.getStateByConversationId(conversationId))
                .isEqualTo(TeamExecutionState.SUCCEEDED);
    }

    /**
     * Test: Load a second handle, drop the team through its owner, then set execution state through
     * the stale handle.
     * Expected: The stale handle call throws SelectAIException.
     */
    @Test
    void test33051StaleTeamHandleSetExecutionStateFailsAfterDrop() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STALE_STATE_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("STALE_STATE_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team owner = createTeam(uniqueName("STALE_STATE_TEAM"),
                agent.getAgentName(), task.getTaskName());
        Team stale = agentClient.team(owner.getTeamName());
        Conversation conversation = createConversation();

        assertThat(owner.drop(true)).isTrue();
        managedTeams.remove(owner);

        assertThatThrownBy(() -> stale.setExecutionState(
                executionState(TeamExecutionState.SUCCEEDED, conversation.getConversationId())))
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Drop a team, verify it cannot be loaded, then create it again through the same object.
     * Expected: The second create succeeds and the team can be fetched by its original name.
     */
    @Test
    void test33025DeleteAndRecreateTeam() throws Exception {
        Agent agent = agentClient.agent(uniqueName("RECREATE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("RECREATE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String name = uniqueName("RECREATE_TEAM");
        Team team = createTeam(name, agent.getAgentName(), task.getTaskName());
        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(() -> agentClient.team(name)).isInstanceOf(Exception.class);
        assertThat(team.create()).isTrue();
        managedTeams.add(team);
        assertThat(agentClient.team(name).getTeamName()).isEqualTo(name);
    }

    /**
     * Test: Drop an existing team with force disabled and look it up afterward.
     * Expected: Drop returns true, and lookup throws SelectAIException with the expected not-found
     * message.
     */
    @Test
    void test33026DropExistingTeamWithoutForce() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_NON_FORCE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DROP_NON_FORCE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String name = uniqueName("DROP_NON_FORCE_TEAM");
        Team team = createTeam(name, agent.getAgentName(), task.getTaskName());
        assertThat(team.drop(false)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(() -> agentClient.team(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI team not found: " + name);
    }

    /**
     * Bug <a href="https://bug.oraclecorp.com/ords/bug/bugui/bug-details?mode=edit&bugnumber=40126447">
     * 40126447</a> - DBMS_CLOUD_AI_AGENT Java drop APIs must delegate repeated forced drops
     * to the database, where force-drop is idempotent.
     */
    /**
     * Test: Force-drop a team and repeat the forced drop through the same handle.
     * Expected: Both calls return true.
     */
    @Test
    void test33027RepeatedForceDropOnSameTeamHandleSucceeds() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_REPEAT_FORCE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DROP_REPEAT_FORCE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("DROP_REPEAT_FORCE_TEAM"),
                agent.getAgentName(), task.getTaskName());
        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        assertThat(team.drop(true)).isTrue();
    }

    /**
     * Test: Load two handles, force-drop through the first, then force-drop through the second.
     * Expected: Both calls return true even though the second handle refers to an already dropped
     * team.
     */
    @Test
    void test33028ForceDropAlreadyMissingTeamSucceeds() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_MISSING_FORCE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DROP_MISSING_FORCE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String name = uniqueName("DROP_MISSING_FORCE_TEAM");
        Team first = createTeam(name, agent.getAgentName(), task.getTaskName());
        Team second = agentClient.team(name);

        assertThat(first.drop(true)).isTrue();
        managedTeams.remove(first);
        assertThat(second.drop(true)).isTrue();
    }

    /**
     * Test: Load two handles, force-drop through the first, then drop through the second without
     * force.
     * Expected: The second call throws SelectAIException whose SQL cause contains an ORA error, the
     * team name, and "does not exist".
     */
    @Test
    void test33029NonForceDropMissingTeamPreservesDatabaseError() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_MISSING_NON_FORCE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DROP_MISSING_NON_FORCE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String name = uniqueName("DROP_MISSING_NON_FORCE_TEAM");
        Team first = createTeam(name, agent.getAgentName(), task.getTaskName());
        Team second = agentClient.team(name);

        assertThat(first.drop(true)).isTrue();
        managedTeams.remove(first);
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
     * Test: List a team by exact name, drop the returned handle, and list by that name again.
     * Expected: The listed handle drops successfully and the follow-up result is empty.
     */
    @Test
    void test33063DropTeamLoadedFromList() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_LISTED_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DROP_LISTED_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team source = createTeam(uniqueName("DROP_LISTED_TEAM"),
                agent.getAgentName(), task.getTaskName());
        String name = source.getTeamName();

        List<Team> listedTeams = agentClient.listTeams("^" + name + "$");
        assertThat(listedTeams).hasSize(1);
        assertThat(listedTeams.get(0).drop(true)).isTrue();
        managedTeams.remove(source);

        assertThat(listTeamNames("^" + name + "$")).isEmpty();
    }

    /**
     * Test: Load a second handle, drop the team through the first, then read status and attributes
     * from the stale handle.
     * Expected: Both reads throw SelectAIException with the expected team-not-found message.
     */
    @Test
    void test33064StaleTeamStatusAndAttributesFailAfterExternalDrop() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STALE_DROP_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("STALE_DROP_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team source = createTeam(uniqueName("STALE_DROP_TEAM"),
                agent.getAgentName(), task.getTaskName());
        String name = source.getTeamName();
        Team stale = agentClient.team(name);

        assertThat(source.drop(true)).isTrue();
        managedTeams.remove(source);

        assertThatThrownBy(stale::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI team not found: " + name);
        assertThatThrownBy(stale::getTeamAttributes)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI team not found: " + name);
    }

    /**
     * Test: Call drop on never-created team handles with force enabled and disabled.
     * Expected: Forced drop returns true; non-forced drop throws IllegalStateException.
     */
    @Test
    void test33065NeverCreatedTeamForceDropDelegatesToDatabase() throws Exception {
        TeamAttributes attributes = TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(uniqueName("UNCREATED_TEAM_AGENT"))
                        .task(uniqueName("UNCREATED_TEAM_TASK")).build()))
                .process("sequential")
                .build();
        Team forceHandle = agentClient.team(uniqueName("UNCREATED_FORCE_DROP"),
                attributes, null, TeamStatus.ENABLED);
        assertThat(forceHandle.drop(true)).isTrue();

        Team nonForceHandle = agentClient.team(uniqueName("UNCREATED_NON_FORCE_DROP"),
                attributes, null, TeamStatus.ENABLED);
        assertThatThrownBy(() -> nonForceHandle.drop(false))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test: Drop a team without force, then repeat the operation through the same handle.
     * Expected: The first drop returns true, the second throws IllegalStateException, and listing
     * confirms the team is absent.
     */
    @Test
    void test33066RepeatedNonForceDropOnSameTeamHandleIsRejectedLocally() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_REPEAT_NON_FORCE_TEAM_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("DROP_REPEAT_NON_FORCE_TEAM_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("DROP_REPEAT_NON_FORCE_TEAM"),
                agent.getAgentName(), task.getTaskName());
        String name = team.getTeamName();

        assertThat(team.drop(false)).isTrue();
        managedTeams.remove(team);
        assertThatThrownBy(() -> team.drop(false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(listTeamNames("^" + name + "$")).isEmpty();
    }

    private List<String> createTeamListFixture() throws Exception {
        Agent agent = agentClient.agent(uniqueName("LIST_PATTERN_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("LIST_PATTERN_TASK"), taskAttributes(), "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        String listPrefix = uniqueName("LIST_PATTERN");
        String otherPrefix = uniqueName("LIST_OTHER");
        return List.of(
                createTeam(listPrefix + "_1", agent.getAgentName(), task.getTaskName()).getTeamName(),
                createTeam(listPrefix + "_2", agent.getAgentName(), task.getTaskName()).getTeamName(),
                createTeam(listPrefix + "_3", agent.getAgentName(), task.getTaskName()).getTeamName(),
                createTeam(listPrefix + "_4", agent.getAgentName(), task.getTaskName()).getTeamName(),
                createTeam(listPrefix + "_5", agent.getAgentName(), task.getTaskName()).getTeamName(),
                createTeam(otherPrefix + "_1", agent.getAgentName(), task.getTaskName()).getTeamName());
    }

    private List<String> listTeamNames(String pattern) throws Exception {
        return agentClient.listTeams(pattern).stream().map(Team::getTeamName).toList();
    }

    private String listPrefix(String name) {
        return name.substring(0, name.lastIndexOf('_'));
    }

    private JsonNode skillsJson(TeamDescription description) throws Exception {
        assertThat(description.getSkills()).isNotBlank();
        JsonNode skills = new ObjectMapper().readTree(description.getSkills());
        assertThat(skills.isArray()).isTrue();
        return skills;
    }

    private void assertDocumentedTeamMetadata(TeamDescription description, Team team,
                                              String expectedDescription) {
        assertThat(description.getVersion()).isEqualTo("1.0");
        assertThat(description.getTeamName()).isEqualTo(team.getTeamName());
        if (expectedDescription == null) {
            assertThat(description.getDescription()).isNull();
        } else {
            assertThat(description.getDescription()).isEqualTo(expectedDescription);
        }
    }

    private List<String> skillNames(JsonNode skills) {
        List<String> names = new ArrayList<>();
        for (JsonNode skill : skills) {
            names.add(skill.path("tool_name").asText());
        }
        return names;
    }

    private static ExecutionStateRequest executionState(TeamExecutionState state,
                                                        String conversationId) {
        return ExecutionStateRequest.builder(state)
                .params(ExecutionStateParams.forConversationId(conversationId))
                .build();
    }

    private void assertTeamCreationRejected(Team team) {
        assertThatThrownBy(team::create)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).isInstanceOf(java.sql.SQLException.class)
                                    .hasMessageContaining("ORA-");
                        });
    }
}
