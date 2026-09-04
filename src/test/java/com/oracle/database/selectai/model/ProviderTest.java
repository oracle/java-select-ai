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

class ProviderTest {

    /**
     * Test: Resolve supported provider names without regard to case.
     * Expected: Each supported name maps to its corresponding provider enum.
     */
    @Test
    void fromValueMatchesProviderNamesCaseInsensitively() {
        assertThat(Provider.fromValue("openai")).isEqualTo(Provider.openai);
        assertThat(Provider.fromValue("OPENAI")).isEqualTo(Provider.openai);
        assertThat(Provider.fromValue("oci")).isEqualTo(Provider.oci);
        assertThat(Provider.fromValue("Azure")).isEqualTo(Provider.azure);
    }

    /**
     * Test: Resolve every supported provider when the input uses mixed casing.
     * Expected: Each mixed-case name maps to its corresponding provider enum.
     */
    @Test
    void fromValueMatchesMixedCaseProviderNames() {
        assertThat(Provider.fromValue("OpEnAi")).isEqualTo(Provider.openai);
        assertThat(Provider.fromValue("CoHeRe")).isEqualTo(Provider.cohere);
        assertThat(Provider.fromValue("AzUrE")).isEqualTo(Provider.azure);
        assertThat(Provider.fromValue("OcI")).isEqualTo(Provider.oci);
        assertThat(Provider.fromValue("GoOgLe")).isEqualTo(Provider.google);
        assertThat(Provider.fromValue("AnThRoPiC")).isEqualTo(Provider.anthropic);
        assertThat(Provider.fromValue("HuGgInGfAcE")).isEqualTo(Provider.huggingface);
        assertThat(Provider.fromValue("AwS")).isEqualTo(Provider.aws);
    }

    /**
     * Test: Resolve null and blank provider input.
     * Expected: Both inputs return null.
     */
    @Test
    void fromValueReturnsNullForBlankInput() {
        assertThat(Provider.fromValue(null)).isNull();
        assertThat(Provider.fromValue(" ")).isNull();
    }

    /**
     * Test: Reject an unsupported provider name.
     * Expected: IllegalArgumentException reports that the provider is unsupported.
     */
    @Test
    void fromValueRejectsUnsupportedProviderNames() {
        assertThatThrownBy(() -> Provider.fromValue("mistral"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported provider");
    }

    /**
     * Test: Expose the complete supported provider enum set.
     * Expected: Provider.values() returns the supported providers in the declared order.
     */
    @Test
    void enumValuesExposeTheSupportedProviderSet() {
        assertThat(Provider.values())
                .containsExactly(
                        Provider.openai,
                        Provider.cohere,
                        Provider.azure,
                        Provider.oci,
                        Provider.google,
                        Provider.anthropic,
                        Provider.huggingface,
                        Provider.aws);
    }
}
