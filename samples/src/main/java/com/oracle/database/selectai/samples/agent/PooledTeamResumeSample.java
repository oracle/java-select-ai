/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import oracle.ucp.UniversalConnectionPoolAdapter;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Starts and resumes a human-input workflow in separate processes using UCP.
 *
 * <p>Use an existing team whose task enables the human tool and asks for
 * missing information or approval. The first prompt must cause the workflow
 * to pause: this sample verifies {@code WAITING_FOR_HUMAN}, rather than
 * treating a second ordinary conversation turn as a resume. It does not
 * create or change the team's definition, profile, or tools.</p>
 *
 * <p>Run {@code start}, preserve its printed conversation ID, then run
 * {@code resume} with that ID and the human's response. Each invocation owns
 * and destroys its own Oracle UCP pool, so resume cannot reuse the first
 * process's database session. Each SDK operation borrows and returns a
 * connection. No connection is held while the application waits for a person
 * between invocations.</p>
 *
 * <p>Required for both commands: {@code SELECTAI_DB_USER},
 * {@code SELECTAI_DB_PASSWORD}, {@code SELECTAI_JDBC_URL}, and
 * {@code SELECTAI_AGENT_TEAM_NAME} (optionally owner-qualified). For
 * {@code start}, also set {@code SELECTAI_AGENT_TEAM_PROMPT}. For
 * {@code resume}, set {@code SELECTAI_AGENT_TEAM_CONVERSATION_ID} and
 * {@code SELECTAI_AGENT_HUMAN_RESPONSE}. The same database identity and team
 * must be used for both commands. Optional {@code SELECTAI_QUERY_TIMEOUT_SECONDS}
 * configures the SDK statement timeout; a timeout does not guarantee that
 * database or provider work stopped. Failed calls are never retried here.</p>
 *
 * <p>The conversation is deliberately retained, including on failure, for
 * later resume or inspection. After the workflow is finished and no longer
 * needed, set {@code SELECTAI_CONVERSATION_ID} to the same ID and run
 * {@link com.oracle.database.selectai.samples.conversation.DropConversationSample}.
 * Do not drop a conversation while its workflow is running. In production,
 * store the team name and conversation ID in authorized application state,
 * and serialize human responses for the same workflow.</p>
 *
 * <p>Build the SDK and copy dependencies with the {@code samples} Maven
 * profile, which includes UCP. After compiling the samples, run:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
 *     com.oracle.database.selectai.samples.agent.PooledTeamResumeSample start
 * // Set SELECTAI_AGENT_TEAM_CONVERSATION_ID to the printed ID and
 * // SELECTAI_AGENT_HUMAN_RESPONSE to the human's answer, then:
 * java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
 *     com.oracle.database.selectai.samples.agent.PooledTeamResumeSample resume
 * }</pre>
 *
 * @see SelectAIAgent#runTeam(String, RunTeamRequest)
 * @see SelectAIAgent#getTeamStateByConversationId(String, String)
 */
public final class PooledTeamResumeSample {
    private PooledTeamResumeSample() {
    }

