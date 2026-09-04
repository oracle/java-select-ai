/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

/**
 * Convenience object for applying provider-specific attributes to
 * {@link ProfileAttributes.Builder}.
 * <p>
 * Provider profiles reduce boilerplate when creating profile attributes for a
 * known provider. They set provider names and caller-supplied provider fields
 * while still allowing the caller to add AI model identifiers, embedding
 * settings, credentials, and object selection details on
 * {@link ProfileAttributes.Builder}.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-manage-profiles.html">
 *      Select AI provider profile configuration</a>
 */
public abstract class ProviderProfile {
    /** Embedding model name used by the provider. */
    private final String embeddingModel;
    /** Chat or generation model name used by the provider. */
    private final String model;
    /** Select AI provider identifier. */
    private final Provider providerName;
    /** Provider endpoint host/path used in profile attributes. */
    private final String providerEndpoint;
    /** Provider region, where applicable. */
    private final String region;

    /**
     * Initializes a provider profile from its builder.
     *
     * @param builder builder containing the provider profile values
     */
    protected ProviderProfile(Builder<?> builder) {
        this.embeddingModel = builder.embeddingModel;
        this.model = builder.model;
        this.providerName = builder.providerName;
        this.providerEndpoint = builder.providerEndpoint;
        this.region = builder.region;
    }

    /**
     * Returns the embedding model configured for this provider profile.
     *
     * @return embedding model name, or {@code null} when unset
     */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Returns the chat or generation model configured for this provider profile.
     *
     * @return AI model name, or {@code null} when unset
     */
    public String getModel() {
        return model;
    }

    /**
     * Returns the Select AI provider identifier.
     *
     * @return provider name, or {@code null} when unset
     */
    public Provider getProviderName() {
        return providerName;
    }

    /**
     * Returns the provider endpoint.
     *
     * @return provider endpoint host/path, or {@code null} when unset
     */
    public String getProviderEndpoint() {
        return providerEndpoint;
    }

    /**
     * Returns the provider region.
     *
     * @return region value, or {@code null} when unset
     */
    public String getRegion() {
        return region;
    }

    /**
     * Creates a builder for OpenAI provider profile attributes.
     *
     * @return OpenAI provider profile builder
     */
    public static OpenAIProviderProfile.Builder openAI() {
        return new OpenAIProviderProfile.Builder();
    }

    /**
     * Creates a builder for Cohere provider profile attributes.
     *
     * @return Cohere provider profile builder
     */
    public static CohereProviderProfile.Builder cohere() {
        return new CohereProviderProfile.Builder();
    }

    /**
     * Creates a builder for Azure OpenAI provider profile attributes.
     *
     * @return Azure provider profile builder
     */
    public static AzureProviderProfile.Builder azure() {
        return new AzureProviderProfile.Builder();
    }

    /**
     * Creates a builder for OCI Generative AI provider profile attributes.
     *
     * @return OCI Generative AI provider profile builder
     */
    public static OCIGenAIProviderProfile.Builder ociGenAI() {
        return new OCIGenAIProviderProfile.Builder();
    }

    /**
     * Creates a builder for Google provider profile attributes.
     *
     * @return Google provider profile builder
     */
    public static GoogleProviderProfile.Builder google() {
        return new GoogleProviderProfile.Builder();
    }

    /**
     * Creates a builder for Hugging Face provider profile attributes.
     *
     * @return Hugging Face provider profile builder
     */
    public static HuggingFaceProviderProfile.Builder huggingFace() {
        return new HuggingFaceProviderProfile.Builder();
    }

    /**
     * Creates a builder for AWS Bedrock provider profile attributes.
     *
     * @return AWS Bedrock provider profile builder
     */
    public static AWSProviderProfile.Builder aws() {
        return new AWSProviderProfile.Builder();
    }

    /**
     * Creates a builder for Anthropic provider profile attributes.
     *
     * @return Anthropic provider profile builder
     */
    public static AnthropicProviderProfile.Builder anthropic() {
        return new AnthropicProviderProfile.Builder();
    }

