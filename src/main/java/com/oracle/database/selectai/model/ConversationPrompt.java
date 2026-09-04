/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import java.sql.Timestamp;

/**
 * Read-only metadata row for a prompt stored in a Select AI conversation.
 * <p>
 * Instances are populated from {@code USER_CLOUD_AI_CONVERSATION_PROMPTS} and
 * returned by {@link com.oracle.database.selectai.Conversation#listPrompts()}. Prompt
 * metadata can contain caller-provided or generated content, including
 * conversation titles, prompt text, prompt responses, client identifiers, and
 * client IP addresses. Applications should avoid logging full prompt metadata
 * unless application policy permits it.
 *
 * @see com.oracle.database.selectai.Conversation#listPrompts()
 * @see com.oracle.database.selectai.Conversation#deletePrompt(String)
 */
public final class ConversationPrompt {
    /** Conversation prompt identifier. */
    private final String conversationPromptId;
    /** Conversation identifier associated with the prompt. */
    private final String conversationId;
    /** Conversation title captured with the prompt row. */
    private final String conversationTitle;
    /** Select AI profile used for the prompt. */
    private final String profileName;
    /** Prompt action such as chat, narrate, or showsql. */
    private final String promptAction;
    /** Prompt text. */
    private final String prompt;
    /** Prompt response text. */
    private final String promptResponse;
    /** Row creation timestamp. */
    private final Timestamp created;
    /** Row modification timestamp. */
    private final Timestamp modified;
    /** Client identifier captured by the database. */
    private final String clientIdentifier;
    /** Client IP captured by the database. */
    private final String clientIp;
    /** Database session ID. */
    private final Long sid;
    /** Database session serial number. */
    private final Long serialNumber;

    private ConversationPrompt(Builder builder) {
        this.conversationPromptId = builder.conversationPromptId;
        this.conversationId = builder.conversationId;
        this.conversationTitle = builder.conversationTitle;
        this.profileName = builder.profileName;
        this.promptAction = builder.promptAction;
        this.prompt = builder.prompt;
        this.promptResponse = builder.promptResponse;
        this.created = copyTimestamp(builder.created);
        this.modified = copyTimestamp(builder.modified);
        this.clientIdentifier = builder.clientIdentifier;
        this.clientIp = builder.clientIp;
        this.sid = builder.sid;
        this.serialNumber = builder.serialNumber;
    }

    /**
     * Creates a builder for conversation prompt metadata.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the conversation prompt identifier.
     *
     * @return conversation prompt identifier, or {@code null} when unavailable
     */
    public String getConversationPromptId() {
        return conversationPromptId;
    }

    /**
     * Returns the conversation identifier associated with this prompt.
     *
     * @return conversation identifier, or {@code null} when unavailable
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Returns the conversation title captured with this prompt row.
     * <p>
     * This value may contain caller-provided or customer content.
     *
     * @return conversation title, or {@code null} when unavailable
     */
    public String getConversationTitle() {
        return conversationTitle;
    }

    /**
     * Returns the Select AI profile used for this prompt.
     *
     * @return profile name, or {@code null} when unavailable
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Returns the prompt action used for this prompt.
     *
     * @return prompt action such as {@code chat}, {@code narrate}, or {@code showsql}
     */
    public String getPromptAction() {
        return promptAction;
    }

    /**
     * Returns the prompt text.
     * <p>
     * This value may contain caller-provided or customer content.
     *
     * @return prompt text, or {@code null} when unavailable
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Returns the prompt response text.
     * <p>
     * This value may contain generated response text or customer content.
     *
     * @return prompt response text, or {@code null} when unavailable
     */
    public String getPromptResponse() {
        return promptResponse;
    }

    /**
     * Returns the prompt row creation timestamp.
     *
     * @return defensive copy of creation timestamp, or {@code null} when unavailable
     */
    public Timestamp getCreated() {
        return copyTimestamp(created);
    }

    /**
     * Returns the prompt row modification timestamp.
     *
     * @return defensive copy of modification timestamp, or {@code null} when unavailable
     */
    public Timestamp getModified() {
        return copyTimestamp(modified);
    }

    /**
     * Returns the database client identifier captured with this prompt.
     *
     * @return client identifier, or {@code null} when unavailable
     */
    public String getClientIdentifier() {
        return clientIdentifier;
    }

    /**
     * Returns the client IP address captured by the database.
     * <p>
     * This value may be customer or client network metadata.
     *
     * @return client IP address, or {@code null} when unavailable
     */
    public String getClientIp() {
        return clientIp;
    }

    /**
     * Returns the database session identifier captured with this prompt.
     *
     * @return database session identifier, or {@code null} when unavailable
     */
    public Long getSid() {
        return sid;
    }

    /**
     * Returns the database session serial number captured with this prompt.
     *
     * @return database session serial number, or {@code null} when unavailable
     */
    public Long getSerialNumber() {
        return serialNumber;
    }

