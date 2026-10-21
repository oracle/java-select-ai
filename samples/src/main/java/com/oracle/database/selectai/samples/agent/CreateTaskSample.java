/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

import java.util.Arrays;
import java.util.List;

/** Sample for creating a DBMS_CLOUD_AI_AGENT task. */
public final class CreateTaskSample {

    private CreateTaskSample() {
    }

    /**
     * Runs the sample and propagates failures to the command-line caller.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when an SDK operation fails
     */
    public static void main(String[] args) throws SelectAIException {
        String taskName = requiredEnv("SELECTAI_AGENT_TASK_NAME");
        try (SelectAIAgent selectAIAgent = createSelectAIAgent()) {
            TaskAttributes attributes = TaskAttributes.builder()
                    .instruction(requiredEnv("SELECTAI_AGENT_TASK_INSTRUCTION"))
                    .tools(optionalList("SELECTAI_AGENT_TASK_TOOLS"))
                    .input(optionalList("SELECTAI_AGENT_TASK_INPUT"))
                    .enableHumanTool(optionalBoolean("SELECTAI_AGENT_TASK_ENABLE_HUMAN_TOOL"))
                    .build();
            Task task = selectAIAgent.task(taskName, attributes,
                    env("SELECTAI_AGENT_TASK_DESCRIPTION"), status("SELECTAI_AGENT_TASK_STATUS"));

            boolean created = task.create();
            System.out.printf("Create task '%s' completed: %s%n", task.getTaskName(), created);
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

    private static List<String> optionalList(String name) {
        String value = env(name);
        return value == null ? null : Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static Boolean optionalBoolean(String name) {
        String value = env(name);
        return value == null ? null : Boolean.valueOf(value);
    }

    private static TaskStatus status(String name) {
        String value = env(name);
        return value == null ? null : TaskStatus.fromValue(value);
    }
}
