/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.Session;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SummaryParams;
import com.oracle.database.selectai.model.SyntheticDataBatchRequest;
import com.oracle.database.selectai.model.SyntheticDataObjectList;
import com.oracle.database.selectai.model.SyntheticDataParams;
import com.oracle.database.selectai.model.SyntheticDataSingleRequest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Reader;
import java.lang.reflect.Field;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProfileTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROFILE_ATTRIBUTES_SQL =
            "SELECT attribute_name, attribute_value "
                    + "FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILE_ATTRIBUTES "
                    + "WHERE profile_name = ?";

    @Mock
    private DbConnection dbConnection;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement profileStatement;
    @Mock
    private PreparedStatement attributesStatement;
    @Mock
    private ResultSet profileResultSet;
    @Mock
    private ResultSet attributesResultSet;
    @Mock
    private CallableStatement callableStatement;
    @Mock
    private Clob clob;

    /**
     * Test: Verifies list maps profile rows and fetches attributes.
     * Expected: One profile named PROFILE_1 is returned with its description, ENABLED status,
     * provider oci, and credential OCI_CRED.
     */
    @Test
    void listMapsProfileRowsAndFetchesAttributes() throws Exception {
        PreparedStatement listStatement = mock(PreparedStatement.class);
        PreparedStatement rowAttributesStatement = mock(PreparedStatement.class);
        PreparedStatement refreshedAttributesStatement = mock(PreparedStatement.class);
        ResultSet listResultSet = mock(ResultSet.class);
        ResultSet rowAttributesResultSet = mock(ResultSet.class);
        ResultSet refreshedAttributesResultSet = mock(ResultSet.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_PROFILES.get())).thenReturn(listStatement);
        when(connection.prepareStatement(PROFILE_ATTRIBUTES_SQL))
                .thenReturn(rowAttributesStatement, refreshedAttributesStatement);
        when(connection.prepareStatement(Sql.GET_PROFILE.get())).thenReturn(profileStatement);
        when(listStatement.executeQuery()).thenReturn(listResultSet);
        when(listResultSet.next()).thenReturn(true, false);
        when(listResultSet.getString("profile_name")).thenReturn("PROFILE_1");
        when(listResultSet.getString("description")).thenReturn("Profile description");
        when(listResultSet.getString("status")).thenReturn("ENABLED");
        when(rowAttributesStatement.executeQuery()).thenReturn(rowAttributesResultSet);
        when(rowAttributesResultSet.next()).thenReturn(true, true, false);
        when(rowAttributesResultSet.getString("attribute_name")).thenReturn("provider", "credential_name");
        when(rowAttributesResultSet.getString("attribute_value")).thenReturn("oci", "OCI_CRED");
        when(refreshedAttributesStatement.executeQuery()).thenReturn(refreshedAttributesResultSet);
        when(refreshedAttributesResultSet.next()).thenReturn(true, true, false);
        when(refreshedAttributesResultSet.getString("attribute_name"))
                .thenReturn("provider", "credential_name");
        when(refreshedAttributesResultSet.getString("attribute_value"))
                .thenReturn("oci", "OCI_CRED");
        when(profileStatement.executeQuery()).thenReturn(profileResultSet);
        when(profileResultSet.next()).thenReturn(true);
        when(profileResultSet.getString("profile_name")).thenReturn("PROFILE_1");
        when(profileResultSet.getString("status")).thenReturn("ENABLED");
        when(profileResultSet.getString("description")).thenReturn("Profile description");

        List<Profile> profiles = new DefaultSelectAI(new SingleConnectionProvider(dbConnection)).listProfiles();

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).getProfileName()).isEqualTo("PROFILE_1");
        assertThat(profiles.get(0).getStatus()).isEqualTo("ENABLED");
        assertThat(profiles.get(0).getDescription()).isEqualTo("Profile description");
        ProfileAttributes currentAttributes = profiles.get(0).getProfileAttributes();
        assertThat(currentAttributes.getProvider()).isEqualTo("oci");
        assertThat(currentAttributes.getCredentialName()).isEqualTo("OCI_CRED");
        verify(rowAttributesStatement).setString(1, "PROFILE_1");
    }

    /**
     * Test: Verifies profile creation performs only the supported Java-side checks.
     * Expected: Null attributes and a missing provider are rejected before JDBC execution;
     * missing and blank credential names remain available for database validation.
     */
    @Test
    void profileCreationValidatesOnlyJavaLevelRequiredInputsBeforeDatabaseCall() throws Exception {
        SingleConnectionProvider provider = new SingleConnectionProvider(dbConnection);

        assertThatThrownBy(() -> new DefaultProfile(
                provider, "PROFILE_NULL_ATTRIBUTES", (ProfileAttributes) null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileAttributes");

        DefaultProfile missingCredential = new DefaultProfile(
                provider, "PROFILE_NO_CREDENTIAL",
                ProfileAttributes.builder().provider("oci").build(), null, null);
        assertThat(missingCredential.getProfileAttributes().getCredentialName()).isNull();

        DefaultProfile blankCredential = new DefaultProfile(
                provider, "PROFILE_BLANK_CREDENTIAL",
                ProfileAttributes.builder()
                        .credentialName(" ")
                        .provider("oci")
                        .build(), null, null);
        assertThat(blankCredential.getProfileAttributes().getCredentialName()).isEqualTo(" ");

        assertThatThrownBy(() -> new DefaultProfile(
                provider, "PROFILE_NO_PROVIDER",
                ProfileAttributes.builder().credentialName("OCI_CRED").build(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");

    verifyNoInteractions(dbConnection);
}

/**
 * Test: Verifies CREATE_PROFILE SQL failures preserve database error details.
 * Expected: A SelectAIException contains the original SQLException, error code 942,
 * and SQL state 42000.
 */
@Test
void createWrapsSqlFailuresWithDatabaseDetails() throws Exception {
    DefaultProfile profile = new DefaultProfile(
            new SingleConnectionProvider(dbConnection),
            "PROFILE_CREATE_FAILURE",
            createProfileAttributes(),
            "description",
            null);
    SQLException sqlException = new SQLException("create failed", "42000", 942);
    when(dbConnection.getConnection()).thenReturn(connection);
    when(connection.prepareCall(Sql.CREATE_PROFILE.get())).thenReturn(callableStatement);
    when(callableStatement.execute()).thenThrow(sqlException);

    assertThatThrownBy(profile::create)
            .isInstanceOf(SelectAIException.class)
            .hasMessageContaining("CREATE_PROFILE")
            .hasCause(sqlException)
            .satisfies(error -> {
                SelectAIException selectAIException = (SelectAIException) error;
                assertThat(selectAIException.getErrorCode()).isEqualTo(942);
                assertThat(selectAIException.getSqlState()).isEqualTo("42000");
            });
}

    /**
     * Test: Verifies CREATE_PROFILE binds large attributes and description values as CLOB streams.
     * Expected: Both CLOB inputs preserve their complete 40,000-character payloads.
     */
    @Test
    void createBindsLargeClobAttributesAndDescriptionAsCharacterStreams() throws Exception {
        String instructions = "profile-instructions:" + "i".repeat(40_000);
        String description = "profile-description:" + "d".repeat(40_000);
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .additionalInstructions(instructions)
                .build();
        DefaultProfile profile = new DefaultProfile(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_LARGE_CLOB_CREATE",
                attributes,
                description,
                null);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_PROFILE.get())).thenReturn(callableStatement);

        assertThat(profile.create()).isTrue();

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
     * Test: Verifies CREATE_PROFILE preserves explicitly supplied empty attribute values.
     * Expected: Empty strings and empty lists remain in the attributes CLOB payload.
     */
    @Test
    void createPreservesExplicitEmptyAttributeValuesInPayload() throws Exception {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .additionalInstructions("")
                .stopTokens(List.of())
                .build();
        DefaultProfile profile = new DefaultProfile(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_EMPTY_VALUES_CREATE",
                attributes,
                null,
                null);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_PROFILE.get())).thenReturn(callableStatement);

        assertThat(profile.create()).isTrue();

        assertEmptyAttributeValuesPreserved(
                captureCharacterStream(callableStatement, 2, attributes.toJson()));
    }

    /**
     * Test: Verifies lifecycle methods bind profile name and execute procedures.
     * Expected: drop(true), enable(), and disable() return true and execute for PROFILE_1;
     * drop also binds force=1.
     */
    @Test
    void lifecycleMethodsBindProfileNameAndExecuteProcedures() throws Exception {
        DefaultProfile profile = loadedProfile();
        CallableStatement dropStatement = mock(CallableStatement.class);
        CallableStatement enableStatement = mock(CallableStatement.class);
        CallableStatement disableStatement = mock(CallableStatement.class);
        when(connection.prepareCall(Sql.DROP_PROFILE.get())).thenReturn(dropStatement);
        when(connection.prepareCall(Sql.ENABLE_PROFILE.get())).thenReturn(enableStatement);
        when(connection.prepareCall(Sql.DISABLE_PROFILE.get())).thenReturn(disableStatement);

        assertThat(profile.drop(true)).isTrue();
        assertThat(profile.enable()).isTrue();
        assertThat(profile.disable()).isTrue();

        verify(dropStatement).setString(1, "PROFILE_1");
        verify(dropStatement).setInt(2, 1);
        verify(dropStatement).execute();
        verify(enableStatement).setString(1, "PROFILE_1");
        verify(enableStatement).execute();
        verify(disableStatement).setString(1, "PROFILE_1");
        verify(disableStatement).execute();
    }

    /**
     * Test: Verifies generate binds prompt profile and action then returns clob text.
     * Expected: The response is "select * from emp", and GENERATE receives the prompt,
     * PROFILE_1, showsql, and SQL NULL for attributes and params.
     */
    @Test
    void generateBindsPromptProfileAndActionThenReturnsClobText() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(17L);
        when(clob.getSubString(1, 17)).thenReturn("select * from emp");

        String response = profile.generate("show employees", GenerateAction.showsql);

        assertThat(response).isEqualTo("select * from emp");
        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verifyCharacterStream(callableStatement, 2, "show employees");
        verify(callableStatement).setString(3, "PROFILE_1");
        verify(callableStatement).setString(4, "showsql");
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).setNull(6, Types.CLOB);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies generate binds embedding action attributes and conversation params.
     * Expected: The response is "[]"; the attributes JSON contains model, embedding_model, and
     * conversation_length,
     * and the params JSON contains conversation_id CONV-123.
     */
    @Test
    void generateBindsEmbeddingActionAttributesAndConversationParams() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(2L);
        when(clob.getSubString(1, 2)).thenReturn("[]");

        ProfileAttributes attributes = ProfileAttributes.builder()
                .model("cohere.command-r-plus")
                .embeddingModel("cohere.embed-english-v3.0")
                .conversationLength(4)
                .build();
        GenerateParams params = GenerateParams.builder()
                .conversationId("CONV-123")
                .build();

        assertThat(profile.generate(
                "embed this text",
                GenerateAction.embedding,
                attributes,
                params)).isEqualTo("[]");

        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verifyCharacterStream(callableStatement, 2, "embed this text");
        verify(callableStatement).setString(3, "PROFILE_1");
        verify(callableStatement).setString(4, "embedding");

        String attributesPayload = captureCharacterStream(callableStatement, 5, attributes.toJson());
        String paramsPayload = captureCharacterStream(callableStatement, 6, params.toJson());

        JsonNode attributesJson = MAPPER.readTree(attributesPayload);
        assertThat(attributesJson.size()).isEqualTo(3);
        assertThat(attributesJson.get("model").asText()).isEqualTo("cohere.command-r-plus");
        assertThat(attributesJson.get("embedding_model").asText())
                .isEqualTo("cohere.embed-english-v3.0");
        assertThat(attributesJson.get("conversation_length").asInt()).isEqualTo(4);

        JsonNode paramsJson = MAPPER.readTree(paramsPayload);
        assertThat(paramsJson.size()).isEqualTo(1);
        assertThat(paramsJson.get("conversation_id").asText()).isEqualTo("CONV-123");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies GENERATE binds the documented summarize and translate actions.
     * Expected: Each action is passed to JDBC using its exact lower-case database value.
     */
    @Test
    void generateBindsSummarizeAndTranslateActions() throws Exception {
        DefaultProfile profile = loadedProfile();
        CallableStatement summarizeStatement = generateStatementReturning("summary response");
        CallableStatement translateStatement = generateStatementReturning("Danke");
        when(connection.prepareCall(Sql.GENERATE.get()))
                .thenReturn(summarizeStatement, translateStatement);

        assertThat(profile.generate("summarize this content", GenerateAction.summarize))
                .isEqualTo("summary response");
        assertThat(profile.generate("Thank you", GenerateAction.translate))
                .isEqualTo("Danke");

        verifyCharacterStream(summarizeStatement, 2, "summarize this content");
        verify(summarizeStatement).setString(3, "PROFILE_1");
        verify(summarizeStatement).setString(4, "summarize");
        verify(summarizeStatement).setNull(5, Types.CLOB);
        verify(summarizeStatement).setNull(6, Types.CLOB);
        verifyCharacterStream(translateStatement, 2, "Thank you");
        verify(translateStatement).setString(3, "PROFILE_1");
        verify(translateStatement).setString(4, "translate");
        verify(translateStatement).setNull(5, Types.CLOB);
        verify(translateStatement).setNull(6, Types.CLOB);
    }

    /**
     * Test: Verifies GENERATE binds a large prompt and request-level attributes as CLOB streams.
     * Expected: Both CLOB inputs preserve their complete payloads at the documented positions.
     */
    @Test
    void generateBindsLargeClobPromptAndAttributesAsCharacterStreams() throws Exception {
        DefaultProfile profile = loadedProfile();
        CallableStatement statement = generateStatementReturning("showprompt response");
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(statement);
        String prompt = "generate-prompt:" + "p".repeat(40_000);
        ProfileAttributes attributes = ProfileAttributes.builder()
                .additionalInstructions("generate-attributes:" + "a".repeat(40_000))
                .build();

        assertThat(profile.generate(prompt, GenerateAction.showprompt, attributes))
                .isEqualTo("showprompt response");

        ArgumentCaptor<Reader> promptReader = ArgumentCaptor.forClass(Reader.class);
        ArgumentCaptor<Reader> attributesReader = ArgumentCaptor.forClass(Reader.class);
        verify(statement, org.mockito.Mockito.never())
                .setString(2, prompt);
        verify(statement).setCharacterStream(eq(2), promptReader.capture(),
                eq(prompt.length()));
        verify(statement).setCharacterStream(eq(5), attributesReader.capture(),
                eq(attributes.toJson().length()));
        assertThat(readAll(promptReader.getValue())).isEqualTo(prompt);
        assertThat(readAll(attributesReader.getValue())).isEqualTo(attributes.toJson());
    }

    /**
     * Test: Verifies generate with attributes only binds null params.
     * Expected: The attributes JSON is bound at parameter 5 and SQL NULL is bound at parameter 6.
     */
    @Test
    void generateWithAttributesOnlyBindsNullParams() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);

        ProfileAttributes attributes = ProfileAttributes.builder()
                .model("request-model")
                .build();

        profile.generate("show employees", GenerateAction.showsql, attributes);

        verifyCharacterStream(callableStatement, 5, attributes.toJson());
        verify(callableStatement).setNull(6, Types.CLOB);
    }

    /**
     * Test: Verifies request-level GENERATE attributes preserve explicitly supplied empty values.
     * Expected: Empty strings and empty lists remain in the attributes CLOB payload.
     */
    @Test
    void generatePreservesExplicitEmptyRequestAttributeValuesInPayload() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);

        ProfileAttributes attributes = ProfileAttributes.builder()
                .additionalInstructions("")
                .stopTokens(List.of())
                .build();

        profile.generate("show employees", GenerateAction.showsql, attributes);

        assertEmptyAttributeValuesPreserved(
                captureCharacterStream(callableStatement, 5, attributes.toJson()));
    }

    /**
     * Test: Verifies generate with params only binds null attributes.
     * Expected: SQL NULL is bound for attributes at parameter 5 and the conversation params JSON
     * is bound at parameter 6.
     */
    @Test
    void generateWithParamsOnlyBindsNullAttributes() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);

        GenerateParams params = GenerateParams.builder()
                .conversationId("CONV-123")
                .build();

        profile.generate("continue conversation", GenerateAction.chat, params);

        verify(callableStatement).setNull(5, Types.CLOB);
        verifyCharacterStream(callableStatement, 6, params.toJson());
    }

    /**
     * Test: Verifies GenerateParams overloads on action shortcuts delegate to GENERATE.
     * Expected: All six shortcut overloads bind the matching action, SQL NULL attributes, and
     * the caller-supplied params payload at the documented GENERATE params position.
     */
    @Test
    void actionShortcutsWithGenerateParamsDelegateToGenerateWithExpectedActions() throws Exception {
        DefaultProfile profile = loadedProfile();
        CallableStatement runsqlStatement = generateStatementReturning("runsql response");
        CallableStatement showsqlStatement = generateStatementReturning("showsql response");
        CallableStatement explainsqlStatement = generateStatementReturning("explainsql response");
        CallableStatement narrateStatement = generateStatementReturning("narrate response");
        CallableStatement showpromptStatement = generateStatementReturning("showprompt response");
        CallableStatement chatStatement = generateStatementReturning("chat response");
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(runsqlStatement, showsqlStatement,
                explainsqlStatement, narrateStatement, showpromptStatement, chatStatement);
        GenerateParams params = GenerateParams.builder()
                .conversationId("CONV-123")
                .build();

        assertThat(profile.runsql("how many customers", params)).isEqualTo("runsql response");
        assertThat(profile.showsql("how many customers", params)).isEqualTo("showsql response");
        assertThat(profile.explainsql("how many customers", params)).isEqualTo("explainsql response");
        assertThat(profile.narrate("how many customers", params)).isEqualTo("narrate response");
        assertThat(profile.showprompt("how many customers", params)).isEqualTo("showprompt response");
        assertThat(profile.chat("how many customers", params)).isEqualTo("chat response");

        verifyGenerateCallWithParams(runsqlStatement, GenerateAction.runsql, params);
        verifyGenerateCallWithParams(showsqlStatement, GenerateAction.showsql, params);
        verifyGenerateCallWithParams(explainsqlStatement, GenerateAction.explainsql, params);
        verifyGenerateCallWithParams(narrateStatement, GenerateAction.narrate, params);
        verifyGenerateCallWithParams(showpromptStatement, GenerateAction.showprompt, params);
        verifyGenerateCallWithParams(chatStatement, GenerateAction.chat, params);
        verify(connection, times(6)).prepareCall(Sql.GENERATE.get());
    }

    /**
     * Test: Verifies generate wraps sql failures.
     * Expected: A SelectAIException containing GENERATE, error code 942, SQL state 42000,
     * and the original SQLException cause is thrown.
     */
    @Test
    void generateWrapsSqlFailures() throws Exception {
        DefaultProfile profile = loadedProfile();
        SQLException sqlException = new SQLException("generate failed", "42000", 942);
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> profile.generate("show employees", GenerateAction.runsql))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("GENERATE")
                .hasCause(sqlException)
                .satisfies(error -> {
                    SelectAIException selectAIException = (SelectAIException) error;
                    assertThat(selectAIException.getErrorCode()).isEqualTo(942);
                    assertThat(selectAIException.getSqlState()).isEqualTo("42000");
                });
    }

    /**
     * Test: Verifies generate failure diagnostics do not expose the raw prompt.
     * Expected: The prompt marker is absent from the SDK exception text and captured logs.
     */
    @Test
    void generateFailureDoesNotExposePromptInExceptionOrLogs() throws Exception {
        DefaultProfile profile = loadedProfile();
        String sensitivePrompt = "SEC_PROMPT_MARKER generate should not be logged";
        SQLException sqlException = new SQLException("database rejected request", "42000", 942);
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        LogCapture.CapturedFailure failure = LogCapture.captureFailure(
                () -> profile.generate(sensitivePrompt, GenerateAction.chat));

        assertThat(failure.throwable())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("GENERATE")
                .hasCause(sqlException);
        LogCapture.assertFailureDoesNotExpose(failure, sensitivePrompt, "SEC_PROMPT_MARKER");
    }

    /**
     * Test: Verifies generate returns null when database returns null clob.
     * Expected: The response is null and the GENERATE callable statement is executed.
     */
    @Test
    void generateReturnsNullWhenDatabaseReturnsNullClob() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(null);

        String response = profile.generate("show employees", GenerateAction.showsql);

        assertThat(response).isNull();
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies generate rejects null action before database call.
     * Expected: IllegalArgumentException is thrown and GENERATE is not prepared.
     */
    @Test
    void generateRejectsNullActionBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.generate("show employees", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generateAction");

        verify(connection, never()).prepareCall(Sql.GENERATE.get());
    }

    /**
     * Test: Verifies prompt operations reject empty prompt before database call.
     * Expected: Every prompt operation throws IllegalArgumentException for an empty prompt
     * without preparing GENERATE.
     */
    @Test
    void promptOperationsRejectEmptyPromptBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.chat(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.narrate(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.showsql(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.showprompt(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.runsql(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.explainsql(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.generate("", GenerateAction.chat))
                .isInstanceOf(IllegalArgumentException.class);

        verify(connection, never()).prepareCall(Sql.GENERATE.get());
    }

    /**
     * Test: Verifies prompt operations reject null prompt before database call.
     * Expected: Every prompt operation throws IllegalArgumentException for a null prompt
     * without preparing GENERATE.
     */
    @Test
    void promptOperationsRejectNullPromptBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.chat(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.narrate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.showsql(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.showprompt(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.runsql(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.explainsql(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.generate(null, GenerateAction.chat))
                .isInstanceOf(IllegalArgumentException.class);

        verify(connection, never()).prepareCall(Sql.GENERATE.get());
    }

    /**
     * Test: Verifies chatSession rejects a missing Conversation before any database call.
     * Expected: Null conversation is rejected as a mandatory Java API parameter.
     */
    @Test
    void chatSessionRejectsNullConversationBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.chatSession(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversation");

        verify(connection, never()).prepareCall(any(String.class));
    }

    /**
     * Test: Verifies chatSession uses an existing conversation ID without creating a new one.
     * Expected: A Session is returned, Conversation.create() is not called, and the default
     * deleteOnClose=false close path does not drop the conversation.
     */
    @Test
    void chatSessionUsesExistingConversationWithoutCreate() throws Exception {
        DefaultProfile profile = loadedProfile();
        Conversation conversation = mock(Conversation.class);
        when(conversation.getConversationId()).thenReturn("CONV-123");

        Session session = profile.chatSession(conversation);
        session.close();

        assertThat(session).isInstanceOf(DefaultSession.class);
        verify(conversation, never()).create();
        verify(conversation, never()).drop(anyBoolean());
        verify(connection, never()).prepareCall(any(String.class));
    }

    /**
     * Test: Verifies chatSession creates a configured conversation when no ID is present.
     * Expected: Conversation.create() is called once and deleteOnClose=true is propagated to
     * the returned Session.
     */
    @Test
    void chatSessionCreatesConversationWhenIdMissing() throws Exception {
        DefaultProfile profile = loadedProfile();
        Conversation conversation = mock(Conversation.class);
        when(conversation.getConversationId()).thenReturn(null, "CONV-NEW");
        when(conversation.create()).thenReturn("CONV-NEW");

        Session session = profile.chatSession(conversation, true);
        session.close();

        assertThat(session).isInstanceOf(DefaultSession.class);
        verify(conversation).create();
        verify(conversation).drop(true);
        verify(connection, never()).prepareCall(any(String.class));
    }

    /**
     * Test: Verifies chatSession surfaces a failed conversation create operation.
     * Expected: The SelectAIException from Conversation.create() is propagated unchanged.
     */
    @Test
    void chatSessionPropagatesConversationCreateFailure() throws Exception {
        DefaultProfile profile = loadedProfile();
        Conversation conversation = mock(Conversation.class);
        SelectAIException failure = new SelectAIException("create failed");
        when(conversation.getConversationId()).thenReturn(null);
        when(conversation.create()).thenThrow(failure);

        assertThatThrownBy(() -> profile.chatSession(conversation)).isSameAs(failure);
    }

    /**
     * Test: Verifies chatSession rejects a create operation that returns no usable ID.
     * Expected: A SelectAIException is thrown before constructing a Session with a blank
     * conversation ID.
     */
    @Test
    void chatSessionRejectsBlankCreatedConversationId() throws Exception {
        DefaultProfile profile = loadedProfile();
        Conversation conversation = mock(Conversation.class);
        when(conversation.getConversationId()).thenReturn(" ");
        when(conversation.create()).thenReturn(" ");

        assertThatThrownBy(() -> profile.chatSession(conversation))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Conversation create did not return a conversation ID");
    }

    /**
     * Test: Verifies prompts submitted through a public Session are validated before JDBC.
     * Expected: All six Session prompt operations reject null or blank prompt text without
     * preparing a GENERATE call.
     */
    @Test
    void chatSessionPromptOperationsRejectInvalidPromptBeforeGenerateCall() throws Exception {
        DefaultProfile profile = loadedProfile();
        Conversation conversation = mock(Conversation.class);
        when(conversation.getConversationId()).thenReturn("CONV-123");
        Session session = profile.chatSession(conversation);

        assertThatThrownBy(() -> session.chat(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.chat(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.narrate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.narrate(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.runsql(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.runsql(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.explainsql(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.explainsql(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.showsql(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.showsql(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.showprompt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> session.showprompt(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        verify(connection, never()).prepareCall(Sql.GENERATE.get());
    }

    /**
     * Test: Verifies schema level profile rejects profile specific operations before database call.
     * Expected: The schema-level profile has no name, every profile-bound operation throws
     * IllegalStateException, and the database is not accessed.
     */
    @Test
    void schemaLevelProfileRejectsProfileSpecificOperationsBeforeDatabaseCall() {
        DefaultProfile schemaLevelProfile = new DefaultProfile(new SingleConnectionProvider(dbConnection));

        assertThat(schemaLevelProfile.getProfileName()).isNull();
        assertThatThrownBy(() -> schemaLevelProfile.generate("show employees", GenerateAction.chat))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.chatSession(mock(Conversation.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.setAttribute("model", "gpt-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.setAttributes(createProfileAttributes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(schemaLevelProfile::getProfileAttributes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.drop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(schemaLevelProfile::enable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(schemaLevelProfile::disable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.feedback(Feedback.builder()
                .sqlText("SELECT 1")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.generateSyntheticData(
                SyntheticDataSingleRequest.builder("EMPLOYEES").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.generateSyntheticData(
                SyntheticDataBatchRequest.builder()
                        .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES").build())
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.summarize("content", null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");
        assertThatThrownBy(() -> schemaLevelProfile.translate("Hello", "English", "French"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile-bound");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies configured profile rejects database operations before create.
     * Expected: Every database operation throws its documented create()-required
     * IllegalStateException and the database is not accessed.
     */
    @Test
    void configuredProfileRejectsDatabaseOperationsBeforeCreate() {
        DefaultProfile pendingProfile = new DefaultProfile(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_PENDING",
                createProfileAttributes(),
                null,
                null);

        assertThatThrownBy(() -> pendingProfile.generate("show employees", GenerateAction.chat))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("generate requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.chat("show employees"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("generate requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.chatSession(mock(Conversation.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("chatSession requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.setAttribute("model", "gpt-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("setAttribute requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.setAttributes(createProfileAttributes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("setAttributes requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.translate("Hello", "en", "de"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("translate requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.drop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created profile; call create() first");
        assertThatThrownBy(pendingProfile::enable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("enable requires a created profile; call create() first");
        assertThatThrownBy(pendingProfile::disable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disable requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.feedback(Feedback.builder()
                .sqlText("SELECT 1")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("feedback requires a created profile; call create() first");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies both synthetic-data overloads reject a configured profile
     * before it has been created.
     * Expected: Each overload throws the documented create-required exception
     * without accessing the database.
     */
    @Test
    void configuredProfileRejectsSyntheticDataBeforeCreate() {
        DefaultProfile pendingProfile = new DefaultProfile(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_PENDING_SYNTHETIC",
                createProfileAttributes(),
                null,
                null);

        SyntheticDataSingleRequest singleRequest = SyntheticDataSingleRequest.builder("EMPLOYEES")
                .recordCount(1)
                .build();
        SyntheticDataBatchRequest batchRequest = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                        .recordCount(1)
                        .build())
                .build();

        assertThatThrownBy(() -> pendingProfile.generateSyntheticData(singleRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("generateSyntheticData requires a created profile; call create() first");
        assertThatThrownBy(() -> pendingProfile.generateSyntheticData(batchRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("generateSyntheticData requires a created profile; call create() first");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies configured profile returns pending status without database call.
     * Expected: ProfileStatus.DISABLED is returned and the database is not accessed.
     */
    @Test
    void configuredProfileReturnsPendingStatusWithoutDatabaseCall() throws Exception {
        DefaultProfile pendingProfile = new DefaultProfile(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_PENDING_STATUS",
                createProfileAttributes(),
                "Pending profile",
                ProfileStatus.DISABLED);

        assertThat(pendingProfile.getStatus()).isEqualTo(ProfileStatus.DISABLED.getValue());
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies configured profile returns pending attributes without database call.
     * Expected: The configured ProfileAttributes instance is returned and the database is not accessed.
     */
    @Test
    void configuredProfileReturnsPendingAttributesWithoutDatabaseCall() throws Exception {
        ProfileAttributes configuredAttributes = createProfileAttributes();
        DefaultProfile pendingProfile = new DefaultProfile(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_PENDING_ATTRIBUTES",
                configuredAttributes,
                null,
                null);

        assertThat(pendingProfile.getProfileAttributes()).isSameAs(configuredAttributes);
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies a database-backed profile refreshes status and description.
     * Expected: getStatus() queries current metadata and exposes the refreshed database values.
     */
    @Test
    void getStatusRefreshesCurrentDatabaseMetadata() throws Exception {
        DefaultProfile profile = DefaultProfile.fromMetadata(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_1", createProfileAttributes(), "Initial description", "ENABLED");
        PreparedStatement refreshedProfileStatement = mock(PreparedStatement.class);
        ResultSet refreshedProfileResultSet = mock(ResultSet.class);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_PROFILE.get())).thenReturn(refreshedProfileStatement);
        when(refreshedProfileStatement.executeQuery()).thenReturn(refreshedProfileResultSet);
        when(refreshedProfileResultSet.next()).thenReturn(true);
        when(refreshedProfileResultSet.getString("profile_name")).thenReturn("PROFILE_1");
        when(refreshedProfileResultSet.getString("status")).thenReturn("DISABLED");
        when(refreshedProfileResultSet.getString("description")).thenReturn("Updated description");

        assertThat(profile.getStatus()).isEqualTo("DISABLED");
        assertThat(profile.getDescription()).isEqualTo("Updated description");
        verify(refreshedProfileStatement).setString(1, "PROFILE_1");
    }

    /**
     * Test: Verifies generate synthetic data preserves blank owner and prompt.
     * Expected: The request succeeds and sends both blank owner and prompt strings unchanged.
     */
    @Test
    void generateSyntheticDataPreservesBlankOwnerAndPrompt() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get()))
                .thenReturn(callableStatement);

        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder("people")
                .ownerName(" ")
                .userPrompt(" ")
                .build();

        assertThat(profile.generateSyntheticData(request)).isTrue();

        verify(callableStatement).setString(3, " ");
        verifyCharacterStream(callableStatement, 5, " ");
    }

    /**
     * Test: Verifies single synthetic data binds all request fields.
     * Expected: The request succeeds with all request fields and all four synthetic-data params
     * bound, then executes and closes the statement.
     */
    @Test
    void singleSyntheticDataBindsAllRequestFields() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get()))
                .thenReturn(callableStatement);
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(10)
                .tableStatistics(true)
                .priority("HIGH")
                .comments(false)
                .build();
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder("EMPLOYEES")
                .ownerName("HR")
                .recordCount(7)
                .userPrompt("Use realistic salary bands")
                .params(params)
                .build();

        assertThat(profile.generateSyntheticData(request)).isTrue();

        verify(connection).prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get());
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setString(2, "EMPLOYEES");
        verify(callableStatement).setString(3, "HR");
        verify(callableStatement).setInt(4, 7);
        verifyCharacterStream(callableStatement, 5, "Use realistic salary bands");
        String paramsPayloadJson = captureCharacterStream(callableStatement, 6, params.toJson());
        JsonNode paramsPayload = MAPPER.readTree(paramsPayloadJson);
        assertThat(paramsPayload).hasSize(4);
        assertThat(paramsPayload.get("sample_rows").asInt()).isEqualTo(10);
        assertThat(paramsPayload.get("table_statistics").asBoolean()).isTrue();
        assertThat(paramsPayload.get("priority").asText()).isEqualTo("HIGH");
        assertThat(paramsPayload.get("comments").asBoolean()).isFalse();
        verify(callableStatement).execute();
        verify(callableStatement).close();
    }

    /**
     * Test: Verifies single-object synthetic data binds a large user prompt as a CLOB stream.
     * Expected: The complete prompt is preserved at the user_prompt bind position.
     */
    @Test
    void singleSyntheticDataBindsLargeUserPromptAsCharacterStream() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get()))
                .thenReturn(callableStatement);
        String userPrompt = "synthetic-prompt:" + "p".repeat(40_000);
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder("EMPLOYEES")
                .userPrompt(userPrompt)
                .build();

        assertThat(profile.generateSyntheticData(request)).isTrue();

        ArgumentCaptor<Reader> promptReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(5), promptReader.capture(),
                eq(userPrompt.length()));
        assertThat(readAll(promptReader.getValue())).isEqualTo(userPrompt);
    }

    /**
     * Test: Verifies single synthetic data binds unset optional fields as sql null.
     * Expected: The request succeeds and SQL NULL is bound for owner, record count, prompt,
     * and optional params.
     */
    @Test
    void singleSyntheticDataBindsUnsetOptionalFieldsAsSqlNull() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get()))
                .thenReturn(callableStatement);

        assertThat(profile.generateSyntheticData(
                SyntheticDataSingleRequest.builder("EMPLOYEES").build())).isTrue();

        verify(callableStatement).setNull(3, Types.VARCHAR);
        verify(callableStatement).setNull(4, Types.INTEGER);
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).setNull(6, Types.CLOB);
    }

    /**
     * Test: Verifies batch synthetic data binds object list and params.
     * Expected: The request succeeds with both object entries and the LOW-priority params JSON
     * bound, then executes and closes the statement.
     */
    @Test
    void batchSyntheticDataBindsObjectListAndParams() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_MULTI.get()))
                .thenReturn(callableStatement);
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                        .recordCount(7)
                        .build())
                .addObject(SyntheticDataObjectList.builder("HR", "DEPARTMENTS")
                        .recordCountPercentage(50)
                        .build())
                .params(SyntheticDataParams.builder().priority("LOW").build())
                .build();

        assertThat(profile.generateSyntheticData(request)).isTrue();

        verify(connection).prepareCall(Sql.GENERATE_SYNTHETIC_DATA_MULTI.get());
        verify(callableStatement).setString(1, "PROFILE_1");
        String objectListPayloadJson = captureCharacterStream(callableStatement, 2,
                request.getObjectListJson());
        String paramsPayloadJson = captureCharacterStream(callableStatement, 3,
                request.getParamsJson());

        JsonNode objectListPayload = MAPPER.readTree(objectListPayloadJson);
        assertThat(objectListPayload).hasSize(2);
        assertThat(objectListPayload.get(0)).hasSize(3);
        assertThat(objectListPayload.get(0).get("owner").asText()).isEqualTo("HR");
        assertThat(objectListPayload.get(0).get("name").asText()).isEqualTo("EMPLOYEES");
        assertThat(objectListPayload.get(0).get("record_count").asInt()).isEqualTo(7);
        assertThat(objectListPayload.get(1)).hasSize(3);
        assertThat(objectListPayload.get(1).get("owner").asText()).isEqualTo("HR");
        assertThat(objectListPayload.get(1).get("name").asText()).isEqualTo("DEPARTMENTS");
        assertThat(objectListPayload.get(1).get("record_count_percentage").asInt()).isEqualTo(50);

        JsonNode paramsPayload = MAPPER.readTree(paramsPayloadJson);
        assertThat(paramsPayload.get("priority").asText()).isEqualTo("LOW");
        assertThat(paramsPayload).hasSize(1);
        verify(callableStatement).execute();
        verify(callableStatement).close();
    }

    /**
     * Test: Verifies batch synthetic data binds an object-list JSON payload larger than the
     * PL/SQL VARCHAR2 bind limit as a CLOB stream.
     * Expected: The complete object-list JSON is preserved at the object_list bind position.
     */
    @Test
    void batchSyntheticDataBindsLargeObjectListAsCharacterStream() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_MULTI.get()))
                .thenReturn(callableStatement);
        SyntheticDataBatchRequest.Builder requestBuilder = SyntheticDataBatchRequest.builder();
        for (int i = 0; i < 1_000; i++) {
            requestBuilder.addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES")
                    .recordCount(1)
                    .build());
        }
        SyntheticDataBatchRequest request = requestBuilder.build();

        assertThat(request.getObjectListJson().length()).isGreaterThan(32_767);
        assertThat(profile.generateSyntheticData(request)).isTrue();

        ArgumentCaptor<Reader> objectListReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), objectListReader.capture(),
                eq(request.getObjectListJson().length()));
        assertThat(readAll(objectListReader.getValue())).isEqualTo(request.getObjectListJson());
    }

    /**
     * Test: Verifies batch synthetic data binds unset params as sql null.
     * Expected: The request succeeds, binds the object-list JSON, and binds SQL NULL for params.
     */
    @Test
    void batchSyntheticDataBindsUnsetParamsAsSqlNull() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_MULTI.get()))
                .thenReturn(callableStatement);
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES").build())
                .build();

        assertThat(profile.generateSyntheticData(request)).isTrue();

        verifyCharacterStream(callableStatement, 2, request.getObjectListJson());
        verify(callableStatement).setNull(3, Types.CLOB);
    }

    /**
     * Test: Verifies action shortcuts delegate to generate with expected actions.
     * Expected: All six shortcuts return their configured responses and invoke GENERATE once
     * with each corresponding action.
     */
    @Test
    void actionShortcutsDelegateToGenerateWithExpectedActions() throws Exception {
        DefaultProfile profile = loadedProfile();
        CallableStatement runsqlStatement = generateStatementReturning("runsql response");
        CallableStatement showsqlStatement = generateStatementReturning("showsql response");
        CallableStatement explainsqlStatement = generateStatementReturning("explainsql response");
        CallableStatement narrateStatement = generateStatementReturning("narrate response");
        CallableStatement showpromptStatement = generateStatementReturning("showprompt response");
        CallableStatement chatStatement = generateStatementReturning("chat response");
        when(connection.prepareCall(Sql.GENERATE.get())).thenReturn(runsqlStatement, showsqlStatement,
                explainsqlStatement, narrateStatement, showpromptStatement, chatStatement);

        assertThat(profile.runsql("how many customers")).isEqualTo("runsql response");
        assertThat(profile.showsql("how many customers")).isEqualTo("showsql response");
        assertThat(profile.explainsql("how many customers")).isEqualTo("explainsql response");
        assertThat(profile.narrate("how many customers")).isEqualTo("narrate response");
        assertThat(profile.showprompt("how many customers")).isEqualTo("showprompt response");
        assertThat(profile.chat("how many customers")).isEqualTo("chat response");

        verifyGenerateCall(runsqlStatement, GenerateAction.runsql);
        verifyGenerateCall(showsqlStatement, GenerateAction.showsql);
        verifyGenerateCall(explainsqlStatement, GenerateAction.explainsql);
        verifyGenerateCall(narrateStatement, GenerateAction.narrate);
        verifyGenerateCall(showpromptStatement, GenerateAction.showprompt);
        verifyGenerateCall(chatStatement, GenerateAction.chat);
        verify(connection, times(6)).prepareCall(Sql.GENERATE.get());
    }

    /**
     * Test: Verifies set attribute overloads bind values and reload current attributes.
     * Expected: All six overload calls return true, bind their value types including SQL NULL
     * and a character stream, and the reloaded attributes contain the updated values.
     */
    @Test
    void setAttributeOverloadsBindValuesAndReloadCurrentAttributes() throws Exception {
        DefaultProfile profile = loadedProfile();
        CallableStatement stringStatement = mock(CallableStatement.class);
        CallableStatement nullStatement = mock(CallableStatement.class);
        CallableStatement booleanStatement = mock(CallableStatement.class);
        CallableStatement integerStatement = mock(CallableStatement.class);
        CallableStatement floatStatement = mock(CallableStatement.class);
        CallableStatement largeValueStatement = mock(CallableStatement.class);
        when(connection.prepareCall(Sql.SET_ATTRIBUTE.get())).thenReturn(stringStatement, nullStatement,
                booleanStatement, integerStatement, floatStatement, largeValueStatement);

        assertThat(profile.setAttribute("model", "cohere.command-r-plus")).isTrue();
        assertThat(profile.setAttribute("target_language", (String) null)).isTrue();
        assertThat(profile.setAttribute("comments", true)).isTrue();
        assertThat(profile.setAttribute("max_tokens", 512)).isTrue();
        assertThat(profile.setAttribute("temperature", 0.4f)).isTrue();
        String largeValue = "x".repeat(40_000);
        assertThat(profile.setAttribute("custom_large_attribute", largeValue)).isTrue();

        verify(stringStatement).setString(1, "PROFILE_1");
        verify(stringStatement).setString(2, "model");
        verifyCharacterStream(stringStatement, 3, "cohere.command-r-plus");
        verify(nullStatement).setNull(3, Types.CLOB);
        verify(booleanStatement).setBoolean(3, true);
        verify(integerStatement).setInt(3, 512);
        verify(floatStatement).setFloat(3, 0.4f);
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(largeValueStatement).setCharacterStream(eq(3), readerCaptor.capture(), eq(largeValue.length()));
        char[] buffer = new char[largeValue.length()];
        assertThat(readerCaptor.getValue().read(buffer)).isEqualTo(largeValue.length());
        assertThat(new String(buffer)).isEqualTo(largeValue);
        verify(connection, times(6)).prepareCall(Sql.SET_ATTRIBUTE.get());

        when(attributesResultSet.next()).thenReturn(true, true, true, true, false);
        when(attributesResultSet.getString("attribute_name"))
                .thenReturn("model", "comments", "max_tokens", "temperature");
        when(attributesResultSet.getString("attribute_value"))
                .thenReturn("cohere.command-r-plus", "true", "512", "0.4");

        ProfileAttributes currentAttributes = profile.getProfileAttributes();
        assertThat(currentAttributes.getModel()).isEqualTo("cohere.command-r-plus");
        assertThat(currentAttributes.getComments()).isTrue();
        assertThat(currentAttributes.getMaxTokens()).isEqualTo(512);
        assertThat(currentAttributes.getTemperature()).isEqualTo(0.4);
    }

    /**
     * Test: Verifies setAttribute local state normalization is independent of default locale.
     * Expected: Updating OCI_APIFORMAT under Turkish locale updates the typed local attribute
     * and does not leave the original upper-case key as a custom attribute.
     */
    @Test
    void setAttributeLocalSyncUsesLocaleRoot() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            DefaultProfile profile = loadedProfile();
            when(connection.prepareCall(Sql.SET_ATTRIBUTE.get())).thenReturn(callableStatement);

            assertThat(profile.setAttribute("OCI_APIFORMAT", "generic")).isTrue();

            ProfileAttributes localAttributes = localProfileAttributes(profile);
            assertThat(localAttributes.getOciApiformat()).isEqualTo("GENERIC");
            assertThat(localAttributes.getCustomAttributes()).doesNotContainKey("OCI_APIFORMAT");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Test: Verifies attribute overloads reject invalid names and null numeric values.
     * Expected: Validation occurs before SET_ATTRIBUTE is prepared for each invalid request.
     */
    @Test
    void setAttributeRejectsInvalidNamesAndNullNumericValuesBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.setAttribute(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributeName");
        assertThatThrownBy(() -> profile.setAttribute(" ", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributeName");
        assertThatThrownBy(() -> profile.setAttribute("max_tokens", (Integer) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributeValue");
        assertThatThrownBy(() -> profile.setAttribute("temperature", (Float) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributeValue");

        verify(connection, never()).prepareCall(Sql.SET_ATTRIBUTE.get());
    }

    /**
     * Test: Verifies set attributes binds profile payload.
     * Expected: The call returns true, binds PROFILE_1 and a payload containing the configured
     * credential, provider, model, and max_tokens, and executes.
     */
    @Test
    void setAttributesBindsProfilePayload() throws Exception {
        DefaultProfile profile = loadedProfile();
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .model("cohere.command-r-plus")
                .comments(true)
                .maxTokens(768)
                .sourceLanguage("English")
                .targetLanguage("French")
                .build();
        when(connection.prepareCall(Sql.SET_ATTRIBUTES.get())).thenReturn(callableStatement);

        assertThat(profile.setAttributes(attributes)).isTrue();

        verify(callableStatement).setString(1, "PROFILE_1");
        String payloadJson = captureCharacterStream(callableStatement, 2, attributes.toJson());
        assertThat(payloadJson)
                .contains("\"credential_name\":\"OCI_CRED\"")
                .contains("\"provider\":\"oci\"")
                .contains("\"model\":\"cohere.command-r-plus\"")
                .contains("\"max_tokens\":768")
                .contains("\"source_language\":\"English\"")
                .contains("\"target_language\":\"French\"");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies SET_ATTRIBUTE preserves explicitly supplied empty attribute values.
     * Expected: Empty strings and empty lists remain in the attributes CLOB payload.
     */
    @Test
    void setAttributesPreservesExplicitEmptyAttributeValuesInPayload() throws Exception {
        DefaultProfile profile = loadedProfile();
        ProfileAttributes attributes = ProfileAttributes.builder()
                .additionalInstructions("")
                .stopTokens(List.of())
                .build();
        when(connection.prepareCall(Sql.SET_ATTRIBUTES.get())).thenReturn(callableStatement);

        assertThat(profile.setAttributes(attributes)).isTrue();

        assertEmptyAttributeValuesPreserved(
                captureCharacterStream(callableStatement, 2, attributes.toJson()));
    }

    /**
     * Test: Verifies bulk attribute binding preserves SQL NULL and supports large payloads.
     * Expected: A null payload uses setNull, while a payload larger than 4000 characters uses
     * a character stream and the exact payload length.
     */
    @Test
    void setAttributesBindsNullAndLargePayload() throws Exception {
        DefaultProfile profile = loadedProfile();
        CallableStatement nullStatement = mock(CallableStatement.class);
        CallableStatement largeStatement = mock(CallableStatement.class);
        when(connection.prepareCall(Sql.SET_ATTRIBUTES.get())).thenReturn(nullStatement, largeStatement);

        assertThat(profile.setAttributes(null)).isTrue();

        String instructions = "x".repeat(40_000);
        ProfileAttributes largeAttributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .additionalInstructions(instructions)
                .build();
        assertThat(profile.setAttributes(largeAttributes)).isTrue();

        verify(nullStatement).setNull(2, Types.CLOB);
        verify(nullStatement).execute();
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(largeStatement).setCharacterStream(eq(2), readerCaptor.capture(),
                eq(largeAttributes.toJson().length()));
        assertThat(readAll(readerCaptor.getValue())).isEqualTo(largeAttributes.toJson());
        verify(largeStatement).execute();
    }

    /**
     * Test: Verifies generate synthetic data rejects null single request.
     * Expected: IllegalArgumentException with "request must not be null" is thrown.
     */
    @Test
    void generateSyntheticDataRejectsNullSingleRequest() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.generateSyntheticData(
                (SyntheticDataSingleRequest) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request must not be null");
    }

    /**
     * Test: Verifies generate synthetic data rejects null batch request.
     * Expected: IllegalArgumentException with "request must not be null" is thrown.
     */
    @Test
    void generateSyntheticDataRejectsNullBatchRequest() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.generateSyntheticData(
                (SyntheticDataBatchRequest) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request must not be null");
    }

    /**
     * Test: Verifies single synthetic data wraps prepare call failure.
     * Expected: A SelectAIException for single synthetic-data generation is thrown with the
     * original SQLException, error code 942, and SQL state 42000.
     */
    @Test
    void singleSyntheticDataWrapsPrepareCallFailure() throws Exception {
        DefaultProfile profile = loadedProfile();
        SQLException sqlException = new SQLException("prepare failed", "42000", 942);
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get()))
                .thenThrow(sqlException);

        assertThatThrownBy(() -> profile.generateSyntheticData(
                SyntheticDataSingleRequest.builder("EMPLOYEES").build()))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("GENERATE_SYNTHETIC_DATA (single)")
                .hasCause(sqlException)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(942);
                    assertThat(wrapped.getSqlState()).isEqualTo("42000");
                });
    }

    /**
     * Test: Verifies batch synthetic data wraps execute failure and closes statement.
     * Expected: A SelectAIException for multi synthetic-data generation is thrown with the
     * original SQLException, error code 20000, SQL state 42000, and the statement is closed.
     */
    @Test
    void batchSyntheticDataWrapsExecuteFailureAndClosesStatement() throws Exception {
        DefaultProfile profile = loadedProfile();
        SQLException sqlException = new SQLException("batch generation failed", "42000", 20000);
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_MULTI.get()))
                .thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES").build())
                .build();

        assertThatThrownBy(() -> profile.generateSyntheticData(request))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("GENERATE_SYNTHETIC_DATA (multi)")
                .hasCause(sqlException)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(20000);
                    assertThat(wrapped.getSqlState()).isEqualTo("42000");
                });

        verify(callableStatement).close();
    }

    /**
     * Test: Verifies single synthetic data wraps execute failure and closes the statement.
     * Expected: A SelectAIException contains the original SQLException, error code 20000,
     * SQL state 42000, and the statement is closed.
     */
    @Test
    void singleSyntheticDataWrapsExecuteFailureAndClosesStatement() throws Exception {
        DefaultProfile profile = loadedProfile();
        SQLException sqlException = new SQLException("single generation failed", "42000", 20000);
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get()))
                .thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder("EMPLOYEES")
                .recordCount(1)
                .build();

        assertThatThrownBy(() -> profile.generateSyntheticData(request))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("GENERATE_SYNTHETIC_DATA (single)")
                .hasCause(sqlException)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(20000);
                    assertThat(wrapped.getSqlState()).isEqualTo("42000");
                });

        verify(callableStatement).close();
    }

    /**
     * Test: Verifies synthetic-data failure diagnostics do not expose user prompts or params.
     * Expected: The sensitive prompt marker is absent from the SDK exception text and captured logs.
     */
    @Test
    void syntheticDataFailureDoesNotExposeUserPromptOrParamsInExceptionOrLogs() throws Exception {
        DefaultProfile profile = loadedProfile();
        String sensitivePrompt = "SEC_SYNTH_MARKER synthetic prompt should not be logged";
        SQLException sqlException = new SQLException("database rejected request", "42000", 942);
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder("EMPLOYEES")
                .ownerName("HR")
                .recordCount(10)
                .userPrompt(sensitivePrompt)
                .params(SyntheticDataParams.builder()
                        .sampleRows(1)
                        .priority(SyntheticDataParams.Priority.HIGH)
                        .build())
                .build();
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get()))
                .thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        LogCapture.CapturedFailure failure = LogCapture.captureFailure(
                () -> profile.generateSyntheticData(request));

        assertThat(failure.throwable())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("GENERATE_SYNTHETIC_DATA (single)")
                .hasCause(sqlException);
        LogCapture.assertFailureDoesNotExpose(failure, sensitivePrompt, "SEC_SYNTH_MARKER");
    }

    /**
     * Test: Verifies batch synthetic data wraps closed connection failure.
     * Expected: A SelectAIException for multi synthetic-data generation is thrown with the
     * closed-connection SQLException, error code 17002, and SQL state 08003.
     */
    @Test
    void batchSyntheticDataWrapsClosedConnectionFailure() throws Exception {
        DefaultProfile profile = loadedProfile();
        SQLException closedConnection = new SQLException("Connection is closed", "08003", 17002);
        when(connection.prepareCall(Sql.GENERATE_SYNTHETIC_DATA_MULTI.get()))
                .thenThrow(closedConnection);
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("HR", "EMPLOYEES").build())
                .build();

        connection.close();

        assertThatThrownBy(() -> profile.generateSyntheticData(request))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("GENERATE_SYNTHETIC_DATA (multi)")
                .hasCause(closedConnection)
                .satisfies(error -> {
                    SelectAIException wrapped = (SelectAIException) error;
                    assertThat(wrapped.getErrorCode()).isEqualTo(17002);
                    assertThat(wrapped.getSqlState()).isEqualTo("08003");
                });
    }

    /**
     * Test: Verifies summarize inline content binds all summary params and returns clob text.
     * Expected: The summary is "Annual leave is paid." and all supplied content and summary
     * parameters are bound to the SUMMARIZE call.
     */
    @Test
    void summarizeInlineContentBindsAllSummaryParamsAndReturnsClobText() throws Exception {
        DefaultProfile profile = loadedProfile();
        String content = "Employees receive paid annual leave.";
        when(connection.prepareCall(Sql.SUMMARIZE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(23L);
        when(clob.getSubString(1, 23)).thenReturn("Annual leave is paid.");

        SummaryParams params = SummaryParams.builder()
                .minWords(10)
                .maxWords(50)
                .summaryStyle(SummaryParams.Style.LIST)
                .chunkProcessingMethod(SummaryParams.ChunkProcessingMethod.MAP_REDUCE)
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.LOW)
                .build();
        String summary = profile.summarize(content, null, null, "Use one sentence", params);

        assertThat(summary).isEqualTo("Annual leave is paid.");
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verify(callableStatement).setCharacterStream(eq(2), readerCaptor.capture(), eq(content.length()));
        char[] buffer = new char[content.length()];
        assertThat(readerCaptor.getValue().read(buffer)).isEqualTo(content.length());
        assertThat(new String(buffer)).isEqualTo(content);
        verify(callableStatement).setString(3, "PROFILE_1");
        verifyCharacterStream(callableStatement, 4, "Use one sentence");
        verifyCharacterStream(callableStatement, 5, params.toJson());
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies SUMMARIZE binds a large user prompt as a CLOB stream.
     * Expected: The complete user_prompt is preserved at the documented bind position.
     */
    @Test
    void summarizeBindsLargeUserPromptAsCharacterStream() throws Exception {
        DefaultProfile profile = loadedProfile();
        String content = "Employees receive paid annual leave.";
        String userPrompt = "summary-instructions:" + "s".repeat(40_000);
        when(connection.prepareCall(Sql.SUMMARIZE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(23L);
        when(clob.getSubString(1, 23)).thenReturn("Annual leave is paid.");

        assertThat(profile.summarize(content, null, null, userPrompt, null))
                .isEqualTo("Annual leave is paid.");

        ArgumentCaptor<Reader> promptReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(4), promptReader.capture(),
                eq(userPrompt.length()));
        assertThat(readAll(promptReader.getValue())).isEqualTo(userPrompt);
    }

    /**
     * Test: Verifies summarize inline content uses database defaults when optional arguments are null.
     * Expected: The summary is "Annual leave is paid." and null optional arguments are bound
     * as SQL NULL for database defaults.
     */
    @Test
    void summarizeInlineContentUsesDatabaseDefaultsWhenOptionalArgumentsAreNull() throws Exception {
        DefaultProfile profile = loadedProfile();
        String content = "Employees receive paid annual leave.";
        when(connection.prepareCall(Sql.SUMMARIZE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(23L);
        when(clob.getSubString(1, 23)).thenReturn("Annual leave is paid.");

        String summary = profile.summarize(content, null, null, null, null);

        assertThat(summary).isEqualTo("Annual leave is paid.");
        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verify(callableStatement).setCharacterStream(eq(2), any(Reader.class), eq(content.length()));
        verify(callableStatement).setString(3, "PROFILE_1");
        verify(callableStatement).setNull(4, Types.CLOB);
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies summarize does not inject unset summary params.
     * Expected: The summary is returned and the params JSON contains only max_words=50, with no
     * unset fields injected.
     */
    @Test
    void summarizeDoesNotInjectUnsetSummaryParams() throws Exception {
        DefaultProfile profile = loadedProfile();
        String content = "Employees receive paid annual leave.";
        when(connection.prepareCall(Sql.SUMMARIZE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(23L);
        when(clob.getSubString(1, 23)).thenReturn("Annual leave is paid.");

        SummaryParams params = SummaryParams.builder()
                .maxWords(50)
                .build();

        String summary = profile.summarize(content, null, null, null, params);

        assertThat(summary).isEqualTo("Annual leave is paid.");
        assertThat(params.toJson()).isEqualTo("{\"max_words\":50}");
        verifyCharacterStream(callableStatement, 5, "{\"max_words\":50}");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies summarize location binds all summary params and returns clob text.
     * Expected: The summary is "Remote work policy summarized." and the location, credential,
     * profile, instructions, and summary params are bound.
     */
    @Test
    void summarizeLocationBindsAllSummaryParamsAndReturnsClobText() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.SUMMARIZE_LOCATION.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(31L);
        when(clob.getSubString(1, 31)).thenReturn("Remote work policy summarized.");

        SummaryParams params = SummaryParams.builder()
                .minWords(10)
                .maxWords(50)
                .summaryStyle(SummaryParams.Style.LIST)
                .chunkProcessingMethod(SummaryParams.ChunkProcessingMethod.ITERATIVE_REFINEMENT)
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.HIGH)
                .build();
        String summary = profile.summarize(null, "OBJ_CRED", "https://object.example/policy.html",
                "Use bullets", params);

        assertThat(summary).isEqualTo("Remote work policy summarized.");
        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verify(callableStatement).setString(2, "https://object.example/policy.html");
        verify(callableStatement).setString(3, "OBJ_CRED");
        verify(callableStatement).setString(4, "PROFILE_1");
        verifyCharacterStream(callableStatement, 5, "Use bullets");
        verifyCharacterStream(callableStatement, 6, params.toJson());
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies summarize location binds null optional arguments as SQL NULL.
     * Expected: Location, credential, and profile use the documented positions, while the
     * nullable user prompt and params positions remain available for database defaults.
     */
    @Test
    void summarizeLocationUsesDatabaseDefaultsWhenOptionalArgumentsAreNull() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.SUMMARIZE_LOCATION.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(31L);
        when(clob.getSubString(1, 31)).thenReturn("Remote work policy summarized.");

        String summary = profile.summarize(
                null,
                "OBJ_CRED",
                "https://object.example/policy.html",
                null,
                null);

        assertThat(summary).isEqualTo("Remote work policy summarized.");
        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verify(callableStatement).setString(2, "https://object.example/policy.html");
        verify(callableStatement).setString(3, "OBJ_CRED");
        verify(callableStatement).setString(4, "PROFILE_1");
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).setNull(6, Types.CLOB);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies summarize treats blank content as missing when location is provided.
     * Expected: Blank content is treated as missing; the location request returns the summary
     * and binds SQL NULL for instructions.
     */
    @Test
    void summarizeTreatsBlankContentAsMissingWhenLocationIsProvided() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.SUMMARIZE_LOCATION.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(31L);
        when(clob.getSubString(1, 31)).thenReturn("Remote work policy summarized.");

        SummaryParams params = SummaryParams.builder()
                .summaryStyle(SummaryParams.Style.PARAGRAPH)
                .build();
        String summary = profile.summarize(" ", "OBJ_CRED", "https://object.example/policy.html",
                null, params);

        assertThat(summary).isEqualTo("Remote work policy summarized.");
        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verify(callableStatement).setString(2, "https://object.example/policy.html");
        verify(callableStatement).setString(3, "OBJ_CRED");
        verify(callableStatement).setString(4, "PROFILE_1");
        verify(callableStatement).setNull(5, Types.CLOB);
        verifyCharacterStream(callableStatement, 6, params.toJson());
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies summarize rejects missing input and missing credential.
     * Expected: Missing content/location and missing object-storage credential each throw
     * IllegalArgumentException.
     */
    @Test
    void summarizeRejectsMissingInputAndMissingCredential() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.summarize(null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either content or location_uri");

        assertThatThrownBy(() -> profile.summarize(null, null, "https://object.example/policy.html", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential_name");

        assertThatThrownBy(() -> profile.summarize(" ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either content or location_uri");

        assertThatThrownBy(() -> profile.summarize(null, null, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either content or location_uri");

        assertThatThrownBy(() -> profile.summarize(
                null, " ", "https://object.example/policy.html", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential_name");

        verify(connection, never()).prepareCall(Sql.SUMMARIZE.get());
        verify(connection, never()).prepareCall(Sql.SUMMARIZE_LOCATION.get());
    }

    /**
     * Test: Verifies summarize rejects inline content and location together before database call.
     * Expected: Supplying both inline content and a location throws IllegalArgumentException
     * without preparing either SUMMARIZE statement.
     */
    @Test
    void summarizeRejectsInlineContentAndLocationTogetherBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.summarize(
                "inline content", "OBJ_CRED", "https://object.example/policy.html", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");

        verify(connection, never()).prepareCall(Sql.SUMMARIZE.get());
        verify(connection, never()).prepareCall(Sql.SUMMARIZE_LOCATION.get());
    }

    /**
     * Test: Verifies get profile attributes reloads current database state after local update.
     * Expected: After a local update, getProfileAttributes() returns database values provider=oci
     * and model=database-value.
     */
    @Test
    void getProfileAttributesReloadsCurrentDatabaseStateAfterLocalUpdate() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.SET_ATTRIBUTE.get())).thenReturn(callableStatement);

        assertThat(profile.setAttribute("model", "local-cache-value")).isTrue();

        when(attributesResultSet.next()).thenReturn(true, true, false);
        when(attributesResultSet.getString("attribute_name"))
                .thenReturn("provider", "model");
        when(attributesResultSet.getString("attribute_value"))
                .thenReturn("oci", "database-value");

        ProfileAttributes current = profile.getProfileAttributes();

        assertThat(current.getProvider()).isEqualTo("oci");
        assertThat(current.getModel()).isEqualTo("database-value");
    }

    /**
     * Test: Verifies profile attribute refresh failures preserve the database cause.
     * Expected: A metadata SQLException is wrapped as SelectAIException when attributes are
     * requested from a database-backed profile.
     */
    @Test
    void getProfileAttributesWrapsMetadataSqlFailures() throws Exception {
        DefaultProfile profile = DefaultProfile.fromMetadata(
                new SingleConnectionProvider(dbConnection),
                "PROFILE_1", createProfileAttributes(), null, "ENABLED");
        SQLException sqlException = new SQLException("attributes failed", "42000", 942);
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(PROFILE_ATTRIBUTES_SQL)).thenThrow(sqlException);

        assertThatThrownBy(profile::getProfileAttributes)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("PROFILE_1")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies summarize wraps sql failures.
     * Expected: A SelectAIException containing SUMMARIZE and the original SQLException cause is thrown.
     */
    @Test
    void summarizeWrapsSqlFailures() throws Exception {
        DefaultProfile profile = loadedProfile();
        SQLException sqlException = new SQLException("summarize failed", "42000", 20000);
        when(connection.prepareCall(Sql.SUMMARIZE.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> profile.summarize("content", null, null, null, null))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("SUMMARIZE")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies summarize failure diagnostics do not expose content, location, credential,
     * or prompt text.
     * Expected: Sensitive markers are absent from the SDK exception text and captured logs.
     */
    @Test
    void summarizeFailureDoesNotExposeContentLocationCredentialOrPromptInExceptionOrLogs()
            throws Exception {
        DefaultProfile profile = loadedProfile();
        String sensitiveContent = "SEC_SUMMARY_CONTENT_MARKER document should not be logged";
        String sensitivePrompt = "SEC_SUMMARY_PROMPT_MARKER instructions should not be logged";
        String sensitiveLocation = "https://object.example/SEC_SUMMARY_LOCATION_MARKER";
        String sensitiveCredential = "SEC_SUMMARY_CREDENTIAL_MARKER";
        SQLException inlineException = new SQLException("database rejected request", "42000", 942);
        SQLException locationException = new SQLException("database rejected request", "42000", 942);
        CallableStatement inlineStatement = mock(CallableStatement.class);
        CallableStatement locationStatement = mock(CallableStatement.class);
        when(connection.prepareCall(Sql.SUMMARIZE.get())).thenReturn(inlineStatement);
        when(connection.prepareCall(Sql.SUMMARIZE_LOCATION.get())).thenReturn(locationStatement);
        when(inlineStatement.execute()).thenThrow(inlineException);
        when(locationStatement.execute()).thenThrow(locationException);

        LogCapture.CapturedFailure inlineFailure = LogCapture.captureFailure(
                () -> profile.summarize(sensitiveContent, null, null, sensitivePrompt, null));
        LogCapture.CapturedFailure locationFailure = LogCapture.captureFailure(
                () -> profile.summarize(null, sensitiveCredential, sensitiveLocation,
                        sensitivePrompt, null));

        assertThat(inlineFailure.throwable())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("SUMMARIZE")
                .hasCause(inlineException);
        assertThat(locationFailure.throwable())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("SUMMARIZE")
                .hasCause(locationException);
        LogCapture.assertFailureDoesNotExpose(inlineFailure,
                sensitiveContent, sensitivePrompt, "SEC_SUMMARY_CONTENT_MARKER",
                "SEC_SUMMARY_PROMPT_MARKER");
        LogCapture.assertFailureDoesNotExpose(locationFailure,
                sensitiveLocation, sensitiveCredential, sensitivePrompt,
                "SEC_SUMMARY_LOCATION_MARKER", "SEC_SUMMARY_CREDENTIAL_MARKER",
                "SEC_SUMMARY_PROMPT_MARKER");
    }

    /**
     * Test: Verifies summarize returns null when database returns null clob.
     * Expected: The summary is null and the SUMMARIZE callable statement is executed.
     */
    @Test
    void summarizeReturnsNullWhenDatabaseReturnsNullClob() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.SUMMARIZE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(null);

        String summary = profile.summarize("content", null, null, null, null);

        assertThat(summary).isNull();
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies translate binds profile text and languages and returns clob text.
     * Expected: The translation is "Danke." and the profile, source text, source language,
     * and target language are bound.
     */
    @Test
    void translateBindsProfileTextAndLanguagesAndReturnsClobText() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(6L);
        when(clob.getSubString(1, 6)).thenReturn("Danke.");

        String translated = profile.translate("Thank you", "en", "de");

        assertThat(translated).isEqualTo("Danke.");
        verify(callableStatement).registerOutParameter(1, Types.CLOB);
        verify(callableStatement).setString(2, "PROFILE_1");
        verifyCharacterStream(callableStatement, 3, "Thank you");
        verify(callableStatement).setString(4, "en");
        verify(callableStatement).setString(5, "de");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies the one-argument translate overload omits both language parameters.
     * Expected: The overload delegates to TRANSLATE with SQL-call language arguments represented
     * by Java null values so the profile or provider can supply them.
     */
    @Test
    void translateWithoutLanguagesBindsNullLanguageParameters() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(5L);
        when(clob.getSubString(1, 5)).thenReturn("Danke");

        assertThat(profile.translate("Thank you")).isEqualTo("Danke");

        verify(callableStatement).setString(2, "PROFILE_1");
        verifyCharacterStream(callableStatement, 3, "Thank you");
        verify(callableStatement).setString(4, (String) null);
        verify(callableStatement).setString(5, (String) null);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies the two-argument translate overload treats its language argument as target.
     * Expected: The source language is omitted and the supplied target language is bound at the
     * target position.
     */
    @Test
    void translateWithTargetLanguageBindsOnlyTargetLanguage() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(5L);
        when(clob.getSubString(1, 5)).thenReturn("Danke");

        assertThat(profile.translate("Thank you", "de")).isEqualTo("Danke");

        verify(callableStatement).setString(2, "PROFILE_1");
        verifyCharacterStream(callableStatement, 3, "Thank you");
        verify(callableStatement).setString(4, (String) null);
        verify(callableStatement).setString(5, "de");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies TRANSLATE binds a large source text as a CLOB stream.
     * Expected: The complete text is preserved at the text bind position.
     */
    @Test
    void translateBindsLargeTextAsCharacterStream() throws Exception {
        DefaultProfile profile = loadedProfile();
        String text = "translate-text:" + "t".repeat(40_000);
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(6L);
        when(clob.getSubString(1, 6)).thenReturn("Danke.");

        assertThat(profile.translate(text, "en", "de")).isEqualTo("Danke.");

        ArgumentCaptor<Reader> textReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(3), textReader.capture(),
                eq(text.length()));
        assertThat(readAll(textReader.getValue())).isEqualTo(text);
    }

    /**
     * Test: Verifies translate passes null languages for profile fallback or provider detection.
     * Expected: The translation is "Danke" and both language parameters are passed as null.
     */
    @Test
    void translatePassesNullLanguagesForProfileFallbackOrProviderDetection() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(5L);
        when(clob.getSubString(1, 5)).thenReturn("Danke");

        String translated = profile.translate("Thank you", null, null);

        assertThat(translated).isEqualTo("Danke");
        verify(callableStatement).setString(2, "PROFILE_1");
        verifyCharacterStream(callableStatement, 3, "Thank you");
        verify(callableStatement).setString(4, (String) null);
        verify(callableStatement).setString(5, (String) null);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies translate passes null target for profile target fallback.
     * Expected: The translation is "Danke" and the target language is passed as null.
     */
    @Test
    void translatePassesNullTargetForProfileTargetFallback() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(clob);
        when(clob.length()).thenReturn(5L);
        when(clob.getSubString(1, 5)).thenReturn("Danke");

        String translated = profile.translate("Thank you", "en", null);

        assertThat(translated).isEqualTo("Danke");
        verify(callableStatement).setString(2, "PROFILE_1");
        verifyCharacterStream(callableStatement, 3, "Thank you");
        verify(callableStatement).setString(4, "en");
        verify(callableStatement).setString(5, (String) null);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies translate returns null when the database returns a null CLOB.
     * Expected: The callable statement executes and the Java result remains null.
     */
    @Test
    void translateReturnsNullWhenDatabaseReturnsNullClob() throws Exception {
        DefaultProfile profile = loadedProfile();
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.getClob(1)).thenReturn(null);

        String translated = profile.translate("Thank you", "en", "de");

        assertThat(translated).isNull();
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies translate rejects null and empty text before database call.
     * Expected: Null, empty, and whitespace-only text each throw IllegalArgumentException
     * without preparing TRANSLATE.
     */
    @Test
    void translateRejectsNullAndEmptyTextBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.translate(null, "en", "de"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.translate("", "en", "de"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.translate("   ", "en", "de"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(connection, never()).prepareCall(Sql.TRANSLATE.get());
    }

    /**
     * Test: Verifies translate wraps sql failures.
     * Expected: A SelectAIException containing TRANSLATE and the original SQLException cause is thrown.
     */
    @Test
    void translateWrapsSqlFailures() throws Exception {
        DefaultProfile profile = loadedProfile();
        SQLException sqlException = new SQLException("translate failed", "42000", 20006);
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> profile.translate("Hello", "English", "French"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("TRANSLATE")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies translate failure diagnostics do not expose source text.
     * Expected: The source-text marker is absent from the SDK exception text and captured logs.
     */
    @Test
    void translateFailureDoesNotExposeSourceTextInExceptionOrLogs() throws Exception {
        DefaultProfile profile = loadedProfile();
        String sensitiveText = "SEC_TRANSLATE_MARKER source text should not be logged";
        SQLException sqlException = new SQLException("database rejected request", "42000", 942);
        when(connection.prepareCall(Sql.TRANSLATE.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        LogCapture.CapturedFailure failure = LogCapture.captureFailure(
                () -> profile.translate(sensitiveText, "en", "de"));

        assertThat(failure.throwable())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("TRANSLATE")
                .hasCause(sqlException);
        LogCapture.assertFailureDoesNotExpose(failure, sensitiveText, "SEC_TRANSLATE_MARKER");
    }

    private DefaultProfile loadedProfile() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_PROFILE.get())).thenReturn(profileStatement);
        when(connection.prepareStatement(PROFILE_ATTRIBUTES_SQL)).thenReturn(attributesStatement);
        when(profileStatement.executeQuery()).thenReturn(profileResultSet);
        when(profileResultSet.next()).thenReturn(true);
        when(profileResultSet.getString("profile_name")).thenReturn("PROFILE_1");
        when(profileResultSet.getString("status")).thenReturn("ENABLED");
        when(profileResultSet.getString("description")).thenReturn("Profile description");
        when(attributesStatement.executeQuery()).thenReturn(attributesResultSet);
        when(attributesResultSet.next()).thenReturn(false);

        DefaultProfile profile = new DefaultProfile(new SingleConnectionProvider(dbConnection), "PROFILE_1");

        assertThat(profile.getProfileName()).isEqualTo("PROFILE_1");
        assertThat(profile.getStatus()).isEqualTo("ENABLED");
        assertThat(profile.getDescription()).isEqualTo("Profile description");
        verify(profileStatement, times(2)).setString(1, "PROFILE_1");
        verify(attributesStatement).setString(1, "PROFILE_1");
        return profile;
    }

    private static ProfileAttributes localProfileAttributes(DefaultProfile profile) throws Exception {
        Field field = DefaultProfile.class.getDeclaredField("profileAttributes");
        field.setAccessible(true);
        return (ProfileAttributes) field.get(profile);
    }

    private static ProfileAttributes createProfileAttributes() {
        return ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("oci")
                .model("cohere.command-r-plus")
                .build();
    }

    private static CallableStatement generateStatementReturning(String value) throws Exception {
        CallableStatement statement = mock(CallableStatement.class);
        Clob resultClob = mock(Clob.class);
        when(statement.getClob(1)).thenReturn(resultClob);
        when(resultClob.length()).thenReturn((long) value.length());
        when(resultClob.getSubString(1, value.length())).thenReturn(value);
        return statement;
    }

    private static void verifyGenerateCall(CallableStatement statement, GenerateAction generateAction)
            throws Exception {
        verify(statement).registerOutParameter(1, Types.CLOB);
        verifyCharacterStream(statement, 2, "how many customers");
        verify(statement).setString(3, "PROFILE_1");
        verify(statement).setString(4, generateAction.name());
        verify(statement).setNull(5, Types.CLOB);
        verify(statement).setNull(6, Types.CLOB);
        verify(statement).execute();
    }

    private static void verifyGenerateCallWithParams(CallableStatement statement,
                                                     GenerateAction generateAction,
                                                     GenerateParams params) throws Exception {
        verify(statement).registerOutParameter(1, Types.CLOB);
        verifyCharacterStream(statement, 2, "how many customers");
        verify(statement).setString(3, "PROFILE_1");
        verify(statement).setString(4, generateAction.name());
        verify(statement).setNull(5, Types.CLOB);
        verifyCharacterStream(statement, 6, params.toJson());
        verify(statement).execute();
    }

    private static void verifyCharacterStream(CallableStatement statement, int parameterIndex,
                                              String expected) throws Exception {
        assertThat(captureCharacterStream(statement, parameterIndex, expected)).isEqualTo(expected);
    }

    private static String captureCharacterStream(CallableStatement statement, int parameterIndex,
                                                 String expected) throws Exception {
        ArgumentCaptor<Reader> readerCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(statement).setCharacterStream(eq(parameterIndex), readerCaptor.capture(),
                eq(expected.length()));
        return readAll(readerCaptor.getValue());
    }

    private static void assertEmptyAttributeValuesPreserved(String payloadJson) throws Exception {
        JsonNode json = MAPPER.readTree(payloadJson);
        assertThat(json.has("additional_instructions")).isTrue();
        assertThat(json.get("additional_instructions").asText()).isEmpty();
        assertThat(json.has("stop_tokens")).isTrue();
        assertThat(json.get("stop_tokens")).isEmpty();
    }

    private static String readAll(Reader reader) throws Exception {
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            value.append(buffer, 0, count);
        }
        return value.toString();
    }
}
