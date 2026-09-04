/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationAttributesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Leaves omitted conversation attributes unset and normalizes blank input.
     * Expected: Blank text becomes null, supplied text is trimmed, and no numeric defaults
     * are injected.
     */
    @Test
    void builderLeavesOmittedAttributesUnsetAndNormalizesBlankInput() {
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("   ")
                .description("  A working conversation  ")
                .retentionDays(null)
                .conversationLength(null)
                .build();

        assertThat(attributes.getTitle()).isNull();
        assertThat(attributes.getDescription()).isEqualTo("A working conversation");
        assertThat(attributes.getRetentionDays()).isNull();
        assertThat(attributes.getConversationLength()).isNull();
        assertThat(attributes.isEmpty()).isFalse();
        assertThat(attributes.toAttributeMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "description", "A working conversation"));
    }

    /**
     * Test: Detects empty conversation attributes.
     * Expected: An empty builder creates empty attributes, while any configured
     * attribute makes the instance non-empty.
     */
    @Test
    void isEmptyReflectsConfiguredAttributes() {
        assertThat(ConversationAttributes.builder().build().isEmpty()).isTrue();
        assertThat(ConversationAttributes.builder().title("Support").build().isEmpty()).isFalse();
        assertThat(ConversationAttributes.builder().description("Escalation").build().isEmpty()).isFalse();
        assertThat(ConversationAttributes.builder().retentionDays(1).build().isEmpty()).isFalse();
        assertThat(ConversationAttributes.builder().conversationLength(1).build().isEmpty()).isFalse();
    }

    /**
     * Test: Converts conversation attributes to database attribute names.
     * Expected: The attribute map contains the corresponding database keys and values.
     */
    @Test
    void toAttributeMapUsesDatabaseAttributeNames() {
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("Quarterly Review")
                .description("Finance follow-up")
                .retentionDays(14)
                .conversationLength(5)
                .build();

        assertThat(attributes.toAttributeMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "title", "Quarterly Review",
                "description", "Finance follow-up",
                "retention_days", "14",
                "conversation_length", "5"
        ));
    }

    /**
     * Test: Reads conversation attributes from case-insensitive database keys.
     * Expected: Keys with different casing produce the same typed values.
     */
    @Test
    void fromAttributeMapAcceptsCaseInsensitiveKeys() {
        ConversationAttributes attributes = ConversationAttributes.fromAttributeMap(Map.of(
                "TITLE", "Support",
                "Description", "Escalation history",
                "RETENTION_DAYS", "30",
                "conversation_length", "9",
                "unknown", "ignored"
        ));

        assertThat(attributes.getTitle()).isEqualTo("Support");
        assertThat(attributes.getDescription()).isEqualTo("Escalation history");
        assertThat(attributes.getRetentionDays()).isEqualTo(30);
        assertThat(attributes.getConversationLength()).isEqualTo(9);
    }

    /**
     * Test: Converts conversation attribute map keys with the root locale.
     * Expected: Turkish default locale does not affect upper-case database key handling.
     */
    @Test
    void fromAttributeMapUsesLocaleRoot() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            ConversationAttributes attributes = ConversationAttributes.fromAttributeMap(Map.of(
                    "TITLE", "Support",
                    "DESCRIPTION", "Escalation history",
                    "RETENTION_DAYS", "30"
            ));

            assertThat(attributes.getTitle()).isEqualTo("Support");
            assertThat(attributes.getDescription()).isEqualTo("Escalation history");
            assertThat(attributes.getRetentionDays()).isEqualTo(30);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Test: Serializes conversation attributes as snake_case JSON.
     * Expected: JSON uses database field names and omits null values.
     */
    @Test
    void toJsonUsesSnakeCaseAndOmitsNulls() throws Exception {
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("Incident Thread")
                .retentionDays(3)
                .build();

        JsonNode json = MAPPER.readTree(attributes.toJson());

        assertThat(json.get("title").asText()).isEqualTo("Incident Thread");
        assertThat(json.get("retention_days").asInt()).isEqualTo(3);
        assertThat(json.has("conversation_length")).isFalse();
        assertThat(json.has("description")).isFalse();
        assertThat(json.has("empty")).isFalse();
    }

    /**
     * Test: Validates retention and conversation-length lower bounds.
     * Expected: Negative retention and non-positive conversation length are rejected, while
     * upper-bound validation remains delegated to the database.
     */
    @Test
    void validatesRetentionAndConversationLengthBounds() throws Exception {
        assertThatThrownBy(() -> ConversationAttributes.builder().retentionDays(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retentionDays");

        assertThatThrownBy(() -> ConversationAttributes.builder().conversationLength(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationLength");

        ConversationAttributes zeroRetention = ConversationAttributes.builder()
                .retentionDays(0)
                .build();
        JsonNode zeroRetentionJson = MAPPER.readTree(zeroRetention.toJson());
        assertThat(zeroRetention.getRetentionDays()).isZero();
        assertThat(zeroRetentionJson.get("retention_days").asInt()).isZero();

        assertThat(ConversationAttributes.builder().conversationLength(1000).build()
                .getConversationLength()).isEqualTo(1000);
    }

    /**
     * Test: Rejects invalid numeric values while reading database attribute maps.
     * Expected: Invalid retention and conversation length values fail with clear messages.
     */
    @Test
    void fromAttributeMapRejectsInvalidNumericAttributesWithClearMessages() {
        assertThatThrownBy(() -> ConversationAttributes.fromAttributeMap(Map.of(
                "retention_days", "not-a-number")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retention_days must be an integer");

        assertThatThrownBy(() -> ConversationAttributes.fromAttributeMap(Map.of(
                "conversation_length", "not-a-number")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("conversation_length must be an integer");
    }

    /**
     * Test: Protects title and description in the string representation.
     * Expected: toString reports presence flags but does not expose customer content.
     */
    @Test
    void toStringDoesNotExposeTitleOrDescriptionValues() {
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("Sensitive Title")
                .description("Sensitive Description")
                .retentionDays(7)
                .conversationLength(3)
                .build();

        assertThat(attributes.toString())
                .contains("hasTitle=true")
                .contains("hasDescription=true")
                .contains("retentionDays=7")
                .contains("conversationLength=3")
                .doesNotContain("Sensitive Title")
                .doesNotContain("Sensitive Description");
    }
}
