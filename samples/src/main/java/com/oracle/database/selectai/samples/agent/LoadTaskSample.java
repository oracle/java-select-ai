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

/**
 * Loads an existing agent task and reads its current metadata and attributes.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD},
 * {@code SELECTAI_JDBC_URL}, and {@code SELECTAI_AGENT_TASK_NAME}. The named
 * task must already exist and be visible to the connected user. See
 * {@code samples/README.md} for build and run commands.</p>
 */
public final class LoadTaskSample {

    private LoadTaskSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String taskName = requiredEnv("SELECTAI_AGENT_TASK_NAME");
        try (SelectAIAgent client = createClient()) {
            Task task = client.task(taskName);
            System.out.printf("Task: %s%n", task.getTaskName());
            System.out.printf("Status: %s%n", task.getStatus());
            System.out.printf("Description: %s%n", task.getDescription());
            TaskAttributes attributes = task.getTaskAttributes();
            System.out.printf("Human tool enabled: %s%n", attributes.getEnableHumanTool());
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
