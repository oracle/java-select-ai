/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolParamsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> knownParams() {
        return Stream.of(
                Arguments.of(ToolType.SQL, SqlToolParams.builder().profileName("SALES").build(),
                        "{\"profile_name\":\"SALES\"}"),
                Arguments.of(ToolType.RAG, RagToolParams.builder().profileName("DOCS").build(),
                        "{\"profile_name\":\"DOCS\"}"),
                Arguments.of(ToolType.WEBSEARCH, WebSearchToolParams.builder().credentialName("WEB").build(),
                        "{\"credential_name\":\"WEB\"}"),
                Arguments.of(ToolType.NOTIFICATION, SlackNotificationToolParams.builder()
                                .credentialName("SLACK").channel("support").build(),
                        "{\"notification_type\":\"slack\",\"credential_name\":\"SLACK\",\"channel\":\"support\"}"),
                Arguments.of(ToolType.NOTIFICATION, EmailNotificationToolParams.builder()
                                .credentialName("EMAIL").recipient("r@example.com").sender("s@example.com")
                                .smtpHost("smtp.example.com").subject("Update").build(),
                        "{\"notification_type\":\"email\",\"credential_name\":\"EMAIL\","
                                + "\"recipient\":\"r@example.com\",\"sender\":\"s@example.com\","
                                + "\"smtp_host\":\"smtp.example.com\",\"subject\":\"Update\"}"));
    }

    @ParameterizedTest
    @MethodSource("knownParams")
    void serializesOnlyDatabaseFieldsAndLoadsTypedParameters(ToolType type, ToolParams params, String expected)
            throws Exception {
        ToolAttributes attributes = ToolAttributes.builder().toolType(type).toolParams(params).build();
        JsonNode json = MAPPER.readTree(attributes.toJson());

        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("tool_type").asText()).isEqualTo(type.getValue());
        assertThat(json.get("tool_params")).isEqualTo(MAPPER.readTree(expected));
        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(params))).isEqualTo(MAPPER.readTree(expected));
        assertThat(attributes.getToolParams()).isExactlyInstanceOf(params.getClass());

        ToolParams loaded = ToolParams.fromMap(type, params.toMap());
        assertThat(loaded).isExactlyInstanceOf(params.getClass());
        assertThat(loaded.toMap()).isEqualTo(params.toMap());
    }

    @Test
    void typedGettersExposeConfiguredValues() {
        assertThat(SqlToolParams.builder().profileName("SQL").build().getProfileName()).isEqualTo("SQL");
        assertThat(RagToolParams.builder().profileName("RAG").build().getProfileName()).isEqualTo("RAG");
        assertThat(WebSearchToolParams.builder().credentialName("WEB").build().getCredentialName()).isEqualTo("WEB");
        SlackNotificationToolParams slack = SlackNotificationToolParams.builder()
                .credentialName("SLACK").channel("support").build();
        assertThat(slack.getCredentialName()).isEqualTo("SLACK");
        assertThat(slack.getChannel()).isEqualTo("support");
        assertThat(slack.getNotificationType()).isEqualTo("slack");
        EmailNotificationToolParams email = EmailNotificationToolParams.builder()
                .credentialName("EMAIL").recipient("r@example.com").sender("s@example.com")
                .smtpHost("smtp.example.com").subject("Update").build();
        assertThat(email.getCredentialName()).isEqualTo("EMAIL");
        assertThat(email.getRecipient()).isEqualTo("r@example.com");
        assertThat(email.getSender()).isEqualTo("s@example.com");
        assertThat(email.getSmtpHost()).isEqualTo("smtp.example.com");
        assertThat(email.getSubject()).isEqualTo("Update");
        assertThat(email.getNotificationType()).isEqualTo("email");
    }

    @Test
    void permitsPartialUpdatesAndLeavesRequiredFieldValidationToDatabase() {
        assertThat(SqlToolParams.builder().build().toMap()).isEmpty();
        assertThat(RagToolParams.builder().build().getProfileName()).isNull();
        assertThat(WebSearchToolParams.builder().build().getCredentialName()).isNull();
        assertThat(SlackNotificationToolParams.builder().build().getChannel()).isNull();
        assertThat(EmailNotificationToolParams.builder().subject("New subject").build().toMap())
                .containsExactlyInAnyOrderEntriesOf(Map.of("notification_type", "email", "subject", "New subject"));
        assertThat(ToolParams.fromMap(ToolType.SQL, Map.of())).isInstanceOf(SqlToolParams.class);
        assertThat(SqlToolParams.builder().profileName("old").profileName(null).build().toMap()).isEmpty();
        assertThat(ToolAttributes.builder().toolParams(null).build().getToolParams()).isNull();
        assertThat(ToolParams.fromMap(ToolType.SQL, null)).isNull();
    }

    @Test
    void additionalParametersAreFlattenedAndSnapshottedAtSetterTime() throws Exception {
        List<Object> options = new ArrayList<>(List.of(Map.of("mode", "original")));
        SqlToolParams.Builder builder = SqlToolParams.builder().profileName("SALES")
                .additionalParameter("future_options", options);
        options.clear();
        SqlToolParams params = builder.build();
        builder.profileName("CHANGED").additionalParameter("later", true);

        assertThat(params.getProfileName()).isEqualTo("SALES");
        assertThat(params.toMap()).doesNotContainKey("later");
        List<?> stored = (List<?>) params.toMap().get("future_options");
        assertThat(stored).hasSize(1);
        assertThatThrownBy(stored::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(((Map<?, ?>) stored.get(0))::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(params.toMap()::clear).isInstanceOf(UnsupportedOperationException.class);

        JsonNode actual = MAPPER.readTree(ToolAttributes.builder().toolParams(params).build().toJson());
        assertThat(actual.get("tool_params")).isEqualTo(MAPPER.readTree(
                "{\"profile_name\":\"SALES\",\"future_options\":[{\"mode\":\"original\"}]}"));
    }

    @Test
    void genericAndCallerImplementedParametersAreDefensivelyCopied() throws Exception {
        Map<String, Object> source = new LinkedHashMap<>();
        List<String> items = new ArrayList<>(List.of("original"));
        source.put("items", items);
        source.put("nested", Collections.singletonMap("nullable", null));
        source.put("precision", new BigDecimal("1.234567890123456789"));
        source.put("optional", null);
        GenericToolParams params = GenericToolParams.fromMap(source);
        ToolAttributes attributes = ToolAttributes.builder().toolParams(() -> source).build();
        items.clear();
        source.clear();

        assertThat(attributes.getToolParams().toMap()).isEqualTo(params.toMap());
        assertThat(params.toMap()).containsEntry("precision", new BigDecimal("1.234567890123456789"))
                .containsEntry("optional", null);
        assertThat((List<?>) params.toMap().get("items")).hasSize(1);
        JsonNode serialized = MAPPER.readTree(attributes.toJson()).get("tool_params");
        assertThat(serialized.has("optional")).isTrue();
        assertThat(serialized.get("optional").isNull()).isTrue();
        assertThat(serialized.path("nested").get("nullable").isNull()).isTrue();
        assertThatThrownBy(params.toMap()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(((List<?>) params.toMap().get("items"))::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void loadingRetainsUnknownFieldsAndUsesGenericModelForFutureSchemas() {
        Map<String, Object> values = Map.of("profile_name", "SALES", "future", Map.of("enabled", true));
        ToolParams sql = ToolParams.fromMap(ToolType.SQL, values);
        assertThat(sql).isInstanceOf(SqlToolParams.class);
        assertThat(sql.toMap()).isEqualTo(values);

        for (ToolType type : List.of(ToolType.of("FUTURE"), ToolType.HUMAN, ToolType.NOTIFICATION)) {
            ToolParams loaded = ToolParams.fromMap(type, values);
            assertThat(loaded).isInstanceOf(GenericToolParams.class);
            assertThat(loaded.toMap()).isEqualTo(values);
        }
        assertThat(ToolParams.fromMap(null, values)).isInstanceOf(GenericToolParams.class);
        Map<String, Object> futureProfile = Map.of("profile_name", List.of("PROFILE1", "PROFILE2"));
        ToolParams changed = ToolParams.fromMap(ToolType.SQL, futureProfile);
        assertThat(changed).isInstanceOf(GenericToolParams.class);
        assertThat(changed.toMap()).isEqualTo(futureProfile);
        Map<String, Object> futureNotification = Map.of("notification_type", "sms", "phone", "example");
        ToolParams notification = ToolParams.fromMap(ToolType.NOTIFICATION, futureNotification);
        assertThat(notification).isInstanceOf(GenericToolParams.class);
        assertThat(notification.toMap()).isEqualTo(futureNotification);
    }

    @Test
    void recognizesNotificationTypeWithoutChangingStoredValue() {
        ToolParams slack = ToolParams.fromMap(ToolType.NOTIFICATION, Map.of("notification_type", "SLACK"));
        assertThat(slack).isInstanceOf(SlackNotificationToolParams.class);
        assertThat(((SlackNotificationToolParams) slack).getNotificationType()).isEqualTo("SLACK");
    }

    @Test
    void httpMetadataUsesGenericParametersRatherThanBuiltInSupport() throws Exception {
        Map<String, Object> values = Map.of("credential_name", "EXAMPLE_CRED",
                "endpoint", "https://example.com");
        ToolType type = ToolType.of("HTTP");
        ToolParams params = ToolParams.fromMap(type, values);

        assertThat(type.isKnown()).isFalse();
        assertThat(params).isExactlyInstanceOf(GenericToolParams.class);
        assertThat(params.toMap()).isEqualTo(values);
        ToolAttributes attributes = ToolAttributes.builder().toolType(type).toolParams(params).build();
        assertThat(attributes.getToolParams()).isExactlyInstanceOf(GenericToolParams.class);
        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.get("tool_type").asText()).isEqualTo("HTTP");
        assertThat(json.get("tool_params")).isEqualTo(MAPPER.valueToTree(values));
    }

    @Test
    void rejectsInvalidExtensionNamesAndPreventsTypedFieldOverrides() {
        assertThatThrownBy(() -> SqlToolParams.builder().additionalParameter("profile_name", "OTHER"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SlackNotificationToolParams.builder().additionalParameter("notification_type", "email"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenericToolParams.builder().parameter(null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenericToolParams.builder().parameter(" ", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenericToolParams.fromMap(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenericToolParams.builder().parameter("invalid", new Object()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