    /**
     * Applies this provider profile to a profile attributes builder.
     *
     * @param builder profile attributes builder to update
     * @return the same builder instance after provider fields are applied
     */
    public ProfileAttributes.Builder applyTo(ProfileAttributes.Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        if (embeddingModel != null) {
            builder.embeddingModel(embeddingModel);
        }
        if (model != null) {
            builder.model(model);
        }
        if (providerName != null) {
            builder.provider(providerName.name());
        }
        if (providerEndpoint != null) {
            builder.providerEndpoint(providerEndpoint);
        }
        if (region != null) {
            builder.region(region);
        }
        return builder;
    }

    /**
     * Base builder for provider profile implementations.
     *
     * @param <T> concrete builder type for fluent chaining
     */
    public abstract static class Builder<T extends Builder<T>> {
        /** Creates an empty provider profile builder. */
        public Builder() {
        }

        /** Embedding model being assembled. */
        protected String embeddingModel;
        /** Chat or generation model being assembled. */
        protected String model;
        /** Provider identifier being assembled. */
        protected Provider providerName;
        /** Provider endpoint being assembled. */
        protected String providerEndpoint;
        /** Provider region being assembled. */
        protected String region;

        /**
         * Returns the concrete builder instance for fluent chaining.
         *
         * @return concrete builder instance
         */
        protected abstract T self();

        /**
         * Sets the embedding model.
         *
         * @param embeddingModel embedding model name
         * @return this builder instance
         */
        public T embeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
            return self();
        }

        /**
         * Sets the chat or generation model.
         *
         * @param model AI model name
         * @return this builder instance
         */
        public T model(String model) {
            this.model = model;
            return self();
        }

        /**
         * Sets the provider identifier.
         *
         * @param providerName Select AI provider name
         * @return this builder instance
         */
        public T providerName(Provider providerName) {
            this.providerName = providerName;
            return self();
        }

        /**
         * Sets the provider endpoint.
         *
         * @param providerEndpoint provider endpoint host/path
         * @return this builder instance
         */
        public T providerEndpoint(String providerEndpoint) {
            this.providerEndpoint = providerEndpoint;
            return self();
        }

