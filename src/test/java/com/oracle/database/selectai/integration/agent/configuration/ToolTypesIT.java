/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.agent.Tool;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.EmailNotificationToolParams;
import com.oracle.database.selectai.model.agent.SlackNotificationToolParams;
import com.oracle.database.selectai.model.agent.ToolStatus;
import com.oracle.database.selectai.model.agent.ToolRunResult;
import com.oracle.database.selectai.model.agent.ToolType;
import com.oracle.database.selectai.model.agent.WebSearchToolParams;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional credential-backed built-in tool coverage. */
class ToolTypesIT extends AgentIntegrationFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private void replaceCredential(String name, String username, String password) throws Exception {
        try {
            selectAI.credential(CredentialConfig.builder(name).build()).drop(true);
        } catch (Exception ignored) {
            // The Python tests recreate these fixed credential names when absent.
        }
        createManagedCredential(CredentialConfig.usernamePassword(name, username, password));
    }

    /**
     * Test: Create a web-search tool with the configured credential and inspect its description.
     * Expected: The tool reports WEBSEARCH, the credential name, a nonblank function, an ID, and
     * at least one input definition.
     */
    @Test
    void test30007WebSearchToolCreated() throws Exception {
        String credential = envOrDefault("SELECT_AI_IT_AGENT_WEBSEARCH_CREDENTIAL", "OPENAI_CRED");
        String apiKey = requiredFeatureValue("SELECT_AI_IT_AGENT_OPENAI_API_KEY",
                "Web-search coverage requires the Python OpenAI credential value");
        replaceCredential(credential, "openai", apiKey);
        Tool tool = agentClient.tool(uniqueName("WEBSEARCH_TOOL"), ToolAttributes.builder()
                .toolType(ToolType.WEBSEARCH)
                .toolParams(WebSearchToolParams.builder().credentialName(credential).build())
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getToolType()).isEqualTo(ToolType.WEBSEARCH);
        assertToolDescription(tool, ToolType.WEBSEARCH, Map.of("credential_name", credential));
    }

    /**
     * Test: Create an email notification tool from the configured credential, recipient, sender,
     * and SMTP host.
     * Expected: The description reports NOTIFICATION and preserves the email type and supplied
     * parameters, with a function and input definitions.
     */
    @Test
    void test30008EmailNotificationToolCreated() throws Exception {
        String credential = envOrDefault(
                "SELECT_AI_IT_AGENT_EMAIL_CREDENTIAL_NAME", "EMAIL_CRED");
        String username = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_CRED_USERNAME",
                "Email username is required by the Python notification fixture");
        String password = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_CRED_PASSWORD",
                "Email password is required by the Python notification fixture");
        String recipient = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_RECIPIENT",
                "Email recipient is required by the Python notification fixture");
        String sender = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_SENDER",
                "Email sender is required by the Python notification fixture");
        String smtpHost = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_SMTPHOST",
                "Email SMTP host is required by the Python notification fixture");
        replaceCredential(credential, username, password);
        Tool tool = agentClient.tool(uniqueName("EMAIL_TOOL"), ToolAttributes.builder()
                .toolType(ToolType.NOTIFICATION)
                .toolParams(emailParams(credential, recipient, sender, smtpHost))
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getToolType()).isEqualTo(ToolType.NOTIFICATION);
        assertToolDescription(tool, ToolType.NOTIFICATION, Map.of(
                "credential_name", credential,
                "notification_type", "email",
                "recipient", recipient,
                "sender", sender,
                "smtp_host", smtpHost));
    }

    /**
     * Test: Invoke an email notification tool using values for every argument in its described
     * input schema.
     * Expected: The run returns a non-null ToolRunResult with a nonblank raw response.
     */
    @Test
    void test30010EmailNotificationToolRuns() throws Exception {
        String credential = envOrDefault(
                "SELECT_AI_IT_AGENT_EMAIL_CREDENTIAL_NAME", "EMAIL_CRED");
        String username = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_CRED_USERNAME",
                "Email username is required by the Python notification fixture");
        String password = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_CRED_PASSWORD",
                "Email password is required by the Python notification fixture");
        String recipient = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_RECIPIENT",
                "Email recipient is required by the Python notification fixture");
        String sender = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_SENDER",
                "Email sender is required by the Python notification fixture");
        String smtpHost = requiredFeatureValue("SELECT_AI_IT_AGENT_EMAIL_SMTPHOST",
                "Email SMTP host is required by the Python notification fixture");
        replaceCredential(credential, username, password);

        Tool tool = agentClient.tool(uniqueName("EMAIL_RUN_TOOL"), ToolAttributes.builder()
                .toolType(ToolType.NOTIFICATION)
                .toolParams(emailParams(credential, recipient, sender, smtpHost))
                .build(), "Java email notification execution test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);

        JsonNode functionArgs = MAPPER.readTree(tool.describe()).path("function_args");
        assertThat(functionArgs.isArray()).as("email tool input schema").isTrue();
        String marker = "Select AI Java email tool invocation " + UUID.randomUUID();
        Map<String, String> input = new LinkedHashMap<>();
        for (JsonNode functionArg : functionArgs) {
            String argumentName = functionArg.path("argument_name").asText();
            assertThat(argumentName).as("email tool input name").isNotBlank();
            input.put(argumentName, marker);
        }
        assertThat(input).as("email tool inputs").isNotEmpty();
        ToolRunResult result = tool.run(MAPPER.writeValueAsString(input));

        assertThat(result).as("email notification tool result").isNotNull();
        assertThat(result.getRawValue()).as("email notification tool response").isNotBlank();
    }

    /**
     * Test: Create a Slack notification tool with the configured credential and #general channel.
     * Expected: The description reports NOTIFICATION and preserves the Slack type, credential,
     * and channel, with a function and input definitions.
     */
    @Test
    void test30009SlackNotificationToolCreated() throws Exception {
        String credential = envOrDefault("SELECT_AI_IT_AGENT_SLACK_CREDENTIAL", "SLACK_CRED");
        String username = requiredFeatureValue("SELECT_AI_IT_AGENT_SLACK_USERNAME",
                "Slack username is required by the Python notification fixture");
        String password = requiredFeatureValue("SELECT_AI_IT_AGENT_SLACK_PASSWORD",
                "Slack password is required by the Python notification fixture");
        replaceCredential(credential, username, password);
        Tool tool = agentClient.tool(uniqueName("SLACK_TOOL"), ToolAttributes.builder()
                .toolType(ToolType.NOTIFICATION)
                .toolParams(SlackNotificationToolParams.builder()
                        .credentialName(credential).channel("#general").build())
                .build(), "Java tool test", ToolStatus.ENABLED);
        tool.create();
        managedTools.add(tool);
        assertThat(tool.getToolAttributes().getToolType()).isEqualTo(ToolType.NOTIFICATION);
        assertToolDescription(tool, ToolType.NOTIFICATION, Map.of(
                "credential_name", credential,
                "notification_type", "slack",
                "channel", "#general"));
    }

    private void assertToolDescription(Tool tool, ToolType expectedType,
                                       Map<String, String> expectedToolParams) throws Exception {
        JsonNode description = MAPPER.readTree(tool.describe());
        assertThat(description.path("id").isIntegralNumber()).isTrue();
        assertThat(description.path("description").asText()).isEqualTo("Java tool test");

        JsonNode attributes = description.path("attributes");
        assertThat(attributes.isObject()).isTrue();
        assertThat(attributes.path("tool_type").asText()).isEqualTo(expectedType.getValue());
        assertThat(attributes.path("function").asText()).isNotBlank();

        JsonNode toolParams = MAPPER.readTree(attributes.path("tool_params").asText());
        expectedToolParams.forEach((name, value) ->
                assertThat(toolParams.path(name).asText()).isEqualTo(value));

        assertThat(description.path("function_args").isArray()).isTrue();
        assertThat(description.path("function_args").size()).isGreaterThan(0);
    }

    private static EmailNotificationToolParams emailParams(String credential, String recipient,
                                                            String sender, String smtpHost) {
        return EmailNotificationToolParams.builder()
                .credentialName(credential)
                .recipient(recipient)
                .sender(sender)
                .smtpHost(smtpHost)
                .build();
    }
}
