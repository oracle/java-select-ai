/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.SelectAIException;

/** Enables an existing Select AI agent owned by the connected database user. */
public final class EnableAgentSample {

    private EnableAgentSample() {
    }

    /**
     * Loads and enables the named agent.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String agentName = AgentLifecycleSampleSupport.requiredEnv("SELECTAI_AGENT_NAME");
        try (SelectAIAgent client = AgentLifecycleSampleSupport.createClient()) {
            Agent agent = client.agent(agentName);
            System.out.printf("Enable agent '%s' completed: %s%n", agentName, agent.enable());
        }
    }
}
