/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.profile;

import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.integration.IntegrationTestFixture;
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live profile lifecycle and attribute integration coverage.
 *
 * <p>The shared fixture supplies environment loading, JDBC
 * setup, isolated resource names, and cleanup. Tests exercise profile
 * creation, retrieval, attribute updates, status changes, and lifecycle
 * behavior.</p>
 */

/** Integration coverage for ProfileValidation. */
class ProfileValidationIT extends ProfileIntegrationFixture {

    /**
     * Test: Builds profile attributes with credential name {@code "OCI_CRED"}, object list, and
     * unsupported provider value {@code "INVALID_PROVIDER"}.
     * Expected: {@code build()} throws {@code IllegalArgumentException} containing
     * {@code "Unsupported provider"}.
     */
    @Test
    void test12300InvalidProviderNameUsesJavaValidation() {
        assertThatThrownBy(() -> ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .provider("INVALID_PROVIDER")
                .objectList(profileTestAttributes().getObjectList())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported provider");
     }

    /**
     * Test: Builds attributes with a credential name but no provider and passes them to the
     * four-argument {@code selectAI.profile(...)} factory.
     * Expected: The factory throws {@code IllegalArgumentException} before a profile is created.
     */
    @Test
    void test12301MissingProviderUsesJavaValidation() {
        ProfileAttributes attributes = ProfileAttributes.builder()
                .credentialName("OCI_CRED")
                .build();

        assertThatThrownBy(() -> selectAI.profile(uniqueProfileName("INVALID_PROVIDER"),
                attributes, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Calls the configured-profile factory once with an empty name and once with a
     * {@code null} name.
     * Expected: Both calls throw {@code IllegalArgumentException}; neither creates a profile.
     */
    @Test
    void test12302InvalidProfileNames() {
        assertThatThrownBy(() -> selectAI.profile("", profileTestAttributes(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selectAI.profile(null, profileTestAttributes(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Retrieves the fixture profile by its configured name and reads its name, description,
     * and credential attribute.
     * Expected: The returned handle matches the fixture name and description and reports the
     * isolated credential name stored in the database.
     */
    @Test
    void test12303FetchProfile() throws Exception {
        Profile fetched = selectAI.profile(profileName);
        assertThat(fetched.getProfileName()).isEqualTo(profileName);
        assertThat(fetched.getDescription()).isEqualTo(profile.getDescription());
        assertThat(fetched.getProfileAttributes().getCredentialName())
                .isEqualTo(isolatedCredentialName());
    }
}
