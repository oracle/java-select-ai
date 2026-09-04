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
import com.oracle.database.selectai.integration.IntegrationTestFixture;
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

/** Integration coverage for DropVectorIndex. */
class DropVectorIndexIT extends VectorIndexIntegrationFixture {

    /**
     * Test: Create one index, call drop(true), and query the managed-index listing by its exact
     * generated name.
     * Expected: drop(true) returns true and the index name is absent from the listing.
     */
    @Test
    void test51000SingleDropRemovesIndex() throws Exception {
        String name = uniqueIndexName("DROP_5101");
        VectorIndex index = createConfiguredIndex(name);
        index.create();
        assertThat(index.drop(true)).isTrue();
        assertIndexAbsent(name);
    }

    /**
     * Test: Create an index, call drop(true), call drop(true) again on the same object, and then
     * query the exact name.
     * Expected: both forced drops return true, the second call does not recreate anything, and the
     * index remains absent.
     */
    @Test
    void test51001DroppingSameIndexTwiceWithForceIsIdempotent() throws Exception {
        String name = uniqueIndexName("DROP_5103");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();
        assertThat(index.drop(true)).isTrue();
        assertThat(index.drop(true)).isTrue();
        assertIndexAbsent(name);
    }

    /**
     * Test: Create an index and call drop(true), where includeData=true requests removal of the
     * vector index and its generated $VECTAB table.
     * Expected: the index is absent from the managed listing and userTableExists(name + "$VECTAB")
     * returns false.
     */
    @Test
    void test51002DropIncludeDataTrueRemovesVectorTable() throws Exception {
        String name = uniqueIndexName("DROP_5104");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();
        assertThat(index.drop(true)).isTrue();
        assertIndexAbsent(name);
        assertThat(userTableExists(name + "$VECTAB")).isFalse();
    }

    /**
     * Test: Create an index, call drop(false, true), and attempt to recreate it while its backing
     * vector table remains; finally drop that table explicitly.
     * Expected: metadata is removed, the $VECTAB table remains, recreation is rejected with an
     * ORA-20000 database error, and the test cleanup removes the leftover table.
     */
    @Test
    void test51003DropIncludeDataFalseLeavesVectorTable() throws Exception {
        String name = uniqueIndexName("DROP_5105");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();

        try {
            assertThat(index.drop(false, true)).isTrue();
            assertIndexAbsent(name);
            assertThat(userTableExists(name + "$VECTAB")).isTrue();
            assertThatThrownBy(() -> index.create())
                    .isInstanceOfSatisfying(SelectAIException.class,
                            exception -> assertThat(exception.getCause())
                                    .hasMessageContaining("ORA-20000:"));
        } finally {
            dropUserTableIfExists(name + "$VECTAB");
        }
    }

    /**
     * Test: Perform a metadata-only forced drop, confirm the backing table prevents recreation,
     * remove the table, then perform the metadata-only drop a second time.
     * Expected: the first drop removes metadata, recreation fails with ORA-20000 while the table
     * exists, and the second drop returns true after table cleanup while the index remains absent.
     */
    @Test
    void test51004RepeatedMetadataOnlyDropIsIdempotent() throws Exception {
        String name = uniqueIndexName("DROP_5106");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();

        try {
            assertThat(index.drop(false, true)).isTrue();
            assertIndexAbsent(name);
            assertThatThrownBy(() -> index.create())
                    .isInstanceOfSatisfying(SelectAIException.class,
                            exception -> assertThat(exception.getCause())
                                    .hasMessageContaining("ORA-20000:"));
            dropUserTableIfExists(name + "$VECTAB");
            assertThat(index.drop(false, true)).isTrue();
            assertIndexAbsent(name);
        } finally {
            dropUserTableIfExists(name + "$VECTAB");
        }
    }

    /**
     * Test: Perform a metadata-only forced drop, attempt recreation while the vector table remains,
     * call the same drop again, and explicitly remove the table afterward.
     * Expected: recreation reports ORA-20000, the repeated metadata-only drop returns true, and
     * the index is absent after the test's table cleanup.
     */
    @Test
    void test51005RepeatedMetadataOnlyDropWithCleanup() throws Exception {
        String name = uniqueIndexName("DROP_5107");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();

        try {
            assertThat(index.drop(false, true)).isTrue();
            assertIndexAbsent(name);
            assertThatThrownBy(() -> index.create())
                    .isInstanceOfSatisfying(SelectAIException.class,
                            exception -> assertThat(exception.getCause())
                                    .hasMessageContaining("ORA-20000:"));
            assertThat(index.drop(false, true)).isTrue();
            dropUserTableIfExists(name + "$VECTAB");
            assertIndexAbsent(name);
        } finally {
            dropUserTableIfExists(name + "$VECTAB");
        }
    }

    /**
     * Test: Ask SelectAI.vectorIndex(name) for a generated name that has not been created.
     * Expected: Java lookup throws SelectAIException with the exact message
     * "Vector index not found: <name>" before drop can be called.
     */
    @Test
    void test51006DropNonexistentIndexUsesJavaLookupContract() throws Exception {
        String name = uniqueIndexName("NONEXISTENT_5109");
        assertThatThrownBy(() -> selectAI.vectorIndex(name))
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Vector index not found: " + name);
    }

    /**
     * Test: Create an index, update match_limit through update("match_limit", "10"), then call
     * drop(true) and look up the name.
     * Expected: the attribute update and drop return true, and the updated index is absent from
     * the managed-index listing.
     */
    @Test
    void test51007DropAfterAttributeUpdate() throws Exception {
        String name = uniqueIndexName("DROP_5110");
        VectorIndex index = createConfiguredIndex(name);
        index.create();
        assertThat(index.update("match_limit", "10")).isTrue();
        assertThat(index.drop(true)).isTrue();
        assertIndexAbsent(name);
    }

