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
import com.oracle.database.selectai.integration.IntegrationTestFixture;
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

/** Integration coverage for GetVectorIndexAttributes. */
class GetVectorIndexAttributesIT extends VectorIndexIntegrationFixture {

    /**
     * Test: Create an index and call getVectorIndexAttributes() on the created VectorIndex.
     * Expected: the getter returns a non-null VectorIndexAttributes object populated from the
     * database metadata.
     */
    @Test
    void test53000GetVectorIndexAttributes() throws Exception {
        VectorIndex index = existingIndex("GETATTR_5301");

        assertThat(index.getVectorIndexAttributes()).isNotNull();
    }

    /**
     * Test: Create an index, retrieve a fresh object by name, and read location, object-storage
     * credential name, profile name, and the generated pipeline name from its attributes.
     * Expected: location equals the configured embedding location, credential and profile equal
     * the isolated fixture values, and pipeline_name equals <INDEX_NAME>$VECPIPELINE.
     */
    @Test
    void test53001GetVectorIndexCoreAttributes() throws Exception {
        VectorIndex index = existingIndex("GETATTR_5302");
        VectorIndex loaded = selectAI.vectorIndex(index.getIndexName());
        VectorIndexAttributes attributes = loaded.getVectorIndexAttributes();

        assertThat(attributes.getLocation()).isEqualTo(embeddingLocation());
        assertThat(attributes.getObjectStorageCredentialName()).isEqualTo(objectStorageCredentialName);
        assertThat(attributes.getProfileName()).isEqualTo(profileName);
        assertThat(attributes.getPipelineName())
                .isEqualTo(index.getIndexName().toUpperCase(Locale.ROOT) + "$VECPIPELINE");
    }

    /**
     * Test: Create an index with only the standard required attributes, then read its optional
     * fields: chunk size, chunk overlap, match limit, refresh rate, distance metric, and vector
     * database provider.
     * Expected: chunk_size, chunk_overlap, and vector_distance_metric are null; match_limit is 1,
     * refresh_rate is 1440, and vector_db_provider is "oracle". The test also records the current
     * Java model limitation that database decimal similarity_threshold is not asserted here.
     */
    @Test
    void test53002GetVectorIndexOptionalAttributes() throws Exception {
        VectorIndex index = existingIndex("GETATTR_5303");
        VectorIndexAttributes attributes = selectAI.vectorIndex(index.getIndexName())
                .getVectorIndexAttributes();

        // The current Java model exposes similarity_threshold as Integer, while
        // the database can return a decimal value such as 0.5.
        // Keep the remaining optional fields covered until that Java type is aligned.
        assertThat(attributes.getChunkSize()).isNull();
        assertThat(attributes.getChunkOverlap()).isNull();
        assertThat(attributes.getMatchLimit()).isEqualTo(1);
        assertThat(attributes.getRefreshRate()).isEqualTo(1440);
        assertThat(attributes.getVectorDistanceMetric()).isNull();
        assertThat(attributes.getVectorDbProvider()).isEqualTo("oracle");
    }

    /**
     * Test: Create an index and retrieve its attributes through a new VectorIndex object.
     * Expected: the database-provided location and object-storage credential name are both
     * non-blank strings.
     */
    @Test
    void test53003GetVectorIndexRequiredAttributes() throws Exception {
        VectorIndex index = existingIndex("GETATTR_5304");
        VectorIndexAttributes attributes = selectAI.vectorIndex(index.getIndexName())
                .getVectorIndexAttributes();

        assertThat(attributes.getLocation()).isNotBlank();
        assertThat(attributes.getObjectStorageCredentialName()).isNotBlank();
    }

    /**
     * Test: Retrieve the same index's attributes twice through separate SelectAI.vectorIndex(name)
     * lookups and serialize both results with toJson().
     * Expected: both JSON payloads are identical, so repeated metadata retrieval does not change
     * the returned attribute values.
     */
    @Test
    void test53004GetVectorIndexAttributesIsRepeatable() throws Exception {
        VectorIndex index = existingIndex("GETATTR_5305");
        VectorIndexAttributes first = selectAI.vectorIndex(index.getIndexName())
                .getVectorIndexAttributes();
        VectorIndexAttributes second = selectAI.vectorIndex(index.getIndexName())
                .getVectorIndexAttributes();

        assertThat(second.toJson()).isEqualTo(first.toJson());
    }

