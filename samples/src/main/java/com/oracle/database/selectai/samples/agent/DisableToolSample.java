/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;

/** Disables an existing Select AI tool owned by the connected database user. */
public final class DisableToolSample {

    private DisableToolSample() {
    }

    /**
     * Loads and disables the named tool.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String toolName = AgentLifecycleSampleSupport.requiredEnv("SELECTAI_AGENT_TOOL_NAME");
        try (SelectAIAgent client = AgentLifecycleSampleSupport.createClient()) {
            Tool tool = client.tool(toolName);
            System.out.printf("Disable tool '%s' completed: %s%n", toolName, tool.disable());
        }
    }
}
