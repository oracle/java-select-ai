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
import java.util.Set;

/** Shared immutable JSON snapshots and metadata mapping for tool parameters. */
final class ToolParamsSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private ToolParamsSupport() {
    }

    static Map<String, Object> snapshot(Map<String, ?> values) {
        if (values == null) {
            throw new IllegalArgumentException("tool parameters must not be null");
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(MAPPER.writeValueAsString(values),
                    new TypeReference<Map<String, Object>>() { });
            return immutableMap(parsed);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalArgumentException("tool parameters must be JSON-compatible", e);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((name, value) -> copy.put(name, immutable(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((name, entry) -> copy.put((String) name, immutable(entry)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(entry -> copy.add(immutable(entry)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    static void put(Map<String, Object> values, String name, String value) {
        if (value == null) {
            values.remove(name);
        } else {
            values.put(name, value);
        }
    }

    static void additionalParameter(Map<String, Object> values, Set<String> knownNames,
                                    String name, Object value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter name must not be null or blank");
        }
        if (knownNames.contains(name)) {
            throw new IllegalArgumentException("Use the typed setter for parameter: " + name);
        }
        values.put(name, snapshot(Collections.singletonMap(name, value)).get(name));
    }

    /** Copies caller implementations while keeping SDK-provided model types. */
    static ToolParams copy(ToolParams params) {
        if (params == null) {
            return null;
        }
        Map<String, Object> values = params.toMap();
        if (params instanceof SqlToolParams) {
            return new SqlToolParams(values);
        }
        if (params instanceof RagToolParams) {
            return new RagToolParams(values);
        }
        if (params instanceof WebSearchToolParams) {
            return new WebSearchToolParams(values);
        }
        if (params instanceof SlackNotificationToolParams) {
            return new SlackNotificationToolParams(values);
        }
        if (params instanceof EmailNotificationToolParams) {
            return new EmailNotificationToolParams(values);
        }
        return new GenericToolParams(values);
    }

    static ToolParams fromMap(ToolType type, Map<String, ?> values) {
        if (values == null) {
            return null;
        }
        if (ToolType.SQL.equals(type) && strings(values, "profile_name")) {
            return new SqlToolParams(values);
        }
        if (ToolType.RAG.equals(type) && strings(values, "profile_name")) {
            return new RagToolParams(values);
        }
        if (ToolType.WEBSEARCH.equals(type) && strings(values, "credential_name")) {
            return new WebSearchToolParams(values);
        }
        if (ToolType.NOTIFICATION.equals(type)) {
            Object notification = values.get("notification_type");
            if (notification instanceof String notificationType) {
                if ("slack".equalsIgnoreCase(notificationType)
                        && strings(values, "credential_name", "channel")) {
                    return new SlackNotificationToolParams(values);
                }
                if ("email".equalsIgnoreCase(notificationType)
                        && strings(values, "credential_name", "recipient", "sender", "smtp_host", "subject")) {
                    return new EmailNotificationToolParams(values);
                }
            }
        }
        return new GenericToolParams(values);
    }

    /** Changed database field types must survive loading without coercion. */
    private static boolean strings(Map<String, ?> values, String... names) {
        for (String name : names) {
            Object value = values.get(name);
            if (value != null && !(value instanceof String)) {
                return false;
            }
        }
        return true;
    }
}
