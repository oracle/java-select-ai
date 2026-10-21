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
 * Immutable parameters for a built-in web-search tool.
 *
 * <p>Unset parameters are omitted. The database validates required fields
 * and tool-specific rules, allowing this model to represent partial updates.
 * Additional parameters are retained for newer database releases.</p>
 *
 * @see ToolParams
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateWebSearchToolSample.html">
 *      CreateWebSearchToolSample source</a>
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/ToolParamsSample.html">
 *      ToolParamsSample source</a>
 * @since 1.1.0
 */
public final class WebSearchToolParams implements ToolParams {
    /** Database parameter names with typed setters or fixed values. */
    private static final Set<String> KNOWN_NAMES = Set.of("credential_name");
    /** Immutable database parameters, including unknown fields. */
    private final Map<String, Object> values;

    WebSearchToolParams(Map<String, ?> values) {
        this.values = ToolParamsSupport.snapshot(values);
    }

    /**
     * Creates a builder for web-search tool parameters.
     *
     * @return new mutable builder
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateWebSearchToolSample.html">
     *      CreateWebSearchToolSample source</a>
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
     * Builder for immutable {@link WebSearchToolParams}.
     *
     * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateWebSearchToolSample.html">
     *      CreateWebSearchToolSample source</a>
     */
    public static final class Builder {
        /** Parameters assembled by this builder. */
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Sets the database credential name.
         *
         * @param credentialName database credential name, or null to omit it
         * @return this builder
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateWebSearchToolSample.html">
         *      CreateWebSearchToolSample source</a>
         */
        public Builder credentialName(String credentialName) {
            ToolParamsSupport.put(values, "credential_name", credentialName);
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
         * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/CreateWebSearchToolSample.html">
         *      CreateWebSearchToolSample source</a>
         */
        public WebSearchToolParams build() {
            return new WebSearchToolParams(values);
        }
    }
}

