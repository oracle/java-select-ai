/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Database-generated description of a Select AI Agent team.
 *
 * <p>The documented team name, description, and version are exposed as typed
 * values. The A2A-compatible skills representation is returned as JSON so
 * fields introduced by a newer database release are not lost. Non-null
 * responses must contain a single valid JSON object; malformed responses are
 * rejected rather than represented as empty metadata. Additional fields are
 * tolerated, and absent optional fields remain null.</p>
 *
 * <p>This class inherits {@link Object#toString()} so ordinary object logging
 * does not include team metadata. Use the accessors explicitly when metadata
 * is needed, and review it before logging.</p>
 *
 * @see <a href="{@docRoot}/src-html/com/oracle/database/selectai/samples/agent/DescribeTeamSample.html">
 *      DescribeTeamSample source</a>
 * @since 1.1.0
 */
public final class TeamDescription {
    /** JSON mapper used to parse the database-generated description. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** Team name reported by the database. */
    private final String teamName;
    /** Team description reported by the database. */
    private final String description;
    /** Optional team version reported by the database. */
    private final String version;
    /** A2A-compatible skills array retained as JSON. */
    private final String skills;

    /**
     * Creates a team description.
     *
     * @param rawJson database-generated team description
     * @throws IllegalArgumentException when a non-null response is not a JSON object
     */
    public TeamDescription(String rawJson) {
        ParsedDescription parsed = parse(rawJson);
        this.teamName = parsed.teamName;
        this.description = parsed.description;
        this.version = parsed.version;
        this.skills = parsed.skills;
    }

    /**
     * Creates a description from a database value.
     *
     * @param rawJson database-generated description
     * @return description, or {@code null} when the database returned no value
     * @throws IllegalArgumentException when a non-null response is not a JSON object
     */
    public static TeamDescription fromRawJson(String rawJson) {
        return rawJson == null ? null : new TeamDescription(rawJson);
    }

    /**
     * Returns the team name reported by the database.
     *
     * @return team name, or {@code null} when it was not present
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * Returns the team description reported by the database.
     *
     * @return team description, or {@code null} when it was not present
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the optional team version reported by the database.
     *
     * @return team version, or {@code null} when not present
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the A2A-compatible skills JSON exposed by the team.
     *
     * @return skills JSON, or {@code null} when the response has no skills
     */
    public String getSkills() {
        return skills;
    }

    private static ParsedDescription parse(String rawJson) {
        if (rawJson == null) {
            return ParsedDescription.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Team description must be a JSON object");
            }
            JsonNode skillsNode = root.get("skills");
            String skills = skillsNode == null || skillsNode.isNull()
                    ? null : skillsNode.toString();
            return new ParsedDescription(
                    text(root, "team_name", "teamName", "name"),
                    text(root, "description"),
                    text(root, "version"),
                    skills);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Team description must be a valid JSON object", e);
        }
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && value.isValueNode()) {
                return value.asText();
            }
        }
        return null;
    }

    private static final class ParsedDescription {
        /** Parsed team name. */
        private final String teamName;
        /** Parsed team description. */
        private final String description;
        /** Parsed optional version. */
        private final String version;
        /** Parsed skills array as JSON. */
        private final String skills;

        private ParsedDescription(String teamName, String description, String version,
                                  String skills) {
            this.teamName = teamName;
            this.description = description;
            this.version = version;
            this.skills = skills;
        }

        private static ParsedDescription empty() {
            return new ParsedDescription(null, null, null, null);
        }
    }
}
