/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackTest {

    /**
     * Test: Normalize operation values and handle null or blank input.
     * Expected: Supported values map to operations, while null and blank input return null.
     */
    @Test
    void operationFromNullOrBlankReturnsNullAndNormalizesText() {
        assertThat(Feedback.Operation.from(null)).isNull();
        assertThat(Feedback.Operation.from(" ")).isNull();
        assertThat(Feedback.Operation.from(" add ")).isEqualTo(Feedback.Operation.ADD);
        assertThat(Feedback.Operation.from(" aDd ")).isEqualTo(Feedback.Operation.ADD);
        assertThat(Feedback.Operation.from("delete")).isEqualTo(Feedback.Operation.DELETE);
        assertThat(Feedback.Operation.from("DeLeTe")).isEqualTo(Feedback.Operation.DELETE);
        assertThat(Feedback.Operation.ADD.getDatabaseValue()).isEqualTo("add");
        assertThat(Feedback.Operation.DELETE.getDatabaseValue()).isEqualTo("delete");
    }

    /**
     * Test: Reject an unsupported feedback operation.
     * Expected: IllegalArgumentException identifies the supported operation values.
     */
    @Test
    void operationFromRejectsUnsupportedValues() {
        assertThatThrownBy(() -> Feedback.Operation.from("archive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add or delete");
    }

    /**
     * Test: Normalize feedback type values and handle null or blank input.
     * Expected: Supported values map to feedback types, while null and blank input return null.
     */
    @Test
    void feedbackTypeFromDefaultsAndNormalizesText() {
        assertThat(Feedback.FeedbackType.from(null)).isNull();
        assertThat(Feedback.FeedbackType.from(" ")).isNull();
        assertThat(Feedback.FeedbackType.from("positive")).isEqualTo(Feedback.FeedbackType.POSITIVE);
        assertThat(Feedback.FeedbackType.from("PoSiTiVe")).isEqualTo(Feedback.FeedbackType.POSITIVE);
        assertThat(Feedback.FeedbackType.from(" negative ")).isEqualTo(Feedback.FeedbackType.NEGATIVE);
        assertThat(Feedback.FeedbackType.from(" NeGaTiVe ")).isEqualTo(Feedback.FeedbackType.NEGATIVE);
    }

    /**
     * Test: Reject an unsupported feedback type.
     * Expected: IllegalArgumentException identifies the supported feedback types.
     */
    @Test
    void feedbackTypeFromRejectsUnsupportedValues() {
        assertThatThrownBy(() -> Feedback.FeedbackType.from("neutral"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive or negative");
    }

    /**
     * Test: Build positive SQL-text feedback without an explicit operation.
     * Expected: The configured fields are preserved and the operation remains null.
     */
    @Test
    void builderPreservesNullOperationAndKeepsPositiveSqlTextFeedbackValues() {
        Feedback feedback = Feedback.builder()
                .sqlText("select * from employees")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .feedbackContent("Looks right")
                .build();

        assertThat(feedback.getOperation()).isNull();
        assertThat(feedback.getFeedbackType()).isEqualTo(Feedback.FeedbackType.POSITIVE);
        assertThat(feedback.getFeedbackTypeValue()).isEqualTo("positive");
        assertThat(feedback.getSqlText()).isEqualTo("select * from employees");
        assertThat(feedback.getSqlId()).isNull();
        assertThat(feedback.getFeedbackContent()).isEqualTo("Looks right");
    }

    /**
     * Test: Build negative ADD feedback through the string-based builder API.
     * Expected: The operation, feedback type, response, and content are retained using database values.
     */
    @Test
    void builderBuildsNegativeAddFeedbackAndExposesDatabaseValues() {
        Feedback feedback = Feedback.builder()
                .sqlId("abc123")
                .operation(" add ")
                .feedbackType(" negative ")
                .response("select count(*) from employees")
                .feedbackContent("Use the employee table")
                .build();

        assertThat(feedback.getOperation()).isEqualTo(Feedback.Operation.ADD);
        assertThat(feedback.getOperationValue()).isEqualTo("add");
        assertThat(feedback.getFeedbackType()).isEqualTo(Feedback.FeedbackType.NEGATIVE);
        assertThat(feedback.getFeedbackTypeValue()).isEqualTo("negative");
        assertThat(Feedback.FeedbackType.NEGATIVE.getDatabaseValue()).isEqualTo("negative");
        assertThat(feedback.getResponse()).isEqualTo("select count(*) from employees");
        assertThat(feedback.getFeedbackContent()).isEqualTo("Use the employee table");
    }

    /**
     * Test: Build delete feedback without a feedback type.
     * Expected: The delete operation and SQL identifier are retained, with feedback type unset.
     */
    @Test
    void builderAllowsDeleteWithoutFeedbackType() {
        Feedback feedback = Feedback.builder()
                .sqlId("abc123")
                .operation(Feedback.Operation.DELETE)
                .build();

        assertThat(feedback.getOperation()).isEqualTo(Feedback.Operation.DELETE);
        assertThat(feedback.getOperationValue()).isEqualTo("delete");
        assertThat(feedback.getFeedbackType()).isNull();
        assertThat(feedback.getFeedbackTypeValue()).isNull();
        assertThat(feedback.getSqlId()).isEqualTo("abc123");
    }

    /**
     * Test: Reject a feedback type on a delete operation.
     * Expected: IllegalArgumentException explains that feedbackType is invalid for DELETE.
     */
    @Test
    void builderRejectsFeedbackTypeForDeleteOperation() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .operation("delete")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feedbackType")
                .hasMessageContaining("DELETE");
    }

    /**
     * Test: Reject a response on a delete operation.
     * Expected: IllegalArgumentException explains that response is invalid for DELETE.
     */
    @Test
    void builderRejectsResponseForDeleteOperation() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .operation("delete")
                .response("select 1 from dual")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("response")
                .hasMessageContaining("DELETE");
    }

    /**
     * Test: Reject feedback content on a delete operation.
     * Expected: IllegalArgumentException explains that feedbackContent is invalid for DELETE.
     */
    @Test
    void builderRejectsFeedbackContentForDeleteOperation() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .operation("delete")
                .feedbackContent("irrelevant delete comment")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feedbackContent")
                .hasMessageContaining("DELETE");
    }

    /**
     * Test: Reject feedback without a SQL identifier or SQL text.
     * Expected: IllegalArgumentException requires either sqlId or sqlText.
     */
    @Test
    void builderRejectsMissingSqlIdentity() {
        assertThatThrownBy(() -> Feedback.builder()
                .feedbackType("positive")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either sqlId or sqlText");
    }

    /**
     * Test: Reject feedback containing both SQL identifier forms.
     * Expected: IllegalArgumentException requires only one SQL identity.
     */
    @Test
    void builderRejectsConflictingSqlIdentity() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .sqlText("select 1 from dual")
                .feedbackType("positive")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only one");
    }

    /**
     * Test: Require a feedback type when adding feedback.
     * Expected: IllegalArgumentException reports that feedbackType is required.
     */
    @Test
    void builderRequiresFeedbackTypeWhenAddingFeedback() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlText("select 1 from dual")
                .operation("add")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feedbackType is required");
    }

    /**
     * Test: Require a response for negative add feedback.
     * Expected: IllegalArgumentException reports that response is required.
     */
    @Test
    void builderRequiresResponseForNegativeAddFeedback() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlText("select 1 from dual")
                .operation("add")
                .feedbackType("negative")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("response is required");
    }

    /**
     * Test: Reject blank SQL identities and blank responses for negative add feedback.
     * Expected: Blank mandatory values fail validation before a request can reach JDBC.
     */
    @Test
    void builderRejectsBlankMandatoryFeedbackValues() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId(" ")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either sqlId or sqlText");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlText(" ")
                .feedbackType(Feedback.FeedbackType.POSITIVE)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either sqlId or sqlText");

        assertThatThrownBy(() -> Feedback.builder()
                .sqlText("select 1 from dual")
                .operation("add")
                .feedbackType(Feedback.FeedbackType.NEGATIVE)
                .response(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("response is required");
    }

    /**
     * Test: Reject an unsupported feedback operation.
     * Expected: IllegalArgumentException identifies the invalid operation.
     */
    @Test
    void builderRejectsUnsupportedOperationValues() {
        assertThatThrownBy(() -> Feedback.builder()
                .sqlId("abc123")
                .operation("archive")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add or delete");
    }

}
