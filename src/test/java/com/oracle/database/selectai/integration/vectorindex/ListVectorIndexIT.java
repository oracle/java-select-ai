/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

/**
 * Live integration coverage for vector-index operations.
 *
 * <p>The suite uses isolated indexes and credentials from the shared integration
 * fixture. Required vector-index credentials are supplied through the
 * SELECT_AI_IT_* configuration keys.</p>
 *
 * <p>The suite verifies creation, update, metadata, listing, lifecycle, and
 * Java-specific resource behavior through the public synchronous SDK APIs.</p>
 */
package com.oracle.database.selectai.integration.vectorindex;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integration coverage for ListVectorIndex. */
class ListVectorIndexIT extends VectorIndexIntegrationFixture {

    /**
     * Test: Create five indexes with one LIST prefix and a sixth index with a different prefix,
     * then list using an alternation of all six exact names.
     * Expected: listVectorIndexes() returns exactly those six uppercase names, with no unrelated
     * database indexes included.
     */
    @Test
    void test54000ListAllManagedIndexes() throws Exception {
        List<String> names = createListFixture();
        assertThat(listNames("^(" + String.join("|", names) + ")$"))
                .containsExactlyInAnyOrderElementsOf(names.stream()
                        .map(String::toUpperCase).toList());
    }

    /**
     * Test: Create the five LIST-prefixed indexes and list them with regex ^<LIST_PREFIX>_[1-5]$;
     * read profile_name from every returned index.
     * Expected: exactly five indexes are returned and every profile_name equals the isolated
     * profile name used during creation.
     */
    @Test
    void test54001ListProfileAttributeVerification() throws Exception {
        createListFixture();
        List<VectorIndex> indexes = selectAI.listVectorIndexes("^" + listPrefix + "_[1-5]$");

        assertThat(indexes).hasSize(5);
        assertThat(indexes).allSatisfy(index ->
                assertThat(index.getVectorIndexAttributes().getProfileName()).isEqualTo(profileName));
    }

    /**
     * Test: List the five LIST-prefixed indexes and read object_storage_credential_name from each
     * returned VectorIndex.
     * Expected: exactly five indexes are returned and every credential name equals the credential
     * created by this test fixture.
     */
    @Test
    void test54002ListObjectCredentialVerification() throws Exception {
        createListFixture();
        List<VectorIndex> indexes = selectAI.listVectorIndexes("^" + listPrefix + "_[1-5]$");

        assertThat(indexes).hasSize(5);
        assertThat(indexes).allSatisfy(index ->
                assertThat(index.getVectorIndexAttributes().getObjectStorageCredentialName())
                        .isEqualTo(objectStorageCredentialName));
    }

    /**
     * Test: List the five LIST-prefixed indexes created with description "Test vector index" and
     * read getDescription() on each result.
     * Expected: all five returned descriptions equal exactly "Test vector index".
     */
    @Test
    void test54003ListDescriptions() throws Exception {
        createListFixture();
        assertThat(selectAI.listVectorIndexes("^" + listPrefix + "_[1-5]$")).hasSize(5)
                .allSatisfy(index -> assertThat(index.getDescription())
                        .isEqualTo("Test vector index"));
    }

    /**
     * Test: Create the list fixture and call listVectorIndexes("^<EXACT_NAME>$") for one generated
     * name.
     * Expected: the anchored regular expression returns exactly one result, whose uppercase name
     * equals the selected fixture name.
     */
    @Test
    void test54004ListExactMatchUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String exact = names.get(0).toUpperCase();
        assertThat(listNames("^" + exact + "$")).containsExactly(exact);
    }

    /**
     * Test: Create five indexes sharing a prefix and call listVectorIndexes("^<PREFIX>").
     * Expected: all five shared-prefix names are returned, while the sixth index with a different
     * prefix is excluded.
     */
    @Test
    void test54005ListMultipleMatchesUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String prefix = names.get(0).substring(0, names.get(0).lastIndexOf('_'));
        assertThat(listNames("^" + prefix)).containsExactlyInAnyOrderElementsOf(
                names.subList(0, 5).stream().map(String::toUpperCase).toList());
    }

    /**
     * Test: Create the list fixture and use an uppercase, anchored prefix regex matching the second
     * generated name.
     * Expected: at least the second fixture index is returned by the database regex lookup.
     */
    @Test
    void test54006ListCaseSensitiveRegexUsingPattern() throws Exception {
        List<String> names = createListFixture();
        assertThat(listNames("^" + names.get(1).toUpperCase()))
                .isNotEmpty();
    }

    /**
     * Test: Create the list fixture and pass the lowercase form of the generated LIST prefix to
     * listVectorIndexes().
     * Expected: database regex matching is case-insensitive for this lookup and returns at least
     * one LIST-prefixed index.
     */
    @Test
    void test54007ListCaseInsensitiveRegexUsingPattern() throws Exception {
        createListFixture();
        assertThat(listNames("^" + listPrefix.toLowerCase(Locale.ROOT))).isNotEmpty();
    }

    /**
     * Test: Create five indexes with one prefix and one index with a second prefix, then list with
     * the regex alternation "^(<FIRST_PREFIX>|<SECOND_PREFIX>)".
     * Expected: all six fixture indexes are returned and no name outside either prefix is included.
     */
    @Test
    void test54008ListOrRegexUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String a = names.get(0).substring(0, names.get(0).lastIndexOf('_'));
        String b = names.get(5).substring(0, names.get(5).lastIndexOf('_'));
        assertThat(listNames("^(" + a + "|" + b + ")"))
                .containsExactlyInAnyOrderElementsOf(names.stream().map(String::toUpperCase).toList());
    }

    /**
     * Test: Create the list fixture and call listVectorIndexes("^xyz"), a prefix not used by any
     * generated fixture name.
     * Expected: the returned list is empty; no unrelated vector index is treated as a match.
     */
    @Test
    void test54009ListNonMatchingPattern() throws Exception {
        createListFixture();
        assertThat(listNames("^xyz")).isEmpty();
    }

    /**
     * Test: Pass the malformed regular expression "[unclosed" to listVectorIndexes().
     * Expected: the database rejects pattern compilation with SelectAIException whose cause
     * contains ORA-12726.
     */
    @Test
    void test54010ListInvalidRegex() throws Exception {
        createListFixture();
        assertThatThrownBy(() -> selectAI.listVectorIndexes("[unclosed"))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12726:"));
    }

    /**
     * Test: Create the list fixture and search with the literal string pattern "test123".
     * Expected: no generated index name matches that pattern, so listVectorIndexes() returns an
     * empty list.
     */
    @Test
    void test54011ListNumericStringPatternUsingPattern() throws Exception {
        createListFixture();
        assertThat(listNames("test123")).isEmpty();
    }

    /**
     * Test: Create the list fixture and append the regex end anchor "$" to one exact generated
     * name.
     * Expected: the anchored pattern returns exactly that one uppercase index name.
     */
    @Test
    void test54012ListDollarPatternUsingPattern() throws Exception {
        List<String> names = createListFixture();
        String exact = names.get(0).toUpperCase();
        assertThat(listNames(exact + "$")).containsExactly(exact);
    }

    /**
     * Test: Pass a regex containing 1,000 consecutive a characters between ^ and $ to
     * listVectorIndexes().
     * Expected: the database rejects the oversized regular expression with SelectAIException and
     * ORA-12733.
     */
    @Test
    void test54013ListOverlongRegex() throws Exception {
        createListFixture();
        String pattern = "^" + "a".repeat(1000) + "$";
        assertThatThrownBy(() -> selectAI.listVectorIndexes(pattern))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-12733:"));
    }
}
