/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.summarize;

import com.oracle.database.selectai.model.SummaryParams;
import com.oracle.database.selectai.model.SelectAIException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live integration tests for profile summarization with inline and URI content.
 */
class SummarizeIT extends SummarizeIntegrationFixture {


    @Override
    protected String profileObjectList() {
        return null;
    }

    private static final String CONTENT = """
            A gas cloud in our galaxy, Sagittarius B2, contains enough alcohol to brew
            400 trillion pints of beer, and some stars are so cool that you could touch
            them without being burned. Meanwhile, on the exoplanet 55 Cancri e, a form
            of hot ice exists where high pressure prevents water from becoming gas even
            at high temperatures. Ancient stars in the Milky Way's halo are much older
            than the Sun, providing clues about the early universe.
            """;

    /**
     * Test: Calls profile.summarize(CONTENT, null, null, SUMMARY_PROMPT, params)
     * with SummaryParams.Style.PARAGRAPH and no URI or credential.
     * Expected: The database returns a non-blank summary that is not equal to CONTENT.trim(),
     * confirming inline text and the paragraph style were processed by the created profile.
     */
    @Test
    void test17000SummarizesInlineContentUsingTheCreatedProfile() throws Exception {
        String summary = profile.summarize(
                CONTENT,
                null,
                null,
                SUMMARY_PROMPT,
                SummaryParams.builder()
                        .summaryStyle(SummaryParams.Style.PARAGRAPH)
                        .build());

        assertThat(summary).isNotBlank().isNotEqualTo(CONTENT.trim());
    }



    /**
     * Test: Calls profile.summarize(CONTENT, null, null, null, null), leaving credential, location,
     * user prompt, and SummaryParams all null so the database supplies its defaults.
     * Expected: The returned summary is non-blank and differs from CONTENT.trim().
     */
    @Test
    void test17001SummarizesInlineContentUsingDatabaseDefaultsWhenOptionalArgumentsAreNull()
            throws Exception {
        String summary = profile.summarize(CONTENT, null, null, null, null);

        assertThat(summary).isNotBlank().isNotEqualTo(CONTENT.trim());
    }

    /**
     * Test: Reads the required object-storage location, resolves the summarization credential,
     * and calls profile.summarize(null, credential, location, SUMMARY_PROMPT,
     * PARAGRAPH parameters). The fixture skips this scenario when the URI prerequisite is absent.
     * Expected: The database reads the external object and returns a non-blank summary.
     */
    @Test
    void test17002SummarizesContentAtConfiguredLocationWhenPrerequisitesAreAvailable() throws Exception {
        String location = requiredFeatureValue(
                "SELECT_AI_IT_SUMMARIZE_LOCATION_URI",
                "The URI summarization scenario requires a configured readable location");
        String credential = summarizeCredential();
        String summary = profile.summarize(
                null,
                credential,
                location,
                SUMMARY_PROMPT,
                SummaryParams.builder()
                        .summaryStyle(SummaryParams.Style.PARAGRAPH)
                        .build());

        assertThat(summary).isNotBlank();
    }

    /**
     * Test: Reads the required configured location and credential, then calls
     * profile.summarize(null, credential, location, null, null), leaving user prompt and summary
     * parameters null.
     * Expected: Database defaults are applied to the external content and the returned summary is
     * non-blank.
     */
    @Test
    void test17003SummarizesContentAtConfiguredLocationUsingDatabaseDefaultsWhenOptionalArgumentsAreNull()
            throws Exception {
        String location = requiredFeatureValue(
                "SELECT_AI_IT_SUMMARIZE_LOCATION_URI",
                "The URI summarization scenario requires a configured readable location");
        String credential = summarizeCredential();

        String summary = profile.summarize(null, credential, location, null, null);

        assertThat(summary).isNotBlank();
    }

    /**
     * Test: Calls profile.summarize() with inline CONTENT, user prompt
     * "Summarize the key facts as a concise list", minWords=20, maxWords=80, style=LIST,
     * chunkProcessingMethod=MAP_REDUCE, and extractivenessLevel=LOW.
     * Expected: The returned summary is non-blank and differs from CONTENT.trim(), proving the
     * complete SummaryParams payload was accepted and used for inline content.
     */
    @Test
    void test17004SummarizesInlineContentWithAllSummaryParameters() throws Exception {
        SummaryParams params = SummaryParams.builder()
                .minWords(20)
                .maxWords(80)
                .summaryStyle(SummaryParams.Style.LIST)
                .chunkProcessingMethod(SummaryParams.ChunkProcessingMethod.MAP_REDUCE)
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.LOW)
                .build();

        String summary = profile.summarize(
                CONTENT,
                null,
                null,
                "Summarize the key facts as a concise list",
                params);