    /**
     * Test: Create an index using a mixed-case name, list it with an exact regular expression,
     * drop it with includeData=true, and repeat the same lookup.
     * Expected: listing exposes the database's uppercase name while the index exists, and the
     * uppercase name is absent after drop.
     */
    @Test
    void test51008CaseSensitiveCreateAndDrop() throws Exception {
        String name = uniqueIndexName("CaseSensitive_5111");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(createForIntegration(index)).isTrue();
        assertThat(listNames("^" + name + "$"))
                .contains(name.toUpperCase(Locale.ROOT));
        index.drop(true);
        assertThat(listNames("^" + name + "$"))
                .doesNotContain(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Test: Create and drop an index whose generated name includes 35 additional X characters.
     * Expected: the database accepts this long-but-valid name, drop(true) removes it, and the
     * exact-name listing is empty afterward.
     */
    @Test
    void test51009LongNameCreateAndDrop() throws Exception {
        String name = uniqueIndexName("DROP_5112_" + "X".repeat(35));
        VectorIndex index = createConfiguredIndex(name);
        index.create();
        index.drop(true);
        assertIndexAbsent(name);
    }

    /**
     * Test: Create three independently named indexes in a loop, then obtain each by name and call
     * drop(true).
     * Expected: each create and drop returns true, and each name is absent immediately after its
     * corresponding drop.
     */
    @Test
    void test51010BulkCreateAndDrop() throws Exception {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String name = uniqueIndexName("BULK_5113_" + i);
            names.add(name);
            createConfiguredIndex(name).create();
        }
        for (String name : names) {
            assertThat(selectAI.vectorIndex(name).drop(true)).isTrue();
            assertIndexAbsent(name);
        }
    }

    /**
     * Test: Create an index, drop it with includeData=true, and create the same configured object
     * again under the same name.
     * Expected: the recreated index is accepted and appears in the exact-name listing.
     */
    @Test
    void test51011DropAndRecreateWithSameName() throws Exception {
        String name = uniqueIndexName("DROP_5115");
        VectorIndex index = createConfiguredIndex(name);
        index.create();
        index.drop(true);
        assertThat(index.create()).isTrue();
        assertIndexPresent(name);
    }

    /**
     * Test: Create two indexes with different names, drop only the first, and query both names.
     * Expected: the first name is absent while the second name remains present, proving that drop
     * targets only the selected index.
     */
    @Test
    void test51012DropOneOfMultipleIndexes() throws Exception {
        String first = uniqueIndexName("DROP_5116_A");
        String second = uniqueIndexName("DROP_5116_B");
        createConfiguredIndex(first).create();
        createConfiguredIndex(second).create();
        selectAI.vectorIndex(first).drop(true);
        assertIndexAbsent(first);
        assertIndexPresent(second);
    }

    /**
     * Test: Create one index, retrieve it through listVectorIndexes("^<name>$"), drop the returned
     * VectorIndex object, and query the exact name again.
     * Expected: the pattern returns exactly the created index, its drop returns true, and the name
     * is absent afterward.
     */
    @Test
    void test51013DropIndexSelectedByPattern() throws Exception {
        String name = uniqueIndexName("DROP_5117");
        createConfiguredIndex(name).create();

        List<VectorIndex> matchingIndexes = selectAI.listVectorIndexes("^" + name + "$");
        assertThat(matchingIndexes).extracting(VectorIndex::getIndexName).containsExactly(name);
        assertThat(matchingIndexes.get(0).drop(true)).isTrue();
        assertIndexAbsent(name);
    }

    /**
     * Test: Create an index and call drop(false), leaving force disabled and using the default data
     * inclusion behavior.
     * Expected: drop(false) returns true and the index is absent from the managed listing.
     */
    @Test
    void test51014NonForceDrop() throws Exception {
        String name = uniqueIndexName("DROP_5119");
        VectorIndex index = createConfiguredIndex(name);
        index.create();
        assertThat(index.drop(false)).isTrue();
        assertIndexAbsent(name);
    }

    /**
     * Test: Create an index, call drop(false), then call drop(false) again on the same object.
     * Expected: the first drop returns true; the second non-forced drop raises SelectAIException
     * with an ORA-20048 cause, and the index remains absent.
     */
    @Test
    void test51015RepeatedNonForceDrop() throws Exception {
        String name = uniqueIndexName("DROP_5120");
        VectorIndex index = createConfiguredIndex(name);
        index.create();
        assertThat(index.drop(false)).isTrue();
        assertIndexAbsent(name);
        assertThatThrownBy(() -> index.drop(false))
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20048:"));
        assertIndexAbsent(name);
    }

    /**
     * Test: Create an index and call drop(false, false), disabling both force and removal of the
     * generated vector table; explicitly remove the table during cleanup.
     * Expected: metadata is removed, the index is absent, and userTableExists(name + "$VECTAB")
     * remains true until the test cleanup drops it.
     */
    @Test
    void test51016DropWithoutIncludeDataAndNonForceLeavesVectorTable() throws Exception {
        String name = uniqueIndexName("DROP_5121");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();

        try {
            assertThat(index.drop(false, false)).isTrue();
            assertIndexAbsent(name);
            assertThat(userTableExists(name + "$VECTAB")).isTrue();
        } finally {
            dropUserTableIfExists(name + "$VECTAB");
        }
    }
}
