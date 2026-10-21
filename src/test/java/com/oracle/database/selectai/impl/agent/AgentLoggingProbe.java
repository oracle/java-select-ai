/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Task;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.impl.DataSourceConnectionProvider;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.GenericToolParams;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TaskAttributes;
import com.oracle.database.selectai.model.agent.TaskStatus;
import com.oracle.database.selectai.model.agent.TeamAttributes;
import com.oracle.database.selectai.model.agent.TeamDescription;
import com.oracle.database.selectai.model.agent.TeamRunResult;
import com.oracle.database.selectai.model.agent.TeamStatus;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolRunResult;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Isolated log-capture scenarios using only synthetic content and mocked JDBC resources. */
public final class AgentLoggingProbe {
    private static final String PROMPT = "SYNTHETIC_PRIVATE_PROMPT_39f7";
    private static final String INPUT = "SYNTHETIC_PRIVATE_TOOL_INPUT_39f7";
    private static final String RESPONSE = "SYNTHETIC_PRIVATE_RESPONSE_39f7";
    private static final String SECRET = "SYNTHETIC_PRIVATE_KEY_39f7";
    private static final String CONTENT = "SYNTHETIC_PRIVATE_CONTENT_39f7";
    private static final String CAUSE = "SYNTHETIC_PRIVATE_CAUSE_39f7";
    private static final String SUPPRESSED = "SYNTHETIC_PRIVATE_SUPPRESSED_39f7";
    private static final String CHAINED = "SYNTHETIC_PRIVATE_NEXT_EXCEPTION_39f7";
    private static final String[] PRIVATE_VALUES = {
            PROMPT, INPUT, RESPONSE, SECRET, CONTENT, CAUSE, SUPPRESSED, CHAINED
    };

    private AgentLoggingProbe() {
    }

