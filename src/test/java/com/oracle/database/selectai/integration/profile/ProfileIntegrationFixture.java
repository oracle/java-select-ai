/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.profile;

import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live profile lifecycle and attribute integration coverage.
 *
 * <p>The shared fixture supplies environment loading, JDBC
 * setup, isolated resource names, and cleanup. Tests exercise profile
 * creation, retrieval, attribute updates, status changes, and lifecycle
 * behavior.</p>
 */
abstract class ProfileIntegrationFixture extends IntegrationTestFixture {

    protected static final String MODEL_UPDATE = "meta.llama-3.1-70b-instruct";
    protected static final String MULTI_ATTRIBUTE_MODEL =
            "meta.llama-4-maverick-17b-128e-instruct-fp8";
    protected static final String DEFAULT_OCI_RUNTIME_TYPE = "COHERE";

    @Override
    protected String profileObjectList() {
        return objectListForOwner("SH");
    }


    protected void assertAllProfileAttributes(ProfileAttributes expected, ProfileAttributes actual) {
        assertThat(actual.getAnnotations()).isEqualTo(expected.getAnnotations());
        assertThat(actual.getAzureDeploymentName()).isEqualTo(expected.getAzureDeploymentName());
        assertThat(actual.getAzureEmbeddingDeploymentName())
                .isEqualTo(expected.getAzureEmbeddingDeploymentName());
        assertThat(actual.getAzureResourceName()).isEqualTo(expected.getAzureResourceName());
        assertThat(actual.getCaseSensitiveValues()).isEqualTo(expected.getCaseSensitiveValues());
        assertThat(actual.getComments()).isEqualTo(expected.getComments());
        assertThat(actual.getConstraints()).isEqualTo(expected.getConstraints());
        assertThat(actual.getConversation()).isEqualTo(expected.getConversation());
        assertThat(actual.getConversationLength()).isEqualTo(expected.getConversationLength());
        assertThat(actual.getCredentialName()).isEqualTo(expected.getCredentialName());
        assertThat(actual.getEmbeddingModel()).isEqualTo(expected.getEmbeddingModel());
        assertThat(actual.getEnableCustomSourceUri())
                .isEqualTo(expected.getEnableCustomSourceUri());
        assertThat(actual.getEnforceObjectList()).isEqualTo(expected.getEnforceObjectList());
        assertThat(actual.getMaxTokens()).isEqualTo(expected.getMaxTokens());
        assertThat(actual.getModel()).isEqualTo(expected.getModel());
        assertThat(actual.getObjectList()).isEqualTo(expected.getObjectList());
        assertThat(actual.getObjectListMode()).isEqualTo(expected.getObjectListMode());
        assertThat(actual.getOciApiformat()).isEqualTo(expected.getOciApiformat());
        assertThat(actual.getOciCompartmentId()).isEqualTo(expected.getOciCompartmentId());
        assertThat(actual.getOciEndpointId()).isEqualTo(expected.getOciEndpointId());
        assertThat(actual.getOciRuntimetype()).isEqualTo(expected.getOciRuntimetype());
        assertThat(actual.getProvider()).isEqualTo(expected.getProvider());
        assertThat(actual.getProviderEndpoint()).isEqualTo(expected.getProviderEndpoint());
        assertThat(actual.getRegion()).isEqualTo(expected.getRegion());
        assertThat(actual.getSeed()).isEqualTo(expected.getSeed());
        assertThat(actual.getStopTokens()).containsExactlyElementsOf(expected.getStopTokens());
        assertThat(actual.getTemperature()).isEqualTo(expected.getTemperature());
        assertThat(actual.getVectorIndexName()).isEqualTo(expected.getVectorIndexName());
        assertThat(actual.getSourceLanguage()).isEqualTo(expected.getSourceLanguage());
        assertThat(actual.getTargetLanguage()).isEqualTo(expected.getTargetLanguage());
        assertThat(actual.toAttributeMap()).containsAllEntriesOf(expected.toAttributeMap());
    }

    protected ProfileAttributes attributesWithout(String attributeName) {
        Map<String, String> attributes = new HashMap<>(profileTestAttributes().toAttributeMap());
        attributes.remove(attributeName);
        return ProfileAttributes.fromAttributeMap(attributes);
    }

    protected ProfileAttributes profileTestAttributes() {
        return ProfileAttributes.builder()
                .credentialName(isolatedCredentialName())
                .provider("oci")
                .region("us-phoenix-1")
                .ociApiformat("GENERIC")
                .objectList(profileObjectList())
                .build();
    }

protected ProfileAttributes profileAttributesWithPromptMetadata() {
    return ProfileAttributes.builder()
            .credentialName(isolatedCredentialName())
            .provider("oci")
                .region("us-phoenix-1")
                .ociApiformat("GENERIC")
                .objectList(profileObjectList())
                .additionalInstructions("Use concise answers")
            .role("You are a database assistant")
            .build();
}

protected static String profileNameWithLength(int length) {
    String unique = UUID.randomUUID().toString().replace("-", "").toUpperCase();
    String prefix = "JSAI_" + unique;
    return prefix + "A".repeat(length - prefix.length());
}

protected static String largeClobValue(String marker) {
    return marker + ":" + "x".repeat(40_000 - marker.length() - 1);
}

protected static String uniqueProfileName(String prefix) {
        return "JSAI_" + prefix + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
