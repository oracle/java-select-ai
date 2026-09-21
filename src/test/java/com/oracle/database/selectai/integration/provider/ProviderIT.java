/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.provider;

import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProviderProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live provider-profile smoke coverage.
 *
 * <p>Each provider has direct-attribute and provider-profile operation coverage.
 * Both paths create the database credential and profile before issuing a chat
 * request. Operation tests skip when provider-specific prerequisites are
 * unavailable.</p>
 *
 * <p>Provider credentials are read from {@code SELECT_AI_IT_*} environment variables. Model
 * and deployment values use the provider setup values by default and can be
 * overridden with the optional keys listed below:</p>
 *
 * <ul>
 *   <li>OpenAI: {@code SELECT_AI_IT_PROVIDER_OPENAI_API_KEY}; optional model
 *       override {@code SELECT_AI_IT_PROVIDER_OPENAI_MODEL}</li>
 *   <li>Cohere: {@code SELECT_AI_IT_PROVIDER_COHERE_API_KEY}; optional model
 *       override {@code SELECT_AI_IT_PROVIDER_COHERE_MODEL}</li>
 *   <li>Azure: {@code SELECT_AI_IT_PROVIDER_AZURE_API_KEY}; optional overrides
 *       {@code SELECT_AI_IT_PROVIDER_AZURE_RESOURCE_NAME},
 *       {@code SELECT_AI_IT_PROVIDER_AZURE_DEPLOYMENT_NAME}, and
 *       {@code SELECT_AI_IT_PROVIDER_AZURE_EMBEDDING_DEPLOYMENT_NAME}</li>
 *   <li>AWS: {@code SELECT_AI_IT_PROVIDER_AWS_ACCESS_KEY_ID},
 *       {@code SELECT_AI_IT_PROVIDER_AWS_SECRET_ACCESS_KEY},
 *       and {@code SELECT_AI_IT_PROVIDER_AWS_REGION}; optional model override
 *       {@code SELECT_AI_IT_PROVIDER_AWS_MODEL}</li>
 *   <li>Google: {@code SELECT_AI_IT_PROVIDER_GOOGLE_API_KEY}; optional model
 *       override {@code SELECT_AI_IT_PROVIDER_GOOGLE_MODEL}</li>
 *   <li>Anthropic: {@code SELECT_AI_IT_PROVIDER_ANTHROPIC_API_KEY}; optional
 *       model override {@code SELECT_AI_IT_PROVIDER_ANTHROPIC_MODEL}</li>
 *   <li>Hugging Face: {@code SELECT_AI_IT_PROVIDER_HUGGINGFACE_API_KEY},
 *       {@code SELECT_AI_IT_PROVIDER_HUGGINGFACE_MODEL}</li>
 *   <li>OCI: the existing {@code SELECT_AI_IT_OCI_USER_OCID},
 *       {@code SELECT_AI_IT_OCI_TENANCY_OCID},
 *       {@code SELECT_AI_IT_OCI_PRIVATE_KEY},
 *       {@code SELECT_AI_IT_OCI_FINGERPRINT},
 *       {@code SELECT_AI_IT_OCI_COMPARTMENT_ID}, and
 *       {@code SELECT_AI_IT_OCI_MODEL} keys</li>
 * </ul>
 */
class ProviderIT extends ProviderIntegrationFixture {

    /**
     * Provider tests create their own provider credential and profile, so the
     * base fixture does not create an unrelated default profile first.
     *
     * @return false because each test creates its own provider resources
     */
    @Override
    protected boolean requiresProfile() {
        return false;
    }

    @Override
    protected String profileObjectList() {
        return null;
    }

    /**
     * Test: Creates an OpenAI credential from the configured API key, builds profile attributes
     * with provider {@code "openai"} and the configured-or-default model {@code "gpt-5.6-luna"},
     * creates the profile, and calls {@code chat(CHAT_PROMPT)}.
     * Expected: The shared helper confirms that the credential name matches, the direct provider
     * is nonblank and is returned by the created profile, and {@code chat(CHAT_PROMPT)} returns a
     * nonblank response containing {@code "17"}.
     */
    @Test
    void test30000OpenAiProfileChatUsingDirectProvider() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_OPENAI_API_KEY",
                "OpenAI API key is required to create the OpenAI credential");
        String model = envOrDefault("SELECT_AI_IT_PROVIDER_OPENAI_MODEL", DEFAULT_OPENAI_MODEL);
        String credentialName = uniqueName("OPENAI_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("OPENAI")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("openai")
                .model(model)
                .build());
    }

    /**
     * Test: Creates an OpenAI credential, configures an
     * {@code OpenAIProviderProfile.Builder} with the configured-or-default model {@code "gpt-5.6-luna"},
     * creates the profile without a direct provider attribute, and calls {@code chat(CHAT_PROMPT)}.
     * Expected: The shared helper confirms that the provider derived from the provider-profile
     * configuration is nonblank and returned by the created profile, and that the chat response
     * is nonblank and contains {@code "17"}.
     */
    @Test
