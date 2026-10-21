/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * End-to-end Select AI Agent workflow sample.
 *
 * <p>The sample creates a SQL tool, task, agent, and team in that order. It
 * then creates a database conversation, runs the team, and sends a second
 * prompt using the same conversation ID. Finally, it drops the resources it
 * created in reverse dependency order. Every cleanup is attempted even when
 * another cleanup fails. Execution and cleanup failures propagate to the
 * command-line caller, producing a nonzero exit status; cleanup errors are
 * suppressed on an existing execution failure.</p>
 *
 * <p>The profile named by {@code SELECTAI_PROFILE_NAME} must already exist and
 * be accessible to the configured database user. The sample creates persistent
 * database resources while it runs and removes them during cleanup.</p>
 *
 * <p>This sample demonstrates conversation continuation, not a guaranteed
 * human-input pause. For a workflow that verifies {@code WAITING_FOR_HUMAN}
 * and resumes through a new connection pool, see {@link PooledTeamResumeSample}.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>{@code SELECTAI_DB_USER}</li>
 *   <li>{@code SELECTAI_DB_PASSWORD}</li>
 *   <li>{@code SELECTAI_JDBC_URL}</li>
 *   <li>{@code SELECTAI_PROFILE_NAME}</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>{@code SELECTAI_AGENT_WORKFLOW_PREFIX} - prefix for generated resource
 *       names; a unique prefix is generated when omitted.</li>
 *   <li>{@code SELECTAI_AGENT_WORKFLOW_TOOL_NAME}</li>
 *   <li>{@code SELECTAI_AGENT_WORKFLOW_TASK_NAME}</li>
 *   <li>{@code SELECTAI_AGENT_WORKFLOW_AGENT_NAME}</li>
 *   <li>{@code SELECTAI_AGENT_WORKFLOW_TEAM_NAME}</li>
 *   <li>{@code SELECTAI_AGENT_WORKFLOW_FIRST_PROMPT} - defaults to
 *       {@code What is 2 + 2?}.</li>
 *   <li>{@code SELECTAI_AGENT_WORKFLOW_CONTINUATION_PROMPT} - defaults to
 *       {@code Explain that result in one sentence.}.</li>
 * </ul>
 *
 * <p>Build and run from the repository root:</p>
 * <pre>{@code
 * mvn clean install
 * mvn -Psamples -DincludeScope=runtime \
 *     -DoutputDirectory=target/dependency dependency:copy-dependencies
 * javac --release 17 -cp "target/select-ai-1.1.0.jar:target/dependency/*" \
 *     -d samples/out \
 *     samples/src/main/java/com/oracle/database/selectai/samples/agent/AgentWorkflowSample.java
 * java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
 *     com.oracle.database.selectai.samples.agent.AgentWorkflowSample
 * }</pre>
 *
 * @see CreateToolSample
 * @see CreateTaskSample
 * @see CreateAgentSample
 * @see CreateTeamSample
 * @see RunTeamSample
 * @see com.oracle.database.selectai.Conversation#create()
 */
public final class AgentWorkflowSample {
    private AgentWorkflowSample() {
    }

    /**
     * Creates the resources, runs the team twice, and removes the resources.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when execution, resource cleanup, or client closure fails
     */
    public static void main(String[] args) throws SelectAIException {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String prefix = envOrDefault("SELECTAI_AGENT_WORKFLOW_PREFIX", generatedPrefix());
        String toolName = envOrDefault("SELECTAI_AGENT_WORKFLOW_TOOL_NAME", prefix + "_TOL");
        String taskName = envOrDefault("SELECTAI_AGENT_WORKFLOW_TASK_NAME", prefix + "_TSK");
        String agentName = envOrDefault("SELECTAI_AGENT_WORKFLOW_AGENT_NAME", prefix + "_AGT");
        String teamName = envOrDefault("SELECTAI_AGENT_WORKFLOW_TEAM_NAME", prefix + "_TEM");

        try (SelectAIAgent agentClient = createAgentClient();
             SelectAI selectAI = createSelectAI()) {
            run(agentClient, selectAI, profileName, toolName, taskName, agentName, teamName,
                    envOrDefault("SELECTAI_AGENT_WORKFLOW_FIRST_PROMPT", "What is 2 + 2?"),
                    envOrDefault("SELECTAI_AGENT_WORKFLOW_CONTINUATION_PROMPT",
                            "Explain that result in one sentence."));
        }
    }

