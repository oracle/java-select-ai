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

import java.util.List;

/**
 * Lists agent tasks owned by the connected database user.
 *
 * <p>Set {@code SELECTAI_DB_USER}, {@code SELECTAI_DB_PASSWORD}, and
 * {@code SELECTAI_JDBC_URL} before running this sample. The database user must
 * have access to Select AI Agent metadata. Optionally set
 * {@code SELECTAI_AGENT_TASK_NAME_PATTERN} to a database regular expression
 * for case-insensitive filtering. See {@code samples/README.md} for
 * build and run commands.</p>
 */
public final class ListTasksSample {

    private ListTasksSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        try (SelectAIAgent client = createClient()) {
            String pattern = optionalEnv("SELECTAI_AGENT_TASK_NAME_PATTERN");
            List<Task> tasks = pattern == null ? client.listTasks() : client.listTasks(pattern);
            System.out.printf("Found %d task(s).%n", tasks.size());
            for (Task task : tasks) {
                System.out.printf("Task: %s; description: %s%n",
                        task.getTaskName(), task.getDescription());
            }
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

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
