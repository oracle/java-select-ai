/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.TaskAttributes;

/** Sample for updating multiple DBMS_CLOUD_AI_AGENT task attributes. */
public final class SetTaskAttributesSample {

    private SetTaskAttributesSample() {
    }

    /**
     * Runs the sample using the configured task instruction.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails; the process exits unsuccessfully
     */
    public static void main(String[] args) throws SelectAIException {
        String taskName = requiredEnv("SELECTAI_AGENT_TASK_NAME");
        TaskAttributes attributes = TaskAttributes.builder()
                .instruction(requiredEnv("SELECTAI_AGENT_TASK_INSTRUCTION"))
                .build();

        try (SelectAIAgent selectAIAgent = createClient()) {
            Task task = selectAIAgent.task(taskName);
            boolean updated = task.setAttributes(attributes);
            System.out.printf("Set attributes on task '%s' completed: %s%n", taskName, updated);
        }
    }

    private static SelectAIAgent createClient() throws SelectAIException {
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
}