    /**
     * Test: Create an index with its generated uppercase name, request it using a lowercase name,
     * and read its location attribute.
     * Expected: database lookup accepts the case variation and returns the configured embedding
     * location.
     */
    @Test
    void test53005GetVectorIndexAttributesWithCaseInsensitiveName() throws Exception {
        VectorIndex index = existingIndex("GETATTR_5306");
        VectorIndex loaded = selectAI.vectorIndex(index.getIndexName().toLowerCase(Locale.ROOT));

        assertThat(loaded.getVectorIndexAttributes().getLocation()).isEqualTo(embeddingLocation());
    }

    /**
     * Test: Retrieve the attributes of a created index and inspect the runtime types of location,
     * profile_name, and object_storage_credential_name.
     * Expected: each of these database-backed values is represented by a Java String.
     */
    @Test
    void test53006GetVectorIndexAttributeTypes() throws Exception {
        VectorIndex index = existingIndex("GETATTR_5307");
        VectorIndexAttributes attributes = selectAI.vectorIndex(index.getIndexName())
                .getVectorIndexAttributes();

        assertThat(attributes.getLocation()).isInstanceOf(String.class);
        assertThat(attributes.getProfileName()).isInstanceOf(String.class);
        assertThat(attributes.getObjectStorageCredentialName()).isInstanceOf(String.class);
    }

    /**
     * Test: Read a created index's VectorIndexAttributes, drop the index, and then inspect the
     * previously returned attribute object.
     * Expected: the already returned object retains the location and object-storage credential
     * values it contained before deletion, even though the database resource is gone.
     */
    @Test
    void test53007AttributesRemainCachedAfterDelete() throws Exception {
        String name = uniqueIndexName("GETATTR_5310");
        VectorIndex configured = createConfiguredIndex(name);
        assertThat(configured.create()).isTrue();
        VectorIndex loaded = selectAI.vectorIndex(name);
        VectorIndexAttributes attributes = loaded.getVectorIndexAttributes();

        assertThat(loaded.drop(true)).isTrue();
        assertThat(attributes.getLocation()).isEqualTo(embeddingLocation());
        assertThat(attributes.getObjectStorageCredentialName()).isEqualTo(objectStorageCredentialName);
    }

