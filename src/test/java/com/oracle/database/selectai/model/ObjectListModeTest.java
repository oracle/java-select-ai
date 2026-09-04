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

class ObjectListModeTest {

    /**
     * Test: Convert supported object-list modes with different casing.
     * Expected: Each value maps to the corresponding object-list mode.
     */
    @Test
    void fromValueParsesModesCaseInsensitively() {
        assertThat(ObjectListMode.fromValue("automated")).isEqualTo(ObjectListMode.AUTOMATED);
        assertThat(ObjectListMode.fromValue("AUTOMATED")).isEqualTo(ObjectListMode.AUTOMATED);
        assertThat(ObjectListMode.fromValue("AuToMaTeD")).isEqualTo(ObjectListMode.AUTOMATED);
        assertThat(ObjectListMode.fromValue("all")).isEqualTo(ObjectListMode.ALL);
        assertThat(ObjectListMode.fromValue("ALL")).isEqualTo(ObjectListMode.ALL);
        assertThat(ObjectListMode.fromValue("aLl")).isEqualTo(ObjectListMode.ALL);
        assertThat(ObjectListMode.fromValue(" all ")).isEqualTo(ObjectListMode.ALL);
    }

    /**
     * Test: Convert null and blank object-list modes.
     * Expected: Both inputs return null.
     */
    @Test
    void fromValueReturnsNullForBlankInput() {
        assertThat(ObjectListMode.fromValue(null)).isNull();
        assertThat(ObjectListMode.fromValue(" ")).isNull();
    }

    /**
     * Test: Convert an unsupported object-list mode.
     * Expected: The conversion throws IllegalArgumentException with the supported modes.
     */
    @Test
    void fromValueRejectsUnsupportedModeValues() {
        assertThatThrownBy(() -> ObjectListMode.fromValue("partial"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("automated, all");
    }

    /**
     * Test: Inspect the wire values for object-list modes.
     * Expected: Each enum constant exposes its database value.
     */
    @Test
    void enumValuesExposeWireValuesUsedByProfileAttributes() {
        assertThat(ObjectListMode.AUTOMATED.getValue()).isEqualTo("automated");
        assertThat(ObjectListMode.ALL.getValue()).isEqualTo("all");
    }

    /**
     * Test: Build profile attributes with a typed object-list mode.
     * Expected: The configured mode is stored using the database value.
     */
    @Test
    void profileAttributesBuilderAcceptsTypedObjectListMode() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .objectListMode(ObjectListMode.ALL)
                .build();

        assertThat(attributes.getObjectListMode()).isEqualTo("all");
    }
}
