/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.feedback;

import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration coverage for feedback operations through the Java SDK.
 */
class FeedbackIT extends FeedbackIntegrationFixture {

    @Override
    protected String profileObjectList() {
        return objectListFor("people", "gymnast");
    }

    @Override
    protected void createIsolatedProfile() throws Exception {
        super.createIsolatedProfile();
        setProfileAndWarmUp(selectAI.getConnection(), profileName);
    }

    private void setProfileAndWarmUp(Connection connection, String profileName) throws SQLException {
        try (java.sql.CallableStatement statement = connection.prepareCall(
                "BEGIN DBMS_CLOUD_AI.SET_PROFILE(?); END;")) {
            statement.setString(1, profileName);
            statement.execute();
        }

        for (String action : new String[]{"showsql", "runsql", "explainsql"}) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("select ai " + action + " " + PROMPT);
            }
        }
    }

    /**
     * Test: Calls profile.feedback() with a NEGATIVE/ADD payload whose sqlText is
     * "select ai showsql Total points of each gymnasts", response is the name-ordered SQL in
     * NEGATIVE_RESPONSE_WITH_NAME_ORDER, and feedbackContent is "print in ascending order of name".
     * Expected: feedback() returns true and profile.showprompt(PROMPT) contains the supplied
     * corrected SQL response.
     */
    @Test
    void test19000NegativeFeedbackUsingShowsqlSqlTextAddsResponseToShowprompt() throws Exception {
        String sqlText = promptSpecSqlText("showsql");

        assertThat(profile.feedback(negativeFeedback(sqlText, NEGATIVE_RESPONSE_WITH_NAME_ORDER,
                "print in ascending order of name"))).isTrue();

        assertThat(profile.showprompt(PROMPT)).contains(NEGATIVE_RESPONSE_WITH_NAME_ORDER);
    }

    /**
     * Test: Looks up the warmed SHOWSQL SQL ID "ahgttusrvh9x5", submits a NEGATIVE/ADD feedback
     * payload with the descending-total-points SQL response, and then calls showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt output contains NEGATIVE_RESPONSE.
     */
    @Test
    void test19001NegativeFeedbackUsingShowsqlSqlIdAddsResponseToShowprompt() throws Exception {
        addNegativeFeedbackUsingSqlId("showsql", NEGATIVE_RESPONSE, "print in descending order of total_points");

        assertThat(profile.showprompt(PROMPT)).contains(NEGATIVE_RESPONSE);
    }

    /**
     * Test: Looks up the warmed RUNSQL SQL ID "6s20ukn8j3p5j", submits NEGATIVE/ADD feedback
     * with NEGATIVE_RESPONSE and the descending-total-points explanation, and calls showprompt.
     * Expected: feedback() returns true and showprompt(PROMPT) contains NEGATIVE_RESPONSE.
     */
    @Test
    void test19002NegativeFeedbackUsingRunsqlSqlIdAddsResponseToShowprompt() throws Exception {
        addNegativeFeedbackUsingSqlId("runsql", NEGATIVE_RESPONSE, "print in descending order of total_points");

        assertThat(profile.showprompt(PROMPT)).contains(NEGATIVE_RESPONSE);
    }

    /**
     * Test: Looks up the warmed EXPLAINSQL SQL ID "2a617cynwfm36", submits NEGATIVE/ADD feedback
     * with NEGATIVE_RESPONSE and the descending-total-points explanation, and calls showprompt.
     * Expected: feedback() returns true and showprompt(PROMPT) contains NEGATIVE_RESPONSE.
     */
    @Test
    void test19003NegativeFeedbackUsingExplainsqlSqlIdAddsResponseToShowprompt() throws Exception {
        addNegativeFeedbackUsingSqlId("explainsql", NEGATIVE_RESPONSE, "print in descending order of total_points");

        assertThat(profile.showprompt(PROMPT)).contains(NEGATIVE_RESPONSE);
    }

    /**
     * Test: Builds NEGATIVE/ADD feedback for the warmed SHOWSQL SQL ID "ahgttusrvh9x5" with
     * response=NEGATIVE_RESPONSE and no feedbackContent, then calls profile.feedback() and
     * showprompt(PROMPT).
     * Expected: The database accepts the payload (feedback() returns true) and showprompt contains
     * the supplied corrected response even though feedbackContent was omitted.
     */
    @Test
    void test19004NegativeFeedbackWithSqlIdAllowsMissingFeedbackContent() throws Exception {
        String sqlId = sqlIdForAction("showsql");

        assertThat(profile.feedback(Feedback.builder()
                .sqlId(sqlId)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(NEGATIVE_RESPONSE)
                .build())).isTrue();

        assertThat(profile.showprompt(PROMPT)).contains(NEGATIVE_RESPONSE);
    }

    /**
     * Test: Builds NEGATIVE/ADD feedback for the SHOWSQL text
     * "select ai showsql Total points of each gymnasts" with response=NEGATIVE_RESPONSE and no
     * feedbackContent, then submits it and reads showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains the corrected response without
     * requiring feedbackContent.
     */
    @Test
    void test19005NegativeFeedbackWithSqlTextAllowsMissingFeedbackContent() throws Exception {
        String sqlText = promptSpecSqlText("showsql");

        assertThat(profile.feedback(Feedback.builder()
                .sqlText(sqlText)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(NEGATIVE_RESPONSE)
                .build())).isTrue();

        assertThat(profile.showprompt(PROMPT)).contains(NEGATIVE_RESPONSE);
    }



    /**
     * Test: Builds NEGATIVE/ADD feedback with sqlText "select * from gymnast", feedbackContent
     * "missing corrected SQL response", and no response field.
     * Expected: Feedback.builder().build() throws IllegalArgumentException with the exact message
     * "response is required when feedbackType is negative and operation is ADD" before feedback()
     * can call the database.
     */
    @Test
    void test19006NegativeFeedbackRejectsMissingResponseBeforeDatabaseCall() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlText("select * from gymnast")
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .feedbackContent("missing corrected SQL response")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("response is required when feedbackType is negative and operation is ADD");
    }

    /**
     * Test: Builds POSITIVE feedback with both sqlId="conflicting" and sqlText="select * from
     * gymnast".
     * Expected: Feedback.builder().build() throws IllegalArgumentException with
     * "Provide only one of sqlId or sqlText", preventing a conflicting payload from reaching JDBC.
     */
    @Test
    void test19007FeedbackRejectsConflictingSqlIdAndSqlTextBeforeDatabaseCall() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("conflicting")
                .sqlText("select * from gymnast")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provide only one of sqlId or sqlText");
    }

    /**
     * Test: Submits NEGATIVE/ADD feedback for the previously unseen SQL text
     * "select ai showsql Adding negative feedback with non existent prompt", using NEGATIVE_RESPONSE
     * and the feedbackContent "non-existent prompt equivalent through Java sqlText path", then
     * calls showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains the corrected response.
     */
    @Test
    void test19008NegativeFeedbackUsingUnseenSqlTextAddsResponseToShowprompt() throws Exception {
        assertThat(profile.feedback(negativeFeedback(
                "select ai showsql Adding negative feedback with non existent prompt",
                NEGATIVE_RESPONSE,
                "non-existent prompt equivalent through Java sqlText path"))).isTrue();

        assertThat(profile.showprompt(PROMPT)).contains(NEGATIVE_RESPONSE);
    }

    /**
     * Test: Submits POSITIVE/ADD feedback by SHOWSQL sqlText for
     * "select ai showsql Total points of each gymnasts", then reads showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains both feedback metadata fields
     * "sql_query" and "user_prompt".
     */
    @Test
    void test19009PositiveFeedbackUsingShowsqlSqlTextAddsFeedbackMetadataToShowprompt() throws Exception {
        String sqlText = promptSpecSqlText("showsql");

        assertThat(profile.feedback(positiveFeedbackBySqlText(sqlText))).isTrue();

        assertShowpromptContainsFeedbackMetadata();
    }

    /**
     * Test: Submits POSITIVE/ADD feedback using the warmed SHOWSQL SQL ID "ahgttusrvh9x5", then
     * calls showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains the "sql_query" and "user_prompt"
     * feedback metadata fields.
     */
    @Test
    void test19010PositiveFeedbackUsingShowsqlSqlIdAddsFeedbackMetadataToShowprompt() throws Exception {
        addPositiveFeedbackUsingSqlId("showsql");

        assertShowpromptContainsFeedbackMetadata();
    }

    /**
     * Test: Submits POSITIVE/ADD feedback by RUNSQL sqlText for
     * "select ai runsql Total points of each gymnasts", then reads showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains "sql_query" and "user_prompt".
     */
    @Test
    void test19011PositiveFeedbackUsingRunsqlSqlTextAddsFeedbackMetadataToShowprompt() throws Exception {
        String sqlText = promptSpecSqlText("runsql");

        assertThat(profile.feedback(positiveFeedbackBySqlText(sqlText))).isTrue();

        assertShowpromptContainsFeedbackMetadata();
    }

    /**
     * Test: Submits POSITIVE/ADD feedback by EXPLAINSQL sqlText for
     * "select ai explainsql Total points of each gymnasts", then reads showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains the "sql_query" and "user_prompt"
     * fields emitted for the feedback record.
     */
    @Test
    void test19012PositiveFeedbackUsingExplainsqlSqlTextAddsFeedbackMetadataToShowprompt() throws Exception {
        String sqlText = promptSpecSqlText("explainsql");

        assertThat(profile.feedback(positiveFeedbackBySqlText(sqlText))).isTrue();

        assertShowpromptContainsFeedbackMetadata();
    }

    /**
     * Test: Sends the complete POSITIVE/ADD payload for SHOWSQL text, including
     * response=NEGATIVE_RESPONSE and feedbackContent="Positive feedback includes response details".
     * Expected: profile.feedback() returns true, confirming the database accepts the positive
     * feedback type with both optional response and feedbackContent populated.
     */
    @Test
    void test19013PositiveFeedbackWithResponseAndFeedbackContentSucceedsThroughRealFeedbackApi() throws Exception {
        assertThat(profile.feedback(Feedback.builder()
                .sqlText(promptSpecSqlText("showsql"))
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(NEGATIVE_RESPONSE)
                .feedbackContent("Positive feedback includes response details")
                .build())).isTrue();
    }

    /**
     * Test: Submits POSITIVE/ADD feedback for the unknown SHOWSQL text
     * "select ai showsql Adding positive feedback with non existent prompt".
     * Expected: profile.feedback() throws SelectAIException with error code 20000 and cause
     * message "ORA-20000: No matching SQL statement found for the SQL_ID or SQL text.".
     */
    @Test
    void test19014PositiveFeedbackForNonExistentShowsqlPromptFailsThroughRealFeedbackApi() {
        String sqlText = "select ai showsql Adding positive feedback with non existent prompt";

        assertThatThrownBy(() -> profile.feedback(positiveFeedbackBySqlText(sqlText)))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).hasMessageContaining(
                            "ORA-20000: No matching SQL statement found for the SQL_ID or SQL text.");
                    assertThat(exception.getErrorCode()).isEqualTo(20000);
                });
    }
    

    /**
     * Test: Submits POSITIVE/ADD feedback using the warmed RUNSQL SQL ID "6s20ukn8j3p5j", then
     * reads showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains "sql_query" and "user_prompt".
     */
    @Test
    void test19015PositiveFeedbackUsingRunsqlSqlIdAddsFeedbackMetadataToShowprompt() throws Exception {
        addPositiveFeedbackUsingSqlId("runsql");

        assertShowpromptContainsFeedbackMetadata();
    }

    /**
     * Test: Submits POSITIVE/ADD feedback using the warmed EXPLAINSQL SQL ID "2a617cynwfm36",
     * then reads showprompt(PROMPT).
     * Expected: feedback() returns true and showprompt contains the "sql_query" and "user_prompt"
     * feedback metadata fields.
     */
    @Test
    void test19016PositiveFeedbackUsingExplainsqlSqlIdAddsFeedbackMetadataToShowprompt() throws Exception {
        addPositiveFeedbackUsingSqlId("explainsql");

        assertShowpromptContainsFeedbackMetadata();
    }

    /**
     * Test: Submits POSITIVE/ADD feedback with sqlId="unknownsqlid".
     * Expected: profile.feedback() throws SelectAIException with error code 20000 and cause
     * message "ORA-20000: No matching SQL statement found for the SQL_ID or SQL text.".
     */
    @Test
    void test19017PositiveFeedbackForUnknownSqlIdFailsThroughRealFeedbackApi() {
        assertThatThrownBy(() -> profile.feedback(Feedback.builder()
                .sqlId("unknownsqlid")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .operation(Feedback.Operation.ADD.name())
                .build()))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).hasMessageContaining(
                            "ORA-20000: No matching SQL statement found for the SQL_ID or SQL text.");
                    assertThat(exception.getErrorCode()).isEqualTo(20000);
                });
    }

    /**
     * Test: Submits NEGATIVE/ADD feedback with sqlId="unknownsqlid", response=NEGATIVE_RESPONSE,
     * and feedbackContent="Feedback for an unknown SQL ID".
     * Expected: profile.feedback() throws SelectAIException with error code 20000 and cause
     * message "ORA-20000: No matching SQL statement found for the SQL_ID or SQL text.".
     */
    @Test
    void test19018NegativeFeedbackForUnknownSqlIdFailsThroughRealFeedbackApi() {
        assertThatThrownBy(() -> profile.feedback(Feedback.builder()
                .sqlId("unknownsqlid")
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(NEGATIVE_RESPONSE)
                .feedbackContent("Feedback for an unknown SQL ID")
                .build()))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).hasMessageContaining(
                            "ORA-20000: No matching SQL statement found for the SQL_ID or SQL text.");
                    assertThat(exception.getErrorCode()).isEqualTo(20000);
                });
    }

    /**
     * Test: Adds POSITIVE/ADD feedback for SHOWSQL sqlText, then submits DELETE feedback for the
     * same sqlText and calls showprompt(PROMPT).
     * Expected: Both feedback() calls return true and showprompt contains PROMPT only once after
     * deletion, indicating the feedback metadata is no longer attached to that prompt.
     */
    @Test
    void test19019DeleteFeedbackBySqlTextAfterPositiveFeedbackRemovesPromptMetadata() throws Exception {
        String sqlText = promptSpecSqlText("showsql");
        assertThat(profile.feedback(positiveFeedbackBySqlText(sqlText))).isTrue();

        assertThat(profile.feedback(deleteFeedbackBySqlText(sqlText))).isTrue();

        assertFeedbackRemovedFromShowprompt();
    }

    /**
     * Test: Adds POSITIVE/ADD feedback for EXPLAINSQL sqlText, submits DELETE feedback for the same
     * sqlText, and calls showprompt(PROMPT).
     * Expected: Both operations return true and showprompt contains PROMPT only once after the
     * feedback record is deleted.
     */
    @Test
    void test19020DeleteFeedbackBySqlTextAfterPositiveExplainsqlFeedbackRemovesPromptMetadata() throws Exception {
        String sqlText = promptSpecSqlText("explainsql");
        assertThat(profile.feedback(positiveFeedbackBySqlText(sqlText))).isTrue();

        assertThat(profile.feedback(deleteFeedbackBySqlText(sqlText))).isTrue();

        assertFeedbackRemovedFromShowprompt();
    }

    /**
     * Test: Adds POSITIVE/ADD feedback for the warmed SHOWSQL SQL ID "ahgttusrvh9x5", submits
     * DELETE feedback for that same SQL ID, and reads showprompt(PROMPT).
     * Expected: Both feedback() calls return true and the resulting showprompt contains PROMPT only
     * once, with no remaining feedback metadata.
     */
    @Test
    void test19021DeleteFeedbackBySqlIdAfterPositiveFeedbackRemovesPromptMetadata() throws Exception {
        String sqlId = sqlIdForAction("showsql");
        assertThat(profile.feedback(positiveFeedbackBySqlId(sqlId))).isTrue();

        assertThat(profile.feedback(deleteFeedbackBySqlId(sqlId))).isTrue();

        assertFeedbackRemovedFromShowprompt();
    }

    /**
     * Test: Adds NEGATIVE/ADD feedback for the warmed SHOWSQL SQL ID "ahgttusrvh9x5" with
     * NEGATIVE_RESPONSE and "Feedback prior to delete", then submits DELETE feedback by that ID.
     * Expected: Both operations return true and showprompt(PROMPT) contains PROMPT only once after
     * the negative feedback is removed.
     */
    @Test
    void test19022DeleteFeedbackBySqlIdAfterNegativeShowsqlFeedbackRemovesPromptMetadata() throws Exception {
        String sqlId = sqlIdForAction("showsql");
        assertThat(profile.feedback(Feedback.builder()
                .sqlId(sqlId)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(NEGATIVE_RESPONSE)
                .feedbackContent("Feedback prior to delete")
                .build())).isTrue();

        assertThat(profile.feedback(deleteFeedbackBySqlId(sqlId))).isTrue();

        assertFeedbackRemovedFromShowprompt();
    }

    /**
     * Test: Adds NEGATIVE/ADD feedback for the warmed RUNSQL SQL ID "6s20ukn8j3p5j" with
     * NEGATIVE_RESPONSE and "Feedback prior to delete", then deletes feedback by the same ID.
     * Expected: Both feedback() calls return true and showprompt(PROMPT) contains PROMPT only once.
     */
    @Test
    void test19023DeleteFeedbackBySqlIdAfterNegativeFeedbackRemovesPromptMetadata() throws Exception {
        String sqlId = sqlIdForAction("runsql");
        assertThat(profile.feedback(Feedback.builder()
                .sqlId(sqlId)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(NEGATIVE_RESPONSE)
                .feedbackContent("Feedback prior to delete")
                .build())).isTrue();

        assertThat(profile.feedback(deleteFeedbackBySqlId(sqlId))).isTrue();

        assertFeedbackRemovedFromShowprompt();
    }

    /**
     * Test: Adds NEGATIVE/ADD feedback for the warmed EXPLAINSQL SQL ID "2a617cynwfm36" with
     * NEGATIVE_RESPONSE and "Feedback prior to delete", then deletes feedback by the same ID.
     * Expected: Both feedback() calls return true and showprompt(PROMPT) contains PROMPT only once.
     */
    @Test
    void test19024DeleteFeedbackBySqlIdAfterNegativeExplainsqlFeedbackRemovesPromptMetadata() throws Exception {
        String sqlId = sqlIdForAction("explainsql");
        assertThat(profile.feedback(Feedback.builder()
                .sqlId(sqlId)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(NEGATIVE_RESPONSE)
                .feedbackContent("Feedback prior to delete")
                .build())).isTrue();

        assertThat(profile.feedback(deleteFeedbackBySqlId(sqlId))).isTrue();

        assertFeedbackRemovedFromShowprompt();
    }

    /**
     * Test: Submits DELETE feedback with sqlId="unknownsqlid" for a feedback record that does not
     * exist.
     * Expected: profile.feedback() throws SelectAIException with error code 20000 and cause
     * message "ORA-20000: No matching feedback record for deletion.".
     */
    @Test
    void test19025DeleteFeedbackForUnknownSqlIdFailsThroughRealFeedbackApi() {
        assertThatThrownBy(() -> profile.feedback(deleteFeedbackBySqlId("unknownsqlid")))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).hasMessageContaining(
                            "ORA-20000: No matching feedback record for deletion.");
                    assertThat(exception.getErrorCode()).isEqualTo(20000);
                });
    }

    /**
     * Test: Submits DELETE feedback for sqlText "select * from non_existent_feedback_sql", which
     * has no stored feedback record.
     * Expected: profile.feedback() throws SelectAIException with error code 20000 and cause
     * message "ORA-20000: No matching feedback record for deletion.".
     */
    @Test
    void test19026DeleteFeedbackForUnknownSqlTextFailsThroughRealFeedbackApi() {
        assertThatThrownBy(() -> profile.feedback(
                deleteFeedbackBySqlText("select * from non_existent_feedback_sql")))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).hasMessageContaining(
                            "ORA-20000: No matching feedback record for deletion.");
                    assertThat(exception.getErrorCode()).isEqualTo(20000);
                });
    }

    /**
     * Test: Builds DELETE feedback with both sqlId="conflicting" and sqlText="select * from
     * gymnast".
     * Expected: Feedback.builder().build() throws IllegalArgumentException with
     * "Provide only one of sqlId or sqlText", so the conflicting delete payload is rejected before
     * profile.feedback() can execute JDBC.
     */
    @Test
    void test19027DeleteFeedbackRejectsConflictingSqlIdAndSqlTextBeforeDatabaseCall() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("conflicting")
                .sqlText("select * from gymnast")
                .operation(Feedback.Operation.DELETE.name())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provide only one of sqlId or sqlText");
    }

    /**
     * Test: Submits negative feedback with response and feedback-content values larger than the
     * PL/SQL VARCHAR2 bind limit using a known SQL ID, then removes the created feedback record.
     * Expected: DBMS_CLOUD_AI accepts the CLOB values without truncation.
     */
     @Test
    void test19028NegativeFeedbackWithLongResponseAndFeedbackContentUsesClobParameters() throws Exception {
        String sqlId = sqlIdForAction("runsql");
        String response = NEGATIVE_RESPONSE + " /* " + "r".repeat(40_000) + " */";
        String feedbackContent = "Long feedback content " + "c".repeat(40_000);

        assertThat(profile.feedback(Feedback.builder()
                .sqlId(sqlId)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(response)
                .feedbackContent(feedbackContent)
                .build())).isTrue();

        assertThat(profile.feedback(deleteFeedbackBySqlId(sqlId))).isTrue();
    }


    private Feedback negativeFeedback(String sqlText, String response, String feedbackContent) {
        return Feedback.builder()
                .sqlText(sqlText)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(response)
                .feedbackContent(feedbackContent)
                .build();
    }

    private String promptSpecSqlText(String action) {
        return "select ai " + action + " " + PROMPT;
    }

    private Feedback positiveFeedbackBySqlText(String sqlText) {
        return Feedback.builder()
                .sqlText(sqlText)
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .operation(Feedback.Operation.ADD.name())
                .build();
    }

    private Feedback positiveFeedbackBySqlId(String sqlId) {
        return Feedback.builder()
                .sqlId(sqlId)
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .operation(Feedback.Operation.ADD.name())
                .build();
    }

    private Feedback deleteFeedbackBySqlText(String sqlText) {
        return Feedback.builder()
                .sqlText(sqlText)
                .operation(Feedback.Operation.DELETE.name())
                .build();
    }

    private Feedback deleteFeedbackBySqlId(String sqlId) {
        return Feedback.builder()
                .sqlId(sqlId)
                .operation(Feedback.Operation.DELETE.name())
                .build();
    }

    private void addNegativeFeedbackUsingSqlId(String action, String response, String feedbackContent)
            throws SelectAIException {
        String sqlId = sqlIdForAction(action);
        assertThat(profile.feedback(Feedback.builder()
                .sqlId(sqlId)
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .operation(Feedback.Operation.ADD.name())
                .response(response)
                .feedbackContent(feedbackContent)
                .build())).isTrue();
    }

    private void addPositiveFeedbackUsingSqlId(String action) throws SelectAIException {
        assertThat(profile.feedback(positiveFeedbackBySqlId(sqlIdForAction(action)))).isTrue();
    }

    private String sqlIdForAction(String action) {
        return switch (action) {
            case "showsql" -> SHOWSQL_SQL_ID;
            case "runsql" -> RUNSQL_SQL_ID;
            case "explainsql" -> EXPLAINSQL_SQL_ID;
            default -> throw new IllegalArgumentException("Unsupported feedback action: " + action);
        };
    }

    private void assertShowpromptContainsFeedbackMetadata() throws SelectAIException {
        String showprompt = profile.showprompt(PROMPT);
        assertThat(showprompt).contains("sql_query", "user_prompt");
    }

    private void assertFeedbackRemovedFromShowprompt() throws SelectAIException {
        assertThat(profile.showprompt(PROMPT)).containsOnlyOnce(PROMPT);
    }

}
