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

/** Integration coverage for CreateProfile. */
class CreateProfileIT extends ProfileIntegrationFixture {

    /**
     * Test: Builds a uniquely named profile with {@code profileTestAttributes()} and the
     * description {@code "OCI GENAI Profile"}, then creates it through the shared fixture.
     * Expected: The returned profile has the generated name, the exact description, and an
     * attribute map equal to the attribute map supplied at creation time.
     */
    @Test
    void test12000CreateBasicProfile() throws Exception {
        String name = uniqueProfileName("PROFILE_1200");
        ProfileAttributes expectedAttributes = profileTestAttributes();
        Profile created = createManagedProfile(name, expectedAttributes,
                "OCI GENAI Profile");

        assertThat(created.getProfileName()).isEqualTo(name);
        assertThat(created.getDescription()).isEqualTo("OCI GENAI Profile");
        assertThat(created.getProfileAttributes().toAttributeMap())
                .isEqualTo(expectedAttributes.toAttributeMap());
    }

    /**
     * Test: Creates a uniquely named profile with the prompt-metadata attribute set, description
     * {@code "Profile created with all create parameters"}, and initial status {@code DISABLED},
     * then retrieves a separate handle with {@code selectAI.profile(name)}.
     * Expected: The retrieved handle has the same name and description, reports status
     * {@code DISABLED}, and returns an attribute map equal to the complete supplied map.
     */
    @Test
void test12001CreateProfileWithAllParametersAndRetrieve() throws Exception {
        String name = uniqueProfileName("PROFILE_1200_PARAMS");
        String description = "Profile created with all create parameters";
        ProfileAttributes expectedAttributes = profileAttributesWithPromptMetadata();

        createManagedProfile(name, expectedAttributes, description, ProfileStatus.DISABLED);

        Profile retrieved = selectAI.profile(name);
        assertThat(retrieved.getProfileName()).isEqualTo(name);
        assertThat(retrieved.getDescription()).isEqualTo(description);
        assertThat(retrieved.getStatus()).isEqualTo(ProfileStatus.DISABLED.getValue());
    assertThat(retrieved.getProfileAttributes().toAttributeMap())
            .isEqualTo(expectedAttributes.toAttributeMap());
}

/**
 * Test: Creates an OCI profile with the complete generic CREATE_PROFILE attribute payload.
 * Expected: Every supplied attribute is persisted and is returned unchanged by a new handle.
 */
@Test
void test12002CreateProfileWithCompleteOciAttributePayload() throws Exception {
    String name = uniqueProfileName("PROFILE_1200_COMPLETE");
    String compartmentId = requiredFeatureValue(
            "SELECT_AI_IT_OCI_COMPARTMENT_ID",
            "test12002 complete payload requires the configured OCI compartment parameter");
    ProfileAttributes expected = ProfileAttributes.builder()
            .annotations(true)
            .additionalInstructions("Use concise answers")
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
            .objectList(profileObjectList())
            .objectListMode("all")
            .ociApiformat("GENERIC")
            .ociCompartmentId(compartmentId)
            .provider("oci")
            .region("us-chicago-1")
            .role("You are a database assistant")
            .seed(12345L)
            .stopTokens(List.of("END_OF_RESPONSE", "STOP"))
            .temperature(0.25)
            .sourceLanguage("en")
            .targetLanguage("de")
            .build();

    createManagedProfile(name, expected, "Complete CREATE_PROFILE payload");

    Profile retrieved = selectAI.profile(name);
    assertThat(retrieved.getProfileAttributes().toAttributeMap())
            .isEqualTo(expected.toAttributeMap());
}

/**
 * Test: Creates a profile without supplying status.
 * Expected: The database applies its ENABLED default rather than Java injecting a value.
 */
@Test
void test12003CreateProfileWithoutStatusUsesDatabaseEnabledDefault() throws Exception {
    Profile created = createManagedProfile(
            uniqueProfileName("PROFILE_1200_STATUS_DEFAULT"),
            profileTestAttributes(),
            null);

    assertThat(created.getStatus()).isEqualTo(ProfileStatus.ENABLED.getValue());
}

/**
 * Test: Creates a profile with a malformed object-list value that Java accepts.
 * Expected: CREATE_PROFILE reaches the database and the database validation is surfaced as
 * SelectAIException rather than Java-side attribute validation.
 */
@Test
void test12004CreateProfileDelegatesInvalidObjectListValidationToDatabase() throws Exception {
    String name = uniqueProfileName("PROFILE_1200_INVALID_OBJECT_LIST");
    ProfileAttributes attributes = ProfileAttributes.builder()
            .credentialName(isolatedCredentialName())
            .provider("oci")
            .region("us-phoenix-1")
            .ociApiformat("GENERIC")
            .objectList("{\"owner\":\"SH\"}")
            .build();
    Profile pending = selectAI.profile(name, attributes, null, null);

    assertThat(attributes.getObjectList()).isEqualTo("{\"owner\":\"SH\"}");
    try {
        assertThatThrownBy(pending::create)
                .isInstanceOfSatisfying(SelectAIException.class, exception ->
                        assertThat(exception.getCause()).isInstanceOf(SQLException.class));
    } finally {
        try {
            pending.drop(true);
        } catch (Exception ignored) {
            // The profile is normally not created when database validation rejects the payload.
        }
    }
}

/**
 * Test: Creates a profile with the maximum documented profile-name length.
 * Expected: A 125-character SQL identifier is accepted by the database.
 */
@Test
void test12005CreateProfileWith125CharacterName() throws Exception {
    String name = profileNameWithLength(125);
    Profile created = createManagedProfile(name, profileTestAttributes(), null);

    assertThat(created.getProfileName()).isEqualTo(name);
}

/**
 * Test: Creates a profile with a name longer than the documented maximum.
 * Expected: The database rejects the name and the SDK exposes SelectAIException.
 */
@Test
void test12006CreateProfileWithOverlongNameUsesDatabaseValidation() throws Exception {
    String name = profileNameWithLength(126);
    Profile pending = selectAI.profile(name, profileTestAttributes(), null, null);

    try {
        assertThatThrownBy(pending::create)
                .isInstanceOfSatisfying(SelectAIException.class, exception ->
                        assertThat(exception.getCause()).isInstanceOf(SQLException.class));
    } finally {
        try {
            pending.drop(true);
        } catch (Exception ignored) {
            // The profile is normally not created when the database rejects the name.
        }
    }
}

/**
 * Test: Creates a profile with OCI endpoint and runtime attributes when an endpoint is configured.
 * Expected: The database persists both supplied values without Java-side rewriting.
 */
@Test
void test12007CreateProfileWithOciEndpointAndRuntimeAttributes() throws Exception {
    String endpointId = requiredFeatureValue(
            "SELECT_AI_IT_OCI_ENDPOINT_ID",
            "test12007 OCI endpoint payload requires a configured endpoint ID");
    String compartmentId = requiredFeatureValue(
            "SELECT_AI_IT_OCI_COMPARTMENT_ID",
            "test12007 OCI endpoint payload requires the configured OCI compartment parameter");
    ProfileAttributes expected = ProfileAttributes.builder()
            .credentialName(isolatedCredentialName())
            .provider("oci")
            .region("us-chicago-1")
            .ociCompartmentId(compartmentId)
            .ociEndpointId(endpointId)
            .ociRuntimetype(envOrDefault("SELECT_AI_IT_OCI_RUNTIMETYPE", DEFAULT_OCI_RUNTIME_TYPE))
            .build();
    String name = uniqueProfileName("PROFILE_1200_OCI_ENDPOINT");

    createManagedProfile(name, expected, null);

    assertThat(selectAI.profile(name).getProfileAttributes().toAttributeMap())
            .isEqualTo(expected.toAttributeMap());
}

