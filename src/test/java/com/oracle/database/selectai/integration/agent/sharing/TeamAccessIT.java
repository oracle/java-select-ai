/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.sharing;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.RunTeamParams;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Live coverage for DBMS_CLOUD_AI_AGENT team sharing and revocation. */
class TeamAccessIT extends AgentIntegrationFixture {
    private static final String UNCONFIGURED_CONSUMER_PROFILE = "JSAI_MISSING_CONSUMER_PROFILE";

    private SelectAI adminClient;
    private DatabaseAdmin databaseAdmin;
    private SelectAI consumerSelectAI;
    private SelectAIAgent consumerAgent;
    private String consumerUser;
    private String consumerPassword;
    private boolean consumerCreated;
    private String roleName;
    private boolean roleCreated;
    private Conversation consumerConversation;
    private Credential consumerCredential;
    private Profile consumerProfile;
    private String consumerProfileName;
    private List<String> consumerHttpHosts = List.of();

    @BeforeEach
    void createIsolatedConsumer() throws Exception {
        consumerUser = uniqueName("TEAM_CONSUMER");
        consumerPassword = "JsaI_" + UUID.randomUUID().toString().replace("-", "") + "Aa1";
        adminClient = SelectAI.create(adminDbConfig());

        try {
            createConsumerUser();
            consumerCreated = true;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1031) {
                assumeTrue(false,
                        "Team sharing tests require CREATE USER privilege: "
                                + exception.getMessage());
            }
            throw exception;
        }

