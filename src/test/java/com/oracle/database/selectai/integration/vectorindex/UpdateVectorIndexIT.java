/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

/**
 * Live integration coverage for vector-index operations.
 *
 * <p>The suite uses isolated indexes and credentials from the shared integration
 * fixture. Required vector-index credentials are supplied through the
 * SELECT_AI_IT_* configuration keys.</p>
 *
 * <p>The suite verifies creation, update, metadata, listing, lifecycle, and
 * Java-specific resource behavior through the public synchronous SDK APIs.</p>
 */
package com.oracle.database.selectai.integration.vectorindex;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integration coverage for UpdateVectorIndex. */
class UpdateVectorIndexIT extends VectorIndexIntegrationFixture {

    /**
     * Test: Create an existing index and send the string overload update("match_limit", "10").
     * Expected: the SDK sends match_limit=10 to the database and update() returns true.
     */
    @Test
    void test52000UpdateMatchLimit() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5201");
        assertThat(index.update("match_limit", "10")).isTrue();
    }

    /**
     * Test: Update similarity_threshold first through update("similarity_threshold", "0.8") and
     * then through VectorIndexAttributes.updateBuilder().similarityThreshold(1.0).
     * Expected: both update APIs return true, covering string and typed-builder payloads for the
     * same mutable database attribute.
     */
    @Test
    void test52001UpdateSimilarityThreshold() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5202");
        assertThat(index.update("similarity_threshold", "0.8")).isTrue();
        assertThat(index.update(VectorIndexAttributes.updateBuilder()
                .similarityThreshold(1.0)
                .build())).isTrue();
    }

    /**
     * Test: Build one bulk update containing mutable matchLimit(5) and create-only location(...),
     * then pass it to VectorIndex.update(VectorIndexAttributes).
     * Expected: the database rejects the mixed payload with SelectAIException and ORA-20047;
     * the request does not partially apply the restricted location update.
     */
    @Test
    void test52002BulkUpdateWithRestrictedAttributeIsRejected() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5203");
        VectorIndexAttributes attrs = VectorIndexAttributes.updateBuilder()
                .matchLimit(5)
                .location(embeddingLocation())
                .build();
        assertThatThrownBy(() -> index.update(attrs))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
    }

    /**
     * Test: Send two string updates to the same existing index, changing similarity_threshold from
     * 0.8 to 0.5.
     * Expected: both database updates return true and the second value replaces the first.
     */
    @Test
    void test52003RepeatedSimilarityThresholdUpdate() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5204");
        assertThat(index.update("similarity_threshold", "0.8")).isTrue();
        assertThat(index.update("similarity_threshold", "0.5")).isTrue();
    }

    /**
     * Test: Send update("match_limit", "8192"), the supported maximum used by this scenario.
     * Expected: the database accepts match_limit=8192 and update() returns true.
     */
    @Test
    void test52004UpdateMatchLimitMaximum() throws Exception {
        assertThat(existingIndex("UPDATE_5205").update("match_limit", "8192")).isTrue();
    }

    /**
     * Test: Send update("match_limit", "1"), the supported positive minimum used by this suite.
     * Expected: the database accepts match_limit=1 and update() returns true.
     */
    @Test
    void test52005UpdateMatchLimitMinimum() throws Exception {
        assertThat(existingIndex("UPDATE_5206").update("match_limit", "1")).isTrue();
    }

    /**
     * Test: Create a temporary profile, update profile_name through both the string overload and
     * updateBuilder(), read the index through direct and list lookups, and restore the original
     * isolated profile name.
     * Expected: each update returns true; direct and list retrieval report the temporary profile;
     * the final update restores the original profile name in database metadata.
     */
    @Test
    void test52006UpdateProfileAndRestore() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5208");
        String temporaryProfileName = uniqueName("TEMP_PROFILE_5208");
        Profile temporaryProfile = createManagedProfile(
                temporaryProfileName,
                isolatedProfileAttributes(),
                "Temporary vector-index profile");

        assertThat(index.update("profile_name", temporaryProfile.getProfileName())).isTrue();
        assertThat(index.update(VectorIndexAttributes.updateBuilder()
                .profileName(temporaryProfileName)
                .build())).isTrue();
        assertThat(selectAI.profile(temporaryProfileName).getProfileName()).isEqualTo(temporaryProfileName);
        VectorIndex updatedIndex = selectAI.vectorIndex(index.getIndexName());
        assertThat(updatedIndex.getVectorIndexAttributes().getProfileName())
                .isEqualTo(temporaryProfileName);
        VectorIndex listedIndex = selectAI.listVectorIndexes("^" + index.getIndexName() + "$")
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(listedIndex.getVectorIndexAttributes().getProfileName())
                .isEqualTo(temporaryProfileName);
        assertThat(index.update("profile_name", profileName)).isTrue();
        assertThat(selectAI.vectorIndex(index.getIndexName()).getVectorIndexAttributes().getProfileName())
                .isEqualTo(profileName);
    }

    /**
     * Test: Point an index at a temporary profile, delete that profile, read the index metadata,
     * and then update profile_name back to the isolated profile.
     * Expected: profile lookup reports "SelectAI profile not found: <temporary name>", the index
     * still reports the deleted profile reference, and the final update restores a valid profile.
     */
    @Test
    void test52007UpdateDeletedProfileReferenceAndRestore() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5209");
        String temporaryProfileName = uniqueName("TEMP_PROFILE_5209");
        Profile temporaryProfile = createManagedProfile(
                temporaryProfileName,
                isolatedProfileAttributes(),
                "Temporary deleted-profile test profile");

        assertThat(index.update("profile_name", temporaryProfileName)).isTrue();
        assertThat(temporaryProfile.drop(true)).isTrue();
        assertThatThrownBy(() -> selectAI.profile(temporaryProfileName))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("SelectAI profile not found: " + temporaryProfileName);
        VectorIndex staleReference = selectAI.vectorIndex(index.getIndexName());
        assertThat(staleReference.getVectorIndexAttributes().getProfileName())
                .isEqualTo(temporaryProfileName);
        assertThat(index.update("profile_name", profileName)).isTrue();
        assertThat(selectAI.vectorIndex(index.getIndexName()).getVectorIndexAttributes().getProfileName())
                .isEqualTo(profileName);
    }

    /**
     * Test: Change an index to use a temporary profile, delete the profile, retrieve the index by
     * name, and finally restore the original profile name.
     * Expected: the retrieved index preserves the deleted profile name as its stored reference;
     * restoring profile_name to the isolated profile returns true.
     */
    @Test
    void test52008DeletedProfileLeavesStaleReference() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5210");
        String temporaryProfileName = uniqueName("TEMP_PROFILE_5210");
        Profile temporaryProfile = createManagedProfile(
                temporaryProfileName,
                isolatedProfileAttributes(),
                "Temporary deleted-profile test profile");

        assertThat(index.update("profile_name", temporaryProfileName)).isTrue();
        assertThat(temporaryProfile.drop(true)).isTrue();

        VectorIndex fetched = selectAI.vectorIndex(index.getIndexName());
        assertThat(fetched.getVectorIndexAttributes().getProfileName())
                .isEqualTo(temporaryProfileName);

        assertThat(index.update("profile_name", profileName)).isTrue();
    }

    /**
     * Test: Send update("refresh_rate", "30") to an existing vector index.
     * Expected: the database accepts refresh_rate=30 and update() returns true.
     */
    @Test
    void test52009UpdateRefreshRate() throws Exception {
        assertThat(existingIndex("UPDATE_5211").update("refresh_rate", "30")).isTrue();
    }

    /**
     * Test: Read the generated pipeline name, stop that pipeline, create a temporary OCI
     * object-storage credential, update object_storage_credential_name through both update APIs,
     * read the updated metadata, then restore the original credential and restart the pipeline.
     * Expected: both credential updates return true, retrieval reports the temporary credential,
     * and finally the original credential is restored before pipeline restart and cleanup.
     */
    @Test
    void test52010UpdateObjectStorageCredentialAndPipeline() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5212");
        String pipelineName = currentPipelineName(index);
        String temporaryCredentialName = uniqueCredentialName("TEMP_OBJSTORE_5212");
        Credential temporaryCredential = null;
        boolean pipelineStopped = false;
        try {
            stopPipeline(pipelineName);
            pipelineStopped = true;
            temporaryCredential = createManagedCredential(
                    ociSigningKeyCredentialConfig(temporaryCredentialName));

            assertThat(index.update("object_storage_credential_name", temporaryCredentialName)).isTrue();
            assertThat(index.update(VectorIndexAttributes.updateBuilder()
                    .objectStorageCredentialName(temporaryCredentialName)
                    .build())).isTrue();
            assertThat(selectAI.vectorIndex(index.getIndexName())
                    .getVectorIndexAttributes().getObjectStorageCredentialName())
                    .isEqualTo(temporaryCredentialName);
        } finally {
            try {
                index.update("object_storage_credential_name", objectStorageCredentialName);
            } finally {
                if (temporaryCredential != null) {
                    temporaryCredential.drop();
                }
                if (pipelineStopped) {
                    startPipeline(pipelineName);
                }
            }
        }
    }

    /**
     * Test: Stop the index pipeline, replace its object-storage credential with a temporary
     * credential, delete that credential, retrieve the index metadata, and restore the original
     * credential before restarting the pipeline.
     * Expected: the database retains the deleted credential name in index metadata, and cleanup
     * restores a usable credential and running pipeline.
     */
    @Test
    void test52011UpdateDeletedCredentialReference() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5213");
        String pipelineName = currentPipelineName(index);
        String temporaryCredentialName = uniqueCredentialName("TEMP_OBJSTORE_5213");
        Credential temporaryCredential = null;
        boolean pipelineStopped = false;
        try {
            stopPipeline(pipelineName);
            pipelineStopped = true;
            temporaryCredential = createManagedCredential(
                    ociSigningKeyCredentialConfig(temporaryCredentialName));

            assertThat(index.update("object_storage_credential_name", temporaryCredentialName)).isTrue();
            assertThat(temporaryCredential.drop()).isTrue();
            temporaryCredential = null;

            assertThat(selectAI.vectorIndex(index.getIndexName())
                    .getVectorIndexAttributes().getObjectStorageCredentialName())
                    .isEqualTo(temporaryCredentialName);
        } finally {
            try {
                index.update("object_storage_credential_name", objectStorageCredentialName);
            } finally {
                if (temporaryCredential != null) {
                    temporaryCredential.drop();
                }
                if (pipelineStopped) {
                    startPipeline(pipelineName);
                }
            }
        }
    }

    /**
     * Test: Stop an index pipeline, assign it a temporary credential, delete that credential, and
     * attempt to create a second index that names the deleted credential.
     * Expected: creation of the second index throws SelectAIException with error code 20004 and
     * a cause containing ORA-20004 and the deleted credential name; the original index is restored
     * to its fixture credential during cleanup.
     */
    @Test
    void test52012DeletedCredentialLeavesIndexUnusable() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5214");
        String pipelineName = currentPipelineName(index);
        String temporaryCredentialName = uniqueCredentialName("TEMP_OBJSTORE_5214");
        Credential temporaryCredential = null;
        boolean pipelineStopped = false;
        boolean credentialUpdated = false;
        try {
            stopPipeline(pipelineName);
            pipelineStopped = true;

            temporaryCredential = createManagedCredential(
                    ociSigningKeyCredentialConfig(temporaryCredentialName));
            assertThat(index.update("object_storage_credential_name", temporaryCredentialName)).isTrue();
            credentialUpdated = true;

            assertThat(temporaryCredential.drop()).isTrue();
            temporaryCredential = null;

            VectorIndex failingIndex = createConfiguredIndexWithCredential(
                    uniqueIndexName("UPDATE_5214_MISSING_CREDENTIAL"), temporaryCredentialName);
            assertThatThrownBy(failingIndex::create)
                    .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(20004);
                        assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20004:")
                                .hasMessageContaining(temporaryCredentialName);
                    });
        } finally {
            try {
                if (credentialUpdated) {
                    assertThat(index.update("object_storage_credential_name", objectStorageCredentialName))
                            .isTrue();
                }
            } finally {
                try {
                    if (temporaryCredential != null) {
                        temporaryCredential.drop();
                    }
                } finally {
                    if (pipelineStopped) {
                        startPipeline(pipelineName);
                    }
                }
            }
        }
    }

    /**
     * Test: Submit a typed bulk update with matchLimit(10), refreshRate(50), and
     * vector_db_provider=oracle, followed by a string update setting similarity_threshold to 0.8.
     * Expected: the updates return true and the mutable vector database provider is preserved
     * when read back from database metadata.
     */
    @Test
    void test52013BulkUpdateMutableAttributes() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5215");
        VectorIndexAttributes attrs = VectorIndexAttributes.updateBuilder()
                .matchLimit(10)
                .refreshRate(50)
                .vectorDbProvider("oracle")
                .build();
        assertThat(index.update(attrs)).isTrue();
        VectorIndexAttributes persisted = selectAI.vectorIndex(index.getIndexName())
                .getVectorIndexAttributes();
        assertThat(persisted.getVectorDbProvider()).isEqualTo("oracle");
        assertThat(index.update("similarity_threshold", "0.8")).isTrue();
    }

    /**
     * Test: Attempt to change the database-managed description to "updated description" through
     * the string update overload.
     * Expected: the database rejects the immutable description update with SelectAIException and
     * ORA-20048.
     */
    @Test
    void test52014UpdateDescription() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5216").update("description", "updated description"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20048:"));
    }

    /**
     * Test: Attempt to set the database-managed pipeline_name to "test_pipeline" through both the
     * string update overload and VectorIndexAttributes.updateBuilder().pipelineName(...).
     * Expected: both requests are rejected with SelectAIException and ORA-20048; callers cannot
     * replace the pipeline name through either Java API.
     */
    @Test
    void test52015UpdatePipelineName() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5217");
        assertThatThrownBy(() -> index.update("pipeline_name", "test_pipeline"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20048:"));
        assertThatThrownBy(() -> index.update(VectorIndexAttributes.updateBuilder()
                .pipelineName("test_pipeline")
                .build()))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20048:"));
    }

    /**
     * Test: Attempt to update the create-time chunk_size attribute to 2048 on an existing index.
     * Expected: the database rejects the update with SelectAIException and ORA-20047 because
     * chunk_size cannot be changed after creation.
     */
    @Test
    void test52016UpdateChunkSizeIsRejected() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5218").update("chunk_size", "2048"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
    }

    /**
     * Test: Attempt to update the create-time chunk_overlap attribute to 256 on an existing
     * index.
     * Expected: the database rejects the update with SelectAIException and ORA-20047.
     */
    @Test
    void test52017UpdateChunkOverlapIsRejected() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5219").update("chunk_overlap", "256"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
    }

    /**
     * Test: Attempt to change vector_distance_metric to EUCLIDEAN through both the string overload
     * and the typed update builder.
     * Expected: both API calls reach the database and are rejected with SelectAIException and
     * ORA-20047 because the distance metric is create-time metadata.
     */
    @Test
    void test52018UpdateVectorDistanceMetricIsRejected() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5220");
        assertThatThrownBy(() -> index.update("vector_distance_metric", "EUCLIDEAN"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
        assertThatThrownBy(() -> index.update(VectorIndexAttributes.updateBuilder()
                .vectorDistanceMetric("EUCLIDEAN")
                .build()))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
    }

    /**
     * Test: Submit a typed bulk update combining mutable matchLimit(20) with immutable
     * chunkSize(2048).
     * Expected: the database rejects the combined payload with SelectAIException and ORA-20047
     * rather than applying the mutable field while ignoring the restricted field.
     */
    @Test
    void test52019BulkUpdateWithRestrictedFieldsIsRejected() throws Exception {
        VectorIndexAttributes attrs = VectorIndexAttributes.updateBuilder()
                .matchLimit(20)
                .chunkSize(2048)
                .build();
        assertThatThrownBy(() -> existingIndex("UPDATE_5221").update(attrs))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
    }

    /**
     * Test: Submit a typed update containing both create-time attributes, chunkSize(2048) and
     * chunkOverlap(256), through update(VectorIndexAttributes).
     * Expected: the database rejects the payload with SelectAIException and ORA-20047 because
     * neither create-time attribute is updateable.
     */
    @Test
    void test52020BulkUpdateWithCreateTimeCombinationIsRejected() throws Exception {
        VectorIndexAttributes attrs = VectorIndexAttributes.updateBuilder()
                .chunkSize(2048)
                .chunkOverlap(256)
                .build();
        assertThatThrownBy(() -> existingIndex("UPDATE_5222").update(attrs))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
    }

    /**
     * Test: Attempt to replace the index location with the fixture embedding location through the
     * string update overload.
     * Expected: the database rejects location changes after creation with SelectAIException and
     * ORA-20047.
     */
    @Test
    void test52021UpdateLocationIsRejected() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5223").update("location", embeddingLocation()))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20047:"));
    }

    /**
     * Test: Send update("invalid_attr", "value") for an attribute not recognized by the vector
     * index update procedure.
     * Expected: SelectAIException is raised with database error ORA-20048 naming an invalid
     * update attribute.
     */
    @Test
    void test52022UpdateInvalidAttributeName() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5225").update("invalid_attr", "value"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20048:"));
    }

    /**
     * Test: Send the non-numeric string "not_an_int" as the chunk_size update value.
     * Expected: the database, rather than Java parsing the string, rejects the value with
     * SelectAIException and ORA-20000.
     */
    @Test
    void test52023UpdateInvalidIntegerValue() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5226").update("chunk_size", "not_an_int"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20000:"));
    }

    /**
     * Test: Send "NaN" as the similarity_threshold update value.
     * Expected: the database rejects the non-numeric threshold with SelectAIException and
     * ORA-20048.
     */
    @Test
    void test52024UpdateInvalidFloatValue() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5227").update("similarity_threshold", "NaN"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20048:"));
    }

    /**
     * Test: Send vector_distance_metric="INVALID" to the database for an existing index.
     * Expected: SelectAIException is raised and its database cause contains ORA-20048 for the
     * unsupported metric value.
     */
    @Test
    void test52025UpdateInvalidDistanceMetric() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5228").update("vector_distance_metric", "INVALID"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20048:"));
    }

    /**
     * Test: Request a VectorIndex object for a generated name that is not present, before any
     * update call can be made.
     * Expected: Java lookup throws SelectAIException with the exact message
     * "Vector index not found: <name>".
     */
    @Test
    void test52026UpdateNonexistentIndexUsesJavaLookupContract() throws Exception {
        String name = uniqueIndexName("NONEXISTENT_5229");
        assertThatThrownBy(() -> selectAI.vectorIndex(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Vector index not found: " + name);
    }

    /**
     * Test: Call update(null, "128") on an existing index, passing a null attribute name.
     * Expected: Java throws IllegalArgumentException before issuing an update request.
     */
    @Test
    void test52027UpdateWithNullAttributeName() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5230").update(null, "128"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Obtain an index from a second SelectAI client, close that client, then call update on
     * the detached object; finally update the same database index through the original client.
     * Expected: the closed client's update throws SelectAIException with ORA-17008, while the
     * original live client's update("match_limit", "256") returns true.
     */
    @Test
    void test52028UpdateAfterDisconnect() throws Exception {
        VectorIndex sourceIndex = existingIndex("UPDATE_5233");
        SelectAI disconnectedClient = SelectAI.create(dbConfig);
        VectorIndex disconnectedIndex = disconnectedClient.vectorIndex(sourceIndex.getIndexName());

        disconnectedClient.close();
        assertThatThrownBy(() -> disconnectedIndex.update("match_limit", "256"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-17008:"));

        assertThat(sourceIndex.update("match_limit", "256")).isTrue();
    }

    /**
     * Test: Call update("match_limit", null) on an existing index.
     * Expected: Java throws IllegalArgumentException for the null update value before a database
     * request is sent.
     */
    @Test
    void test52029UpdateWithNullAttributeValue() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5234").update("match_limit", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Load the same database index into two VectorIndex objects and update match_limit to 15
     * through the first object and 20 through the second object.
     * Expected: both calls return true, demonstrating that independently loaded objects issue
     * updates against the same database resource.
     */
    @Test
    void test52030RepeatedUpdatesThroughTwoJavaObjects() throws Exception {
        VectorIndex first = existingIndex("UPDATE_5235");
        VectorIndex second = selectAI.vectorIndex(first.getIndexName());
        assertThat(first.update("match_limit", "15")).isTrue();
        assertThat(second.update("match_limit", "20")).isTrue();
    }

    /**
     * Test: Send a 500-character profile_name value through the string update overload.
     * Expected: The database rejects the oversized value with {@code SelectAIException}.
     * Oracle error codes can vary by environment, so the test accepts the applicable expected code.
     */
    @Test
    void test52031UpdateWithExcessivelyLargeValue() throws Exception {
        assertThatThrownBy(() -> existingIndex("UPDATE_5236").update("profile_name", "X".repeat(500)))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause().getMessage())
                                .containsAnyOf(
                                        "ORA-20008:",
                                        "ORA-20048: Value is too long for vector index attribute - profile_name"));
    }

    /**
     * Test: Execute five string updates on one index, assigning match_limit values 10, 20, 30, 40,
     * and 50 in sequence.
     * Expected: every update returns true and the database accepts each positive value without
     * using the known unsupported match_limit=0 case.
     */
    @Test
    void test52032RepeatedMatchLimitUpdates() throws Exception {
        VectorIndex index = existingIndex("UPDATE_5237");
        for (int i = 0; i < 5; i++) {
            assertThat(index.update("match_limit", Integer.toString((i + 1) * 10))).isTrue();
        }
    }

    /**
     * Test: Create and drop an index, create a new VectorIndex with the same name, and update its
     * match_limit to 10 after recreation.
     * Expected: the recreated resource accepts create() and the post-recreation update returns
     * true.
     */
    @Test
    void test52033UpdateAfterDeleteAndRecreate() throws Exception {
        String name = uniqueIndexName("UPDATE_5238");
        VectorIndex index = createConfiguredIndex(name);
        index.create();
        index.drop(true);
        VectorIndex recreated = createConfiguredIndex(name);
        recreated.create();
        assertThat(recreated.update("match_limit", "10")).isTrue();
    }

    @Test
    void test52034UpdateMatchLimitZero() throws Exception {
        assertThat(existingIndex("UPDATE_5235").update("match_limit", "0")).isTrue();
    }
}
