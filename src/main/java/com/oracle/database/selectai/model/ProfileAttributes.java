/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Attribute payload for {@code DBMS_CLOUD_AI.CREATE_PROFILE},
 * {@code SET_ATTRIBUTE}, and {@code SET_ATTRIBUTES}.
 * <p>
 * Profile attributes describe the provider, AI model, credential, object
 * selection, prompt behavior, conversation behavior, and RAG/vector-index
 * settings that Select AI uses when answering prompts. Most attributes map to
 * database-side Select AI profile settings; callers can build the payload once
 * for profile creation or reuse it for bulk updates.
 * <p>
 * For complete runnable sample sources that build {@code ProfileAttributes},
 * see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/CreateProfileSample.html">
 * CreateProfileSample source</a> and
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/profile/SetProfileAttributesSample.html">
 * SetProfileAttributesSample source</a>.
 *
 * <p>Commonly used profile attributes include:</p>
 *
 * <table>
 *   <caption>Common Select AI profile attributes</caption>
 *   <tr>
 *     <th>Attribute</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>{@code provider}</td>
 *     <td>AI provider used by the profile.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code credential_name}</td>
 *     <td>Credential used to access the AI provider, where required.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code model}</td>
 *     <td>AI model used by the profile.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code object_list}</td>
 *     <td>Database objects available for NL2SQL processing.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code object_list_mode}</td>
 *     <td>Controls how database objects are selected for NL2SQL processing.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code temperature}</td>
 *     <td>Controls randomness in model-generated responses.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code max_tokens}</td>
 *     <td>Specifies the maximum number of tokens to generate.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code vector_index_name}</td>
 *     <td>Specifies the vector index associated with the profile.</td>
 *   </tr>
 * </table>
 *
 * <p>
 * The SDK performs basic, deterministic validation of profile attributes where
 * the constraint can be validated independently of the configured provider or
 * database version. This includes validation of supported SDK-defined values,
 * numeric constraints, and basic input structure. Invalid values detected by
 * the SDK result in an {@link IllegalArgumentException}.
 *
 * <p>
 * Provider-specific, model-specific, database-version-specific, and other
 * database-side semantic validation is delegated to {@code DBMS_CLOUD_AI} and
 * Oracle Database. Therefore, an attribute value that passes SDK validation
 * may still be rejected by the database based on the selected provider, model,
 * database version, or other database-side requirements.
 *
 * <p>
 * Supported profile attributes, valid values, defaults, requirements, and
 * provider-specific behavior are defined by Oracle Database and may vary by
 * database version and provider. See the {@code DBMS_CLOUD_AI} Profile
 * Attributes documentation for the complete and current list.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html#GUID-12D91681-B51C-48E0-93FD-9ABC67B0F375">
 *      DBMS_CLOUD_AI Profile Attributes</a>
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Manage AI profiles</a>
 */
public final class ProfileAttributes {
    /** Include table/column annotations (26ai) in augmented metadata. Default: false. */
    private final Boolean annotations;
    /** Additional prompt instructions supplied to Select AI. */
    private final String additionalInstructions;
    /** Azure OpenAI deployment name for chat/completion model. */
    private final String azureDeploymentName;
    /** Azure OpenAI deployment name for embedding model. */
    private final String azureEmbeddingDeploymentName;
    /** Azure OpenAI resource name. */
    private final String azureResourceName;
    /** Whether SQL value matching should be case-sensitive. Default: false. */
    private final Boolean caseSensitiveValues;
    /** Include table and column comments in metadata sent to LLM. Default: false. */
    private final Boolean comments;
    /** Include referential constraints in metadata. Default: false. */
    private final Boolean constraints;
    /** Enable conversation history for profile. Default: false. */
    private final Boolean conversation;
    /** Number of past turns retained when conversation is enabled. */
    private final Integer conversationLength;
    /** Credential name used to call provider APIs, where required. */
    private final String credentialName;
    /** Embedding model name. */
    private final String embeddingModel;
    /** Enables custom source URL metadata for RAG citations. Default: false. */
    private final Boolean enableCustomSourceUri;
    /** Restrict SQL generation to objects listed in objectList. Default: false. */
    private final Boolean enforceObjectList;
    /** Maximum tokens generated per response. */
    private final Integer maxTokens;
    /** Provider model identifier. */
    private final String model;
    /** JSON array of objects eligible for NL2SQL (owner mandatory, name optional). */
    private final String objectList;
    /** Metadata selection mode: all or automated. */
    private final String objectListMode;
    /** OCI API format for dedicated endpoint/model OCID (COHERE|GENERIC). */
    private final String ociApiformat;
    /** OCI compartment OCID. */
    private final String ociCompartmentId;
    /** OCI dedicated endpoint OCID. */
    private final String ociEndpointId;
    /** OCI runtime type (deprecated in favor of oci_apiformat). */
    private final String ociRuntimetype;
    /** AI provider name (mandatory). */
    private final String provider;
    /** Provider endpoint host/path for OpenAI-compatible providers. */
    private final String providerEndpoint;
    /** Provider region. */
    private final String region;
    /** Role prompt supplied to Select AI. */
    private final String role;
    /** Optional deterministic sampling seed (signed 64-bit integer). */
    private final Long seed;
    /** Stop sequences for generation. */
    private final List<String> stopTokens;
    /** Non-negative sampling temperature. */
    private final Double temperature;
    /** Vector index name (Oracle SQL identifier, up to 125 chars). */
    private final String vectorIndexName;
    /** Source language for translation. */
    private final String sourceLanguage;
    /** Target language for translation. */
    private final String targetLanguage;
    /** Unmodeled attributes preserved for metadata round-tripping. */
    private final Map<String, String> customAttributes;