    /**
     * Test: Constructs a pending profile with prompt-metadata attributes, a description, and
     * status {@code DISABLED}; reads both getters before {@code create()}, creates it, and reloads it.
     * Expected: Before persistence, {@code getStatus()} and {@code getProfileAttributes()} return
     * the caller-supplied values without losing them; after creation, the database-backed handle
     * reports {@code DISABLED} and the same complete attribute map.
     */
    @Test
    void test12008PendingProfileGettersPreserveConfigurationBeforeCreate() throws Exception {
        String name = uniqueProfileName("PROFILE_PENDING_GETTERS");
        String description = "Pending profile getter regression";
        ProfileAttributes expectedAttributes = profileAttributesWithPromptMetadata();
        Profile pending = selectAI.profile(name, expectedAttributes, description,
                ProfileStatus.DISABLED);

        try {
            assertThat(pending.getStatus()).isEqualTo(ProfileStatus.DISABLED.getValue());
            assertThat(pending.getProfileAttributes().toAttributeMap())
                    .isEqualTo(expectedAttributes.toAttributeMap());

            assertThat(pending.create()).isTrue();
            Profile reloaded = selectAI.profile(name);
            assertThat(reloaded.getStatus()).isEqualTo(ProfileStatus.DISABLED.getValue());
            assertThat(reloaded.getProfileAttributes().toAttributeMap())
                    .isEqualTo(expectedAttributes.toAttributeMap());
        } finally {
            try {
                pending.drop(true);
            } catch (Exception ignored) {
                // Preserve the primary assertion or creation failure.
            }
        }
    }

