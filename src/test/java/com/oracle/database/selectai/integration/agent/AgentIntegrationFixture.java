/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ToolType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Shared setup and cleanup for live DBMS_CLOUD_AI_AGENT tests. */
public abstract class AgentIntegrationFixture extends IntegrationTestFixture {
    private static final String AGENT_TEST_MODEL = "openai.gpt-6.1-sol";

    protected SelectAIAgent agentClient;
    protected final List<Agent> managedAgents = new ArrayList<>();
    protected final List<Task> managedTasks = new ArrayList<>();
    protected final List<Tool> managedTools = new ArrayList<>();
    protected final List<Team> managedTeams = new ArrayList<>();
    protected final List<Conversation> managedConversations = new ArrayList<>();
    private final List<String> managedFunctions = new ArrayList<>();
    private final List<String> managedTables = new ArrayList<>();

    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        profile.setAttribute("model", AGENT_TEST_MODEL);
    }

    @BeforeEach
    final void setUpAgentConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
        agentClient = SelectAIAgent.create(dbConfig);
    }

    @AfterEach
    final void cleanAgentResources() throws Exception {
        try {
            dropAll(managedTeams);
            dropAll(managedConversations);
            dropAll(managedAgents);
            dropAll(managedTasks);
            dropAll(managedTools);
            dropManagedFunctions();
            dropManagedTables();
        } finally {
            try {
                if (agentClient != null) {
                    agentClient.close();
                }
            } finally {
                closeIsolatedConnection();
            }
        }
    }

    @Override
    protected String profileObjectList() {
        return null;
    }

    protected String uniqueName(String stem) {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        return ("JSAI_AG_" + stem + "_" + suffix).toUpperCase();
    }

    protected AgentAttributes agentAttributes() {
        return AgentAttributes.builder()
                .profileName(profileName)
                .role("Answer database questions clearly and accurately.")
                .enableHumanTool(false)
                .shortTermMemoryLength(10)
                .build();
    }

    protected TaskAttributes taskAttributes(String... tools) {
        return TaskAttributes.builder()
                .instruction("Answer this question briefly: {query}")
                .tools(tools.length == 0 ? null : List.of(tools))
                .enableHumanTool(false)
                .build();
    }

    protected ToolAttributes sqlToolAttributes() {
        return ToolAttributes.builder()
                .toolType(ToolType.SQL)
                .toolParams(SqlToolParams.builder().profileName(profileName).build())
                .build();
    }

    protected void createManagedTable(String name, String definition) throws Exception {
        try (Statement statement = jdbcConnection().createStatement()) {
            statement.executeUpdate(definition);
        }
        managedTables.add(name);
    }

    protected void createManagedPlsqlFunction(String name, String definition) throws Exception {
        try (Statement statement = jdbcConnection().createStatement()) {
            statement.executeUpdate(definition);
        }
        managedFunctions.add(name);
    }

    /** Creates the same age function used by the Python agent tool tests. */
    protected String createPythonPlsqlFunction() throws Exception {
        String functionName = uniqueName("AGE_FUNCTION");
        try (Statement statement = jdbcConnection().createStatement()) {
            statement.executeUpdate("CREATE OR REPLACE FUNCTION " + functionName
                    + " (p_birth_date DATE) RETURN NUMBER IS BEGIN "
                    + "RETURN TRUNC(MONTHS_BETWEEN(SYSDATE, p_birth_date) / 12); END;");
        }
        managedFunctions.add(functionName);
        return functionName;
    }

    protected Team createTeam(String name, String agentName, String taskName) throws Exception {
        TeamAttributes attributes = TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(agentName).task(taskName).build()))
                .process("sequential")
                .longTermMemoryLength(10)
                .build();
        Team value = agentClient.team(name, attributes, "Java team test", TeamStatus.ENABLED);
        value.create();
        managedTeams.add(value);
        return value;
    }

    protected Conversation createConversation() throws Exception {
        Conversation value = selectAI.conversation(ConversationAttributes.builder()
                .title("Java agent test " + UUID.randomUUID())
                .description("Conversation for DBMS_CLOUD_AI_AGENT integration coverage")
                .build());
        value.create();
        managedConversations.add(value);
        return value;
    }

    protected void requireOptional(String name, String reason) {
        assumeTrue(env(name) != null, reason + " (missing " + name + ")");
    }

    private static void dropAll(List<?> resources) {
        for (Object resource : new ArrayList<>(resources)) {
            try {
                if (resource instanceof Team team) {
                    team.drop(true);
                } else if (resource instanceof Conversation conversation) {
                    conversation.drop(true);
                } else if (resource instanceof Agent agent) {
                    agent.drop(true);
                } else if (resource instanceof Task task) {
                    task.drop(true);
                } else if (resource instanceof Tool tool) {
                    tool.drop(true);
                }
            } catch (Exception ignored) {
                // Preserve the original test failure; cleanup is best effort.
            }
        }
        resources.clear();
    }
    private void dropManagedFunctions() {
        if (selectAI == null) {
            managedFunctions.clear();
            return;
        }
        for (String functionName : new ArrayList<>(managedFunctions)) {
            try (Statement statement = jdbcConnection().createStatement()) {
                statement.executeUpdate("DROP FUNCTION " + functionName);
            } catch (Exception ignored) {
                // Preserve the original test failure; cleanup is best effort.
            }
        }
        managedFunctions.clear();
    }

    private void dropManagedTables() {
        if (selectAI == null) {
            managedTables.clear();
            return;
        }
        for (String name : new ArrayList<>(managedTables)) {
            try (Statement statement = jdbcConnection().createStatement()) {
                statement.executeUpdate("DROP TABLE " + name + " PURGE");
            } catch (Exception ignored) {
                // Preserve the original test failure; only test-created tables are tracked.
            }
        }
        managedTables.clear();
    }
}