        databaseAdmin = DatabaseAdmin.create(adminDbConfig());
        databaseAdmin.grantPrivileges(List.of(consumerUser));
        consumerHttpHosts = consumerHttpHosts();
        for (String host : consumerHttpHosts) {
            databaseAdmin.grantHttpAccess(List.of(consumerUser), host);
        }
        consumerSelectAI = SelectAI.create(connectionConfigFor(consumerUser, consumerPassword));
        consumerAgent = SelectAIAgent.create(connectionConfigFor(consumerUser, consumerPassword));
    }

    @AfterEach
    void removeIsolatedConsumer() {
        try {
            if (consumerConversation != null) {
                try {
                    consumerConversation.drop(true);
                } catch (Exception ignored) {
                    // Preserve the primary test failure; cleanup is best effort.
                }
            }
            dropConsumerRuntimeProfile();
            closeClientQuietly(consumerAgent);
            closeClientQuietly(consumerSelectAI);

            // Remove grants while the owner handle is still usable. This also
            // keeps cleanup correct when a test fails before explicit revoke.
            if (consumerUser != null) {
                for (Team team : new ArrayList<>(managedTeams)) {
                    try {
                        team.revokeAccess(consumerUser);
                    } catch (Exception ignored) {
                        // The grant may not have been created; cleanup is best effort.
                    }
                }
            }
            if (roleName != null) {
                for (Team team : new ArrayList<>(managedTeams)) {
                    try {
                        team.revokeAccess(roleName);
                    } catch (Exception ignored) {
                        // The role may not have been granted; cleanup is best effort.
                    }
                }
            }
        } finally {
            try {
                if (databaseAdmin != null && consumerCreated) {
                    for (String host : consumerHttpHosts) {
                        try {
                            databaseAdmin.revokeHttpAccess(List.of(consumerUser), host);
                        } catch (Exception ignored) {
                            // Dropping the user below also removes user ACL entries.
                        }
                    }
                    databaseAdmin.revokePrivileges(List.of(consumerUser));
                }
            } catch (Exception ignored) {
                // Dropping the user below also removes direct package grants.
            } finally {
                dropRole();
                dropConsumerUser();
                closeClientQuietly(databaseAdmin);
                closeClientQuietly(adminClient);
            }
        }
    }

    private void dropConsumerRuntimeProfile() {
        if (consumerProfile != null) {
            try {
                consumerProfile.drop(true);
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            } finally {
                consumerProfile = null;
                consumerProfileName = null;
            }
        }
        if (consumerCredential != null) {
            try {
                consumerCredential.drop();
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            } finally {
                consumerCredential = null;
            }
        }
    }

    /**
     * Test: Grant a team through the owner client, configure the consumer's runtime profile, and
     * run the owner's qualified team as the consumer with the prompt "Calculate 4 + 4."
     * Expected: The consumer's response contains 8.
     */
    @Test
    void ownerCanGrantThroughClientAndConsumerCanRunQualifiedTeam() throws Exception {
        Team team = createRuntimeTeam("CLIENT_GRANT");

        assertThat(agentClient.grantTeamAccess(team.getTeamName(), consumerUser)).isTrue();
        createConsumerRuntimeProfile();

        String result = runAsConsumer(team,
                "Calculate 4 + 4. Reply with only the numeral.",
                createConsumerConversation(), consumerProfileName);

        assertThat(result).contains("8");
    }

    /**
     * Test: Grant the owner’s team to the consumer and list consumer-owned teams by its exact name.
     * Expected: The shared team is absent from the consumer's owned-team list.
     */
    @Test
    void sharedTeamIsNotListedAsConsumerOwnedTeam() throws Exception {
        TeamGraph graph = createTeamGraph("DISCOVERY_BOUNDARY");
        assertThat(graph.team().grantAccess(consumerUser)).isTrue();

        assertThat(consumerAgent.listTeams("^" + graph.team().getTeamName() + "$"))
                .isEmpty();
    }

    /**
     * Test: Grant access through a Team handle, run the qualified team, revoke access, and run it
     * again using the same conversation.
     * Expected: The granted run reaches runtime profile resolution; after revocation the database
     * reports that the team does not exist.
     */
    @Test
    void teamHandleGrantAndRevokeControlsQualifiedConsumerRun() throws Exception {
        TeamGraph graph = createTeamGraph("HANDLE_GRANT");
        String conversationId = createConsumerConversation();

        assertThat(graph.team().grantAccess(consumerUser)).isTrue();
        assertConsumerRunReachesRuntime(graph.team(), "Run before revoke.", conversationId);

        assertThat(graph.team().revokeAccess(consumerUser)).isTrue();
        assertConsumerRunRejected(graph.team(), "Run after revoke.", conversationId);
    }

    /**
     * Test: Grant two teams to the consumer and revoke access to only one of them.
     * Expected: The revoked team is reported as nonexistent, while the other team still reaches
     * runtime profile resolution.
     */
    @Test
    void revokeRemovesOnlyTargetTeamAccess() throws Exception {
        TeamGraph revoked = createTeamGraph("REVOKED_DISCOVERY");
        TeamGraph retained = createTeamGraph("RETAINED_DISCOVERY");
        String conversationId = createConsumerConversation();
        assertThat(revoked.team().grantAccess(consumerUser)).isTrue();
        assertThat(retained.team().grantAccess(consumerUser)).isTrue();

        assertConsumerRunReachesRuntime(revoked.team(), "Run the team before revoke.", conversationId);
        assertConsumerRunReachesRuntime(retained.team(), "Run the retained team before revoke.",
                conversationId);

        assertThat(revoked.team().revokeAccess(consumerUser)).isTrue();

        assertConsumerRunRejected(revoked.team(), "Run the revoked team after revoke.", conversationId);
        assertConsumerRunReachesRuntime(retained.team(), "Run the retained team after revoke.",
                conversationId);
    }

    /**
     * Test: Grant a team, confirm the consumer can resolve it for a run, revoke access, and start
     * another run.
     * Expected: The post-revocation run is rejected with a database error stating that the team
     * does not exist.
     */
    @Test
    void revokePreventsNewRun() throws Exception {
        TeamGraph graph = createTeamGraph("REVOKE_RUN");
        assertThat(graph.team().grantAccess(consumerUser)).isTrue();
        String conversationId = createConsumerConversation();
        assertConsumerRunReachesRuntime(graph.team(), "Run before revoke.", conversationId);

        assertThat(graph.team().revokeAccess(consumerUser)).isTrue();

        assertConsumerRunRejected(graph.team(), "This run must be rejected after revoke.", conversationId);
    }

    /**
     * Test: Use the consumer client to grant access to the owner's team and to grant or revoke the
     * owner's access.
     * Expected: All three operations throw SelectAIException with an SQLException cause.
     */
    @Test
    void nonOwnerCannotGrantOrRevokeOwnerTeam() throws Exception {
        TeamGraph graph = createTeamGraph("OWNER_ONLY");
        String ownerUser = dbConfig.getDbUser().toUpperCase(Locale.ROOT);

        assertThatThrownBy(() -> consumerAgent.grantTeamAccess(
                graph.team().getTeamName(), consumerUser))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class));

        assertThatThrownBy(() -> consumerAgent.grantTeamAccess(graph.team().getTeamName(), ownerUser))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class));
        assertThatThrownBy(() -> consumerAgent.revokeTeamAccess(graph.team().getTeamName(), ownerUser))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class));
    }

    /**
     * Test: Grant the team to the consumer, then look up its agent, task, and tool and request the
     * agent definition directly.
     * Expected: The resource lists are empty and retrieving the agent definition throws
     * SelectAIException.
     */
    @Test
    void teamGrantDoesNotExposeUnderlyingResources() throws Exception {
        TeamGraph graph = createTeamGraph("RESOURCE_BOUNDARY");
        assertThat(graph.team().grantAccess(consumerUser)).isTrue();

        assertThat(consumerAgent.listAgents("^" + graph.agent().getAgentName() + "$"))
                .isEmpty();
        assertThat(consumerAgent.listTasks("^" + graph.task().getTaskName() + "$"))
                .isEmpty();
        assertThat(consumerAgent.listTools("^" + graph.tool().getToolName() + "$"))
                .isEmpty();
        assertThatThrownBy(() -> consumerAgent.getDefinition(
                AgentObjectType.AGENT, graph.agent().getAgentName(), false))
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Grant team access to a role held by the consumer, run the qualified team, revoke the
     * role grant, and run it again.
     * Expected: The first run reaches runtime profile resolution; after revocation the database
     * reports that the team does not exist.
     */
    @Test
    void roleCanBeGrantedAndRevoked() throws Exception {
        TeamGraph graph = createTeamGraph("ROLE_GRANT");
        roleName = uniqueName("TEAM_ROLE");
        createRoleGrantedToConsumer();
        reconnectConsumerClients();
        String conversationId = createConsumerConversation();

        assertThat(graph.team().grantAccess(roleName)).isTrue();
        assertConsumerRunReachesRuntime(graph.team(), "Run with role grant.", conversationId);

        assertThat(graph.team().revokeAccess(roleName)).isTrue();
        assertConsumerRunRejected(graph.team(), "Run after role revoke.", conversationId);
    }

    private TeamGraph createTeamGraph(String stem) throws Exception {
        Tool tool = agentClient.tool(uniqueName(stem + "_TOOL"), sqlToolAttributes(),
                "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName(stem + "_TASK"), taskAttributes(tool.getToolName()),
                "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName(stem + "_AGENT"), AgentAttributes.builder()
                        .profileName("{llm_profile}")
                        .role("Answer database questions clearly and accurately.")
                        .enableHumanTool(false)
                        .shortTermMemoryLength(10)
                        .build(),
                "Java shared-team agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName(stem + "_TEAM"),
                agent.getAgentName(), task.getTaskName());
        return new TeamGraph(tool, task, agent, team);
    }

    private Team createRuntimeTeam(String stem) throws Exception {
        Task task = agentClient.task(uniqueName(stem + "_TASK"),
                TaskAttributes.builder()
                        .instruction("Calculate basic arithmetic and reply with only the requested numeral: "
                                + "{query}")
                        .enableHumanTool(false)
                        .build(),
                "Java shared-team deterministic runtime task", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);

        Agent agent = agentClient.agent(uniqueName(stem + "_AGENT"), AgentAttributes.builder()
                        .profileName("{llm_profile}")
                        .role("Answer arithmetic questions with only the requested numeral.")
                        .enableHumanTool(false)
                        .shortTermMemoryLength(10)
                        .build(),
                "Java shared-team agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        return createTeam(uniqueName(stem + "_TEAM"), agent.getAgentName(), task.getTaskName());
    }

    private String createConsumerConversation() throws Exception {
        consumerConversation = consumerSelectAI.conversation(
                com.oracle.database.selectai.model.ConversationAttributes.builder()
                        .title("Team sharing integration " + UUID.randomUUID())
                        .build());
        return consumerConversation.create();
    }

    private String runAsConsumer(Team team, String prompt, String conversationId,
                                 String runtimeProfileName) throws Exception {
        return consumerAgent.runTeam(qualifiedTeamName(team), RunTeamRequest.builder()
                .userPrompt(prompt)
                .params(RunTeamParams.builder()
                        .conversationId(conversationId)
                        .attributeVariables(Map.of("llm_profile", runtimeProfileName))
                        .build())
                .build()).getRawValue();
    }

    private void assertConsumerRunReachesRuntime(Team team, String prompt, String conversationId) {
        assertThatThrownBy(() -> runAsConsumer(team, prompt, conversationId,
                UNCONFIGURED_CONSUMER_PROFILE))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .isInstanceOfSatisfying(SQLException.class,
                                        sqlException -> assertThat(sqlException.getMessage())
                                                .contains("Profile")));
    }

    private void assertConsumerRunRejected(Team team, String prompt, String conversationId) {
        assertThatThrownBy(() -> runAsConsumer(team, prompt, conversationId,
                UNCONFIGURED_CONSUMER_PROFILE))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .isInstanceOfSatisfying(SQLException.class,
                                        sqlException -> assertThat(sqlException.getMessage())
                                                .contains("Team \"")
                                                .contains("does not exist")));
    }

    private void createConsumerRuntimeProfile() throws Exception {
        String credentialName = uniqueName("TEAM_CONSUMER_CREDENTIAL");
        consumerCredential = consumerSelectAI.credential(CredentialConfig.builder(credentialName)
                .userOcid(requiredFeatureValue("SELECT_AI_IT_OCI_USER_OCID",
                        "consumer credential requires an OCI user OCID"))
                .tenancyOcid(requiredFeatureValue("SELECT_AI_IT_OCI_TENANCY_OCID",
                        "consumer credential requires an OCI tenancy OCID"))
                .privateKey(requiredFeatureValue("SELECT_AI_IT_OCI_PRIVATE_KEY",
                        "consumer credential requires an OCI private key"))
                .fingerprint(requiredFeatureValue("SELECT_AI_IT_OCI_FINGERPRINT",
                        "consumer credential requires an OCI fingerprint"))
                .build());
        assertThat(consumerCredential.create()).isTrue();

        Map<String, String> attributes = new HashMap<>(isolatedProfileAttributes().toAttributeMap());
        attributes.put("credential_name", credentialName);
        consumerProfileName = uniqueName("TEAM_CONSUMER_PROFILE");
        consumerProfile = consumerSelectAI.profile(consumerProfileName,
                ProfileAttributes.fromAttributeMap(attributes),
                "Consumer-owned profile for shared-team integration coverage", null);
        assertThat(consumerProfile.create()).isTrue();
    }

    private void reconnectConsumerClients() throws Exception {
        closeClientQuietly(consumerAgent);
        closeClientQuietly(consumerSelectAI);
        consumerSelectAI = SelectAI.create(connectionConfigFor(consumerUser, consumerPassword));
        consumerAgent = SelectAIAgent.create(connectionConfigFor(consumerUser, consumerPassword));
    }

    private List<String> consumerHttpHosts() {
        String awsRegion = envOrDefault("SELECT_AI_IT_PROVIDER_AWS_REGION", "us-east-1");
        String azureResource = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AZURE_RESOURCE_NAME", "ADBST-AI-RESOURCE-JAPAN-EAST");
        return List.of(
                "bedrock-runtime." + awsRegion.toLowerCase(Locale.ROOT) + ".amazonaws.com",
                "api.openai.com",
                "api.anthropic.com",
                azureResource.toLowerCase(Locale.ROOT) + ".openai.azure.com",
                "generativelanguage.googleapis.com");
    }

    private String qualifiedTeamName(Team team) {
        return dbConfig.getDbUser().toUpperCase(Locale.ROOT) + "." + team.getTeamName();
    }

    private void createConsumerUser() throws SQLException {
        Connection connection = adminClient.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE USER " + consumerUser + " IDENTIFIED BY \""
                    + consumerPassword.replace("\"", "\"\"") + "\"");
            statement.execute("GRANT CREATE SESSION TO " + consumerUser);
        }
    }

    private void createRoleGrantedToConsumer() throws SQLException {
        Connection connection = adminClient.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + roleName);
            roleCreated = true;
            statement.execute("GRANT " + roleName + " TO " + consumerUser);
        }
    }

    private void dropRole() {
        if (!roleCreated || adminClient == null) {
            return;
        }
        Connection connection = adminClient.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ROLE " + roleName);
        } catch (Exception ignored) {
            // Preserve the primary test failure; cleanup is best effort.
        }
        roleCreated = false;
    }

    private void dropConsumerUser() {
        if (!consumerCreated || adminClient == null) {
            return;
        }
        Connection connection = adminClient.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP USER " + consumerUser + " CASCADE");
        } catch (Exception ignored) {
            // Preserve the primary test failure; cleanup is best effort.
        }
        consumerCreated = false;
    }

    private static void closeClientQuietly(AutoCloseable client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            }
        }
    }

    private record TeamGraph(Tool tool, Task task, Agent agent, Team team) {
    }
}
