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

/** Disables an existing Select AI team owned by the connected database user. */
public final class DisableTeamSample {

    private DisableTeamSample() {
    }

    /**
     * Loads and disables the named team.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String teamName = AgentLifecycleSampleSupport.requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        try (SelectAIAgent client = AgentLifecycleSampleSupport.createClient()) {
            Team team = client.team(teamName);
            System.out.printf("Disable team '%s' completed: %s%n", teamName, team.disable());
        }
    }
}
