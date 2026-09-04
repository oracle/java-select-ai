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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileAttributesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Test: Parses typed profile values from an attribute map.
     * Expected: Each supported value is converted to its corresponding Java type.
     */
    @Test
    void fromAttributeMapParsesTypedValues() {
        ProfileAttributes attributes = ProfileAttributes.fromAttributeMap(Map.of(
                "credential_name", "OCI_CRED",
                "provider", "oci",
                "max_tokens", "2048",
                "temperature", "0.7",
                "conversation", "true",
                "conversation_length", "4",
                "seed", "12345",
                "object_list_mode", "automated",
                "additional_instructions", "Use concise answers",
                "role", "You are a database assistant"
        ));

        assertThat(attributes.getCredentialName()).isEqualTo("OCI_CRED");
        assertThat(attributes.getProvider()).isEqualTo("oci");
        assertThat(attributes.getMaxTokens()).isEqualTo(2048);
        assertThat(attributes.getTemperature()).isEqualTo(0.7);
        assertThat(attributes.getConversation()).isTrue();
        assertThat(attributes.getConversationLength()).isEqualTo(4);
        assertThat(attributes.getSeed()).isEqualTo(12345L);
        assertThat(attributes.getObjectListMode()).isEqualTo("automated");
        assertThat(attributes.getAdditionalInstructions()).isEqualTo("Use concise answers");
        assertThat(attributes.getRole()).isEqualTo("You are a database assistant");
    }

    /**
     * Test: Preserves stop tokens through map parsing and JSON serialization.
     * Expected: Stop-token values remain unchanged in both representations.
     */
    @Test
    void stopTokensSurviveAttributeMapAndJsonSerialization() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .stopTokens(List.of("END_OF_RESPONSE", "STOP"))
                .build();

        assertThat(attributes.toAttributeMap())
                .containsEntry("stop_tokens", "[\"END_OF_RESPONSE\",\"STOP\"]");
        JsonNode stopTokensJson = MAPPER.readTree(attributes.toJson()).get("stop_tokens");
        assertThat(stopTokensJson.isArray()).isTrue();
        assertThat(stopTokensJson.get(0).asText()).isEqualTo("END_OF_RESPONSE");
        assertThat(stopTokensJson.get(1).asText()).isEqualTo("STOP");

        ProfileAttributes reloaded = ProfileAttributes.fromAttributeMap(attributes.toAttributeMap());
        assertThat(reloaded.getStopTokens())
                .containsExactly("END_OF_RESPONSE", "STOP");
    }

    /**
     * Test: Preserves explicitly supplied empty attribute values in JSON.
     * Expected: Empty strings and empty lists are emitted because only null values are omitted.
     */
    @Test
    void toJsonPreservesExplicitEmptyAttributeValues() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .additionalInstructions("")
                .stopTokens(List.of())
                .build();

        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.has("additional_instructions")).isTrue();
        assertThat(json.get("additional_instructions").asText()).isEmpty();
        assertThat(json.has("stop_tokens")).isTrue();
        assertThat(json.get("stop_tokens")).isEmpty();
    }

    /**
     * Test: Normalizes profile attributes independently of the default locale.
     * Expected: Attribute normalization remains stable under locale changes.
     */
    @Test
    void attributeNormalizationIsIndependentOfDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            ProfileAttributes attributes = ProfileAttributes.fromAttributeMap(Map.of(
                    "PROVIDER", "OPENAI",
                    "CREDENTIAL_NAME", "OPENAI_CRED",
                    "OCI_APIFORMAT", "generic"));

            assertThat(attributes.getProvider()).isEqualTo("openai");
            assertThat(attributes.getCredentialName()).isEqualTo("OPENAI_CRED");
            assertThat(attributes.getOciApiformat()).isEqualTo("GENERIC");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Test: Normalizes a mixed-case provider supplied through the public builder.
     * Expected: The model and its GENERATE JSON payload contain the lower-case provider value.
     */
    @Test
    void builderNormalizesMixedCaseProviderForJsonPayload() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .provider("OpEnAi")
                .build();

        assertThat(attributes.getProvider()).isEqualTo("openai");
        assertThat(MAPPER.readTree(attributes.toJson()).get("provider").asText())
                .isEqualTo("openai");
    }

    /**
     * Test: Reloads database attributes without adding SDK defaults.
     * Expected: Only attributes supplied by the database are represented.
     */
    @Test
    void databaseAttributesDoNotGainSdkDefaultsWhenReloaded() {
        ProfileAttributes attributes = ProfileAttributes.fromAttributeMap(Map.of(
                "provider", "oci",
                "credential_name", "OCI_CRED"));

        assertThat(attributes.getProvider()).isEqualTo("oci");
        assertThat(attributes.getCredentialName()).isEqualTo("OCI_CRED");
        assertThat(attributes.getProviderEndpoint()).isNull();
        assertThat(attributes.getRegion()).isNull();
        assertThat(attributes.getMaxTokens()).isNull();
        assertThat(attributes.toAttributeMap())
                .containsOnlyKeys("provider", "credential_name");
        assertThat(attributes.toJson())
                .doesNotContain("source_language")
                .doesNotContain("target_language");
    }

    /**
     * Test: Preserves database attributes that are not modeled by the SDK.
     * Expected: Unknown values round-trip through the attribute map but are not emitted as nested JSON.
     */
    @Test
    void preservesUnknownAttributesForAttributeMapRoundTrip() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.fromAttributeMap(Map.of(
                "provider", "oci",
                "future_attribute", "future-value"));

        assertThat(attributes.getCustomAttributes())
                .containsEntry("future_attribute", "future-value");
        assertThat(attributes.toAttributeMap())
                .containsEntry("future_attribute", "future-value")
                .containsEntry("provider", "oci");
        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.has("custom_attributes")).isFalse();
        assertThat(json.has("future_attribute")).isFalse();
    }

    /**
     * Test: Builds minimal OpenAI profile attributes.
     * Expected: Only the explicitly supplied provider, credential, and model are retained.
     */
    @Test
    void openAiMinimalAttributesDoNotPopulateEndpoint() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OPENAI_CRED")
                .provider("openai")
                .model("gpt-test")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "OPENAI_CRED",
                "provider", "openai",
                "model", "gpt-test"));
    }

    /**
     * Test: Builds minimal Cohere profile attributes.
     * Expected: Only the explicitly supplied provider, credential, and model are retained.
     */
    @Test
    void cohereMinimalAttributesDoNotPopulateEndpoint() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("COHERE_CRED")
                .provider("cohere")
                .model("command-test")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "COHERE_CRED",
                "provider", "cohere",
                "model", "command-test"));
    }

    /**
     * Test: Builds minimal Azure profile attributes.
     * Expected: Only explicitly supplied Azure fields are retained.
     */
    @Test
    void azureMinimalAttributesDoNotPopulateEndpoint() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("AZURE_CRED")
                .provider("azure")
                .azureResourceName("azure-resource")
                .azureDeploymentName("azure-deployment")
                .azureEmbeddingDeploymentName("azure-embedding-deployment")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "AZURE_CRED",
                "provider", "azure",
                "azure_resource_name", "azure-resource",
                "azure_deployment_name", "azure-deployment",
                "azure_embedding_deployment_name", "azure-embedding-deployment"));
    }

    /**
     * Test: Builds minimal AWS profile attributes.
     * Expected: Only the explicitly supplied provider, credential, region, and model are retained.
     */
    @Test
    void awsMinimalAttributesDoNotPopulateEndpoint() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("AWS_CRED")
                .provider("aws")
                .region("us-east-1")
                .model("aws-model")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "AWS_CRED",
                "provider", "aws",
                "region", "us-east-1",
                "model", "aws-model"));
    }

    /**
     * Test: Builds minimal Google profile attributes.
     * Expected: Only the explicitly supplied provider, credential, and model are retained.
     */
    @Test
    void googleMinimalAttributesDoNotPopulateEndpoint() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("GOOGLE_CRED")
                .provider("google")
                .model("gemini-test")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "GOOGLE_CRED",
                "provider", "google",
                "model", "gemini-test"));
    }

    /**
     * Test: Builds minimal Anthropic profile attributes.
     * Expected: Only the explicitly supplied provider, credential, and model are retained.
     */
    @Test
    void anthropicMinimalAttributesDoNotPopulateEndpoint() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("ANTHROPIC_CRED")
                .provider("anthropic")
                .model("claude-test")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "ANTHROPIC_CRED",
                "provider", "anthropic",
                "model", "claude-test"));
    }

    /**
     * Test: Builds minimal Hugging Face profile attributes.
     * Expected: Only the explicitly supplied provider, credential, and model are retained.
     */
    @Test
    void huggingFaceMinimalAttributesDoNotPopulateEndpoint() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("HUGGINGFACE_CRED")
                .provider("huggingface")
                .model("huggingface-model")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "HUGGINGFACE_CRED",
                "provider", "huggingface",
                "model", "huggingface-model"));
    }

    /**
     * Test: Builds minimal OCI profile attributes without populating unspecified fields.
     * Expected: Only explicitly supplied OCI attributes are present before database access.
     */
    @Test
    void ociMinimalAttributesDoNotPopulateUnspecifiedFieldsBeforeDatabaseCall() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .region("us-chicago-1")
                .ociApiformat("GENERIC")
                .ociCompartmentId("ocid1.compartment.oc1..example")
                .model("oci-model")
                .build();

        assertOnlyExplicitAttributes(attributes, Map.of(
                "credential_name", "OCI_CRED",
                "provider", "oci",
                "region", "us-chicago-1",
                "oci_apiformat", "GENERIC",
                "oci_compartment_id", "ocid1.compartment.oc1..example",
                "model", "oci-model"));
    }

    private static void assertOnlyExplicitAttributes(ProfileAttributes attributes,
                                                      Map<String, String> expectedAttributes) {
        Map<String, Object> allAttributes = new LinkedHashMap<>();
        allAttributes.put("annotations", attributes.getAnnotations());
        allAttributes.put("azure_deployment_name", attributes.getAzureDeploymentName());
        allAttributes.put("azure_embedding_deployment_name", attributes.getAzureEmbeddingDeploymentName());
        allAttributes.put("azure_resource_name", attributes.getAzureResourceName());
        allAttributes.put("case_sensitive_values", attributes.getCaseSensitiveValues());
        allAttributes.put("comments", attributes.getComments());
        allAttributes.put("constraints", attributes.getConstraints());
        allAttributes.put("conversation", attributes.getConversation());
        allAttributes.put("conversation_length", attributes.getConversationLength());
        allAttributes.put("credential_name", attributes.getCredentialName());
        allAttributes.put("embedding_model", attributes.getEmbeddingModel());
        allAttributes.put("enable_custom_source_uri", attributes.getEnableCustomSourceUri());
        allAttributes.put("enforce_object_list", attributes.getEnforceObjectList());
        allAttributes.put("max_tokens", attributes.getMaxTokens());
        allAttributes.put("model", attributes.getModel());
        allAttributes.put("object_list", attributes.getObjectList());
        allAttributes.put("object_list_mode", attributes.getObjectListMode());
        allAttributes.put("oci_apiformat", attributes.getOciApiformat());
        allAttributes.put("oci_compartment_id", attributes.getOciCompartmentId());
        allAttributes.put("oci_endpoint_id", attributes.getOciEndpointId());
        allAttributes.put("oci_runtimetype", attributes.getOciRuntimetype());
        allAttributes.put("provider", attributes.getProvider());
        allAttributes.put("provider_endpoint", attributes.getProviderEndpoint());
        allAttributes.put("region", attributes.getRegion());
        allAttributes.put("seed", attributes.getSeed());
        allAttributes.put("stop_tokens", attributes.getStopTokens());
        allAttributes.put("temperature", attributes.getTemperature());
        allAttributes.put("vector_index_name", attributes.getVectorIndexName());
        allAttributes.put("source_language", attributes.getSourceLanguage());
        allAttributes.put("target_language", attributes.getTargetLanguage());

        Map<String, Object> nonNullAttributes = new LinkedHashMap<>(allAttributes);
        nonNullAttributes.entrySet().removeIf(entry -> entry.getValue() == null);

        assertThat(nonNullAttributes.keySet())
                .containsExactlyInAnyOrderElementsOf(expectedAttributes.keySet());
        assertThat(attributes.getCustomAttributes()).isEmpty();
        assertThat(attributes.toAttributeMap())
                .containsOnlyKeys(expectedAttributes.keySet().toArray(new String[0]));
        expectedAttributes.forEach((name, value) ->
                assertThat(attributes.toAttributeMap()).containsEntry(name, value));
    }

    /**
     * Test: Captures the extended profile-attribute field matrix.
     * Expected: Every configured field is retained with its corresponding value.
     */
    @Test
    void builderCapturesExtendedProfileMatrixFields() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .providerEndpoint("selectai.example.com")
                .model("cohere.command-r-plus")
                .additionalInstructions("Use concise answers")
                .embeddingModel("cohere.embed-english-v3.0")
                .annotations(true)
                .caseSensitiveValues(true)
                .comments(true)
                .constraints(true)
                .conversation(true)
                .conversationLength(12)
                .enableCustomSourceUri(true)
                .enforceObjectList(true)
                .maxTokens(2048)
                .ociEndpointId("ocid1.generativeaiendpoint.oc1..example")
                .ociRuntimetype("COHERE")
                .region("us-ashburn-1")
                .role("You are a database assistant")
                .temperature(0.25)
                .vectorIndexName("RAG_IDX")
                .sourceLanguage("English")
                .targetLanguage("French")
                .build();

        assertThat(attributes.getCredentialName()).isEqualTo("OCI_CRED");
        assertThat(attributes.getProvider()).isEqualTo("oci");
        assertThat(attributes.getProviderEndpoint()).isEqualTo("selectai.example.com");
        assertThat(attributes.getModel()).isEqualTo("cohere.command-r-plus");
        assertThat(attributes.getAdditionalInstructions()).isEqualTo("Use concise answers");
        assertThat(attributes.getEmbeddingModel()).isEqualTo("cohere.embed-english-v3.0");
        assertThat(attributes.getAnnotations()).isTrue();
        assertThat(attributes.getCaseSensitiveValues()).isTrue();
        assertThat(attributes.getComments()).isTrue();
        assertThat(attributes.getConstraints()).isTrue();
        assertThat(attributes.getConversation()).isTrue();
        assertThat(attributes.getConversationLength()).isEqualTo(12);
        assertThat(attributes.getEnableCustomSourceUri()).isTrue();
        assertThat(attributes.getEnforceObjectList()).isTrue();
        assertThat(attributes.getMaxTokens()).isEqualTo(2048);
        assertThat(attributes.getOciEndpointId()).isEqualTo("ocid1.generativeaiendpoint.oc1..example");
        assertThat(attributes.getOciRuntimetype()).isEqualTo("COHERE");
        assertThat(attributes.getRegion()).isEqualTo("us-ashburn-1");
        assertThat(attributes.getRole()).isEqualTo("You are a database assistant");
        assertThat(attributes.getTemperature()).isEqualTo(0.25);
        assertThat(attributes.getVectorIndexName()).isEqualTo("RAG_IDX");
        assertThat(attributes.getSourceLanguage()).isEqualTo("English");
        assertThat(attributes.getTargetLanguage()).isEqualTo("French");

        assertThat(attributes.toAttributeMap())
                .containsEntry("annotations", "true")
                .containsEntry("case_sensitive_values", "true")
                .containsEntry("comments", "true")
                .containsEntry("constraints", "true")
                .containsEntry("conversation", "true")
                .containsEntry("conversation_length", "12")
                .containsEntry("enable_custom_source_uri", "true")
                .containsEntry("enforce_object_list", "true")
                .containsEntry("embedding_model", "cohere.embed-english-v3.0")
                .containsEntry("additional_instructions", "Use concise answers")
                .containsEntry("max_tokens", "2048")
                .containsEntry("oci_endpoint_id", "ocid1.generativeaiendpoint.oc1..example")
                .containsEntry("oci_runtimetype", "COHERE")
                .containsEntry("provider_endpoint", "selectai.example.com")
                .containsEntry("region", "us-ashburn-1")
                .containsEntry("role", "You are a database assistant")
                .containsEntry("temperature", "0.25")
                .containsEntry("vector_index_name", "RAG_IDX")
                .containsEntry("source_language", "English")
                .containsEntry("target_language", "French");

        JsonNode json = MAPPER.readTree(attributes.toJson());
        assertThat(json.get("annotations").asBoolean()).isTrue();
        assertThat(json.get("conversation").asBoolean()).isTrue();
        assertThat(json.get("conversation_length").asInt()).isEqualTo(12);
        assertThat(json.get("embedding_model").asText()).isEqualTo("cohere.embed-english-v3.0");
        assertThat(json.get("additional_instructions").asText()).isEqualTo("Use concise answers");
        assertThat(json.get("oci_endpoint_id").asText()).isEqualTo("ocid1.generativeaiendpoint.oc1..example");
        assertThat(json.get("provider_endpoint").asText()).isEqualTo("selectai.example.com");
        assertThat(json.get("role").asText()).isEqualTo("You are a database assistant");
        assertThat(json.get("vector_index_name").asText()).isEqualTo("RAG_IDX");
        assertThat(json.get("source_language").asText()).isEqualTo("English");
        assertThat(json.get("target_language").asText()).isEqualTo("French");
    }

    /**
     * Test: Applies only the supported SDK-level profile-attribute checks.
     * Expected: Enum values and supported numeric lower bounds are checked, while opaque
     * database-owned attribute values are retained for database validation.
     */
    @Test
    void validatesOnlyEnumAndNumericProfileAttributes() {
        ProfileAttributes opaqueAttributes = ProfileAttributes.builder()
                .credentialName(" ")
                .azureDeploymentName("bad_")
                .azureResourceName("-bad")
                .ociCompartmentId("ocid1.compartment/invalid")
                .providerEndpoint("bad endpoint")
                .region("us_chicago_1")
                .objectList("{\"owner\":\"HR\"}")
                .stopTokens(List.of("END", " "))
                .vectorIndexName("1_BAD")
                .build();

        assertThat(opaqueAttributes.getCredentialName()).isEqualTo(" ");
        assertThat(opaqueAttributes.getAzureDeploymentName()).isEqualTo("bad_");
        assertThat(opaqueAttributes.getAzureResourceName()).isEqualTo("-bad");
        assertThat(opaqueAttributes.getOciCompartmentId())
                .isEqualTo("ocid1.compartment/invalid");
        assertThat(opaqueAttributes.getProviderEndpoint()).isEqualTo("bad endpoint");
        assertThat(opaqueAttributes.getRegion()).isEqualTo("us_chicago_1");
        assertThat(opaqueAttributes.getObjectList()).isEqualTo("{\"owner\":\"HR\"}");
        assertThat(opaqueAttributes.getStopTokens()).containsExactly("END", " ");
        assertThat(opaqueAttributes.getVectorIndexName()).isEqualTo("1_BAD");

        assertThatThrownBy(() -> ProfileAttributes.builder().maxTokens(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTokens");

        assertThatThrownBy(() -> ProfileAttributes.builder().conversationLength(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationLength");

        assertThatThrownBy(() -> ProfileAttributes.builder().temperature(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");

        assertThatThrownBy(() -> ProfileAttributes.builder().provider("unsupported-provider"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");

        assertThatThrownBy(() -> ProfileAttributes.builder().objectListMode("unsupported-mode"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectListMode");

    assertThatThrownBy(() -> ProfileAttributes.builder().ociApiformat("unsupported-format"))
            .isInstanceOf(IllegalArgumentException.class);
}

/**
 * Test: Normalizes object-list mode when supplied through the builder.
 * Expected: The builder stores the documented lower-case wire value.
 */
@Test
void builderNormalizesMixedCaseObjectListMode() {
    ProfileAttributes attributes = ProfileAttributes.builder()
            .objectListMode("ALL")
            .build();

    assertThat(attributes.getObjectListMode()).isEqualTo("all");
}

/**
 * Test: Preserves the documented signed 64-bit seed range through map and JSON conversion.
 * Expected: Both signed 64-bit endpoints are retained without narrowing or overflow.
 */
@Test
void seedSupportsSigned64BitBoundaries() throws Exception {
    ProfileAttributes minimum = ProfileAttributes.builder()
            .seed(Long.MIN_VALUE)
            .build();
    ProfileAttributes maximum = ProfileAttributes.fromAttributeMap(Map.of(
            "seed", String.valueOf(Long.MAX_VALUE)));

    assertThat(minimum.getSeed()).isEqualTo(Long.MIN_VALUE);
    assertThat(minimum.toAttributeMap())
            .containsEntry("seed", String.valueOf(Long.MIN_VALUE));
    assertThat(MAPPER.readTree(minimum.toJson()).get("seed").asLong())
            .isEqualTo(Long.MIN_VALUE);

    assertThat(maximum.getSeed()).isEqualTo(Long.MAX_VALUE);
    assertThat(maximum.toAttributeMap())
            .containsEntry("seed", String.valueOf(Long.MAX_VALUE));
    assertThat(MAPPER.readTree(maximum.toJson()).get("seed").asLong())
            .isEqualTo(Long.MAX_VALUE);
}
}