    /** Runs with caller-owned clients; only resources created here are cleaned up. */
    static void run(SelectAIAgent agentClient, SelectAI selectAI, String profileName,
                   String toolName, String taskName, String agentName, String teamName,
                   String firstPrompt, String continuationPrompt) throws SelectAIException {
        try (Cleanup cleanup = new Cleanup()) {
            Tool tool = agentClient.tool(toolName,
                    ToolAttributes.builder()
                            .toolType(ToolType.SQL)
                            .toolParams(SqlToolParams.builder().profileName(profileName).build())
                            .build(),
                    "SQL tool created by AgentWorkflowSample.",
                    ToolStatus.ENABLED);
            if (tool.create()) {
                cleanup.add("tool", toolName, () -> tool.drop(false));
            }

            Task task = agentClient.task(taskName,
                    TaskAttributes.builder()
                            .instruction("Answer the user's question using the SQL tool: {query}")
                            .tools(List.of(toolName))
                            .build(),
                    "Task created by AgentWorkflowSample.",
                    TaskStatus.ENABLED);
            if (task.create()) {
                cleanup.add("task", taskName, () -> task.drop(false));
            }

            Agent agent = agentClient.agent(agentName,
                    AgentAttributes.builder()
                            .profileName(profileName)
                            .role("Answer the user's question clearly and accurately.")
                            .tools(List.of(toolName))
                            .build(),
                    "Agent created by AgentWorkflowSample.",
                    AgentStatus.ENABLED);
            if (agent.create()) {
                cleanup.add("agent", agentName, () -> agent.drop(false));
            }

            Team team = agentClient.team(teamName,
                    TeamAttributes.builder()
                            .agents(List.of(TeamMember.builder(agentName).task(taskName).build()))
                            .process("sequential")
                            .build(),
                    "Team created by AgentWorkflowSample.",
                    TeamStatus.ENABLED);
            if (team.create()) {
                cleanup.add("team", teamName, () -> team.drop(false));
            }

            Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                    .title("AgentWorkflowSample conversation")
                    .build());
            String conversationId = conversation.create();
            cleanup.add("conversation", conversationId, () -> conversation.drop(false));

            RunTeamRequest.Builder request = RunTeamRequest.builder()
                    .userPrompt(firstPrompt)
                    .conversationId(conversationId);
            TeamRunResult firstResult = team.run(request.build());
            System.out.printf("First team response: %s%n", firstResult.getRawValue());

            // Reuse the builder and conversation without changing the first request.
            TeamRunResult continuationResult = team.run(request.userPrompt(continuationPrompt).build());
            System.out.printf("Continued team response: %s%n", continuationResult.getRawValue());
        }
        System.out.println("Workflow cleanup completed.");
    }

    private static SelectAIAgent createAgentClient() throws SelectAIException {
        return SelectAIAgent.create(connectionConfig());
    }

    private static SelectAI createSelectAI() throws SelectAIException {
        return SelectAI.create(connectionConfig());
    }

    private static DbConnectionConfig connectionConfig() {
        return DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();
    }

    /** Aggregates cleanup failures while preserving try-with-resources suppression. */
    private static final class Cleanup implements AutoCloseable {
        /** Successfully created resources, most recent first. */
        private final Deque<CleanupAction> actions = new ArrayDeque<>();

        private void add(String resourceType, String name, DropOperation operation) {
            actions.push(new CleanupAction(resourceType, name, operation));
        }

        @Override
        public void close() throws SelectAIException {
            SelectAIException failure = null;
            while (!actions.isEmpty()) {
                CleanupAction action = actions.pop();
                try {
                    action.operation().drop();
                } catch (Exception e) {
                    SelectAIException cleanupFailure = new SelectAIException(
                            "Failed to drop " + action.resourceType() + " '" + action.name()
                                    + "' created by AgentWorkflowSample", e);
                    if (failure == null) {
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** One resource owned by this sample. */
    private record CleanupAction(String resourceType, String name, DropOperation operation) { }

    /** Drop operation registered only after successful creation. */
    @FunctionalInterface
    private interface DropOperation {
        void drop() throws SelectAIException;
    }

    private static String generatedPrefix() {
        return "JSAIWF_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
