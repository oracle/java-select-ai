/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed runtime parameters for {@code DBMS_CLOUD_AI_AGENT.RUN_TEAM}.
 *
 * <p>The model represents the documented runtime parameter groups and
 * serializes them to the JSON CLOB expected by the database package. Values
 * supplied in maps are snapshotted when the builder method is called, so later
 * caller mutations cannot change a request that has already been built.
 * Decimal values are retained as {@link java.math.BigDecimal} rather than
 * rounded to binary floating-point values during the snapshot.</p>
 *
 * <p>For runnable examples, see
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunTeamSample.html">
 * RunTeamSample source</a> and
 * <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/RunTeamParamsSample.html">
 * RunTeamParamsSample source</a>.</p>
 *
 * @since 1.1.0
 */
public final class RunTeamParams {
    /** JSON mapper used to snapshot and serialize runtime values. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    /** Optional database conversation identifier. */
    private final String conversationId;
    /** Values substituted into task instructions. */
    private final Map<String, Object> instructionVariables;
    /** Values that override configurable agent or tool attributes. */
    private final Map<String, Object> attributeVariables;
    /** Values shared between tools during a team run. */
    private final Map<String, Object> stateVariables;

    private RunTeamParams(Builder builder) {
        this.conversationId = builder.conversationId;
        this.instructionVariables = builder.instructionVariables;
        this.attributeVariables = builder.attributeVariables;
        this.stateVariables = builder.stateVariables;
    }

    /**
     * Creates a builder for team runtime parameters.
     *
     * @return mutable builder for team runtime parameters
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates parameters for one database conversation.
     *
     * @param conversationId database-created conversation identifier
     * @return immutable team runtime parameters
     * @throws IllegalArgumentException when the identifier is null or blank
     */
    public static RunTeamParams forConversationId(String conversationId) {
        return builder().conversationId(conversationId).build();
    }

    /**
     * Returns the optional database conversation identifier.
     *
     * @return conversation identifier, or {@code null} when unset
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Returns instruction variables, or {@code null} when unset.
     *
     * @return immutable instruction variables, or {@code null}
     */
    public Map<String, Object> getInstructionVariables() {
        return instructionVariables;
    }

    /**
     * Returns attribute variables, or {@code null} when unset.
     *
     * @return immutable attribute variables, or {@code null}
     */
    public Map<String, Object> getAttributeVariables() {
        return attributeVariables;
    }

    /**
     * Returns state variables, or {@code null} when unset.
     *
     * @return immutable state variables, or {@code null}
     */
    public Map<String, Object> getStateVariables() {
        return stateVariables;
    }

    /**
     * Serializes these parameters for the database package.
     *
     * @return JSON object, or {@code null} when no parameters were supplied
     */
    public String toParamsJson() {
        Map<String, Object> params = new LinkedHashMap<>();
        if (conversationId != null) {
            params.put("conversation_id", conversationId);
        }
        if (instructionVariables != null) {
            params.put("instruction_variables", instructionVariables);
        }
        if (attributeVariables != null) {
            params.put("attribute_variables", attributeVariables);
        }
        if (stateVariables != null) {
            params.put("state_variables", stateVariables);
        }
        if (params.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize RunTeamParams", e);
        }
    }

    /** Builder for immutable team runtime parameters. */
    public static final class Builder {
        /** Optional database conversation identifier. */
        private String conversationId;
        /** Values substituted into task instructions. */
        private Map<String, Object> instructionVariables;
        /** Values that override configurable agent or tool attributes. */
        private Map<String, Object> attributeVariables;
        /** Values shared between tools during a team run. */
        private Map<String, Object> stateVariables;

        private Builder() {
        }

        /**
         * Sets an application-managed conversation identifier.
         *
         * @param conversationId database-created conversation identifier
         * @return this builder
         * @throws IllegalArgumentException when the identifier is null or blank
         */
        public Builder conversationId(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                throw new IllegalArgumentException("conversationId must not be null or blank");
            }
            this.conversationId = conversationId;
            return this;
        }

        /**
         * Sets instruction variables as a snapshot of a JSON-compatible map.
         *
         * @param instructionVariables values substituted into task instructions
         * @return this builder
         * @throws IllegalArgumentException when the map is not JSON-compatible
         */
        public Builder instructionVariables(Map<String, ?> instructionVariables) {
            this.instructionVariables = snapshotVariables(instructionVariables, "instructionVariables");
            return this;
        }

        /**
         * Sets attribute variables as a snapshot of a JSON-compatible map.
         *
         * @param attributeVariables values that override configurable attributes
         * @return this builder
         * @throws IllegalArgumentException when the map is not JSON-compatible
         */
        public Builder attributeVariables(Map<String, ?> attributeVariables) {
            this.attributeVariables = snapshotVariables(attributeVariables, "attributeVariables");
            return this;
        }

        /**
         * Sets state variables as a snapshot of a JSON-compatible map.
         *
         * @param stateVariables values shared between tools during a run
         * @return this builder
         * @throws IllegalArgumentException when the map is not JSON-compatible
         */
        public Builder stateVariables(Map<String, ?> stateVariables) {
            this.stateVariables = snapshotVariables(stateVariables, "stateVariables");
            return this;
        }

        /**
         * Builds immutable team runtime parameters.
         *
         * @return immutable team runtime parameters
         */
        public RunTeamParams build() {
            return new RunTeamParams(this);
        }
    }

    private static Map<String, Object> snapshotVariables(Map<String, ?> values, String fieldName) {
        if (values == null) {
            return null;
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(MAPPER.writeValueAsString(values),
                    new TypeReference<>() { });
            return immutableMap(parsed);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " must be JSON-compatible", e);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, immutableJsonValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("Runtime parameter map keys must be strings");
                }
                copy.put(stringKey, immutableJsonValue(entry));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(entry -> copy.add(immutableJsonValue(entry)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