    private ProfileAttributes(Builder builder) {
        this.annotations = builder.annotations;
        this.additionalInstructions = builder.additionalInstructions;
        this.azureDeploymentName = builder.azureDeploymentName;
        this.azureEmbeddingDeploymentName = builder.azureEmbeddingDeploymentName;
        this.azureResourceName = builder.azureResourceName;
        this.caseSensitiveValues = builder.caseSensitiveValues;
        this.comments = builder.comments;
        this.constraints = builder.constraints;
        this.conversation = builder.conversation;
        this.conversationLength = builder.conversationLength;
        this.credentialName = builder.credentialName;
        this.embeddingModel = builder.embeddingModel;
        this.enableCustomSourceUri = builder.enableCustomSourceUri;
        this.enforceObjectList = builder.enforceObjectList;
        this.maxTokens = builder.maxTokens;
        this.model = builder.model;
        this.objectList = builder.objectList;
        this.objectListMode = builder.objectListMode;
        this.ociApiformat = builder.ociApiformat;
        this.ociCompartmentId = builder.ociCompartmentId;
        this.ociEndpointId = builder.ociEndpointId;
        this.ociRuntimetype = builder.ociRuntimetype;
        this.provider = builder.provider;
        this.providerEndpoint = builder.providerEndpoint;
        this.region = builder.region;
        this.role = builder.role;
        this.seed = builder.seed;
        this.stopTokens = builder.stopTokens == null ? null : List.copyOf(builder.stopTokens);
        this.temperature = builder.temperature;
        this.vectorIndexName = builder.vectorIndexName;
        this.sourceLanguage = builder.sourceLanguage;
        this.targetLanguage = builder.targetLanguage;
        this.customAttributes = Collections.unmodifiableMap(new HashMap<>(builder.customAttributes));
    }

    /**
     * Creates a builder for profile attributes.
     *
     * @return new builder for constructing ProfileAttributes
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether table/column annotations are included in metadata.
     *
     * @return annotations flag value, or {@code null} when unset
     */
    public Boolean getAnnotations() {
        return annotations;
    }

    /**
     * Returns additional instructions included in Select AI prompts.
     *
     * @return additional instructions, or {@code null} when unset
     */
    public String getAdditionalInstructions() {
        return additionalInstructions;
    }

    /**
     * Returns the Azure OpenAI deployment name.
     *
     * @return Azure deployment name, or {@code null} when unset
     */
    public String getAzureDeploymentName() {
        return azureDeploymentName;
    }

    /**
     * Returns the Azure OpenAI embedding deployment name.
     *
     * @return Azure embedding deployment name, or {@code null} when unset
     */
    public String getAzureEmbeddingDeploymentName() {
        return azureEmbeddingDeploymentName;
    }

    /**
     * Returns the Azure OpenAI resource name.
     *
     * @return Azure resource name, or {@code null} when unset
     */
    public String getAzureResourceName() {
        return azureResourceName;
    }

