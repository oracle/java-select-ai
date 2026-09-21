/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Attributes used to create or update a Select AI conversation.
 * <p>
 * Conversation attributes control the user-visible title and description,
 * conversation retention, and the number of conversation turns retained for
 * contextual follow-up prompts.
 * <p>
 * For complete runnable sample sources that build
 * {@code ConversationAttributes}, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/CreateConversationSample.html">
 * CreateConversationSample source</a> and
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/conversation/SetConversationAttributesSample.html">
 * SetConversationAttributesSample source</a>.
 *
 * <p>
 * The SDK performs basic, deterministic validation and normalization of
 * conversation attribute values where the constraint can be evaluated
 * independently of Oracle Database. Blank title and description values are
 * normalized to {@code null}. When specified, {@code retentionDays} must be
 * non-negative and {@code conversationLength} must be greater than zero.
 * Invalid values detected by the SDK result in an
 * {@link IllegalArgumentException}.
 *
 * <p>
 * Database-specific and database-version-specific semantic validation is
 * delegated to {@code DBMS_CLOUD_AI} and Oracle Database. Therefore, an
 * attribute value that passes SDK validation may still be rejected by the
 * database when the conversation is created or updated.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html">
 *      DBMS_CLOUD_AI conversation reference</a>
 */
public final class ConversationAttributes {
    /** Human-readable conversation title. */
    private final String title;
    /** Optional conversation description. */
    private final String description;
    /** Number of days Select AI should retain the conversation. */
    private final Integer retentionDays;
    /** Number of prompt/response pairs retained for conversation context. */
    private final Integer conversationLength;

    private ConversationAttributes(Builder builder) {
        this.title = builder.title;
        this.description = builder.description;
        this.retentionDays = builder.retentionDays;
        this.conversationLength = builder.conversationLength;
    }

    /**
     * Creates a builder for conversation attributes.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the configured conversation title.
     *
     * @return conversation title, or {@code null} when unset
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the optional conversation description.
     *
     * @return optional conversation description, or {@code null} when unset
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the conversation retention period in days.
     *
     * @return retention days value, or {@code null} when unset
     */
    public Integer getRetentionDays() {
        return retentionDays;
    }

    /**
     * Returns the configured conversation history length.
     *
     * @return optional conversation history length, or {@code null} when not configured
     */
    public Integer getConversationLength() {
        return conversationLength;
    }

    /**
     * Returns whether this instance contains no configured conversation attributes.
     *
     * @return {@code true} when no conversation attributes are configured
     */
    @JsonIgnore
    public boolean isEmpty() {
        return title == null
                && description == null
                && retentionDays == null
                && conversationLength == null;
    }

    /**
     * Converts attributes to DBMS_CLOUD_AI attribute names and string values.
     *
     * @return map representation containing only non-null attributes
     */
    public Map<String, String> toAttributeMap() {
        Map<String, String> map = new HashMap<>();
        putIfNotNull(map, "title", title);
        putIfNotNull(map, "description", description);
        putIfNotNull(map, "retention_days", retentionDays);
        putIfNotNull(map, "conversation_length", conversationLength);
        return map;
    }

    /**
     * Builds conversation attributes from database attribute rows.
     *
     * @param attributes map using DBMS_CLOUD_AI attribute names
     * @return ConversationAttributes instance built from recognized map keys
     */
    public static ConversationAttributes fromAttributeMap(Map<String, String> attributes) {
        Builder builder = ConversationAttributes.builder();
        if (attributes == null || attributes.isEmpty()) {
            return builder.build();
        }

        attributes.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.toLowerCase(Locale.ROOT);
            switch (key) {
                case "title" -> builder.title(v);
                case "description" -> builder.description(v);
                case "retention_days" -> builder.retentionDays(parseIntegerAttribute("retention_days", v));
                case "conversation_length" -> builder.conversationLength(parseIntegerAttribute("conversation_length", v));
                default -> {
                    // Ignore unknown attributes.
                }
            }
        });
        return builder.build();
    }

    /**
     * Adds a non-null attribute value to the output map.
     *
     * @param map destination attribute map
     * @param key DBMS_CLOUD_AI attribute name
     * @param value value to stringify and add
     */
    private static void putIfNotNull(Map<String, String> map, String key, Object value) {
        if (value != null) {
            map.put(key, String.valueOf(value));
        }
    }

    /**
     * Parses a database numeric conversation attribute value.
     *
     * @param attributeName DBMS_CLOUD_AI attribute name
     * @param value database value
     * @return parsed integer value
     * @throws IllegalArgumentException when value is not an integer
     */
    private static Integer parseIntegerAttribute(String attributeName, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(attributeName + " must be an integer", e);
        }
    }

    /**
     * Serializes attributes using snake_case JSON keys expected by DBMS_CLOUD_AI.
     *
     * @return JSON string representation of this object with snake_case keys and non-null fields
     */
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ConversationAttributes to JSON", e);
        }
    }

    /**
     * Returns a non-sensitive summary of configured conversation attributes.
     *
     * @return summary string that omits title and description values
     */
    @Override
    public String toString() {
        return "ConversationAttributes{"
                + "hasTitle=" + (title != null)
                + ", hasDescription=" + (description != null)
                + ", retentionDays=" + retentionDays
                + ", conversationLength=" + conversationLength
                + '}';
    }

    /**
     * Builder for {@link ConversationAttributes}.
     */
    public static final class Builder {
        /** Conversation title being assembled. */
        private String title;
        /** Conversation description being assembled. */
        private String description;
        /** Retention period in days being assembled. */
        private Integer retentionDays;
        /** Conversation history length being assembled. */
        private Integer conversationLength;

        private Builder() {
        }

        /**
         * Sets the conversation title.
         *
         * @param title conversation title
         * @return this builder instance
         */
        public Builder title(String title) {
            this.title = normalize(title);
            return this;
        }

        /**
         * Sets the conversation description.
         *
         * @param description optional conversation description
         * @return this builder instance
         */
        public Builder description(String description) {
            this.description = normalize(description);
            return this;
        }

        /**
         * Sets how many days Select AI should retain the conversation.
         *
         * @param retentionDays retention period in days, or {@code null} when unset
         * @return this builder instance
         * @throws IllegalArgumentException if {@code retentionDays} is negative.
         */
        public Builder retentionDays(Integer retentionDays) {
            if (retentionDays != null && retentionDays < 0) {
                throw new IllegalArgumentException(
                        "retentionDays must be non-negative");
            }
            this.retentionDays = retentionDays;
            return this;
        }

        /**
         * Sets how many turns Select AI should keep in conversation context.
         *
         * @param conversationLength context length, or {@code null} when unset
         * @return this builder instance
         * @throws IllegalArgumentException if {@code conversationLength} is less than
         *         or equal to zero
         */
        public Builder conversationLength(Integer conversationLength) {
            if (conversationLength != null && conversationLength <= 0) {
                throw new IllegalArgumentException(
                        "conversationLength must be greater than 0");
            }
            this.conversationLength = conversationLength;
            return this;
        }

        /**
         * Normalizes blank input strings to {@code null}.
         *
         * @param value value to trim
         * @return trimmed value, or {@code null} when input is null/blank
         */
        private static String normalize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        /**
         * Builds immutable conversation attributes.
         *
         * @return immutable ConversationAttributes instance from current builder state
         */
        public ConversationAttributes build() {
            return new ConversationAttributes(this);
        }
    }
}
