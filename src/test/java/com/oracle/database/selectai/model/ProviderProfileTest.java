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

class ProviderProfileTest {

    /**
     * Test: Creates provider profile builders from static factory methods.
     * Expected: Each factory returns the corresponding provider-specific builder.
     */
    @Test
    void staticFactoriesReturnProviderSpecificBuilders() {
        assertThat(ProviderProfile.openAI().build())
                .isInstanceOf(ProviderProfile.OpenAIProviderProfile.class);
        assertThat(ProviderProfile.cohere().build())
                .isInstanceOf(ProviderProfile.CohereProviderProfile.class);
        assertThat(ProviderProfile.azure().build())
                .isInstanceOf(ProviderProfile.AzureProviderProfile.class);
        assertThat(ProviderProfile.ociGenAI().build())
                .isInstanceOf(ProviderProfile.OCIGenAIProviderProfile.class);
        assertThat(ProviderProfile.google().build())
                .isInstanceOf(ProviderProfile.GoogleProviderProfile.class);
        assertThat(ProviderProfile.huggingFace().build())
                .isInstanceOf(ProviderProfile.HuggingFaceProviderProfile.class);
        assertThat(ProviderProfile.aws().build())
                .isInstanceOf(ProviderProfile.AWSProviderProfile.class);
        assertThat(ProviderProfile.anthropic().build())
                .isInstanceOf(ProviderProfile.AnthropicProviderProfile.class);
    }

    /**
     * Test: Rejects null profile attributes builders when applying provider values.
     * Expected: applyTo(null) throws a clear IllegalArgumentException.
     */
    @Test
    void applyToRejectsNullBuilder() {
        ProviderProfile profile = ProviderProfile.openAI().build();

        assertThatThrownBy(() -> profile.applyTo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("builder");
    }

    /**
     * Test: Applies the OpenAI provider to profile attributes.
     * Expected: The provider is populated, while no endpoint is derived.
     */
    @Test
    void openAiProfileAppliesProviderWithoutPopulatingEndpoint() {
        ProviderProfile.OpenAIProviderProfile profile =
                new ProviderProfile.OpenAIProviderProfile.Builder().build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OPENAI_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.openai);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(attributes.getProvider()).isEqualTo("openai");
        assertThat(attributes.getProviderEndpoint()).isNull();
    }