void test30001OpenAiProfileChatUsingProviderProfile() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_OPENAI_API_KEY",
                "OpenAI API key is required for the OpenAI provider test");
        String model = envOrDefault("SELECT_AI_IT_PROVIDER_OPENAI_MODEL", DEFAULT_OPENAI_MODEL);
        String credentialName = uniqueName("OPENAI");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("OPENAI")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.OpenAIProviderProfile.Builder()
                        .model(model)
                        .build())
    .build());
}

/**
 * Test: Creates an OpenAI-compatible profile with a configured provider endpoint.
 * Expected: CREATE_PROFILE persists the explicit provider endpoint without Java-side
 * rewriting. The test is skipped unless an endpoint-compatible credential and endpoint are
 * configured in the integration environment.
 */
@Test
void test30002OpenAiCompatibleProfilePersistsProviderEndpoint() throws Exception {
    String apiKey = env("SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_API_KEY");
    String endpoint = env("SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_ENDPOINT");
    if (apiKey == null || endpoint == null) {
        logSkipped("SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_API_KEY or "
                + "SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_ENDPOINT");
        assumeTrue(false,
                "OpenAI-compatible API key and endpoint are required for provider_endpoint coverage");
    }
    String model = envOrDefault(
            "SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_MODEL", DEFAULT_OPENAI_COMPATIBLE_MODEL);
    String credentialName = uniqueName("OPENAI_COMPATIBLE");

    createManagedCredential(CredentialConfig.builder(credentialName)
            .username("OPENAI")
            .password(apiKey)
            .build());

    ProfileAttributes attributes = ProfileAttributes.builder()
            .credentialName(credentialName)
            .provider("openai")
            .providerEndpoint(endpoint)
            .model(model)
            .build();
    Profile created = createManagedProfile(
            uniqueName("OPENAI_COMPATIBLE_PROFILE"), attributes, PROFILE_DESCRIPTION);

    assertThat(created.getProfileAttributes().getProviderEndpoint()).isEqualTo(endpoint);
}

    /**
     * Test: Creates a Cohere username/password credential from the configured API key, builds
     * direct attributes with provider {@code "cohere"} and the configured-or-default model
     * {@code "command-a-03-2025"}, and sends {@code CHAT_PROMPT} through the created profile.
     * Expected: The shared helper confirms the credential name and direct {@code "cohere"}
     * provider on the created profile; the chat response is nonblank and contains {@code "17"}.
     */
    @Test
    void test30003CohereProfileChatUsingDirectProvider() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_COHERE_API_KEY",
                "Cohere API key is required to create the Cohere credential");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_COHERE_MODEL", DEFAULT_COHERE_MODEL);
        String credentialName = uniqueName("COHERE_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("COHERE")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("cohere")
                .model(model)
                .build());
    }

    /**
     * Test: Creates a Cohere username/password credential, configures a
     * {@code CohereProviderProfile.Builder} with model {@code "command-a-03-2025"} or its
     * configured override, and sends {@code CHAT_PROMPT} through the created profile.
     * Expected: The shared helper confirms the provider derived from the Cohere provider-profile
     * configuration is returned by the created profile; the chat response is nonblank and
     * contains {@code "17"}.
     */
    @Test
    void test30004CohereProfileChatUsingProviderProfile() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_COHERE_API_KEY",
                "Cohere API key is required for the Cohere provider test");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_COHERE_MODEL", DEFAULT_COHERE_MODEL);
        String credentialName = uniqueName("COHERE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("COHERE")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.CohereProviderProfile.Builder()
                        .model(model)
                        .build())
                .build());
    }

    /**
     * Test: Creates an Azure credential and builds direct Azure attributes containing the
     * configured resource name, deployment name, and embedding deployment name before invoking
     * the shared profile-create-and-chat helper.
     * Expected: If enabled, the shared helper would confirm the Azure provider is returned by the
     * created profile and the chat response is nonblank and contains {@code "17"}; the method is
     * currently disabled under Bug 39901111.
     */
    
    @Test
    void test30005AzureProfileChatUsingDirectProvider() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AZURE_API_KEY",
                "Azure API key is required to create the Azure credential");
        String resourceName = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AZURE_RESOURCE_NAME", DEFAULT_AZURE_RESOURCE_NAME);
        String deploymentName = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AZURE_DEPLOYMENT_NAME",
                DEFAULT_AZURE_DEPLOYMENT_NAME);
        String embeddingDeploymentName = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AZURE_EMBEDDING_DEPLOYMENT_NAME",
                DEFAULT_AZURE_EMBEDDING_DEPLOYMENT_NAME);
        String credentialName = uniqueName("AZURE_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("azure")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("azure")
                .azureResourceName(resourceName)
                .azureDeploymentName(deploymentName)
                .azureEmbeddingDeploymentName(embeddingDeploymentName)
                .build());
    }

    /**
     * Test: Creates an Azure credential, configures an {@code AzureProviderProfile.Builder} with
     * the resource, deployment, and embedding deployment names, and sends {@code CHAT_PROMPT}.
     * Expected: If enabled, the shared helper would confirm the Azure provider derived from the
     * provider profile and a nonblank response containing {@code "17"}; the method is disabled
     * under Bug 39901111.
     */
    
    @Test
    void test30006AzureProfileChatUsingProviderProfile() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AZURE_API_KEY",
                "Azure API key is required for the Azure provider test");
        String resourceName = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AZURE_RESOURCE_NAME", DEFAULT_AZURE_RESOURCE_NAME);
        String deploymentName = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AZURE_DEPLOYMENT_NAME",
                DEFAULT_AZURE_DEPLOYMENT_NAME);
        String embeddingDeploymentName = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AZURE_EMBEDDING_DEPLOYMENT_NAME",
                DEFAULT_AZURE_EMBEDDING_DEPLOYMENT_NAME);
        String credentialName = uniqueName("AZURE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("azure")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.AzureProviderProfile.Builder()
                        .azureResourceName(resourceName)
                        .azureDeploymentName(deploymentName)
                        .azureEmbeddingDeploymentName(embeddingDeploymentName)
                        .build())
                .build());
    }

    /**
     * Test: Creates an AWS username/password credential from the configured access-key pair,
     * builds direct attributes with provider {@code "aws"}, configured region, and model
     * {@code "meta.llama3-70b-instruct-v1:0"} or its override, and sends {@code CHAT_PROMPT}.
     * Expected: If enabled, the shared helper would confirm the direct AWS provider is returned by
     * the created profile and the response is nonblank and contains {@code "17"}; the method is
     * disabled under Bug 39901111.
     */
    
    @Test
    void test30007AwsProfileChatUsingDirectProvider() throws Exception {
        String accessKeyId = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AWS_ACCESS_KEY_ID",
                "AWS access key ID is required to create the AWS credential");
        String secretAccessKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AWS_SECRET_ACCESS_KEY",
                "AWS secret access key is required to create the AWS credential");
        String region = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AWS_REGION",
                "AWS region is required to create the AWS profile");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AWS_MODEL", DEFAULT_AWS_MODEL);
        String credentialName = uniqueName("AWS_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username(accessKeyId)
                .password(secretAccessKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("aws")
                .region(region)
                .model(model)
                .build());
    }

    /**
     * Test: Creates an AWS credential, configures an {@code AWSProviderProfile.Builder} with the
     * configured region and model, and sends {@code CHAT_PROMPT} through the created profile.
     * Expected: If enabled, the shared helper would confirm the AWS provider derived from the
     * provider profile and a nonblank response containing {@code "17"}; the method is disabled
     * under Bug 39901111.
     */
    
    @Test
    void test30008AwsProfileChatUsingProviderProfile() throws Exception {
        String accessKeyId = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AWS_ACCESS_KEY_ID",
                "AWS access key ID is required for the AWS provider test");
        String secretAccessKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AWS_SECRET_ACCESS_KEY",
                "AWS secret access key is required for the AWS provider test");
        String region = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_AWS_REGION",
                "AWS region is required for the AWS provider test");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_AWS_MODEL", DEFAULT_AWS_MODEL);
        String credentialName = uniqueName("AWS");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username(accessKeyId)
                .password(secretAccessKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.AWSProviderProfile.Builder()
                        .region(region)
                        .model(model)
                        .build())
                .build());
    }

    /**
     * Test: Creates a Google credential from the configured API key, builds direct attributes with
     * provider {@code "google"} and configured-or-default model {@code "gemini-3-flash-preview"},
     * and sends {@code CHAT_PROMPT}.
     * Expected: If enabled, the shared helper would confirm the direct Google provider is returned
     * by the created profile and the response is nonblank and contains {@code "17"}; the method
     * is disabled under Bug 39901111.
     */
    
    @Test
    void test30009GoogleProfileChatUsingDirectProvider() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_GOOGLE_API_KEY",
                "Google API key is required to create the Google credential");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_GOOGLE_MODEL", DEFAULT_GOOGLE_MODEL);
        String credentialName = uniqueName("GOOGLE_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("GOOGLE")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("google")
                .model(model)
                .build());
    }

    /**
     * Test: Creates a Google credential, configures a {@code GoogleProviderProfile.Builder} with
     * the configured-or-default model {@code "gemini-3-flash-preview"}, and sends
     * {@code CHAT_PROMPT} through the created profile.
     * Expected: If enabled, the shared helper would confirm the Google provider derived from the
     * provider profile and a nonblank response containing {@code "17"}; the method is disabled
     * under Bug 39901111.
     */
    
    @Test
    void test30010GoogleProfileChatUsingProviderProfile() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_GOOGLE_API_KEY",
                "Google API key is required for the Google provider test");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_GOOGLE_MODEL", DEFAULT_GOOGLE_MODEL);
        String credentialName = uniqueName("GOOGLE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("GOOGLE")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.GoogleProviderProfile.Builder()
                        .model(model)
                        .build())
                .build());
    }

    /**
     * Test: Creates an Anthropic credential from the configured API key, builds direct attributes
     * with provider {@code "anthropic"} and configured-or-default model {@code "claude-opus-4-6"},
     * and sends {@code CHAT_PROMPT}.
     * Expected: The shared helper confirms the direct {@code "anthropic"} provider is returned by
     * the created profile and the nonblank chat response contains {@code "17"}.
     */
    @Test
    void test30011AnthropicProfileChatUsingDirectProvider() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_ANTHROPIC_API_KEY",
                "Anthropic API key is required to create the Anthropic credential");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL);
        String credentialName = uniqueName("ANTHROPIC_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("ANTHROPIC")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("anthropic")
                .model(model)
                .build());
    }

    /**
     * Test: Creates an Anthropic credential, configures an
     * {@code AnthropicProviderProfile.Builder} with configured-or-default model
     * {@code "claude-opus-4-6"}, and sends {@code CHAT_PROMPT} through the created profile.
     * Expected: The shared helper confirms the Anthropic provider derived from the provider
     * profile is returned by the created profile and the response contains {@code "17"}.
     */
    @Test
    void test30012AnthropicProfileChatUsingProviderProfile() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_ANTHROPIC_API_KEY",
                "Anthropic API key is required for the Anthropic provider test");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL);
        String credentialName = uniqueName("ANTHROPIC");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("ANTHROPIC")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.AnthropicProviderProfile.Builder()
                        .model(model)
                        .build())
                .build());
    }

    /**
     * Test: Creates a Hugging Face credential from the configured API key, builds direct attributes
     * with provider {@code "huggingface"} and configured-or-default model {@code "test-model"},
     * and sends {@code CHAT_PROMPT}.
     * Expected: The shared helper confirms the direct {@code "huggingface"} provider is returned
     * by the created profile and the nonblank chat response contains {@code "17"}.
     */
    @Test
    void test30013HuggingFaceProfileChatUsingDirectProvider() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_HUGGINGFACE_API_KEY",
                "Hugging Face API key is required to create the Hugging Face credential");
        String model = envOrDefault(
                "SELECT_AI_IT_PROVIDER_HUGGINGFACE_MODEL", DEFAULT_HUGGINGFACE_MODEL);
        String credentialName = uniqueName("HUGGINGFACE_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("HUGGINGFACE")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("huggingface")
                .model(model)
                .build());
    }

    /**
     * Test: Creates a Hugging Face credential, configures a
     * {@code HuggingFaceProviderProfile.Builder} with the required configured model, and sends
     * {@code CHAT_PROMPT} through the created profile.
     * Expected: The shared helper confirms the Hugging Face provider derived from the provider
     * profile is returned by the created profile and the response contains {@code "17"}.
     */
    @Test
    void test30014HuggingFaceProfileChatUsingProviderProfile() throws Exception {
        String apiKey = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_HUGGINGFACE_API_KEY",
                "Hugging Face API key is required for the Hugging Face provider test");
        String model = requiredFeatureValue(
                "SELECT_AI_IT_PROVIDER_HUGGINGFACE_MODEL",
                "Hugging Face model is required for the Hugging Face provider test");
        String credentialName = uniqueName("HUGGINGFACE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .username("HUGGINGFACE")
                .password(apiKey)
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.HuggingFaceProviderProfile.Builder()
                        .model(model)
                        .build())
                .build());
    }

    /**
     * Test: Creates an OCI signing-key credential, builds direct OCI attributes with the required
     * compartment ID, configured region and API format, and configured model, then sends
     * {@code CHAT_PROMPT}.
     * Expected: The shared helper confirms the direct provider {@code "oci"} is returned by the
     * created profile and the nonblank chat response contains {@code "17"}.
     */
    @Test
    void test30015OciProfileChatUsingDirectProvider() throws Exception {
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "OCI compartment ID is required for the OCI provider test");
        String model = requiredFeatureValue(
                "SELECT_AI_IT_OCI_MODEL",
                "OCI model is required for the OCI provider test");
        String credentialName = uniqueName("OCI_CREATE");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .userOcid(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_USER_OCID",
                        "OCI user OCID is required to create the OCI credential"))
                .tenancyOcid(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_TENANCY_OCID",
                        "OCI tenancy OCID is required to create the OCI credential"))
                .privateKey(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_PRIVATE_KEY",
                        "OCI private key is required to create the OCI credential"))
                .fingerprint(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_FINGERPRINT",
                        "OCI fingerprint is required to create the OCI credential"))
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .provider("oci")
                .region(envOrDefault("SELECT_AI_IT_REGION", DEFAULT_REGION))
                .ociApiformat(envOrDefault("SELECT_AI_IT_OCI_APIFORMAT", DEFAULT_OCI_API_FORMAT))
                .ociCompartmentId(compartmentId)
                .model(model)
                .build());
    }

    /**
     * Test: Creates an OCI signing-key credential and configures an
     * {@code OCIGenAIProviderProfile.Builder} with the required compartment ID, region, API format,
     * and model before sending {@code CHAT_PROMPT}.
     * Expected: The shared helper confirms the OCI provider derived from the provider profile is
     * returned by the created profile and the nonblank chat response contains {@code "17"}.
     */
    @Test
    void test30016OciProfileChatUsingProviderProfile() throws Exception {
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "OCI compartment ID is required for the OCI provider test");
        String model = requiredFeatureValue(
                "SELECT_AI_IT_OCI_MODEL",
                "OCI model is required for the OCI provider test");
        String credentialName = uniqueName("OCI");

        createManagedCredential(CredentialConfig.builder(credentialName)
                .userOcid(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_USER_OCID",
                        "OCI user OCID is required for the OCI provider test"))
                .tenancyOcid(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_TENANCY_OCID",
                        "OCI tenancy OCID is required for the OCI provider test"))
                .privateKey(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_PRIVATE_KEY",
                        "OCI private key is required for the OCI provider test"))
                .fingerprint(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_FINGERPRINT",
                        "OCI fingerprint is required for the OCI provider test"))
                .build());

        assertProviderChat(credentialName, ProfileAttributes.builder()
                .credentialName(credentialName)
                .providerProfile(new ProviderProfile.OCIGenAIProviderProfile.Builder()
                        .region(envOrDefault("SELECT_AI_IT_REGION", DEFAULT_REGION))
                        .ociApiFormat(envOrDefault("SELECT_AI_IT_OCI_APIFORMAT", DEFAULT_OCI_API_FORMAT))
                        .ociCompartmentId(compartmentId)
                        .model(model)
                        .build())
                .build());
    }

    private void assertProviderChat(String credentialName,
                                    ProfileAttributes attributes) throws Exception {
        assertThat(attributes.getCredentialName()).isEqualTo(credentialName);
        assertThat(attributes.getProvider()).isNotBlank();

        String profileName = uniqueName(attributes.getProvider().toUpperCase());
        Profile providerProfile = createManagedProfile(
                profileName, attributes, PROFILE_DESCRIPTION);

        assertThat(providerProfile.getProfileAttributes().getProvider())
                .isEqualTo(attributes.getProvider());

        assertThat(providerProfile.chat(CHAT_PROMPT))
                .isNotBlank()
                .contains("17");
    }

    private String uniqueName(String prefix) {
        return "JSAI_" + prefix + "_"
                + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
    }
}
