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

class GenerateActionTest {

    /**
     * Test: Expose all supported generate actions.
     * Expected: GenerateAction.values() contains every supported action in declaration order.
     */
    @Test
    void test1600ActionEnumMembers() {
        assertThat(GenerateAction.values())
                .containsExactly(
                        GenerateAction.runsql,
                        GenerateAction.showsql,
                        GenerateAction.explainsql,
                        GenerateAction.narrate,
                        GenerateAction.summarize,
                        GenerateAction.translate,
                        GenerateAction.chat,
                        GenerateAction.embedding,
                        GenerateAction.showprompt);
    }

    /**
     * Test: Expose the wire names for generate actions.
     * Expected: Each action name matches its documented lowercase value.
     */
    @Test
    void test1601ActionEnumValues() {
        assertThat(GenerateAction.runsql.name()).isEqualTo("runsql");
        assertThat(GenerateAction.showsql.name()).isEqualTo("showsql");
        assertThat(GenerateAction.explainsql.name()).isEqualTo("explainsql");
        assertThat(GenerateAction.narrate.name()).isEqualTo("narrate");
        assertThat(GenerateAction.summarize.name()).isEqualTo("summarize");
        assertThat(GenerateAction.translate.name()).isEqualTo("translate");
        assertThat(GenerateAction.chat.name()).isEqualTo("chat");
        assertThat(GenerateAction.embedding.name()).isEqualTo("embedding");
        assertThat(GenerateAction.showprompt.name()).isEqualTo("showprompt");
    }

    /**
     * Test: Resolve generate actions from their string names.
     * Expected: Supported names return the corresponding enum constants.
     */
    @Test
    void test1602ActionFromString() {
        assertThat(GenerateAction.valueOf("runsql")).isSameAs(GenerateAction.runsql);
        assertThat(GenerateAction.valueOf("chat")).isSameAs(GenerateAction.chat);
        assertThat(GenerateAction.valueOf("explainsql")).isSameAs(GenerateAction.explainsql);
        assertThat(GenerateAction.valueOf("narrate")).isSameAs(GenerateAction.narrate);
        assertThat(GenerateAction.valueOf("showsql")).isSameAs(GenerateAction.showsql);
        assertThat(GenerateAction.valueOf("embedding")).isSameAs(GenerateAction.embedding);
    }

    /**
     * Test: Reject an unsupported generate action name.
     * Expected: valueOf throws IllegalArgumentException.
     */
    @Test
    void test1603InvalidAction() {
        assertThatThrownBy(() -> GenerateAction.valueOf("invalid_action"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
