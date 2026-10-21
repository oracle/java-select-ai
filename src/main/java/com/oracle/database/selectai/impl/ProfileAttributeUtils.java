/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal utility for loading profile attributes from
 * {@code USER_CLOUD_AI_PROFILE_ATTRIBUTES} and converting database values into
 * {@link ProfileAttributes} objects.
 * <p>
 * Database hydration deliberately does not apply the SDK creation allow-list
 * to provider, OCI API format, or object-list mode values. Known values retain
 * their established normalization; unknown values are preserved in the
 * returned model so one newer profile cannot prevent profile listing.
 * <p>
 * This class is used by the SDK implementation and is not intended for direct
 * application use. Applications should use
 * {@link Profile#getProfileAttributes()}.
 */
public final class ProfileAttributeUtils {

    /** Logger for profile attribute lookup and JDBC failure diagnostics. */
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
     *         are retained for round-tripping
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
     *         are retained for round-tripping
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
            Map<String, String> attributes = new LinkedHashMap<>();

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String attributeName = rs.getString("attribute_name");
                    String attributeValue = rs.getString("attribute_value");
                    if (attributeName != null && attributeValue != null) {
                        attributes.put(attributeName, attributeValue);
                    }
                }
            }

            return ProfileAttributes.fromDatabaseAttributeMap(attributes);
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch profile attributes for {}", profileName, e);
            throw new SelectAIException(
                    "Failed to fetch SelectAI profile attributes: " + profileName,
                    e,
                    e.getErrorCode(),
                    e.getSQLState());
        }
    }

}