        assertThat(summary).isNotBlank().isNotEqualTo(CONTENT.trim());
    }

    /**
     * Test: Calls profile.summarize() with the documented MEDIUM extractiveness level.
     * Expected: The database accepts the medium extractiveness value and returns a non-blank
     * summary without the SDK injecting other summary attributes.
     */
    @Test
    void test17005SummarizesInlineContentWithMediumExtractivenessLevel() throws Exception {
        SummaryParams params = SummaryParams.builder()
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.MEDIUM)
                .build();

        String summary = profile.summarize(
                CONTENT,
                null,
                null,
                "Summarize the key facts",
                params);

        assertThat(summary).isNotBlank().isNotEqualTo(CONTENT.trim());
    }

    /**
     * Test: Reads the configured location and credential, then calls profile.summarize() with no
     * inline content, user prompt "Summarize the key facts as a concise list", minWords=20,
     * maxWords=80, style=LIST, chunkProcessingMethod=ITERATIVE_REFINEMENT, and
     * extractivenessLevel=HIGH.
     * Expected: The database summarizes the external object and returns non-blank content.
     */
    @Test
    void test17006SummarizesContentAtConfiguredLocationWithAllSummaryParameters() throws Exception {
        String location = requiredFeatureValue(
                "SELECT_AI_IT_SUMMARIZE_LOCATION_URI",
                "The URI summarization scenario requires a configured readable location");
        String credential = summarizeCredential();
        SummaryParams params = SummaryParams.builder()
                .minWords(20)
                .maxWords(80)
                .summaryStyle(SummaryParams.Style.LIST)
                .chunkProcessingMethod(SummaryParams.ChunkProcessingMethod.ITERATIVE_REFINEMENT)
                .extractivenessLevel(SummaryParams.ExtractivenessLevel.HIGH)
                .build();

        String summary = profile.summarize(
                null,
                credential,
                location,
                "Summarize the key facts as a concise list",
                params);

        assertThat(summary).isNotBlank();
    }

    /**
     * Test: Takes the configured readable location, appends a generated "JSAI_MISSING_..." suffix,
     * and calls
     * profile.summarize(null, validCredential, missingLocation, null, null).
     * Expected: The SDK throws SelectAIException with error code 20404; its cause is SQLException
     * whose message starts with "ORA-20404: Object not found - ".
     */
    @Test
    void test17007SummarizeRejectsMissingObjectWithOra20404() throws Exception {
        String location = requiredLocation();
        String missingLocation = location + "-JSAI_MISSING_"
                + UUID.randomUUID().toString().replace("-", "");
        String credential = summarizeCredential();

        assertThatThrownBy(() -> profile.summarize(
                null, credential, missingLocation, null, null))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(20404);
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-20404: Object not found - ");
                });
    }

    /**
     * Test: Reads the configured location, generates a credential name beginning
     * "JSAI_MISSING_SUMMARIZE_CREDENTIAL_" that does not exist, and calls
     * profile.summarize(null, missingCredential, location, null, null).
     * Expected: The SDK throws SelectAIException whose cause is SQLException with a message that
     * starts "ORA-20004: Credential \"", identifying the missing database credential.
     */
    @Test
    void test17008SummarizeRejectsNonexistentCredential() throws Exception {
        String location = requiredLocation();
        String missingCredential = "JSAI_MISSING_SUMMARIZE_CREDENTIAL_"
                + UUID.randomUUID().toString().replace("-", "").toUpperCase();

        assertThatThrownBy(() -> profile.summarize(
                null, missingCredential, location, null, null))
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-20004: Credential \"");
                });
    }

    /**
     * Test: Calls profile.disable(), attempts profile.summarize(CONTENT, null, null, null, null)
     * while the profile is disabled, and executes profile.enable() in the finally block.
     * Expected: disable() and cleanup enable() return true; summarize() throws SelectAIException
     * whose SQLException cause starts with "ORA-20046: Profile ".
     */
    @Test
    void test17009SummarizeWithDisabledProfileFails() throws Exception {
        assertThat(profile.disable()).isTrue();
        try {
            assertThatThrownBy(() -> profile.summarize(CONTENT, null, null, null, null))
                    .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                        assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                        assertThat(exception.getCause().getMessage())
                                .startsWith("ORA-20046: Profile ");
                    });
        } finally {
            assertThat(profile.enable()).isTrue();
        }
    }

    /**
     * Test: Calls SUMMARIZE with inline content larger than the PL/SQL VARCHAR2 bind limit.
     * Expected: The CLOB content reaches DBMS_CLOUD_AI and a summary is returned.
     */
     @Test
    void test17010SummarizesLargeInlineClobContent() throws Exception {
        String content = largeClobValue("large-summarize-content");

        String summary = profile.summarize(content, null, null, null, null);

        assertThat(summary).isNotBlank();
    }


    private String requiredLocation() {
        return requiredFeatureValue(
                "SELECT_AI_IT_SUMMARIZE_LOCATION_URI",
                "The summarization location scenario requires a configured readable location");
    }

    private static String largeClobValue(String marker) {
        String prefix = marker + ":";
        String filler = "The source text contains reusable facts for summarization. ";
        int remainingLength = 40_000 - prefix.length();
        return prefix + filler.repeat(remainingLength / filler.length() + 1)
                .substring(0, remainingLength);
    }
}
