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
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.ConversationPrompt;
import com.oracle.database.selectai.model.SelectAIException;
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
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultConversationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * Test: Verifies loading existing conversation maps metadata to attributes.
     * Expected: The conversation id is CONV-123 and its title, description, retention days,
     * and conversation length are mapped to the configured values.
     */
    @Test
    void loadingExistingConversationMapsMetadataToAttributes() throws Exception {
        DefaultConversation conversation = loadedConversation();
        ConversationAttributes attributes = conversation.getConversationAttributes();

        assertThat(conversation.getConversationId()).isEqualTo("CONV-123");
        assertThat(attributes.getTitle()).isEqualTo("Support Thread");
        assertThat(attributes.getDescription()).isEqualTo("Incident triage");
        assertThat(attributes.getRetentionDays()).isEqualTo(21);
        assertThat(attributes.getConversationLength()).isEqualTo(3);
        verify(preparedStatement, times(2)).setString(1, "CONV-123");
    }

    /**
     * Test: Verifies second conversation reference reloads attributes after another reference updates.
     * Expected: The second reference observes the updated title, description, retention days,
     * and conversation length after the first reference updates the conversation.
     */
    @Test
    void secondConversationReferenceReloadsAttributesAfterAnotherReferenceUpdates()
            throws Exception {
        PreparedStatement firstMetadataStatement = mock(PreparedStatement.class);
        PreparedStatement secondMetadataStatement = mock(PreparedStatement.class);
        PreparedStatement refreshedMetadataStatement = mock(PreparedStatement.class);
        ResultSet firstMetadataResultSet = mock(ResultSet.class);
        ResultSet secondMetadataResultSet = mock(ResultSet.class);
        ResultSet refreshedMetadataResultSet = mock(ResultSet.class);

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_CONVERSATION.get()))
                .thenReturn(firstMetadataStatement, secondMetadataStatement, refreshedMetadataStatement);
        stubConversationMetadata(firstMetadataStatement, firstMetadataResultSet,
                "Initial Title", "Initial Description", 7, 10);
        stubConversationMetadata(secondMetadataStatement, secondMetadataResultSet,
                "Initial Title", "Initial Description", 7, 10);
        stubConversationMetadata(refreshedMetadataStatement, refreshedMetadataResultSet,
                "Updated Title", "Updated Description", 30, 5);

        DefaultConversation first = new DefaultConversation(
                new SingleConnectionProvider(dbConnection), "CONV-123");
        DefaultConversation second = new DefaultConversation(
                new SingleConnectionProvider(dbConnection), "CONV-123");
        ConversationAttributes updated = ConversationAttributes.builder()
                .title("Updated Title")
                .description("Updated Description")
                .retentionDays(30)
                .conversationLength(5)
                .build();
        when(connection.prepareCall(Sql.UPDATE_CONVERSATION.get())).thenReturn(callableStatement);

        assertThat(first.setAttributes(updated)).isTrue();

        ConversationAttributes observed = second.getConversationAttributes();
        assertThat(observed.getTitle()).isEqualTo("Updated Title");
        assertThat(observed.getDescription()).isEqualTo("Updated Description");
        assertThat(observed.getRetentionDays()).isEqualTo(30);
        assertThat(observed.getConversationLength()).isEqualTo(5);
        verify(refreshedMetadataStatement).setString(1, "CONV-123");
    }

    /**
     * Test: Verifies loading missing conversation throws select ai exception.
     * Expected: Constructing a missing conversation throws SelectAIException with a
     * conversation-not-found message.
     */
    @Test
    void loadingMissingConversationThrowsSelectAIException() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_CONVERSATION.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertThatThrownBy(() -> new DefaultConversation(new SingleConnectionProvider(dbConnection), "MISSING-CONV"))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("conversation not found");
    }

    /**
     * Test: Verifies conversation constructors reject missing required inputs.
     * Expected: A blank conversation ID and null pending attributes each raise
     * IllegalArgumentException without accessing the database.
     */
    @Test
    void constructorsRejectMissingRequiredInputsWithoutDatabaseCall() {
        assertThatThrownBy(() -> new DefaultConversation(
                new SingleConnectionProvider(dbConnection), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
        assertThatThrownBy(() -> new DefaultConversation(
                new SingleConnectionProvider(dbConnection), (ConversationAttributes) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationAttributes");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies create binds attributes and stores returned conversation id.
     * Expected: create() returns and stores CONV-123, binds all four attributes in the JSON
     * payload, registers the output, and executes the statement.
     */
    @Test
    void createBindsAttributesAndStoresReturnedConversationId() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("Support Thread")
                .description("Incident triage")
                .retentionDays(21)
                .conversationLength(3)
                .build();
        when(connection.prepareCall(Sql.CREATE_CONVERSATION.get())).thenReturn(callableStatement);
        when(callableStatement.getString(1)).thenReturn("CONV-123");

        DefaultConversation conversation = new DefaultConversation(new SingleConnectionProvider(dbConnection), attributes);

        assertThat(conversation.create()).isEqualTo("CONV-123");
        assertThat(conversation.getConversationId()).isEqualTo("CONV-123");

        ArgumentCaptor<Reader> jsonCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).registerOutParameter(1, Types.VARCHAR);
        verify(callableStatement).setCharacterStream(eq(2), jsonCaptor.capture(),
                eq(attributes.toJson().length()));
        verify(callableStatement).execute();
        String payload = readAll(jsonCaptor.getValue());
        assertThat(payload).contains("\"title\":\"Support Thread\"")
                .contains("\"description\":\"Incident triage\"")
                .contains("\"retention_days\":21")
                .contains("\"conversation_length\":3");
    }

    /**
     * Test: Verifies conversation attribute log summaries do not expose sensitive text values.
     * Expected: The summary records attribute presence while omitting title and description content.
     */
    @Test
    void logAttributeSummaryDoesNotExposeTitleOrDescription() {
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("Investigation of employee John Smith")
                .description("Suspected fraud involving customer account 983421")
                .retentionDays(30)
                .conversationLength(5)
                .build();

        String logSummary = DefaultConversation.describeConversationAttributesForLog(attributes);

        assertThat(logSummary)
                .contains("attributesPresent=true")
                .contains("titlePresent=true")
                .contains("descriptionPresent=true")
                .contains("retentionDaysPresent=true")
                .contains("conversationLengthPresent=true")
                .doesNotContain("Investigation")
                .doesNotContain("John Smith")
                .doesNotContain("Suspected fraud")
                .doesNotContain("983421");
    }

    /**
     * Test: Verifies CREATE_CONVERSATION binds a large description as a CLOB stream.
     * Expected: The complete 40,000-character description reaches JDBC without truncation.
     */
    @Test
    void createBindsLargeDescriptionAsCharacterStream() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        String description = "conversation-description:" + "d".repeat(40_000);
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("Support Thread")
                .description(description)
                .build();
        when(connection.prepareCall(Sql.CREATE_CONVERSATION.get())).thenReturn(callableStatement);
        when(callableStatement.getString(1)).thenReturn("CONV-LARGE-CLOB");

        DefaultConversation conversation = new DefaultConversation(
                new SingleConnectionProvider(dbConnection), attributes);

        assertThat(conversation.create()).isEqualTo("CONV-LARGE-CLOB");

        ArgumentCaptor<Reader> descriptionReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), descriptionReader.capture(),
                eq(attributes.toJson().length()));
        assertThat(readAll(descriptionReader.getValue())).isEqualTo(attributes.toJson());
    }

    /**
     * Test: Verifies create sends only explicitly supplied attributes.
     * Expected: An empty attributes object is serialized without title, description,
     * retention-days, or conversation-length defaults.
     */
    @Test
    void createOmitsUnspecifiedAttributesWithoutInjectingDefaults() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareCall(Sql.CREATE_CONVERSATION.get())).thenReturn(callableStatement);
        when(callableStatement.getString(1)).thenReturn("CONV-EMPTY-ATTRIBUTES");

        DefaultConversation conversation = new DefaultConversation(
                new SingleConnectionProvider(dbConnection),
                ConversationAttributes.builder().build());

        assertThat(conversation.create()).isEqualTo("CONV-EMPTY-ATTRIBUTES");

        ArgumentCaptor<Reader> jsonCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), jsonCaptor.capture(),
                eq(ConversationAttributes.builder().build().toJson().length()));
        String json = readAll(jsonCaptor.getValue());
        JsonNode payload = MAPPER.readTree(json);

        assertThat(payload).hasSize(0);
        assertThat(payload.has("title")).isFalse();
        assertThat(payload.has("description")).isFalse();
        assertThat(payload.has("retention_days")).isFalse();
        assertThat(payload.has("conversation_length")).isFalse();
    }

    /**
     * Test: Verifies create wraps sql failures.
     * Expected: create() throws SelectAIException naming CREATE_CONVERSATION and preserving
     * the SQL cause.
     */
    @Test
    void createWrapsSqlFailures() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        ConversationAttributes attributes = ConversationAttributes.builder()
                .title("Support Thread")
                .build();
        SQLException sqlException = new SQLException("create failed", "42000", 20000);
        when(connection.prepareCall(Sql.CREATE_CONVERSATION.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        DefaultConversation conversation = new DefaultConversation(new SingleConnectionProvider(dbConnection), attributes);

        assertThatThrownBy(conversation::create)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("CREATE_CONVERSATION")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies set attributes requires conversation id.
     * Expected: setAttributes() throws IllegalStateException identifying the missing
     * conversation id.
     */
    @Test
    void setAttributesRequiresConversationId() throws SelectAIException {
        DefaultConversation conversation = new DefaultConversation(new SingleConnectionProvider(dbConnection));

        assertThatThrownBy(() -> conversation.setAttributes(ConversationAttributes.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conversationId");
    }

    /**
     * Test: Verifies configured conversation rejects database operations before create.
     * Expected: Each database operation throws its operation-specific IllegalStateException,
     * and no database interaction occurs before create().
     */
    @Test
    void configuredConversationRejectsDatabaseOperationsBeforeCreate() throws Exception {
        DefaultConversation pendingConversation = new DefaultConversation(
                new SingleConnectionProvider(dbConnection),
                ConversationAttributes.builder().title("Pending conversation").build());

        assertThatThrownBy(() -> pendingConversation.setAttributes(
                ConversationAttributes.builder().title("Updated conversation").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("setAttributes requires a created conversation; call create() first");
        assertThatThrownBy(pendingConversation::listPrompts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("list conversation prompts requires a created conversation; call create() first");
        assertThatThrownBy(() -> pendingConversation.deletePrompt("PROMPT-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delete conversation prompt requires a created conversation; call create() first");
        assertThatThrownBy(() -> pendingConversation.drop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created conversation; call create() first");

        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies configured conversation returns pending attributes without database call.
     * Expected: getConversationAttributes() returns the exact pending attributes object and
     * does not access the database.
     */
    @Test
    void configuredConversationReturnsPendingAttributesWithoutDatabaseCall() throws Exception {
        ConversationAttributes configuredAttributes = ConversationAttributes.builder()
                .title("Pending conversation")
                .description("Pending description")
                .retentionDays(30)
                .conversationLength(12)
                .build();
        DefaultConversation pendingConversation = new DefaultConversation(
                new SingleConnectionProvider(dbConnection), configuredAttributes);

        assertThat(pendingConversation.getConversationAttributes()).isSameAs(configuredAttributes);
        verifyNoInteractions(dbConnection);
    }

    /**
     * Test: Verifies set attributes binds conversation id and payload.
     * Expected: setAttributes() returns true, binds CONV-123 and the four updated attributes,
     * executes the statement, and reads back the updated values.
     */
    @Test
    void setAttributesBindsConversationIdAndPayload() throws Exception {
        DefaultConversation conversation = loadedConversation();
        ConversationAttributes newAttributes = ConversationAttributes.builder()
                .title("Updated Support Thread")
                .description("Updated incident triage")
                .retentionDays(30)
                .conversationLength(5)
                .build();
        when(connection.prepareCall(Sql.UPDATE_CONVERSATION.get())).thenReturn(callableStatement);

        assertThat(conversation.setAttributes(newAttributes)).isTrue();

        ArgumentCaptor<Reader> payloadCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setString(1, "CONV-123");
        verify(callableStatement).setCharacterStream(eq(2), payloadCaptor.capture(),
                eq(newAttributes.toJson().length()));
        assertThat(readAll(payloadCaptor.getValue()))
                .contains("\"title\":\"Updated Support Thread\"")
                .contains("\"description\":\"Updated incident triage\"")
                .contains("\"retention_days\":30")
                .contains("\"conversation_length\":5");
        verify(callableStatement).execute();

        when(resultSet.getString("conversation_title")).thenReturn("Updated Support Thread");
        when(resultSet.getString("description")).thenReturn("Updated incident triage");
        when(resultSet.getInt("retention_days")).thenReturn(30);
        when(resultSet.getInt("conversation_length")).thenReturn(5);
        ConversationAttributes observed = conversation.getConversationAttributes();
        assertThat(observed.getTitle()).isEqualTo(newAttributes.getTitle());
        assertThat(observed.getDescription()).isEqualTo(newAttributes.getDescription());
        assertThat(observed.getRetentionDays()).isEqualTo(newAttributes.getRetentionDays());
        assertThat(observed.getConversationLength()).isEqualTo(newAttributes.getConversationLength());
    }

    /**
     * Test: Verifies UPDATE_CONVERSATION binds a large description as a CLOB stream.
     * Expected: The complete 40,000-character description reaches JDBC without truncation.
     */
    @Test
    void setAttributesBindsLargeDescriptionAsCharacterStream() throws Exception {
        DefaultConversation conversation = loadedConversation();
        String description = "updated-conversation-description:" + "u".repeat(40_000);
        ConversationAttributes attributes = ConversationAttributes.builder()
                .description(description)
                .build();
        when(connection.prepareCall(Sql.UPDATE_CONVERSATION.get())).thenReturn(callableStatement);

        assertThat(conversation.setAttributes(attributes)).isTrue();

        ArgumentCaptor<Reader> descriptionReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setString(1, "CONV-123");
        verify(callableStatement).setCharacterStream(eq(2), descriptionReader.capture(),
                eq(attributes.toJson().length()));
        assertThat(readAll(descriptionReader.getValue())).isEqualTo(attributes.toJson());
    }

    /**
     * Test: Verifies set attributes sends every attribute without adding unexpected fields.
     * Expected: The update payload contains exactly title, description, retention days, and
     * conversation length with the supplied values.
     */
    @Test
    void setAttributesSendsEveryAttributeWithoutAddingUnexpectedFields() throws Exception {
        DefaultConversation conversation = loadedConversation();
        ConversationAttributes newAttributes = ConversationAttributes.builder()
                .title("Complete Support Thread")
                .description("Complete incident triage")
                .retentionDays(45)
                .conversationLength(8)
                .build();
        when(connection.prepareCall(Sql.UPDATE_CONVERSATION.get())).thenReturn(callableStatement);

        assertThat(conversation.setAttributes(newAttributes)).isTrue();

        ArgumentCaptor<Reader> payloadCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), payloadCaptor.capture(),
                eq(newAttributes.toJson().length()));
        JsonNode payload = MAPPER.readTree(readAll(payloadCaptor.getValue()));

        assertThat(payload).hasSize(4);
        assertThat(payload.get("title").asText()).isEqualTo("Complete Support Thread");
        assertThat(payload.get("description").asText()).isEqualTo("Complete incident triage");
        assertThat(payload.get("retention_days").asInt()).isEqualTo(45);
        assertThat(payload.get("conversation_length").asInt()).isEqualTo(8);
    }

    /**
     * Test: Verifies set attributes sends only explicitly supplied values.
     * Expected: The update payload contains the supplied description and no title, retention,
     * or conversation-length defaults.
     */
    @Test
    void setAttributesSendsOnlyExplicitAttributesWhenOptionalFieldsAreOmitted() throws Exception {
        DefaultConversation conversation = loadedConversation();
        ConversationAttributes newAttributes = ConversationAttributes.builder()
                .description("Description-only update")
                .build();
        when(connection.prepareCall(Sql.UPDATE_CONVERSATION.get())).thenReturn(callableStatement);

        assertThat(conversation.setAttributes(newAttributes)).isTrue();

        ArgumentCaptor<Reader> payloadCaptor = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), payloadCaptor.capture(),
                eq(newAttributes.toJson().length()));
        JsonNode payload = MAPPER.readTree(readAll(payloadCaptor.getValue()));

        assertThat(payload).hasSize(1);
        assertThat(payload.get("description").asText()).isEqualTo("Description-only update");
        assertThat(payload.has("title")).isFalse();
        assertThat(payload.has("retention_days")).isFalse();
        assertThat(payload.has("conversation_length")).isFalse();
    }

    /**
     * Test: Verifies set attributes wraps sql failures.
     * Expected: setAttributes() throws SelectAIException naming UPDATE_CONVERSATION and
     * preserving the SQL cause.
     */
    @Test
    void setAttributesWrapsSqlFailures() throws Exception {
        DefaultConversation conversation = loadedConversation();
        ConversationAttributes newAttributes = ConversationAttributes.builder()
                .title("Updated Support Thread")
                .build();
        SQLException sqlException = new SQLException("update failed", "42000", 20001);
        when(connection.prepareCall(Sql.UPDATE_CONVERSATION.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> conversation.setAttributes(newAttributes))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("UPDATE_CONVERSATION")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies null conversation attributes are rejected after a conversation is loaded.
     * Expected: setAttributes(null) raises IllegalArgumentException without preparing JDBC.
     */
    @Test
    void setAttributesRejectsNullAttributesBeforeDatabaseCall() throws Exception {
        DefaultConversation conversation = loadedConversation();

        assertThatThrownBy(() -> conversation.setAttributes(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationAttributes");

        verify(connection, org.mockito.Mockito.never())
                .prepareCall(Sql.UPDATE_CONVERSATION.get());
    }

    /**
     * Test: Verifies set attributes failure preserves previous local attributes.
     * Expected: A failed update throws SelectAIException while the previously loaded local
     * title, description, retention days, and conversation length remain unchanged.
     */
    @Test
    void setAttributesFailurePreservesPreviousLocalAttributes() throws Exception {
        DefaultConversation conversation = loadedConversation();
        ConversationAttributes originalAttributes = conversation.getConversationAttributes();
        ConversationAttributes newAttributes = ConversationAttributes.builder()
                .title("Failed Update Thread")
                .description("Failed update description")
                .retentionDays(30)
                .conversationLength(5)
                .build();
        SQLException sqlException = new SQLException("update failed", "42000", 20001);
        when(connection.prepareCall(Sql.UPDATE_CONVERSATION.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> conversation.setAttributes(newAttributes))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("UPDATE_CONVERSATION")
                .hasCause(sqlException);

        ConversationAttributes afterFailure = conversation.getConversationAttributes();
        assertThat(afterFailure.getTitle()).isEqualTo(originalAttributes.getTitle());
        assertThat(afterFailure.getDescription()).isEqualTo(originalAttributes.getDescription());
        assertThat(afterFailure.getRetentionDays()).isEqualTo(originalAttributes.getRetentionDays());
        assertThat(afterFailure.getConversationLength())
                .isEqualTo(originalAttributes.getConversationLength());
    }

    /**
     * Test: Verifies drop binds conversation id and force flag.
     * Expected: drop(true) returns true, binds CONV-123 and force=1, and executes the drop
     * statement.
     */
    @Test
    void dropBindsConversationIdAndForceFlag() throws Exception {
        DefaultConversation conversation = loadedConversation();
        when(connection.prepareCall(Sql.DROP_CONVERSATION.get())).thenReturn(callableStatement);

        assertThat(conversation.drop(true)).isTrue();

        verify(callableStatement).setString(1, "CONV-123");
        verify(callableStatement).setInt(2, 1);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies drop wraps sql failures.
     * Expected: drop(false) throws SelectAIException naming DROP_CONVERSATION and preserving
     * the SQL cause.
     */
    @Test
    void dropWrapsSqlFailures() throws Exception {
        DefaultConversation conversation = loadedConversation();
        SQLException sqlException = new SQLException("drop failed", "42000", 20002);
        when(connection.prepareCall(Sql.DROP_CONVERSATION.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> conversation.drop(false))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("DROP_CONVERSATION")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies prompt deletion binds the conversation prompt ID and force flag.
     * Expected: The default overload sends force=0 and the explicit force overload sends
     * force=1, with both calls executing the delete procedure.
     */
    @Test
    void deletePromptBindsPromptIdAndForceFlag() throws Exception {
        DefaultConversation conversation = loadedConversation();
        CallableStatement defaultDeleteStatement = mock(CallableStatement.class);
        CallableStatement forcedDeleteStatement = mock(CallableStatement.class);
        when(connection.prepareCall(Sql.DELETE_CONVERSATION_PROMPT.get()))
                .thenReturn(defaultDeleteStatement, forcedDeleteStatement);

        assertThat(conversation.deletePrompt("PROMPT-1")).isTrue();
        assertThat(conversation.deletePrompt("PROMPT-2", true)).isTrue();

        verify(defaultDeleteStatement).setString(1, "PROMPT-1");
        verify(defaultDeleteStatement).setInt(2, 0);
        verify(defaultDeleteStatement).execute();
        verify(forcedDeleteStatement).setString(1, "PROMPT-2");
        verify(forcedDeleteStatement).setInt(2, 1);
        verify(forcedDeleteStatement).execute();
    }

    /**
     * Test: Verifies prompt deletion rejects a blank prompt ID before JDBC execution.
     * Expected: A blank prompt ID raises IllegalArgumentException and no delete statement
     * is prepared.
     */
    @Test
    void deletePromptRejectsBlankPromptIdBeforeDatabaseCall() throws Exception {
        DefaultConversation conversation = loadedConversation();

        assertThatThrownBy(() -> conversation.deletePrompt(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationPromptId");

        verify(connection, org.mockito.Mockito.never())
                .prepareCall(Sql.DELETE_CONVERSATION_PROMPT.get());
    }

    /**
     * Test: Verifies prompt deletion rejects a null prompt ID before JDBC execution.
     * Expected: A null prompt ID raises IllegalArgumentException and no delete statement
     * is prepared.
     */
    @Test
    void deletePromptRejectsNullPromptIdBeforeDatabaseCall() throws Exception {
        DefaultConversation conversation = loadedConversation();

        assertThatThrownBy(() -> conversation.deletePrompt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationPromptId");

        verify(connection, org.mockito.Mockito.never())
                .prepareCall(Sql.DELETE_CONVERSATION_PROMPT.get());
    }

    /**
     * Test: Verifies prompt deletion wraps SQL failures.
     * Expected: A SelectAIException names DELETE_CONVERSATION_PROMPT, preserves the SQL cause,
     * and retains the database error metadata.
     */
    @Test
    void deletePromptWrapsSqlFailures() throws Exception {
        DefaultConversation conversation = loadedConversation();
        SQLException sqlException = new SQLException("delete failed", "42000", 20003);
        when(connection.prepareCall(Sql.DELETE_CONVERSATION_PROMPT.get()))
                .thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> conversation.deletePrompt("PROMPT-123", false))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Failed to execute DBMS_CLOUD_AI.DELETE_CONVERSATION_PROMPT")
                .hasCause(sqlException)
                .satisfies(error -> {
                    SelectAIException exception = (SelectAIException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(20003);
                    assertThat(exception.getSqlState()).isEqualTo("42000");
                });
    }

    /**
     * Test: Verifies list maps result set rows to conversation objects.
     * Expected: listConversations() returns CONV-1 and CONV-2 with their corresponding titles,
     * descriptions, and retention days.
     */
    @Test
    void listMapsResultSetRowsToConversationObjects() throws Exception {
        PreparedStatement firstConversationStatement = mock(PreparedStatement.class);
        PreparedStatement secondConversationStatement = mock(PreparedStatement.class);
        ResultSet firstConversationResultSet = mock(ResultSet.class);
        ResultSet secondConversationResultSet = mock(ResultSet.class);

        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_CONVERSATIONS.get())).thenReturn(preparedStatement);
        when(connection.prepareStatement(Sql.GET_CONVERSATION.get()))
                .thenReturn(firstConversationStatement, secondConversationStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("conversation_id")).thenReturn("CONV-1", "CONV-2");
        when(resultSet.getString("conversation_title")).thenReturn("First", "Second");
        when(resultSet.getString("description")).thenReturn("First description", "Second description");
        when(resultSet.getInt("retention_days")).thenReturn(7, 30);
        when(resultSet.getInt("conversation_length")).thenReturn(3, 5);
        when(resultSet.wasNull()).thenReturn(false);
        stubConversationMetadata(firstConversationStatement, firstConversationResultSet,
                "CONV-1", "First", "First description", 7, 3);
        stubConversationMetadata(secondConversationStatement, secondConversationResultSet,
                "CONV-2", "Second", "Second description", 30, 5);

        List<Conversation> conversations = new DefaultSelectAI(
                new SingleConnectionProvider(dbConnection)).listConversations();

        assertThat(conversations).hasSize(2);
        assertThat(conversations.get(0).getConversationId()).isEqualTo("CONV-1");
        assertThat(conversations.get(1).getConversationId()).isEqualTo("CONV-2");
        ConversationAttributes firstAttributes = conversations.get(0).getConversationAttributes();
        ConversationAttributes secondAttributes = conversations.get(1).getConversationAttributes();
        assertThat(firstAttributes.getTitle()).isEqualTo("First");
        assertThat(firstAttributes.getDescription()).isEqualTo("First description");
        assertThat(firstAttributes.getRetentionDays()).isEqualTo(7);
        assertThat(secondAttributes.getTitle()).isEqualTo("Second");
        assertThat(secondAttributes.getDescription()).isEqualTo("Second description");
        assertThat(secondAttributes.getRetentionDays()).isEqualTo(30);
        verify(preparedStatement).executeQuery();
    }

    /**
     * Test: Verifies list prompts maps every result set column to conversation prompt.
     * Expected: listPrompts() maps the single row's id, conversation metadata, profile, action,
     * prompt, response, timestamps, client data, SID, and serial number.
     */
    @Test
    void listPromptsMapsEveryResultSetColumnToConversationPrompt() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.LIST_CONVERSATION_PROMPTS.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("conversation_prompt_id")).thenReturn("PROMPT-123");
        when(resultSet.getString("conversation_id")).thenReturn("CONV-123");
        when(resultSet.getString("conversation_title")).thenReturn("Support Thread");
        when(resultSet.getString("profile_name")).thenReturn("AI_PROFILE");
        when(resultSet.getString("prompt_action")).thenReturn("chat");
        when(resultSet.getString("prompt")).thenReturn("How many customers are active?");
        when(resultSet.getString("prompt_response")).thenReturn("There are 42 active customers.");
        Timestamp created = Timestamp.valueOf("2026-01-02 03:04:05");
        Timestamp modified = Timestamp.valueOf("2026-01-02 03:05:06");
        when(resultSet.getTimestamp("created")).thenReturn(created);
        when(resultSet.getTimestamp("modified")).thenReturn(modified);
        when(resultSet.getString("client_identifier")).thenReturn("CLIENT-123");
        when(resultSet.getString("client_ip")).thenReturn("192.0.2.10");
        when(resultSet.getLong("sid")).thenReturn(123L);
        when(resultSet.getLong("serial#")).thenReturn(456L);
        when(resultSet.wasNull()).thenReturn(false);

        DefaultConversation conversation = new DefaultConversation(
                new SingleConnectionProvider(dbConnection),
                "CONV-123",
                ConversationAttributes.builder().title("Support Thread").build());

        List<ConversationPrompt> prompts = conversation.listPrompts();

        assertThat(prompts).hasSize(1);
        ConversationPrompt prompt = prompts.get(0);
        assertThat(prompt.getConversationPromptId()).isEqualTo("PROMPT-123");
        assertThat(prompt.getConversationId()).isEqualTo("CONV-123");
        assertThat(prompt.getConversationTitle()).isEqualTo("Support Thread");
        assertThat(prompt.getProfileName()).isEqualTo("AI_PROFILE");
        assertThat(prompt.getPromptAction()).isEqualTo("chat");
        assertThat(prompt.getPrompt()).isEqualTo("How many customers are active?");
        assertThat(prompt.getPromptResponse()).isEqualTo("There are 42 active customers.");
        assertThat(prompt.getCreated()).isEqualTo(created);
        assertThat(prompt.getModified()).isEqualTo(modified);
        assertThat(prompt.getClientIdentifier()).isEqualTo("CLIENT-123");
        assertThat(prompt.getClientIp()).isEqualTo("192.0.2.10");
        assertThat(prompt.getSid()).isEqualTo(123L);
        assertThat(prompt.getSerialNumber()).isEqualTo(456L);
        verify(preparedStatement).setString(1, "CONV-123");
        verify(preparedStatement).executeQuery();
    }

    /**
     * Test: Verifies prompt-list SQL failures are translated to SelectAIException.
     * Expected: The original SQLException is preserved as the cause and the prompt-list
     * operation is named in the exception.
     */
    @Test
    void listPromptsWrapsSqlFailures() throws Exception {
        DefaultConversation conversation = loadedConversation();
        SQLException sqlException = new SQLException("prompt metadata failed", "42000", 942);
        when(connection.prepareStatement(Sql.LIST_CONVERSATION_PROMPTS.get()))
                .thenThrow(sqlException);

        assertThatThrownBy(conversation::listPrompts)
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("conversation prompts")
                .hasCause(sqlException);
    }

    /**
     * Test: Verifies list wraps sql failures.
     * Expected: listConversations() throws SelectAIException naming the conversation metadata
     * view and preserving the SQL cause.
     */
    @Test
    void listWrapsSqlFailures() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        SQLException sqlException = new SQLException("metadata view failed", "42000", 942);
        when(connection.prepareStatement(Sql.LIST_CONVERSATIONS.get())).thenThrow(sqlException);

        assertThatThrownBy(() -> new DefaultSelectAI(
                new SingleConnectionProvider(dbConnection)).listConversations())
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("USER_CLOUD_AI_CONVERSATIONS")
                .hasCause(sqlException);
    }

    private DefaultConversation loadedConversation() throws Exception {
        when(dbConnection.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(Sql.GET_CONVERSATION.get())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("conversation_id")).thenReturn("CONV-123");
        when(resultSet.getString("conversation_title")).thenReturn("Support Thread");
        when(resultSet.getString("description")).thenReturn("Incident triage");
        when(resultSet.getInt("retention_days")).thenReturn(21);
        when(resultSet.getInt("conversation_length")).thenReturn(3);
        when(resultSet.wasNull()).thenReturn(false);

        return new DefaultConversation(new SingleConnectionProvider(dbConnection), "CONV-123");
    }

    private static void stubConversationMetadata(PreparedStatement statement,
                                                  ResultSet resultSet,
                                                  String title,
                                                  String description,
                                                  int retentionDays,
                                                  int conversationLength) throws SQLException {
        stubConversationMetadata(statement, resultSet, "CONV-123", title, description,
                retentionDays, conversationLength);
    }

    private static void stubConversationMetadata(PreparedStatement statement,
                                                  ResultSet resultSet,
                                                  String conversationId,
                                                  String title,
                                                  String description,
                                                  int retentionDays,
                                                  int conversationLength) throws SQLException {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("conversation_id")).thenReturn(conversationId);
        when(resultSet.getString("conversation_title")).thenReturn(title);
        when(resultSet.getString("description")).thenReturn(description);
        when(resultSet.getInt("retention_days")).thenReturn(retentionDays);
        when(resultSet.getInt("conversation_length")).thenReturn(conversationLength);
        when(resultSet.wasNull()).thenReturn(false);
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
