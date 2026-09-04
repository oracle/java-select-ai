/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.syntheticdata;

import com.oracle.database.selectai.model.SyntheticDataBatchRequest;
import com.oracle.database.selectai.model.SyntheticDataObjectList;
import com.oracle.database.selectai.model.SyntheticDataParams;
import com.oracle.database.selectai.model.SyntheticDataSingleRequest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live integration coverage for synthetic-data generation.
 *
 * <p>The suite exercises the typed single-object and batch request models,
 * parameter combinations, generated row counts, and database error handling
 * through the public synchronous SDK APIs.</p>
 */
class SyntheticDataIT extends SyntheticDataIntegrationFixture {

    @Override
    protected String profileObjectList() {
        return objectListFor("people", "gymnast");
    }

    /**
     * Test: Uses the fixture's enforced object list containing {@code people} and
     * {@code gymnast} to build a single-object request for {@code people} with
     * {@code record_count=5}, the prompt {@code "age must be greater than 20"},
     * and parameters {@code sample_rows=10}, {@code table_statistics=true},
     * {@code priority=HIGH}, and {@code comments=true}. It also checks the
     * serialized parameter JSON before sending the request and records the
     * existing row count in the configured database schema.
     * Expected: Parameter JSON contains all four supplied values, the synchronous
     * {@code generateSyntheticData} call returns {@code true}, and the
     * {@code people} row count increases by exactly five rows.
     */
    @Test
    void test18000GeneratesWithFullParameterSet() throws Exception {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(10)
                .tableStatistics(true)
                .priority("HIGH")
                .comments(true)
                .build();
        assertThat(params.toJson())
                .contains("\"sample_rows\":10")
                .contains("\"table_statistics\":true")
                .contains("\"priority\":\"HIGH\"")
                .contains("\"comments\":true");
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder(SINGLE_OBJECT_NAME)
                .recordCount(5)
                .userPrompt("age must be greater than 20")
                .params(params)
                .build();

        assertRowsGenerated(request, 5);
    }

    /**
     * Test: Builds the minimum single-object request for the fixture's
     * {@code people} table: {@code record_count=1}, with no user prompt and no
     * optional parameter object. It records the table's current row count before
     * invoking the profile's synchronous synthetic-data operation.
     * Expected: The request is accepted, {@code generateSyntheticData} returns
     * {@code true}, and exactly one row is added to {@code people}.
     */
    @Test
    void test18001GeneratesWithMinimumFields() throws Exception {
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder(SINGLE_OBJECT_NAME)
                .recordCount(1)
                .build();

        assertRowsGenerated(request, 1);
    }

    /**
     * Test: Builds a request for the fixture's {@code people} table with
     * {@code record_count=1} and parameters {@code sample_rows=0} and
     * {@code priority=HIGH}. This checks the request's requested output count
     * separately from its sample-row parameter.
     * Expected: The database accepts {@code sample_rows=0}; the call returns
     * {@code true}, and the {@code people} row count increases by one row.
     */
    @Test
    void test18002GeneratesWithZeroSampleRows() throws Exception {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(0)
                .priority("HIGH")
                .build();
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder(SINGLE_OBJECT_NAME)
                .recordCount(1)
                .params(params)
                .build();

        assertRowsGenerated(request, 1);
    }

    /**
     * Test: Builds a request for the fixture's {@code people} table with
     * {@code record_count=1} and parameters {@code sample_rows=1} and
     * {@code priority=HIGH}, then compares the table row count before and after
     * the synchronous generation call.
     * Expected: The call returns {@code true} and adds exactly one row to
     * {@code people}.
     */
    @Test
    void test18003GeneratesWithOneSampleRow() throws Exception {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(1)
                .priority("HIGH")
                .build();
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder(SINGLE_OBJECT_NAME)
                .recordCount(1)
                .params(params)
                .build();

        assertRowsGenerated(request, 1);
    }

    /**
     * Test: Builds a one-row request for the fixture's {@code people} table with
     * {@code sample_rows=1} and {@code priority=LOW}, then invokes synthetic-data
     * generation through the profile.
     * Expected: The low-priority request is accepted, the method returns
     * {@code true}, and the {@code people} row count increases by exactly one.
     */
    @Test
    void test18004GeneratesWithLowPriority() throws Exception {
        SyntheticDataParams params = SyntheticDataParams.builder()
                .sampleRows(1)
                .priority("LOW")
                .build();
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder(SINGLE_OBJECT_NAME)
                .recordCount(1)
                .params(params)
                .build();

        assertRowsGenerated(request, 1);
    }



    /**
     * Test: Builds a single-object request with an explicit owner and submits it
     * through the single-object overload.
     * Expected: The request is accepted and one row is generated for
     * {@code ADMIN.people}.
     */
    @Test
    void test18005GeneratesSingleObjectForExplicitOwner() throws Exception {
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder(SINGLE_OBJECT_NAME)
                .ownerName(BATCH_OBJECT_OWNER)
                .recordCount(1)
                .build();

        assertRowsGenerated(request, 1);
    }