    /**
     * Returns whether generated SQL value matching is case-sensitive.
     *
     * @return case-sensitive-values flag, or {@code null} when unset
     */
    public Boolean getCaseSensitiveValues() {
        return caseSensitiveValues;
    }

    /**
     * Returns whether table and column comments are included in prompt metadata.
     *
     * @return comments flag, or {@code null} when unset
     */
    public Boolean getComments() {
        return comments;
    }

    /**
     * Returns whether referential constraints are included in prompt metadata.
     *
     * @return constraints flag, or {@code null} when unset
     */
    public Boolean getConstraints() {
        return constraints;
    }

    /**
     * Returns whether conversation history is enabled for the profile.
     *
     * @return conversation flag, or {@code null} when unset
     */
    public Boolean getConversation() {
        return conversation;
    }

    /**
     * Returns how many conversation turns are retained for context.
     *
     * @return configured conversation length, or {@code null} when unset
     */
    public Integer getConversationLength() {
        return conversationLength;
    }

    /**
     * Returns the credential name used for provider API calls.
     *
     * @return credential name, or {@code null} when unset
     */
    public String getCredentialName() {
        return credentialName;
    }

    /**
     * Returns the embedding model name.
     *
     * @return embedding model name, or {@code null} when unset
     */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Returns whether custom source URI metadata is enabled.
     *
     * @return custom-source-URI flag, or {@code null} when unset
     */
    public Boolean getEnableCustomSourceUri() {
        return enableCustomSourceUri;
    }

    /**
     * Returns whether SQL generation is restricted to {@code object_list}.
     *
     * @return enforce-object-list flag, or {@code null} when unset
     */
    public Boolean getEnforceObjectList() {
        return enforceObjectList;
    }

    /**
     * Returns the maximum generated output tokens.
     *
     * @return maximum tokens value, or {@code null} when unset
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Returns the provider model identifier.
     *
     * @return provider model identifier, or {@code null} when unset
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns the JSON object list supplied to Select AI.
     *
     * @return object-list JSON string, or {@code null} when unset
     */
    public String getObjectList() {
        return objectList;
    }

    /**
     * Returns how Select AI chooses objects for metadata.
     *
     * @return object-list mode value, or {@code null} when unset
     */
    public String getObjectListMode() {
        return objectListMode;
    }

    /**
     * Returns the OCI Generative AI API format.
     *
     * @return OCI API format value, or {@code null} when unset
     */
    public String getOciApiformat() {
        return ociApiformat;
    }

    /**
     * Returns the OCI compartment OCID.
     *
     * @return OCI compartment ID, or {@code null} when unset
     */
    public String getOciCompartmentId() {
        return ociCompartmentId;
    }

    /**
     * Returns the OCI dedicated endpoint OCID.
     *
     * @return OCI endpoint ID, or {@code null} when unset
     */
    public String getOciEndpointId() {
        return ociEndpointId;
    }

    /**
     * Returns the OCI runtime type attribute.
     *
     * @return OCI runtime type value, or {@code null} when unset
     */
    public String getOciRuntimetype() {
        return ociRuntimetype;
    }

    /**
     * Returns the AI provider name.
     *
     * @return provider name, or {@code null} when unset
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Returns the provider endpoint host/path.
     *
     * @return provider endpoint value, or {@code null} when unset
     */
    public String getProviderEndpoint() {
        return providerEndpoint;
    }

    /**
     * Returns the provider region.
     *
     * @return configured region value, or {@code null} when unset
     */
    public String getRegion() {
        return region;
    }

    /**
     * Returns the role prompt used by Select AI.
     *
     * @return role prompt, or {@code null} when unset
     */
    public String getRole() {
        return role;
    }

    /**
     * Returns the deterministic sampling seed.
     *
     * @return deterministic sampling seed, or {@code null} when unset
     */
    public Long getSeed() {
        return seed;
    }

    /**
     * Returns stop tokens used to terminate generation.
     *
     * @return configured stop-token list, or {@code null} when unset
     */
    public List<String> getStopTokens() {
        return stopTokens;
    }

    /**
     * Returns the provider sampling temperature.
     *
     * @return sampling temperature, or {@code null} when unset
     */
    public Double getTemperature() {
        return temperature;
    }

