/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

import java.util.Arrays;
import java.util.List;

/**
 * Sample for creating a {@code DBMS_CLOUD_AI_AGENT} team.
 *
 * <p>Required environment variables are {@code SELECTAI_DB_USER},
 * {@code SELECTAI_DB_PASSWORD}, {@code SELECTAI_JDBC_URL},
 * {@code SELECTAI_AGENT_TEAM_NAME}, {@code SELECTAI_AGENT_TEAM_AGENTS}, and
 * {@code SELECTAI_AGENT_TEAM_PROCESS}. The agents variable is a comma-separated
 * list of {@code agentName:taskName} pairs, for example
 * {@code SUPPORT_AGENT:SUPPORT_TASK,BILLING_AGENT:BILLING_TASK}.</p>
 *
 * <p>{@code SELECTAI_AGENT_TEAM_LONG_TERM_MEMORY_LENGTH},
 * {@code SELECTAI_AGENT_TEAM_SUPERVISOR_AGENT},
 * {@code SELECTAI_AGENT_TEAM_DESCRIPTION}, and
 * {@code SELECTAI_AGENT_TEAM_STATUS} are optional. The database default for
 * long-term memory length is 30.</p>
 */
public final class CreateTeamSample {

    private CreateTeamSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            TeamAttributes attributes = TeamAttributes.builder()
                    .agents(parseAgents(requiredEnv("SELECTAI_AGENT_TEAM_AGENTS")))
                    .process(requiredEnv("SELECTAI_AGENT_TEAM_PROCESS"))
                    .longTermMemoryLength(
                            optionalInteger("SELECTAI_AGENT_TEAM_LONG_TERM_MEMORY_LENGTH"))
                    .supervisorAgent(env("SELECTAI_AGENT_TEAM_SUPERVISOR_AGENT"))
                    .build();
            Team team = selectAIAgent.team(teamName, attributes,
                    env("SELECTAI_AGENT_TEAM_DESCRIPTION"), status("SELECTAI_AGENT_TEAM_STATUS"));

            boolean created = team.create();
            System.out.printf("Create team '%s' completed: %s%n", team.getTeamName(), created);
        }
    }

    private static SelectAIAgent createSelectAIAgent() throws SelectAIException {
        return SelectAIAgent.create(DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static List<TeamMember> parseAgents(String value) {
        List<TeamMember> agents = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(CreateTeamSample::parseAgent)
                .toList();
        if (agents.isEmpty()) {
            throw new IllegalStateException(
                    "SELECTAI_AGENT_TEAM_AGENTS must contain at least one agent:task pair");
        }
        return agents;
    }

    private static TeamMember parseAgent(String value) {
        String[] parts = value.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalStateException(
                    "Each SELECTAI_AGENT_TEAM_AGENTS entry must use agentName:taskName format");
        }
        return TeamMember.builder(parts[0].trim()).task(parts[1].trim()).build();
    }

    private static Integer optionalInteger(String name) {
        String value = env(name);
        return value == null ? null : Integer.valueOf(value);
    }

    private static TeamStatus status(String name) {
        String value = env(name);
        return value == null ? null : TeamStatus.fromValue(value);
    }
}
