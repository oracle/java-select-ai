/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.state;

import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Live integration coverage for DBMS_CLOUD_AI_AGENT runtime variables. */
class RuntimeVariableIT extends IntegrationTestFixture {
    private SelectAIAgent agentClient;

    @BeforeEach
    void setUpAgentConnection(TestInfo testInfo) throws Exception {
        openIsolatedConnection(testInfo);
        agentClient = SelectAIAgent.create(dbConfig);
    }

    @AfterEach
    void closeAgentConnection() throws Exception {
        try {
            if (agentClient != null) {
                agentClient.close();
            }
        } finally {
            closeIsolatedConnection();
        }
    }

    @Override
    protected boolean requiresProfile() {
        return false;
    }

    /**
     * Test: Set a state variable without specifying a scope, then read it using the default scope.
     * Expected: The write returns true and the read returns "account-42".
     */
    @Test
    void test39100SetAndGetStateVariableWithDefaultScope() throws Exception {
        String name = uniqueName("STATE_VARIABLE");

        assertThat(agentClient.setVariable("state_variables", name, "account-42")).isTrue();

        assertThat(agentClient.getVariable("state_variables", name)).isEqualTo("account-42");
    }

    /**
     * Test: Set the same variable name to distinct values in state, instruction, and attribute
     * namespaces.
     * Expected: Reading each namespace returns only the value stored in that namespace.
     */
    @Test
    void test39101NamespacesAreIndependent() throws Exception {
        String name = uniqueName("NAMESPACE_VARIABLE");

        assertThat(agentClient.setVariable("state_variables", name, "state-value")).isTrue();
        assertThat(agentClient.setVariable("instruction_variables", name, "instruction-value"))
                .isTrue();
        assertThat(agentClient.setVariable("attribute_variables", name, "attribute-value"))
                .isTrue();

        assertThat(agentClient.getVariable("state_variables", name)).isEqualTo("state-value");
        assertThat(agentClient.getVariable("instruction_variables", name))
                .isEqualTo("instruction-value");
        assertThat(agentClient.getVariable("attribute_variables", name))
                .isEqualTo("attribute-value");
    }

    /**
     * Test: Set the same state variable first to "first-value" and then to "second-value".
     * Expected: Both writes return true, and a subsequent read returns "second-value".
     */
    @Test
    void test39102SetVariableOverwritesExistingValue() throws Exception {
        String name = uniqueName("OVERWRITE_VARIABLE");

        assertThat(agentClient.setVariable("state_variables", name, "first-value")).isTrue();
        assertThat(agentClient.setVariable("state_variables", name, "second-value")).isTrue();

        assertThat(agentClient.getVariable("state_variables", name)).isEqualTo("second-value");
    }

    /**
     * Test: Set a state variable, then set that variable to null.
     * Expected: Both writes return true, and reading the variable returns null.
     */
    @Test
    void test39103NullValueClearsVariable() throws Exception {
        String name = uniqueName("CLEAR_VARIABLE");

        assertThat(agentClient.setVariable("state_variables", name, "value-to-clear")).isTrue();
        assertThat(agentClient.setVariable("state_variables", name, null)).isTrue();

        assertThat(agentClient.getVariable("state_variables", name)).isNull();
    }

    /**
     * Test: Read a uniquely named state variable that has not been set.
     * Expected: getVariable returns null.
     */
    @Test
    void test39104MissingVariableReturnsNull() throws Exception {
        assertThat(agentClient.getVariable("state_variables", uniqueName("MISSING_VARIABLE")))
                .isNull();
    }

    /**
     * Test: Read an implicitly scoped variable with explicit "ephemeral" scope, then read an
     * explicitly scoped variable with the default scope.
     * Expected: Both reads return the value written through the other scope form.
     */
    @Test
    void test39105ExplicitEphemeralScopeMatchesDefaultScope() throws Exception {
        String defaultScopeName = uniqueName("DEFAULT_SCOPE_VARIABLE");
        String explicitScopeName = uniqueName("EXPLICIT_SCOPE_VARIABLE");

        assertThat(agentClient.setVariable("state_variables", defaultScopeName, "default-value"))
                .isTrue();
        assertThat(agentClient.getVariable("state_variables", defaultScopeName, "ephemeral"))
                .isEqualTo("default-value");

        assertThat(agentClient.setVariable("state_variables", explicitScopeName, "explicit-value",
                "ephemeral")).isTrue();
        assertThat(agentClient.getVariable("state_variables", explicitScopeName))
                .isEqualTo("explicit-value");
    }

    /**
     * Test: Set a state variable to a Unicode string longer than 32,768 characters.
     * Expected: setVariable accepts the large value and returns true.
     */
    @Test
    void test39106SetVariableAcceptsLargeUnicodeValue() throws Exception {
        String name = uniqueName("LARGE_VARIABLE");
        String expectedValue = "large-value-start\n" + "café 東京 🚀\n".repeat(4_000)
                + "large-value-end";
        assertThat(expectedValue.length()).isGreaterThan(32_768);

        assertThat(agentClient.setVariable("state_variables", name, expectedValue)).isTrue();
    }

    /**
     * Test: Set a state variable in one SelectAIAgent session and read it from a second session.
     * Expected: The second session reads null, while the original session still reads its value.
     */
    @Test
    void test39107RuntimeVariablesAreSessionIsolated() throws Exception {
        String name = uniqueName("SESSION_VARIABLE");
        assertThat(agentClient.setVariable("state_variables", name, "first-session-value")).isTrue();

        try (SelectAIAgent secondClient = SelectAIAgent.create(dbConfig)) {
            assertThat(secondClient.getVariable("state_variables", name)).isNull();
        }

        assertThat(agentClient.getVariable("state_variables", name)).isEqualTo("first-session-value");
    }

    /**
     * Test: Set a state variable using the unsupported "persistent" scope.
     * Expected: The call throws SelectAIException with an SQLException cause from the database.
     */
    @Test
    void test39108UnsupportedScopeIsRejectedByDatabase() {
        assertDatabaseRejection(() -> agentClient.setVariable("state_variables",
                uniqueName("UNSUPPORTED_SCOPE"), "value", "persistent"));
    }

    /**
     * Test: Set a variable in an unsupported namespace, then set a state variable with a blank
     * name.
     * Expected: The unsupported namespace throws SelectAIException with an SQLException cause;
     * the blank name throws IllegalArgumentException.
     */
    @Test
    void test39109InvalidNamespaceIsDelegatedToDatabaseAndBlankNameIsRejectedBySdk() {
        assertDatabaseRejection(() -> agentClient.setVariable("unsupported_namespace",
                uniqueName("INVALID_NAMESPACE"), "value"));

        assertThatThrownBy(() -> agentClient.setVariable("state_variables", " ", "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String uniqueName(String stem) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "JSAI_" + stem + "_" + suffix;
    }

    private void assertDatabaseRejection(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause()).isInstanceOf(SQLException.class));
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
