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
import com.oracle.database.selectai.model.SyntheticDataBatchRequest;
import com.oracle.database.selectai.model.SyntheticDataObjectList;
import com.oracle.database.selectai.model.SyntheticDataParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for generating synthetic data with a batch request.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, open a
 * {@link Profile}, build a {@link SyntheticDataBatchRequest}, and call
 * {@link Profile#generateSyntheticData(SyntheticDataBatchRequest)}.</p>
 *
 * <p>This sample changes database state by generating synthetic data for one or
 * more requested database objects.</p>
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
 *   <li>SELECTAI_SYNTHETIC_OWNER_NAME - defaults to {@code SELECTAI_DB_USER}.</li>
 *   <li>SELECTAI_SYNTHETIC_RECORD_COUNT</li>
 *   <li>SELECTAI_SYNTHETIC_USER_PROMPT</li>
 *   <li>SELECTAI_SYNTHETIC_OBJECT_NAME_2</li>
 *   <li>SELECTAI_SYNTHETIC_OWNER_NAME_2</li>
 *   <li>SELECTAI_SYNTHETIC_RECORD_COUNT_2</li>
 *   <li>SELECTAI_SYNTHETIC_USER_PROMPT_2</li>
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
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/profile/GenerateSyntheticDataBatchRequestSample.java
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.GenerateSyntheticDataBatchRequestSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class GenerateSyntheticDataBatchRequestSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateSyntheticDataBatchRequestSample.class);

    private GenerateSyntheticDataBatchRequestSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String objectName = requiredEnv("SELECTAI_SYNTHETIC_OBJECT_NAME");

        try {
            Profile profile = createSelectAI().profile(profileName);
            SyntheticDataBatchRequest request = buildRequest(objectName);
            boolean generated = profile.generateSyntheticData(request);

            System.out.printf("Generate synthetic data batch completed: %s%n", generated);
            System.out.printf("Object list: %s%n", request.getObjectListJson());
        } catch (SelectAIException e) {
            LOGGER.error("Failed to generate synthetic data batch for first object '{}'.", objectName, e);
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

    private static SyntheticDataBatchRequest buildRequest(String objectName) {
        String ownerName = envOrDefault("SELECTAI_SYNTHETIC_OWNER_NAME", requiredEnv("SELECTAI_DB_USER"));
        SyntheticDataBatchRequest.Builder builder = SyntheticDataBatchRequest.builder()
                .addObject(buildObject(ownerName, objectName,
                        optionalInteger("SELECTAI_SYNTHETIC_RECORD_COUNT"),
                        optionalEnv("SELECTAI_SYNTHETIC_USER_PROMPT")))
                .params(buildSyntheticDataParams());

        String secondObjectName = optionalEnv("SELECTAI_SYNTHETIC_OBJECT_NAME_2");
        if (secondObjectName != null) {
            builder.addObject(buildObject(
                    envOrDefault("SELECTAI_SYNTHETIC_OWNER_NAME_2", ownerName),
                    secondObjectName,
                    optionalInteger("SELECTAI_SYNTHETIC_RECORD_COUNT_2"),
                    optionalEnv("SELECTAI_SYNTHETIC_USER_PROMPT_2")));
        }

        return builder.build();
    }

    private static SyntheticDataObjectList buildObject(String ownerName,
                                                       String objectName,
                                                       Integer recordCount,
                                                       String userPrompt) {
        SyntheticDataObjectList.Builder builder = SyntheticDataObjectList.builder(ownerName, objectName);
        if (recordCount != null) {
            builder.recordCount(recordCount);
        }
        if (userPrompt != null && !userPrompt.isBlank()) {
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

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
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
