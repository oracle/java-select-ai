/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Reader;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultVectorIndexTest {

    @Mock
    private DbConnection dbConnection;
    @Mock
    private Connection connection;
    @Mock
    private CallableStatement callableStatement;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;
    @Mock
    private PreparedStatement vectorAttributesPreparedStatement;
    @Mock
    private ResultSet vectorAttributesResultSet;

    /**
     * Test: Reject missing connection providers for vector-index construction.
     * Expected: Both constructor paths fail before any vector-index work begins.
     */
    @Test
    void constructorsRejectNullConnectionProvider() {
        assertThatThrownBy(() -> new DefaultVectorIndex(null, vectorIndexConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionProvider");
        assertThatThrownBy(() -> new DefaultVectorIndex(null, "RAG_IDX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionProvider");
    }

    /**
     * Test: Verifies create binds config payload.
     * Expected: Creation returns true and binds RAG_IDX, the configured location/profile/
     * credential attributes, Disabled status, Product docs, and waitForCompletion=0.
     */
    @Test
    void createBindsConfigPayload() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_VECTOR_INDEX.get())).thenReturn(callableStatement);
        DefaultVectorIndex index = new DefaultVectorIndex(new SingleConnectionProvider(dbConnection), vectorIndexConfig());

        assertThat(index.create()).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        ArgumentCaptor<Reader> attributesCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), attributesCaptor.capture(), anyInt());
        assertThat(readAll(attributesCaptor.getValue()))
                .contains("\"location\":\"https://object.example/docs\"")
                .contains("\"profile_name\":\"RAG_PROFILE\"")
                .contains("\"object_storage_credential_name\":\"OBJ_CRED\"");
        verify(callableStatement).setString(3, "Disabled");
        ArgumentCaptor<Reader> descriptionCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(4), descriptionCaptor.capture(), eq("Product docs".length()));
        assertThat(readAll(descriptionCaptor.getValue())).isEqualTo("Product docs");
        verify(callableStatement).setInt(5, 0);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies CREATE_VECTOR_INDEX preserves oversized attributes and description CLOBs.
     * Expected: Both CLOB inputs are passed as character streams with their complete payloads.
     */
    @Test
    void createBindsLargeAttributesAndDescriptionAsCharacterStreams() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_VECTOR_INDEX.get())).thenReturn(callableStatement);
        String location = "https://example.com/" + "l".repeat(40_000);
        String description = "vector-description:" + "d".repeat(40_000);
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location(location)
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build();
        VectorIndexConfig config = VectorIndexConfig.builder("RAG_IDX_LARGE_CLOB")
                .description(description)
                .vectorIndexAttributes(attributes)
                .build();
        DefaultVectorIndex index = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), config);

        assertThat(index.create()).isTrue();

        ArgumentCaptor<Reader> attributesReader = ArgumentCaptor.forClass(Reader.class);
        ArgumentCaptor<Reader> descriptionReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), attributesReader.capture(),
                eq(attributes.toJson().length()));
        verify(callableStatement).setCharacterStream(eq(4), descriptionReader.capture(),
                eq(description.length()));
        assertThat(readAll(attributesReader.getValue())).isEqualTo(attributes.toJson());
        assertThat(readAll(descriptionReader.getValue())).isEqualTo(description);
    }

    /**
     * Test: Verifies create binds a null description as a CLOB SQL NULL.
     * Expected: CREATE_VECTOR_INDEX receives the configured attributes, a null description, and
     * the default wait-for-completion value.
     */
    @Test
    void createBindsNullDescriptionAsSqlNull() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_VECTOR_INDEX.get())).thenReturn(callableStatement);
        VectorIndexConfig config = VectorIndexConfig.builder("RAG_IDX")
                .vectorIndexAttributes(vectorIndexConfig().getVectorIndexAttributes())
                .description(null)
                .build();
        DefaultVectorIndex index = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), config);

        assertThat(index.create()).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setNull(4, Types.CLOB);
        verify(callableStatement).setInt(5, 0);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies create binds wait for completion true.
     * Expected: Creation returns true and binds waitForCompletion=1.
     */
    @Test
    void createBindsWaitForCompletionTrue() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_VECTOR_INDEX.get())).thenReturn(callableStatement);
        DefaultVectorIndex index = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), vectorIndexConfig(true));

        assertThat(index.create()).isTrue();

        verify(callableStatement).setInt(5, 1);
    }

    /**
     * Test: Verifies configured index exposes wait for completion flag.
     * Expected: The configured indexes report true and false for their respective
     * waitForCompletion settings.
     */
    @Test
    void configuredIndexExposesWaitForCompletionFlag() throws Exception {
        DefaultVectorIndex waitForCompletion = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), vectorIndexConfig(true));
        DefaultVectorIndex doNotWaitForCompletion = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), vectorIndexConfig(false));

        assertThat(waitForCompletion.isWaitForCompletion()).isTrue();
        assertThat(doNotWaitForCompletion.isWaitForCompletion()).isFalse();
    }

    /**
     * Test: Verifies create wraps sql failures.
     * Expected: A SelectAIException containing CREATE_VECTOR_INDEX and the original SQLException
     * cause is thrown.
     */
    @Test
    void createWrapsSqlFailures() throws Exception {
        SQLException sqlException = new SQLException("create failed", "42000", 20000);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_VECTOR_INDEX.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);
        DefaultVectorIndex index = new DefaultVectorIndex(new SingleConnectionProvider(dbConnection), vectorIndexConfig());

        assertThatThrownBy(() -> index.create())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("CREATE_VECTOR_INDEX")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies create allows null vector index attributes.
     * Expected: The SDK binds SQL NULL for the optional attributes CLOB and lets
     * DBMS_CLOUD_AI.CREATE_VECTOR_INDEX apply database-side validation/defaults.
     */
    @Test
    void createBindsNullVectorIndexAttributesForDatabaseValidation() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_VECTOR_INDEX.get())).thenReturn(callableStatement);
        VectorIndexConfig config = VectorIndexConfig.builder("RAG_IDX").build();
        DefaultVectorIndex index = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), config);

        assertThat(index.create()).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setNull(2, Types.CLOB);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies configured vector index rejects database operations before create.
     * Expected: Update, enable, disable, and both drop overloads throw their documented
     * create()-required IllegalStateException without database access.
     */
    @Test
    void configuredVectorIndexRejectsDatabaseOperationsBeforeCreate() throws Exception {
        DefaultVectorIndex pendingIndex = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), vectorIndexConfig());

        assertThatThrownBy(() -> pendingIndex.update("match_limit", "10"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("update requires a created vector index; call create() first");
        assertThatThrownBy(() -> pendingIndex.update(VectorIndexAttributes.updateBuilder()
                .matchLimit(10)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("update requires a created vector index; call create() first");
        assertThatThrownBy(pendingIndex::enable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("enable requires a created vector index; call create() first");
        assertThatThrownBy(pendingIndex::disable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disable requires a created vector index; call create() first");
        assertThatThrownBy(() -> pendingIndex.drop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created vector index; call create() first");
        assertThatThrownBy(() -> pendingIndex.drop(true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created vector index; call create() first");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies configured vector index returns pending status without database call.
     * Expected: The configured index returns DISABLED and the database is not accessed.
     */
    @Test
    void configuredVectorIndexReturnsPendingStatusWithoutDatabaseCall() throws Exception {
        DefaultVectorIndex pendingIndex = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), vectorIndexConfig());

        assertThat(pendingIndex.getStatus()).isEqualTo(VectorIndexConfig.Status.DISABLED.getValue());
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies configured vector index returns pending attributes without database call.
     * Expected: The configured VectorIndexAttributes instance is returned and the database is not accessed.
     */
    @Test
    void configuredVectorIndexReturnsPendingAttributesWithoutDatabaseCall() throws Exception {
        VectorIndexConfig configured = vectorIndexConfig();
        DefaultVectorIndex pendingIndex = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), configured);

        assertThat(pendingIndex.getVectorIndexAttributes())
                .isSameAs(configured.getVectorIndexAttributes());
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies open existing index loads description.
     * Expected: The loaded index has name RAG_IDX, description Product docs, and binds RAG_IDX
     * when reading metadata.
     */
    @Test
    void openExistingIndexLoadsDescription() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("index_name")).thenReturn("RAG_IDX");
        when(resultSet.getString("status")).thenReturn("Enabled");
        when(resultSet.getString("description")).thenReturn("Product docs");
        stubEmptyVectorIndexAttributes();

        DefaultVectorIndex index = new DefaultVectorIndex(new SingleConnectionProvider(dbConnection), "RAG_IDX");

        assertThat(index.getIndexName()).isEqualTo("RAG_IDX");
        assertThat(index.getDescription()).isEqualTo("Product docs");
        assertThat(index.isWaitForCompletion()).isNull();
        verify(preparedStatement).setString(1, "RAG_IDX");
    }

    /**
     * Test: Verifies open existing index maps all database attributes to java getters.
     * Expected: All 14 database attributes are converted to their corresponding Java getter
     * values, and attributes are read twice for construction and refresh.
     */
    @Test
    void openExistingIndexMapsAllDatabaseAttributesToJavaGetters() throws Exception {
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        ResultSet metadataResultSet = mock(ResultSet.class);
        PreparedStatement attributesStatement = mock(PreparedStatement.class);
        ResultSet initialAttributesResultSet = mock(ResultSet.class);
        ResultSet refreshedAttributesResultSet = mock(ResultSet.class);
        String[] attributeNames = {
                "chunk_size",
                "chunk_overlap",
                "enable_sources",
                "location",
                "match_limit",
                "object_storage_credential_name",
                "pipeline_name",
                "profile_name",
                "refresh_rate",
                "similarity_threshold",
                "vector_distance_metric",
                "vector_db_provider",
                "vector_dimension",
                "vector_table_name"
        };
        String[] attributeValues = {
                "1024",
                "128",
                "false",
                "https://object.example/docs",
                "8",
                "OBJ_CRED",
                "RAG_IDX$VECPIPELINE",
                "RAG_PROFILE",
                "60",
                "0.7",
                "COSINE",
                "oracle",
                "1536",
                "RAG_IDX_TABLE"
        };
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get())).thenReturn(metadataStatement);
        when(metadataStatement.executeQuery()).thenReturn(metadataResultSet);
        when(metadataResultSet.next()).thenReturn(true);
        when(metadataResultSet.getString("index_name")).thenReturn("RAG_IDX");
        when(metadataResultSet.getString("status")).thenReturn("Enabled");
        when(metadataResultSet.getString("description")).thenReturn("Product docs");
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX_ATTRIBUTES.get()))
                .thenReturn(attributesStatement);
        stubVectorIndexAttributes(initialAttributesResultSet, attributeNames, attributeValues);
        stubVectorIndexAttributes(refreshedAttributesResultSet, attributeNames, attributeValues);
        when(attributesStatement.executeQuery())
                .thenReturn(initialAttributesResultSet, refreshedAttributesResultSet);

        DefaultVectorIndex index = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), "RAG_IDX");
        VectorIndexAttributes attributes = index.getVectorIndexAttributes();

        assertThat(attributes.getChunkSize()).isEqualTo(1024);
        assertThat(attributes.getChunkOverlap()).isEqualTo(128);
        assertThat(attributes.getEnableSources()).isFalse();
        assertThat(attributes.getLocation()).isEqualTo("https://object.example/docs");
        assertThat(attributes.getMatchLimit()).isEqualTo(8);
        assertThat(attributes.getObjectStorageCredentialName()).isEqualTo("OBJ_CRED");
        assertThat(attributes.getPipelineName()).isEqualTo("RAG_IDX$VECPIPELINE");
        assertThat(attributes.getProfileName()).isEqualTo("RAG_PROFILE");
        assertThat(attributes.getRefreshRate()).isEqualTo(60);
        assertThat(attributes.getSimilarityThreshold()).isEqualTo(0.7);
        assertThat(attributes.getVectorDistanceMetric()).isEqualTo("COSINE");
        assertThat(attributes.getVectorDbProvider()).isEqualTo("oracle");
        assertThat(attributes.getVectorDimension()).isEqualTo(1536);
        assertThat(attributes.getVectorTableName()).isEqualTo("RAG_IDX_TABLE");
        verify(attributesStatement, times(2)).setString(1, "RAG_IDX");
    }

    /**
     * Test: Verifies open existing index binds regex metacharacters literally.
     * Expected: The index name RAG_IDX[1].A is returned unchanged and bound unchanged to JDBC.
     */
    @Test
    void openExistingIndexBindsRegexMetacharactersLiterally() throws Exception {
        String indexName = "RAG_IDX[1].A";
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("index_name")).thenReturn(indexName);
        when(resultSet.getString("status")).thenReturn("Enabled");
        when(resultSet.getString("description")).thenReturn("Product docs");
        stubEmptyVectorIndexAttributes();

        DefaultVectorIndex index = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), indexName);

        assertThat(index.getIndexName()).isEqualTo(indexName);
        verify(preparedStatement).setString(1, indexName);
    }

    /**
     * Test: Verifies loaded vector index does not invent unavailable status.
     * Expected: A loaded index with a null database status returns null.
     */
    @Test
    void loadedVectorIndexDoesNotInventUnavailableStatus() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("index_name")).thenReturn("RAG_IDX");
        when(resultSet.getString("status")).thenReturn((String) null);
        when(resultSet.getString("description")).thenReturn("Product docs");
        stubEmptyVectorIndexAttributes();

        DefaultVectorIndex index = new DefaultVectorIndex(new SingleConnectionProvider(dbConnection), "RAG_IDX");

        assertThat(index.getStatus()).isNull();
    }

    /**
     * Test: Verifies get status refreshes status from database for loaded index.
     * Expected: getStatus() refreshes the loaded index and returns the database value DISABLED.
     */
    @Test
    void getStatusRefreshesStatusFromDatabaseForLoadedIndex() throws Exception {
        PreparedStatement initialMetadataStatement = mock(PreparedStatement.class);
        ResultSet initialMetadataResultSet = mock(ResultSet.class);
        PreparedStatement refreshedMetadataStatement = mock(PreparedStatement.class);
        ResultSet refreshedMetadataResultSet = mock(ResultSet.class);
        PreparedStatement attributesStatement = mock(PreparedStatement.class);
        ResultSet attributesResultSet = mock(ResultSet.class);

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get()))
                .thenReturn(initialMetadataStatement, refreshedMetadataStatement);
        when(initialMetadataStatement.executeQuery()).thenReturn(initialMetadataResultSet);
        when(initialMetadataResultSet.next()).thenReturn(true);
        when(initialMetadataResultSet.getString("index_name")).thenReturn("RAG_IDX");
        when(initialMetadataResultSet.getString("status"))
                .thenReturn(VectorIndexConfig.Status.ENABLED.getValue());
        when(initialMetadataResultSet.getString("description")).thenReturn("Product docs");
        when(refreshedMetadataStatement.executeQuery()).thenReturn(refreshedMetadataResultSet);
        when(refreshedMetadataResultSet.next()).thenReturn(true);
        when(refreshedMetadataResultSet.getString("index_name")).thenReturn("RAG_IDX");
        when(refreshedMetadataResultSet.getString("status"))
                .thenReturn(VectorIndexConfig.Status.DISABLED.getValue());
        when(refreshedMetadataResultSet.getString("description")).thenReturn("Product docs");
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX_ATTRIBUTES.get()))
                .thenReturn(attributesStatement);
        when(attributesStatement.executeQuery()).thenReturn(attributesResultSet);
        when(attributesResultSet.next()).thenReturn(false);

        DefaultVectorIndex index = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), "RAG_IDX");

        assertThat(index.getStatus()).isEqualTo(VectorIndexConfig.Status.DISABLED.getValue());
        verify(refreshedMetadataStatement).setString(1, "RAG_IDX");
    }

    /**
     * Test: Verifies second vector index reference reloads attributes after another reference updates.
     * Expected: The second reference refreshes its attributes and returns match_limit=10 after
     * the first reference updates the index.
     */
    @Test
    void secondVectorIndexReferenceReloadsAttributesAfterAnotherReferenceUpdates()
            throws Exception {
        PreparedStatement firstMetadataStatement = mock(PreparedStatement.class);
        PreparedStatement secondMetadataStatement = mock(PreparedStatement.class);
        PreparedStatement firstAttributesStatement = mock(PreparedStatement.class);
        PreparedStatement secondAttributesStatement = mock(PreparedStatement.class);
        PreparedStatement refreshedAttributesStatement = mock(PreparedStatement.class);
        ResultSet firstMetadataResultSet = mock(ResultSet.class);
        ResultSet secondMetadataResultSet = mock(ResultSet.class);
        ResultSet firstAttributesResultSet = mock(ResultSet.class);
        ResultSet secondAttributesResultSet = mock(ResultSet.class);
        ResultSet refreshedAttributesResultSet = mock(ResultSet.class);

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get()))
                .thenReturn(firstMetadataStatement, secondMetadataStatement);
        stubVectorIndexMetadata(firstMetadataStatement, firstMetadataResultSet);
        stubVectorIndexMetadata(secondMetadataStatement, secondMetadataResultSet);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX_ATTRIBUTES.get()))
                .thenReturn(firstAttributesStatement, secondAttributesStatement,
                        refreshedAttributesStatement);
        stubSingleVectorIndexAttribute(firstAttributesStatement, firstAttributesResultSet, "5");
        stubSingleVectorIndexAttribute(secondAttributesStatement, secondAttributesResultSet, "5");
        stubSingleVectorIndexAttribute(refreshedAttributesStatement, refreshedAttributesResultSet, "10");
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get()))
                .thenReturn(callableStatement);

        DefaultVectorIndex first = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), "RAG_IDX");
        DefaultVectorIndex second = new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), "RAG_IDX");

        assertThat(first.update("match_limit", "10")).isTrue();

        assertThat(second.getVectorIndexAttributes().getMatchLimit()).isEqualTo(10);
        verify(refreshedAttributesStatement).setString(1, "RAG_IDX");
    }

    /**
     * Test: Verifies open missing index throws select ai exception.
     * Expected: Opening a missing RAG_IDX throws SelectAIException with a Vector index not found
     * message and binds RAG_IDX to the lookup.
     */
    @Test
    void openMissingIndexThrowsSelectAIException() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertThatThrownBy(() -> new DefaultVectorIndex(new SingleConnectionProvider(dbConnection), "RAG_IDX"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Vector index not found: RAG_IDX");

        verify(preparedStatement).setString(1, "RAG_IDX");
    }

    /**
     * Test: Report a missing database-backed index during status refresh.
     * Expected: getStatus() propagates the SelectAIException instead of returning stale status.
     */
    @Test
    void getStatusFailsWhenLoadedIndexDisappears() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        DefaultVectorIndex index = loadedVectorIndex();

        assertThatThrownBy(index::getStatus)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("Vector index not found: RAG_IDX");
        verify(preparedStatement).setString(1, "RAG_IDX");
    }

    /**
     * Test: Reject invalid single- and bulk-update arguments before JDBC execution.
     * Expected: Null payloads, blank names, and null values raise IllegalArgumentException.
     */
    @Test
    void updateRejectsInvalidArgumentsBeforeDatabaseCall() throws Exception {
        DefaultVectorIndex index = loadedVectorIndex();

        assertThatThrownBy(() -> index.update((VectorIndexAttributes) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorIndexAttributes");
        assertThatThrownBy(() -> index.update((String) null, "10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributeName");
        assertThatThrownBy(() -> index.update(" ", "10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributeName");
        assertThatThrownBy(() -> index.update("match_limit", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributeValue");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Wrap failures while fetching existing vector-index attributes.
     * Expected: Opening an index reports the attribute-query context and preserves the SQL cause.
     */
    @Test
    void openExistingIndexWrapsAttributeFetchFailure() throws Exception {
        SQLException sqlException = new SQLException("attribute read failed", "42000", 942);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("index_name")).thenReturn("RAG_IDX");
        when(resultSet.getString("status")).thenReturn("Enabled");
        when(resultSet.getString("description")).thenReturn("Product docs");
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX_ATTRIBUTES.get()))
                .thenThrow(sqlException);

        assertThatThrownBy(() -> new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection), "RAG_IDX"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("vector index attributes")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies drop enable and disable bind index name and execute procedures.
     * Expected: Drop, enable, and disable return true, execute for RAG_IDX, and drop binds both
     * includeData=1 and force=1.
     */
    @Test
    void dropEnableAndDisableBindIndexNameAndExecuteProcedures() throws Exception {
        DefaultVectorIndex index = loadedVectorIndex();
        CallableStatement dropStatement = mock(CallableStatement.class);
        CallableStatement enableStatement = mock(CallableStatement.class);
        CallableStatement disableStatement = mock(CallableStatement.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.DROP_VECTOR_INDEX.get())).thenReturn(dropStatement);
        when(connection.prepareCall(Sql.ENABLE_VECTOR_INDEX.get())).thenReturn(enableStatement);
        when(connection.prepareCall(Sql.DISABLE_VECTOR_INDEX.get())).thenReturn(disableStatement);

        assertThat(index.drop(true)).isTrue();
        assertThat(index.enable()).isTrue();
        assertThat(index.disable()).isTrue();

        verify(dropStatement).setString(1, "RAG_IDX");
        verify(dropStatement).setInt(2, 1);
        verify(dropStatement).setInt(3, 1);
        verify(dropStatement).execute();
        verify(enableStatement).setString(1, "RAG_IDX");
        verify(enableStatement).execute();
        verify(disableStatement).setString(1, "RAG_IDX");
        verify(disableStatement).execute();
    }

    /**
     * Test: Verifies drop overload binds include data and force independently.
     * Expected: drop(false, true) returns true and binds RAG_IDX, includeData=0, and force=1.
     */
    @Test
    void dropOverloadBindsIncludeDataAndForceIndependently() throws Exception {
        DefaultVectorIndex index = loadedVectorIndex();
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.DROP_VECTOR_INDEX.get())).thenReturn(callableStatement);

        assertThat(index.drop(false, true)).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setInt(2, 0);
        verify(callableStatement).setInt(3, 1);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies the shorthand drop(force) overload uses the documented include-data default.
     * Expected: drop(false) binds includeData=1 and force=0.
     */
    @Test
    void dropShorthandBindsIncludeDataTrueAndForceFalse() throws Exception {
        DefaultVectorIndex index = loadedVectorIndex();
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.DROP_VECTOR_INDEX.get())).thenReturn(callableStatement);

        assertThat(index.drop(false)).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setInt(2, 1);
        verify(callableStatement).setInt(3, 0);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies lifecycle methods wrap sql failures.
     * Expected: Drop, enable, and disable each throw SelectAIException containing their
     * operation name and the corresponding SQLException cause.
     */
    @Test
    void lifecycleMethodsWrapSqlFailures() throws Exception {
        DefaultVectorIndex index = loadedVectorIndex();
        SQLException dropException = new SQLException("drop failed", "42000", 20001);
        SQLException enableException = new SQLException("enable failed", "42000", 20002);
        SQLException disableException = new SQLException("disable failed", "42000", 20003);
        CallableStatement dropStatement = mock(CallableStatement.class);
        CallableStatement enableStatement = mock(CallableStatement.class);
        CallableStatement disableStatement = mock(CallableStatement.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.DROP_VECTOR_INDEX.get())).thenReturn(dropStatement);
        when(connection.prepareCall(Sql.ENABLE_VECTOR_INDEX.get())).thenReturn(enableStatement);
        when(connection.prepareCall(Sql.DISABLE_VECTOR_INDEX.get())).thenReturn(disableStatement);
        when(dropStatement.execute()).thenThrow(dropException);
        when(enableStatement.execute()).thenThrow(enableException);
        when(disableStatement.execute()).thenThrow(disableException);

        assertThatThrownBy(() -> index.drop(false))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("DROP_VECTOR_INDEX")
                .hasCause(dropException);
        assertThatThrownBy(index::enable)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("ENABLE_VECTOR_INDEX")
                .hasCause(enableException);
        assertThatThrownBy(index::disable)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("DISABLE_VECTOR_INDEX")
                .hasCause(disableException);
    }

    /**
     * Test: Verifies list returns discovered vector indexes.
     * Expected: Two indexes with their names and descriptions are returned, and the default
     * pattern .* is bound.
     */
    @Test
    void listReturnsDiscoveredVectorIndexes() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_VECTOR_INDEXES.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("index_name")).thenReturn("RAG_IDX_1", "RAG_IDX_2");
        when(resultSet.getString("description")).thenReturn("Product docs", "Support docs");
        stubEmptyVectorIndexAttributes();

        List<VectorIndex> indexes = new DefaultSelectAI(
                new SingleConnectionProvider(dbConnection)).listVectorIndexes();

        assertThat(indexes).hasSize(2);
        assertThat(indexes.get(0).getIndexName()).isEqualTo("RAG_IDX_1");
        assertThat(indexes.get(0).getDescription()).isEqualTo("Product docs");
        assertThat(indexes.get(1).getIndexName()).isEqualTo("RAG_IDX_2");
        assertThat(indexes.get(1).getDescription()).isEqualTo("Support docs");
        verify(preparedStatement).setString(1, ".*");
    }

    /**
     * Test: Verifies list binds caller supplied pattern.
     * Expected: Only RAG_IDX_1 is returned and the caller pattern ^RAG_IDX_[0-9]+$ is bound.
     */
    @Test
    void listBindsCallerSuppliedPattern() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_VECTOR_INDEXES.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("index_name")).thenReturn("RAG_IDX_1");
        when(resultSet.getString("description")).thenReturn("Product docs");
        stubEmptyVectorIndexAttributes();

        List<VectorIndex> indexes = new DefaultSelectAI(
                new SingleConnectionProvider(dbConnection)).listVectorIndexes("^RAG_IDX_[0-9]+$");

        assertThat(indexes).extracting(VectorIndex::getIndexName)
                .containsExactly("RAG_IDX_1");
        verify(preparedStatement).setString(1, "^RAG_IDX_[0-9]+$");
    }

    /**
     * Test: Verifies list rejects null or blank pattern before database access.
     * Expected: Null, blank, and empty patterns each throw IllegalArgumentException without
     * accessing the database.
     */
    @Test
    void listRejectsNullOrBlankPatternBeforeDatabaseAccess() throws Exception {
        DefaultSelectAI selectAI = new DefaultSelectAI(new SingleConnectionProvider(dbConnection));

        assertThatThrownBy(() -> selectAI.listVectorIndexes((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexNamePattern");
        assertThatThrownBy(() -> selectAI.listVectorIndexes(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexNamePattern");
        assertThatThrownBy(() -> selectAI.listVectorIndexes(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexNamePattern");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies list wraps sql exceptions.
     * Expected: Listing indexes throws SelectAIException containing vector indexes and the
     * original metadata-read SQLException cause.
     */
    @Test
    void listWrapsSqlExceptions() throws Exception {
        SQLException sqlException = new SQLException("metadata read failed", "42000", 942);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_VECTOR_INDEXES.get())).thenThrow(sqlException);

        assertThatThrownBy(() -> new DefaultSelectAI(
                new SingleConnectionProvider(dbConnection)).listVectorIndexes())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("vector indexes")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies update single attribute normalizes name and binds value.
     * Expected: The update returns true and binds RAG_IDX, match_limit, and 10 before execution.
     */
    @Test
    void updateSingleAttributeNormalizesNameAndBindsValue() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get())).thenReturn(callableStatement);
        DefaultVectorIndex index = loadedVectorIndex();

        assertThat(index.update(" Match_Limit ", "10")).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setString(2, "match_limit");
        verify(callableStatement).setString(3, "10");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies update attribute normalization is locale independent.
     * Expected: Under the Turkish locale, MATCH_LIMIT is still normalized to match_limit.
     */
    @Test
    void updateAttributeNormalizationIsLocaleIndependent() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get())).thenReturn(callableStatement);
        DefaultVectorIndex index = loadedVectorIndex();
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(index.update("MATCH_LIMIT", "10")).isTrue();

            verify(callableStatement).setString(2, "match_limit");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Test: Verifies update single attribute can send character stream.
     * Expected: The update returns true and sends the user_prompt value through a character stream.
     */
    @Test
    void updateSingleAttributeCanSendCharacterStream() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get())).thenReturn(callableStatement);
        DefaultVectorIndex index = loadedVectorIndex();
        String prompt = "vector-user-prompt:" + "p".repeat(40_000);

        assertThat(index.update("user_prompt", prompt, true)).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setString(2, "user_prompt");
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(3), readerCaptor.capture(), eq(prompt.length()));
        assertThat(readAll(readerCaptor.getValue())).isEqualTo(prompt);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies update methods wrap sql failures.
     * Expected: Single-attribute and bulk updates each throw SelectAIException with their
     * operation name and corresponding SQLException cause.
     */
    @Test
    void updateMethodsWrapSqlFailures() throws Exception {
        SQLException singleException = new SQLException("single update failed", "42000", 20004);
        SQLException bulkException = new SQLException("bulk update failed", "42000", 20005);
        CallableStatement singleStatement = mock(CallableStatement.class);
        CallableStatement bulkStatement = mock(CallableStatement.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get())).thenReturn(singleStatement);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTES.get())).thenReturn(bulkStatement);
        when(singleStatement.execute()).thenThrow(singleException);
        when(bulkStatement.execute()).thenThrow(bulkException);
        DefaultVectorIndex index = loadedVectorIndex();

        assertThatThrownBy(() -> index.update("match_limit", "10"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("UPDATE_VECTOR_INDEX(attribute)")
                .hasCause(singleException);
        assertThatThrownBy(() -> index.update(VectorIndexAttributes.updateBuilder().matchLimit(10).build()))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("UPDATE_VECTOR_INDEX")
                .hasCause(bulkException);
    }

    /**
     * Test: Verifies update single attribute sql failure uses generic wrapper.
     * Expected: The update throws SelectAIException containing UPDATE_VECTOR_INDEX(attribute),
     * preserves the SQLException cause, and binds the normalized attribute and value.
     */
    @Test
    void updateSingleAttributeSqlFailureUsesGenericWrapper() throws Exception {
        SQLException sqlException = new SQLException("invalid value", "42000", 20007);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);
        DefaultVectorIndex index = loadedVectorIndex();

        assertThatThrownBy(() -> index.update("MATCH_LIMIT", "bad"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("UPDATE_VECTOR_INDEX(attribute)")
                .hasCause(sqlException);
        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setString(2, "match_limit");
        verify(callableStatement).setString(3, "bad");
    }

    /**
     * Test: Verifies update bulk binds mutable payload.
     * Expected: The bulk payload contains only match_limit=8, refresh_rate=720, and
     * similarity_threshold=1, then executes for RAG_IDX.
     */
    @Test
    void updateBulkBindsMutablePayload() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTES.get())).thenReturn(callableStatement);
        DefaultVectorIndex index = loadedVectorIndex();
        VectorIndexAttributes attributes = VectorIndexAttributes.updateBuilder()
                .matchLimit(8)
                .refreshRate(720)
                .similarityThreshold(1.0)
                .build();

        assertThat(index.update(attributes)).isTrue();

        ArgumentCaptor<Reader> payloadCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setCharacterStream(eq(2), payloadCaptor.capture(),
                eq(attributes.toJson().length()));
        assertThat(readAll(payloadCaptor.getValue()))
                .contains("\"match_limit\":8")
                .contains("\"refresh_rate\":720")
                .contains("\"similarity_threshold\":1")
                .doesNotContain("chunk_size")
                .doesNotContain("location")
                .doesNotContain("vector_distance_metric");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies UPDATE_VECTOR_INDEX binds a large bulk attributes JSON payload as a CLOB
     * stream.
     * Expected: The complete location value is preserved at the attributes bind position.
     */
    @Test
    void updateBulkBindsLargeAttributesAsCharacterStream() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTES.get())).thenReturn(callableStatement);
        DefaultVectorIndex index = loadedVectorIndex();
        String location = "https://example.com/" + "l".repeat(40_000);
        VectorIndexAttributes attributes = VectorIndexAttributes.updateBuilder()
                .location(location)
                .build();

        assertThat(index.update(attributes)).isTrue();

        ArgumentCaptor<Reader> attributesReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setCharacterStream(eq(2), attributesReader.capture(),
                eq(attributes.toJson().length()));
        assertThat(readAll(attributesReader.getValue())).isEqualTo(attributes.toJson());
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies update delegates create time attribute validation to database.
     * Expected: The location update returns true and passes location validation and binding to
     * the database for RAG_IDX.
     */
    @Test
    void updateDelegatesCreateTimeAttributeValidationToDatabase() throws Exception {
        DefaultVectorIndex index = loadedVectorIndex();
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE.get())).thenReturn(callableStatement);

        assertThat(index.update("location", "https://new.example/docs")).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        verify(callableStatement).setString(2, "location");
        verify(callableStatement).setString(3, "https://new.example/docs");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies update bulk delegates create time attribute validation to database.
     * Expected: The bulk create-time attributes are serialized and passed to the database for
     * RAG_IDX without Java-side rejection.
     */
    @Test
    void updateBulkDelegatesCreateTimeAttributeValidationToDatabase() throws Exception {
        DefaultVectorIndex index = loadedVectorIndex();
        VectorIndexAttributes createPayload = VectorIndexAttributes.builder()
                .location("https://object.example/docs")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build();

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.UPDATE_VECTOR_INDEX_ATTRIBUTES.get())).thenReturn(callableStatement);

        assertThat(index.update(createPayload)).isTrue();

        verify(callableStatement).setString(1, "RAG_IDX");
        ArgumentCaptor<Reader> payloadCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), payloadCaptor.capture(),
                eq(createPayload.toJson().length()));
        assertThat(readAll(payloadCaptor.getValue())).contains("location");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies default vector index does not expose underlying connection provider.
     * Expected: No public connection-provider, DbConnection, or Connection getter exists, and
     * no profileName field is declared.
     */
    @Test
    void defaultVectorIndexDoesNotExposeUnderlyingConnectionProvider() {
        assertThat(Arrays.stream(DefaultVectorIndex.class.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .noneMatch(method -> method.getName().equals("getConnectionProvider")
                        || method.getName().equals("getDbConnection")
                        || method.getName().equals("getConnection")))
                .isTrue();
        assertThat(Arrays.stream(DefaultVectorIndex.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("profileName")))
                .isTrue();
    }

    private static VectorIndexConfig vectorIndexConfig() {
        return vectorIndexConfig(false);
    }

    private static VectorIndexConfig vectorIndexConfig(boolean waitForCompletion) {
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location("https://object.example/docs")
                .objectStorageCredentialName("OBJ_CRED")
                .profileName("RAG_PROFILE")
                .vectorDbProvider("oracle")
                .build();

        return VectorIndexConfig.builder("RAG_IDX")
                .description("Product docs")
                .vectorIndexAttributes(attributes)
                .waitForCompletion(waitForCompletion)
                .build();
    }

    private DefaultVectorIndex loadedVectorIndex() {
        return new DefaultVectorIndex(
                new SingleConnectionProvider(dbConnection),
                "RAG_IDX",
                "Product docs",
                VectorIndexConfig.Status.ENABLED.getValue(),
                vectorIndexConfig().getVectorIndexAttributes());
    }

    private void stubEmptyVectorIndexAttributes() throws SQLException {
        when(connection.prepareStatement(Sql.GET_VECTOR_INDEX_ATTRIBUTES.get()))
                .thenReturn(vectorAttributesPreparedStatement);
        when(vectorAttributesPreparedStatement.executeQuery()).thenReturn(vectorAttributesResultSet);
        when(vectorAttributesResultSet.next()).thenReturn(false);
    }

    private static void stubVectorIndexMetadata(PreparedStatement statement,
                                                ResultSet resultSet) throws SQLException {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("index_name")).thenReturn("RAG_IDX");
        when(resultSet.getString("status")).thenReturn(VectorIndexConfig.Status.ENABLED.getValue());
        when(resultSet.getString("description")).thenReturn("Product docs");
    }

    private static void stubSingleVectorIndexAttribute(PreparedStatement statement,
                                                       ResultSet resultSet,
                                                       String value) throws SQLException {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("attribute_name")).thenReturn("match_limit");
        when(resultSet.getString("attribute_value")).thenReturn(value);
    }

    private static void stubVectorIndexAttributes(ResultSet resultSet,
                                                  String[] attributeNames,
                                                  String[] attributeValues) throws SQLException {
        int[] row = {0};
        int[] nameRow = {0};
        int[] valueRow = {0};
        when(resultSet.next()).thenAnswer(invocation -> row[0]++ < attributeNames.length);
        when(resultSet.getString("attribute_name"))
                .thenAnswer(invocation -> attributeNames[nameRow[0]++]);
        when(resultSet.getString("attribute_value"))
                .thenAnswer(invocation -> attributeValues[valueRow[0]++]);
    }

    private static String readAll(Reader reader) throws Exception {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[256];
        int charsRead;
        while ((charsRead = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, charsRead);
        }
        return builder.toString();
    }
}
