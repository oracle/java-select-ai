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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorIndexModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Serializes only caller-supplied vector-index creation values.
     * Expected: The create payload contains configured values without SDK defaults.
     */
    @Test
    void vectorIndexAttributesSerializeOnlyCallerSuppliedCreateValues() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("  https://object.example/bucket/docs/  ")
                .objectStorageCredentialName("  OBJ_CRED  ")
                .profileName("  RAG_PROFILE  ")
                .vectorDbProvider("oracle")
                .vectorDimension(1536)
                .build();

        assertThat(attributes.getLocation()).isEqualTo("https://object.example/bucket/docs/");
        assertThat(attributes.getObjectStorageCredentialName()).isEqualTo("OBJ_CRED");
        assertThat(attributes.getProfileName()).isEqualTo("RAG_PROFILE");
        assertThat(attributes.getChunkSize()).isNull();
        assertThat(attributes.getChunkOverlap()).isNull();
        assertThat(attributes.getEnableSources()).isNull();
        assertThat(attributes.getMatchLimit()).isNull();
        assertThat(attributes.getRefreshRate()).isNull();
        assertThat(attributes.getSimilarityThreshold()).isNull();
        assertThat(attributes.getVectorDistanceMetric()).isNull();
        assertThat(attributes.getVectorDbProvider()).isEqualTo("oracle");

        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.get("object_storage_credential_name").asText()).isEqualTo("OBJ_CRED");
        assertThat(json.get("profile_name").asText()).isEqualTo("RAG_PROFILE");
        assertThat(json.get("vector_dimension").asInt()).isEqualTo(1536);
        assertThat(json.has("chunk_size")).isFalse();
        assertThat(json.has("chunk_overlap")).isFalse();
        assertThat(json.has("match_limit")).isFalse();
        assertThat(json.has("refresh_rate")).isFalse();
        assertThat(json.has("similarity_threshold")).isFalse();
        assertThat(json.has("vector_distance_metric")).isFalse();
        assertThat(json.has("enable_sources")).isFalse();
        assertThat(json.has("pipeline_name")).isFalse();

        VectorIndexAttributes attributesWithSources = VectorIndexAttributes.builder()
                .location("https://object.example/bucket/docs/")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .enableSources(true)
                .build();

        JsonNode jsonWithSources = MAPPER.readTree(attributesWithSources.toJson());
        assertThat(jsonWithSources.get("enable_sources").asBoolean()).isTrue();
    }

    /**
     * Test: Serializes vector-index update attributes without create defaults.
     * Expected: The update payload contains only the requested update values.
     */
    @Test
    void vectorIndexUpdateAttributesDoNotUseCreateDefaults() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.updateBuilder()
                .matchLimit(8)
                .pipelineName("PIPELINE_1")
                .refreshRate(720)
                .similarityThreshold(1.0)
                .build();

        assertThat(attributes.getLocation()).isNull();
        assertThat(attributes.getObjectStorageCredentialName()).isNull();
        assertThat(attributes.getProfileName()).isNull();
        assertThat(attributes.getChunkSize()).isNull();
        assertThat(attributes.getChunkOverlap()).isNull();
        assertThat(attributes.getVectorDistanceMetric()).isNull();
        assertThat(attributes.getVectorDbProvider()).isNull();
        assertThat(attributes.getMatchLimit()).isEqualTo(8);
        assertThat(attributes.getPipelineName()).isEqualTo("PIPELINE_1");
        assertThat(attributes.getRefreshRate()).isEqualTo(720);
        assertThat(attributes.getSimilarityThreshold()).isEqualTo(1);

        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.get("match_limit").asInt()).isEqualTo(8);
        assertThat(json.get("pipeline_name").asText()).isEqualTo("PIPELINE_1");
        assertThat(json.get("refresh_rate").asInt()).isEqualTo(720);
        assertThat(json.get("similarity_threshold").asInt()).isEqualTo(1);
        assertThat(json.has("chunk_size")).isFalse();
        assertThat(json.has("location")).isFalse();
        assertThat(json.has("vector_distance_metric")).isFalse();

        assertThat(MAPPER.readTree(VectorIndexAttributes.updateBuilder().build().toJson()))
                .hasSize(0);
    }

    /**
     * Test: Delegates vector-index required and numeric attribute validation to the database.
     * Expected: Missing fields and database-specific numeric values are represented by the
     * Java payload; only the database-managed pipeline name is rejected during creation.
     */
    @Test
    void vectorIndexAttributesDelegateMandatoryAndNumericValidationToDatabase() {
        VectorIndexAttributes missingLocation = VectorIndexAttributes.builder()
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .build();
        assertThat(missingLocation.getLocation()).isNull();

        VectorIndexAttributes missingCredential = VectorIndexAttributes.builder()
                .location("https://object.example")
                .profileName("RAG_PROFILE")
                .build();
        assertThat(missingCredential.getObjectStorageCredentialName()).isNull();

        VectorIndexAttributes missingProfile = VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .build();
        assertThat(missingProfile.getProfileName()).isNull();

        VectorIndexAttributes missingProvider = VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .build();
        assertThat(missingProvider.getVectorDbProvider()).isNull();

        assertThatThrownBy(() -> VectorIndexAttributes.builder().pipelineName("PIPELINE_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pipelineName");

        VectorIndexAttributes numericValues = VectorIndexAttributes.builder()
                .chunkSize(0)
                .chunkOverlap(-1)
                .matchLimit(0)
                .refreshRate(0)
                .similarityThreshold(-1.0)
                .vectorDimension(0)
                .build();

        assertThat(numericValues.getChunkSize()).isEqualTo(0);
        assertThat(numericValues.getChunkOverlap()).isEqualTo(-1);
        assertThat(numericValues.getMatchLimit()).isEqualTo(0);
        assertThat(numericValues.getRefreshRate()).isEqualTo(0);
        assertThat(numericValues.getSimilarityThreshold()).isEqualTo(-1.0);
        assertThat(numericValues.getVectorDimension()).isEqualTo(0);
    }

    /**
     * Test: Normalizes a vector-index name and validates its status.
     * Expected: Names are normalized and unsupported statuses are rejected.
     */
    @Test
    void vectorIndexConfigNormalizesNameAndValidatesStatus() {
        VectorIndexConfig config = VectorIndexConfig.builder(" RAG_IDX_1 ")
                .description("Product docs")
                .status("enabled")
                .waitForCompletion(true)
                .build();

        assertThat(config.getIndexName()).isEqualTo("RAG_IDX_1");
        assertThat(config.getDescription()).isEqualTo("Product docs");
        assertThat(config.getStatus()).isEqualTo(VectorIndexConfig.Status.ENABLED);
        assertThat(config.getStatusValue()).isEqualTo("Enabled");
        assertThat(config.isWaitForCompletion()).isTrue();

        assertThat(VectorIndexConfig.Status.fromValue(null)).isEqualTo(VectorIndexConfig.Status.DISABLED);
        assertThat(VectorIndexConfig.Status.fromValue(" disabled "))
                .isEqualTo(VectorIndexConfig.Status.DISABLED);

        VectorIndexConfig longNameConfig = VectorIndexConfig.builder("X".repeat(150)).build();
        assertThat(longNameConfig.getIndexName()).hasSize(150);

        VectorIndexConfig databaseValidatedNameConfig = VectorIndexConfig.builder("1_BAD").build();
        assertThat(databaseValidatedNameConfig.getIndexName()).isEqualTo("1_BAD");

        assertThatThrownBy(() -> VectorIndexConfig.builder("IDX").status("paused"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enabled or Disabled");
    }
}
