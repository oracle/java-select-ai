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
import java.util.Locale;
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

/** Integration coverage for ListProfiles. */
class ListProfilesIT extends ProfileIntegrationFixture {

    /**
     * Test: Creates two named profiles with descriptions and one minimum-attribute profile, then
     * calls {@code selectAI.listProfiles()}.
     * Expected: The returned collection contains all three generated profile names and includes
     * the exact description {@code "OCI GENAI Profile 2"} among the returned descriptions.
     */
    @Test
    void test12100ListProfiles() throws Exception {
        String prefix = uniqueProfileName("PROFILE_1204");
        Profile first = createManagedProfile(prefix + "_A", profileTestAttributes(),
                "OCI GENAI Profile");
        Profile second = createManagedProfile(prefix + "_B", profileTestAttributes(),
                "OCI GENAI Profile 2");
        Profile minimum = createManagedProfile(prefix + "_MIN", ProfileAttributes.builder()
                .credentialName(isolatedCredentialName())
                .provider("oci")
                .build(), null);

        List<Profile> profiles = selectAI.listProfiles();
        assertThat(profiles).extracting(Profile::getProfileName)
                .contains(first.getProfileName(), second.getProfileName(), minimum.getProfileName());
        assertThat(profiles).extracting(Profile::getDescription)
                .contains("OCI GENAI Profile 2");
    }

