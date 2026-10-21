/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.model.SelectAIException;

/** Enables an existing Select AI task owned by the connected database user. */
public final class EnableTaskSample {

    private EnableTaskSample() {
    }

    /**
     * Loads and enables the named task.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String taskName = AgentLifecycleSampleSupport.requiredEnv("SELECTAI_AGENT_TASK_NAME");
        try (SelectAIAgent client = AgentLifecycleSampleSupport.createClient()) {
            Task task = client.task(taskName);
            System.out.printf("Enable task '%s' completed: %s%n", taskName, task.enable());
        }
    }
}
