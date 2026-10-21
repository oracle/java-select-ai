/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.SlackNotificationToolParams;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolType;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live CLOB, Object Storage, and database-directory export coverage. */
class TeamExportIT extends TeamExportIntegrationFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Export a two-agent team graph to Object Storage or a database directory.
     * Expected: The exported definition contains both agents, tasks, and tools with their
     * configured attributes and enabled statuses.
     */
    @ParameterizedTest
    @EnumSource(value = ExportDestination.class, names = {"OBJECT_STORAGE", "DIRECTORY"})
    void test33025ExportTeamDefinitionToConfiguredLocation(ExportDestination destination)
            throws Exception {
        ExportGraph graph = createExportGraph();
        assertDefinitions(exportAndRead(graph.team(), destination), graph);
    }

    /**
     * Test: Export a two-agent team graph to a CLOB definition.
     * Expected: The CLOB contains both agents, tasks, and tools with their configured attributes
     * and enabled statuses.
     */
    @Test
    void test33080ExportClobIncludesAllAgentsTasksAndTools() throws Exception {
        ExportGraph graph = createExportGraph();
        assertDefinitions(graph.team().exportDefinition(), graph);
    }

    /**
     * Test: Export a team definition to a CLOB using ExportTeamParams with overwrite disabled.
     * Expected: The returned definition contains both agents, tasks, and tools with their
     * configured attributes and enabled statuses.
     */
    @Test
    void test33093ExportClobWithTypedParametersIncludesAllComponents() throws Exception {
        ExportGraph graph = createExportGraph();
        String specification = graph.team().exportDefinition(
                ExportTeamParams.builder().overwrite(false).build());

        assertDefinitions(specification, graph);
    }

    /**
     * Test: Export a team after disabling its tasks and tools, then import the definition after
     * dropping the team.
     * Expected: The export and import preserve enabled team and agent statuses and disabled task
     * and tool statuses.
     */
    @ParameterizedTest
    @EnumSource(ExportDestination.class)
    void test33081ExportImportsEnabledAgentsWithDisabledTasksAndTools(ExportDestination destination)
            throws Exception {
        ExportGraph graph = createExportGraph();
        graph.team().disable();
        for (Task task : graph.tasks()) {
            task.disable();
        }
        for (Tool tool : graph.tools()) {
            tool.disable();
        }
        String specification = exportAndRead(graph.team(), destination);
        assertDefinitions(specification, graph, AgentStatus.ENABLED.name(),
                TaskStatus.DISABLED.name(), ToolStatus.DISABLED.name());

        // Existing objects are restored only to let force-import replace them;
        // the imported statuses still come from the exported definition.
        for (Task task : graph.tasks()) {
            task.enable();
        }
        for (Tool tool : graph.tools()) {
            tool.enable();
        }
        graph.team().drop(true);
        managedTeams.remove(graph.team());

        Team imported = importExportedDefinition(specification);

        assertThat(imported.getStatus()).isEqualTo(TeamStatus.ENABLED);
        for (Agent agent : graph.agents()) {
            assertThat(agentClient.agent(agent.getAgentName()).getStatus()).isEqualTo(AgentStatus.ENABLED);
        }
        for (Task task : graph.tasks()) {
            assertThat(agentClient.task(task.getTaskName()).getStatus()).isEqualTo(TaskStatus.DISABLED);
        }
        for (Tool tool : graph.tools()) {
            assertThat(agentClient.tool(tool.getToolName()).getStatus()).isEqualTo(ToolStatus.DISABLED);
        }
    }

    /**
     * Test: Export a Slack tool that references a credential containing a password.
     * Expected: The definition includes the credential and tool names but contains no password
     * value, password field, or private-key field.
     */
    @ParameterizedTest
    @EnumSource(ExportDestination.class)
    void test33082ExportPreservesCredentialNameWithoutSecrets(ExportDestination destination)
            throws Exception {
        String credentialName = uniqueName("EXP_NOTIFY_CRED");
        String secret = "export-secret-" + UUID.randomUUID();
        createManagedCredential(CredentialConfig.usernamePassword(credentialName, "export-test", secret));
        // Creating/exporting this definition does not invoke Slack or need a Slack token.
        Tool tool = agentClient.tool(uniqueName("EXP_NOTIFY_TOOL"), ToolAttributes.builder()
                .toolType(ToolType.NOTIFICATION)
                .toolParams(SlackNotificationToolParams.builder()
                        .credentialName(credentialName).channel("#selectai-export-it").build())
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("EXP_CRED_TASK"), taskAttributes(tool.getToolName()),
                "Java task test", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("EXP_CRED_AGENT"), agentAttributes(),
                "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("EXP_CRED_TEAM"), agent.getAgentName(), task.getTaskName());

        String specification = exportAndRead(team, destination);
        JsonNode definition = MAPPER.readTree(specification);
        assertExportedValue(textValues(definition), credentialName, "referenced credential name");
        assertExportedValue(textValues(definition), tool.getToolName(), "notification tool name");
        // Boolean assertions avoid echoing credential secrets on failure.
        assertThat(specification.contains(secret)).as("export excludes the credential password").isFalse();
        assertThat(definition.findValues("password").isEmpty()).as("export has no password fields").isTrue();
        assertThat(definition.findValues("private_key").isEmpty()).as("export has no private-key fields").isTrue();
    }

    /**
     * Test: Export a graph with long Unicode agent and task text to Object Storage or a
     * directory.
     * Expected: The export exceeds 32,767 characters and retains the complete role, instruction,
     * and agent, task, and tool names.
     */
    @ParameterizedTest
    @EnumSource(ExportDestination.class)
    void test33083LargeExportIsNotTruncated(ExportDestination destination) throws Exception {
        String role = "Export context café 東京. ".repeat(1000) + "ROLE_END_MARKER";
        String instruction = "Summarize the export context. ".repeat(800) + "{query} TASK_END_MARKER";
        Tool tool = agentClient.tool(uniqueName("EXP_LARGE_TOOL"), ToolAttributes.builder()
                .toolType(ToolType.SQL)
                .instruction("Use SQL to support the large export test.")
                .toolParams(SqlToolParams.builder().profileName(profileName).build()).build(),
                "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Agent agent = agentClient.agent(uniqueName("EXP_LARGE_AGENT"), AgentAttributes.builder()
                .profileName(profileName).role(role).enableHumanTool(false).build(),
                "Large export agent", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Task task = agentClient.task(uniqueName("EXP_LARGE_TASK"), TaskAttributes.builder()
                .instruction(instruction).tools(List.of(tool.getToolName())).enableHumanTool(false).build(),
                "Large export task", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Team team = createTeam(uniqueName("EXP_LARGE_TEAM"), agent.getAgentName(), task.getTaskName());

        String specification = exportAndRead(team, destination);
        assertThat(specification.length()).isGreaterThan(32767);
        assertThat(textValues(MAPPER.readTree(specification)))
                .contains(role, instruction, agent.getAgentName(), task.getTaskName(), tool.getToolName());
    }

    /**
     * Test: Delete a team through a second client, then export through its still-held original
     * handle.
     * Expected: Export to the selected destination fails with a database ORA error wrapped by
     * SelectAIException.
     */
    @ParameterizedTest
    @EnumSource(ExportDestination.class)
    void test33084ExportAfterDeletionThroughAnotherClientFailsInDatabase(ExportDestination destination)
            throws Exception {
        ExportGraph graph = createExportGraph();
        ExportTarget target = destination == ExportDestination.CLOB ? null : createExportTarget(destination);
        try (SelectAIAgent otherClient = SelectAIAgent.create(dbConfig)) {
            assertThat(otherClient.team(graph.team().getTeamName()).drop(true)).isTrue();
        }
        // The original handle remains locally created: the database must reject this call.
        assertDatabaseFailure(() -> {
            if (target == null) {
                graph.team().exportDefinition();
            } else {
                graph.team().exportDefinition(target.request(null));
            }
        });
    }

    /**
     * Test: Export a second graph to an existing Object Storage or directory file with overwrite
     * set to false.
     * Expected: The second export fails with a database ORA error, and the original file content
     * remains unchanged as JSON.
     */
    @ParameterizedTest
    @EnumSource(value = ExportDestination.class, names = {"OBJECT_STORAGE", "DIRECTORY"})
    void test33085OverwriteFalsePreservesExistingDefinition(ExportDestination destination) throws Exception {
        assertExistingFileProtected(destination, false);
    }

    /**
     * Test: Export a second graph to an existing file without specifying the overwrite option.
     * Expected: The export fails with a database ORA error, and the original JSON definition
     * remains unchanged.
     */
    @ParameterizedTest
    @EnumSource(value = ExportDestination.class, names = {"OBJECT_STORAGE", "DIRECTORY"})
    void test33086OmittedOverwriteDefaultsToFalse(ExportDestination destination) throws Exception {
        assertExistingFileProtected(destination, null);
    }

    /**
     * Test: Export two different graphs to the same existing file, enabling overwrite for the
     * second export.
     * Expected: The file then matches the replacement graph and no longer contains the original
     * team name.
     */
    @ParameterizedTest
    @EnumSource(value = ExportDestination.class, names = {"OBJECT_STORAGE", "DIRECTORY"})
    void test33087OverwriteTrueReplacesExistingDefinition(ExportDestination destination) throws Exception {
        ExportTarget target = createExportTarget(destination);
        ExportGraph original = createExportGraph();
        ExportGraph replacement = createExportGraph();
        assertThat(original.team().exportDefinition(target.request(null))).isTrue();
        assertDefinitions(readExport(target), original);

        assertThat(replacement.team().exportDefinition(target.request(true))).isTrue();
        String content = readExport(target);
        assertDefinitions(content, replacement);
        assertThat(textValues(MAPPER.readTree(content))).doesNotContain(original.team().getTeamName());
    }

    /**
     * Test: Export to Object Storage with either no credential name or a nonexistent credential
     * name.
     * Expected: Both credential cases fail with a database ORA error wrapped by
     * SelectAIException.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test33088MissingObjectStorageCredentialRejectedByDatabase(boolean useNonexistentName)
            throws Exception {
        ExportTarget target = createExportTarget(ExportDestination.OBJECT_STORAGE);
        ExportGraph graph = createExportGraph();
        String credentialName = useNonexistentName ? uniqueName("EXP_MISSING_CRED") : null;
        ExportTeamRequest request = ExportTeamRequest.builder(target.location())
                .objectStorageCredentialName(credentialName).build();
        assertDatabaseFailure(() -> graph.team().exportDefinition(request));
    }

    /**
     * Test: Export to a filename in a database directory that does not exist.
     * Expected: The export fails with a database ORA error wrapped by SelectAIException.
     */
    @Test
    void test33089MissingDirectoryRejectedByDatabase() throws Exception {
        ExportGraph graph = createExportGraph();
        ExportTeamRequest request = ExportTeamRequest.builder(uniqueName("EXP_NO_DIR") + ":team.json").build();
        assertDatabaseFailure(() -> graph.team().exportDefinition(request));
    }

    /**
     * Test: Export to a directory location with no filename, with and without a trailing colon.
     * Expected: Both locations fail with a database ORA error wrapped by SelectAIException.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", ":"})
    void test33090DirectoryLocationWithoutFilenameRejectedByDatabase(String suffix) throws Exception {
        String directory = exportDirectoryName();
        ExportGraph graph = createExportGraph();
        ExportTeamRequest request = ExportTeamRequest.builder(directory + suffix).build();
        assertDatabaseFailure(() -> graph.team().exportDefinition(request));
    }

    /**
     * Test: Export using an incomplete HTTPS URI or a string that is not an Object Storage URI.
     * Expected: Both locations fail with a database ORA error wrapped by SelectAIException.
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://", "not-an-object-storage-uri"})
    void test33091InvalidObjectStorageLocationRejectedByDatabase(String location) throws Exception {
        ExportGraph graph = createExportGraph();
        ExportTeamRequest request = ExportTeamRequest.builder(location)
                .objectStorageCredentialName(exportCredentialName()).build();
        assertDatabaseFailure(() -> graph.team().exportDefinition(request));
    }

    private void assertExistingFileProtected(ExportDestination destination, Boolean overwrite) throws Exception {
        ExportTarget target = createExportTarget(destination);
        ExportGraph original = createExportGraph();
        ExportGraph replacement = createExportGraph();
        assertThat(original.team().exportDefinition(target.request(overwrite))).isTrue();
        String before = readExport(target);
        assertDefinitions(before, original);

        assertDatabaseFailure(() -> replacement.team().exportDefinition(target.request(overwrite)));
        assertThat(MAPPER.readTree(readExport(target))).isEqualTo(MAPPER.readTree(before));
    }

    private ExportGraph createExportGraph() throws Exception {
        List<Agent> agents = new ArrayList<>();
        List<Task> tasks = new ArrayList<>();
        List<Tool> tools = new ArrayList<>();
        List<TeamMember> members = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            String toolName = uniqueName("EXP_SQL_TOOL");
            Tool tool = agentClient.tool(toolName, ToolAttributes.builder()
                    .toolType(ToolType.SQL)
                    .instruction("Use SQL export tool " + index + " to answer database questions.")
                    .toolParams(SqlToolParams.builder().profileName(profileName).build()).build(),
                    "Java tool test", ToolStatus.ENABLED);
            tool.create();
            managedTools.add(tool);
            tools.add(tool);
            String taskName = uniqueName("EXP_TASK");
            Task task = agentClient.task(taskName, TaskAttributes.builder()
                    .instruction("Answer this question using export task " + taskName + ": {query}")
                    .tools(List.of(toolName)).enableHumanTool(false).build(),
                    "Export integration task", TaskStatus.ENABLED);
            task.create();
            managedTasks.add(task);
            tasks.add(task);
            String agentName = uniqueName("EXP_AGENT");
            Agent agent = agentClient.agent(agentName, AgentAttributes.builder()
                    .profileName(profileName).role("Export worker " + agentName + " answers database questions.")
                    .enableHumanTool(false).build(), "Export integration agent", AgentStatus.ENABLED);
            agent.create();
            managedAgents.add(agent);
            agents.add(agent);
            members.add(TeamMember.builder(agent.getAgentName()).task(task.getTaskName()).build());
        }
        Team team = agentClient.team(uniqueName("EXP_TEAM"), TeamAttributes.builder()
                .agents(members).process("sequential").build(), "Export integration team", TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);
        return new ExportGraph(team, agents, tasks, tools);
    }

    private static void assertDefinitions(String specification, ExportGraph graph) throws Exception {
        assertDefinitions(specification, graph, AgentStatus.ENABLED.name(),
                TaskStatus.ENABLED.name(), ToolStatus.ENABLED.name());
    }

    private static void assertDefinitions(String specification, ExportGraph graph,
                                          String agentStatus, String taskStatus, String toolStatus)
            throws Exception {
        assertThat(specification).isNotBlank();
        JsonNode definition = MAPPER.readTree(specification);
        List<JsonNode> exportedAgents = exportedAgents(definition);
        assertThat(exportedAgents).as("exported agent count").hasSize(graph.agents().size());
        for (Agent agent : graph.agents()) {
            JsonNode exportedAgent = findByName(exportedAgents, agent.getAgentName(), "agent");
            assertThat(exportedAgent.path("component_type").asText())
                    .as("component type of agent %s", agent.getAgentName())
                    .isEqualTo("Agent");
            assertThat(exportedAgent.path("system_prompt").asText())
                    .as("role of agent %s", agent.getAgentName())
                    .isEqualTo(agent.getAgentAttributes().getRole());
            assertStatus(exportedAgent.path("status"), agentStatus,
                    "agent " + agent.getAgentName());
        }
        for (Task task : graph.tasks()) {
            JsonNode exportedTask = findTask(exportedAgents, task.getTaskName());
            assertThat(exportedTask.path("instruction").asText())
                    .as("instruction of task %s", task.getTaskName())
                    .isEqualTo(task.getTaskAttributes().getInstruction());
            assertStatus(exportedTask.path("status"), taskStatus,
                    "task " + task.getTaskName());
            JsonNode exportedToolNames = exportedTask.path("task_attributes").path("tools");
            assertThat(exportedToolNames.isArray())
                    .as("tool references of task %s", task.getTaskName())
                    .isTrue();
            assertThat(textValues(exportedToolNames))
                    .containsExactlyInAnyOrderElementsOf(task.getTaskAttributes().getTools());
        }
        List<JsonNode> exportedTools = exportedTools(exportedAgents);
        assertThat(exportedTools).as("exported tool count").hasSize(graph.tools().size());
        for (Tool tool : graph.tools()) {
            JsonNode exportedTool = findByName(exportedTools, tool.getToolName(), "tool");
            assertThat(exportedTool.path("component_type").asText())
                    .as("component type of tool %s", tool.getToolName())
                    .isEqualTo("ServerTool");
            assertThat(exportedTool.path("description").asText())
                    .as("instruction of tool %s", tool.getToolName())
                    .isEqualTo(tool.getToolAttributes().getInstruction());
            assertStatus(exportedTool.path("metadata").path("select_ai").path("status"),
                    toolStatus, "tool " + tool.getToolName());
        }
    }

    private static List<JsonNode> exportedAgents(JsonNode definition) {
        assertThat(definition.isObject() || definition.isArray())
                .as("export is an agent object or an array of agent objects")
                .isTrue();
        List<JsonNode> agents = new ArrayList<>();
        if (definition.isObject()) {
            agents.add(definition);
        } else {
            definition.forEach(agents::add);
        }
        return agents;
    }

    private static JsonNode findTask(List<JsonNode> exportedAgents, String taskName) {
        for (JsonNode exportedAgent : exportedAgents) {
            JsonNode task = exportedAgent.path("task");
            if (taskName.equals(task.path("task_name").asText())) {
                return task;
            }
        }
        throw new AssertionError("Export does not contain task " + taskName);
    }

    private static List<JsonNode> exportedTools(List<JsonNode> exportedAgents) {
        List<JsonNode> tools = new ArrayList<>();
        for (JsonNode exportedAgent : exportedAgents) {
            JsonNode agentTools = exportedAgent.path("tools");
            assertThat(agentTools.isArray()).as("tools of exported agent %s",
                    exportedAgent.path("name").asText()).isTrue();
            agentTools.forEach(tools::add);
        }
        return tools;
    }

    private static JsonNode findByName(List<JsonNode> objects, String name, String objectType) {
        for (JsonNode object : objects) {
            if (name.equals(object.path("name").asText())) {
                return object;
            }
        }
        throw new AssertionError("Export does not contain " + objectType + " " + name);
    }

    private static void assertStatus(JsonNode actual, String expected, String label) {
        assertThat(actual.asText()).as("status of %s", label).isEqualToIgnoringCase(expected);
    }

    private static void assertExportedValue(List<String> values, String expected, String label) {
        assertThat(values.contains(expected)).as("export contains %s", label).isTrue();
    }

    private static List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        collectTextValues(node, values);
        return values;
    }

    private static void collectTextValues(JsonNode node, List<String> values) {
        if (node.isTextual()) {
            values.add(node.asText());
        } else {
            node.forEach(child -> collectTextValues(child, values));
        }
    }

    private static void assertDatabaseFailure(ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(SelectAIException.class,
                exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class)
                        .hasMessageContaining("ORA-"));
    }

    private record ExportGraph(Team team, List<Agent> agents, List<Task> tasks, List<Tool> tools) { }
}