    /**
     * Returns the vector index name associated with this profile.
     *
     * @return vector index name, or {@code null} when unset
     */
    public String getVectorIndexName() {
        return vectorIndexName;
    }

    /**
     * Returns the source language for translation requests.
     *
     * @return source language for translation, or {@code null} when unset
     */
    public String getSourceLanguage() {
        return sourceLanguage;
    }

    /**
     * Returns the target language for translation requests.
     *
     * @return target language for translation, or {@code null} when unset
     */
    public String getTargetLanguage() {
        return targetLanguage;
    }

    /**
     * Returns unmodeled database attributes preserved for forward-compatible
     * metadata round-tripping.
     * <p>
     * These values are emitted by {@link #toAttributeMap()} but excluded from
     * {@link #toJson()} to avoid sending a nested {@code custom_attributes}
     * object to {@code DBMS_CLOUD_AI}.
     *
     * @return immutable map of custom attribute names and values
     */
    @JsonIgnore
    public Map<String, String> getCustomAttributes() {
        return customAttributes;
    }

    /**
     * Converts non-null attributes to DBMS_CLOUD_AI attribute names and string values.
     *
     * @return map containing non-null profile attributes
     */
    public Map<String, String> toAttributeMap() {
        Map<String, String> map = new HashMap<>();
        putIfNotNull(map, "annotations", annotations);
        putIfNotNull(map, "additional_instructions", additionalInstructions);
        putIfNotNull(map, "azure_deployment_name", azureDeploymentName);
        putIfNotNull(map, "azure_embedding_deployment_name", azureEmbeddingDeploymentName);
        putIfNotNull(map, "azure_resource_name", azureResourceName);
        putIfNotNull(map, "case_sensitive_values", caseSensitiveValues);
        putIfNotNull(map, "comments", comments);
        putIfNotNull(map, "constraints", constraints);
        putIfNotNull(map, "conversation", conversation);
        putIfNotNull(map, "conversation_length", conversationLength);
        putIfNotNull(map, "credential_name", credentialName);
        putIfNotNull(map, "embedding_model", embeddingModel);
        putIfNotNull(map, "enable_custom_source_uri", enableCustomSourceUri);
        putIfNotNull(map, "enforce_object_list", enforceObjectList);
        putIfNotNull(map, "max_tokens", maxTokens);
        putIfNotNull(map, "model", model);
        putIfNotNull(map, "object_list", objectList);
        putIfNotNull(map, "object_list_mode", objectListMode);
        putIfNotNull(map, "oci_apiformat", ociApiformat);
        putIfNotNull(map, "oci_compartment_id", ociCompartmentId);
        putIfNotNull(map, "oci_endpoint_id", ociEndpointId);
        putIfNotNull(map, "oci_runtimetype", ociRuntimetype);
        putIfNotNull(map, "provider", provider);
        putIfNotNull(map, "provider_endpoint", providerEndpoint);
        putIfNotNull(map, "region", region);
        putIfNotNull(map, "role", role);
        putIfNotNull(map, "seed", seed);
        putStopTokensIfNotNull(map, stopTokens);
        putIfNotNull(map, "temperature", temperature);
        putIfNotNull(map, "vector_index_name", vectorIndexName);
        putIfNotNull(map, "source_language", sourceLanguage);
        putIfNotNull(map, "target_language", targetLanguage);
        map.putAll(customAttributes);
        return map;
    }

