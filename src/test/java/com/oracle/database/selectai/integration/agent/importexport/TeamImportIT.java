/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamAttributes.TeamMember;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolInput;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Live IMPORT_TEAM coverage for conflicts, profiles, source failures, and restored definitions. */
class TeamImportIT extends TeamExportIntegrationFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SENTINEL = "Existing component must survive an unforced import";

    /**
     * Test: Import without force when the exported agent or tool name already exists, using
     * force=false or the default.
     * Expected: Import fails, and snapshots show that both the conflicting component and an
     * unrelated agent are unchanged.
     */
    @ParameterizedTest
    @MethodSource("unforcedAgentOrToolConflicts")
    void test33100UnforcedImportRejectsConflictingAgentOrTool(AgentObjectType type, Boolean force)
            throws Exception {
        ImportGraph source = createImportGraph();
        String specification = source.team().exportDefinition();
        dropGraph(source);
        createConflictingComponent(source, type);
        ResourceSnapshot existing = snapshot(type, componentName(source, type));
        Agent unrelated = agentClient.agent(uniqueName("IMP_UNRELATED"), agentAttributes(),
                "Java agent test", AgentStatus.ENABLED);
        unrelated.create();
        managedAgents.add(unrelated);
        ResourceSnapshot untouched = snapshot(AgentObjectType.AGENT, unrelated.getAgentName());

        ImportTeamRequest request = inlineRequest(specification, profileName).force(force).build();
        assertImportFailure(request);

        assertSnapshot(existing);
        assertSnapshot(untouched);
    }

    static Stream<Arguments> unforcedAgentOrToolConflicts() {
        return Stream.of(AgentObjectType.AGENT, AgentObjectType.TOOL)
                .flatMap(type -> Stream.of(Arguments.of(type, false), Arguments.of(type, (Boolean) null)));
    }

    /**
     * Test: Import without force when the exported task name already exists, using force=false or
     * the default.
     * Expected: Import succeeds with the exported task mapped to the team, while the existing
     * task snapshot remains unchanged.
     */
    @ParameterizedTest
    @MethodSource("unforcedValues")
    void test33100UnforcedImportRetainsConflictingTask(Boolean force) throws Exception {
        ImportGraph source = createImportGraph();
        String specification = source.team().exportDefinition();
        dropGraph(source);
        createConflictingComponent(source, AgentObjectType.TASK);
        ResourceSnapshot existing = snapshot(AgentObjectType.TASK, source.task().getTaskName());

        Team imported = importExportedDefinition(inlineRequest(specification, profileName).force(force).build());

        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getTask)
                .containsExactly(source.task().getTaskName());
        assertSnapshot(existing);
    }

    static Stream<Arguments> unforcedValues() {
        return Stream.of(Arguments.of(false), Arguments.of((Boolean) null));
    }

    /**
     * Test: Force-import an exported graph over an existing agent, task, or tool name.
     * Expected: The imported graph matches the source; unrelated agent and tool snapshots, the
     * supporting function, and profile attributes remain unchanged.
     */
    @ParameterizedTest
    @EnumSource(value = AgentObjectType.class, names = {"AGENT", "TASK", "TOOL"})
    void test33101ForcedImportReplacesConflictAndPreservesUnrelatedResources(AgentObjectType type)
            throws Exception {
        ImportGraph source = createImportGraph();
        String specification = source.team().exportDefinition();
        long functionId = functionObjectId(source.functionName());
        JsonNode profileAttributes = MAPPER.readTree(profile.getProfileAttributes().toJson());
        dropGraph(source);
        createConflictingComponent(source, type);
        Agent unrelatedAgent = agentClient.agent(uniqueName("IMP_UNRELATED_AGENT"), agentAttributes(),
                "Java agent test", AgentStatus.ENABLED);
        unrelatedAgent.create();
        managedAgents.add(unrelatedAgent);
        Tool unrelatedTool = agentClient.tool(uniqueName("IMP_UNRELATED_TOOL"), sqlToolAttributes(),
                "Java SQL tool test", ToolStatus.ENABLED);
        unrelatedTool.create();
        managedTools.add(unrelatedTool);
        List<ResourceSnapshot> untouched = List.of(
                snapshot(AgentObjectType.AGENT, unrelatedAgent.getAgentName()),
                snapshot(AgentObjectType.TOOL, unrelatedTool.getToolName()));

        Team imported = importExportedDefinition(inlineRequest(specification, profileName).force(true).build());

        assertImportedGraph(imported, source, profileName);
        for (ResourceSnapshot resource : untouched) {
            assertSnapshot(resource);
        }
        assertThat(functionObjectId(source.functionName())).isEqualTo(functionId);
        assertThat(MAPPER.readTree(profile.getProfileAttributes().toJson())).isEqualTo(profileAttributes);
    }

    /**
     * Test: Import truncated or malformed JSON as an inline team specification.
     * Expected: Import fails with a SelectAIException carrying the database SQLException and
     * matching SQL error metadata.
     */
    @ParameterizedTest
    @ValueSource(strings = {"{\"agents\":", "[}"})
    void test33102MalformedInlineSpecificationRejectedByDatabase(String specification) throws Exception {
        assertImportFailure(inlineRequest(specification, profileName).build());
    }

    /**
     * Test: Import JSON whose top-level value is null, a number, or a string.
     * Expected: Import fails with a SelectAIException carrying the database SQLException and
     * matching SQL error metadata.
     */
    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "\"not-a-team-specification\""})
    void test33103UnsupportedSpecificationShapeRejectedByDatabase(String specification) throws Exception {
        assertImportFailure(inlineRequest(specification, profileName).build());
    }

    /**
     * Test: Corrupt an exported agent by making its name an array or its tools field a string,
     * then import it.
     * Expected: Import fails with a SelectAIException carrying the database SQLException and
     * matching SQL error metadata.
     */
    @ParameterizedTest
    @ValueSource(strings = {"name", "tools"})
    void test33104InvalidExportedAgentFieldsRejectedByDatabase(String field) throws Exception {
        ImportGraph source = createImportGraph();
        ObjectNode specification = exportedAgent(source);
        assertThat(specification.has(field)).as("export contains the field being corrupted").isTrue();
        if (field.equals("name")) {
            specification.set(field, MAPPER.createArrayNode());
        } else {
            specification.put(field, "invalid-attribute-type");
        }
        dropGraph(source);

        assertImportFailure(inlineRequest(MAPPER.writeValueAsString(specification), profileName).build());
    }

    /**
     * Test: Import an agent specification whose task value is a string instead of a task object.
     * Expected: Import assigns a new task name distinct from the source and creates a task with a
     * nonblank instruction.
     */
    @Test
    void test33104NonObjectTaskIsHandledAsMissingByDatabase() throws Exception {
        ImportGraph source = createImportGraph();
        ObjectNode specification = exportedAgent(source);
        specification.put("task", "not-an-object");
        dropGraph(source);

        Team imported = importExportedDefinition(inlineRequest(MAPPER.writeValueAsString(specification),
                profileName).build());

        String generatedTask = imported.getTeamAttributes().getAgents().get(0).getTask();
        trackImportedComponent(AgentObjectType.TASK, generatedTask);
        assertThat(generatedTask).isNotBlank().isNotEqualTo(source.task().getTaskName());
        assertThat(agentClient.task(generatedTask).getTaskAttributes().getInstruction()).isNotBlank();
    }

    /**
     * Test: Import an exported graph under a profile name that does not yet exist.
     * Expected: The imported agent uses the new profile name, and the created profile has the
     * source profile provider.
     */
    @Test
    void test33105MissingProfileIsCreatedFromExportedMetadata() throws Exception {
        ImportGraph source = createImportGraph();
        String specification = source.team().exportDefinition();
        String importedProfileName = uniqueName("IMP_MISSING_PROFILE");
        trackImportedProfile(importedProfileName);
        dropGraph(source);

        importExportedDefinition(inlineRequest(specification, importedProfileName).build());

        assertThat(agentClient.agent(source.agent().getAgentName()).getAgentAttributes().getProfileName())
                .isEqualTo(importedProfileName);
        assertThat(selectAI.profile(importedProfileName).getProfileAttributes().getProvider())
                .isEqualTo(profile.getProfileAttributes().getProvider());
    }

    /**
     * Test: Import an exported graph using an existing disabled profile.
     * Expected: Import succeeds and the imported agent references the disabled profile name.
     */
    @Test
    void test33106DisabledProfileCanBeUsedForImport() throws Exception {
        ImportGraph source = createImportGraph();
        String specification = source.team().exportDefinition();
        Profile disabled = createManagedProfile(uniqueName("IMP_DISABLED_PROFILE"),
                isolatedProfileAttributes(), "Disabled import profile", ProfileStatus.DISABLED);
        assertThat(disabled.getStatus()).isEqualTo(ProfileStatus.DISABLED.getValue());
        dropGraph(source);

        importExportedDefinition(inlineRequest(specification, disabled.getProfileName()).build());
        assertThat(agentClient.agent(source.agent().getAgentName()).getAgentAttributes().getProfileName())
                .isEqualTo(disabled.getProfileName());
    }

    /**
     * Test: Import an exported graph under a different enabled profile.
     * Expected: The imported team references the source agent, that agent uses the alternate
     * profile, and the tool retains its function name.
     */
    @Test
    void test33107ImportUsesDifferentEnabledProfile() throws Exception {
        ImportGraph source = createImportGraph();
        String specification = source.team().exportDefinition();
        Profile alternate = createManagedProfile(uniqueName("IMP_ALTERNATE_PROFILE"),
                isolatedProfileAttributes(), "Alternate import profile", ProfileStatus.ENABLED);
        assertThat(alternate.getProfileName()).isNotEqualTo(profileName);
        dropGraph(source);

        Team imported = importExportedDefinition(inlineRequest(specification, alternate.getProfileName()).build());

        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getName)
                .containsExactly(source.agent().getAgentName());
        assertThat(agentClient.agent(source.agent().getAgentName()).getAgentAttributes().getProfileName())
                .isEqualTo(alternate.getProfileName());
        assertThat(agentClient.tool(source.tool().getToolName()).getToolAttributes().getFunction())
                .isEqualTo(source.functionName());
    }

    /**
     * Test: Import from a nonexistent file in Object Storage or a database directory.
     * Expected: Import fails with a SelectAIException carrying the database SQLException and
     * matching SQL error metadata.
     */
    @ParameterizedTest
    @EnumSource(value = ExportDestination.class, names = {"OBJECT_STORAGE", "DIRECTORY"})
    void test33108MissingImportFileRejectedByDatabase(ExportDestination destination) throws Exception {
        ExportTarget target = createExportTarget(destination);
        assertImportFailure(locationRequest(target).build());
    }

    /**
     * Test: Import an existing Object Storage export with no credential name or a nonexistent
     * credential name.
     * Expected: Both credential cases fail with a SelectAIException carrying the database
     * SQLException and matching SQL error metadata.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test33109MissingObjectStorageCredentialRejectedByDatabase(boolean nonexistentName) throws Exception {
        ExportTarget target = createExportTarget(ExportDestination.OBJECT_STORAGE);
        ImportGraph source = createImportGraph();
        assertThat(source.team().exportDefinition(target.request(null))).isTrue();
        assertThat(MAPPER.readTree(readExport(target)).isContainerNode()).isTrue();
        dropGraph(source);
        ImportTeamRequest request = locationRequest(target)
                .objectStorageCredentialName(nonexistentName ? uniqueName("IMP_NO_CRED") : null).build();

        assertImportFailure(request);
    }

    /**
     * Test: Import a team file from a database directory that does not exist.
     * Expected: Import fails with a SelectAIException carrying the database SQLException and
     * matching SQL error metadata.
     */
    @Test
    void test33110MissingImportDirectoryRejectedByDatabase() throws Exception {
        ImportTeamRequest request = ImportTeamRequest.builder(profileName, uniqueName("IMP_NO_DIRECTORY"))
                .location(uniqueName("IMP_MISSING_DIR") + ":team.json").build();
        assertImportFailure(request);
    }

    /**
     * Test: Import from a database directory location with no filename, with and without a
     * trailing colon.
     * Expected: Both locations fail with a SelectAIException carrying the database SQLException
     * and matching SQL error metadata.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", ":"})
    void test33111DirectoryWithoutFilenameRejectedByDatabase(String suffix) throws Exception {
        ImportTeamRequest request = ImportTeamRequest.builder(profileName, uniqueName("IMP_NO_FILENAME"))
                .location(exportDirectoryName() + suffix).build();
        assertImportFailure(request);
    }

    /**
     * Test: Import malformed JSON or a numeric JSON value from Object Storage or a database
     * directory.
     * Expected: Each file content case fails with a SelectAIException carrying the database
     * SQLException and matching SQL error metadata.
     */
    @ParameterizedTest
    @MethodSource("invalidFileSpecifications")
    void test33112InvalidFileContentRejectedByDatabase(ExportDestination destination, String specification)
            throws Exception {
        ExportTarget target = createExportTarget(destination);
        writeImportContent(target, specification);
        assertThat(readExport(target)).isEqualTo(specification);

        assertImportFailure(locationRequest(target).build());
    }

    static Stream<Arguments> invalidFileSpecifications() {
        return Stream.of(ExportDestination.OBJECT_STORAGE, ExportDestination.DIRECTORY)
                .flatMap(destination -> Stream.of(Arguments.of(destination, "{\"agents\":"),
                        Arguments.of(destination, "42")));
    }

    /**
     * Test: Export a graph to a directory, remove read access, attempt import, then restore
     * access and retry.
     * Expected: The first import fails without changing the file; after access is restored, the
     * same file imports as an enabled team.
     */
    @Test
    void test33113ImportRequiresDirectoryReadPrivilege() throws Exception {
        assumeTrue(!jdbcConnection().getMetaData().getUserName().equalsIgnoreCase(adminDbConfig().getDbUser()),
                "Directory privilege coverage requires a feature user distinct from the directory administrator");
        ExportTarget target = createExportTarget(ExportDestination.DIRECTORY);
        ImportGraph source = createImportGraph();
        assertThat(source.team().exportDefinition(target.request(null))).isTrue();
        String before = readExport(target);
        dropGraph(source);

        setExportDirectoryReadAccess(false);
        try {
            assertImportFailure(locationRequest(target).build());
        } finally {
            setExportDirectoryReadAccess(true);
        }
        assertThat(readExport(target)).isEqualTo(before);
        assertThat(importExportedDefinition(locationRequest(target).build()).getStatus())
                .isEqualTo(TeamStatus.ENABLED);
    }

    /**
     * Test: Import using an incomplete HTTPS URI or a string that is not an Object Storage URI.
     * Expected: Import fails with a SelectAIException carrying the database SQLException and
     * matching SQL error metadata.
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://", "not-an-object-storage-uri"})
    void test33114InvalidObjectStorageLocationRejectedByDatabase(String location) throws Exception {
        ImportTeamRequest request = ImportTeamRequest.builder(profileName, uniqueName("IMP_INVALID_URI"))
                .location(location).objectStorageCredentialName(exportCredentialName()).build();
        assertImportFailure(request);
    }

    /**
     * Test: Import a graph with long Unicode role and instruction text from a CLOB, Object
     * Storage, or directory.
     * Expected: The imported graph retains the exact role and instruction strings, including
     * their ending markers and component references.
     */
    @ParameterizedTest
    @EnumSource(ExportDestination.class)
    void test33115LargeImportPreservesCompleteUnicodeAttributes(ExportDestination destination) throws Exception {
        ExportTarget target = destination == ExportDestination.CLOB ? null : createExportTarget(destination);
        String role = "Import context café 東京. ".repeat(1000) + "ROLE_END_MARKER";
        String instruction = "Preserve the imported instruction. ".repeat(800) + "{query} TASK_END_MARKER";
        ImportGraph source = createImportGraph(role, instruction);
        String specification = source.team().exportDefinition();
        assertThat(specification.length()).isGreaterThan(32767);
        assertLargeExport(MAPPER.readTree(specification), source, role, instruction);
        ImportTeamRequest.Builder request = inlineRequest(specification, profileName);
        if (target != null) {
            assertThat(source.team().exportDefinition(target.request(null))).isTrue();
            assertLargeExport(MAPPER.readTree(readExport(target)), source, role, instruction);
            request = locationRequest(target);
        }
        dropGraph(source);

        Team imported = importExportedDefinition(request.build());

        assertImportedGraph(imported, source, profileName);
        assertThat(agentClient.agent(source.agent().getAgentName()).getAgentAttributes().getRole()).isEqualTo(role);
        assertThat(agentClient.task(source.task().getTaskName()).getTaskAttributes().getInstruction())
                .isEqualTo(instruction);
    }

    private void assertLargeExport(JsonNode specification, ImportGraph source, String role,
                                   String instruction) {
        assertThat(specification.isObject()).as("export is an agent specification").isTrue();
        assertThat(specification.path("name").asText()).isEqualTo(source.agent().getAgentName());
        assertThat(specification.path("component_type").asText()).isEqualTo("Agent");
        assertThat(specification.path("system_prompt").asText()).isEqualTo(role);

        JsonNode task = specification.path("task");
        assertThat(task.isObject()).as("export contains the task definition").isTrue();
        assertThat(task.path("task_name").asText()).isEqualTo(source.task().getTaskName());
        assertThat(task.path("instruction").asText()).isEqualTo(instruction);
        JsonNode taskTools = task.path("task_attributes").path("tools");
        assertThat(taskTools.isArray()).as("task tools are serialized as an array").isTrue();
        assertThat(taskTools.size()).isEqualTo(1);
        assertThat(taskTools.get(0).asText())
                .isEqualTo(source.tool().getToolName());

        JsonNode tools = specification.path("tools");
        assertThat(tools.isArray()).as("export contains referenced tools").isTrue();
        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.get(0).path("name").asText()).isEqualTo(source.tool().getToolName());
    }

    /**
     * Test: Import an agent specification with its task field removed, then export the imported
     * team again.
     * Expected: Import creates a nonblank generated task, while reexport omits that task
     * definition and name but retains the agent.
     */
    @Test
    void test33116GeneratedTaskIsCreatedButExcludedFromReexport() throws Exception {
        ImportGraph source = createImportGraph();
        ObjectNode specification = exportedAgent(source);
        assertThat(specification.remove("task")).as("source has an explicitly defined task").isNotNull();
        dropGraph(source);

        Team imported = importExportedDefinition(inlineRequest(MAPPER.writeValueAsString(specification),
                profileName).build());
        List<TeamMember> members = imported.getTeamAttributes().getAgents();
        // Register generated names before assertions so this metadata is cleaned up on failure too.
        for (TeamMember member : members) {
            if (member.getTask() != null && !member.getTask().isBlank()) {
                trackImportedComponent(AgentObjectType.TASK, member.getTask());
            }
        }
        assertThat(members).hasSize(1);
        String generatedTask = members.get(0).getTask();
        assertThat(generatedTask).isNotBlank().isNotEqualTo(source.task().getTaskName());
        assertThat(agentClient.task(generatedTask).getTaskAttributes().getInstruction()).isNotBlank();
        JsonNode reexported = MAPPER.readTree(imported.exportDefinition());
        assertThat(reexported.findValuesAsText("task_name")).doesNotContain(generatedTask);
        assertThat(reexported.findValues("task")).allSatisfy(task ->
                assertThat(task.isNull()).as("reexport omits the generated task definition").isTrue());
        assertThat(reexported.findValuesAsText("name")).contains(source.agent().getAgentName());
    }

    /**
     * Test: Import a two-member team whose tasks include an upstream dependency, supervisor, and
     * long-term memory setting.
     * Expected: Agent-to-task mappings and task/tool resources are restored; the imported team
     * keeps its process while omitted description, memory, and supervisor values are null.
     */
    @ParameterizedTest
    @EnumSource(ExportDestination.class)
    void test33117ImportPreservesMemberMappingsAndTaskDependencies(ExportDestination destination)
            throws Exception {
        ExportTarget target = destination == ExportDestination.CLOB ? null : createExportTarget(destination);
        ImportGraph source = createImportGraph();
        Tool sqlTool = agentClient.tool(uniqueName("IMP_SQL_TOOL"), sqlToolAttributes(),
                "Java SQL tool test", ToolStatus.ENABLED);
        sqlTool.create();
        managedTools.add(sqlTool);
        Task upstream = agentClient.task(uniqueName("IMP_UPSTREAM_TASK"),
                taskAttributes(sqlTool.getToolName()), "Java task test", TaskStatus.ENABLED);
        upstream.create();
        managedTasks.add(upstream);
        Agent worker = agentClient.agent(uniqueName("IMP_UPSTREAM_AGENT"), agentAttributes(),
                "Java agent test", AgentStatus.ENABLED);
        worker.create();
        managedAgents.add(worker);
        assertThat(source.team().drop(true)).isTrue();
        managedTeams.remove(source.team());
        // Recreate only this test's downstream task with an explicit upstream dependency.
        assertThat(source.task().drop(true)).isTrue();
        managedTasks.remove(source.task());
        Task downstream = agentClient.task(source.task().getTaskName(), TaskAttributes.builder()
                .instruction("Summarize the upstream result for {query}")
                .tools(List.of(source.tool().getToolName())).input(List.of(upstream.getTaskName()))
                .enableHumanTool(false).build(), "Dependent import task", TaskStatus.ENABLED);
        downstream.create();
        managedTasks.add(downstream);
        Agent supervisor = agentClient.agent(uniqueName("IMP_SUPERVISOR"), AgentAttributes.builder()
                .profileName(profileName).role("Coordinate these import workers")
                .supervisor(true).enableHumanTool(false).shortTermMemoryLength(9).build(),
                "Import supervisor", AgentStatus.ENABLED);
        supervisor.create();
        managedAgents.add(supervisor);
        Team team = agentClient.team(uniqueName("IMP_DETAILED_TEAM"), TeamAttributes.builder()
                .agents(List.of(TeamMember.builder(worker.getAgentName()).task(upstream.getTaskName()).build(),
                        TeamMember.builder(source.agent().getAgentName()).task(downstream.getTaskName()).build()))
                .process("sequential").longTermMemoryLength(17).supervisorAgent(supervisor.getAgentName()).build(),
                "Detailed import team", TeamStatus.ENABLED);
        team.create();
        managedTeams.add(team);
        ImportTeamRequest.Builder request = exportRequest(team, target);
        trackImportedComponents(worker.getAgentName(), upstream.getTaskName(), sqlTool.getToolName());
        assertThat(team.drop(true)).isTrue();
        managedTeams.remove(team);
        dropGraph(new ImportGraph(source.team(), source.agent(), downstream, source.tool(), source.functionName()));
        assertThat(worker.drop(true)).isTrue();
        managedAgents.remove(worker);
        assertThat(supervisor.drop(true)).isTrue();
        managedAgents.remove(supervisor);
        assertThat(upstream.drop(true)).isTrue();
        managedTasks.remove(upstream);
        assertThat(sqlTool.drop(true)).isTrue();
        managedTools.remove(sqlTool);

        Team imported = importExportedDefinition(request.build());

        assertThat(imported.getStatus()).isEqualTo(TeamStatus.ENABLED);
        assertThat(imported.getDescription()).isNull();
        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getName)
                .containsExactly(worker.getAgentName(), source.agent().getAgentName());
        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getTask)
                .containsExactly(upstream.getTaskName(), downstream.getTaskName());
        assertThat(imported.getTeamAttributes().getProcess()).isEqualTo("sequential");
        assertThat(imported.getTeamAttributes().getLongTermMemoryLength()).isNull();
        assertThat(imported.getTeamAttributes().getSupervisorAgent()).isNull();
        assertImportedAgent(worker, profileName);
        assertImportedAgent(source.agent(), profileName);
        assertImportedTask(upstream);
        assertImportedTask(downstream);
        assertImportedTool(sqlTool);
        assertImportedTool(source.tool());
        assertThat(agentClient.listAgents("^" + supervisor.getAgentName() + "$")).isEmpty();
    }

    private ImportGraph createImportGraph() throws Exception {
        return createImportGraph("Import worker with explicit settings", "Use the tool to answer {query}");
    }

    private ImportGraph createImportGraph(String role, String instruction) throws Exception {
        String function = createPythonPlsqlFunction();
        Tool tool = agentClient.tool(uniqueName("IMP_CUSTOM_TOOL"), ToolAttributes.builder()
                .function(function).instruction("Compute age from the supplied birth date")
                .toolInputs(List.of(ToolInput.builder()
                        .name("p_birth_date").description("Birth date input").build()))
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        Task task = agentClient.task(uniqueName("IMP_TASK"), TaskAttributes.builder()
                .instruction(instruction).tools(List.of(tool.getToolName())).enableHumanTool(false).build(),
                "Imported task description", TaskStatus.ENABLED);
        task.create();
        managedTasks.add(task);
        Agent agent = agentClient.agent(uniqueName("IMP_AGENT"), AgentAttributes.builder()
                .profileName(profileName).role(role).enableHumanTool(false).shortTermMemoryLength(7)
                .tools(List.of(tool.getToolName())).build(), "Imported agent description", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Team team = createTeam(uniqueName("IMP_SOURCE_TEAM"), agent.getAgentName(), task.getTaskName());
        return new ImportGraph(team, agent, task, tool, function);
    }

    private ObjectNode exportedAgent(ImportGraph graph) throws Exception {
        JsonNode specification = MAPPER.readTree(graph.team().exportDefinition());
        assertThat(specification.isObject()).as("single-worker export is an agent specification").isTrue();
        return (ObjectNode) specification;
    }

    private ImportTeamRequest.Builder inlineRequest(String specification, String importProfile) {
        return ImportTeamRequest.builder(importProfile, uniqueName("IMP_TARGET_TEAM")).specification(specification);
    }

    private ImportTeamRequest.Builder locationRequest(ExportTarget target) {
        return ImportTeamRequest.builder(profileName, uniqueName("IMP_TARGET_TEAM"))
                .location(target.location()).objectStorageCredentialName(target.credentialName());
    }

    private ImportTeamRequest.Builder exportRequest(Team team, ExportTarget target) throws Exception {
        if (target == null) {
            return inlineRequest(team.exportDefinition(), profileName);
        }
        assertThat(team.exportDefinition(target.request(null))).isTrue();
        return locationRequest(target);
    }

    private void assertImportedGraph(Team imported, ImportGraph source, String importProfile)
            throws Exception {
        assertThat(imported.getStatus()).isEqualTo(TeamStatus.ENABLED);
        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getName)
                .containsExactly(source.agent().getAgentName());
        assertThat(imported.getTeamAttributes().getAgents()).extracting(TeamMember::getTask)
                .containsExactly(source.task().getTaskName());
        assertImportedAgent(source.agent(), importProfile);
        assertImportedTask(source.task());
        assertImportedTool(source.tool());
    }

    private void assertImportedAgent(Agent expected, String importProfile) throws Exception {
        Agent actual = agentClient.agent(expected.getAgentName());
        assertThat(actual.getDescription()).isEqualTo(expected.getDescription());
        assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
        assertThat(actual.getAgentAttributes().getProfileName()).isEqualTo(importProfile);
        assertThat(actual.getAgentAttributes().getRole())
                .isEqualTo(expected.getAgentAttributes().getRole());
        assertThat(actual.getAgentAttributes().getEnableHumanTool()).isTrue();
    }

    private void assertImportedTask(Task expected) throws Exception {
        Task actual = agentClient.task(expected.getTaskName());
        assertThat(actual.getDescription()).isEqualTo(expected.getTaskAttributes().getInstruction());
        assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
        assertThat(actual.getTaskAttributes().getInstruction())
                .isEqualTo(expected.getTaskAttributes().getInstruction());
        assertThat(actual.getTaskAttributes().getTools()).isEqualTo(expected.getTaskAttributes().getTools());
        assertThat(actual.getTaskAttributes().getInput()).isEqualTo(expected.getTaskAttributes().getInput());
        assertThat(actual.getTaskAttributes().getEnableHumanTool()).isTrue();
    }

    private void assertImportedTool(Tool expected) throws Exception {
        Tool actual = agentClient.tool(expected.getToolName());
        assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
        assertThat(actual.getToolAttributes().getInstruction())
                .isEqualTo(expected.getToolAttributes().getInstruction());
        if (expected.getToolAttributes().getFunction() != null) {
            assertThat(actual.getToolAttributes().getFunction())
                    .isEqualTo(expected.getToolAttributes().getFunction());
        }
    }

    private void dropGraph(ImportGraph graph) throws Exception {
        trackImportedComponents(graph.agent().getAgentName(), graph.task().getTaskName(), graph.tool().getToolName());
        if (managedTeams.contains(graph.team())) {
            assertThat(graph.team().drop(true)).isTrue();
            managedTeams.remove(graph.team());
        }
        assertThat(graph.agent().drop(true)).isTrue();
        managedAgents.remove(graph.agent());
        assertThat(graph.task().drop(true)).isTrue();
        managedTasks.remove(graph.task());
        assertThat(graph.tool().drop(true)).isTrue();
        managedTools.remove(graph.tool());
    }

    private void createConflictingComponent(ImportGraph graph, AgentObjectType type) throws Exception {
        switch (type) {
            case AGENT -> {
                Agent agent = agentClient.agent(graph.agent().getAgentName(), AgentAttributes.builder()
                        .profileName(profileName).role(SENTINEL).enableHumanTool(false).build(),
                        SENTINEL, AgentStatus.DISABLED);
                agent.create();
                managedAgents.add(agent);
            }
            case TASK -> {
                Task task = agentClient.task(graph.task().getTaskName(), TaskAttributes.builder()
                        .instruction(SENTINEL).build(), SENTINEL, TaskStatus.DISABLED);
                task.create();
                managedTasks.add(task);
            }
            case TOOL -> {
                Tool tool = agentClient.tool(graph.tool().getToolName(), ToolAttributes.builder()
                        .function(graph.functionName()).instruction(SENTINEL).build(), SENTINEL, ToolStatus.DISABLED);
                tool.create();
                managedTools.add(tool);
            }
            default -> throw new IllegalArgumentException("Expected an agent, task, or tool conflict");
        }
    }

    private static String componentName(ImportGraph graph, AgentObjectType type) {
        return switch (type) {
            case AGENT -> graph.agent().getAgentName();
            case TASK -> graph.task().getTaskName();
            case TOOL -> graph.tool().getToolName();
            default -> throw new IllegalArgumentException("Expected an agent, task, or tool conflict");
        };
    }

    private ResourceSnapshot snapshot(AgentObjectType type, String name) throws Exception {
        return switch (type) {
            case AGENT -> {
                Agent agent = agentClient.agent(name);
                yield new ResourceSnapshot(type, name, agent.getDescription(),
                        MAPPER.readTree(agent.getAgentAttributes().toJson()), agent.getStatus().getValue());
            }
            case TASK -> {
                Task task = agentClient.task(name);
                yield new ResourceSnapshot(type, name, task.getDescription(),
                        MAPPER.readTree(task.getTaskAttributes().toJson()), task.getStatus().getValue());
            }
            case TOOL -> {
                Tool tool = agentClient.tool(name);
                yield new ResourceSnapshot(type, name, tool.getDescription(),
                        MAPPER.readTree(tool.getToolAttributes().toJson()), tool.getStatus().getValue());
            }
            case TEAM -> {
                Team team = agentClient.team(name);
                yield new ResourceSnapshot(type, name, team.getDescription(),
                        MAPPER.readTree(team.getTeamAttributes().toJson()), team.getStatus().getValue());
            }
        };
    }

    private void assertSnapshot(ResourceSnapshot expected) throws Exception {
        assertThat(snapshot(expected.type(), expected.name())).isEqualTo(expected);
    }

    private long functionObjectId(String name) throws Exception {
        try (PreparedStatement statement = jdbcConnection().prepareStatement(
                "SELECT object_id FROM USER_OBJECTS WHERE object_name = ? AND object_type = 'FUNCTION'")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).as("supporting function exists").isTrue();
                return rows.getLong(1);
            }
        }
    }

    private void assertImportFailure(ImportTeamRequest request) throws Exception {
        // The inherited helper registers the target before calling the SDK, even for partial failures.
        assertThatThrownBy(() -> importExportedDefinition(request))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    SQLException cause = (SQLException) exception.getCause();
                    assertThat(cause.getMessage()).contains("ORA-");
                    assertThat(cause.getErrorCode()).isNotZero();
                    assertThat(exception.getErrorCode()).isEqualTo(cause.getErrorCode());
                    assertThat(exception.getSqlState()).isEqualTo(cause.getSQLState());
                });
    }

    private record ImportGraph(Team team, Agent agent, Task task, Tool tool, String functionName) { }

    private record ResourceSnapshot(AgentObjectType type, String name, String description,
                                    JsonNode attributes, String status) { }
}
