/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.SelectAIException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.io.Reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class DefaultProfileFeedbackTest {

    private static final String PROFILE_ATTRIBUTES_SQL =
            "SELECT attribute_name, attribute_value "
                    + "FROM USER_CLOUD_AI_PROFILE_ATTRIBUTES "
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

    /**
     * Test: Verifies feedback with sql id binds current profile and feedback values.
     * Expected: The call returns true and binds PROFILE_1, SQL ID 346vcu5qnr7x1, positive
     * feedback, the content, and ADD before execution.
     */
    @Test
    void feedbackWithSqlIdBindsCurrentProfileAndFeedbackValues() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlId("346vcu5qnr7x1")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .feedbackContent("Approved generated SQL")
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_ID.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setString(2, "346vcu5qnr7x1");
        verify(callableStatement).setString(3, "positive");
        verify(callableStatement).setCharacterStream(eq(5), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("Approved generated SQL".length()));
        verify(callableStatement).setString(6, "add");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback with omitted operation binds sql null for database default.
     * Expected: The call returns true, binds SQL NULL for the omitted operation, and executes.
     */
    @Test
    void feedbackWithOmittedOperationBindsSqlNullForDatabaseDefault() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlId("346vcu5qnr7x1")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .feedbackContent("Approved generated SQL")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_ID.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setNull(6, Types.VARCHAR);
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback with sql text binds current profile and sql text.
     * Expected: The call returns true and binds PROFILE_1, the SQL text, positive feedback,
     * and ADD before execution.
     */
    @Test
    void feedbackWithSqlTextBindsCurrentProfileAndSqlText() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlText("select * from employees")
                .feedbackType("positive")
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_TEXT.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setCharacterStream(eq(2), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees".length()));
        verify(callableStatement).setString(3, "positive");
        verify(callableStatement).setString(6, "add");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback requires a request and a SQL reference.
     * Expected: Null feedback and feedback without sql_id or sql_text each raise
     * IllegalArgumentException before FEEDBACK is prepared.
     */
    @Test
    void feedbackRejectsMissingRequestAndSqlReferenceBeforeDatabaseCall() throws Exception {
        DefaultProfile profile = loadedProfile();

        assertThatThrownBy(() -> profile.feedback(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feedbackRequest");
        assertThatThrownBy(() -> profile.feedback(Feedback.builder()
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sqlId or sqlText");

        verify(connection, org.mockito.Mockito.never()).prepareCall(Sql.FEEDBACK_SQL_ID.get());
        verify(connection, org.mockito.Mockito.never()).prepareCall(Sql.FEEDBACK_SQL_TEXT.get());
    }

    /**
     * Test: Verifies feedback with negative sql id binds response and feedback content.
     * Expected: The call returns true and binds the SQL ID, negative feedback, response,
     * feedback content, and ADD before execution.
     */
    @Test
    void feedbackWithNegativeSqlIdBindsResponseAndFeedbackContent() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlId("346vcu5qnr7x1")
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .response("select * from employees order by employee_id desc")
                .feedbackContent("Sort employees by descending employee_id")
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_ID.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setString(2, "346vcu5qnr7x1");
        verify(callableStatement).setString(3, "negative");
        verify(callableStatement).setCharacterStream(eq(4), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees order by employee_id desc".length()));
        verify(callableStatement).setCharacterStream(eq(5), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("Sort employees by descending employee_id".length()));
        verify(callableStatement).setString(6, "add");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback with negative sql text binds response and feedback content.
     * Expected: The call returns true and binds the SQL text, negative feedback, response,
     * feedback content, and ADD before execution.
     */
    @Test
    void feedbackWithNegativeSqlTextBindsResponseAndFeedbackContent() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlText("select * from employees order by employee_id desc")
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .response("select * from employees order by employee_id asc")
                .feedbackContent("Sort employees by ascending employee_id")
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_TEXT.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setCharacterStream(eq(2), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees order by employee_id desc".length()));
        verify(callableStatement).setString(3, "negative");
        verify(callableStatement).setCharacterStream(eq(4), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees order by employee_id asc".length()));
        verify(callableStatement).setCharacterStream(eq(5), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("Sort employees by ascending employee_id".length()));
        verify(callableStatement).setString(6, "add");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback with negative sql id without feedback content.
     * Expected: The call returns true, binds the response and null feedback content for the
     * SQL ID, and executes with ADD.
     */
    @Test
    void feedbackWithNegativeSqlIdWithoutFeedbackContent() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlId("346vcu5qnr7x1")
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .response("select * from employees order by employee_id desc")
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_ID.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setString(2, "346vcu5qnr7x1");
        verify(callableStatement).setString(3, "negative");
        verify(callableStatement).setCharacterStream(eq(4), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees order by employee_id desc".length()));
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).setString(6, "add");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback with negative sql text without feedback content.
     * Expected: The call returns true, binds the response and null feedback content for the
     * SQL text, and executes with ADD.
     */
    @Test
    void feedbackWithNegativeSqlTextWithoutFeedbackContent() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlText("select * from employees order by employee_id desc")
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .response("select * from employees order by employee_id asc")
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_TEXT.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setCharacterStream(eq(2), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees order by employee_id desc".length()));
        verify(callableStatement).setString(3, "negative");
        verify(callableStatement).setCharacterStream(eq(4), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees order by employee_id asc".length()));
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).setString(6, "add");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback preserves CLOB-sized SQL text, response, and content values when
     * binding the SQL-text overload.
     * Expected: Values longer than 4,000 characters reach their respective JDBC bind positions
     * without truncation.
     */
    @Test
    void feedbackWithLongClobValuesPreservesAllCharactersInJdbcBinds() throws Exception {
        DefaultProfile profile = loadedProfile();
        String sqlText = "select ai showsql " + "s".repeat(4_001);
        String response = "select " + "r".repeat(4_001);
        String feedbackContent = "feedback " + "c".repeat(4_001);
        Feedback feedback = Feedback.builder()
                .sqlText(sqlText)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .response(response)
                .feedbackContent(feedbackContent)
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_TEXT.get())).thenReturn(callableStatement);

        assertThat(profile.feedback(feedback)).isTrue();

        verify(callableStatement).setCharacterStream(eq(2), org.mockito.ArgumentMatchers.any(Reader.class),
                eq(sqlText.length()));
        verify(callableStatement).setCharacterStream(eq(4), org.mockito.ArgumentMatchers.any(Reader.class),
                eq(response.length()));
        verify(callableStatement).setCharacterStream(eq(5), org.mockito.ArgumentMatchers.any(Reader.class),
                eq(feedbackContent.length()));
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies the SQL-text feedback overload binds sql_text, response, and
     * feedback_content values larger than the PL/SQL VARCHAR2 bind limit as CLOB streams.
     * Expected: All three payloads preserve their complete characters at the documented positions.
     */
    @Test
    void feedbackWithVeryLargeClobValuesUsesCharacterStreams() throws Exception {
        DefaultProfile profile = loadedProfile();
        String sqlText = "select ai showsql " + "s".repeat(40_000);
        String response = "select " + "r".repeat(40_000);
        String feedbackContent = "feedback " + "c".repeat(40_000);
        Feedback feedback = Feedback.builder()
                .sqlText(sqlText)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .response(response)
                .feedbackContent(feedbackContent)
                .operation("add")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_TEXT.get())).thenReturn(callableStatement);

        assertThat(profile.feedback(feedback)).isTrue();

        ArgumentCaptor<Reader> sqlTextReader = ArgumentCaptor.forClass(Reader.class);
        ArgumentCaptor<Reader> responseReader = ArgumentCaptor.forClass(Reader.class);
        ArgumentCaptor<Reader> feedbackContentReader = ArgumentCaptor.forClass(Reader.class);
        verify(callableStatement).setCharacterStream(eq(2), sqlTextReader.capture(),
                eq(sqlText.length()));
        verify(callableStatement).setCharacterStream(eq(4), responseReader.capture(),
                eq(response.length()));
        verify(callableStatement).setCharacterStream(eq(5), feedbackContentReader.capture(),
                eq(feedbackContent.length()));
        assertThat(readAll(sqlTextReader.getValue())).isEqualTo(sqlText);
        assertThat(readAll(responseReader.getValue())).isEqualTo(response);
        assertThat(readAll(feedbackContentReader.getValue())).isEqualTo(feedbackContent);
    }

    /**
     * Test: Verifies feedback delete binds delete operation and null feedback fields.
     * Expected: The call returns true, binds the SQL text and null feedback fields, and executes
     * with DELETE.
     */
    @Test
    void feedbackDeleteBindsDeleteOperationAndNullFeedbackFields() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlText("select * from employees")
                .operation("delete")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_TEXT.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setCharacterStream(eq(2), org.mockito.ArgumentMatchers.any(Reader.class),
                eq("select * from employees".length()));
        verify(callableStatement).setString(3, null);
        verify(callableStatement).setNull(4, Types.CLOB);
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).setString(6, "delete");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback delete binds delete operation and null feedback fields with sql id.
     * Expected: The call returns true, binds the SQL ID and null feedback fields, and executes
     * with DELETE.
     */
    @Test
    void feedbackDeleteBindsDeleteOperationAndNullFeedbackFieldsWithSqlId() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlId("346vcu5qnr7x1")
                .operation("delete")
                .build();
        when(connection.prepareCall(Sql.FEEDBACK_SQL_ID.get())).thenReturn(callableStatement);

        boolean submitted = profile.feedback(feedback);

        assertThat(submitted).isTrue();
        verify(callableStatement).setString(1, "PROFILE_1");
        verify(callableStatement).setString(2, "346vcu5qnr7x1");
        verify(callableStatement).setString(3, null);
        verify(callableStatement).setNull(4, Types.CLOB);
        verify(callableStatement).setNull(5, Types.CLOB);
        verify(callableStatement).setString(6, "delete");
        verify(callableStatement).execute();
    }

    /**
     * Test: Verifies feedback wraps sql failures.
     * Expected: A SelectAIException containing FEEDBACK and the original SQLException cause is thrown.
     */
    @Test
    void feedbackWrapsSqlFailures() throws Exception {
        DefaultProfile profile = loadedProfile();
        Feedback feedback = Feedback.builder()
                .sqlId("abc123")
                .feedbackType("positive")
                .build();
        SQLException sqlException = new SQLException("feedback failed", "42000", 20000);
        when(connection.prepareCall(Sql.FEEDBACK_SQL_ID.get())).thenReturn(callableStatement);
        when(callableStatement.execute()).thenThrow(sqlException);

        assertThatThrownBy(() -> profile.feedback(feedback))
                .isInstanceOf(SelectAIException.class)
                .hasMessageContaining("FEEDBACK")
                .hasCause(sqlException);
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
