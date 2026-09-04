/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.selectai;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for configuring a Select AI vector-index object through {@link SelectAI}.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a
 * {@link VectorIndexConfig}, and call {@link SelectAI#vectorIndex(VectorIndexConfig)}.
 * It does not call {@link VectorIndex#create()}.</p>
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
 *   <li>SELECTAI_VECTOR_INDEX_DESCRIPTION</li>
 *   <li>SELECTAI_VECTOR_INDEX_STATUS - defaults to {@code Disabled}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_WAIT_FOR_COMPLETION - defaults to {@code false}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_CHUNK_SIZE - defaults to {@code 1024}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_CHUNK_OVERLAP - defaults to {@code 128}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_MATCH_LIMIT - defaults to {@code 5}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_REFRESH_RATE - defaults to {@code 1440}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_ENABLE_SOURCES - defaults to {@code true}.</li>
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
 * <p>Compile and run this sample:</p>
 * <pre>{@code
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/selectai/ConfigureVectorIndexSample.java
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.selectai.ConfigureVectorIndexSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ConfigureVectorIndexSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigureVectorIndexSample.class);

    private ConfigureVectorIndexSample() {
    }

    public static void main(String[] args) {
        String indexName = requiredEnv("SELECTAI_VECTOR_INDEX_NAME");

        try {
            SelectAI selectAI = createSelectAI();
            VectorIndex vectorIndex = selectAI.vectorIndex(buildVectorIndexConfig(indexName));

            System.out.printf("Configured vector index: %s%n", valueOrEmpty(vectorIndex.getIndexName()));
            System.out.printf("Description          : %s%n", valueOrEmpty(vectorIndex.getDescription()));
            System.out.printf("Status               : %s%n", valueOrEmpty(vectorIndex.getStatus()));
            System.out.printf("Wait for completion  : %s%n", valueOrEmpty(vectorIndex.isWaitForCompletion()));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to configure Select AI vector index '{}'.", indexName, e);
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
        VectorIndexAttributes.Builder attributes = VectorIndexAttributes.builder()
                .location(requiredEnv("SELECTAI_VECTOR_INDEX_LOCATION"))
                .objectStorageCredentialName(requiredEnv("SELECTAI_CREDENTIAL_NAME"))
                .profileName(profileName)
                .chunkSize(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_CHUNK_SIZE", "1024")))
                .chunkOverlap(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_CHUNK_OVERLAP", "128")))
                .matchLimit(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_MATCH_LIMIT", "5")))
                .refreshRate(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_REFRESH_RATE", "1440")))
                .enableSources(Boolean.parseBoolean(envOrDefault("SELECTAI_VECTOR_INDEX_ENABLE_SOURCES", "true")))
                .similarityThreshold(Double.valueOf(
                        envOrDefault("SELECTAI_VECTOR_INDEX_SIMILARITY_THRESHOLD", "0")))
                .vectorDistanceMetric(envOrDefault("SELECTAI_VECTOR_INDEX_DISTANCE_METRIC", "COSINE"))
                .vectorDbProvider(envOrDefault("SELECTAI_VECTOR_DB_PROVIDER", "oracle"));

        applyOptionalInteger(attributes::vectorDimension, "SELECTAI_VECTOR_INDEX_DIMENSION");
        applyOptionalString(attributes::vectorTableName, "SELECTAI_VECTOR_INDEX_TABLE_NAME");

        return VectorIndexConfig.builder(indexName)
                .description(envOrDefault("SELECTAI_VECTOR_INDEX_DESCRIPTION",
                        "Vector index configured by the Java Select AI SDK sample."))
                .status(envOrDefault("SELECTAI_VECTOR_INDEX_STATUS", "Disabled"))
                .waitForCompletion(Boolean.parseBoolean(
                        envOrDefault("SELECTAI_VECTOR_INDEX_WAIT_FOR_COMPLETION", "false")))
                .vectorIndexAttributes(attributes.build())
                .build();
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
}