    /**
     * Runs a selected scenario after SLF4J is configured by the parent JVM.
     *
     * @param args scenario name
     * @throws Exception when setup or an assertion fails
     */
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "payloads" -> payloadsStayOutOfEnabledLogs();
            case "benign-failures" -> benignFailuresDoNotLogPayloadArguments();
            case "exception-content" -> originalExceptionLoggingExposesNestedContent();
            case "response-objects" -> responseObjectsDoNotLogTheirContents();
            default -> throw new IllegalArgumentException("Unknown logging scenario");
        }
        System.out.println("Verified log capture: " + args[0]);
    }

    private static void payloadsStayOutOfEnabledLogs() throws Exception {
        Fixture fixture = new Fixture();
        Captured captured = capture(() -> {
            assertThat(fixture.client.agent("NEW_AGENT", fixture.agentAttributes, CONTENT,
                    AgentStatus.ENABLED).create()).isTrue();
            assertThat(fixture.client.task("NEW_TASK", fixture.taskAttributes, CONTENT,
                    TaskStatus.ENABLED).create()).isTrue();
            assertThat(fixture.client.tool("NEW_TOOL", fixture.toolAttributes, CONTENT,
                    ToolStatus.ENABLED).create()).isTrue();
            assertThat(fixture.client.team("NEW_TEAM", fixture.teamAttributes, CONTENT,
                    TeamStatus.ENABLED).create()).isTrue();
            assertThat(fixture.team.run(fixture.runRequest).getRawValue()).isEqualTo(RESPONSE);
            assertThat(fixture.client.runTeam("OWNER.TEAM", fixture.runRequest).getRawValue())
                    .isEqualTo(RESPONSE);
            assertThat(fixture.tool.run("{\"query\":\"" + INPUT + "\"}").getRawValue()).isEqualTo(RESPONSE);
            assertThat(fixture.client.setVariable("state_variables", "context", SECRET)).isTrue();
            assertThat(fixture.client.getVariable("state_variables", "context")).isEqualTo(RESPONSE);
            assertThat(fixture.client.importTeam(fixture.importRequest)).isTrue();
            assertThat(fixture.team.exportDefinition(fixture.exportRequest)).isTrue();
            assertThat(fixture.team.exportDefinition()).isEqualTo(RESPONSE);
            assertThat(fixture.client.getDefinition(AgentObjectType.TEAM, "TEAM", true)).isEqualTo(RESPONSE);
            assertThat(fixture.tool.describe()).isEqualTo(RESPONSE);
            fixture.response = "{\"name\":\"TEAM\",\"description\":\"" + CONTENT
                    + "\",\"skills\":[{\"description\":\"" + RESPONSE + "\"}]}";
            assertThat(fixture.team.describe().getDescription()).isEqualTo(CONTENT);
        });

        assertThat(captured.failure()).isNull();
        assertThat(captured.logs()).contains("DEBUG", "INFO", "Running Select AI agent team TEAM",
                "Created Select AI agent NEW_AGENT", "Created Select AI agent task NEW_TASK",
                "Created Select AI agent tool NEW_TOOL", "Created Select AI agent team NEW_TEAM");
        assertNoPrivateContent(captured.logs());
    }

    private static void benignFailuresDoNotLogPayloadArguments() throws Exception {
        Fixture fixture = new Fixture();
        SQLException sqlFailure = new SQLException("Synthetic database failure", "42000", 20053);
        when(fixture.statement.execute()).thenThrow(sqlFailure);
        for (Operation operation : fixture.failureOperations()) {
            Captured captured = capture(operation.action());
            assertWrappedFailure(captured, sqlFailure, operation.name());
            assertThat(captured.logs()).contains("Synthetic database failure");
            assertNoPrivateContent(captured.logs());
        }
    }

    /** Characterization, not a redaction guarantee: current ERROR logs include exception content. */
    private static void originalExceptionLoggingExposesNestedContent() throws Exception {
        Fixture fixture = new Fixture();
        SQLException sqlFailure = new SQLException("Rejected payload " + PROMPT + " " + SECRET,
                "42000", 20053, new IllegalStateException(CAUSE));
        sqlFailure.addSuppressed(new SQLException(SUPPRESSED));
        sqlFailure.setNextException(new SQLException(CHAINED));
        when(fixture.statement.execute()).thenThrow(sqlFailure);

        for (Operation operation : fixture.failureOperations()) {
            Captured captured = capture(operation.action());
            assertWrappedFailure(captured, sqlFailure, operation.name());
            assertThat(captured.logs()).as("Exception content currently logged by %s", operation.name())
                    .contains(PROMPT, SECRET, "Caused by:", CAUSE, "Suppressed:", SUPPRESSED);
            assertThat(captured.failure().getMessage()).doesNotContain(PRIVATE_VALUES);
            assertThat(sqlFailure.getNextException().getMessage()).isEqualTo(CHAINED);
            assertThat(sqlFailure.getSuppressed()[0].getMessage()).isEqualTo(SUPPRESSED);
        }
    }

    private static void responseObjectsDoNotLogTheirContents() {
        Logger logger = LoggerFactory.getLogger(AgentLoggingProbe.class);
        Captured captured = capture(() -> {
            logger.debug("Team result object: {}", new TeamRunResult(RESPONSE));
            logger.info("Tool result object: {}", new ToolRunResult(RESPONSE));
            logger.error("Team description object: {}",
                    new TeamDescription("{\"name\":\"" + CONTENT + "\",\"description\":\""
                            + SECRET + "\",\"skills\":[{\"description\":\"" + RESPONSE + "\"}]}"));
        });
        assertThat(captured.failure()).isNull();
        assertThat(captured.logs()).contains("DEBUG", "INFO", "ERROR", "TeamRunResult@",
                "ToolRunResult@", "TeamDescription@");
        assertNoPrivateContent(captured.logs());
    }

    private static void assertWrappedFailure(Captured captured, SQLException original, String operation) {
        assertThat(captured.failure()).as(operation).isInstanceOf(SelectAIException.class).hasCause(original);
        SelectAIException failure = (SelectAIException) captured.failure();
        assertThat(failure.getErrorCode()).isEqualTo(20053);
        assertThat(failure.getSqlState()).isEqualTo("42000");
        assertThat(captured.logs()).as("Enabled operation logging for %s", operation)
                .contains("DEBUG", "ERROR", "DBMS_CLOUD_AI_AGENT." + operation);
    }

    private static void assertNoPrivateContent(String logs) {
        assertThat(logs).doesNotContain(PRIVATE_VALUES);
    }

    private static Captured capture(ThrowingCallable operation) {
        PrintStream previous = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setErr(stream);
            Throwable failure = catchThrowable(operation);
            stream.flush();
            return new Captured(failure, output.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(previous);
        }
    }

    private record Captured(Throwable failure, String logs) { }

    private record Operation(String name, ThrowingCallable action) { }

    /** All JDBC calls are mocks; payloads and credentials are synthetic test markers. */
    private static final class Fixture {
        private final CallableStatement statement = mock(CallableStatement.class);
        private final DefaultSelectAIAgent client;
        private final Agent agent;
        private final Task task;
        private final Tool tool;
        private final Team team;
        private String response = RESPONSE;
        private final AgentAttributes agentAttributes = AgentAttributes.builder()
                .profileName("PROFILE").role(PROMPT).build();
        private final TaskAttributes taskAttributes = TaskAttributes.builder().instruction(CONTENT).build();
        private final ToolAttributes toolAttributes = ToolAttributes.builder().instruction(CONTENT)
                .toolParams(GenericToolParams.builder().parameter("token", SECRET).build()).build();
        private final TeamAttributes teamAttributes = TeamAttributes.builder().process("sequential").build();
        private final RunTeamRequest runRequest = RunTeamRequest.builder().conversationId("C1")
                .userPrompt(PROMPT).instructionVariables(Map.of("context", CONTENT))
                .attributeVariables(Map.of("token", SECRET)).stateVariables(Map.of("input", INPUT)).build();
        private final ImportTeamRequest importRequest = ImportTeamRequest.builder("PROFILE", "TEAM")
                .specification(Map.of("description", CONTENT)).build();
        private final ExportTeamRequest exportRequest = ExportTeamRequest.builder("DIR:" + CONTENT + ".json")
                .objectStorageCredentialName(SECRET).build();

        private Fixture() throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareCall(anyString())).thenReturn(statement);
            when(statement.getClob(1)).thenAnswer(invocation -> {
                Clob clob = mock(Clob.class);
                when(clob.getCharacterStream()).thenReturn(new StringReader(response));
                return clob;
            });
            when(statement.getString(1)).thenReturn(RESPONSE);
            ConnectionProvider provider = new DataSourceConnectionProvider(dataSource);
            client = new DefaultSelectAIAgent(provider);
            agent = DefaultAgent.fromMetadata(provider, "AGENT", agentAttributes, CONTENT, "ENABLED");
            task = DefaultTask.fromMetadata(provider, "TASK", taskAttributes, CONTENT, "ENABLED");
            tool = DefaultTool.fromMetadata(provider, "TOOL", toolAttributes, CONTENT, "ENABLED");
            team = DefaultTeam.fromMetadata(provider, "TEAM", teamAttributes, CONTENT, "ENABLED");
        }

        private List<Operation> failureOperations() {
            return List.of(
                    new Operation("RUN_TEAM", () -> team.run(runRequest)),
                    new Operation("RUN_TEAM", () -> client.runTeam("OWNER.TEAM", runRequest)),
                    new Operation("RUN_TOOL", () -> tool.run("{\"input\":\"" + INPUT + "\"}")),
                    new Operation("SET_ATTRIBUTE", () -> agent.setAttribute("role", CONTENT)),
                    new Operation("SET_ATTRIBUTES", () -> task.setAttributes(taskAttributes)),
                    new Operation("SET_ATTRIBUTES", () -> tool.setAttributes(toolAttributes)),
                    new Operation("SET_ATTRIBUTES", () -> team.setAttributes(teamAttributes)),
                    new Operation("IMPORT_TEAM", () -> client.importTeam(importRequest)),
                    new Operation("EXPORT_TEAM", () -> team.exportDefinition(exportRequest)),
                    new Operation("GET_DEFINITION", () -> client.getDefinition(AgentObjectType.TEAM, "TEAM", true)),
                    new Operation("GET_VARIABLE", () -> client.getVariable("state_variables", "context")),
                    new Operation("SET_VARIABLE", () -> client.setVariable("state_variables", "context", SECRET)));
        }
    }
}
