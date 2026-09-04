/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAttributeUtilsTest {

    @Mock
    private DbConnection dbConnection;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;

    /**
     * Test: Map known database profile attributes to their typed Java fields.
 * Expected: All documented values are converted and unknown attributes are preserved.
     */
    @Test
void fetchProfileAttributesMapsKnownDatabaseAttributes() throws Exception {
    when(dbConnection.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(org.mockito.Mockito.anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    List<String> attributeNames = List.of(
            "PrOvIdEr",
            "CrEdEnTiAl_NaMe",
            "MaX_ToKeNs",
            "TeMpErAtUrE",
            "CoNvErSaTiOn",
            "CoNvErSaTiOn_LeNgTh",
            "SeEd",
            "StOp_ToKeNs",
            "AdDiTiOnAl_InStRuCtIoNs",
            "AnNoTaTiOnS",
            "AzUrE_DePlOyMeNt_NaMe",
            "AzUrE_EmBeDdInG_DePlOyMeNt_NaMe",
            "AzUrE_ReSoUrCe_NaMe",
            "CaSe_SeNsItIvE_VaLuEs",
            "CoMmEnTs",
            "CoNsTrAiNtS",
            "EmBeDdInG_MoDeL",
            "EnAbLe_CuStOm_SoUrCe_UrI",
            "EnFoRcE_ObJeCt_LiSt",
            "MoDeL",
            "ObJeCt_LiSt",
            "ObJeCt_LiSt_MoDe",
            "OcI_ApIfOrMaT",
            "OcI_CoMpArTmEnT_Id",
            "OcI_EnDpOiNt_Id",
            "OcI_RuNtImEtYpE",
            "PrOvIdEr_EnDpOiNt",
            "ReGiOn",
            "RoLe",
            "VeCtOr_InDeX_NaMe",
            "SoUrCe_LaNgUaGe",
            "TaRgEt_LaNgUaGe"
    );
    List<String> attributeValues = List.of(
            "OpEnAi",
            "OPENAI_CRED",
            "512",
            "0.4",
            "TrUe",
            "6",
            String.valueOf(Long.MIN_VALUE),
            "[\"END_OF_RESPONSE\",\"STOP\"]",
            "Use concise answers",
            "TrUe",
            "azure-deployment",
            "azure-embedding-deployment",
            "azure-resource",
            "FaLsE",
            "TrUe",
            "FaLsE",
            "text-embedding-3-large",
            "TrUe",
            "FaLsE",
            "gpt-test",
            "[{\"owner\":\"HR\"}]",
            "AuToMaTeD",
            "gEnErIc",
            "ocid1.compartment.oc1..example",
            "ocid1.generativeaiendpoint.oc1..example",
            "COHERE",
            "selectai.example.com",
            "us-chicago-1",
            "You are a database assistant",
            "RAG_IDX",
            "English",
            "French"
    );
    int[] row = {-1};
    when(resultSet.next()).thenAnswer(invocation -> {
        row[0]++;
        return row[0] < attributeNames.size();
    });
    when(resultSet.getString("attribute_name"))
            .thenAnswer(invocation -> attributeNames.get(row[0]));
    when(resultSet.getString("attribute_value"))
            .thenAnswer(invocation -> attributeValues.get(row[0]));

        ProfileAttributes attributes = ProfileAttributeUtils.fetchProfileAttributes(dbConnection, "PROFILE_1");

        assertThat(attributes.getProvider()).isEqualTo("openai");
        assertThat(attributes.getCredentialName()).isEqualTo("OPENAI_CRED");
        assertThat(attributes.getMaxTokens()).isEqualTo(512);
        assertThat(attributes.getTemperature()).isEqualTo(0.4);
        assertThat(attributes.getConversation()).isTrue();
        assertThat(attributes.getConversationLength()).isEqualTo(6);
    assertThat(attributes.getSeed()).isEqualTo(Long.MIN_VALUE);
    assertThat(attributes.getStopTokens()).containsExactly("END_OF_RESPONSE", "STOP");
    assertThat(attributes.getAdditionalInstructions()).isEqualTo("Use concise answers");
    assertThat(attributes.getAnnotations()).isTrue();
    assertThat(attributes.getAzureDeploymentName()).isEqualTo("azure-deployment");
    assertThat(attributes.getAzureEmbeddingDeploymentName())
            .isEqualTo("azure-embedding-deployment");
    assertThat(attributes.getAzureResourceName()).isEqualTo("azure-resource");
    assertThat(attributes.getCaseSensitiveValues()).isFalse();
    assertThat(attributes.getComments()).isTrue();
    assertThat(attributes.getConstraints()).isFalse();
    assertThat(attributes.getEmbeddingModel()).isEqualTo("text-embedding-3-large");
    assertThat(attributes.getEnableCustomSourceUri()).isTrue();
    assertThat(attributes.getEnforceObjectList()).isFalse();
    assertThat(attributes.getModel()).isEqualTo("gpt-test");
    assertThat(attributes.getObjectList()).isEqualTo("[{\"owner\":\"HR\"}]");
    assertThat(attributes.getObjectListMode()).isEqualTo("automated");
    assertThat(attributes.getOciApiformat()).isEqualTo("GENERIC");
    assertThat(attributes.getOciCompartmentId())
            .isEqualTo("ocid1.compartment.oc1..example");
    assertThat(attributes.getOciEndpointId())
            .isEqualTo("ocid1.generativeaiendpoint.oc1..example");
    assertThat(attributes.getOciRuntimetype()).isEqualTo("COHERE");
    assertThat(attributes.getProviderEndpoint()).isEqualTo("selectai.example.com");
    assertThat(attributes.getRegion()).isEqualTo("us-chicago-1");
    assertThat(attributes.getRole()).isEqualTo("You are a database assistant");
    assertThat(attributes.getVectorIndexName()).isEqualTo("RAG_IDX");
    assertThat(attributes.getSourceLanguage()).isEqualTo("English");
    assertThat(attributes.getTargetLanguage()).isEqualTo("French");
    verify(preparedStatement).setString(1, "PROFILE_1");
}

    /**
     * Test: Maps profile attribute names with the root locale.
     * Expected: Turkish default locale does not affect upper-case database attribute names.
     */
    @Test
    void fetchProfileAttributesUsesLocaleRootWhenMappingAttributeNames() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            when(dbConnection.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(org.mockito.Mockito.anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getString("attribute_name")).thenReturn("OCI_APIFORMAT", "CREDENTIAL_NAME");
            when(resultSet.getString("attribute_value")).thenReturn("generic", "OCI_CRED");

            ProfileAttributes attributes = ProfileAttributeUtils.fetchProfileAttributes(dbConnection, "PROFILE_1");

            assertThat(attributes.getOciApiformat()).isEqualTo("GENERIC");
            assertThat(attributes.getCredentialName()).isEqualTo("OCI_CRED");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Test: Map database translation-language attributes without changing their values.
     * Expected: Source and target language values are hydrated exactly as returned by the database.
     */
    @Test
    void fetchProfileAttributesMapsTranslationLanguageAttributes() throws Exception {
        when(connection.prepareStatement(org.mockito.Mockito.anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("attribute_name")).thenReturn("SOURCE_LANGUAGE", "TARGET_LANGUAGE");
        when(resultSet.getString("attribute_value")).thenReturn("eNgLiSh", "FrEnCh");

        ProfileAttributes attributes = ProfileAttributeUtils.fetchProfileAttributes(
                connection, "PROFILE_TRANSLATE");

        assertThat(attributes.getSourceLanguage()).isEqualTo("eNgLiSh");
        assertThat(attributes.getTargetLanguage()).isEqualTo("FrEnCh");
        verify(preparedStatement).setString(1, "PROFILE_TRANSLATE");
    }

    /**
     * Test: Wrap a SQL failure while fetching profile attributes.
     * Expected: A SelectAIException identifies the profile and preserves the SQL cause.
     */
    @Test
    void fetchProfileAttributesWrapsSqlExceptions() throws Exception {
        SQLException sqlException = new SQLException("view unavailable", "42000", 942);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.Mockito.anyString())).thenThrow(sqlException);

        assertThatThrownBy(() -> ProfileAttributeUtils.fetchProfileAttributes(dbConnection, "PROFILE_1"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("PROFILE_1")
                .hasCause(sqlException);
    }

    /**
     * Test: Fetches profile attributes through the direct JDBC overload.
     * Expected: The overload binds the profile name and maps the returned row.
     */
    @Test
    void fetchProfileAttributesUsingConnectionOverload() throws Exception {
        when(connection.prepareStatement(org.mockito.Mockito.anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("attribute_name")).thenReturn("provider");
        when(resultSet.getString("attribute_value")).thenReturn("oci");

        ProfileAttributes attributes = ProfileAttributeUtils.fetchProfileAttributes(connection, "PROFILE_2");

        assertThat(attributes.getProvider()).isEqualTo("oci");
        verify(preparedStatement).setString(1, "PROFILE_2");
    }

    /**
     * Test: Rejects missing connection arguments before preparing SQL.
     * Expected: Both public overloads throw IllegalArgumentException for null connections.
     */
    @Test
    void rejectsNullConnectionArguments() {
        assertThatThrownBy(() -> ProfileAttributeUtils.fetchProfileAttributes((DbConnection) null, "PROFILE_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbConnection");

        assertThatThrownBy(() -> ProfileAttributeUtils.fetchProfileAttributes((Connection) null, "PROFILE_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connection");
    }
}
