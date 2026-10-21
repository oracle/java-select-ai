/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamExecutionState;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.sql.DataSource;
import javax.sql.rowset.serial.SerialClob;
import java.io.File;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Offline sample tests: temporary compilation, mocked SDKs, and a failing JDBC driver. */
class AgentSamplesTest {
    @TempDir
    static Path compiledSamples;
    private static URLClassLoader sampleLoader;
    private static Method workflow;
    private static Method supervisorWorkflow;
    private static Method pooledStart;
    private static Method pooledResume;
    private static String classPath;

    private SelectAIAgent agentClient;
    private SelectAI coreClient;
    private Tool tool;
    private Task task;
    private Agent agent;
    private Team team;
    private Conversation conversation;

    @BeforeAll
    static void compileSamplesInTemporaryDirectory() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Sample tests require a JDK").isNotNull();
        classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var sourceFiles = Files.list(Path.of(
                "samples/src/main/java/com/oracle/database/selectai/samples/agent"));
             var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<File> sources = sourceFiles.filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile).toList();
            boolean compiled = compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "17", "-classpath", classPath,
                            "-d", compiledSamples.toString()), null,
                    manager.getJavaFileObjectsFromFiles(sources)).call();
            assertThat(compiled).withFailMessage("Sample compilation failed: %s",
                    diagnostics.getDiagnostics()).isTrue();
        }
        sampleLoader = new URLClassLoader(new URL[] {compiledSamples.toUri().toURL()},
                AgentSamplesTest.class.getClassLoader());
        workflow = sampleLoader.loadClass("com.oracle.database.selectai.samples.agent.AgentWorkflowSample")
                .getDeclaredMethod("run", SelectAIAgent.class, SelectAI.class,
                        String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class);
        workflow.setAccessible(true);
        supervisorWorkflow = sampleLoader.loadClass(
                "com.oracle.database.selectai.samples.agent.SupervisorTeamSample")
                .getDeclaredMethod("run", SelectAIAgent.class, SelectAI.class,
                        String.class, String.class, String.class);
        supervisorWorkflow.setAccessible(true);
        Class<?> pooledSample = sampleLoader.loadClass(
                "com.oracle.database.selectai.samples.agent.PooledTeamResumeSample");
        pooledStart = pooledSample.getDeclaredMethod("start", DataSource.class, SelectAIOptions.class,
                String.class, String.class);
        pooledResume = pooledSample.getDeclaredMethod("resume", DataSource.class, SelectAIOptions.class,
                String.class, String.class, String.class);
        pooledStart.setAccessible(true);
        pooledResume.setAccessible(true);
    }

    @AfterAll
    static void closeSampleLoader() throws Exception {
        if (sampleLoader != null) {
            sampleLoader.close();
        }
    }

    @BeforeEach
    void configureWorkflow() throws Exception {
        agentClient = mock(SelectAIAgent.class);
        coreClient = mock(SelectAI.class);
        tool = mock(Tool.class);
        task = mock(Task.class);
        agent = mock(Agent.class);
        team = mock(Team.class);
        conversation = mock(Conversation.class);
        when(agentClient.tool(eq("TOOL"), any(ToolAttributes.class), anyString(), eq(ToolStatus.ENABLED)))
                .thenReturn(tool);
        when(agentClient.task(eq("TASK"), any(TaskAttributes.class), anyString(), eq(TaskStatus.ENABLED)))
                .thenReturn(task);
        when(agentClient.agent(eq("AGENT"), any(AgentAttributes.class), anyString(), eq(AgentStatus.ENABLED)))
                .thenReturn(agent);
        when(agentClient.team(eq("TEAM"), any(TeamAttributes.class), anyString(), eq(TeamStatus.ENABLED)))
                .thenReturn(team);
        when(coreClient.conversation(any(ConversationAttributes.class))).thenReturn(conversation);
        when(tool.create()).thenReturn(true);
        when(task.create()).thenReturn(true);
        when(agent.create()).thenReturn(true);
        when(team.create()).thenReturn(true);
        when(conversation.create()).thenReturn("C1");
        when(team.run(any(RunTeamRequest.class))).thenReturn(new TeamRunResult("First"),
                new TeamRunResult("Continued"));
    }

    @Test
    void workflowReusesConversationAndCleansUpEveryCreatedResource() throws Throwable {
        runWorkflow();

        ArgumentCaptor<RunTeamRequest> requests = ArgumentCaptor.forClass(RunTeamRequest.class);
        verify(team, times(2)).run(requests.capture());
        assertThat(requests.getAllValues()).extracting(RunTeamRequest::getConversationId)
                .containsExactly("C1", "C1");
        assertThat(requests.getAllValues()).extracting(RunTeamRequest::getUserPrompt)
                .containsExactly("First prompt", "Next prompt");
        verifyCleanup();
        verify(agentClient, never()).close();
        verify(coreClient, never()).close();
    }

    @Test
    void workflowPreservesRunFailureAndAggregatesCleanupFailures() throws Exception {
        SelectAIException runFailure = new SelectAIException("Run failed");
        SelectAIException teamFailure = new SelectAIException("Team cleanup failed");
        SelectAIException toolFailure = new SelectAIException("Tool cleanup failed");
        when(team.run(any(RunTeamRequest.class))).thenThrow(runFailure);
        when(team.drop(false)).thenThrow(teamFailure);
        when(tool.drop(false)).thenThrow(toolFailure);

        assertThatThrownBy(this::runWorkflow).isSameAs(runFailure);
        assertThat(runFailure.getSuppressed()).hasSize(1);
        Throwable cleanupFailure = runFailure.getSuppressed()[0];
        assertThat(cleanupFailure).hasCause(teamFailure).hasMessageContaining("TEAM");
        assertThat(cleanupFailure.getSuppressed()).hasSize(1);
        assertThat(cleanupFailure.getSuppressed()[0]).hasCause(toolFailure).hasMessageContaining("TOOL");
        verifyCleanup();
    }

    @Test
    void cleanupOnlyFailurePropagatesAfterEveryDropIsAttempted() throws Exception {
        SelectAIException failure = new SelectAIException("Conversation cleanup failed");
        when(conversation.drop(false)).thenThrow(failure);

        assertThatThrownBy(this::runWorkflow).isInstanceOf(SelectAIException.class)
                .hasCause(failure).hasMessageContaining("conversation 'C1'");
        verifyCleanup();
    }

    @Test
    void partialCreationOnlyDropsSuccessfullyCreatedResources() throws Exception {
        SelectAIException failure = new SelectAIException("Task creation failed");
        when(task.create()).thenThrow(failure);

        assertThatThrownBy(this::runWorkflow).isSameAs(failure);
        verify(tool).drop(false);
        verify(task, never()).drop(false);
        verify(agent, never()).drop(false);
        verify(team, never()).drop(false);
        verify(conversation, never()).drop(false);
    }

    @Test
    void failedFirstCreationDoesNotDropAnExistingResource() throws Exception {
        SelectAIException failure = new SelectAIException("Tool already exists");
        when(tool.create()).thenThrow(failure);

        assertThatThrownBy(this::runWorkflow).isSameAs(failure);
        verify(tool, never()).drop(false);
        verify(task, never()).create();
    }

    @Test
    void conversationCreationFailureStillCleansUpTeamResources() throws Exception {
        SelectAIException failure = new SelectAIException("Conversation creation failed");
        when(conversation.create()).thenThrow(failure);

        assertThatThrownBy(this::runWorkflow).isSameAs(failure);
        verify(conversation, never()).drop(false);
        verify(team).drop(false);
        verify(agent).drop(false);
        verify(task).drop(false);
        verify(tool).drop(false);
    }

    @Test
    void uncheckedCleanupFailureDoesNotPreventRemainingDrops() throws Exception {
        when(team.drop(false)).thenThrow(new IllegalStateException("Invalid handle"));

        assertThatThrownBy(this::runWorkflow).isInstanceOf(SelectAIException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyCleanup();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ListAgentsSample", "RunTeamSample", "DescribeTeamSample", "AgentWorkflowSample",
            "SupervisorTeamSample"})
    void standaloneDatabaseFailureExitsUnsuccessfully(String sample) throws Exception {
        ProcessResult result = runSampleProcess(sample);
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Offline sample JDBC failure");
    }

    @Test
    void supervisorWorkflowAssignsSupervisorSeparatelyFromWorkers() throws Throwable {
        SupervisorFixture fixture = new SupervisorFixture();
        fixture.run();

        ArgumentCaptor<AgentAttributes> agents = ArgumentCaptor.forClass(AgentAttributes.class);
        verify(fixture.client, times(3)).agent(anyString(), agents.capture(), anyString(), eq(AgentStatus.ENABLED));
        assertThat(agents.getAllValues()).extracting(AgentAttributes::getSupervisor)
                .containsExactly(false, false, true);
        assertThat(agents.getAllValues()).extracting(AgentAttributes::getProfileName)
                .containsOnly("PROFILE");
        assertThat(agents.getAllValues()).extracting(AgentAttributes::getEnableHumanTool)
                .containsOnly(false);

        ArgumentCaptor<TaskAttributes> tasks = ArgumentCaptor.forClass(TaskAttributes.class);
        verify(fixture.client, times(2)).task(anyString(), tasks.capture(), anyString(), eq(TaskStatus.ENABLED));
        assertThat(tasks.getAllValues()).allSatisfy(attributes -> {
            assertThat(attributes.getTools()).containsExactly("SUP_SQL");
            assertThat(attributes.getEnableHumanTool()).isFalse();
            assertThat(attributes.getInstruction()).contains("{query}", "DUAL");
        });

        ArgumentCaptor<TeamAttributes> attributes = ArgumentCaptor.forClass(TeamAttributes.class);
        verify(fixture.client).team(eq("SUP_TEAM"), attributes.capture(), anyString(), eq(TeamStatus.ENABLED));
        assertThat(attributes.getValue().getSupervisorAgent()).isEqualTo("SUP_SUPERVISOR");
        assertThat(attributes.getValue().getProcess()).isEqualTo("sequential");
        assertThat(attributes.getValue().getAgents()).extracting(TeamAttributes.TeamMember::getName)
                .containsExactly("SUP_REV_AGENT", "SUP_MARGIN_AGENT");
        assertThat(attributes.getValue().getAgents()).extracting(TeamAttributes.TeamMember::getTask)
                .containsExactly("SUP_REV_TASK", "SUP_MARGIN_TASK");

        ArgumentCaptor<RunTeamRequest> request = ArgumentCaptor.forClass(RunTeamRequest.class);
        verify(fixture.team).run(request.capture());
        assertThat(request.getValue().getConversationId()).isEqualTo("SUP_C1");
        assertThat(request.getValue().getUserPrompt()).isEqualTo("Synthetic figures");
        fixture.verifyCleanup();
        verify(fixture.client, never()).close();
        verify(fixture.core, never()).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tool", "revenueTask", "marginTask", "revenueAgent", "marginAgent",
            "supervisor", "team", "conversation"})
    void supervisorCreationFailureOnlyCleansUpResourcesCreatedBySample(String stage) throws Exception {
        SupervisorFixture fixture = new SupervisorFixture();
        SelectAIException failure = new SelectAIException("Creation failed: " + stage);
        switch (stage) {
            case "tool" -> when(fixture.tool.create()).thenThrow(failure);
            case "revenueTask" -> when(fixture.revenueTask.create()).thenThrow(failure);
            case "marginTask" -> when(fixture.marginTask.create()).thenThrow(failure);
            case "revenueAgent" -> when(fixture.revenueAgent.create()).thenThrow(failure);
            case "marginAgent" -> when(fixture.marginAgent.create()).thenThrow(failure);
            case "supervisor" -> when(fixture.supervisor.create()).thenThrow(failure);
            case "team" -> when(fixture.team.create()).thenThrow(failure);
            case "conversation" -> when(fixture.conversation.create()).thenThrow(failure);
            default -> throw new IllegalArgumentException(stage);
        }

        assertThatThrownBy(fixture::run).isSameAs(failure);
        int failedIndex = List.of("tool", "revenueTask", "marginTask", "revenueAgent", "marginAgent",
                "supervisor", "team", "conversation").indexOf(stage);
        verify(fixture.tool, times(failedIndex > 0 ? 1 : 0)).drop(false);
        verify(fixture.revenueTask, times(failedIndex > 1 ? 1 : 0)).drop(false);
        verify(fixture.marginTask, times(failedIndex > 2 ? 1 : 0)).drop(false);
        verify(fixture.revenueAgent, times(failedIndex > 3 ? 1 : 0)).drop(false);
        verify(fixture.marginAgent, times(failedIndex > 4 ? 1 : 0)).drop(false);
        verify(fixture.supervisor, times(failedIndex > 5 ? 1 : 0)).drop(false);
        verify(fixture.team, times(failedIndex > 6 ? 1 : 0)).drop(false);
        verify(fixture.conversation, never()).drop(false);
        verify(fixture.team, never()).run(any(RunTeamRequest.class));
    }

    @Test
    void supervisorRunFailurePreservesAllCleanupFailures() throws Exception {
        SupervisorFixture fixture = new SupervisorFixture();
        SelectAIException runFailure = new SelectAIException("Run failed");
        SelectAIException conversationFailure = new SelectAIException("Conversation drop failed");
        IllegalStateException supervisorFailure = new IllegalStateException("Supervisor drop failed");
        when(fixture.team.run(any(RunTeamRequest.class))).thenThrow(runFailure);
        when(fixture.conversation.drop(false)).thenThrow(conversationFailure);
        when(fixture.supervisor.drop(false)).thenThrow(supervisorFailure);

        assertThatThrownBy(fixture::run).isSameAs(runFailure);
        assertThat(runFailure.getSuppressed()).hasSize(1);
        Throwable cleanupFailure = runFailure.getSuppressed()[0];
        assertThat(cleanupFailure).hasCause(conversationFailure);
        assertThat(cleanupFailure.getSuppressed()).hasSize(1);
        assertThat(cleanupFailure.getSuppressed()[0]).hasCause(supervisorFailure);
        fixture.verifyCleanup();
    }

    @Test
    void supervisorCleanupFailurePropagatesAfterRemainingDrops() throws Exception {
        SupervisorFixture fixture = new SupervisorFixture();
        SelectAIException failure = new SelectAIException("Team drop failed");
        when(fixture.team.drop(false)).thenThrow(failure);

        assertThatThrownBy(fixture::run).isInstanceOf(SelectAIException.class)
                .hasCause(failure).hasMessageContaining("SUP_TEAM");
        fixture.verifyCleanup();
    }

    @Test
    void offlineParameterSamplePreservesDecimalsAndBuilderSnapshots() throws Exception {
        ProcessResult result = runSampleProcess("RunTeamParamsSample");
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("1.234567890123456789",
                "First prompt: Review the amount.", "Next prompt: Explain the calculation.");
    }

    @Test
    void pooledResumeReusesIdAcrossNewClientsAndDifferentBorrowedConnections() throws Throwable {
        DataSource firstPool = mock(DataSource.class);
        DataSource nextPool = mock(DataSource.class);
        JdbcCall create = jdbcCall("C1");
        JdbcCall firstRun = jdbcCall(null);
        when(firstRun.statement().getClob(1)).thenReturn(new SerialClob("Please approve.".toCharArray()));
        JdbcCall firstState = jdbcCall("WAITING_FOR_HUMAN");
        JdbcCall checkWaiting = jdbcCall("WAITING_FOR_HUMAN");
        JdbcCall resumeRun = jdbcCall(null);
        when(resumeRun.statement().getClob(1)).thenReturn(new SerialClob("Approved.".toCharArray()));
        JdbcCall finalState = jdbcCall("SUCCEEDED");
        when(firstPool.getConnection()).thenReturn(create.connection(), firstRun.connection(),
                firstState.connection());
        when(nextPool.getConnection()).thenReturn(checkWaiting.connection(), resumeRun.connection(),
                finalState.connection());

        String id = (String) invokeSample(pooledStart, firstPool, sampleOptions(), "OWNER.TEAM", "Ask for approval");
        assertThat(id).isEqualTo("C1");
        // The first request has returned every connection before a human responds.
        for (JdbcCall call : List.of(create, firstRun, firstState)) {
            verifyClosed(call);
        }
        Object state = invokeSample(pooledResume, nextPool, sampleOptions(), "OWNER.TEAM", id, "Approved");
        assertThat(state).isEqualTo(TeamExecutionState.SUCCEEDED);

        for (JdbcCall call : List.of(firstRun, resumeRun)) {
            verify(call.statement()).setString(2, "OWNER.TEAM");
            assertThat(boundClob(call.statement(), 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
            verify(call.connection()).prepareCall(org.mockito.ArgumentMatchers.contains(".RUN_TEAM("));
        }
        assertThat(boundClob(firstRun.statement(), 3)).isEqualTo("Ask for approval");
        assertThat(boundClob(resumeRun.statement(), 3)).isEqualTo("Approved");
        for (JdbcCall call : List.of(firstState, checkWaiting, finalState)) {
            verify(call.statement()).setString(2, "OWNER.TEAM");
            assertThat(boundClob(call.statement(), 3)).isEqualTo("{\"conversation_id\":\"C1\"}");
            verify(call.connection()).prepareCall(org.mockito.ArgumentMatchers.contains(".GET_TEAM_STATE("));
        }
        for (JdbcCall call : List.of(checkWaiting, resumeRun, finalState)) {
            verifyClosed(call);
        }
        verify(firstPool, times(3)).getConnection();
        verify(nextPool, times(3)).getConnection();
        verifyNoMoreInteractions(firstPool, nextPool);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCEEDED", "RUNNING", "FAILED", "FUTURE_STATE"})
    void pooledResumeRejectsNonWaitingStateWithoutRunningTeam(String state) throws Exception {
        DataSource pool = mock(DataSource.class);
        JdbcCall check = jdbcCall(state);
        when(pool.getConnection()).thenReturn(check.connection());

        assertThatThrownBy(() -> invokeSample(pooledResume, pool, sampleOptions(), "TEAM", "C1", "Approved"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Expected WAITING_FOR_HUMAN");
        verify(pool).getConnection();
        verify(check.connection()).prepareCall(org.mockito.ArgumentMatchers.contains(".GET_TEAM_STATE("));
        verifyClosed(check);
        verifyNoMoreInteractions(pool);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCEEDED", "RUNNING", "FAILED", "FUTURE_STATE"})
    void pooledStartDoesNotMistakeOrdinaryContinuationForHumanResume(String state) throws Exception {
        DataSource pool = mock(DataSource.class);
        JdbcCall create = jdbcCall("C1");
        JdbcCall run = jdbcCall(null);
        JdbcCall check = jdbcCall(state);
        when(pool.getConnection()).thenReturn(create.connection(), run.connection(), check.connection());

        assertThatThrownBy(() -> invokeSample(pooledStart, pool, sampleOptions(), "TEAM", "Ask for approval"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Expected WAITING_FOR_HUMAN");
        verify(pool, times(3)).getConnection();
        for (JdbcCall call : List.of(create, run, check)) {
            verifyClosed(call);
        }
        verifyNoMoreInteractions(pool);
    }

    @Test
    void pooledResumeCanPauseForAnotherHumanResponse() throws Throwable {
        DataSource pool = mock(DataSource.class);
        JdbcCall before = jdbcCall("WAITING_FOR_HUMAN");
        JdbcCall run = jdbcCall(null);
        JdbcCall after = jdbcCall("WAITING_FOR_HUMAN");
        when(pool.getConnection()).thenReturn(before.connection(), run.connection(), after.connection());

        assertThat(invokeSample(pooledResume, pool, sampleOptions(), "TEAM", "C1", "First answer"))
                .isEqualTo(TeamExecutionState.WAITING_FOR_HUMAN);
        assertThat(boundClob(run.statement(), 4)).isEqualTo("{\"conversation_id\":\"C1\"}");
        verify(pool, times(3)).getConnection();
        for (JdbcCall call : List.of(before, run, after)) {
            verifyClosed(call);
        }
        verifyNoMoreInteractions(pool);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "RUNNING", "FUTURE_STATE"})
    void pooledResumeDoesNotReportUnexpectedFinalStateAsSuccess(String state) throws Exception {
        DataSource pool = mock(DataSource.class);
        JdbcCall before = jdbcCall("WAITING_FOR_HUMAN");
        JdbcCall run = jdbcCall(null);
        JdbcCall after = jdbcCall(state);
        when(pool.getConnection()).thenReturn(before.connection(), run.connection(), after.connection());

        assertThatThrownBy(() -> invokeSample(pooledResume, pool, sampleOptions(), "TEAM", "C1", "Approved"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected state after resume");
        verify(pool, times(3)).getConnection();
        for (JdbcCall call : List.of(before, run, after)) {
            verifyClosed(call);
        }
        verifyNoMoreInteractions(pool);
    }

    @Test
    void pooledResumePropagatesFailureWithoutRetryAndReturnsConnections() throws Exception {
        DataSource pool = mock(DataSource.class);
        JdbcCall before = jdbcCall("WAITING_FOR_HUMAN");
        JdbcCall run = jdbcCall(null);
        SQLException failure = new SQLException("Synthetic resume failure", "99999", 20053);
        when(run.statement().execute()).thenThrow(failure);
        when(pool.getConnection()).thenReturn(before.connection(), run.connection());

        assertThatThrownBy(() -> invokeSample(pooledResume, pool, sampleOptions(), "TEAM", "C1", "Approved"))
                .isInstanceOf(SelectAIException.class).hasCause(failure);
        verify(run.statement()).execute();
        verify(pool, times(2)).getConnection();
        verifyClosed(before);
        verifyClosed(run);
        verifyNoMoreInteractions(pool);
    }

    private static Object invokeSample(Method method, Object... arguments) throws Throwable {
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static SelectAIOptions sampleOptions() {
        return SelectAIOptions.builder().queryTimeoutSeconds(30).build();
    }

    private static JdbcCall jdbcCall(String result) throws SQLException {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connection.prepareCall(anyString())).thenReturn(statement);
        when(statement.getString(1)).thenReturn(result);
        return new JdbcCall(connection, statement);
    }

    private static String boundClob(CallableStatement statement, int index) throws Exception {
        ArgumentCaptor<Reader> reader = ArgumentCaptor.forClass(Reader.class);
        verify(statement).setCharacterStream(eq(index), reader.capture(), anyInt());
        StringWriter writer = new StringWriter();
        reader.getValue().transferTo(writer);
        return writer.toString();
    }

    private static void verifyClosed(JdbcCall call) throws SQLException {
        verify(call.statement()).setQueryTimeout(30);
        verify(call.statement()).close();
        verify(call.connection()).close();
    }

    private record JdbcCall(Connection connection, CallableStatement statement) { }

    private void runWorkflow() throws Throwable {
        try {
            workflow.invoke(null, agentClient, coreClient, "PROFILE", "TOOL", "TASK", "AGENT", "TEAM",
                    "First prompt", "Next prompt");
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void verifyCleanup() throws Exception {
        var order = inOrder(conversation, team, agent, task, tool);
        order.verify(conversation).drop(false);
        order.verify(team).drop(false);
        order.verify(agent).drop(false);
        order.verify(task).drop(false);
        order.verify(tool).drop(false);
    }

    private ProcessResult runSampleProcess(String sample) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Path output = Files.createTempFile(compiledSamples, "sample-process-", ".log");
        ProcessBuilder builder = new ProcessBuilder(java, "-cp",
                classPath + File.pathSeparator + compiledSamples,
                AgentSampleFailureProcess.class.getName(), sample)
                .redirectErrorStream(true).redirectOutput(output.toFile());
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(name -> name.startsWith("SELECTAI_")
                || List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").contains(name));
        environment.putAll(Map.of("SELECTAI_DB_USER", "sample_user", "SELECTAI_DB_PASSWORD", "dummy",
                "SELECTAI_JDBC_URL", "jdbc:oracle:thin:@offline-sample",
                "SELECTAI_PROFILE_NAME", "PROFILE", "SELECTAI_AGENT_TEAM_NAME", "TEAM",
                "SELECTAI_AGENT_TEAM_CONVERSATION_ID", "C1"));
        Process process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("Sample process completed").isTrue();
            return new ProcessResult(process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    /** Independent resources make supervisor-to-worker wiring and cleanup order observable. */
    private static final class SupervisorFixture {
        private final SelectAIAgent client = mock(SelectAIAgent.class);
        private final SelectAI core = mock(SelectAI.class);
        private final Tool tool = mock(Tool.class);
        private final Task revenueTask = mock(Task.class);
        private final Task marginTask = mock(Task.class);
        private final Agent revenueAgent = mock(Agent.class);
        private final Agent marginAgent = mock(Agent.class);
        private final Agent supervisor = mock(Agent.class);
        private final Team team = mock(Team.class);
        private final Conversation conversation = mock(Conversation.class);

        private SupervisorFixture() throws SelectAIException {
            when(client.tool(eq("SUP_SQL"), any(ToolAttributes.class), anyString(), eq(ToolStatus.ENABLED)))
                    .thenReturn(tool);
            when(client.task(eq("SUP_REV_TASK"), any(TaskAttributes.class), anyString(), eq(TaskStatus.ENABLED)))
                    .thenReturn(revenueTask);
            when(client.task(eq("SUP_MARGIN_TASK"), any(TaskAttributes.class), anyString(), eq(TaskStatus.ENABLED)))
                    .thenReturn(marginTask);
            when(client.agent(eq("SUP_REV_AGENT"), any(AgentAttributes.class), anyString(), eq(AgentStatus.ENABLED)))
                    .thenReturn(revenueAgent);
            when(client.agent(eq("SUP_MARGIN_AGENT"), any(AgentAttributes.class), anyString(), eq(AgentStatus.ENABLED)))
                    .thenReturn(marginAgent);
            when(client.agent(eq("SUP_SUPERVISOR"), any(AgentAttributes.class), anyString(), eq(AgentStatus.ENABLED)))
                    .thenReturn(supervisor);
            when(client.team(eq("SUP_TEAM"), any(TeamAttributes.class), anyString(), eq(TeamStatus.ENABLED)))
                    .thenReturn(team);
            when(core.conversation(any(ConversationAttributes.class))).thenReturn(conversation);
            when(tool.create()).thenReturn(true);
            when(revenueTask.create()).thenReturn(true);
            when(marginTask.create()).thenReturn(true);
            when(revenueAgent.create()).thenReturn(true);
            when(marginAgent.create()).thenReturn(true);
            when(supervisor.create()).thenReturn(true);
            when(supervisor.getAgentName()).thenReturn("SUP_SUPERVISOR");
            when(team.create()).thenReturn(true);
            when(conversation.create()).thenReturn("SUP_C1");
            when(team.run(any(RunTeamRequest.class))).thenReturn(new TeamRunResult("Analysis"));
        }

        private void run() throws Throwable {
            invokeSample(supervisorWorkflow, client, core, "PROFILE", "SUP", "Synthetic figures");
        }

        private void verifyCleanup() throws SelectAIException {
            var order = inOrder(conversation, team, supervisor, marginAgent, revenueAgent,
                    marginTask, revenueTask, tool);
            order.verify(conversation).drop(false);
            order.verify(team).drop(false);
            order.verify(supervisor).drop(false);
            order.verify(marginAgent).drop(false);
            order.verify(revenueAgent).drop(false);
            order.verify(marginTask).drop(false);
            order.verify(revenueTask).drop(false);
            order.verify(tool).drop(false);
        }
    }

    private record ProcessResult(int exitCode, String output) { }
}
