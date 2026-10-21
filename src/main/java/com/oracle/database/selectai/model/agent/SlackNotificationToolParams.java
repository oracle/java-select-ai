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
 * Immutable parameters for a built-in Slack notification tool.
 *
 * <p>Unset parameters are omitted. The database validates required fields
 * and tool-specific rules, allowing this model to represent partial updates.
 * Additional parameters are retained for newer database releases.</p>
 *
 * @see ToolParams
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSlackNotificationToolSample.html">
 *      CreateSlackNotificationToolSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
 *      ToolParamsSample source</a>
 * @since 1.1.0
 */
public final class SlackNotificationToolParams implements ToolParams {
    /** Database parameter names with typed setters or fixed values. */
    private static final Set<String> KNOWN_NAMES = Set.of("credential_name", "channel", "notification_type");
    /** Immutable database parameters, including unknown fields. */
    private final Map<String, Object> values;

    SlackNotificationToolParams(Map<String, ?> values) {
        this.values = ToolParamsSupport.snapshot(values);
    }

    /**
     * Creates a builder for Slack notification tool parameters.
     *
     * @return new mutable builder
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSlackNotificationToolSample.html">
     *      CreateSlackNotificationToolSample source</a>
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
     * Returns the Slack channel.
     *
     * @return Slack channel, or null when unset
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
     *      ToolParamsSample source</a>
     */
    public String getChannel() {
        return (String) values.get("channel");
    }

    /**
     * Returns the notification type.
     *
     * @return {@code slack} for builder-created parameters, or the database value
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
     * Builder for immutable {@link SlackNotificationToolParams}.
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSlackNotificationToolSample.html">
     *      CreateSlackNotificationToolSample source</a>
     */
    public static final class Builder {
        /** Parameters assembled by this builder. */
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
            values.put("notification_type", "slack");
        }

        /**
         * Sets the database credential name.
         *
         * @param credentialName database credential name, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSlackNotificationToolSample.html">
         *      CreateSlackNotificationToolSample source</a>
         */
        public Builder credentialName(String credentialName) {
            ToolParamsSupport.put(values, "credential_name", credentialName);
            return this;
        }

        /**
         * Sets the Slack channel.
         *
         * @param channel Slack channel, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSlackNotificationToolSample.html">
         *      CreateSlackNotificationToolSample source</a>
         */
        public Builder channel(String channel) {
            ToolParamsSupport.put(values, "channel", channel);
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
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateSlackNotificationToolSample.html">
         *      CreateSlackNotificationToolSample source</a>
         */
        public SlackNotificationToolParams build() {
            return new SlackNotificationToolParams(values);
        }
    }
}

