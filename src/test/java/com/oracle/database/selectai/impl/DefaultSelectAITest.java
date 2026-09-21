/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Properties;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSelectAITest {

    @Mock
    private DbConnection dbConnection;
    @Mock
    private Connection connection;
    @Mock
    private CallableStatement statement;

    /**
     * Test: Verifies implementation classes are not part of the public SDK surface.
     * Expected: Default implementation classes are package-private before GA.
     */
    @Test
    void implementationClassesAreNotPublicApi() {
        assertThat(Modifier.isPublic(DefaultSelectAI.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(DefaultDatabaseAdmin.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(DefaultProfile.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(DefaultCredential.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(DefaultConversation.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(DefaultVectorIndex.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(DefaultDbConnection.class.getModifiers())).isFalse();
    }

    /**
     * Test: Verifies constructor rejects null db connection config.
     * Expected: IllegalArgumentException mentioning dbConnectionConfig is thrown.
     */
    @Test
    void constructorRejectsNullDbConnectionConfig() {
        assertThatThrownBy(() -> new DefaultSelectAI((DbConnectionConfig) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbConnectionConfig");
    }

    /**
     * Test: Verifies constructor rejects null data source.
     * Expected: IllegalArgumentException mentioning dataSource is thrown.
     */
    @Test
    void constructorRejectsNullDataSource() {
        assertThatThrownBy(() -> new DefaultSelectAI((DataSource) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSource");
    }

    /**
     * Test: Reject an absent connection provider in the package-level constructor.
     * Expected: IllegalArgumentException identifies the missing provider.
     */
    @Test
    void constructorRejectsNullConnectionProvider() {
        assertThatThrownBy(() -> new DefaultSelectAI((ConnectionProvider) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionProvider");
    }

    /**
     * Test: Reject a DbConnection supplied without matching configuration.
     * Expected: IllegalArgumentException explains that DbConnectionConfig is required.
     */
    @Test
    void constructorRejectsDbConnectionWithoutConfiguration() {
        assertThatThrownBy(() -> new DefaultSelectAI(
                (DbConnectionConfig) null, dbConnection, SelectAIOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dbConnectionConfig");
    }

    /**
     * Test: Build a DataSource-backed client through the public factory.
     * Expected: A null options value uses defaults and does not borrow a connection eagerly.
     */
    @Test
    void publicFactoryBuildsDataSourceClientWithDefaultOptionsWhenOptionsAreNull() throws Exception {
        DataSource dataSource = mock(DataSource.class);

        SelectAI selectAI = SelectAI.create(dataSource, null);

        assertThat(selectAI).isNotNull();
        selectAI.close();
        verifyNoInteractions(dataSource);
    }

    /**
     * Test: Verifies the public factory creates a DataSource-backed client.
     * Expected: SelectAI.create(DataSource) returns the default implementation without
     * borrowing a connection eagerly.
     */
    @Test
    void publicFactoryCreatesDataSourceBackedClient() {
        DataSource dataSource = mock(DataSource.class);

        SelectAI selectAI = SelectAI.create(dataSource);

        assertThat(selectAI).isInstanceOf(DefaultSelectAI.class);
        verifyNoInteractions(dataSource);
    }

    /**
     * Test: Verifies the public factory accepts explicit DataSource execution options.
     * Expected: SelectAI.create(DataSource, options) returns the default implementation
     * without borrowing a connection eagerly.
     */
    @Test
    void publicFactoryCreatesDataSourceBackedClientWithOptions() {
        DataSource dataSource = mock(DataSource.class);
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(30)
                .build();

        SelectAI selectAI = SelectAI.create(dataSource, options);

        assertThat(selectAI).isInstanceOf(DefaultSelectAI.class);
        verifyNoInteractions(dataSource);
    }

    /**
     * Test: Verifies the public factory accepts a DataSource and explicit options.
     * Expected: Factory construction returns the default implementation without borrowing
     * a connection eagerly.
     */
    @Test
    void publicFactoryAcceptsDataSourceAndOptions() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(30)
                .build();

        SelectAI selectAI = SelectAI.create(dataSource, options);

        assertThat(selectAI).isInstanceOf(DefaultSelectAI.class);
        verifyNoInteractions(dataSource);
    }

    /**
     * Test: Build a client through the public DbConnectionConfig constructor.
     * Expected: The JDBC connection is initialized and exposed through getConnection().
     */
    @Test
    void dbConnectionConfigConstructorCreatesClient() throws Exception {
        TestDriver driver = new TestDriver(connection);
        DriverManager.registerDriver(driver);
        try {
            DefaultSelectAI selectAI = new DefaultSelectAI(DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("password")
                .jdbcUrl("jdbc:oracle:thin:@selectai-test")
                    .build());

            assertThat(selectAI.getConnection()).isSameAs(connection);
            assertThat(driver.connectionCalls).isEqualTo(1);
            selectAI.close();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    /**
     * Test: Verifies select ai does not expose administrative operations.
     * Expected: SelectAI has none of the listed administrative or privilege-management methods.
     */
    @Test
    void selectAIDoesNotExposeAdministrativeOperations() {
        assertThat(Arrays.stream(SelectAI.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain(
                        "enableDataAccess",
                        "disableDataAccess",
                        "grantPrivileges",
                        "revokePrivileges",
                        "grantHttpAccess",
                        "revokeHttpAccess",
                        "grantNetworkAccess",
                        "revokeNetworkAccess");
    }

    /**
     * Test: Verifies get connection returns retained connection for db connection config client.
     * Expected: getConnection() returns the same retained JDBC Connection and asks dbConnection
     * for it once.
     */
    @Test
    void getConnectionReturnsRetainedConnectionForDbConnectionConfigClient() {
        when(dbConnection.getConnection()).thenReturn(connection);
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        assertThat(selectAI.getConnection()).isSameAs(connection);
        verify(dbConnection).getConnection();
    }

    /**
     * Test: Verifies get connection rejects data source backed client.
     * Expected: getConnection() throws IllegalStateException mentioning DbConnectionConfig
     * without touching the DataSource.
     */
    @Test
    void getConnectionRejectsDataSourceBackedClient() {
        DataSource dataSource = mock(DataSource.class);
        DefaultSelectAI selectAI = new DefaultSelectAI(dataSource);

        assertThatThrownBy(selectAI::getConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DbConnectionConfig");
        verifyNoInteractions(dataSource);
    }

    /**
     * Test: Verifies close closes owned db connection.
     * Expected: close() delegates to the retained DbConnection.
     */
    @Test
    void closeClosesOwnedDbConnection() throws Exception {
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        selectAI.close();

        verify(dbConnection).close();
    }

    /**
     * Test: Verifies close propagates connection close failure.
     * Expected: close() rethrows the exact SelectAIException from DbConnection.close().
     */
    @Test
    void closePropagatesConnectionCloseFailure() throws Exception {
        SelectAIException closeFailure = new SelectAIException("close failed");
        doThrow(closeFailure).when(dbConnection).close();
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        assertThatThrownBy(selectAI::close)
                .isSameAs(closeFailure);
    }

    /**
     * Test: Verifies close is no op for data source backed client.
     * Expected: close() completes without interacting with the DataSource.
     */
    @Test
    void closeIsNoOpForDataSourceBackedClient() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        DefaultSelectAI selectAI = new DefaultSelectAI(dataSource);

        selectAI.close();

        verifyNoInteractions(dataSource);
    }

    /**
     * Test: Verifies all connection using select ai apis wrap closed connection as select ai exception.
     * Expected: Every exercised factory/list operation throws SelectAIException preserving the
     * closed-connection cause, error code 17002, and SQL state 08003.
     */
    @Test
    void allConnectionUsingSelectAIApisWrapClosedConnectionAsSelectAIException() throws Exception {
        SQLException closedConnection = new SQLException("Connection is closed", "08003", 17002);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.isClosed()).thenReturn(true);
        when(connection.prepareCall(anyString())).thenThrow(closedConnection);
        when(connection.prepareStatement(anyString())).thenThrow(closedConnection);
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        assertThat(connection.isClosed()).isTrue();

        // Factory methods are included by invoking one database operation on each returned resource.
        assertClosedConnectionFailure(() -> selectAI.credential(credentialConfig()).create(), closedConnection);
        assertClosedConnectionFailure(selectAI::listProfiles, closedConnection);
        assertClosedConnectionFailure(() -> selectAI.profile("PROFILE_1"), closedConnection);
        assertClosedConnectionFailure(() -> selectAI.profile(
                "PROFILE_2", profileAttributes(), "description", null).create(), closedConnection);
        assertClosedConnectionFailure(() -> selectAI.profile(
                "PROFILE_3", profileAttributes(), "description", ProfileStatus.DISABLED).create(),
                closedConnection);
        assertClosedConnectionFailure(selectAI::listVectorIndexes, closedConnection);
        assertClosedConnectionFailure(() -> selectAI.vectorIndex("RAG_IDX"), closedConnection);
        assertClosedConnectionFailure(() -> selectAI.vectorIndex(vectorIndexConfig()).create(), closedConnection);
        assertClosedConnectionFailure(selectAI::listConversations, closedConnection);
        assertClosedConnectionFailure(() -> selectAI.conversation(conversationAttributes()).create(), closedConnection);
        assertClosedConnectionFailure(() -> selectAI.conversation("CONV-123"), closedConnection);
    }

    /**
     * Test: Verifies factory methods return bound sdk objects.
     * Expected: Factory methods return the corresponding SDK implementations, including vector
     * index RAG_IDX/Product docs and conversation title Support Thread.
     */
    @Test
    void factoryMethodsReturnBoundSdkObjects() throws Exception {
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        Credential credential = selectAI.credential(credentialConfig());
        Profile configuredProfile = selectAI.profile(
                "PROFILE_1", profileAttributes(), null, null);
        VectorIndex configuredVectorIndex = selectAI.vectorIndex(vectorIndexConfig());
        Conversation configuredConversation = selectAI.conversation(conversationAttributes());

        assertThat(credential).isInstanceOf(DefaultCredential.class);
        assertThat(configuredProfile).isInstanceOf(DefaultProfile.class);
        assertThat(configuredVectorIndex).isInstanceOf(DefaultVectorIndex.class);
        assertThat(configuredVectorIndex.getIndexName()).isEqualTo("RAG_IDX");
        assertThat(configuredVectorIndex.getDescription()).isEqualTo("Product docs");
        assertThat(configuredConversation).isInstanceOf(DefaultConversation.class);
        assertThat(configuredConversation.getConversationAttributes().getTitle()).isEqualTo("Support Thread");
    }

    /**
     * Test: Verifies factory methods validate required arguments before touching database.
     * Expected: Null or blank required arguments throw IllegalArgumentException and the database
     * is not accessed.
     */
    @Test
    void factoryMethodsValidateRequiredArgumentsBeforeTouchingDatabase() {
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        assertThatThrownBy(() -> selectAI.credential(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialConfig");
        assertThatThrownBy(() -> selectAI.profile(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileName");
        assertThatThrownBy(() -> selectAI.profile(" ", profileAttributes(), "description", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileName");
        assertThatThrownBy(() -> selectAI.vectorIndex((VectorIndexConfig) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorIndexConfig");
        assertThatThrownBy(() -> selectAI.vectorIndex(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexName");
        assertThatThrownBy(() -> selectAI.conversation((ConversationAttributes) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationAttributes");
        assertThatThrownBy(() -> selectAI.conversation(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Lists profiles whose names match a regular-expression pattern and hydrates their
     * metadata and attributes from JDBC result sets.
     * Expected: The pattern is bound to the profile query, the matching profile is mapped, and
     * its profile name is bound when fetching attributes.
     */
    @Test
    void listProfilesByPatternBindsPatternAndMapsRows() throws Exception {
        PreparedStatement listStatement = mock(PreparedStatement.class);
        PreparedStatement attributesStatement = mock(PreparedStatement.class);
        ResultSet listResultSet = mock(ResultSet.class);
        ResultSet attributesResultSet = mock(ResultSet.class);
        String profileAttributesSql = "SELECT attribute_name, attribute_value " +
                "FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILE_ATTRIBUTES " +
                "WHERE profile_name = ?";
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_PROFILES_BY_PATTERN.get())).thenReturn(listStatement);
        when(connection.prepareStatement(profileAttributesSql)).thenReturn(attributesStatement);
        when(listStatement.executeQuery()).thenReturn(listResultSet);
        when(listResultSet.next()).thenReturn(true, false);
        when(listResultSet.getString("profile_name")).thenReturn("JSAI_PROFILE_1");
        when(listResultSet.getString("description")).thenReturn("Profile description");
        when(listResultSet.getString("status")).thenReturn("ENABLED");
        when(attributesStatement.executeQuery()).thenReturn(attributesResultSet);
        when(attributesResultSet.next()).thenReturn(false);
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        List<Profile> profiles = selectAI.listProfiles("^JSAI_PROFILE_[0-9]+$");

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).getProfileName()).isEqualTo("JSAI_PROFILE_1");
        verify(listStatement).setString(1, "^JSAI_PROFILE_[0-9]+$");
        verify(attributesStatement).setString(1, "JSAI_PROFILE_1");
    }

    /**
     * Test: Attempts to list profiles with a null or blank profile-name pattern.
     * Expected: IllegalArgumentException identifies profileNamePattern and no database
     * interaction occurs.
     */
    @Test
    void listProfilesByPatternRejectsNullOrBlankPatternBeforeJdbc() throws Exception {
        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        assertThatThrownBy(() -> selectAI.listProfiles(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileNamePattern");
        assertThatThrownBy(() -> selectAI.listProfiles(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileNamePattern");
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies profile name at 125 characters is accepted.
     * Expected: Profile creation succeeds and the profile retains the 125-character name.
     */
    @Test
    void profileNameAt125CharactersIsAccepted() throws Exception {
    String profileName = "P".repeat(125);

    when(dbConnection.getConnection()).thenReturn(connection);
    // Reuse the existing @Mock CallableStatement named `statement`.
    when(connection.prepareCall(Sql.CREATE_PROFILE.get()))
            .thenReturn(statement);

    DefaultSelectAI selectAI =
            new DefaultSelectAI(dbConnectionConfig(), dbConnection);

    Profile profile = selectAI.profile(
            profileName,
            profileAttributes(),
            null,
            null);

    profile.create();

    assertThat(profile.getProfileName()).isEqualTo(profileName);
}

    /**
     * Test: Verifies profile creation passes explicit initial status to database.
     * Expected: Profile creation preserves DISABLED status and binds the profile name and
     * explicit status before execution.
     */
    @Test
    void profileCreationPassesExplicitInitialStatusToDatabase() throws Exception {
        PreparedStatement profileStatement = mock(PreparedStatement.class);
        ResultSet profileResultSet = mock(ResultSet.class);

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_PROFILE.get())).thenReturn(profileStatement);
        when(profileStatement.executeQuery()).thenReturn(profileResultSet);
        when(profileResultSet.next()).thenReturn(true);
        when(profileResultSet.getString("profile_name")).thenReturn("DISABLED_PROFILE");
        when(profileResultSet.getString("status")).thenReturn("DISABLED");
        when(profileResultSet.getString("description")).thenReturn("description");
        when(connection.prepareCall(Sql.CREATE_PROFILE.get())).thenReturn(statement);

        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        Profile profile = selectAI.profile(
                "DISABLED_PROFILE",
                profileAttributes(),
                "description",
                ProfileStatus.DISABLED);

        profile.create();

        assertThat(profile.getStatus()).isEqualTo("DISABLED");
    verify(statement).setString(1, "DISABLED_PROFILE");
    verify(statement).setString(3, "DISABLED");
    verify(statement).setCharacterStream(eq(4), any(Reader.class), eq("description".length()));
    verify(statement).execute();
}

    /**
     * Test: Verifies profile creation does not inject defaults into create parameters.
     * Expected: Profile status and description remain null, and creation binds the supplied
     * attributes with null status and description parameters.
     */
    @Test
    void profileCreationDoesNotInjectDefaultsIntoCreateParameters() throws Exception {
        PreparedStatement profileStatement = mock(PreparedStatement.class);
        ResultSet profileResultSet = mock(ResultSet.class);
        ProfileAttributes attributes = profileAttributes();

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_PROFILE.get())).thenReturn(profileStatement);
        when(profileStatement.executeQuery()).thenReturn(profileResultSet);
        when(profileResultSet.next()).thenReturn(true);
        when(profileResultSet.getString("profile_name")).thenReturn("DEFAULT_PARAMETER_PROFILE");
        when(profileResultSet.getString("status")).thenReturn((String) null);
        when(profileResultSet.getString("description")).thenReturn((String) null);
        when(connection.prepareCall(Sql.CREATE_PROFILE.get())).thenReturn(statement);

        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        Profile profile = selectAI.profile(
                "DEFAULT_PARAMETER_PROFILE",
                attributes,
                null,
                null);

        profile.create();

        assertThat(profile.getStatus()).isNull();
        assertThat(profile.getDescription()).isNull();
        verify(statement).setString(1, "DEFAULT_PARAMETER_PROFILE");
        verify(statement).setCharacterStream(eq(2), any(Reader.class), eq(attributes.toJson().length()));
        verify(statement).setString(3, null);
        verify(statement).setNull(4, java.sql.Types.CLOB);
        verify(statement).execute();
    }

    /**
     * Test: Verifies profile name over125 characters is passed to database.
     * Expected: Profile creation passes the over-125-character name unchanged to the database
     * and the profile retains that name.
     */
    @Test
    void profileNameOver125CharactersIsPassedToDatabase() throws Exception {
        String profileName = "P" + "A".repeat(125);

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_PROFILE.get())).thenReturn(statement);

        DefaultSelectAI selectAI = new DefaultSelectAI(dbConnectionConfig(), dbConnection);

        Profile profile = selectAI.profile(
                profileName,
                profileAttributes(),
                null,
                null);

        profile.create();

        assertThat(profile.getProfileName()).isEqualTo(profileName);
        verify(statement).setString(1, profileName);
        verify(statement).execute();
    } 

    /**
     * Test: Map nullable numeric conversation metadata from JDBC.
     * Expected: Non-null retention days and a null conversation length are preserved.
     */
    @Test
    void listConversationsMapsNullableNumericMetadata() throws Exception {
        PreparedStatement conversationStatement = mock(PreparedStatement.class);
        ResultSet conversationResultSet = mock(ResultSet.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_CONVERSATIONS.get()))
                .thenReturn(conversationStatement);
        when(conversationStatement.executeQuery()).thenReturn(conversationResultSet);
        when(conversationResultSet.next()).thenReturn(true, false);
        when(conversationResultSet.getString("conversation_id")).thenReturn("CONV-1");
        when(conversationResultSet.getString("conversation_title")).thenReturn("Support Thread");
        when(conversationResultSet.getString("description")).thenReturn("Incident triage");
        when(conversationResultSet.getInt("retention_days")).thenReturn(21);
        when(conversationResultSet.getInt("conversation_length")).thenReturn(0);
        when(conversationResultSet.wasNull()).thenReturn(false, true);

        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        ResultSet metadataResultSet = mock(ResultSet.class);
        when(connection.prepareStatement(Sql.GET_CONVERSATION.get()))
                .thenReturn(metadataStatement);
        when(metadataStatement.executeQuery()).thenReturn(metadataResultSet);
        when(metadataResultSet.next()).thenReturn(true);
        when(metadataResultSet.getString("conversation_id")).thenReturn("CONV-1");
        when(metadataResultSet.getString("conversation_title")).thenReturn("Support Thread");
        when(metadataResultSet.getString("description")).thenReturn("Incident triage");
        when(metadataResultSet.getInt("retention_days")).thenReturn(21);
        when(metadataResultSet.getInt("conversation_length")).thenReturn(0);
        when(metadataResultSet.wasNull()).thenReturn(false, true);

        List<Conversation> conversations = new DefaultSelectAI(
                new SingleConnectionProvider(dbConnection)).listConversations();

        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).getConversationId()).isEqualTo("CONV-1");
        ConversationAttributes attributes = conversations.get(0).getConversationAttributes();
        assertThat(attributes.getTitle())
                .isEqualTo("Support Thread");
        assertThat(attributes.getDescription())
                .isEqualTo("Incident triage");
        assertThat(attributes.getRetentionDays())
                .isEqualTo(21);
        assertThat(attributes.getConversationLength())
                .isNull();
    }

    private static void assertClosedConnectionFailure(
            Executable operation, SQLException expectedCause) {
        SelectAIException exception = assertThrows(SelectAIException.class, operation);

        assertThat(exception)
                .hasCause(expectedCause);
        assertThat(exception.getErrorCode()).isEqualTo(expectedCause.getErrorCode());
        assertThat(exception.getSqlState()).isEqualTo(expectedCause.getSQLState());
    }

    private static DbConnectionConfig dbConnectionConfig() {
        return DbConnectionConfig.builder()
                .dbUser("admin")
                .dbPassword("password")
                .jdbcUrl("jdbc:oracle:thin:@example_high?TNS_ADMIN=/wallet")
                .build();
    }

    private static CredentialConfig credentialConfig() {
        return CredentialConfig.builder("OCI_CRED")
                .userOcid("user-ocid")
                .tenancyOcid("tenancy-ocid")
                .privateKey("private-key")
                .fingerprint("fingerprint")
                .build();
    }

    private static ProfileAttributes profileAttributes() {
        return ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .build();
    }

    private static VectorIndexConfig vectorIndexConfig() {
        return VectorIndexConfig.builder("RAG_IDX")
                .description("Product docs")
                .vectorIndexAttributes(VectorIndexAttributes.builder()
                        .profileName("RAG_PROFILE")
                        .location("https://object.example/docs")
                        .objectStorageCredentialName("OBJ_CRED")
                        .vectorDbProvider("oracle")
                        .build())
                .build();
    }

    private static ConversationAttributes conversationAttributes() {
        return ConversationAttributes.builder()
                .title("Support Thread")
                .description("Incident triage")
                .retentionDays(21)
                .build();
    }

    private static final class TestDriver implements Driver {
        private final Connection connectionToReturn;
        private int connectionCalls;

        private TestDriver(Connection connectionToReturn) {
            this.connectionToReturn = connectionToReturn;
        }

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            connectionCalls++;
            return connectionToReturn;
        }

        @Override
        public boolean acceptsURL(String url) {
            return "jdbc:oracle:thin:@selectai-test".equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger()
                throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Not supported in test driver");
        }
    }
}
