/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.configuration;

import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT agent attribute mutation operations. */
class AgentConfigurationIT extends AgentIntegrationFixture {
    /**
     * Test: Set the agent role, human-tool flag, and short-term memory length with individual
     * setters.
     * Expected: Each setter succeeds, and all three values match on the original agent and a
     * freshly loaded handle.
     */
    @Test
    void test32033SetAgentAttributesPersistThroughFreshHandle() throws Exception {
        Agent agent = agentClient.agent(uniqueName("SET_ATTRIBUTE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        String updatedRole = "Answer support questions using the configured tools.";

        assertThat(agent.setAttribute("role", updatedRole)).isTrue();
        assertThat(agent.setAttribute("enable_human_tool", true)).isTrue();
        assertThat(agent.setAttribute("short_term_memory_length", 24)).isTrue();

        assertAttributes(agent, updatedRole, true, 24);
        assertAttributes(agentClient.agent(agent.getAgentName()), updatedRole, true, 24);
    }

    /**
     * Test: Replace agent attributes, including profile, role, human-tool flag, tool list, and
     * memory length.
     * Expected: The update succeeds; the original and freshly loaded agent expose the requested
     * profile, role, flag, tools, and memory length.
     */
    @Test
    void test32034SetAgentAttributesPersistThroughFreshHandle() throws Exception {
        Agent agent = agentClient.agent(uniqueName("SET_ATTRIBUTES"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        Tool firstTool = agentClient.tool(uniqueName("SET_ATTRIBUTES_TOOL_ONE"), sqlToolAttributes(),
                "Java SQL tool test", ToolStatus.ENABLED);
        firstTool.create();
        managedTools.add(firstTool);
        Tool secondTool = agentClient.tool(uniqueName("SET_ATTRIBUTES_TOOL_TWO"), sqlToolAttributes(),
                "Java SQL tool test", ToolStatus.ENABLED);
        secondTool.create();
        managedTools.add(secondTool);
        String updatedRole = "Coordinate database support requests with the assigned tools.";
        AgentAttributes updatedAttributes = AgentAttributes.builder()
                .profileName(profileName)
                .role(updatedRole)
                .enableHumanTool(true)
                .tools(List.of(firstTool.getToolName(), secondTool.getToolName()))
                .shortTermMemoryLength(24)
                .build();

        assertThat(agent.setAttributes(updatedAttributes)).isTrue();

        assertThat(agent.getAgentAttributes().getProfileName()).isEqualTo(profileName);
        assertAttributes(agent, updatedRole, true, 24);
        assertThat(agent.getAgentAttributes().getTools())
                .containsExactly(firstTool.getToolName(), secondTool.getToolName());

        Agent reloaded = agentClient.agent(agent.getAgentName());
        assertThat(reloaded.getAgentAttributes().getProfileName()).isEqualTo(profileName);
        assertAttributes(reloaded, updatedRole, true, 24);
        assertThat(reloaded.getAgentAttributes().getTools())
                .containsExactly(firstTool.getToolName(), secondTool.getToolName());
    }

    /**
     * Test: Set an unrecognized agent attribute on a created agent.
     * Expected: The SDK surfaces a SelectAIException whose cause is the database SQLException.
     */
    @Test
    void test32035UnknownAgentAttributeIsRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("UNKNOWN_ATTRIBUTE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);

        assertDatabaseRejection(() -> agent.setAttribute("SDK_UNKNOWN_AGENT_ATTRIBUTE", "value"));
    }

    /**
     * Test: Clear the required profile_name and role attributes on a created agent.
     * Expected: Both updates are rejected with database SQLExceptions wrapped by
     * SelectAIException.
     */
    @Test
    void test32036ClearingMandatoryAgentAttributesIsRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("CLEAR_MANDATORY_ATTRIBUTE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);

        assertDatabaseRejection(() -> agent.setAttribute("profile_name", (String) null));
        assertDatabaseRejection(() -> agent.setAttribute("role", (String) null));
    }

    /**
     * Test: Set the supervisor attribute on an agent after creation.
     * Expected: The database rejects the mutation and the SDK exposes its SQLException through
     * SelectAIException.
     */
    @Test
    void test32037SupervisorMutationIsRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("SUPERVISOR_MUTATION"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);

        assertDatabaseRejection(() -> agent.setAttribute("supervisor", true));
    }

    private static void assertAttributes(Agent agent, String role, boolean enableHumanTool,
                                         int shortTermMemoryLength)
            throws SelectAIException {
        AgentAttributes attributes = agent.getAgentAttributes();
        assertThat(attributes.getRole()).isEqualTo(role);
        assertThat(attributes.getEnableHumanTool()).isEqualTo(enableHumanTool);
        assertThat(attributes.getShortTermMemoryLength()).isEqualTo(shortTermMemoryLength);
    }

    private static void assertDatabaseRejection(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class));
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