    /**
     * Returns a non-sensitive summary of this prompt metadata.
     *
     * @return summary string that omits prompt text, prompt response, and title
     */
    @Override
    public String toString() {
        return "ConversationPrompt{"
                + "conversationPromptId='" + conversationPromptId + '\''
                + ", conversationId='" + conversationId + '\''
                + ", profileName='" + profileName + '\''
                + ", promptAction='" + promptAction + '\''
                + ", created=" + created
                + ", modified=" + modified
                + ", hasConversationTitle=" + (conversationTitle != null)
                + ", hasPrompt=" + (prompt != null)
                + ", hasPromptResponse=" + (promptResponse != null)
                + ", hasClientIdentifier=" + (clientIdentifier != null)
                + ", hasClientIp=" + (clientIp != null)
                + ", sid=" + sid
                + ", serialNumber=" + serialNumber
                + '}';
    }

    /**
     * Builder for {@link ConversationPrompt}.
     */
    public static final class Builder {
        /** Conversation prompt identifier being assembled. */
        private String conversationPromptId;
        /** Conversation identifier being assembled. */
        private String conversationId;
        /** Conversation title being assembled. */
        private String conversationTitle;
        /** Profile name being assembled. */
        private String profileName;
        /** Prompt action being assembled. */
        private String promptAction;
        /** Prompt text being assembled. */
        private String prompt;
        /** Prompt response text being assembled. */
        private String promptResponse;
        /** Creation timestamp being assembled. */
        private Timestamp created;
        /** Modification timestamp being assembled. */
        private Timestamp modified;
        /** Client identifier being assembled. */
        private String clientIdentifier;
        /** Client IP address being assembled. */
        private String clientIp;
        /** Database session identifier being assembled. */
        private Long sid;
        /** Database session serial number being assembled. */
        private Long serialNumber;

        private Builder() {
        }

        /**
         * Sets the conversation prompt identifier.
         *
         * @param conversationPromptId conversation prompt identifier
         * @return this builder instance
         */
        public Builder conversationPromptId(String conversationPromptId) {
            this.conversationPromptId = conversationPromptId;
            return this;
        }

        /**
         * Sets the conversation identifier associated with the prompt.
         *
         * @param conversationId conversation identifier
         * @return this builder instance
         */
        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        /**
         * Sets the conversation title captured with the prompt row.
         *
         * @param conversationTitle conversation title
         * @return this builder instance
         */
        public Builder conversationTitle(String conversationTitle) {
            this.conversationTitle = conversationTitle;
            return this;
        }

        /**
         * Sets the Select AI profile used for the prompt.
         *
         * @param profileName profile name
         * @return this builder instance
         */
        public Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        /**
         * Sets the prompt action.
         *
         * @param promptAction prompt action such as {@code chat}, {@code narrate}, or {@code showsql}
         * @return this builder instance
         */
        public Builder promptAction(String promptAction) {
            this.promptAction = promptAction;
            return this;
        }

        /**
         * Sets the prompt text.
         *
         * @param prompt prompt text
         * @return this builder instance
         */
        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets the prompt response text.
         *
         * @param promptResponse prompt response text
         * @return this builder instance
         */
        public Builder promptResponse(String promptResponse) {
            this.promptResponse = promptResponse;
            return this;
        }

        /**
         * Sets the prompt row creation timestamp.
         *
         * @param created creation timestamp
         * @return this builder instance
         */
        public Builder created(Timestamp created) {
            this.created = created;
            return this;
        }

        /**
         * Sets the prompt row modification timestamp.
         *
         * @param modified modification timestamp
         * @return this builder instance
         */
        public Builder modified(Timestamp modified) {
            this.modified = modified;
            return this;
        }

        /**
         * Sets the database client identifier captured with the prompt.
         *
         * @param clientIdentifier client identifier
         * @return this builder instance
         */
        public Builder clientIdentifier(String clientIdentifier) {
            this.clientIdentifier = clientIdentifier;
            return this;
        }

        /**
         * Sets the client IP address captured by the database.
         *
         * @param clientIp client IP address
         * @return this builder instance
         */
        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        /**
         * Sets the database session identifier.
         *
         * @param sid database session identifier
         * @return this builder instance
         */
        public Builder sid(Long sid) {
            this.sid = sid;
            return this;
        }

        /**
         * Sets the database session serial number.
         *
         * @param serialNumber database session serial number
         * @return this builder instance
         */
        public Builder serialNumber(Long serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        /**
         * Builds immutable conversation prompt metadata.
         *
         * @return immutable conversation prompt metadata
         */
        public ConversationPrompt build() {
            return new ConversationPrompt(this);
        }
    }

    private static Timestamp copyTimestamp(Timestamp value) {
        return value == null ? null : new Timestamp(value.getTime());
    }
}
