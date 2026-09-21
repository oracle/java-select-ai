/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.profile;

import com.oracle.database.selectai.Profile;
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
 * <p>The common integration support supplies environment loading, JDBC
 * setup, isolated resource names, and cleanup. Tests exercise profile
 * creation, retrieval, attribute updates, status changes, and lifecycle
 * behavior.</p>
 */

/** Integration coverage for ProfileAttributes. */
class ProfileAttributesIT extends ProfileIntegrationFixture {

    /**
     * Test: Retrieves the fixture profile by name and reads its name, credential name, provider,
     * and object-list attributes.
     * Expected: The handle has the fixture profile name, the isolated credential name, provider
     * {@code "oci"}, and an object list equal to the fixture profile's stored object list.
     */
    @Test
    void test12200FetchProfileAttributes() throws Exception {
        Profile fetched = selectAI.profile(profileName);
        assertThat(fetched.getProfileName()).isEqualTo(profileName);
        assertThat(fetched.getProfileAttributes().getCredentialName())
                .isEqualTo(isolatedCredentialName());
        assertThat(fetched.getProfileAttributes().getProvider()).isEqualTo("oci");
        assertThat(fetched.getProfileAttributes().getObjectList())
                .isEqualTo(profile.getProfileAttributes().getObjectList());
    }

    /**
     * Test: Creates an enabled OCI profile with model {@code MODEL_UPDATE}, opens a second handle,
     * changes the database model through {@code setAttribute("model", MULTI_ATTRIBUTE_MODEL)},
     * and reads the original handle again.
     * Expected: The original handle refreshes from the database and returns
     * {@code MULTI_ATTRIBUTE_MODEL}, rather than retaining the old model value.
     */
    @Test
    void test12201GetProfileAttributesReflectsExternalAttributeUpdate() throws Exception {
        String name = uniqueProfileName("PROFILE_1206_STALE");
        ProfileAttributes initialAttributes = ProfileAttributes.builder()
                .credentialName(isolatedCredentialName())
                .model(MODEL_UPDATE)
                .provider("oci")
                .region("us-phoenix-1")
                .ociApiformat("GENERIC")
                .objectList(profileObjectList())
                .build();
        Profile original = createManagedProfile(name, initialAttributes, null);

        assertThat(original.getProfileAttributes().getModel()).isEqualTo(MODEL_UPDATE);

        Profile independent = selectAI.profile(name);
        assertThat(independent.setAttribute("model", MULTI_ATTRIBUTE_MODEL)).isTrue();

        assertThat(original.getProfileAttributes().getModel()).isEqualTo(MULTI_ATTRIBUTE_MODEL);
    }

    /**
     * Test: Creates a profile without a model, calls {@code setAttribute("model", MODEL_UPDATE)},
     * and retrieves the profile through a new handle.
     * Expected: The setter returns {@code true}; the reloaded profile has the generated name,
     * model {@code MODEL_UPDATE}, and an attribute map containing the {@code model} entry.
     */
    @Test
    void test12202SetAttribute() throws Exception {
        ProfileAttributes attributes = attributesWithout("model");
        String name = uniqueProfileName("PROFILE_1207");
        Profile target = createManagedProfile(name, attributes, null);
        assertThat(target.getProfileAttributes().getModel()).isNull();

        assertThat(target.setAttribute("model", MODEL_UPDATE)).isTrue();
        Profile fetched = selectAI.profile(name);
        assertThat(fetched.getProfileName()).isEqualTo(name);
        assertThat(fetched.getProfileAttributes().getModel()).isEqualTo(MODEL_UPDATE);
        assertThat(fetched.getProfileAttributes().toAttributeMap())
                .containsEntry("model", MODEL_UPDATE);
    }

    /**
     * Test: Builds {@code ProfileAttributes} with model {@code MODEL_UPDATE} but without a
     * provider, then reads both model and provider from the built object.
     * Expected: The model remains {@code MODEL_UPDATE} and the provider remains {@code null}; the
     * attributes builder does not populate a provider implicitly.
     */
    @Test
    void test12203ProfileAttributesSetProviderAttributeWithoutProvider() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .model(MODEL_UPDATE)
                .build();

