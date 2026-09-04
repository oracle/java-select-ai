/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.util.Locale;

/**
 * Immutable request payload for {@code DBMS_CLOUD_AI.FEEDBACK}.
 * <p>
 * Use this request object when an application wants to tell Select AI whether
 * generated SQL was useful, provide a correction for a poor NL2SQL answer, or
 * remove previously submitted guidance. Feedback is profile-specific prompt
 * guidance for future SQL generation, not model fine-tuning and not a general
 * rating mechanism for chat or RAG responses. The target profile is supplied by
 * the {@code Profile} object used to submit the request. A feedback request
 * identifies one generated SQL statement either by its database SQL identifier
 * or by the SQL text itself.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI feedback reference</a>
 */
public final class Feedback {

    /**
     * Action to perform on the feedback store.
     */
    public enum Operation {
        /**
         * Stores feedback for a generated SQL statement.
         */
        ADD("add"),

        /**
         * Removes feedback that was previously stored for a generated SQL statement.
         */
        DELETE("delete");

        /** Database value expected by DBMS_CLOUD_AI.FEEDBACK. */
        private final String databaseValue;

        Operation(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        /**
         * Returns the lowercase database value.
         *
         * @return DBMS_CLOUD_AI.FEEDBACK operation value
         */
        public String getDatabaseValue() {
            return databaseValue;
        }

        /**
         * Parses user-supplied operation text.
         *
         * @param value action text such as {@code add} or {@code delete}
         * @return parsed operation, or {@code null} when input is null/blank so
         *         DBMS_CLOUD_AI can apply its default
         */
        public static Operation from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            try {
                return Operation.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("operation must be add or delete", e);
            }
        }
    }

    /**
     * User assessment of generated SQL accepted by DBMS_CLOUD_AI.FEEDBACK.
     */
    public enum FeedbackType {
        /**
         * Marks generated SQL as useful or correct.
         */
        POSITIVE("positive"),

        /**
         * Marks generated SQL as incorrect or requiring guidance.
         */
        NEGATIVE("negative");

        /** Database value expected by DBMS_CLOUD_AI.FEEDBACK. */
        private final String databaseValue;

        FeedbackType(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        /**
         * Returns the lowercase database value.
         *
         * @return DBMS_CLOUD_AI.FEEDBACK feedback_type value
         */
        public String getDatabaseValue() {
            return databaseValue;
        }

        /**
         * Parses user-supplied feedback type text.
         *
         * @param value feedback type text such as {@code positive} or {@code negative}
         * @return parsed feedback type, or {@code null} when input is null/blank
         */
        public static FeedbackType from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            try {
                return FeedbackType.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("feedbackType must be positive or negative", e);
            }
        }
    }

    /**
     * Database SQL identifier for the generated statement.
     * <p>
     * Use this when the generated SQL has an identifier available from database
     * execution or query history. Provide either this value or the SQL text, not
     * both.
     */
    private final String sqlId;

    /**
     * Generated SQL statement text.
     * <p>
     * Use this when the SQL statement should be reviewed but a database SQL
     * identifier is not available. Provide either this value or the SQL
     * identifier, not both. This value may contain customer data; SDK code
     * must not log it.
     */
    private final String sqlText;

    /**
     * User's assessment of the generated SQL.
     * <p>
     * Use positive feedback to accept a good generated statement. Use negative
     * feedback when the statement needs a correction or better guidance.
     * Required when adding feedback.
     */
    private final FeedbackType feedbackType;

    /**
     * Expected or corrected response for the reviewed SQL.
     * <p>
     * This is especially important for negative feedback because it gives Select
     * AI a concrete target for what the generated SQL should have produced.
     * This value may contain customer data; SDK code must not log it.
     */
    private final String response;

    /**
     * Natural-language feedback from the user.
     * <p>
     * Use this field for comments such as why the SQL was correct, what was
     * wrong, or how the query should be improved. This value may contain
     * customer data; SDK code must not log it.
     */
    private final String feedbackContent;

    /**
     * Requested action for the feedback request.
     */
    private final Operation operation;

    private Feedback(Builder builder) {
        this.sqlId = builder.sqlId;
        this.sqlText = builder.sqlText;
        this.feedbackType = builder.feedbackType;
        this.response = builder.response;
        this.feedbackContent = builder.feedbackContent;
        this.operation = Operation.from(builder.operation);

        if (!hasValue(sqlId) && !hasValue(sqlText)) {
            throw new IllegalArgumentException("Either sqlId or sqlText must be provided");
        }

        if (hasValue(sqlId) && hasValue(sqlText)) {
            throw new IllegalArgumentException("Provide only one of sqlId or sqlText");
        }

        if (operation == Operation.ADD) {
            if (feedbackType == null) {
                throw new IllegalArgumentException("feedbackType is required when operation is ADD");
            }
            if (feedbackType == FeedbackType.NEGATIVE && !hasValue(response)) {
                throw new IllegalArgumentException(
                        "response is required when feedbackType is negative and operation is ADD");
            }
        } else if (operation == Operation.DELETE
                && (feedbackType != null || hasValue(response) || hasValue(feedbackContent))) {
            throw new IllegalArgumentException(
                    "DELETE feedback must not include feedbackType, response, or feedbackContent");
        }
    }

    /**
     * Returns whether a string contains non-blank content.
     *
     * @param value value to inspect
     * @return {@code true} when the value is non-null and non-blank
     */
    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Returns the database SQL identifier used to locate the reviewed SQL.
     *
     * @return SQL identifier associated with the feedback, or {@code null} when SQL text is used instead
     */
    public String getSqlId() {
        return sqlId;
    }