    /**
     * Test: Create an index, drop it with includeData=true, and request a new VectorIndex object
     * for the deleted name.
     * Expected: Java lookup throws SelectAIException with the exact message
     * "Vector index not found: <name>".
     */
    @Test
    void test53008DeletedIndexUsesJavaLookupContract() throws Exception {
        String name = uniqueIndexName("GETATTR_DELETE_5309");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();
        assertThat(index.drop(true)).isTrue();
        assertThatThrownBy(() -> selectAI.vectorIndex(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Vector index not found: " + name);
    }

    /**
     * Test: Load the same created index into two VectorIndex objects, drop it through the first
     * object, and call getStatus() through the second object.
     * Expected: the second database-backed reference refreshes metadata and throws SelectAIException
     * with "Vector index not found: <name>" rather than returning stale status.
     */
    @Test
    void test53009VectorIndexDatabaseOperationFailsOnSecondReferenceAfterFirstDrops() throws Exception {
        String name = uniqueIndexName("SHARED_DROP");
        VectorIndex first = createConfiguredIndex(name);
        assertThat(first.create()).isTrue();
        VectorIndex second = selectAI.vectorIndex(name);

        assertThat(first.drop(true)).isTrue();

        assertThatThrownBy(second::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Vector index not found: " + name);
    }

    /**
     * Test: Call SelectAI.vectorIndex("") with an empty index name.
     * Expected: Java throws IllegalArgumentException before attempting database lookup.
     */
    @Test
    void test53010EmptyNameUsesJavaArgumentContract() throws Exception {
        assertThatThrownBy(() -> selectAI.vectorIndex(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Call SelectAI.vectorIndex((String) null) with no index name.
     * Expected: Java throws IllegalArgumentException before attempting database lookup.
     */
    @Test
    void test53011NullNameUsesJavaArgumentContract() throws Exception {
        assertThatThrownBy(() -> selectAI.vectorIndex((String) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Request a vector index named "@@invalid!!", which is not a created database resource.
     * Expected: Java lookup throws SelectAIException with the exact not-found message containing
     * the supplied special-character name.
     */
    @Test
    void test53012SpecialCharacterNameUsesJavaLookupContract() throws Exception {
        assertThatThrownBy(() -> selectAI.vectorIndex("@@invalid!!"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Vector index not found: @@invalid!!");
    }

    /**
     * Test: Request a vector index named "テスト", which is not present in the database.
     * Expected: Java lookup throws SelectAIException with the exact message
     * "Vector index not found: テスト".
     */
    @Test
    void test53013UnicodeNameUsesJavaLookupContract() throws Exception {
        assertThatThrownBy(() -> selectAI.vectorIndex("テスト"))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Vector index not found: テスト");
    }

    /**
     * Test: Request a non-existent vector index whose name contains 100 X characters.
     * Expected: Java lookup throws SelectAIException and preserves the complete supplied name in
     * the exact "Vector index not found" message.
     */
    @Test
    void test53014LongNameUsesJavaLookupContract() throws Exception {
        String name = "X".repeat(100);
        assertThatThrownBy(() -> selectAI.vectorIndex(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Vector index not found: " + name);
    }

    /**
     * Test: Create two indexes with different names and retrieve each generated pipeline_name
     * through getVectorIndexAttributes().
     * Expected: the two pipeline names are different, so each index has an independent database
     * pipeline resource.
     */
    @Test
    void test53015MultipleIndexesHaveDistinctPipelineNames() throws Exception {
        String firstName = uniqueIndexName("GETATTR_5315_A");
        String secondName = uniqueIndexName("GETATTR_5315_B");
        createConfiguredIndex(firstName).create();
        createConfiguredIndex(secondName).create();

        String firstPipeline = selectAI.vectorIndex(firstName)
                .getVectorIndexAttributes().getPipelineName();
        String secondPipeline = selectAI.vectorIndex(secondName)
                .getVectorIndexAttributes().getPipelineName();

        assertThat(firstPipeline).isNotEqualTo(secondPipeline);
    }

    /**
     * Test: Create an index, drop it, create the same name again with the standard attributes, and
     * read location and object-storage credential from the recreated database resource.
     * Expected: the recreated index contains the configured embedding location and fixture
     * credential rather than stale metadata from the deleted resource.
     */
    @Test
    void test53016AttributesSurviveDeleteAndRecreate() throws Exception {
        String name = uniqueIndexName("GETATTR_5316");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();
        assertThat(index.drop(true)).isTrue();

        VectorIndex recreated = createConfiguredIndex(name);
        assertThat(recreated.create()).isTrue();
        VectorIndexAttributes attributes = selectAI.vectorIndex(name).getVectorIndexAttributes();

        assertThat(attributes.getObjectStorageCredentialName()).isEqualTo(objectStorageCredentialName);
        assertThat(attributes.getLocation()).isEqualTo(embeddingLocation());
    }

    /**
     * Test: Close a SelectAI client before using it to look up an existing index, then create a
     * fresh client and retrieve the same index's attributes.
     * Expected: the closed client reports SelectAIException with ORA-17008, while the fresh client
     * retrieves the index and its location getter equals the configured embedding location.
     */
    @Test
    void test53017GetAttributesAfterDisconnect() throws Exception {
        VectorIndex sourceIndex = existingIndex("GETATTR_5318");
        String indexName = sourceIndex.getIndexName();

        try (SelectAI disconnectedClient = SelectAI.create(dbConfig)) {
            disconnectedClient.close();
            assertThatThrownBy(() -> disconnectedClient.vectorIndex(indexName))
                    .isInstanceOfSatisfying(SelectAIException.class,
                            exception -> assertThat(exception.getCause())
                                    .hasMessageContaining("ORA-17008:"));
        }

        try (SelectAI reconnectedClient = SelectAI.create(dbConfig)) {
            VectorIndex reconnectedIndex = reconnectedClient.vectorIndex(indexName);
            assertThat(reconnectedIndex.getVectorIndexAttributes().getLocation())
                    .isEqualTo(embeddingLocation());
        }
    }
}
