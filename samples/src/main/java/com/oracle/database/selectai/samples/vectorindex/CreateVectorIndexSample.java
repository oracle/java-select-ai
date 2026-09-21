/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.vectorindex;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for creating a Select AI vector index.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a
 * {@link VectorIndexConfig} payload, obtain a {@link VectorIndex} object from
 * {@link SelectAI#vectorIndex(VectorIndexConfig)}, call
 * {@link VectorIndex#create()}, and print the result.</p>
 *
 * <p>This sample changes database state by creating the vector index identified
 * by {@code SELECTAI_VECTOR_INDEX_NAME}. Verify the index name before running
 * it.</p>
 *
 * <p>To run this sample, provide an object-storage location through
 * {@code SELECTAI_VECTOR_INDEX_LOCATION}. The location must be readable by the
 * credential configured for the vector index and should reference documents
 * suitable for embedding. When using Oracle Object Storage, ensure the
 * credential, bucket or object URI, network access, and compartment/provider
 * settings are configured before running the sample.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_VECTOR_INDEX_NAME</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 *   <li>SELECTAI_VECTOR_INDEX_LOCATION</li>
 *   <li>SELECTAI_CREDENTIAL_NAME</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_VECTOR_INDEX_DESCRIPTION - vector index description.</li>
 *   <li>SELECTAI_VECTOR_INDEX_STATUS - {@code Enabled} or {@code Disabled}.
 *       Defaults to {@code Disabled}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_WAIT_FOR_COMPLETION - defaults to {@code false}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_CHUNK_SIZE - defaults to {@code 1024}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_CHUNK_OVERLAP - defaults to {@code 128}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_MATCH_LIMIT - defaults to {@code 5}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_REFRESH_RATE - defaults to {@code 1440}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_ENABLE_SOURCES - enables or disables source
 *       citations when supported by the database.</li>
 *   <li>SELECTAI_VECTOR_INDEX_SIMILARITY_THRESHOLD - defaults to {@code 0}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_DISTANCE_METRIC - defaults to {@code COSINE}.</li>
 *   <li>SELECTAI_VECTOR_DB_PROVIDER - defaults to {@code oracle}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_DIMENSION</li>
 *   <li>SELECTAI_VECTOR_INDEX_TABLE_NAME</li>
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
 * javac --release 17 -cp "target/select-ai-1.0.0.jar:target/dependency/*" \
 *   -d samples/out \
 *   samples/src/main/java/com/oracle/database/selectai/samples/vectorindex/CreateVectorIndexSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.vectorindex.CreateVectorIndexSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CreateVectorIndexSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateVectorIndexSample.class);

    private CreateVectorIndexSample() {
    }

    public static void main(String[] args) {
        String indexName = requiredEnv("SELECTAI_VECTOR_INDEX_NAME");

        try {
            SelectAI selectAI = createSelectAI();
            VectorIndexConfig vectorIndexConfig = buildVectorIndexConfig(indexName);
            VectorIndex vectorIndex = selectAI.vectorIndex(vectorIndexConfig);
            boolean created = vectorIndex.create();

            System.out.printf("Create vector index '%s' completed: %s%n", indexName, created);
            System.out.printf("Description         : %s%n", valueOrEmpty(vectorIndex.getDescription()));
            System.out.printf("Configured status   : %s%n", valueOrEmpty(vectorIndex.getStatus()));
            System.out.printf("Wait for completion : %s%n", valueOrEmpty(vectorIndex.isWaitForCompletion()));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to create Select AI vector index '{}'.", indexName, e);
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

    private static VectorIndexConfig buildVectorIndexConfig(String indexName) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        VectorIndexAttributes attributes = buildVectorIndexAttributes(profileName);

        return VectorIndexConfig.builder(indexName)
                .description(envOrDefault("SELECTAI_VECTOR_INDEX_DESCRIPTION",
                        "Vector index created by the Java Select AI SDK sample."))
                .status(envOrDefault("SELECTAI_VECTOR_INDEX_STATUS", "Disabled"))
                .waitForCompletion(Boolean.parseBoolean(
                        envOrDefault("SELECTAI_VECTOR_INDEX_WAIT_FOR_COMPLETION", "false")))
                .vectorIndexAttributes(attributes)
                .build();
    }

    private static VectorIndexAttributes buildVectorIndexAttributes(String profileName) {
        VectorIndexAttributes.Builder builder = VectorIndexAttributes.builder()
                .location(requiredEnv("SELECTAI_VECTOR_INDEX_LOCATION"))
                .objectStorageCredentialName(requiredEnv("SELECTAI_CREDENTIAL_NAME"))
                .profileName(profileName)
                .chunkSize(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_CHUNK_SIZE", "1024")))
                .chunkOverlap(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_CHUNK_OVERLAP", "128")))
                .matchLimit(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_MATCH_LIMIT", "5")))
                .refreshRate(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_REFRESH_RATE", "1440")))
                .similarityThreshold(Double.valueOf(
                        envOrDefault("SELECTAI_VECTOR_INDEX_SIMILARITY_THRESHOLD", "0")))
                .vectorDistanceMetric(envOrDefault("SELECTAI_VECTOR_INDEX_DISTANCE_METRIC", "COSINE"))
                .vectorDbProvider(envOrDefault("SELECTAI_VECTOR_DB_PROVIDER", "oracle"));

        applyOptionalBoolean(builder::enableSources, "SELECTAI_VECTOR_INDEX_ENABLE_SOURCES");
        applyOptionalInteger(builder::vectorDimension, "SELECTAI_VECTOR_INDEX_DIMENSION");
        applyOptionalString(builder::vectorTableName, "SELECTAI_VECTOR_INDEX_TABLE_NAME");

        return builder.build();
    }

    private static void applyOptionalString(StringAttributeSetter setter, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            setter.set(value);
        }
    }

    private static void applyOptionalInteger(IntegerAttributeSetter setter, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            setter.set(Integer.valueOf(value));
        }
    }

    private static void applyOptionalBoolean(BooleanAttributeSetter setter, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            setter.set(Boolean.valueOf(value));
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @FunctionalInterface
    private interface StringAttributeSetter {
        void set(String value);
    }

    @FunctionalInterface
    private interface IntegerAttributeSetter {
        void set(Integer value);
    }

    @FunctionalInterface
    private interface BooleanAttributeSetter {
        void set(Boolean value);
    }
}
