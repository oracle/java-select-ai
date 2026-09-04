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

/** Integration coverage for EnableDisableVectorIndex. */
class EnableDisableVectorIndexIT extends VectorIndexIntegrationFixture {

    /**
     * Test: Load an enabled index, call disable(), and then call enable() on the same VectorIndex
     * object.
     * Expected: both lifecycle calls return true, restoring the database-backed index to enabled
     * state after the temporary disable.
     */
    @Test
    void test55000DisableAndEnable() throws Exception {
        VectorIndex index = existingIndex("ENABLE_5501");
        assertThat(index.disable()).isTrue();
        assertThat(index.getStatus())
                .isEqualToIgnoringCase(VectorIndexConfig.Status.DISABLED.getValue());
        assertThat(index.enable()).isTrue();
        assertThat(index.getStatus())
                .isEqualToIgnoringCase(VectorIndexConfig.Status.ENABLED.getValue());
    }

    /**
     * Test: Call disable() twice on an index that starts enabled.
     * Expected: the first call returns true; the second call throws SelectAIException with an
     * ORA-20000 database cause because the index is already disabled.
     */
    @Test
    void test55001DisableTwice() throws Exception {
        VectorIndex index = existingIndex("ENABLE_5502");
        assertThat(index.disable()).isTrue();
        assertThatThrownBy(index::disable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20000:"));
    }

    /**
     * Test: Disable an enabled index, call enable(), and call enable() again without another
     * disable.
     * Expected: the first enable returns true; the second enable throws SelectAIException with an
     * ORA-20000 cause because the index is already enabled.
     */
    @Test
    void test55002EnableTwice() throws Exception {
        VectorIndex index = existingIndex("ENABLE_5503");
        assertThat(index.disable()).isTrue();
        assertThat(index.enable()).isTrue();
        assertThatThrownBy(index::enable)
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20000:"));
    }

    /**
     * Test: Set the profile's vector_index_name attribute to an enabled index and execute the
     * natural-language runsql prompt "How many rows in test_items".
     * Expected: setAttribute returns true and Profile.runsql() returns a non-blank response while
     * the enabled vector index is available to the profile.
     */
    @Test
    void test55003EnabledIndexAllowsProfileQuery() throws Exception {
        VectorIndex index = existingIndex("ENABLE_5504");
        assertThat(profile.setAttribute("vector_index_name", index.getIndexName())).isTrue();
        String response = profile.runsql("How many rows in test_items");
        assertThat(response).isNotBlank();
    }

    /**
     * Test: Disable an index, set the profile's vector_index_name to that index, and execute
     * Profile.runsql("Show all rows from test_items").
     * Expected: the database rejects the query with SelectAIException whose cause contains
     * ORA-20000 because the configured vector index is disabled.
     */
    @Test
    void test55004DisabledIndexBlocksProfileQuery() throws Exception {
        VectorIndex index = existingIndex("ENABLE_5505");
        index.disable();
        assertThat(profile.setAttribute("vector_index_name", index.getIndexName())).isTrue();
        assertThatThrownBy(() -> profile.runsql("Show all rows from test_items"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20000:"));
    }

    /**
     * Test: Create an enabled index, read its status through one object, disable it through a
     * second object, and read getStatus() again through the first object.
     * Expected: the first read is Enabled and the later read reflects the database's Disabled
     * state, proving getStatus() refreshes an existing database-backed object.
     */
    @Test
    void test55005VectorIndexGetStatusRefreshesExistingObjectAfterExternalDisable() throws Exception {
        String name = uniqueIndexName("STATUS_REFRESH");
        VectorIndex configured = createConfiguredIndex(name);
        assertThat(configured.create()).isTrue();

        VectorIndex staleIndex = selectAI.vectorIndex(name);
        assertThat(staleIndex.getStatus())
                .isEqualToIgnoringCase(VectorIndexConfig.Status.ENABLED.getValue());

        VectorIndex updater = selectAI.vectorIndex(name);
        assertThat(updater.disable()).isTrue();

        assertThat(staleIndex.getStatus())
                .isEqualToIgnoringCase(VectorIndexConfig.Status.DISABLED.getValue());
    }

    /**
     * Test: Create an index, load an observer object, update match_limit to 10 through another
     * object, and call getVectorIndexAttributes() on the observer.
     * Expected: the observer reports match_limit=10 from refreshed database metadata rather than a
     * stale value.
     */
    @Test
    void test55006VectorIndexGetAttributesRefreshesExistingObjectAfterExternalUpdate() throws Exception {
        String name = uniqueIndexName("ATTR_REFRESH");
        VectorIndex updater = createConfiguredIndex(name);
        assertThat(updater.create()).isTrue();

        VectorIndex observer = selectAI.vectorIndex(name);
        assertThat(updater.update("match_limit", "10")).isTrue();

        assertThat(observer.getVectorIndexAttributes().getMatchLimit()).isEqualTo(10);
    }

    /**
     * Test: Create an enabled index, obtain its published pipeline status-table name from
     * USER_CLOUD_PIPELINES, and execute SELECT COUNT(*) against that table.
     * Expected: when the database publishes a status table, the query returns a row and a count
     * greater than or equal to zero; otherwise the test is skipped with the explicit prerequisite
     * assumption.
     */
    @Test
    void test55007EnabledIndexPipelineMetadata() throws Exception {
        VectorIndex index = existingIndex("ENABLE_5510");
        String statusTable = pipelineStatusTable(index.getIndexName());
        assumeTrue(statusTable != null, "The database has not published a pipeline status table yet.");
        try (var statement = jdbcConnection().createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + sqlIdentifier(statusTable))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isGreaterThanOrEqualTo(0);
        }
    }

    /**
     * Test: Build VectorIndexConfig with the generated name, standard attributes, status ENABLED,
     * description "Java-specific description", and waitForCompletion(true), then read its typed
     * getters without creating the index.
     * Expected: getters return the supplied name, profile name, true wait flag, and Java status
     * value "Enabled" exactly as stored in the config object.
     */
    @Test
    void test55008JavaSpecificTypedConfigExposesCreateMetadata() throws Exception {
        String name = uniqueIndexName("JAVA_CONFIG");
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(createAttributes())
                .status(VectorIndexConfig.Status.ENABLED)
                .description("Java-specific description")
                .waitForCompletion(true)
                .build();
        assertThat(config.getIndexName()).isEqualTo(name);
        assertThat(config.getVectorIndexAttributes().getProfileName()).isEqualTo(profileName);
        assertThat(config.isWaitForCompletion()).isTrue();
        assertThat(config.getStatusValue()).isEqualTo("Enabled");
    }

    /**
     * Test: Read isWaitForCompletion() from a configured index before create(), create it, and read
     * the same property from a newly loaded database-backed index.
     * Expected: the pending configured object returns true, while the loaded object returns null
     * because the wait option belongs to the Java create configuration and is not database metadata.
     */
    @Test
    void test55009JavaSpecificConfiguredIndexReportsWaitForCompletionAndLoadedIndexDoesNot() throws Exception {
        String name = uniqueIndexName("WAIT_OPTION");
        VectorIndex configured = createConfiguredIndex(name, "Wait option test");

        assertThat(configured.isWaitForCompletion()).isTrue();
        assertThat(configured.create()).isTrue();

        VectorIndex loaded = selectAI.vectorIndex(name);
        assertThat(loaded.isWaitForCompletion()).isNull();
    }

    /**
     * Test: Create a SelectAI client with queryTimeoutSeconds=10, build an asynchronous vector
     * index with waitForCompletion(false), and call create() through that client.
     * Expected: create() returns true before the ten-second JDBC timeout and the client closes at
     * the end of the try-with-resources block.
     */
    @Test
    void test55010JavaSpecificAsyncVectorIndexCreateCompletesWithinTimeout() throws Exception {
        String name = uniqueIndexName("TIMEOUT_ASYNC");
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(10)
                .build();

        try (SelectAI timeoutClient = SelectAI.create(dbConfig, options)) {
            VectorIndex configured = createConfiguredIndex(
                    timeoutClient, name, "Asynchronous timeout test", false);
            assertThat(configured.create()).isTrue();
        }
    }

    /**
     * Test: Create a SelectAI client with queryTimeoutSeconds=1, build a synchronous index with
     * waitForCompletion(true), and capture the throwable from create().
     * Expected: creation is cancelled by the JDBC timeout and throws SelectAIException whose
     * database cause contains ORA-01013; the cause is SQLException and its text identifies timeout,
     * cancellation, or error 01013.
     */
    @Test
    void test55011JavaSpecificSynchronousVectorIndexCreateHonorsTimeout() throws Exception {
        String name = uniqueIndexName("TIMEOUT_SYNC");
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(1)
                .build();

        try (SelectAI timeoutClient = SelectAI.create(dbConfig, options)) {
            VectorIndex configured = createConfiguredIndex(
                    timeoutClient, name, "Synchronous timeout test", true);
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(configured::create);

            assertThat(failure).isInstanceOfSatisfying(SelectAIException.class,
                    exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-01013:"));
            assertThat(failure.getCause()).isInstanceOf(SQLException.class);
            assertThat(String.valueOf(failure.getCause().getMessage()).toLowerCase(Locale.ROOT))
                    .containsAnyOf("timeout", "cancel", "01013");
        }
    }

    /**
     * Test: Build a complete index configuration whose profile_name is a generated name that has
     * not been created, then call create().
     * Expected: the database rejects the missing profile reference with SelectAIException and an
     * ORA-20046 cause.
     */
    @Test
    void test55012JavaSpecificCreateWithNonexistentProfile() throws Exception {
        String name = uniqueIndexName("MISSING_PROFILE");
        String missingProfileName = uniqueName("MISSING_PROFILE");
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location(embeddingLocation())
                .matchLimit(1)
                .objectStorageCredentialName(objectStorageCredentialName)
                .profileName(missingProfileName)
                .vectorDbProvider("oracle")
                .build();
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(attributes)
                .status(VectorIndexConfig.Status.ENABLED)
                .description("Missing profile")
                .waitForCompletion(true)
                .build();

        assertThatThrownBy(() -> selectAI.vectorIndex(config).create())
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20046:"));
    }

    /**
     * Test: Create an enabled index with all supported attributes, including a decimal
     * similarity_threshold.
     * Expected: create() returns true and every configured attribute is preserved when the index
     * is loaded again; the database derives pipeline_name as <INDEX_NAME>$VECPIPELINE.
     */
    @Test
    void test55013JavaSpecificCreateWithAllSupportedAttributes() throws Exception {
        String name = uniqueIndexName("ALL_ATTRS");
        String pipelineName = name + "$VECPIPELINE";
        String vectorTableName = name + "_TABLE";
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .chunkSize(1024)
                .chunkOverlap(128)
                .location(embeddingLocation())
                .matchLimit(5)
                .objectStorageCredentialName(objectStorageCredentialName)
                .profileName(profileName)
                .refreshRate(1440)
                .similarityThreshold(0.7)
                .vectorDistanceMetric("COSINE")
                .vectorDbProvider("oracle")
                .vectorDimension(1024)
                .vectorTableName(vectorTableName)
                .build();
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(attributes)
                .status(VectorIndexConfig.Status.ENABLED)
                .description("All vector index attributes")
                .waitForCompletion(true)
                .build();

        assertThat(selectAI.vectorIndex(config).create()).isTrue();
        assertIndexPresent(name);
        VectorIndexAttributes persisted = selectAI.vectorIndex(name).getVectorIndexAttributes();
        assertThat(persisted.getChunkSize()).isEqualTo(1024);
        assertThat(persisted.getChunkOverlap()).isEqualTo(128);
        assertThat(persisted.getLocation()).isEqualTo(embeddingLocation());
        assertThat(persisted.getMatchLimit()).isEqualTo(5);
        assertThat(persisted.getObjectStorageCredentialName()).isEqualTo(objectStorageCredentialName);
        assertThat(persisted.getPipelineName()).isEqualTo(pipelineName);
        assertThat(persisted.getProfileName()).isEqualTo(profileName);
        assertThat(persisted.getRefreshRate()).isEqualTo(1440);
        assertThat(persisted.getSimilarityThreshold()).isEqualTo(0.7);
        assertThat(persisted.getVectorDistanceMetric()).isEqualTo("COSINE");
        assertThat(persisted.getVectorDbProvider()).isEqualTo("oracle");
        assertThat(persisted.getVectorDimension()).isEqualTo(1024);
        assertThat(persisted.getVectorTableName()).isEqualTo(vectorTableName);
    }

    /**
     * Test: Pass a caller-supplied pipeline_name to VectorIndexAttributes.builder().
     * Expected: the Java builder throws IllegalArgumentException because pipeline_name is
     * database-managed and cannot be supplied as a create attribute.
     */
    @Test
    void test55014JavaSpecificCreateRejectsPipelineName() {
        assertThatThrownBy(() -> VectorIndexAttributes.builder()
                .pipelineName(uniqueName("PIPELINE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Call the CLOB-capable update overload update("profile_name", profileName, true) on an
     * existing index.
     * Expected: the overload sends the profile name with the CLOB flag and returns true.
     */
    @Test
    void test55015JavaSpecificClobUpdateOverload() throws Exception {
        VectorIndex index = existingIndex("JAVA_CLOB");
        assertThat(index.update("profile_name", profileName, true)).isTrue();
    }

    /**
     * Test: Create an index with description "Java list description", retrieve it through an exact
     * listVectorIndexes() pattern, and read getIndexName() and getDescription().
     * Expected: the returned object has the generated name and exactly the supplied description,
     * demonstrating that list hydration preserves both fields.
     */
    @Test
    void test55016JavaSpecificListReturnsNameAndDescription() throws Exception {
        String name = uniqueIndexName("JAVA_LIST");
        assertThat(createForIntegration(createConfiguredIndex(name, "Java list description")))
                .isTrue();
        VectorIndex listed = selectAI.listVectorIndexes("^" + name + "$").stream()
                .findFirst().orElseThrow();
        assertThat(listed.getIndexName()).isEqualTo(name);
        assertThat(listed.getDescription()).isEqualTo("Java list description");
    }
}
