/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorIndexConfigTest {

    /**
     * Test: Normalize vector-index configuration values and apply defaults.
     * Expected: Names and descriptions are trimmed, while status and wait flag use their defaults.
     */
    @Test
    void builderNormalizesConfiguredValuesAndDefaultsStatusAndWaitFlag() {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example/bucket/docs/")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build();

        VectorIndexConfig config = VectorIndexConfig.builder(" RAG_IDX_1 ")
                .vectorIndexAttributes(attributes)
                .description(" Product docs ")
                .build();

        assertThat(config.getIndexName()).isEqualTo("RAG_IDX_1");
        assertThat(config.getVectorIndexAttributes()).isSameAs(attributes);
        assertThat(config.getDescription()).isEqualTo("Product docs");
        assertThat(config.getStatus()).isEqualTo(VectorIndexConfig.Status.DISABLED);
        assertThat(config.getStatusValue()).isEqualTo("Disabled");
        assertThat(config.isWaitForCompletion()).isFalse();
    }

    /**
     * Test: Build a vector-index configuration with explicit status and wait flag.
     * Expected: The configured enabled status and true wait flag are preserved.
     */
    @Test
    void builderAcceptsStatusTextAndWaitFlag() {
        VectorIndexConfig config = VectorIndexConfig.builder("RAG_IDX_1")
                .status("enabled")
                .waitForCompletion(true)
                .build();

        assertThat(config.getStatus()).isEqualTo(VectorIndexConfig.Status.ENABLED);
        assertThat(config.getStatusValue()).isEqualTo("Enabled");
        assertThat(config.isWaitForCompletion()).isTrue();
    }

    /**
     * Test: Accepts the typed status setter.
     * Expected: An explicitly supplied enum value is retained and exposed using the database value.
     */
    @Test
    void builderAcceptsTypedStatus() {
        VectorIndexConfig config = VectorIndexConfig.builder("RAG_IDX_1")
                .status(VectorIndexConfig.Status.ENABLED)
                .build();

        assertThat(config.getStatus()).isEqualTo(VectorIndexConfig.Status.ENABLED);
        assertThat(config.getStatusValue()).isEqualTo("Enabled");
    }

    /**
     * Test: Apply defaults when status and wait flag are explicitly null.
     * Expected: Status is disabled and the wait flag is false.
     */
    @Test
    void builderDefaultsNullStatusAndWaitFlag() {
        VectorIndexConfig config = VectorIndexConfig.builder("RAG_IDX_1")
                .status((VectorIndexConfig.Status) null)
                .waitForCompletion(null)
                .build();

        assertThat(config.getStatus()).isEqualTo(VectorIndexConfig.Status.DISABLED);
        assertThat(config.isWaitForCompletion()).isFalse();
    }

    /**
     * Test: Parse vector-index status values with normalization and defaults.
     * Expected: Null or blank values default to disabled and supported text maps correctly.
     */
    @Test
    void statusFromValueNormalizesTextAndDefaultsBlankValues() {
        assertThat(VectorIndexConfig.Status.fromValue(null)).isEqualTo(VectorIndexConfig.Status.DISABLED);
        assertThat(VectorIndexConfig.Status.fromValue(" ")).isEqualTo(VectorIndexConfig.Status.DISABLED);
        assertThat(VectorIndexConfig.Status.fromValue(" enabled ")).isEqualTo(VectorIndexConfig.Status.ENABLED);
        assertThat(VectorIndexConfig.Status.fromValue(" EnAbLeD ")).isEqualTo(VectorIndexConfig.Status.ENABLED);
        assertThat(VectorIndexConfig.Status.fromValue(" disabled ")).isEqualTo(VectorIndexConfig.Status.DISABLED);
        assertThat(VectorIndexConfig.Status.fromValue(" dIsAbLeD ")).isEqualTo(VectorIndexConfig.Status.DISABLED);
    }

    /**
     * Test: Reject an unsupported vector-index status.
     * Expected: IllegalArgumentException identifies the enabled and disabled values.
     */
    @Test
    void statusFromValueRejectsUnsupportedValues() {
        assertThatThrownBy(() -> VectorIndexConfig.Status.fromValue("paused"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enabled or Disabled");
    }

    /**
     * Test: Validate null, blank, and ordinary vector-index names.
     * Expected: Null and blank names are rejected, while a valid name is retained.
     */
    @Test
    void builderRejectsInvalidIndexNames() {
        assertThatThrownBy(() -> VectorIndexConfig.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexName");

        assertThatThrownBy(() -> VectorIndexConfig.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexName");

        assertThat(VectorIndexConfig.builder("1_BAD").build().getIndexName())
                .isEqualTo("1_BAD");
    }

    /**
     * Test: Accept a vector-index name at the database maximum length.
     * Expected: The maximum-length name is retained unchanged.
     */
    @Test
    void builderAcceptsIndexNameAtDatabaseMaximumLength() {
        String indexName = "A" + "X".repeat(124);

        assertThat(VectorIndexConfig.builder(indexName).build().getIndexName())
                .isEqualTo(indexName);
    }

    /**
     * Test: Leave overlong vector-index name validation to the database.
     * Expected: The Java builder retains the overlong name without rejecting it.
     */
    @Test
    void builderDelegatesOverlongIndexNameValidationToDatabase() {
        String indexName = "A" + "X".repeat(125);

        assertThat(VectorIndexConfig.builder(indexName).build().getIndexName())
                .isEqualTo(indexName);
    }

    /**
     * Test: Keep profile name ownership in vector-index attributes.
     * Expected: VectorIndexConfig has no declared profileName field.
     */
    @Test
    void profileNameIsOwnedByVectorIndexAttributes() {
        assertThat(Arrays.stream(VectorIndexConfig.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("profileName")))
                .isTrue();
    }
}