        /**
         * Sets the provider region.
         *
         * @param region provider region
         * @return this builder instance
         */
        public T region(String region) {
            this.region = region;
            return self();
        }
    }

    /**
     * Provider profile attributes for Azure OpenAI.
     */
    public static final class AzureProviderProfile extends ProviderProfile {
        /** Azure OpenAI deployment used for generation. */
        private final String azureDeploymentName;
        /** Azure OpenAI deployment used for embeddings. */
        private final String azureEmbeddingDeploymentName;
        /** Azure OpenAI resource name. */
        private final String azureResourceName;

        private AzureProviderProfile(Builder builder) {
            super(builder.providerName(Provider.azure)
                    .providerEndpoint(builder.providerEndpoint));
            this.azureDeploymentName = builder.azureDeploymentName;
            this.azureEmbeddingDeploymentName = builder.azureEmbeddingDeploymentName;
            this.azureResourceName = builder.azureResourceName;
        }

        /**
         * Applies Azure-specific attributes to a profile builder.
         *
         * @param builder profile attributes builder to update
         * @return the same builder instance after Azure fields are applied
         */
        @Override
        public ProfileAttributes.Builder applyTo(ProfileAttributes.Builder builder) {
            super.applyTo(builder)
                    .azureDeploymentName(azureDeploymentName)
                    .azureEmbeddingDeploymentName(azureEmbeddingDeploymentName)
                    .azureResourceName(azureResourceName);
            return builder;
        }

        /**
         * Builder for {@link AzureProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty Azure provider profile builder. */
            public Builder() {
            }

            /** Azure OpenAI deployment name being assembled. */
            private String azureDeploymentName;
            /** Azure embedding deployment name being assembled. */
            private String azureEmbeddingDeploymentName;
            /** Azure resource name being assembled. */
            private String azureResourceName;

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Sets the Azure OpenAI deployment name.
             *
             * @param value deployment name
             * @return this builder instance
             */
            public Builder azureDeploymentName(String value) {
                this.azureDeploymentName = value;
                return this;
            }

            /**
             * Sets the Azure OpenAI embedding deployment name.
             *
             * @param value embedding deployment name
             * @return this builder instance
             */
            public Builder azureEmbeddingDeploymentName(String value) {
                this.azureEmbeddingDeploymentName = value;
                return this;
            }

            /**
             * Sets the Azure OpenAI resource name.
             *
             * @param value Azure resource name
             * @return this builder instance
             */
            public Builder azureResourceName(String value) {
                this.azureResourceName = value;
                return this;
            }

            /**
             * Builds the Azure provider profile.
             *
             * @return Azure provider profile
             */
            public AzureProviderProfile build() {
                return new AzureProviderProfile(this);
            }
        }
    }

    /**
     * Provider profile attributes for AWS Bedrock.
     */
    public static final class AWSProviderProfile extends ProviderProfile {
        /** AWS API format passed as a custom profile attribute. */
        private final String awsApiFormat;

        private AWSProviderProfile(Builder builder) {
            super(builder.providerName(Provider.aws)
                    .providerEndpoint(builder.providerEndpoint));
            this.awsApiFormat = builder.awsApiFormat;
        }

        /**
         * Applies AWS-specific attributes to a profile builder.
         *
         * @param builder profile attributes builder to update
         * @return the same builder instance after AWS fields are applied
         */
        @Override
        public ProfileAttributes.Builder applyTo(ProfileAttributes.Builder builder) {
            super.applyTo(builder);
            if (awsApiFormat != null) {
                builder.customAttribute("aws_apiformat", awsApiFormat);
            }
            return builder;
        }

        /**
         * Builder for {@link AWSProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty AWS provider profile builder. */
            public Builder() {
            }

            /** AWS API format being assembled. */
            private String awsApiFormat;

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Sets the AWS API format custom attribute.
             *
             * @param value AWS API format
             * @return this builder instance
             */
            public Builder awsApiFormat(String value) {
                this.awsApiFormat = value;
                return this;
            }

            /**
             * Builds the AWS provider profile.
             *
             * @return AWS provider profile
             */
            public AWSProviderProfile build() {
                return new AWSProviderProfile(this);
            }
        }
    }

    /**
     * Provider profile attributes for OpenAI.
     */
    public static final class OpenAIProviderProfile extends ProviderProfile {
        private OpenAIProviderProfile(Builder builder) {
            super(builder.providerName(Provider.openai).providerEndpoint(builder.providerEndpoint));
        }

        /**
         * Builder for {@link OpenAIProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty OpenAI provider profile builder. */
            public Builder() {
            }

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Builds the OpenAI provider profile.
             *
             * @return OpenAI provider profile
             */
            public OpenAIProviderProfile build() {
                return new OpenAIProviderProfile(this);
            }
        }
    }

    /**
     * Provider profile attributes for Cohere.
     */
    public static final class CohereProviderProfile extends ProviderProfile {
        private CohereProviderProfile(Builder builder) {
            super(builder.providerName(Provider.cohere).providerEndpoint(builder.providerEndpoint));
        }

        /**
         * Builder for {@link CohereProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty Cohere provider profile builder. */
            public Builder() {
            }

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Builds the Cohere provider profile.
             *
             * @return Cohere provider profile
             */
            public CohereProviderProfile build() {
                return new CohereProviderProfile(this);
            }
        }
    }

    /**
     * Provider profile attributes for Google.
     */
    public static final class GoogleProviderProfile extends ProviderProfile {
        private GoogleProviderProfile(Builder builder) {
            super(builder.providerName(Provider.google).providerEndpoint(builder.providerEndpoint));
        }

        /**
         * Builder for {@link GoogleProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty Google provider profile builder. */
            public Builder() {
            }

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Builds the Google provider profile.
             *
             * @return Google provider profile
             */
            public GoogleProviderProfile build() {
                return new GoogleProviderProfile(this);
            }
        }
    }

    /**
     * Provider profile attributes for Hugging Face.
     */
    public static final class HuggingFaceProviderProfile extends ProviderProfile {
        private HuggingFaceProviderProfile(Builder builder) {
            super(builder.providerName(Provider.huggingface).providerEndpoint(builder.providerEndpoint));
        }

        /**
         * Builder for {@link HuggingFaceProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty Hugging Face provider profile builder. */
            public Builder() {
            }

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Builds the Hugging Face provider profile.
             *
             * @return Hugging Face provider profile
             */
            public HuggingFaceProviderProfile build() {
                return new HuggingFaceProviderProfile(this);
            }
        }
    }

    /**
     * Provider profile attributes for Anthropic.
     */
    public static final class AnthropicProviderProfile extends ProviderProfile {
        private AnthropicProviderProfile(Builder builder) {
            super(builder.providerName(Provider.anthropic).providerEndpoint(builder.providerEndpoint));
        }

        /**
         * Builder for {@link AnthropicProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty Anthropic provider profile builder. */
            public Builder() {
            }

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Builds the Anthropic provider profile.
             *
             * @return Anthropic provider profile
             */
            public AnthropicProviderProfile build() {
                return new AnthropicProviderProfile(this);
            }
        }
    }

    /**
     * Provider profile attributes for OCI Generative AI.
     */
    public static final class OCIGenAIProviderProfile extends ProviderProfile {
        /** OCI API format profile attribute. */
        private final String ociApiFormat;
        /** OCI compartment OCID profile attribute. */
        private final String ociCompartmentId;
        /** OCI dedicated endpoint OCID profile attribute. */
        private final String ociEndpointId;
        /** OCI runtime type profile attribute. */
        private final String ociRuntimeType;

        private OCIGenAIProviderProfile(Builder builder) {
            super(builder.providerName(Provider.oci));
            this.ociApiFormat = builder.ociApiFormat;
            this.ociCompartmentId = builder.ociCompartmentId;
            this.ociEndpointId = builder.ociEndpointId;
            this.ociRuntimeType = builder.ociRuntimeType;
        }

        /**
         * Applies OCI-specific attributes to a profile builder.
         *
         * @param builder profile attributes builder to update
         * @return the same builder instance after OCI fields are applied
         */
        @Override
        public ProfileAttributes.Builder applyTo(ProfileAttributes.Builder builder) {
            super.applyTo(builder)
                    .ociApiformat(ociApiFormat)
                    .ociCompartmentId(ociCompartmentId)
                    .ociEndpointId(ociEndpointId)
                    .ociRuntimetype(ociRuntimeType);
            return builder;
        }

        /**
         * Builder for {@link OCIGenAIProviderProfile}.
         */
        public static class Builder extends ProviderProfile.Builder<Builder> {
            /** Creates an empty OCI Generative AI provider profile builder. */
            public Builder() {
            }

            /** OCI API format being assembled. */
            private String ociApiFormat;
            /** OCI compartment OCID being assembled. */
            private String ociCompartmentId;
            /** OCI endpoint OCID being assembled. */
            private String ociEndpointId;
            /** OCI runtime type being assembled. */
            private String ociRuntimeType;

            /** {@inheritDoc} */
            @Override
            protected Builder self() {
                return this;
            }

            /**
             * Sets the OCI API format.
             *
             * @param value OCI API format
             * @return this builder instance
             */
            public Builder ociApiFormat(String value) {
                this.ociApiFormat = value;
                return this;
            }

            /**
             * Sets the OCI API format.
             *
             * @param value OCI API format
             * @return this builder instance
             */
            public Builder ociApiFormat(OciApiFormat value) {
                this.ociApiFormat = value == null ? null : value.getValue();
                return this;
            }

            /**
             * Sets the OCI compartment OCID.
             *
             * @param value compartment OCID
             * @return this builder instance
             */
            public Builder ociCompartmentId(String value) {
                this.ociCompartmentId = value;
                return this;
            }

            /**
             * Sets the OCI dedicated endpoint OCID.
             *
             * @param value endpoint OCID
             * @return this builder instance
             */
            public Builder ociEndpointId(String value) {
                this.ociEndpointId = value;
                return this;
            }

            /**
             * Sets the OCI runtime type.
             *
             * @param value runtime type
             * @return this builder instance
             */
            public Builder ociRuntimeType(String value) {
                this.ociRuntimeType = value;
                return this;
            }

            /**
             * Builds the OCI Generative AI provider profile.
             *
             * @return OCI Generative AI provider profile
             */
            public OCIGenAIProviderProfile build() {
                return new OCIGenAIProviderProfile(this);
            }
        }
    }
}