        assertThat(attributes.getProvider()).isNull();
        assertThat(attributes.getModel()).isEqualTo(MODEL_UPDATE);
    }

    /**
     * Test: Creates an OCI profile and updates each supported OCI attribute separately through
     * {@code setAttribute()}, including booleans, numeric values, credential/provider fields,
     * object-list settings, OCI identifiers, region, seed, stop tokens, and temperature.
     * Expected: Every setter returns {@code true}; reloading the profile returns the exact expected
     * value for every updated field, including the two stop tokens and numeric values.
     */
    @Test
    void test12204SetAllOciAttributesWithSetAttribute() throws Exception {
        String name = uniqueProfileName("PROFILE_1208_SINGLE");
        Profile target = createManagedProfile(name, profileTestAttributes(), null);
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "test_1208 requires the configured OCI compartment parameter");
        ProfileAttributes expected = ProfileAttributes.builder()
                .annotations(true)
                .caseSensitiveValues(true)
                .comments(true)
                .constraints(true)
                .conversation(true)
                .conversationLength(12)
                .credentialName(isolatedCredentialName())
                .embeddingModel("text-embedding-3-large")
                .enableCustomSourceUri(true)
                .enforceObjectList(true)
                .maxTokens(2048)
                .model(MULTI_ATTRIBUTE_MODEL)
                .objectList("[{\"owner\":\"ADMIN\",\"name\":\"gymnasts\"}]")
                .objectListMode("all")
                .ociApiformat("GENERIC")
                .ociCompartmentId(compartmentId)
                .provider("oci")
                .region("us-chicago-1")
                .seed(12345L)
                .stopTokens(List.of("END_OF_RESPONSE", "STOP"))
                .temperature(0.25)
                .build();
        String stopTokensJson = "[\"END_OF_RESPONSE\",\"STOP\"]";

        assertThat(target.setAttribute("annotations", expected.getAnnotations())).isTrue();
        assertThat(target.setAttribute("case_sensitive_values", expected.getCaseSensitiveValues()))
                .isTrue();
        assertThat(target.setAttribute("comments", expected.getComments())).isTrue();
        assertThat(target.setAttribute("constraints", expected.getConstraints())).isTrue();
        assertThat(target.setAttribute("conversation", expected.getConversation())).isTrue();
        assertThat(target.setAttribute("conversation_length", expected.getConversationLength()))
                .isTrue();
        assertThat(target.setAttribute("credential_name", expected.getCredentialName())).isTrue();
        assertThat(target.setAttribute("embedding_model", expected.getEmbeddingModel())).isTrue();
        assertThat(target.setAttribute("enable_custom_source_uri",
                expected.getEnableCustomSourceUri())).isTrue();
        assertThat(target.setAttribute("enforce_object_list", expected.getEnforceObjectList()))
                .isTrue();
        assertThat(target.setAttribute("max_tokens", expected.getMaxTokens())).isTrue();
        assertThat(target.setAttribute("model", expected.getModel())).isTrue();
        assertThat(target.setAttribute("object_list", expected.getObjectList())).isTrue();
        assertThat(target.setAttribute("object_list_mode", expected.getObjectListMode())).isTrue();
        assertThat(target.setAttribute("oci_apiformat", expected.getOciApiformat())).isTrue();
        assertThat(target.setAttribute("oci_compartment_id", expected.getOciCompartmentId()))
                .isTrue();
        assertThat(target.setAttribute("provider", expected.getProvider())).isTrue();
        assertThat(target.setAttribute("region", expected.getRegion())).isTrue();
        assertThat(target.setAttribute("seed", String.valueOf(expected.getSeed()))).isTrue();
        assertThat(target.setAttribute("stop_tokens", stopTokensJson)).isTrue();
        assertThat(target.setAttribute("temperature", expected.getTemperature().floatValue()))
                .isTrue();

        Profile fetchedProfile = selectAI.profile(name);
        assertThat(fetchedProfile.getProfileName()).isEqualTo(name);
        assertAllProfileAttributes(expected, fetchedProfile.getProfileAttributes());
    }

    /**
     * Test: Creates an OCI profile and sends one {@code ProfileAttributes} object through
     * {@code setAttributes()} containing the complete multi-field replacement, including
     * object-list, OCI, model, token, seed, stop-token, and temperature values.
     * Expected: The bulk setter returns {@code true}; a reloaded profile has an attribute map
     * equal to the complete replacement map, with no field omitted.
     */
    @Test
    void test12205SetMultipleAttributes() throws Exception {
        String name = uniqueProfileName("PROFILE_1208");
        Profile target = createManagedProfile(name, profileTestAttributes(), null);
        ProfileAttributes replacement = ProfileAttributes.builder()
                .annotations(true)
                .caseSensitiveValues(true)
                .comments(true)
                .constraints(true)
                .conversation(true)
                .conversationLength(12)
                .credentialName(isolatedCredentialName())
                .embeddingModel("text-embedding-3-large")
                .enableCustomSourceUri(true)
                .enforceObjectList(true)
                .maxTokens(2048)
                .model(MULTI_ATTRIBUTE_MODEL)
                .objectList("[{\"owner\":\"ADMIN\",\"name\":\"gymnasts\"}]")
                .objectListMode("all")
                .ociApiformat("GENERIC")
                .ociCompartmentId(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                        "test_1208 requires the configured OCI compartment parameter"))
                .provider("oci")
                .region("us-chicago-1")
                .seed(12345L)
                .stopTokens(List.of("END_OF_RESPONSE", "STOP"))
                .temperature(0.25)
                .build();

        assertThat(target.setAttributes(replacement)).isTrue();
        Profile fetchedProfile = selectAI.profile(name);
        assertThat(fetchedProfile.getProfileName()).isEqualTo(name);
        assertAllProfileAttributes(replacement, fetchedProfile.getProfileAttributes());
    }

    /**
     * Test: Creates an OCI profile with credential, provider, region, API format, and compartment,
     * then calls {@code setAttribute("vector_index_name", "RAG_IDX")}.
     * Expected: The setter returns {@code true}; the reloaded attributes report vector-index name
     * {@code "RAG_IDX"}, while object-list remains {@code null}.
     */
    @Test