    /**
     * Builds a {@link ProfileAttributes} instance from an attribute name/value map.
     * <p>
     * Recognized keys are converted to strongly-typed builder fields; unknown
     * keys are accepted so metadata reads do not fail when the database exposes a
     * newer attribute than this SDK version supports directly.
     *
     * @param attributes attribute map (typically DB-sourced key/value pairs)
     * @return populated {@link ProfileAttributes} instance
     */
    public static ProfileAttributes fromAttributeMap(Map<String, String> attributes) {
        Builder builder = ProfileAttributes.builder();
        if (attributes == null || attributes.isEmpty()) {
            return builder.build();
        }

        attributes.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.toLowerCase(Locale.ROOT);
            switch (key) {
                case "annotations" -> builder.annotations(Boolean.parseBoolean(v));
                case "additional_instructions" -> builder.additionalInstructions(v);
                case "azure_deployment_name" -> builder.azureDeploymentName(v);
                case "azure_embedding_deployment_name" -> builder.azureEmbeddingDeploymentName(v);
                case "azure_resource_name" -> builder.azureResourceName(v);
                case "case_sensitive_values" -> builder.caseSensitiveValues(Boolean.parseBoolean(v));
                case "comments" -> builder.comments(Boolean.parseBoolean(v));
                case "constraints" -> builder.constraints(Boolean.parseBoolean(v));
                case "conversation" -> builder.conversation(Boolean.parseBoolean(v));
                case "conversation_length" -> builder.conversationLength(Integer.parseInt(v));
                case "credential_name" -> builder.credentialName(v);
                case "embedding_model" -> builder.embeddingModel(v);
                case "enable_custom_source_uri" -> builder.enableCustomSourceUri(Boolean.parseBoolean(v));
                case "enforce_object_list" -> builder.enforceObjectList(Boolean.parseBoolean(v));
                case "max_tokens" -> builder.maxTokens(Integer.parseInt(v));
                case "model" -> builder.model(v);
                case "object_list" -> builder.objectList(v);
                case "object_list_mode" -> builder.objectListMode(v);
                case "oci_apiformat" -> builder.ociApiformat(v);
                case "oci_compartment_id" -> builder.ociCompartmentId(v);
                case "oci_endpoint_id" -> builder.ociEndpointId(v);
                case "oci_runtimetype" -> builder.ociRuntimetype(v);
                case "provider" -> builder.provider(v);
                case "provider_endpoint" -> builder.providerEndpoint(v);
                case "region" -> builder.region(v);
                case "role" -> builder.role(v);
                case "seed" -> builder.seed(Long.parseLong(v));
                case "stop_tokens" -> builder.stopTokens(parseStopTokens(v));
                case "temperature" -> builder.temperature(Double.parseDouble(v));
                case "vector_index_name" -> builder.vectorIndexName(v);
                case "source_language" -> builder.sourceLanguage(v);
                case "target_language" -> builder.targetLanguage(v);
                default -> builder.customAttribute(k, v);
            }
        });
        return builder.build();
    }

    /**
     * Adds a non-null profile attribute value to the output map.
     *
     * @param map destination attribute map
     * @param key DBMS_CLOUD_AI attribute name
     * @param value value to stringify and add
     */
    private static void putIfNotNull(Map<String, String> map, String key, Object value) {
        if (value != null) {
            map.put(key, String.valueOf(value));
        }
    }

    /**
     * Adds stop tokens as JSON array text so string-valued attribute maps can
     * preserve token boundaries.
     *
     * @param map destination attribute map
     * @param stopTokens stop-token list
     */
    private static void putStopTokensIfNotNull(Map<String, String> map, List<String> stopTokens) {
        if (stopTokens == null) {
            return;
        }
        try {
            map.put("stop_tokens", new ObjectMapper().writeValueAsString(stopTokens));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize stopTokens", e);
        }
    }

    /**
     * Parses a stop_tokens attribute value serialized as a JSON array.
     *
     * @param value stop_tokens attribute value
     * @return parsed stop-token list
     */
    private static List<String> parseStopTokens(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            var root = mapper.readTree(value);
            if (!root.isArray()) {
                throw new IllegalArgumentException("stop_tokens must be a JSON array");
            }
            List<String> parsedStopTokens = new ArrayList<>();
            for (var node : root) {
                if (!node.isTextual()) {
                    throw new IllegalArgumentException("stop_tokens must contain only string values");
                }
                parsedStopTokens.add(node.asText());
            }
            return parsedStopTokens;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("stop_tokens must be valid JSON array text", e);
        }
    }

    /**
     * Serializes the profile attributes using snake_case JSON names.
     *
     * @return JSON representation of this ProfileAttributes instance
     */
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ProfileAttributes to JSON", e);
        }
    }
    
    /**
     * Builder for {@link ProfileAttributes}.
     */
    public static final class Builder {
        /** Annotation metadata flag being assembled. */
        private Boolean annotations;
        /** Additional prompt instructions being assembled. */
        private String additionalInstructions;
        /** Azure OpenAI deployment name being assembled. */
        private String azureDeploymentName;
        /** Azure embedding deployment name being assembled. */
        private String azureEmbeddingDeploymentName;
        /** Azure resource name being assembled. */
        private String azureResourceName;
        /** Case-sensitive value matching flag being assembled. */
        private Boolean caseSensitiveValues;
        /** Table/column comments flag being assembled. */
        private Boolean comments;
        /** Referential constraints flag being assembled. */
        private Boolean constraints;
        /** Conversation support flag being assembled. */
        private Boolean conversation;
        /** Conversation context length being assembled. */
        private Integer conversationLength;
        /** Provider credential name being assembled. */
        private String credentialName;
        /** Embedding model name being assembled. */
        private String embeddingModel;
        /** Custom source URI flag being assembled. */
        private Boolean enableCustomSourceUri;
        /** Object list enforcement flag being assembled. */
        private Boolean enforceObjectList;
        /** Maximum output token count being assembled. */
        private Integer maxTokens;
        /** Provider model name being assembled. */
        private String model;
        /** Object list JSON being assembled. */
        private String objectList;
        /** Object list mode being assembled. */
        private String objectListMode;
        /** OCI API format being assembled. */
        private String ociApiformat;
        /** OCI compartment OCID being assembled. */
        private String ociCompartmentId;
        /** OCI endpoint OCID being assembled. */
        private String ociEndpointId;
        /** OCI runtime type being assembled. */
        private String ociRuntimetype;
        /** Provider name being assembled. */
        private String provider;
        /** Provider endpoint being assembled. */
        private String providerEndpoint;
        /** Provider region being assembled. */
        private String region;
        /** Role prompt being assembled. */
        private String role;
        /** Deterministic sampling seed being assembled. */
        private Long seed;
        /** Stop token list being assembled. */
        private List<String> stopTokens;
        /** Provider temperature being assembled. */
        private Double temperature;
        /** Unmodeled attributes accepted for forward compatibility. */
        private Map<String, String> customAttributes;
        /** Vector index name being assembled. */
        private String vectorIndexName;
        /** Translation source language being assembled. */
        private String sourceLanguage;
        /** Translation target language being assembled. */
        private String targetLanguage;
        /** Provider profile shortcut that populated this builder. */
        private ProviderProfile providerProfile;

        private Builder() {
            this.customAttributes = new HashMap<>();
        }

        /**
         * Sets whether table/column annotations are included in metadata.
         *
         * @param annotations annotation metadata flag
         * @return this builder instance
         */
        public Builder annotations(Boolean annotations) {
            this.annotations = annotations;
            return this;
        }

        /**
         * Sets additional instructions included in Select AI prompts.
         *
         * @param additionalInstructions additional prompt instructions
         * @return this builder instance
         */
        public Builder additionalInstructions(String additionalInstructions) {
            this.additionalInstructions = additionalInstructions;
            return this;
        }

        /**
         * Sets the Azure OpenAI deployment name for the generation model.
         *
         * @param azureDeploymentName Azure deployment name
         * @return this builder instance
         */
        public Builder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
            return this;
        }

        /**
         * Sets the Azure OpenAI deployment name for the embedding model.
         *
         * @param azureEmbeddingDeploymentName Azure embedding deployment name
         * @return this builder instance
         */
        public Builder azureEmbeddingDeploymentName(String azureEmbeddingDeploymentName) {
            this.azureEmbeddingDeploymentName = azureEmbeddingDeploymentName;
            return this;
        }

        /**
         * Sets the Azure OpenAI resource name.
         *
         * @param azureResourceName Azure resource name
         * @return this builder instance
         */
        public Builder azureResourceName(String azureResourceName) {
            this.azureResourceName = azureResourceName;
            return this;
        }

        /**
         * Sets whether generated SQL value matching is case-sensitive.
         *
         * @param caseSensitiveValues case-sensitive values flag
         * @return this builder instance
         */
        public Builder caseSensitiveValues(Boolean caseSensitiveValues) {
            this.caseSensitiveValues = caseSensitiveValues;
            return this;
        }

        /**
         * Sets whether table and column comments are included in metadata.
         *
         * @param comments comments metadata flag
         * @return this builder instance
         */
        public Builder comments(Boolean comments) {
            this.comments = comments;
            return this;
        }

        /**
         * Sets whether referential constraints are included in metadata.
         *
         * @param constraints constraints metadata flag
         * @return this builder instance
         */
        public Builder constraints(Boolean constraints) {
            this.constraints = constraints;
            return this;
        }

        /**
         * Sets whether conversation history is enabled for the profile.
         *
         * @param conversation conversation flag
         * @return this builder instance
         */
        public Builder conversation(Boolean conversation) {
            this.conversation = conversation;
            return this;
        }

        /**
         * Sets how many conversation turns are retained for context.
         *
         * @param conversationLength positive conversation context length
         * @return this builder instance
         */
        public Builder conversationLength(Integer conversationLength) {
            if (conversationLength != null && conversationLength <= 0) {
                throw new IllegalArgumentException("conversationLength must be greater than 0");
            }
            this.conversationLength = conversationLength;
            return this;
        }

        /**
         * Sets the database credential used for provider API calls.
         *
         * @param credentialName credential name
         * @return this builder instance
         */
        public Builder credentialName(String credentialName) {
            this.credentialName = credentialName;
            return this;
        }

        /**
         * Sets the embedding model name.
         *
         * @param embeddingModel embedding model name
         * @return this builder instance
         */
        public Builder embeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets whether custom source URI metadata is enabled.
         *
         * @param enableCustomSourceUri custom source URI flag
         * @return this builder instance
         */
        public Builder enableCustomSourceUri(Boolean enableCustomSourceUri) {
            this.enableCustomSourceUri = enableCustomSourceUri;
            return this;
        }

        /**
         * Sets whether generated SQL is restricted to the object list.
         *
         * @param enforceObjectList object list enforcement flag
         * @return this builder instance
         */
        public Builder enforceObjectList(Boolean enforceObjectList) {
            this.enforceObjectList = enforceObjectList;
            return this;
        }

        /**
         * Sets the maximum generated output tokens.
         *
         * @param maxTokens positive token limit
         * @return this builder instance
         */
        public Builder maxTokens(Integer maxTokens) {
            if (maxTokens != null && maxTokens <= 0) {
                throw new IllegalArgumentException("maxTokens must be greater than 0");
            }
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the provider model identifier.
         *
         * @param model provider model identifier
         * @return this builder instance
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the object list JSON used to constrain metadata.
         *
         * @param objectList JSON array containing object descriptors
         * @return this builder instance
         */
        public Builder objectList(String objectList) {
            this.objectList = objectList;
            return this;
        }

        /**
         * Sets the object-list selection mode used for NL2SQL processing.
         *
         * <p>A {@code null} value leaves the object-list mode unset. A non-null value
         * must correspond to an object-list mode supported by the SDK; otherwise,
         * this method throws an {@link IllegalArgumentException}.</p>
         *
         * @param objectListMode object-list mode, or {@code null} to leave the mode unset
         * @return this builder instance
         * @throws IllegalArgumentException if {@code objectListMode} is non-null and
         *         does not correspond to a supported object-list mode
         */
        public Builder objectListMode(String objectListMode) {
            ObjectListMode parsedObjectListMode =
                    ObjectListMode.fromValue(objectListMode);
            this.objectListMode = parsedObjectListMode == null ? null : parsedObjectListMode.getValue();
            return this;
        }

        /**
         * Sets the object-list selection mode used for NL2SQL processing.
         *
         * @param objectListMode object-list mode
         * @return this builder instance
         */
        public Builder objectListMode(ObjectListMode objectListMode) {
            this.objectListMode = objectListMode == null ? null : objectListMode.getValue();
            return this;
        }

        /**
         * Sets the OCI Generative AI API format.
         *
         * @param ociApiformat OCI API format, such as COHERE or GENERIC
         * @return this builder instance
         */
        public Builder ociApiformat(String ociApiformat) {
            OciApiFormat parsedOciApiFormat = OciApiFormat.fromValue(ociApiformat);
            this.ociApiformat = parsedOciApiFormat == null ? null : parsedOciApiFormat.getValue();
            return this;
        }

        /**
         * Sets the OCI Generative AI API format.
         *
         * @param ociApiFormat OCI API format
         * @return this builder instance
         */
        public Builder ociApiformat(OciApiFormat ociApiFormat) {
            this.ociApiformat = ociApiFormat == null ? null : ociApiFormat.getValue();
            return this;
        }

        /**
         * Sets the OCI compartment OCID.
         *
         * @param ociCompartmentId OCI compartment OCID
         * @return this builder instance
         */
        public Builder ociCompartmentId(String ociCompartmentId) {
            this.ociCompartmentId = ociCompartmentId;
            return this;
        }

        /**
         * Sets the OCI dedicated endpoint OCID.
         *
         * @param ociEndpointId OCI endpoint OCID
         * @return this builder instance
         */
        public Builder ociEndpointId(String ociEndpointId) {
            this.ociEndpointId = ociEndpointId;
            return this;
        }

        /**
         * Sets the OCI runtime type attribute.
         *
         * @param ociRuntimetype OCI runtime type
         * @return this builder instance
         */
        public Builder ociRuntimetype(String ociRuntimetype) {
            this.ociRuntimetype = ociRuntimetype;
            return this;
        }

        /**
         * Sets the AI provider for the profile.
         *
         * <p>A {@code null} value leaves the provider unset. A non-null value must
         * correspond to a provider supported by the SDK; otherwise, this method
         * throws an {@link IllegalArgumentException}.</p>
         *
         * @param provider provider name, or {@code null} to leave the provider unset
         * @return this builder instance
         * @throws IllegalArgumentException if {@code provider} is non-null and does
         *         not correspond to a supported provider
         */
        public Builder provider(String provider) {
            if (provider == null) {
                this.provider = null;
                return this;
            }

            Provider parsedProvider = Provider.fromValue(provider);

            if (parsedProvider == null) {
                throw new IllegalArgumentException(
                        "Unsupported provider: " + provider);
            }

            this.provider = parsedProvider.name();
            return this;
        }

        /**
         * Sets the provider endpoint host/path.
         *
         * @param providerEndpoint provider endpoint
         * @return this builder instance
         */
        public Builder providerEndpoint(String providerEndpoint) {
            this.providerEndpoint = providerEndpoint;
            return this;
        }

        /**
         * Sets the provider region.
         *
         * @param region provider region
         * @return this builder instance
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Sets the role prompt used by Select AI.
         *
         * @param role role prompt
         * @return this builder instance
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * Sets the deterministic sampling seed.
         *
         * @param seed sampling seed
         * @return this builder instance
         */
        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets stop tokens that terminate generation.
         *
         * @param stopTokens stop token list
         * @return this builder instance
         */
        public Builder stopTokens(List<String> stopTokens) {
            this.stopTokens = stopTokens;
            return this;
        }

        /**
         * Sets the provider sampling temperature.
         *
         * @param temperature non-negative temperature
         * @return this builder instance
         */
        public Builder temperature(Double temperature) {
            if (temperature != null && temperature < 0) {
                throw new IllegalArgumentException("temperature must be a non-negative float");
            }
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the vector index name associated with the profile.
         *
         * @param vectorIndexName Oracle SQL identifier for the vector index
         * @return this builder instance
         */
        public Builder vectorIndexName(String vectorIndexName) {
            this.vectorIndexName = vectorIndexName;
            return this;
        }

        /**
         * Sets the source language for translation.
         *
         * @param sourceLanguage source language name or code
         * @return this builder instance
         */
        public Builder sourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
            return this;
        }

        /**
         * Sets the target language for translation.
         *
         * @param targetLanguage target language name or code
         * @return this builder instance
         */
        public Builder targetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
            return this;
        }

        /**
         * Applies a provider profile shortcut to this builder.
         *
         * @param providerProfile provider profile to apply
         * @return this builder instance
         */
        public Builder providerProfile(ProviderProfile providerProfile) {
            if (providerProfile == null) {
                return this;
            }
            this.providerProfile = providerProfile;
            providerProfile.applyTo(this);
            return this;
        }

        /**
         * Accepts an unmodeled profile attribute for forward compatibility.
         * <p>
         * Current typed JSON serialization only emits attributes represented by
         * this SDK. This method prevents unknown database attributes from failing a
         * read/round-trip through {@link #fromAttributeMap(Map)}.
         *
         * @param key DBMS_CLOUD_AI attribute name
         * @param value attribute value
         * @return this builder instance
         */
        public Builder customAttribute(String key, String value) {
            if (key != null && value != null) {
                this.customAttributes.put(key, value);
            }
            return this;
        }

        /**
         * Builds immutable profile attributes.
         *
         * @return immutable ProfileAttributes instance built from current builder state
         */
        public ProfileAttributes build() {
            return new ProfileAttributes(this);
        }
    }
}
