/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.lifecycle;

import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.agent.Agent;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.AgentAttributes;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.AgentStatus;
import com.oracle.database.selectai.model.agent.ToolStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live coverage for DBMS_CLOUD_AI_AGENT agent operations. */
class AgentIT extends AgentIntegrationFixture {
    /**
     * Test: Create an enabled agent with a description and the fixture profile.
     * Expected: The handle exposes a nonblank name, the supplied description, and the configured profile
     * name.
     */
    @Test
    void test32000Identity() throws Exception {
        Agent agent = agentClient.agent(uniqueName("AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.getAgentName()).isNotBlank();
        assertThat(agent.getDescription()).isEqualTo("Java agent test");
        assertThat(agent.getAgentAttributes().getProfileName()).isEqualTo(profileName);
    }

    /**
     * Test: Create an enabled agent and query the full collection and an anchored name pattern.
     * Expected: The unfiltered collection includes the agent, and the anchored pattern returns exactly that
     * agent.
     */
    @Test
    void test32001ListAgents() throws Exception {
        Agent agent = agentClient.agent(uniqueName("LIST_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agentClient.listAgents()).extracting(Agent::getAgentName).contains(agent.getAgentName());
        assertThat(agentClient.listAgents("^" + agent.getAgentName() + "$"))
                .extracting(Agent::getAgentName).containsExactly(agent.getAgentName());
    }

    /**
     * Test: Create a disabled agent with a description, role, profile, and memory setting, then
     * retrieve it through a name-filtered list.
     * Expected: The single listed handle preserves the name, description, disabled status, profile, role,
     * and memory length of seven.
     */
    @Test
    void test32033ListAgentsHydratesMetadataAndAttributes() throws Exception {
        String name = uniqueName("LIST_METADATA");
        AgentAttributes attributes = AgentAttributes.builder()
                .profileName(profileName)
                .role("Answer list metadata questions.")
                .enableHumanTool(false)
                .shortTermMemoryLength(7)
                .build();
        Agent agent = agentClient.agent(name, attributes, "Agent list metadata", AgentStatus.DISABLED);
        agent.create();
        managedAgents.add(agent);

        List<Agent> listedAgents = agentClient.listAgents("^" + name + "$");
        assertThat(listedAgents).hasSize(1);
        Agent listed = listedAgents.get(0);
        assertThat(listed.getAgentName()).isEqualTo(name);
        assertThat(listed.getDescription()).isEqualTo("Agent list metadata");
        assertThat(listed.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(listed.getAgentAttributes().getProfileName()).isEqualTo(profileName);
        assertThat(listed.getAgentAttributes().getRole()).isEqualTo(attributes.getRole());
        assertThat(listed.getAgentAttributes().getShortTermMemoryLength()).isEqualTo(7);
    }

    /**
     * Test: Create five agents sharing a unique prefix plus one agent with a different prefix, then
     * list by the shared prefix.
     * Expected: The result contains exactly the five matching names, regardless of their order.
     */
    @Test
    void test32034ListAgentsMultipleMatchesUsingPattern() throws Exception {
        List<String> names = createAgentListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listAgentNames("^" + prefix)).containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create agents with a mixed-case unique prefix and search using its lowercase form.
     * Expected: The pattern matches all five agents with that prefix and excludes the differently prefixed
     * agent.
     */
    @Test
    void test32035ListAgentsCaseInsensitivePattern() throws Exception {
        List<String> names = createAgentListFixture();
        String prefix = listPrefix(names.get(0));

        assertThat(listAgentNames("^" + prefix.toLowerCase(Locale.ROOT)))
                .containsExactlyInAnyOrderElementsOf(names.subList(0, 5));
    }

    /**
     * Test: Create two groups of agents with distinct prefixes and search with a pattern that
     * alternates between them.
     * Expected: The result contains exactly all six names from both groups.
     */
    @Test
    void test32036ListAgentsAlternationPattern() throws Exception {
        List<String> names = createAgentListFixture();
        String firstPrefix = listPrefix(names.get(0));
        String secondPrefix = listPrefix(names.get(5));

        assertThat(listAgentNames("^(" + firstPrefix + "|" + secondPrefix + ")"))
                .containsExactlyInAnyOrderElementsOf(names);
    }

    /**
     * Test: Search for an anchored pattern built from a unique prefix that no agent uses.
     * Expected: The agent collection is empty.
     */
    @Test
    void test32037ListAgentsNonMatchingPatternReturnsEmptyList() throws Exception {
        String pattern = "^" + uniqueName("NO_MATCH") + "$";

        assertThat(agentClient.listAgents(pattern)).isEmpty();
    }

    /**
     * Test: Search for a unique literal name that does not identify an agent.
     * Expected: The agent collection is empty.
     */
    @Test
    void test32038ListAgentsLiteralNonMatchingPatternReturnsEmptyList() throws Exception {
        assertThat(agentClient.listAgents(uniqueName("NO_MATCH_LITERAL"))).isEmpty();
    }

    /**
     * Test: Create an agent, then list agents using an unclosed regular-expression character class.
     * Expected: The call throws SelectAIException and its database cause contains ORA-12726.
     */
    @Test
    void test32039ListAgentsInvalidPatternIsRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("INVALID_REGEX_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);

        assertThatThrownBy(() -> agentClient.listAgents("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
    }

    /**
     * Test: Create the list fixture and search using the first agent name followed by an end anchor.
     * Expected: Only the selected agent is returned.
     */
    @Test
    void test32040ListAgentsEndAnchorPattern() throws Exception {
        List<String> names = createAgentListFixture();
        String exact = names.get(0);
        String pattern = exact + "$";

        assertThat(listAgentNames(pattern)).containsExactly(exact);
    }

    /**
     * Test: Create an agent, then search with a 1,000-character regular-expression body.
     * Expected: The call throws SelectAIException and its database cause contains ORA-12733.
     */
    @Test
    void test32041ListAgentsOverlongPatternIsRejectedByDatabase() throws Exception {
        Agent agent = agentClient.agent(uniqueName("OVERLONG_REGEX_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        String pattern = "^" + "a".repeat(1000) + "$";

        assertThatThrownBy(() -> agentClient.listAgents(pattern))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12733:"));
    }

    /**
     * Test: Create three agents in B, A, C order and list them with a pattern matching their shared
     * prefix.
     * Expected: The returned names are ordered A, B, C.
     */
    @Test
    void test32042ListAgentsAreOrderedByName() throws Exception {
        String prefix = uniqueName("LIST_ORDER");
        String first = prefix + "_A";
        String second = prefix + "_B";
        String third = prefix + "_C";
        Agent secondAgent = agentClient.agent(second, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        secondAgent.create();
        managedAgents.add(secondAgent);
        Agent firstAgent = agentClient.agent(first, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        firstAgent.create();
        managedAgents.add(firstAgent);
        Agent thirdAgent = agentClient.agent(third, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        thirdAgent.create();
        managedAgents.add(thirdAgent);

        assertThat(listAgentNames("^" + prefix + "_[ABC]$"))
                .containsExactly(first, second, third);
    }

    /**
     * Test: Create an agent and load a second handle using its name.
     * Expected: The fetched handle has the same name and role as the created agent.
     */
    @Test
    void test32002FetchAgent() throws Exception {
        Agent created = agentClient.agent(uniqueName("FETCH_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        created.create();
        managedAgents.add(created);
        Agent fetched = agentClient.agent(created.getAgentName());
        assertThat(fetched.getAgentName()).isEqualTo(created.getAgentName());
        assertThat(fetched.getAgentAttributes().getRole())
                .isEqualTo(created.getAgentAttributes().getRole());
    }

    /**
     * Test: Create a disabled agent configured with a profile, role, human-tool flag, SQL tool, memory
     * length, and supervisor flag, then fetch it.
     * Expected: The fetched agent preserves its name, description, disabled status, and every configured
     * attribute.
     */
    @Test
    void test32006AllAgentAttributesRoundTrip() throws Exception {
        String toolName = uniqueName("ROUND_TRIP_TOOL");
        Tool tool = agentClient.tool(toolName, sqlToolAttributes(), "Java SQL tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        String agentName = uniqueName("ALL_ATTRIBUTES");
        AgentAttributes attributes = AgentAttributes.builder()
                .profileName(profileName)
                .role("Answer database questions using the configured SQL tool.")
                .enableHumanTool(true)
                .tools(java.util.List.of(toolName))
                .shortTermMemoryLength(7)
                .supervisor(true)
                .build();
        Agent created = agentClient.agent(agentName, attributes, "All attributes agent", AgentStatus.DISABLED);
        created.create();
        managedAgents.add(created);

        Agent fetched = agentClient.agent(agentName);
        AgentAttributes fetchedAttributes = fetched.getAgentAttributes();
        assertThat(fetched.getAgentName()).isEqualTo(agentName);
        assertThat(fetched.getDescription()).isEqualTo("All attributes agent");
        assertThat(fetched.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(fetchedAttributes.getProfileName()).isEqualTo(attributes.getProfileName());
        assertThat(fetchedAttributes.getRole()).isEqualTo(attributes.getRole());
        assertThat(fetchedAttributes.getEnableHumanTool()).isEqualTo(attributes.getEnableHumanTool());
        assertThat(fetchedAttributes.getTools()).containsExactlyElementsOf(attributes.getTools());
        assertThat(fetchedAttributes.getShortTermMemoryLength())
                .isEqualTo(attributes.getShortTermMemoryLength());
        assertThat(fetchedAttributes.getSupervisor()).isEqualTo(attributes.getSupervisor());
    }

    /**
     * Test: Request a handle for a unique agent name that has not been created.
     * Expected: The lookup throws an exception.
     */
    @Test
    void test32003FetchMissingAgentFails() {
        assertThatThrownBy(() -> agentClient.agent(uniqueName("MISSING_AGENT")))
                .isInstanceOf(Exception.class);
    }

    /**
     * Test: Create an agent without specifying its description or status.
     * Expected: The created agent reports ENABLED status.
     */
    @Test
    void test32004DefaultStatusEnabled() throws Exception {
        String name = uniqueName("DEFAULT_AGENT");
        Agent agent = agentClient.agent(name, agentAttributes(), null, null);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ENABLED);
    }

    /**
     * Test: Create an agent with DISABLED as its requested initial status.
     * Expected: The created agent reports DISABLED status.
     */
    @Test
    void test32005CreateDisabledAgent() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DISABLED_AGENT"), agentAttributes(), "Java agent test", AgentStatus.DISABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
    }

    /**
     * Test: Ask the client to construct an agent handle with an empty name.
     * Expected: Construction throws IllegalArgumentException.
     */
    @Test
    void test32008MissingAgentNameFailsLocally() {
        assertThatThrownBy(() -> agentClient.agent("", agentAttributes(), null, AgentStatus.ENABLED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Ask the client to construct an agent handle without attributes.
     * Expected: Construction throws IllegalArgumentException.
     */
    @Test
    void test32009MissingAgentAttributesFailsLocally() {
        assertThatThrownBy(() -> agentClient.agent(uniqueName("NO_ATTRIBUTES"), null, null,
                AgentStatus.ENABLED)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Create an enabled agent, then disable and re-enable it.
     * Expected: Both status changes succeed, and the handle reports DISABLED then ENABLED.
     */
    @Test
    void test32010DisableEnable() throws Exception {
        Agent agent = agentClient.agent(uniqueName("STATUS_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.disable()).isTrue();
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
        assertThat(agent.enable()).isTrue();
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ENABLED);
    }

    /**
     * Test: Create an enabled agent and request that it be enabled again.
     * Expected: The call throws SelectAIException with ORA-20050 in its cause, and status remains ENABLED.
     */
    @Test
    void test32011EnableAlreadyEnabledAgentFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("ENABLE_TWICE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ENABLED);

        assertThatThrownBy(agent::enable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).hasMessageContaining("ORA-20050:"));
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ENABLED);
    }

    /**
     * Test: Create a disabled agent and request that it be disabled again.
     * Expected: The call throws SelectAIException with ORA-20050 in its cause, and status remains DISABLED.
     */
    @Test
    void test32012DisableAlreadyDisabledAgentFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DISABLE_TWICE"), agentAttributes(), "Java agent test", AgentStatus.DISABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);

        assertThatThrownBy(agent::disable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).hasMessageContaining("ORA-20050:"));
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DISABLED);
    }

    /**
     * Test: Create an agent, then attempt to create a second handle with the same name.
     * Expected: The duplicate creation throws an exception, while the original agent retains its name.
     */
    @Test
    void test32016DuplicateCreationFails() throws Exception {
        String name = uniqueName("DUPLICATE_AGENT");
        Agent first = agentClient.agent(name, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        first.create();
        managedAgents.add(first);
        Agent duplicate = agentClient.agent(name, agentAttributes(), null, AgentStatus.ENABLED);
        assertThatThrownBy(duplicate::create).isInstanceOf(Exception.class);
        assertThat(first.getAgentName()).isEqualTo(name);
    }

    /**
     * Test: Create an agent, force-drop it, and create it again through the same handle.
     * Expected: The first lookup fails after the drop, recreation succeeds, and a fresh lookup returns the
     * name.
     */
    @Test
    void test32017DeleteAndRecreate() throws Exception {
        String name = uniqueName("RECREATE_AGENT");
        Agent agent = agentClient.agent(name, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(() -> agentClient.agent(name)).isInstanceOf(Exception.class);
        assertThat(agent.create()).isTrue();
        managedAgents.add(agent);
        assertThat(agentClient.agent(name).getAgentName()).isEqualTo(name);
    }

    /**
     * Test: Create and force-drop an agent, then try to disable its deleted handle.
     * Expected: The status change throws an exception.
     */
    @Test
    void test32018DisableAfterDeleteFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DELETE_DISABLE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(agent::disable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create and force-drop an agent, then try to enable its deleted handle.
     * Expected: The status change throws an exception.
     */
    @Test
    void test32019EnableAfterDeleteFails() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DELETE_ENABLE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(agent::enable).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create and force-drop an agent, then look it up again by name.
     * Expected: The lookup throws an exception.
     */
    @Test
    void test32023FetchAfterDeleteFails() throws Exception {
        String name = uniqueName("FETCH_DELETE");
        Agent agent = agentClient.agent(name, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(() -> agentClient.agent(name)).isInstanceOf(Exception.class);
    }

    /**
     * Test: Create an agent and retrieve the unfiltered agent collection.
     * Expected: The collection is nonempty and includes the created agent name.
     */
    @Test
    void test32024ListNonEmpty() throws Exception {
        Agent agent = agentClient.agent(uniqueName("NON_EMPTY"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agentClient.listAgents()).isNotEmpty().extracting(Agent::getAgentName)
                .contains(agent.getAgentName());
    }

    /**
     * Test: Create an agent and request its definition without dependencies.
     * Expected: The definition contains the agent name and the DBMS_CLOUD_AI_AGENT.CREATE_AGENT call.
     */
    @Test
    void test32025GetDefinition() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DEFINITION_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        String definition = agentClient.getDefinition(AgentObjectType.AGENT, agent.getAgentName(), false);
        assertThat(definition).containsIgnoringCase(agent.getAgentName())
                .containsIgnoringCase("DBMS_CLOUD_AI_AGENT.CREATE_AGENT");
    }

    /**
     * Test: Create an enabled supervisor agent with the supervisor attribute set.
     * Expected: A fresh handle reports the supervisor attribute as true.
     */
    @Test
    void test32026CreateSupervisorAgent() throws Exception {
        AgentAttributes attributes = AgentAttributes.builder()
                .profileName(profileName)
                .role("Coordinate and supervise the team.")
                .supervisor(true)
                .enableHumanTool(false)
                .build();
        String name = uniqueName("SUPERVISOR");
        Agent agent = agentClient.agent(name, attributes, null, AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agentClient.agent(name).getAgentAttributes().getSupervisor()).isTrue();
    }

    /**
     * Test: Create an agent whose attributes include a role but omit the required profile name.
     * Expected: Creation throws SelectAIException.
     */
    @Test
    void test32027MissingProfileNameIsRejectedByDatabase() {
        Agent agent = agentClient.agent(uniqueName("MISSING_PROFILE"),
                AgentAttributes.builder().role("Answer database questions.").build(),
                null, AgentStatus.ENABLED);

        assertThatThrownBy(agent::create).isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Create an agent whose attributes include a profile but omit the required role.
     * Expected: Creation throws SelectAIException.
     */
    @Test
    void test32028MissingRoleIsRejectedByDatabase() {
        Agent agent = agentClient.agent(uniqueName("MISSING_ROLE"),
                AgentAttributes.builder().profileName(profileName).build(),
                null, AgentStatus.ENABLED);

        assertThatThrownBy(agent::create).isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Create and drop an agent with force disabled, then look it up by name.
     * Expected: The drop succeeds, and the subsequent lookup reports that the agent was not found.
     */
    @Test
    void test32029DropExistingAgentWithoutForce() throws Exception {
        String name = uniqueName("DROP_NON_FORCE");
        Agent agent = agentClient.agent(name, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.drop(false)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(() -> agentClient.agent(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI agent not found: " + name);
    }

    /**
     * Test: Create an agent and force-drop the same handle twice.
     * Expected: Both drop calls return true.
     *
     * Bug <a href="https://bug.oraclecorp.com/ords/bug/bugui/bug-details?mode=edit&bugnumber=40126447">
     * 40126447</a> - Repeated force drops must be delegated to database force-drop semantics.
     */
    @Test
    void test32030RepeatedForceDropOnSameHandleSucceeds() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_REPEAT_FORCE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        assertThat(agent.drop(true)).isTrue();
        managedAgents.remove(agent);
        assertThat(agent.drop(true)).isTrue();
    }

    /**
     * Test: Create two handles for one agent, then force-drop the agent through each handle.
     * Expected: The first and repeated force-drop calls both return true.
     */
    @Test
    void test32031ForceDropAlreadyMissingAgentSucceeds() throws Exception {
        String name = uniqueName("DROP_MISSING_FORCE");
        Agent first = agentClient.agent(name, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        first.create();
        managedAgents.add(first);
        Agent second = agentClient.agent(name);

        assertThat(first.drop(true)).isTrue();
        managedAgents.remove(first);
        assertThat(second.drop(true)).isTrue();
    }

    /**
     * Test: Create two handles for one agent, force-drop it through the first, then drop it without
     * force through the second.
     * Expected: The second call throws SelectAIException with a database error code and a cause naming the
     * missing agent.
     */
    @Test
    void test32032NonForceDropMissingAgentPreservesDatabaseError() throws Exception {
        String name = uniqueName("DROP_MISSING_NON_FORCE");
        Agent first = agentClient.agent(name, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        first.create();
        managedAgents.add(first);
        Agent second = agentClient.agent(name);

        assertThat(first.drop(true)).isTrue();
        managedAgents.remove(first);
        assertThatThrownBy(() -> second.drop(false))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isNotNull();
                            assertThat(exception.getCause()).hasMessageContaining("ORA-")
                                    .hasMessageContaining(name)
                                    .hasMessageContaining("does not exist");
                        });
    }

    /**
     * Test: Create an agent, retrieve it from a filtered list, and force-drop the listed handle.
     * Expected: The listed collection contains one agent, the drop succeeds, and a later filtered list is
     * empty.
     */
    @Test
    void test32043DropAgentLoadedFromList() throws Exception {
        Agent source = agentClient.agent(uniqueName("DROP_LISTED_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        source.create();
        managedAgents.add(source);
        String name = source.getAgentName();

        List<Agent> listedAgents = agentClient.listAgents("^" + name + "$");
        assertThat(listedAgents).hasSize(1);
        assertThat(listedAgents.get(0).drop(true)).isTrue();
        managedAgents.remove(source);

        assertThat(listAgentNames("^" + name + "$")).isEmpty();
    }

    /**
     * Test: Create two handles for one agent, drop it through the source handle, then read status and
     * attributes from the other handle.
     * Expected: Both reads throw SelectAIException stating that the agent was not found.
     */
    @Test
    void test32044StaleAgentStatusAndAttributesFailAfterExternalDrop() throws Exception {
        Agent source = agentClient.agent(uniqueName("STALE_DROP_AGENT"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        source.create();
        managedAgents.add(source);
        String name = source.getAgentName();
        Agent stale = agentClient.agent(name);

        assertThat(source.drop(true)).isTrue();
        managedAgents.remove(source);

        assertThatThrownBy(stale::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI agent not found: " + name);
        assertThatThrownBy(stale::getAgentAttributes)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Select AI agent not found: " + name);
    }

    /**
     * Test: Force-drop a handle for an agent that was never created, then try a non-force drop on
     * another uncreated handle.
     * Expected: The force drop returns true, while the non-force drop throws IllegalStateException.
     */
    @Test
    void test32045NeverCreatedAgentForceDropDelegatesToDatabase() throws Exception {
        String forceName = uniqueName("UNCREATED_FORCE_DROP");
        Agent forceHandle = agentClient.agent(forceName, agentAttributes(), null, AgentStatus.ENABLED);
        assertThat(forceHandle.drop(true)).isTrue();

        Agent nonForceHandle = agentClient.agent(uniqueName("UNCREATED_NON_FORCE_DROP"),
                agentAttributes(), null, AgentStatus.ENABLED);
        assertThatThrownBy(() -> nonForceHandle.drop(false))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test: Create an agent, drop it without force, then repeat the non-force drop on the same handle.
     * Expected: The first drop succeeds, the second throws IllegalStateException, and listing the name
     * returns no agents.
     */
    @Test
    void test32046RepeatedNonForceDropOnSameAgentHandleIsRejectedLocally() throws Exception {
        Agent agent = agentClient.agent(uniqueName("DROP_REPEAT_NON_FORCE"), agentAttributes(), "Java agent test", AgentStatus.ENABLED);
        agent.create();
        managedAgents.add(agent);
        String name = agent.getAgentName();

        assertThat(agent.drop(false)).isTrue();
        managedAgents.remove(agent);
        assertThatThrownBy(() -> agent.drop(false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(listAgentNames("^" + name + "$")).isEmpty();
    }

    private List<String> createAgentListFixture() throws Exception {
        String listPrefix = uniqueName("LIST_PATTERN");
        String otherPrefix = uniqueName("LIST_OTHER");
        List<String> names = List.of(
                listPrefix + "_1",
                listPrefix + "_2",
                listPrefix + "_3",
                listPrefix + "_4",
                listPrefix + "_5",
                otherPrefix + "_1");
        for (String name : names) {
            Agent agent = agentClient.agent(name, agentAttributes(), "Java agent test", AgentStatus.ENABLED);
            agent.create();
            managedAgents.add(agent);
        }
        return names;
    }

    private List<String> listAgentNames(String pattern) throws Exception {
        return agentClient.listAgents(pattern).stream().map(Agent::getAgentName).toList();
    }

    private String listPrefix(String name) {
        return name.substring(0, name.lastIndexOf('_'));
    }
}
