/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.selectai.DbConnection;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Internal utility for loading profile attributes from
 * {@code USER_CLOUD_AI_PROFILE_ATTRIBUTES} and converting database values into
 * {@link ProfileAttributes} objects.
 * <p>
 * This class is used by the SDK implementation and is not intended for direct
 * application use. Applications should use
 * {@link Profile#getProfileAttributes()}.
 */
public final class ProfileAttributeUtils {

    /** Logger for profile attribute lookup and parsing diagnostics. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileAttributeUtils.class);

    private ProfileAttributeUtils() {
    }

    /**
     * Fetches the current attributes stored for a Select AI profile.
     * <p>
     * Attributes absent from the database remain unset in the returned object.
     * This method does not close the supplied {@link DbConnection}; connection
     * ownership and lifecycle remain with the caller.
     *
     * @param dbConnection database connection used to query profile attributes
     * @param profileName profile whose attributes should be fetched
     * @return profile attributes hydrated from database metadata; attributes not
     *         present in the metadata remain unset and unrecognized attributes
     *         are ignored
     * @throws IllegalArgumentException when {@code dbConnection} is null
     * @throws SelectAIException when the database query fails
     */
    public static ProfileAttributes fetchProfileAttributes(DbConnection dbConnection, String profileName)
            throws SelectAIException {
        if (dbConnection == null) {
            LOGGER.error("dbConnection must not be null when fetching profile attributes for {}", profileName);
            throw new IllegalArgumentException("dbConnection must not be null");
        }
        return fetchProfileAttributes(dbConnection.getConnection(), profileName);
    }

    /**
     * Fetches the current attributes stored for a Select AI profile using the
     * supplied JDBC connection.
     * <p>
     * This overload lets implementation classes use DataSource-backed connection
     * lifecycle while keeping the profile-attribute mapping logic in one place.
     * This method does not close the supplied connection; connection ownership
     * and lifecycle remain with the caller.
     *
     * @param connection JDBC connection used to query profile attributes
     * @param profileName profile whose attributes should be fetched
     * @return profile attributes hydrated from database metadata; attributes not
     *         present in the metadata remain unset and unrecognized attributes
     *         are ignored
     * @throws IllegalArgumentException when {@code connection} is null
     * @throws SelectAIException when the database query fails
     */
    public static ProfileAttributes fetchProfileAttributes(Connection connection, String profileName)
            throws SelectAIException {
        if (connection == null) {
            LOGGER.error("connection must not be null when fetching profile attributes for {}", profileName);
            throw new IllegalArgumentException("connection must not be null");
        }
        try (PreparedStatement ps = connection.prepareStatement(Sql.GET_PROFILE_ATTRIBUTES.get())) {
            ps.setString(1, profileName);
            ProfileAttributes.Builder builder = ProfileAttributes.builder();

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String attributeName = rs.getString("attribute_name");
                    String attributeValue = rs.getString("attribute_value");
                    mapAttribute(builder, attributeName, attributeValue);
                }
            }

            return builder.build();
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch profile attributes for {}", profileName, e);
            throw new SelectAIException(
                    "Failed to fetch SelectAI profile attributes: " + profileName,
                    e,
                    e.getErrorCode(),
                    e.getSQLState());
        }
    }

    /**
     * Maps a recognized database attribute into the corresponding strongly typed
     * builder field. Attribute names are matched case-insensitively. Unknown
     * attribute names are ignored and logged without logging attribute values.
     *
     * @param builder profile attributes builder to update
     * @param attributeName database attribute name
     * @param attributeValue database attribute value
     */
    private static void mapAttribute(ProfileAttributes.Builder builder, String attributeName, String attributeValue) {
        if (attributeName == null) {
            return;
        }

        switch (attributeName.toLowerCase(Locale.ROOT)) {
            case "provider" -> builder.provider(attributeValue);
            case "additional_instructions" -> builder.additionalInstructions(attributeValue);
            case "credential_name" -> builder.credentialName(attributeValue);
            case "oci_compartment_id" -> builder.ociCompartmentId(attributeValue);
            case "object_list" -> builder.objectList(attributeValue);
            case "oci_endpoint_id" -> builder.ociEndpointId(attributeValue);
            case "oci_runtimetype" -> builder.ociRuntimetype(attributeValue);
            case "oci_apiformat" -> builder.ociApiformat(attributeValue);
            case "provider_endpoint" -> builder.providerEndpoint(attributeValue);
            case "azure_resource_name" -> builder.azureResourceName(attributeValue);
            case "azure_deployment_name" -> builder.azureDeploymentName(attributeValue);
            case "azure_embedding_deployment_name" -> builder.azureEmbeddingDeploymentName(attributeValue);
            case "embedding_model" -> builder.embeddingModel(attributeValue);
            case "model" -> builder.model(attributeValue);
            case "max_tokens" -> builder.maxTokens(parseInteger(attributeValue));
            case "temperature" -> builder.temperature(parseDouble(attributeValue));
            case "conversation" -> builder.conversation(parseBoolean(attributeValue));
            case "conversation_length" -> builder.conversationLength(parseInteger(attributeValue));
            case "annotations" -> builder.annotations(parseBoolean(attributeValue));
            case "comments" -> builder.comments(parseBoolean(attributeValue));
            case "constraints" -> builder.constraints(parseBoolean(attributeValue));
            case "case_sensitive_values" -> builder.caseSensitiveValues(parseBoolean(attributeValue));
            case "object_list_mode" -> builder.objectListMode(attributeValue);
            case "enforce_object_list" -> builder.enforceObjectList(parseBoolean(attributeValue));
            case "enable_custom_source_uri" -> builder.enableCustomSourceUri(parseBoolean(attributeValue));
            case "vector_index_name" -> builder.vectorIndexName(attributeValue);
            case "region" -> builder.region(attributeValue);
            case "role" -> builder.role(attributeValue);
            case "seed" -> builder.seed(parseLong(attributeValue));
            case "stop_tokens" -> builder.stopTokens(parseStopTokens(attributeValue));
            case "source_language" -> builder.sourceLanguage(attributeValue);
            case "target_language" -> builder.targetLanguage(attributeValue);
            default -> LOGGER.warn("Unknown profile attribute '{}' ignored; valuePresent={}",
                    attributeName, attributeValue != null);
        }
    }

    /**
     * Parses a database profile attribute as an integer.
     *
     * @param value database attribute value
     * @return parsed integer, or {@code null} when the value is null or malformed
     */
    private static Integer parseInteger(String value) {
        try {
            return value != null ? Integer.valueOf(value) : null;
        } catch (NumberFormatException ex) {
            LOGGER.warn("Unable to parse integer profile attribute value; valuePresent={}", value != null, ex);
            return null;
        }
    }

    /**
     * Parses a database profile attribute as a long.
     *
     * @param value database attribute value
     * @return parsed long, or {@code null} when the value is null or malformed
     */
    private static Long parseLong(String value) {
        try {
            return value != null ? Long.valueOf(value) : null;
        } catch (NumberFormatException ex) {
            LOGGER.warn("Unable to parse long profile attribute value; valuePresent={}", value != null, ex);
            return null;
        }
    }

    /**
     * Parses a database profile attribute as a double.
     *
     * @param value database attribute value
     * @return parsed double, or {@code null} when the value is null or malformed
     */
    private static Double parseDouble(String value) {
        try {
            return value != null ? Double.valueOf(value) : null;
        } catch (NumberFormatException ex) {
            LOGGER.warn("Unable to parse double profile attribute value; valuePresent={}", value != null, ex);
            return null;
        }
    }

    /**
     * Parses a database profile attribute as a boolean.
     * <p>
     * Non-null values are interpreted using {@link Boolean#parseBoolean(String)};
     * therefore only a case-insensitive {@code "true"} value produces
     * {@code true}.
     *
     * @param value database attribute value
     * @return parsed boolean, or {@code null} when the value is null
     */
    private static Boolean parseBoolean(String value) {
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Parses the {@code stop_tokens} JSON array from profile metadata.
     *
     * @param value database {@code stop_tokens} attribute value
     * @return parsed stop-token list, or {@code null} when the value is null,
     *         blank, or malformed
     */
    private static List<String> parseStopTokens(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var root = new ObjectMapper().readTree(value);
            if (!root.isArray()) {
                LOGGER.warn("Unable to parse stop_tokens because value is not a JSON array; valuePresent={}",
                        value != null);
                return null;
            }
            List<String> stopTokens = new ArrayList<>();
            for (var node : root) {
                if (!node.isTextual()) {
                    LOGGER.warn("Unable to parse stop_tokens because array contains a non-string value; valuePresent={}",
                            value != null);
                    return null;
                }
                stopTokens.add(node.asText());
            }
            return stopTokens;
        } catch (Exception ex) {
            LOGGER.warn("Unable to parse stop_tokens profile attribute value; valuePresent={}", value != null, ex);
            return null;
        }
    }
}
