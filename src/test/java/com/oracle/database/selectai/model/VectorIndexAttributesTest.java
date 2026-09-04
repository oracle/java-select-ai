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

class VectorIndexAttributesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Builds create attributes with normalized required strings and unset optionals.
     * Expected: Required values are normalized and optional defaults remain unset.
     */
    @Test
    void createBuilderLeavesOptionalDefaultsToDatabaseAndNormalizesRequiredStrings() {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("  https://object.example/bucket/docs/  ")
                .objectStorageCredentialName("  OBJ_CRED  ")
                .profileName("  RAG_PROFILE  ")
                .vectorDbProvider(" oracle ")
                .vectorDimension(1536)
                .build();

        assertThat(attributes.getLocation()).isEqualTo("https://object.example/bucket/docs/");
        assertThat(attributes.getObjectStorageCredentialName()).isEqualTo("OBJ_CRED");
        assertThat(attributes.getProfileName()).isEqualTo("RAG_PROFILE");
        assertThat(attributes.getChunkSize()).isNull();
        assertThat(attributes.getChunkOverlap()).isNull();
        assertThat(attributes.getMatchLimit()).isNull();
        assertThat(attributes.getRefreshRate()).isNull();
        assertThat(attributes.getSimilarityThreshold()).isNull();
        assertThat(attributes.getVectorDistanceMetric()).isNull();
        assertThat(attributes.getVectorDbProvider()).isEqualTo("oracle");
        assertThat(attributes.getVectorDimension()).isEqualTo(1536);
        assertThat(attributes.getEnableSources()).isNull();
    }

    /**
     * Test: Prevents callers from supplying the database-managed pipeline name.
     * Expected: A pipeline name supplied during creation is rejected.
     */
    @Test
    void createBuilderRejectsPipelineName() {
        assertThatThrownBy(() ->
                VectorIndexAttributes.builder()
                        .pipelineName("PIPELINE_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pipelineName");
    }

    /**
     * Test: Serializes create attributes using database field names.
     * Expected: JSON uses snake_case and omits null values.
     */
    @Test
    void createPayloadSerializesSnakeCaseAndOmitsNullValues() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example/bucket/docs/")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .vectorDimension(1536)
                .build();

        JsonNode json = MAPPER.readTree(attributes.toJson());

        assertThat(json.get("location").asText()).isEqualTo("https://object.example/bucket/docs/");
        assertThat(json.get("object_storage_credential_name").asText()).isEqualTo("OBJ_CRED");
        assertThat(json.get("profile_name").asText()).isEqualTo("RAG_PROFILE");
        assertThat(json.get("vector_db_provider").asText()).isEqualTo("oracle");
        assertThat(json.get("vector_dimension").asInt()).isEqualTo(1536);
        assertThat(json.has("chunk_size")).isFalse();
        assertThat(json.has("chunk_overlap")).isFalse();
        assertThat(json.has("match_limit")).isFalse();
        assertThat(json.has("refresh_rate")).isFalse();
        assertThat(json.has("similarity_threshold")).isFalse();
        assertThat(json.has("vector_distance_metric")).isFalse();
        assertThat(json.has("enable_sources")).isFalse();
    }

    /**
     * Test: Serializes the enable-sources create attribute when supplied.
     * Expected: JSON contains the configured enable_sources value.
     */
    @Test
    void enableSourcesIsSerializedWhenProvided() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example/bucket/docs/")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .enableSources(true)
                .build();

        JsonNode json = MAPPER.readTree(attributes.toJson());

        assertThat(json.get("enable_sources").asBoolean()).isTrue();
    }

    /**
     * Test: Serializes the remaining optional create attributes without omission.
     * Expected: Every configured field uses its corresponding database JSON name and value.
     */
    @Test
    void createPayloadSerializesAllOptionalAttributes() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example/bucket/docs/")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .chunkSize(512)
                .chunkOverlap(32)
                .enableSources(false)
                .matchLimit(5)
                .refreshRate(60)
                .similarityThreshold(0.7)
                .vectorDistanceMetric("cosine")
                .vectorDimension(1536)
                .vectorTableName("RAG_VECTORS")
                .build();

        JsonNode json = MAPPER.readTree(attributes.toJson());

        assertThat(json.get("chunk_size").asInt()).isEqualTo(512);
        assertThat(json.get("chunk_overlap").asInt()).isEqualTo(32);
        assertThat(json.get("enable_sources").asBoolean()).isFalse();
        assertThat(json.get("match_limit").asInt()).isEqualTo(5);
        assertThat(json.get("refresh_rate").asInt()).isEqualTo(60);
        assertThat(json.get("similarity_threshold").asDouble()).isEqualTo(0.7);
        assertThat(json.get("vector_distance_metric").asText()).isEqualTo("cosine");
        assertThat(json.get("vector_dimension").asInt()).isEqualTo(1536);
        assertThat(json.get("vector_table_name").asText()).isEqualTo("RAG_VECTORS");
    }

    /**
     * Test: Serializes update-only pipeline metadata.
     * Expected: The pipeline name is accepted only in an update payload and uses snake_case JSON.
     */
    @Test
    void updatePayloadSerializesPipelineName() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.updateBuilder()
                .pipelineName("PIPELINE_1")
                .build();

        assertThat(attributes.getPipelineName()).isEqualTo("PIPELINE_1");
        assertThat(MAPPER.readTree(attributes.toJson()).get("pipeline_name").asText())
                .isEqualTo("PIPELINE_1");
    }

    /**
     * Test: Builds update attributes without applying create-time defaults.
     * Expected: The update payload contains only explicitly configured values.
     */
    @Test
    void updateBuilderDoesNotUseCreateDefaults() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.updateBuilder()
                .matchLimit(8)
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
        assertThat(attributes.getRefreshRate()).isEqualTo(720);
        assertThat(attributes.getSimilarityThreshold()).isEqualTo(1);

        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.get("match_limit").asInt()).isEqualTo(8);
        assertThat(json.get("refresh_rate").asInt()).isEqualTo(720);
        assertThat(json.get("similarity_threshold").asInt()).isEqualTo(1);
        assertThat(json.has("chunk_size")).isFalse();
        assertThat(json.has("location")).isFalse();
        assertThat(json.has("vector_distance_metric")).isFalse();
    }

    /**
     * Test: Represents a decimal similarity threshold in typed attributes.
     * Expected: The decimal value is retained and serialized accurately.
     */
    @Test
    void typedBuilderSupportsDecimalSimilarityThreshold() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.updateBuilder()
                .similarityThreshold(0.7)
                .build();

        assertThat(attributes.getSimilarityThreshold()).isEqualTo(0.7);

        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.get("similarity_threshold").asDouble()).isEqualTo(0.7);
    }

    /**
     * Test: Builds an empty update payload without injecting attributes.
     * Expected: An empty update configuration is represented as an empty JSON object;
     * database-side validation remains responsible for deciding whether it is usable.
     */
    @Test
    void updateBuilderAllowsEmptyAttributePayload() throws Exception {
        VectorIndexAttributes attributes = VectorIndexAttributes.updateBuilder().build();

        assertThat(MAPPER.readTree(attributes.toJson())).hasSize(0);
    }

    /**
     * Test: Builds vector-index creation attributes without a location.
     * Expected: The location remains unset for database-side validation.
     */
    @Test
    void createBuilderAllowsMissingLocation() {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .build();

        assertThat(attributes.getLocation()).isNull();
    }

    /**
     * Test: Builds vector-index creation attributes without an object-storage credential.
     * Expected: The credential name remains unset for database-side validation.
     */
    @Test
    void createBuilderAllowsMissingObjectStorageCredentialName() {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example")
                .profileName("RAG_PROFILE")
                .build();

        assertThat(attributes.getObjectStorageCredentialName()).isNull();
    }

    /**
     * Test: Builds vector-index creation attributes without a profile name.
     * Expected: The profile name remains unset for database-side validation.
     */
    @Test
    void createBuilderAllowsMissingProfileName() {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .build();

        assertThat(attributes.getProfileName()).isNull();
    }

    /**
     * Test: Builds vector-index creation attributes without a vector database provider.
     * Expected: The provider remains unset for database-side validation.
     */
    @Test
    void createBuilderAllowsMissingVectorDbProvider() {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .build();

        assertThat(attributes.getVectorDbProvider()).isNull();
    }

    /**
     * Test: Preserves null vector-index creation values.
     * Expected: Null values remain unset and are not rejected by the Java attribute model.
     */
    @Test
    void createBuilderPreservesNullValues() {
        assertThat(VectorIndexAttributes.builder()
                .location(null)
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build().getLocation()).isNull();

        assertThat(VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName(null)
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build().getObjectStorageCredentialName()).isNull();

        assertThat(VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName(null)
                .vectorDbProvider("oracle")
                .build().getProfileName()).isNull();

        assertThat(VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider(null)
                .build().getVectorDbProvider()).isNull();
    }

    /**
     * Test: Normalizes blank vector-index creation strings without rejecting them.
     * Expected: Blank values become unset values for database-side validation.
     */
    @Test
    void createBuilderNormalizesBlankValues() {
        assertThat(VectorIndexAttributes.builder()
                .location(" ")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build().getLocation()).isNull();

        assertThat(VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName(" ")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build().getObjectStorageCredentialName()).isNull();

        assertThat(VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName(" ")
                .vectorDbProvider("oracle")
                .build().getProfileName()).isNull();

        assertThat(VectorIndexAttributes.builder()
                .location("https://object.example")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider(" ")
                .build().getVectorDbProvider()).isNull();
    }

    /**
     * Test: Passes vector-index numeric attributes through to the database.
     * Expected: Java does not reject or rewrite values such as zero or negative values;
     * database-side validation remains authoritative.
     */
    @Test
    void numericAttributesArePassedThroughWithoutSdkValidation() {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .chunkSize(0)
                .chunkOverlap(-1)
                .matchLimit(0)
                .refreshRate(0)
                .similarityThreshold(-1.0)
                .vectorDimension(0)
                .build();

        assertThat(attributes.getChunkSize()).isEqualTo(0);
        assertThat(attributes.getChunkOverlap()).isEqualTo(-1);
        assertThat(attributes.getMatchLimit()).isEqualTo(0);
        assertThat(attributes.getRefreshRate()).isEqualTo(0);
        assertThat(attributes.getSimilarityThreshold()).isEqualTo(-1.0);
        assertThat(attributes.getVectorDimension()).isEqualTo(0);
    }
}
