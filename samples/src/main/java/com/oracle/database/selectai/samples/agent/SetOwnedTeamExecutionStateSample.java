/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.ExecutionStateRequest;
import com.oracle.database.selectai.model.agent.TeamExecutionState;

/** Updates an owner-scoped team's execution state. */
public final class SetOwnedTeamExecutionStateSample {

    private SetOwnedTeamExecutionStateSample() {
    }

    /**
     * Loads the named team and updates the requested conversation state.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = AgentLifecycleSampleSupport.requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        String conversationId = AgentLifecycleSampleSupport.requiredEnv(
                "SELECTAI_AGENT_TEAM_CONVERSATION_ID");
        TeamExecutionState state = TeamExecutionState.of(
                envOrDefault("SELECTAI_AGENT_TEAM_EXECUTION_STATE", "SUCCEEDED"));
        try (SelectAIAgent client = AgentLifecycleSampleSupport.createClient()) {
            Team team = client.team(teamName);
            boolean updated = team.setExecutionState(ExecutionStateRequest.builder(state)
                    .conversationId(conversationId)
                    .build());
            System.out.printf("Updated owned team %s execution state for conversation %s: %s%n",
                    teamName, conversationId, updated);
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