    /**
     * Test: Applies the Cohere provider to profile attributes.
     * Expected: The provider is populated, while no endpoint is derived.
     */
    @Test
    void cohereProfileAppliesProviderWithoutPopulatingEndpoint() {
        ProviderProfile.CohereProviderProfile profile =
                new ProviderProfile.CohereProviderProfile.Builder().build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("COHERE_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.cohere);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(attributes.getProvider()).isEqualTo("cohere");
        assertThat(attributes.getProviderEndpoint()).isNull();
    }

    /**
     * Test: Applies the Google provider to profile attributes.
     * Expected: The provider is populated, while no endpoint is derived.
     */
    @Test
    void googleProfileAppliesProviderWithoutPopulatingEndpoint() {
        ProviderProfile.GoogleProviderProfile profile =
                new ProviderProfile.GoogleProviderProfile.Builder().build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("GOOGLE_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.google);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(attributes.getProvider()).isEqualTo("google");
        assertThat(attributes.getProviderEndpoint()).isNull();
    }

    /**
     * Test: Applies the Hugging Face provider to profile attributes.
     * Expected: The provider is populated, while no endpoint is derived.
     */
    @Test
    void huggingFaceProfileAppliesProviderWithoutPopulatingEndpoint() {
        ProviderProfile.HuggingFaceProviderProfile profile =
                new ProviderProfile.HuggingFaceProviderProfile.Builder().build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("HF_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.huggingface);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(attributes.getProvider()).isEqualTo("huggingface");
        assertThat(attributes.getProviderEndpoint()).isNull();
    }

    /**
     * Test: Applies the Anthropic provider to profile attributes.
     * Expected: The provider is populated, while no endpoint is derived.
     */
    @Test
    void anthropicProfileAppliesProviderWithoutPopulatingEndpoint() {
        ProviderProfile.AnthropicProviderProfile profile =
                new ProviderProfile.AnthropicProviderProfile.Builder().build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("ANTHROPIC_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.anthropic);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(attributes.getProvider()).isEqualTo("anthropic");
        assertThat(attributes.getProviderEndpoint()).isNull();
    }

    /**
     * Test: Applies Azure provider fields without deriving its endpoint.
     * Expected: Azure resource, deployment, model, embedding, and provider fields are retained;
     * the endpoint remains unset unless explicitly supplied.
     */
    @Test
    void azureProfileAppliesAzureFieldsWithoutPopulatingEndpoint() {
        ProviderProfile.AzureProviderProfile profile =
                new ProviderProfile.AzureProviderProfile.Builder()
                        .azureResourceName("selectai-prod")
                        .azureDeploymentName("chat-prod")
                        .azureEmbeddingDeploymentName("embed-prod")
                        .model("gpt-4o")
                        .embeddingModel("text-embedding-3-large")
                        .build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("AZURE_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.azure);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(attributes.getProvider()).isEqualTo("azure");
        assertThat(attributes.getProviderEndpoint()).isNull();
        assertThat(attributes.getAzureResourceName()).isEqualTo("selectai-prod");
        assertThat(attributes.getAzureDeploymentName()).isEqualTo("chat-prod");
        assertThat(attributes.getAzureEmbeddingDeploymentName()).isEqualTo("embed-prod");
        assertThat(attributes.getModel()).isEqualTo("gpt-4o");
        assertThat(attributes.getEmbeddingModel()).isEqualTo("text-embedding-3-large");
    }

    /**
     * Test: Applies AWS provider format and region.
     * Expected: The provider, region, and aws_apiformat custom attribute are retained;
     * no endpoint is derived.
     */
    @Test
    void awsProfileAppliesCustomFormatWithoutPopulatingEndpoint() {
        ProviderProfile.AWSProviderProfile profile =
                new ProviderProfile.AWSProviderProfile.Builder()
                        .region("us-west-2")
                        .awsApiFormat("GENERIC")
                        .build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("AWS_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.aws);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(profile.getRegion()).isEqualTo("us-west-2");
        assertThat(attributes.getProvider()).isEqualTo("aws");
        assertThat(attributes.getProviderEndpoint()).isNull();
        assertThat(attributes.getRegion()).isEqualTo("us-west-2");
        assertThat(attributes.getCustomAttributes()).containsEntry("aws_apiformat", "GENERIC");
    }

    /**
     * Test: Applies an AWS profile with a blank region.
     * Expected: The blank region is retained as supplied, and no endpoint or API format is derived.
     */
    @Test
    void awsProfileDoesNotDeriveEndpointOrApiFormat() {
        ProviderProfile.AWSProviderProfile profile =
                new ProviderProfile.AWSProviderProfile.Builder()
                        .region(" ")
                        .build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("AWS_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(profile.getRegion()).isEqualTo(" ");
        assertThat(attributes.getProviderEndpoint()).isNull();
        assertThat(attributes.getRegion()).isEqualTo(" ");
        assertThat(attributes.getCustomAttributes()).doesNotContainKey("aws_apiformat");
    }

    /**
     * Test: Applies an Azure provider profile without a resource name.
     * Expected: The provider is set, the endpoint remains unset, and no endpoint is derived from a null resource.
     */
    @Test
    void azureProfileLeavesEndpointUnsetWithoutResourceName() {
        ProviderProfile.AzureProviderProfile profile =
                new ProviderProfile.AzureProviderProfile.Builder()
                        .model("gpt-4o")
                        .build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("AZURE_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.azure);
        assertThat(profile.getProviderEndpoint()).isNull();
        assertThat(attributes.getProvider()).isEqualTo("azure");
        assertThat(attributes.getProviderEndpoint()).isNull();
        assertThat(attributes.getAzureResourceName()).isNull();
        assertThat(attributes.getModel()).isEqualTo("gpt-4o");
    }

    /**
     * Test: Apply OCI provider-specific profile fields.
     * Expected: OCI provider, API format, compartment, endpoint, and runtime values are retained.
     */
    @Test
    void ociProviderProfileAppliesOciSpecificFields() {
        ProviderProfile.OCIGenAIProviderProfile profile =
                new ProviderProfile.OCIGenAIProviderProfile.Builder()
                        .ociApiFormat("generic")
                        .ociCompartmentId("ocid1.compartment.oc1..example")
                        .ociEndpointId("ocid1.generativeaiendpoint.oc1..example")
                        .ociRuntimeType("COHERE")
                        .build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .providerProfile(profile)
                .build();

        assertThat(profile.getProviderName()).isEqualTo(Provider.oci);
        assertThat(attributes.getProvider()).isEqualTo("oci");
        assertThat(attributes.getOciApiformat()).isEqualTo("GENERIC");
        assertThat(attributes.getOciCompartmentId()).isEqualTo("ocid1.compartment.oc1..example");
        assertThat(attributes.getOciEndpointId()).isEqualTo("ocid1.generativeaiendpoint.oc1..example");
        assertThat(attributes.getOciRuntimetype()).isEqualTo("COHERE");
    }

    /**
     * Test: Apply typed OCI API format to provider-specific profile fields.
     * Expected: OCI API format is retained using the database value.
     */
    @Test
    void ociProviderProfileAcceptsTypedOciApiFormat() {
        ProviderProfile.OCIGenAIProviderProfile profile =
                ProviderProfile.ociGenAI()
                        .ociApiFormat(OciApiFormat.GENERIC)
                        .build();

        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .providerProfile(profile)
                .build();

        assertThat(attributes.getOciApiformat()).isEqualTo("GENERIC");
    }
}
