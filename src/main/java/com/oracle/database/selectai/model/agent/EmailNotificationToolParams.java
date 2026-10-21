/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable parameters for a built-in email notification tool.
 *
 * <p>Unset parameters are omitted. The database validates required fields
 * and tool-specific rules, allowing this model to represent partial updates.
 * Additional parameters are retained for newer database releases.</p>
 *
 * @see ToolParams
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
 *      CreateEmailNotificationToolSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
 *      ToolParamsSample source</a>
 * @since 1.1.0
 */
public final class EmailNotificationToolParams implements ToolParams {
    /** Database parameter names with typed setters or fixed values. */
    private static final Set<String> KNOWN_NAMES = Set.of(
            "credential_name", "recipient", "sender", "smtp_host", "subject", "notification_type");
    /** Immutable database parameters, including unknown fields. */
    private final Map<String, Object> values;

    EmailNotificationToolParams(Map<String, ?> values) {
        this.values = ToolParamsSupport.snapshot(values);
    }

    /**
     * Creates a builder for email notification tool parameters.
     *
     * @return new mutable builder
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
     *      CreateEmailNotificationToolSample source</a>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the database credential name.
     *
     * @return database credential name, or null when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String getCredentialName() {
        return (String) values.get("credential_name");
    }

    /**
     * Returns the email recipient.
     *
     * @return email recipient, or null when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String getRecipient() {
        return (String) values.get("recipient");
    }

    /**
     * Returns the email sender.
     *
     * @return email sender, or null when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String getSender() {
        return (String) values.get("sender");
    }

    /**
     * Returns the SMTP host.
     *
     * @return SMTP host, or null when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String getSmtpHost() {
        return (String) values.get("smtp_host");
    }

    /**
     * Returns the optional email subject.
     *
     * @return optional email subject, or null when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String getSubject() {
        return (String) values.get("subject");
    }

    /**
     * Returns the notification type.
     *
     * @return {@code email} for builder-created parameters, or the database value
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String getNotificationType() {
        return (String) values.get("notification_type");
    }

    /**
     * {@inheritDoc}
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    @Override
    public Map<String, Object> toMap() {
        return values;
    }

    /**
     * Builder for immutable {@link EmailNotificationToolParams}.
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
     *      CreateEmailNotificationToolSample source</a>
     */
    public static final class Builder {
        /** Parameters assembled by this builder. */
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
            values.put("notification_type", "email");
        }

        /**
         * Sets the database credential name.
         *
         * @param credentialName database credential name, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
         *      CreateEmailNotificationToolSample source</a>
         */
        public Builder credentialName(String credentialName) {
            ToolParamsSupport.put(values, "credential_name", credentialName);
            return this;
        }

        /**
         * Sets the email recipient.
         *
         * @param recipient email recipient, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
         *      CreateEmailNotificationToolSample source</a>
         */
        public Builder recipient(String recipient) {
            ToolParamsSupport.put(values, "recipient", recipient);
            return this;
        }

        /**
         * Sets the email sender.
         *
         * @param sender email sender, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
         *      CreateEmailNotificationToolSample source</a>
         */
        public Builder sender(String sender) {
            ToolParamsSupport.put(values, "sender", sender);
            return this;
        }

        /**
         * Sets the SMTP host.
         *
         * @param smtpHost SMTP host, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
         *      CreateEmailNotificationToolSample source</a>
         */
        public Builder smtpHost(String smtpHost) {
            ToolParamsSupport.put(values, "smtp_host", smtpHost);
            return this;
        }

        /**
         * Sets the optional email subject.
         *
         * @param subject optional email subject, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
         *      CreateEmailNotificationToolSample source</a>
         */
        public Builder subject(String subject) {
            ToolParamsSupport.put(values, "subject", subject);
            return this;
        }

        /**
         * Snapshots an additional database parameter.
         *
         * @param name database JSON parameter name
         * @param value JSON-compatible value, including null
         * @return this builder
         * @throws IllegalArgumentException when the name is blank, is a known
         *         parameter name, or the value is not JSON-compatible
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
         *      ToolParamsSample source</a>
         */
        public Builder additionalParameter(String name, Object value) {
            ToolParamsSupport.additionalParameter(values, KNOWN_NAMES, name, value);
            return this;
        }

        /**
         * Builds an immutable snapshot without database-specific validation.
         *
         * @return immutable parameters
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateEmailNotificationToolSample.html">
         *      CreateEmailNotificationToolSample source</a>
         */
        public EmailNotificationToolParams build() {
            return new EmailNotificationToolParams(values);
        }
    }
}