void test12206SetVectorIndexNameWithSetAttribute() throws Exception {
        String name = uniqueProfileName("PROFILE_1208_VECTOR_SINGLE");
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "test_1208 requires the configured OCI compartment parameter");
        Profile target = createManagedProfile(
                name,
                ProfileAttributes.builder()
                        .credentialName(isolatedCredentialName())
                        .provider("oci")
                        .region("us-chicago-1")
                        .ociApiformat("GENERIC")
                        .ociCompartmentId(compartmentId)
                .build(),
                null);

        assertThat(target.setAttribute("vector_index_name", "RAG_IDX")).isTrue();

        ProfileAttributes actual = selectAI.profile(name).getProfileAttributes();
        assertThat(actual.getVectorIndexName()).isEqualTo("RAG_IDX");
        assertThat(actual.getObjectList()).isNull();
    }

    /**
     * Test: Creates an OCI profile with no object list and calls {@code setAttributes()} with the
     * same OCI configuration plus vector-index name {@code "RAG_IDX"}.
     * Expected: The bulk setter returns {@code true}; the reloaded attributes return
     * {@code "RAG_IDX"} and preserve object-list as {@code null}.
     */
    @Test
    void test12207SetVectorIndexName() throws Exception {
        String name = uniqueProfileName("PROFILE_1208_VECTOR");
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "test_1208 requires the configured OCI compartment parameter");
        Profile target = createManagedProfile(
                name,
                ProfileAttributes.builder()
                        .credentialName(isolatedCredentialName())
                        .provider("oci")
                        .region("us-chicago-1")
                        .ociApiformat("GENERIC")
                        .ociCompartmentId(compartmentId)
                .build(),
                null);
        ProfileAttributes expected = ProfileAttributes.builder()
                .credentialName(isolatedCredentialName())
                .provider("oci")
                .region("us-chicago-1")
                .ociApiformat("GENERIC")
                .ociCompartmentId(compartmentId)
                .vectorIndexName("RAG_IDX")
                .build();

        assertThat(target.setAttributes(expected)).isTrue();

        ProfileAttributes actual = selectAI.profile(name).getProfileAttributes();
        assertThat(actual.getVectorIndexName()).isEqualTo(expected.getVectorIndexName());
        assertThat(actual.getObjectList()).isNull();
    }

    /**
     * Test: Creates an OCI profile with an object list and compartment, then calls
     * {@code setAttribute("oci_runtimetype", "COHERE")} without setting an OCI API format.
     * Expected: The reloaded profile retains provider {@code "oci"} and the compartment, reports
     * runtime type {@code "COHERE"}, and leaves OCI API format {@code null}.
     */
    @Test
    void test12208SetOciRuntimeTypeWithSetAttribute() throws Exception {
        String name = uniqueProfileName("PROFILE_1208_RUNTIME_SINGLE");
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "test_1208 requires the configured OCI compartment parameter");
        Profile target = createManagedProfile(
                name,
                ProfileAttributes.builder()
                        .credentialName(isolatedCredentialName())
                        .provider("oci")
                        .region("us-phoenix-1")
                        .ociCompartmentId(compartmentId)
                        .objectList(profileObjectList())
                .build(),
                null);

        assertThat(target.setAttribute("oci_runtimetype", "COHERE")).isTrue();

        ProfileAttributes actual = selectAI.profile(name).getProfileAttributes();
        assertThat(actual.getProvider()).isEqualTo("oci");
        assertThat(actual.getOciCompartmentId()).isEqualTo(compartmentId);
        assertThat(actual.getOciApiformat()).isNull();
        assertThat(actual.getOciRuntimetype()).isEqualTo("COHERE");
    }

    /**
     * Test: Creates an OCI profile with model absent, then bulk-updates it with endpoint ID
     * {@code "ocid1.generativeaiendpoint.oc1..example"} and the existing OCI configuration.
     * Expected: The reloaded profile retains provider {@code "oci"} and the compartment, returns
     * the exact endpoint ID, and keeps model {@code null} rather than deriving one.
     */
    @Test
    void test12209SetOciEndpointId() throws Exception {
        String name = uniqueProfileName("PROFILE_1208_ENDPOINT");
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "test_1208 requires the configured OCI compartment parameter");
        Profile target = createManagedProfile(
                name,
                ProfileAttributes.builder()
                        .credentialName(isolatedCredentialName())
                        .provider("oci")
                        .region("us-chicago-1")
                        .ociApiformat("GENERIC")
                        .ociCompartmentId(compartmentId)
                        .objectList(profileObjectList())
                .build(),
                null);
        ProfileAttributes expected = ProfileAttributes.builder()
                .credentialName(isolatedCredentialName())
                .provider("oci")
                .region("us-chicago-1")
                .ociApiformat("GENERIC")
                .ociCompartmentId(compartmentId)
                .ociEndpointId("ocid1.generativeaiendpoint.oc1..example")
                .objectList(profileObjectList())
                .build();

        assertThat(target.setAttributes(expected)).isTrue();

        ProfileAttributes actual = selectAI.profile(name).getProfileAttributes();
        assertThat(actual.getProvider()).isEqualTo("oci");
        assertThat(actual.getModel()).isNull();
        assertThat(actual.getOciEndpointId()).isEqualTo(expected.getOciEndpointId());
        assertThat(actual.getOciCompartmentId()).isEqualTo(compartmentId);
    }

    /**
     * Test: Creates an OCI profile with an object list and calls {@code setAttributes()} with
     * runtime type {@code "COHERE"}, credential, provider, region, and compartment values.
     * Expected: The bulk setter returns {@code true}; the reloaded attributes report provider
     * {@code "oci"}, the supplied compartment, and runtime type {@code "COHERE"}.
     */
    @Test
    void test12210SetOciRuntimeType() throws Exception {
        String name = uniqueProfileName("PROFILE_1208_RUNTIME");
        String compartmentId = requiredFeatureValue(
                "SELECT_AI_IT_OCI_COMPARTMENT_ID",
                "test_1208 requires the configured OCI compartment parameter");
        Profile target = createManagedProfile(
                name,
                ProfileAttributes.builder()
                        .credentialName(isolatedCredentialName())
                        .provider("oci")
                        .region("us-phoenix-1")
                        .ociCompartmentId(compartmentId)
                        .objectList(profileObjectList())
                .build(),
                null);
        ProfileAttributes expected = ProfileAttributes.builder()
                .credentialName(isolatedCredentialName())
                .provider("oci")
                .region("us-phoenix-1")
                .ociCompartmentId(compartmentId)
                .ociRuntimetype("COHERE")
                .objectList(profileObjectList())
                .build();

        assertThat(target.setAttributes(expected)).isTrue();
        ProfileAttributes actual = selectAI.profile(name).getProfileAttributes();
        assertThat(actual.getProvider()).isEqualTo("oci");
        assertThat(actual.getOciCompartmentId()).isEqualTo(compartmentId);
        assertThat(actual.getOciRuntimetype()).isEqualTo("COHERE");
    }

}
