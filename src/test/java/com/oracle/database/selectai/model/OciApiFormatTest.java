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

class OciApiFormatTest {

    /**
     * Test: Enumerate supported OCI API formats.
     * Expected: The enum contains COHERE and GENERIC.
     */
    @Test
    void enumValuesMatchSupportedOciApiFormats() {
        assertThat(OciApiFormat.values())
                .containsExactly(OciApiFormat.COHERE, OciApiFormat.GENERIC);
        assertThat(OciApiFormat.COHERE.getValue()).isEqualTo("COHERE");
        assertThat(OciApiFormat.GENERIC.getValue()).isEqualTo("GENERIC");
    }

    /**
     * Test: Parse OCI API format values.
     * Expected: Null and blank values remain absent, and supported values are
     * parsed case-insensitively.
     */
    @Test
    void fromValueParsesSupportedOciApiFormats() {
        assertThat(OciApiFormat.fromValue(null)).isNull();
        assertThat(OciApiFormat.fromValue(" ")).isNull();
        assertThat(OciApiFormat.fromValue("generic")).isEqualTo(OciApiFormat.GENERIC);
        assertThat(OciApiFormat.fromValue(" CoHeRe ")).isEqualTo(OciApiFormat.COHERE);
    }

    /**
     * Test: Parse an unsupported OCI API format.
     * Expected: A clear validation exception is thrown.
     */
    @Test
    void fromValueRejectsUnsupportedOciApiFormat() {
        assertThatThrownBy(() -> OciApiFormat.fromValue("unsupported-format"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ociApiformat must be either COHERE or GENERIC");
    }

    /**
     * Test: Build profile attributes with a case-insensitive OCI API format.
     * Expected: The configured format is normalized to uppercase.
     */
    @Test
    void profileAttributesBuilderNormalizesOciApiFormatValues() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .ociApiformat("GeNeRiC")
                .build();

        assertThat(attributes.getOciApiformat()).isEqualTo("GENERIC");

        ProfileAttributes cohereAttributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .ociApiformat("cOhErE")
                .build();

        assertThat(cohereAttributes.getOciApiformat()).isEqualTo("COHERE");
    }

    /**
     * Test: Build profile attributes with a typed OCI API format.
     * Expected: The configured format is stored using the database value.
     */
    @Test
    void profileAttributesBuilderAcceptsTypedOciApiFormat() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .ociApiformat(OciApiFormat.GENERIC)
                .build();

        assertThat(attributes.getOciApiformat()).isEqualTo("GENERIC");
    }
}
