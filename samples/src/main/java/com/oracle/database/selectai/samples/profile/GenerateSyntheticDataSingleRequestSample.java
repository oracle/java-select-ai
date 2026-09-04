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
import com.oracle.database.selectai.model.SyntheticDataParams;
import com.oracle.database.selectai.model.SyntheticDataSingleRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for generating synthetic data with a single-object request.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, open a
 * {@link Profile}, build a {@link SyntheticDataSingleRequest}, and call
 * {@link Profile#generateSyntheticData(SyntheticDataSingleRequest)}.</p>
 *
 * <p>This sample changes database state by generating synthetic data for the
 * requested database object.</p>
 *
 * <p>When running this sample individually, ensure the target table or object
 * identified by the synthetic-data environment variables already exists and is
 * visible to {@code SELECTAI_DB_USER}. The integration script creates its
 * synthetic-data validation table before running synthetic-data samples.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 *   <li>SELECTAI_SYNTHETIC_OBJECT_NAME</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_SYNTHETIC_OWNER_NAME</li>
 *   <li>SELECTAI_SYNTHETIC_RECORD_COUNT</li>
 *   <li>SELECTAI_SYNTHETIC_USER_PROMPT</li>
 *   <li>SELECTAI_SYNTHETIC_SAMPLE_ROWS</li>
 *   <li>SELECTAI_SYNTHETIC_TABLE_STATISTICS</li>
 *   <li>SELECTAI_SYNTHETIC_PRIORITY</li>
 *   <li>SELECTAI_SYNTHETIC_COMMENTS</li>
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
 * <p>Compile and run this sample:</p>
 * <pre>{@code
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/profile/GenerateSyntheticDataSingleRequestSample.java
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.GenerateSyntheticDataSingleRequestSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class GenerateSyntheticDataSingleRequestSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateSyntheticDataSingleRequestSample.class);

    private GenerateSyntheticDataSingleRequestSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String objectName = requiredEnv("SELECTAI_SYNTHETIC_OBJECT_NAME");

        try {
            Profile profile = createSelectAI().profile(profileName);
            SyntheticDataSingleRequest request = buildRequest(objectName);
            boolean generated = profile.generateSyntheticData(request);

            System.out.printf("Generate synthetic data for '%s' completed: %s%n", objectName, generated);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to generate synthetic data for object '{}'.", objectName, e);
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

    private static SyntheticDataSingleRequest buildRequest(String objectName) {
        SyntheticDataSingleRequest.Builder builder = SyntheticDataSingleRequest.builder(objectName)
                .params(buildSyntheticDataParams());

        String ownerName = optionalEnv("SELECTAI_SYNTHETIC_OWNER_NAME");
        if (ownerName != null) {
            builder.ownerName(ownerName);
        }
        Integer recordCount = optionalInteger("SELECTAI_SYNTHETIC_RECORD_COUNT");
        if (recordCount != null) {
            builder.recordCount(recordCount);
        }
        String userPrompt = optionalEnv("SELECTAI_SYNTHETIC_USER_PROMPT");
        if (userPrompt != null) {
            builder.userPrompt(userPrompt);
        }
        return builder.build();
    }

    private static SyntheticDataParams buildSyntheticDataParams() {
        SyntheticDataParams.Builder builder = SyntheticDataParams.builder();
        boolean configured = false;

        Integer sampleRows = optionalInteger("SELECTAI_SYNTHETIC_SAMPLE_ROWS");
        if (sampleRows != null) {
            builder.sampleRows(sampleRows);
            configured = true;
        }
        Boolean tableStatistics = optionalBoolean("SELECTAI_SYNTHETIC_TABLE_STATISTICS");
        if (tableStatistics != null) {
            builder.tableStatistics(tableStatistics);
            configured = true;
        }
        String priority = optionalEnv("SELECTAI_SYNTHETIC_PRIORITY");
        if (priority != null) {
            builder.priority(priority);
            configured = true;
        }
        Boolean comments = optionalBoolean("SELECTAI_SYNTHETIC_COMMENTS");
        if (comments != null) {
            builder.comments(comments);
            configured = true;
        }
        return configured ? builder.build() : null;
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

    private static Integer optionalInteger(String name) {
        String value = optionalEnv(name);
        return value == null ? null : Integer.valueOf(value);
    }

    private static Boolean optionalBoolean(String name) {
        String value = optionalEnv(name);
        return value == null ? null : Boolean.valueOf(value);
    }
}