    /**
     * Test: Creates a uniquely named profile with {@code profileTestAttributes()} and the
     * description {@code "OCI GENAI Profile 2"} through the configured profile's create flow.
     * Expected: The created handle reports the generated name, exact description, and every
     * supplied profile attribute with no omitted or substituted values.
     */
    @Test
    void test12009CreateProfileWithCreateFlow() throws Exception {
        String name = uniqueProfileName("PROFILE_1201");
        ProfileAttributes expectedAttributes = profileTestAttributes();
        Profile created = createManagedProfile(name, expectedAttributes,
                "OCI GENAI Profile 2");

        assertThat(created.getProfileName()).isEqualTo(name);
        assertThat(created.getDescription()).isEqualTo("OCI GENAI Profile 2");
        assertThat(created.getProfileAttributes().toAttributeMap())
                .isEqualTo(expectedAttributes.toAttributeMap());
    }

    /**
     * Test: Calls {@code generate}, {@code chat}, both attribute-update methods, {@code drop},
     * {@code enable}, and {@code disable} on a profile handle that has configuration but has not
     * been created.
     * Expected: Each call throws {@code IllegalStateException} with the operation-specific
     * message stating that {@code create()} must be called first; no database-backed operation is attempted.
     */
    @Test
    void test12010ProfileRejectsDatabaseOperationsBeforeCreate() throws Exception {
        Profile pendingProfile = selectAI.profile(
                uniqueProfileName("PROFILE_PENDING"),
                profileTestAttributes(),
                null,
                null);

        assertThatThrownBy(() -> pendingProfile.generate("show employees", GenerateAction.chat))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("generate requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.chat("show employees"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("generate requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.setAttribute("model", MODEL_UPDATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("setAttribute requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.setAttributes(profileTestAttributes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("setAttributes requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.drop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created profile; call create() first");
        assertThatThrownBy(pendingProfile::enable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("enable requires a created profile; call create() first");
        assertThatThrownBy(pendingProfile::disable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disable requires a created profile; call create() first");
    }

    /**
     * Test: Creates a profile with only the isolated credential name and provider {@code "oci"},
     * leaving the description unset.
     * Expected: Creation returns a profile with the generated name, a {@code null} description,
     * the isolated credential name, and provider {@code "oci"}.
     */
    @Test
    void test12011CreateMinimumAttributes() throws Exception {
        ProfileAttributes minimum = ProfileAttributes.builder()
                .credentialName(isolatedCredentialName())
                .provider("oci")
                .build();
        String name = uniqueProfileName("PROFILE_1203_MIN");

        Profile created = createManagedProfile(name, minimum, null);
        assertThat(created.getProfileName()).isEqualTo(name);
        assertThat(created.getDescription()).isNull();
        assertThat(created.getProfileAttributes().getCredentialName())
                .isEqualTo(isolatedCredentialName());
        assertThat(created.getProfileAttributes().getProvider()).isEqualTo("oci");
    }

/**
 * Test: Creates a profile with a description larger than the PL/SQL VARCHAR2 bind limit.
 * Expected: CREATE_PROFILE accepts and returns the complete CLOB description without truncation.
 */
@Test
void test12012CreateProfileWithLargeClobDescription() throws Exception {
    String name = uniqueProfileName("PROFILE_1200_LARGE_DESCRIPTION");
    String description = largeClobValue("profile-description");

    createManagedProfile(name, profileTestAttributes(), description);

    assertThat(selectAI.profile(name).getDescription()).isEqualTo(description);
}

}
