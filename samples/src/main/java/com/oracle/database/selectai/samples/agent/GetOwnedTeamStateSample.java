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
import com.oracle.database.selectai.model.agent.TeamExecutionState;

/** Reads an owner-scoped team's state using a database conversation ID. */
public final class GetOwnedTeamStateSample {

    private GetOwnedTeamStateSample() {
    }

    /**
     * Loads the named team and prints the state for a database conversation.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = AgentLifecycleSampleSupport.requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        String conversationId = AgentLifecycleSampleSupport.requiredEnv(
                "SELECTAI_AGENT_TEAM_CONVERSATION_ID");
        try (SelectAIAgent client = AgentLifecycleSampleSupport.createClient()) {
            Team team = client.team(teamName);
            TeamExecutionState state = team.getStateByConversationId(conversationId);
            System.out.printf("Owned team %s state for conversation %s: %s%n",
                    teamName, conversationId, state);
        }
    }

}