    /**
     * Returns the generated SQL text being reviewed.
     *
     * @return SQL text associated with the feedback, or {@code null} when SQL identifier is used instead
     */
    public String getSqlText() {
        return sqlText;
    }

    /**
     * Returns whether the generated SQL was marked useful or needing correction.
     *
     * @return normalized feedback type
     */
    public FeedbackType getFeedbackType() {
        return feedbackType;
    }

    /**
     * Returns the lowercase database value for {@code feedback_type}.
     *
     * @return {@code positive}, {@code negative}, or {@code null}
     */
    public String getFeedbackTypeValue() {
        return feedbackType == null ? null : feedbackType.getDatabaseValue();
    }

    /**
     * Returns the expected or corrected response for the reviewed SQL.
     *
     * @return expected SQL response; required for negative {@code ADD} feedback
     */
    public String getResponse() {
        return response;
    }

    /**
     * Returns the user's natural-language explanation for the feedback.
     *
     * @return optional natural-language feedback content
     */
    public String getFeedbackContent() {
        return feedbackContent;
    }

    /**
     * Returns the normalized action to perform on the feedback store.
     *
     * @return normalized feedback operation enum, or {@code null} when DBMS_CLOUD_AI
     *         should apply its default operation
     */
    public Operation getOperation() {
        return operation;
    }

    /**
     * Returns the database value for the requested operation.
     *
     * @return lowercase operation value, or {@code null} when DBMS_CLOUD_AI should apply
     *         its default operation
     */
    public String getOperationValue() {
        return operation == null ? null : operation.getDatabaseValue();
    }

    /**
     * Creates a builder for a feedback request.
     *
     * @return new builder for constructing validated Feedback instances
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link Feedback} request payloads.
     */
    public static final class Builder {
        /** Optional database SQL identifier for the generated statement. */
        private String sqlId;
        /** Optional generated SQL text when an identifier is not available. */
        private String sqlText;
        /** User assessment of the generated SQL. */
        private FeedbackType feedbackType;
        /** Expected or corrected response for negative feedback. */
        private String response;
        /** Natural-language feedback notes. */
        private String feedbackContent;
        /** Action text before it is normalized into {@link Operation}. */
        private String operation;

        private Builder() {
        }

        /**
         * Identifies the reviewed SQL by its database SQL identifier.
         * <p>
         * Use this when the generated SQL can be identified by SQL ID. Do not
         * also set {@link #sqlText(String)}.
         *
         * @param sqlId SQL_ID value
         * @return this builder instance
         */
        public Builder sqlId(String sqlId) {
            this.sqlId = sqlId;
            return this;
        }

        /**
         * Identifies the reviewed SQL by its full statement text.
         * <p>
         * Use this when the generated SQL should be identified by its full SQL
         * text. Do not also set {@link #sqlId(String)}.
         *
         * @param sqlText generated SQL text
         * @return this builder instance
         */
        public Builder sqlText(String sqlText) {
            this.sqlText = sqlText;
            return this;
        }

        /**
         * Sets whether the generated SQL was useful or needs correction.
         *
         * @param feedbackType feedback type, typically {@code positive} or {@code negative}
         * @return this builder instance
         */
        public Builder feedbackType(String feedbackType) {
            this.feedbackType = FeedbackType.from(feedbackType);
            return this;
        }

        /**
         * Sets whether the generated SQL was useful or needs correction.
         *
         * @param feedbackType feedback type enum
         * @return this builder instance
         */
        public Builder feedbackType(FeedbackType feedbackType) {
            this.feedbackType = feedbackType;
            return this;
        }

        /**
         * Sets the expected or corrected response for the reviewed SQL.
         *
         * @param response expected SQL result or corrected SQL response
         * @return this builder instance
         */
        public Builder response(String response) {
            this.response = response;
            return this;
        }

        /**
         * Sets the natural-language explanation for the feedback.
         *
         * @param feedbackContent natural-language feedback notes or revised SQL guidance
         * @return this builder instance
         */
        public Builder feedbackContent(String feedbackContent) {
            this.feedbackContent = feedbackContent;
            return this;
        }

        /**
         * Sets the action to perform on the feedback store.
         * <p>
         * Null or blank input is preserved so {@code DBMS_CLOUD_AI.FEEDBACK}
         * can apply its database default. Non-blank values are parsed into
         * {@link Operation#ADD} or {@link Operation#DELETE}.
         *
         * @param operation operation text, for example {@code add} or {@code delete}
         * @return this builder instance
         */
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        /**
         * Sets the action to perform on the feedback store.
         *
         * @param operation operation enum, or {@code null} to let
         *        {@code DBMS_CLOUD_AI.FEEDBACK} apply its database default
         * @return this builder instance
         */
        public Builder operation(Operation operation) {
            this.operation = operation == null ? null : operation.name();
            return this;
        }

        /**
         * Builds and validates the feedback request.
         * <p>
         * A valid request must identify the generated SQL exactly one way, must
         * include an assessment when storing feedback, must include an
         * expected/corrected response when storing negative feedback, and must
         * not include add-only fields when deleting feedback.
         *
         * @return validated immutable Feedback request
         * @throws IllegalArgumentException when the request is invalid
         */
        public Feedback build() {
            return new Feedback(this);
        }
    }
}
