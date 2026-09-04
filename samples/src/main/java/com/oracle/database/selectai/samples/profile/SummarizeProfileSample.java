/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.profile;

import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SummaryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for summarizing content with a Select AI profile.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a
 * {@link Profile} object, call
 * {@link Profile#summarize(String, String, String, String, SummaryParams)}, and
 * print the summary response.</p>
 *
 * <p>This sample can summarize inline content or content referenced by
 * {@code SELECTAI_SUMMARIZE_LOCATION_URI}. For URI-based summarization, ensure
 * {@code SELECTAI_SUMMARIZE_CREDENTIAL_NAME} names a database credential that
 * can read the referenced object-storage URI. The object URI, credential,
 * network access, and provider/profile settings must be configured before
 * running URI-based summarization.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 *   <li>SELECTAI_SUMMARIZE_CONTENT or SELECTAI_SUMMARIZE_LOCATION_URI</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_SUMMARIZE_CREDENTIAL_NAME - credential name for external content access.</li>
 *   <li>SELECTAI_SUMMARIZE_LOCATION_URI - external content URI.</li>
 *   <li>SELECTAI_SUMMARIZE_PROMPT - summary instruction.</li>
 *   <li>SELECTAI_SUMMARIZE_MIN_WORDS</li>
 *   <li>SELECTAI_SUMMARIZE_MAX_WORDS</li>
 *   <li>SELECTAI_SUMMARIZE_STYLE - {@code paragraph} or {@code list}.</li>
 *   <li>SELECTAI_SUMMARIZE_CHUNK_PROCESSING_METHOD -
 *       {@code iterative_refinement} or {@code map_reduce}.</li>
 *   <li>SELECTAI_SUMMARIZE_EXTRACTIVENESS_LEVEL -
 *       {@code high}, {@code medium}, or {@code low}.</li>
 * </ul>
 *
 * <p>Example {@code SELECTAI_JDBC_URL}:</p>
 * <pre>{@code
 * jdbc:oracle:thin:@mydb_high?TNS_ADMIN=/path/to/wallet
 * }</pre>
 *
 * <p>Before compiling or running this sample, build the SDK jar and copy runtime
 * dependencies. Run the dependency copy command after {@code mvn clean install},
 * because Maven {@code clean} removes the {@code target/} directory.</p>
 * <pre>{@code
 * mvn clean install
 * mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies
 * }</pre>
 *
 * <p>Compile this sample:</p>
 * <pre>{@code
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/profile/SummarizeProfileSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.SummarizeProfileSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class SummarizeProfileSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(SummarizeProfileSample.class);

    private SummarizeProfileSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String content = optionalEnv("SELECTAI_SUMMARIZE_CONTENT");
        String credentialName = optionalEnv("SELECTAI_SUMMARIZE_CREDENTIAL_NAME");
        String locationUri = optionalEnv("SELECTAI_SUMMARIZE_LOCATION_URI");
        String userPrompt = optionalEnv("SELECTAI_SUMMARIZE_PROMPT");
        SummaryParams typedParams = buildSummaryParams();
        validateSummarizeInput(content, credentialName, locationUri);

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            String summary = profile.summarize(content, credentialName, locationUri, userPrompt, typedParams);

            System.out.printf("Summary response:%n%s%n", valueOrEmpty(summary));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to summarize content for Select AI profile '{}'.", profileName, e);
        }
    }

    private static SelectAI createSelectAI() throws SelectAIException {
        DbConnectionConfig dbConnectionConfig = DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();

        return SelectAI.create(dbConnectionConfig);
    }

    private static SummaryParams buildSummaryParams() {
        if (!hasAnyTypedParam()) {
            return null;
        }
        SummaryParams.Builder builder = SummaryParams.builder();
        applyOptionalInteger(builder::minWords, "SELECTAI_SUMMARIZE_MIN_WORDS");
        applyOptionalInteger(builder::maxWords, "SELECTAI_SUMMARIZE_MAX_WORDS");
        applyOptionalStyle(builder, "SELECTAI_SUMMARIZE_STYLE");
        applyOptionalChunkProcessingMethod(builder, "SELECTAI_SUMMARIZE_CHUNK_PROCESSING_METHOD");
        applyOptionalExtractivenessLevel(builder, "SELECTAI_SUMMARIZE_EXTRACTIVENESS_LEVEL");
        return builder.build();
    }

    private static boolean hasAnyTypedParam() {
        return optionalEnv("SELECTAI_SUMMARIZE_MIN_WORDS") != null
                || optionalEnv("SELECTAI_SUMMARIZE_MAX_WORDS") != null
                || optionalEnv("SELECTAI_SUMMARIZE_STYLE") != null
                || optionalEnv("SELECTAI_SUMMARIZE_CHUNK_PROCESSING_METHOD") != null
                || optionalEnv("SELECTAI_SUMMARIZE_EXTRACTIVENESS_LEVEL") != null;
    }

    private static void applyOptionalInteger(IntegerAttributeSetter setter, String envName) {
        String value = optionalEnv(envName);
        if (value != null) {
            setter.set(Integer.valueOf(value));
        }
    }

    private static void applyOptionalStyle(SummaryParams.Builder builder, String envName) {
        String value = optionalEnv(envName);
        if (value != null) {
            builder.summaryStyle(SummaryParams.Style.valueOf(value.trim().toUpperCase()));
        }
    }

    private static void applyOptionalChunkProcessingMethod(SummaryParams.Builder builder, String envName) {
        String value = optionalEnv(envName);
        if (value != null) {
            builder.chunkProcessingMethod(SummaryParams.ChunkProcessingMethod.valueOf(
                    value.trim().toUpperCase()));
        }
    }

    private static void applyOptionalExtractivenessLevel(SummaryParams.Builder builder, String envName) {
        String value = optionalEnv(envName);
        if (value != null) {
            builder.extractivenessLevel(SummaryParams.ExtractivenessLevel.valueOf(value.trim().toUpperCase()));
        }
    }

    private static void validateSummarizeInput(String content, String credentialName, String locationUri) {
        if ((content == null || content.isBlank()) && (locationUri == null || locationUri.isBlank())) {
            throw new IllegalStateException(
                    "Set SELECTAI_SUMMARIZE_CONTENT or SELECTAI_SUMMARIZE_LOCATION_URI before running this sample");
        }
        if (locationUri != null && !locationUri.isBlank()
                && (credentialName == null || credentialName.isBlank())) {
            throw new IllegalStateException(
                    "Set SELECTAI_SUMMARIZE_CREDENTIAL_NAME when SELECTAI_SUMMARIZE_LOCATION_URI is provided");
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface IntegerAttributeSetter {
        void set(Integer value);
    }

}
