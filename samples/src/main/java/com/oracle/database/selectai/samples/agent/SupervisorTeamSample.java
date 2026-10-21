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
 * Creates and runs a supervisor-led team with two worker agent-task pairs.
 *
 * <p>{@link AgentAttributes.Builder#supervisor(Boolean) supervisor(true)}
 * designates an agent as a supervisor at creation time. The team assigns that
 * same agent through {@link TeamAttributes.Builder#supervisorAgent(String)}.
 * Its {@code agents} list contains only workers, not the supervisor. With
 * {@code process("sequential")}, the supervisor delegates one worker subtask
 * at a time. Worker-specific instructions belong to the worker tasks.</p>
 *
 * <p>The sample creates one SQL tool, two tasks, two worker agents, a supervisor
 * agent, a team, and a database conversation. The default prompt supplies
 * synthetic revenue and expense figures; no sales tables or SH schema are
 * required. The profile must already exist, support the SQL tool, and be
 * accessible to the configured database user. The database must support
 * supervisor-led teams. Execution can invoke an external model and incur
 * provider charges. Generated responses are printed for demonstration only.</p>
 *
 * <p>Every successfully created resource is dropped in reverse dependency
 * order, including after a failure. Existing profiles and credentials are not
 * modified or deleted. Cleanup failures are propagated, or suppressed on an
 * existing execution failure, so failed execution or cleanup produces a
 * nonzero process exit status.</p>
 *
 * <p>Required environment variables: {@code SELECTAI_DB_USER},
 * {@code SELECTAI_DB_PASSWORD}, {@code SELECTAI_JDBC_URL}, and
 * {@code SELECTAI_PROFILE_NAME}.</p>
 *
 * <p>Optional variables: {@code SELECTAI_AGENT_SUPERVISOR_PREFIX} overrides the
 * unique resource-name prefix; {@code SELECTAI_AGENT_SUPERVISOR_PROMPT}
 * overrides the synthetic analysis request. Use a prefix that does not name
 * existing resources. The supervisor flag cannot be changed after creation.</p>
 *
 * <p>Build and run from the repository root:</p>
 * <pre>{@code
 * mvn clean install
 * mvn -Psamples -DincludeScope=runtime \
 *     -DoutputDirectory=target/dependency dependency:copy-dependencies
 * javac --release 17 -cp "target/select-ai-1.1.0.jar:target/dependency/*" \
 *     -d samples/out \
 *     samples/src/main/java/com/oracle/database/selectai/samples/agent/SupervisorTeamSample.java
 * java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
 *     com.oracle.database.selectai.samples.agent.SupervisorTeamSample
 * }</pre>
 *
 * @see CreateAgentSample
 * @see CreateTeamSample
 * @see AgentWorkflowSample
 */
public final class SupervisorTeamSample {
    private SupervisorTeamSample() {
    }

    /**
     * Creates the supervised workflow, runs it, and cleans up its resources.
     *
     * @param args command-line arguments; not used
     * @throws SelectAIException when execution, cleanup, or client closure fails
     */
    public static void main(String[] args) throws SelectAIException {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String prefix = envOrDefault("SELECTAI_AGENT_SUPERVISOR_PREFIX", generatedPrefix());
        String prompt = envOrDefault("SELECTAI_AGENT_SUPERVISOR_PROMPT",
                "Using these synthetic figures, calculate the quarter-over-quarter revenue change "
                        + "and the change in profit margin. Q1 revenue is 100000 and expenses are 60000; "
                        + "Q2 revenue is 80000 and expenses are 60000. Summarize what we should investigate.");
        DbConnectionConfig config = DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();

        try (SelectAIAgent agentClient = SelectAIAgent.create(config);
             SelectAI selectAI = SelectAI.create(config)) {
            run(agentClient, selectAI, profileName, prefix, prompt);
        }
    }

    /** Runs with caller-owned clients; cleanup covers only resources created here. */
    static void run(SelectAIAgent agentClient, SelectAI selectAI, String profileName,
                    String prefix, String prompt) throws SelectAIException {
        String toolName = prefix + "_SQL";
        String revenueTaskName = prefix + "_REV_TASK";
        String marginTaskName = prefix + "_MARGIN_TASK";
        String revenueAgentName = prefix + "_REV_AGENT";
        String marginAgentName = prefix + "_MARGIN_AGENT";
        String supervisorName = prefix + "_SUPERVISOR";
        String teamName = prefix + "_TEAM";
        System.out.printf("Supervisor team: %s; supervisor agent: %s%n", teamName, supervisorName);

        try (Cleanup cleanup = new Cleanup()) {
            Tool tool = agentClient.tool(toolName,
                    ToolAttributes.builder()
                            .toolType(ToolType.SQL)
                            .toolParams(SqlToolParams.builder().profileName(profileName).build())
                            .build(),
                    "SQL calculation tool created by SupervisorTeamSample.", ToolStatus.ENABLED);
            if (tool.create()) {
                cleanup.add("tool", toolName, () -> tool.drop(false));
            }

            Task revenueTask = agentClient.task(revenueTaskName,
                    TaskAttributes.builder()
                            .instruction("Calculate the quarter-over-quarter revenue change and percentage "
                                    + "from the supplied figures: {query}. Use the SQL tool with RUNSQL "
                                    + "and arithmetic SELECT expressions over DUAL; do not query sales tables.")
                            .tools(List.of(toolName))
                            .enableHumanTool(false)
                            .build(),
                    "Revenue analysis task created by SupervisorTeamSample.", TaskStatus.ENABLED);
            if (revenueTask.create()) {
                cleanup.add("task", revenueTaskName, () -> revenueTask.drop(false));
            }

            Task marginTask = agentClient.task(marginTaskName,
                    TaskAttributes.builder()
                            .instruction("Calculate profit and profit margin for each quarter from the supplied "
                                    + "figures: {query}. Explain the margin change. Use the SQL tool with RUNSQL "
                                    + "and arithmetic SELECT expressions over DUAL; do not query sales tables.")
                            .tools(List.of(toolName))
                            .enableHumanTool(false)
                            .build(),
                    "Margin analysis task created by SupervisorTeamSample.", TaskStatus.ENABLED);
            if (marginTask.create()) {
                cleanup.add("task", marginTaskName, () -> marginTask.drop(false));
            }

            Agent revenueAgent = agentClient.agent(revenueAgentName,
                    AgentAttributes.builder()
                            .profileName(profileName)
                            .role("Analyze revenue changes using only the figures supplied in the request.")
                            .supervisor(false)
                            .enableHumanTool(false)
                            .build(),
                    "Revenue worker created by SupervisorTeamSample.", AgentStatus.ENABLED);
            if (revenueAgent.create()) {
                cleanup.add("agent", revenueAgentName, () -> revenueAgent.drop(false));
            }

            Agent marginAgent = agentClient.agent(marginAgentName,
                    AgentAttributes.builder()
                            .profileName(profileName)
                            .role("Analyze profit margins using only the figures supplied in the request.")
                            .supervisor(false)
                            .enableHumanTool(false)
                            .build(),
                    "Margin worker created by SupervisorTeamSample.", AgentStatus.ENABLED);
            if (marginAgent.create()) {
                cleanup.add("agent", marginAgentName, () -> marginAgent.drop(false));
            }

            // This flag designates the agent as a supervisor; it does not assign a team.
            Agent supervisor = agentClient.agent(supervisorName,
                    AgentAttributes.builder()
                            .profileName(profileName)
                            .role("Delegate revenue analysis to " + revenueAgentName
                                    + " and margin analysis to " + marginAgentName
                                    + ". Pass the supplied figures to each worker. Combine their results "
                                    + "and suggest follow-up questions without inventing business facts.")
                            .supervisor(true)
                            .enableHumanTool(false)
                            .build(),
                    "Supervisor created by SupervisorTeamSample.", AgentStatus.ENABLED);
            if (supervisor.create()) {
                cleanup.add("agent", supervisorName, () -> supervisor.drop(false));
            }

            // Assign the supervisor separately; only worker-task pairs belong in agents.
            Team team = agentClient.team(teamName,
                    TeamAttributes.builder()
                            .supervisorAgent(supervisor.getAgentName())
                            .process("sequential")
                            .agents(List.of(
                                    TeamMember.builder(revenueAgentName).task(revenueTaskName).build(),
                                    TeamMember.builder(marginAgentName).task(marginTaskName).build()))
                            .build(),
                    "Supervisor-led team created by SupervisorTeamSample.", TeamStatus.ENABLED);
            if (team.create()) {
                cleanup.add("team", teamName, () -> team.drop(false));
            }

            Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                    .title("SupervisorTeamSample conversation")
                    .build());
            String conversationId = conversation.create();
            cleanup.add("conversation", conversationId, () -> conversation.drop(false));

            String response = team.run(RunTeamRequest.builder()
                    .userPrompt(prompt)
                    .conversationId(conversationId)
                    .build()).getRawValue();
            System.out.printf("Supervisor team response: %s%n", response);
        }
        System.out.println("Supervisor workflow cleanup completed.");
    }

    /** Attempts every registered drop and preserves failures for the caller. */
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
                                    + "' created by SupervisorTeamSample", e);
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

    /** One resource created successfully by this sample. */
    private record CleanupAction(String resourceType, String name, DropOperation operation) { }

    /** Drop operation registered only after successful creation. */
    @FunctionalInterface
    private interface DropOperation {
        void drop() throws SelectAIException;
    }

    private static String generatedPrefix() {
        return "JSAISUP_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
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
