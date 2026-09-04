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

abstract class VectorIndexIntegrationFixture extends IntegrationTestFixture {

    protected final Set<String> managedIndexNames = new HashSet<>();
    protected Credential objectStorageCredential;
    protected String objectStorageCredentialName;
    protected String listPrefix;
    protected String listVectorPrefix;

    @Override
    protected String profileObjectList() {
        return null;
    }

    @BeforeEach
    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        objectStorageCredentialName = uniqueCredentialName("OBJSTORE_CRED");
        objectStorageCredential = selectAI.credential(
                ociSigningKeyCredentialConfig(objectStorageCredentialName));
        objectStorageCredential.create();
    }

    @AfterEach
    void cleanVectorIndexResources() throws Exception {
        for (String name : new ArrayList<>(managedIndexNames)) {
            try {
                selectAI.vectorIndex(name).drop(true);
            } catch (Exception ignored) {
                // A test may already have removed the index; cleanup is best effort.
            }
        }
        managedIndexNames.clear();
        if (objectStorageCredential != null) {
            try {
                objectStorageCredential.drop();
            } catch (Exception ignored) {
                // Preserve the original test failure when asynchronous DB cleanup lags.
            }
        }
    }


    protected static Stream<String> requiredCreateAttributeNames() {
        return Stream.of(
                "location",
                "object_storage_credential_name",
                "profile_name");
    }


    protected String embeddingLocation() {
        return requiredFeatureValue(
                "SELECT_AI_IT_VECTOR_INDEX_EMBEDDING_LOCATION",
                "Vector-index integration tests require the embedding_location parameter");
    }

    protected CredentialConfig ociSigningKeyCredentialConfig(String credentialName) {
        return CredentialConfig.builder(credentialName)
                .userOcid(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_USER_OCID",
                        "Vector-index integration tests require the OCI user OCID"))
                .tenancyOcid(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_TENANCY_OCID",
                        "Vector-index integration tests require the OCI tenancy OCID"))
                .privateKey(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_PRIVATE_KEY",
                        "Vector-index integration tests require the OCI private key"))
                .fingerprint(requiredFeatureValue(
                        "SELECT_AI_IT_OCI_FINGERPRINT",
                        "Vector-index integration tests require the OCI key fingerprint"))
                .build();
    }

    protected VectorIndex existingIndex(String stem) throws Exception {
        String name = uniqueIndexName(stem);
        VectorIndex index = createConfiguredIndex(name);
        assertThat(createForIntegration(index)).isTrue();
        return index;
    }

    protected boolean createForIntegration(VectorIndex index) throws Exception {
        try {
            return index.create();
        } catch (SelectAIException exception) {
            String details = exception.getMessage() + " "
                    + (exception.getCause() == null ? "" : exception.getCause().getMessage());
            boolean embeddingServiceUnavailable = exception.getErrorCode() != null
                    && (exception.getErrorCode() == 20429
                    || (exception.getErrorCode() == 20000
                    && details.contains("files failed to load")));
            assumeTrue(!embeddingServiceUnavailable,
                    "Vector-index embedding service prerequisite unavailable: " + details);
            throw exception;
        }
    }

    protected VectorIndex createConfiguredIndex(String name) throws Exception {
        return createConfiguredIndex(name, "Test vector index");
    }

    protected VectorIndex createConfiguredIndex(String name, String description) throws Exception {
        return createConfiguredIndex(selectAI, name, description, true);
    }

    protected VectorIndex createConfiguredIndex(SelectAI client, String name,
                                              String description, boolean waitForCompletion)
            throws Exception {
        managedIndexNames.add(name);
        VectorIndexAttributes attributes = createAttributes();
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(attributes)
                .status(VectorIndexConfig.Status.ENABLED)
                .description(description)
                .waitForCompletion(waitForCompletion)
                .build();
        return client.vectorIndex(config);
    }

    protected VectorIndex createConfiguredIndexWithCredential(String name, String credentialName) throws Exception {
        managedIndexNames.add(name);
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location(embeddingLocation())
                .matchLimit(1)
                .objectStorageCredentialName(credentialName)
                .profileName(profileName)
                .vectorDbProvider("oracle")
                .build();
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(attributes)
                .status(VectorIndexConfig.Status.ENABLED)
                .description("Test vector index")
                .waitForCompletion(true)
                .build();
        return selectAI.vectorIndex(config);
    }

    protected VectorIndex createConfiguredIndexWithLocation(String name, String location) throws Exception {
        managedIndexNames.add(name);
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location(location)
                .matchLimit(1)
                .objectStorageCredentialName(objectStorageCredentialName)
                .profileName(profileName)
                .vectorDbProvider("oracle")
                .build();
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(attributes)
                .status(VectorIndexConfig.Status.ENABLED)
                .description("Test vector index")
                .waitForCompletion(true)
                .build();
        return selectAI.vectorIndex(config);
    }

    protected VectorIndexAttributes createAttributes() {
        return VectorIndexAttributes.builder()
                .location(embeddingLocation())
                .matchLimit(1)
                .objectStorageCredentialName(objectStorageCredentialName)
                .profileName(profileName)
                .vectorDbProvider("oracle")
                .build();
    }

    protected VectorIndexAttributes requiredCreateAttributes() {
        return VectorIndexAttributes.builder()
                .location(embeddingLocation())
                .objectStorageCredentialName(objectStorageCredentialName)
                .profileName(profileName)
                .vectorDbProvider("oracle")
                .build();
    }

    protected String uniqueIndexName(String stem) {
        String name = uniqueName(stem);
        managedIndexNames.add(name);
        return name;
    }

    protected String uniqueName(String stem) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return ("JSAI_VI_" + stem + "_" + suffix).replaceAll("[^A-Za-z0-9_$#]", "_").toUpperCase();
    }

    protected String uniqueCredentialName(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return (prefix + "_JSAI_" + suffix).replaceAll("[^A-Za-z0-9_$#]", "_").toUpperCase();
    }

    protected void assertIndexPresent(String name) throws Exception {
        assertThat(listNames("^" + name + "$"))
                .contains(name.toUpperCase(Locale.ROOT));
    }

    protected void assertIndexAbsent(String name) throws Exception {
        assertThat(listNames("^" + name + "$"))
                .doesNotContain(name.toUpperCase(Locale.ROOT));
    }

    protected List<String> listNames(String pattern) throws Exception {
        return selectAI.listVectorIndexes(pattern).stream()
                .map(VectorIndex::getIndexName)
                .map(String::toUpperCase)
                .toList();
    }

    protected List<String> createListFixture() throws Exception {
        listPrefix = uniqueName("LIST");
        listVectorPrefix = uniqueName("VECIDX");
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String name = listPrefix + "_" + i;
            names.add(name);
            createForIntegration(createConfiguredIndex(name));
        }
        String extra = listVectorPrefix + "_6";
        names.add(extra);
        createForIntegration(createConfiguredIndex(extra));
        return names;
    }

    protected String currentPipelineName(VectorIndex index) throws SelectAIException {
        String pipelineName = selectAI.vectorIndex(index.getIndexName())
                .getVectorIndexAttributes().getPipelineName();
        assumeTrue(pipelineName != null && !pipelineName.isBlank(),
                "The database did not publish a pipeline name for " + index.getIndexName());
        return pipelineName;
    }

    protected void stopPipeline(String pipelineName) throws SQLException {
        executePipelineProcedure("stop_pipeline", pipelineName);
    }

    protected void startPipeline(String pipelineName) throws SQLException {
        executePipelineProcedure("start_pipeline", pipelineName);
    }

    protected void executePipelineProcedure(String procedure, String pipelineName) throws SQLException {
        String sql = "BEGIN DBMS_CLOUD_PIPELINE." + procedure + "(pipeline_name => ?); END;";
        try (CallableStatement statement = jdbcConnection().prepareCall(sql)) {
            statement.setString(1, pipelineName);
            statement.execute();
        }
    }

    protected String pipelineStatusTable(String indexName) throws SQLException {
        String pipelineName = indexName.toUpperCase() + "$VECPIPELINE";
        try (var statement = jdbcConnection().prepareStatement(
                "SELECT status_table FROM user_cloud_pipelines WHERE pipeline_name = ?")) {
            statement.setString(1, pipelineName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    protected String sqlIdentifier(String value) {
        assumeTrue(value != null && value.matches("[A-Za-z][A-Za-z0-9_$#]*"),
                "Database returned an unsafe pipeline status-table identifier");
        return value;
    }

    protected boolean userTableExists(String tableName) throws SQLException {
        try (var statement = jdbcConnection().prepareStatement(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = ?")) {
            statement.setString(1, tableName.toUpperCase());
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    protected void dropUserTableIfExists(String tableName) throws SQLException {
        if (!userTableExists(tableName)) {
            return;
        }
        try (var statement = jdbcConnection().createStatement()) {
            statement.execute("DROP TABLE " + sqlIdentifier(tableName) + " PURGE");
        }
    }
}