    /**
     * Test: Creates two profiles whose names share a generated prefix and a third profile with a
     * different prefix, lists all profiles, and applies the Java regex {@code ^<prefix>} to names.
     * Expected: The filtered result contains exactly the two matching profile names, excludes the
     * unrelated profile, and includes the description {@code "OCI GENAI Profile 2"}.
     */
    @Test
    void test12101ListProfilesWithJavaRegexFilter() throws Exception {
        String prefix = uniqueProfileName("PROFILE_1205");
        Profile first = createManagedProfile(prefix + "_A", profileTestAttributes(),
                "OCI GENAI Profile");
        Profile second = createManagedProfile(prefix + "_B", profileTestAttributes(),
                "OCI GENAI Profile 2");
        createManagedProfile(uniqueProfileName("PROFILE_OTHER"), profileTestAttributes(),
                "Other profile");

        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix));
        List<Profile> filtered = selectAI.listProfiles().stream()
                .filter(item -> pattern.matcher(item.getProfileName()).find())
                .toList();
        assertThat(filtered).extracting(Profile::getProfileName)
                .containsExactlyInAnyOrder(first.getProfileName(), second.getProfileName());
        assertThat(filtered).extracting(Profile::getDescription)
                .contains("OCI GENAI Profile 2");
    }

    /**
     * Test: Creates profile fixtures and calls listProfiles("^<EXACT_NAME>$") for one generated
     * name.
     * Expected: the anchored database regular expression returns exactly that profile.
     */
    @Test
    void test12102ListProfilesExactMatchUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String exact = names.get(0).toUpperCase(Locale.ROOT);

        assertThat(listNames("^" + exact + "$")).containsExactly(exact);
    }

    /**
     * Test: Creates five profiles sharing a prefix and one profile with a different prefix, then
     * calls listProfiles("^<PREFIX>").
     * Expected: all five shared-prefix profiles are returned and the unrelated profile is excluded.
     */
    @Test
    void test12103ListProfilesMultipleMatchesUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String prefix = names.get(0).substring(0, names.get(0).lastIndexOf('_'));

        assertThat(listNames("^" + prefix)).containsExactlyInAnyOrderElementsOf(
                names.subList(0, 5).stream().map(name -> name.toUpperCase(Locale.ROOT)).toList());
    }

    /**
     * Test: Creates profile fixtures and passes the lowercase generated prefix to listProfiles().
     * Expected: database regular-expression matching is case-insensitive and returns matching
     * profiles.
     */
    @Test
    void test12104ListProfilesCaseInsensitiveRegexUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String prefix = names.get(0).substring(0, names.get(0).lastIndexOf('_'));

        assertThat(listNames("^" + prefix.toLowerCase(Locale.ROOT))).isNotEmpty();
    }

    /**
     * Test: Creates profiles with two generated prefixes, then lists with the regex alternation
     * "^(<FIRST_PREFIX>|<SECOND_PREFIX>)".
     * Expected: all fixture profiles are returned and no profile outside either prefix is included.
     */
    @Test
    void test12105ListProfilesOrRegexUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String firstPrefix = names.get(0).substring(0, names.get(0).lastIndexOf('_'));
        String secondPrefix = names.get(5).substring(0, names.get(5).lastIndexOf('_'));

        assertThat(listNames("^(" + firstPrefix + "|" + secondPrefix + ")"))
                .containsExactlyInAnyOrderElementsOf(names.stream()
                        .map(name -> name.toUpperCase(Locale.ROOT)).toList());
    }

    /**
     * Test: Creates profile fixtures and calls listProfiles("^xyz"), a prefix not used by any
     * generated fixture name.
     * Expected: the returned list is empty and no unrelated profile is treated as a match.
     */
    @Test
    void test12106ListProfilesNonMatchingPattern() throws Exception {
        createListFixture();

        assertThat(listNames("^xyz")).isEmpty();
    }

    /**
     * Test: Passes the malformed regular expression "[unclosed" to listProfiles().
     * Expected: the database rejects pattern compilation with SelectAIException whose cause
     * contains ORA-12726.
     */
    @Test
    void test12107ListProfilesInvalidRegex() {
        assertThatThrownBy(() -> selectAI.listProfiles("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
    }

    /**
     * Test: Creates profile fixtures and searches with the literal string pattern "test123".
     * Expected: no generated profile name matches that pattern, so listProfiles() returns an
     * empty list.
     */
    @Test
    void test12108ListProfilesNumericStringPatternUsingPattern() throws Exception {
        createListFixture();

        assertThat(listNames("test123")).isEmpty();
    }

    /**
     * Test: Creates profile fixtures and appends the regex end anchor "$" to one exact generated
     * name.
     * Expected: the anchored pattern returns exactly that profile name.
     */
    @Test
    void test12109ListProfilesDollarPatternUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String exact = names.get(0).toUpperCase(Locale.ROOT);

        assertThat(listNames(exact + "$")).containsExactly(exact);
    }

    /**
     * Test: Passes a regex containing 1,000 consecutive a characters between ^ and $ to
     * listProfiles().
     * Expected: the database rejects the oversized regular expression with SelectAIException and
     * ORA-12733.
     */
    @Test
    void test12110ListProfilesOverlongRegex() {
        String pattern = "^" + "a".repeat(1000) + "$";

        assertThatThrownBy(() -> selectAI.listProfiles(pattern))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12733:"));
    }

    private List<String> createListFixture() throws Exception {
        String listPrefix = uniqueProfileName("PROFILE_LIST");
        String otherPrefix = uniqueProfileName("PROFILE_OTHER");
        return List.of(
                createManagedProfile(listPrefix + "_1", profileTestAttributes(),
                        "Test profile").getProfileName(),
                createManagedProfile(listPrefix + "_2", profileTestAttributes(),
                        "Test profile").getProfileName(),
                createManagedProfile(listPrefix + "_3", profileTestAttributes(),
                        "Test profile").getProfileName(),
                createManagedProfile(listPrefix + "_4", profileTestAttributes(),
                        "Test profile").getProfileName(),
                createManagedProfile(listPrefix + "_5", profileTestAttributes(),
                        "Test profile").getProfileName(),
                createManagedProfile(otherPrefix + "_1", profileTestAttributes(),
                        "Other profile").getProfileName()
        );
    }

    private List<String> listNames(String pattern) throws Exception {
        return selectAI.listProfiles(pattern).stream()
                .map(Profile::getProfileName)
                .toList();
    }
}
