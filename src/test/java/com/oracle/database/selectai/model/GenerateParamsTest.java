/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerateParamsTest {

    /**
     * Test: Serializes the conversation identifier with its database field name.
     * Expected: JSON uses conversation_id.
     */
    @Test
    void toJsonSerializesConversationIdUsingSnakeCase() {
        GenerateParams params = GenerateParams.builder()
                .conversationId("CONV-123")
                .build();

        assertThat(params.toJson()).isEqualTo("{\"conversation_id\":\"CONV-123\"}");
    }

    /**
     * Test: Preserve the conversation identifier and the builder's fluent contract.
     * Expected: The builder setter returns itself and the immutable object exposes the supplied identifier.
     */
    @Test
    void builderPreservesConversationIdAndReturnsItself() {
        GenerateParams.Builder builder = GenerateParams.builder();

        assertThat(builder.conversationId("CONV-123")).isSameAs(builder);
        assertThat(builder.build().getConversationId()).isEqualTo("CONV-123");
    }

    /**
     * Test: Inspect whether generate params contain configured values.
     * Expected: Params with a conversation ID are not empty.
     */
    @Test
    void isEmptyReturnsFalseWhenConversationIdIsSet() {
        GenerateParams params = GenerateParams.builder()
                .conversationId("CONV-123")
                .build();

        assertThat(params.isEmpty()).isFalse();
    }

    /**
     * Test: Validates the required conversation identifier.
     * Expected: Missing and blank identifiers are rejected.
     */
    @Test
    void builderRejectsMissingOrBlankConversationId() {
        assertThatThrownBy(() -> GenerateParams.builder().conversationId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
        assertThatThrownBy(() -> GenerateParams.builder().conversationId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
        assertThatThrownBy(() -> GenerateParams.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one generate parameter");
    }
}