    /**
     * Test: Captures row counts for {@code ADMIN.people} and {@code ADMIN.gymnast},
     * then builds one batch request with {@code sample_rows=1} and
     * {@code priority=HIGH}. The batch contains {@code record_count=2} for
     * {@code ADMIN.people} with the prompt {@code "Use realistic person names"}
     * and {@code record_count=3} for {@code ADMIN.gymnast} with the prompt
     * {@code "Use realistic gymnastics scores"}; the test also inspects the
     * generated object-list JSON.
     * Expected: The JSON contains both object names, both record counts, and both
     * prompts; generation returns {@code true}; and the database row counts rise
     * by two for {@code ADMIN.people} and three for {@code ADMIN.gymnast}.
     */
    @Test
    void test18006GeneratesForMultipleObjectsUsingDifferentCountsAndPrompts() throws Exception {
        long[] rowCountsBefore = new long[BATCH_OBJECT_NAMES.length];
        for (int index = 0; index < BATCH_OBJECT_NAMES.length; index++) {
            rowCountsBefore[index] = rowCount(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[index]);
        }

        SyntheticDataBatchRequest.Builder request = SyntheticDataBatchRequest.builder()
                .params(SyntheticDataParams.builder()
                        .sampleRows(1)
                        .priority("HIGH")
                        .build());
        request.addObject(SyntheticDataObjectList.builder(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[0])
                .recordCount(2)
                .userPrompt("Use realistic person names")
                .build());
        request.addObject(SyntheticDataObjectList.builder(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[1])
                .recordCount(3)
                .userPrompt("Use realistic gymnastics scores")
                .build());
        SyntheticDataBatchRequest builtRequest = request.build();

        assertThat(builtRequest.getObjectListJson())
                .contains("\"record_count\":2")
                .contains("\"record_count\":3")
                .contains("Use realistic person names")
                .contains("Use realistic gymnastics scores");

        assertThat(profile.generateSyntheticData(builtRequest)).isTrue();

        assertThat(rowCount(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[0]))
                .as("generated rows for %s.%s", BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[0])
                .isEqualTo(rowCountsBefore[0] + 2);
        assertThat(rowCount(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[1]))
                .as("generated rows for %s.%s", BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[1])
                .isEqualTo(rowCountsBefore[1] + 3);
    }



    /**
     * Test: Records the initial row counts for {@code ADMIN.people} and
     * {@code ADMIN.gymnast}, builds a batch requesting one row for each object
     * with {@code sample_rows=1} and {@code priority=HIGH}, and submits the same
     * batch request twice through the profile.
     * Expected: Both synchronous calls return {@code true}; each invocation adds
     * one row to each table, so the final count is the initial count plus two in
     * both {@code ADMIN.people} and {@code ADMIN.gymnast}.
     */
    @Test
    void test18007RepeatedBatchGenerationAddsRowsForEachObject() throws Exception {
        long peopleBefore = rowCount(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[0]);
        long gymnastBefore = rowCount(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[1]);
        SyntheticDataBatchRequest request = batchRequest(1, 1);

        assertThat(profile.generateSyntheticData(request)).isTrue();
        assertThat(profile.generateSyntheticData(request)).isTrue();

        assertThat(rowCount(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[0]))
                .isEqualTo(peopleBefore + 2);
        assertThat(rowCount(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[1]))
                .isEqualTo(gymnastBefore + 2);
    }

    /**
     * Test: Builds a two-object batch for {@code people} and {@code gymnast} using
     * the nonexistent owner {@code JSAI_IT_OWNER_DOES_NOT_EXIST}, requesting one
     * row for each object, and submits it through the profile.
     * Expected: No rows are generated; the call throws {@code SelectAIException}
     * whose cause is {@code SQLException} with a message beginning
     * {@code ORA-20000: Object "JSAI_IT_OWNER_DOES_NOT_EXIST"}. The fixture's
     * normal profile, credential, and schema cleanup still runs after the test.
     */
    @Test
    void test18008BatchGenerationRejectsWrongOwner() {
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("JSAI_IT_OWNER_DOES_NOT_EXIST", "people")
                        .recordCount(1)
                        .build())
                .addObject(SyntheticDataObjectList.builder("JSAI_IT_OWNER_DOES_NOT_EXIST", "gymnast")
                        .recordCount(1)
                        .build())
                .build();

        assertThatThrownBy(() -> profile.generateSyntheticData(request))
                .isInstanceOfSatisfying(com.oracle.database.selectai.model.SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-20000: Object \"JSAI_IT_OWNER_DOES_NOT_EXIST\"");
                });
    }

    /**
     * Test: Builds a single-object request with a nonexistent owner.
     * Expected: The single-object overload reports the database failure as a
     * SelectAIException with the original SQL exception as its cause.
     */
    @Test
    void test18009SingleGenerationRejectsWrongOwner() {
        SyntheticDataSingleRequest request = SyntheticDataSingleRequest.builder(SINGLE_OBJECT_NAME)
                .ownerName("JSAI_IT_OWNER_DOES_NOT_EXIST")
                .recordCount(1)
                .build();

        assertThatThrownBy(() -> profile.generateSyntheticData(request))
                .isInstanceOfSatisfying(com.oracle.database.selectai.model.SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-20000: Object \"JSAI_IT_OWNER_DOES_NOT_EXIST\"");
                });
    }

    private SyntheticDataBatchRequest batchRequest(int peopleCount, int gymnastCount) {
        return SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[0])
                        .recordCount(peopleCount)
                        .build())
                .addObject(SyntheticDataObjectList.builder(BATCH_OBJECT_OWNER, BATCH_OBJECT_NAMES[1])
                        .recordCount(gymnastCount)
                        .build())
                .params(SyntheticDataParams.builder().sampleRows(1).priority("HIGH").build())
                .build();
    }

    private void assertRowsGenerated(SyntheticDataSingleRequest request, long expectedRows)
            throws Exception {
        String owner = request.getOwnerName() == null
                ? env("SELECT_AI_IT_DB_USER")
                : request.getOwnerName();
        assertThat(owner).as("row-count owner").isNotBlank();
        long rowsBefore = rowCount(owner, request.getObjectName());

        assertThat(profile.generateSyntheticData(request)).isTrue();

        assertThat(rowCount(owner, request.getObjectName()))
                .as("generated rows for %s.%s", owner, request.getObjectName())
                .isEqualTo(rowsBefore + expectedRows);
    }
}
