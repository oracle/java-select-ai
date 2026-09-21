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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for updating Select AI vector-index attributes in bulk.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a
 * {@link VectorIndex} object for an existing vector index, build a
 * {@link VectorIndexAttributes} payload, call
 * {@link VectorIndex#update(VectorIndexAttributes)}, and print whether the
 * update succeeded.</p>
 *
 * <p>This sample changes database state by updating the vector index identified
 * by {@code SELECTAI_VECTOR_INDEX_NAME}. Verify the vector index name and
 * attributes before running it. Attribute mutability is determined by
 * {@code DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX}; if the database rejects an
 * attribute, this sample logs the resulting {@link SelectAIException}.</p>
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
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_VECTOR_INDEX_MATCH_LIMIT - defaults to {@code 5}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_REFRESH_RATE - defaults to {@code 1440}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_ENABLE_SOURCES - enables or disables source
 *       citations when supported by the database.</li>
 *   <li>SELECTAI_VECTOR_INDEX_SIMILARITY_THRESHOLD - defaults to {@code 0}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/vectorindex/UpdateVectorIndexAttributesSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.vectorindex.UpdateVectorIndexAttributesSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class UpdateVectorIndexAttributesSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateVectorIndexAttributesSample.class);

    private UpdateVectorIndexAttributesSample() {
    }

    public static void main(String[] args) {
        String indexName = requiredEnv("SELECTAI_VECTOR_INDEX_NAME");

        try {
            SelectAI selectAI = createSelectAI();
            VectorIndex vectorIndex = selectAI.vectorIndex(indexName);
            VectorIndexAttributes attributes = buildVectorIndexAttributes();
            boolean updated = vectorIndex.update(attributes);

            System.out.printf("Update vector index '%s' attributes completed: %s%n", indexName, updated);
            System.out.printf("Attribute payload: %s%n", attributes.toJson());
        } catch (SelectAIException e) {
            LOGGER.error("Failed to update Select AI vector index '{}'.", indexName, e);
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

    private static VectorIndexAttributes buildVectorIndexAttributes() {
        VectorIndexAttributes.Builder builder = VectorIndexAttributes.updateBuilder()
                .matchLimit(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_MATCH_LIMIT", "5")))
                .refreshRate(Integer.valueOf(envOrDefault("SELECTAI_VECTOR_INDEX_REFRESH_RATE", "1440")))
                .similarityThreshold(Double.valueOf(
                        envOrDefault("SELECTAI_VECTOR_INDEX_SIMILARITY_THRESHOLD", "0")));

        applyOptionalBoolean(builder::enableSources, "SELECTAI_VECTOR_INDEX_ENABLE_SOURCES");

        return builder.build();
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

    @FunctionalInterface
    private interface BooleanAttributeSetter {
        void set(Boolean value);
    }
}