    /**
     * Runs one application request and closes its application-owned pool.
     *
     * @param args exactly one command: {@code start} or {@code resume}
     * @throws Exception when configuration, execution, or pool cleanup fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("start".equals(args[0]) || "resume".equals(args[0]))) {
            throw new IllegalArgumentException("Usage: PooledTeamResumeSample start|resume");
        }
        boolean starting = "start".equals(args[0]);
        String teamName = requiredEnv("SELECTAI_AGENT_TEAM_NAME");
        String input = requiredEnv(starting
                ? "SELECTAI_AGENT_TEAM_PROMPT" : "SELECTAI_AGENT_HUMAN_RESPONSE");
        String conversationId = starting ? null : requiredEnv("SELECTAI_AGENT_TEAM_CONVERSATION_ID");
        String timeout = System.getenv("SELECTAI_QUERY_TIMEOUT_SECONDS");
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(timeout == null || timeout.isBlank() ? null : Integer.valueOf(timeout))
                .build();

        PoolDataSource pool = createPool();
        UniversalConnectionPoolManager manager = UniversalConnectionPoolManagerImpl
                .getUniversalConnectionPoolManager();
        manager.createConnectionPool((UniversalConnectionPoolAdapter) pool);
        // The SDK closes borrowed connections, but the application owns the pool.
        try (AutoCloseable cleanup = () -> manager.destroyConnectionPool(pool.getConnectionPoolName())) {
            manager.startConnectionPool(pool.getConnectionPoolName());
            if (starting) {
                start(pool, options, teamName, input);
            } else {
                resume(pool, options, teamName, conversationId, input);
            }
        }
    }

    /** Creates a conversation and verifies that the first run really pauses. */
    static String start(DataSource dataSource, SelectAIOptions options, String teamName,
                        String prompt) throws SelectAIException {
        try (SelectAI selectAI = SelectAI.create(dataSource, options);
             SelectAIAgent agentClient = SelectAIAgent.create(dataSource, options)) {
            Conversation conversation = selectAI.conversation(ConversationAttributes.builder()
                    .title("Pooled human-input workflow")
                    .build());
            String conversationId = conversation.create();
            // Print before RUN_TEAM so a failed call still leaves a recovery ID.
            System.out.println("Conversation ID: " + conversationId);
            TeamRunResult result = agentClient.runTeam(teamName, RunTeamRequest.builder()
                    .userPrompt(prompt)
                    .conversationId(conversationId)
                    .build());
            System.out.println("Team response: " + (result == null ? "<no response>" : result.getRawValue()));
            requireWaiting(agentClient, teamName, conversationId);
            System.out.println("State: WAITING_FOR_HUMAN. Preserve the ID and run resume with the human response.");
            return conversationId;
        }
    }

    /** Resumes only a waiting run, using the ID from a previous application request. */
    static TeamExecutionState resume(DataSource dataSource, SelectAIOptions options, String teamName,
                                     String conversationId, String humanResponse) throws SelectAIException {
        try (SelectAIAgent agentClient = SelectAIAgent.create(dataSource, options)) {
            requireWaiting(agentClient, teamName, conversationId);
            TeamRunResult result = agentClient.runTeam(teamName, RunTeamRequest.builder()
                    .userPrompt(humanResponse)
                    .conversationId(conversationId)
                    .build());
            System.out.println("Resumed team response: " + (result == null ? "<no response>" : result.getRawValue()));
            TeamExecutionState state = agentClient.getTeamStateByConversationId(teamName, conversationId);
            System.out.println("State after resume: " + state);
            if (state != TeamExecutionState.SUCCEEDED && state != TeamExecutionState.WAITING_FOR_HUMAN) {
                throw new IllegalStateException("Unexpected state after resume: " + state
                        + ". Inspect the retained conversation; do not automatically retry RUN_TEAM.");
            }
            if (state == TeamExecutionState.WAITING_FOR_HUMAN) {
                System.out.println("More human input is required. Preserve the same conversation ID.");
            }
            return state;
        }
    }

    private static void requireWaiting(SelectAIAgent client, String teamName, String conversationId)
            throws SelectAIException {
        TeamExecutionState state = client.getTeamStateByConversationId(teamName, conversationId);
        if (state != TeamExecutionState.WAITING_FOR_HUMAN) {
            throw new IllegalStateException("Expected WAITING_FOR_HUMAN but found " + state
                    + ". This sample requires a workflow paused for human input.");
        }
    }

    private static PoolDataSource createPool() throws SQLException {
        PoolDataSource pool = PoolDataSourceFactory.getPoolDataSource();
        pool.setConnectionPoolName("PooledTeamResumeSample-" + UUID.randomUUID());
        pool.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pool.setURL(requiredEnv("SELECTAI_JDBC_URL"));
        pool.setUser(requiredEnv("SELECTAI_DB_USER"));
        pool.setPassword(requiredEnv("SELECTAI_DB_PASSWORD"));
        pool.setInitialPoolSize(0);
        pool.setMinPoolSize(0);
        pool.setMaxPoolSize(2);
        pool.setConnectionWaitTimeout(10);
        return pool;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
