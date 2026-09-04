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
 * Sample for reading vector-index attributes from database metadata.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, open an
 * existing {@link VectorIndex} by name, call
 * {@link VectorIndex#getVectorIndexAttributes()}, and print the loaded
 * attributes.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_VECTOR_INDEX_NAME</li>
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
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   -d samples/out \
 *   samples/src/main/java/com/oracle/database/selectai/samples/vectorindex/GetVectorIndexAttributesSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.vectorindex.GetVectorIndexAttributesSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class GetVectorIndexAttributesSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetVectorIndexAttributesSample.class);

    private GetVectorIndexAttributesSample() {
    }

    public static void main(String[] args) {
        String indexName = requiredEnv("SELECTAI_VECTOR_INDEX_NAME");

        try {
            SelectAI selectAI = createSelectAI();
            VectorIndex vectorIndex = selectAI.vectorIndex(indexName);
            VectorIndexAttributes attributes = vectorIndex.getVectorIndexAttributes();

            System.out.printf("Vector index attributes for %s:%n", vectorIndex.getIndexName());
            if (attributes == null) {
                System.out.println("No vector index attributes found.");
                return;
            }
            printAttributes(attributes);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to read attributes for Select AI vector index '{}'.", indexName, e);
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

    private static void printAttributes(VectorIndexAttributes attributes) {
        System.out.printf("chunkSize: %s%n", valueOrEmpty(attributes.getChunkSize()));
        System.out.printf("chunkOverlap: %s%n", valueOrEmpty(attributes.getChunkOverlap()));
        System.out.printf("enableSources: %s%n", valueOrEmpty(attributes.getEnableSources()));
        System.out.printf("location: %s%n", valueOrEmpty(attributes.getLocation()));
        System.out.printf("matchLimit: %s%n", valueOrEmpty(attributes.getMatchLimit()));
        System.out.printf("objectStorageCredentialName: %s%n",
                valueOrEmpty(attributes.getObjectStorageCredentialName()));
        System.out.printf("pipelineName: %s%n", valueOrEmpty(attributes.getPipelineName()));
        System.out.printf("profileName: %s%n", valueOrEmpty(attributes.getProfileName()));
        System.out.printf("refreshRate: %s%n", valueOrEmpty(attributes.getRefreshRate()));
        System.out.printf("similarityThreshold: %s%n", valueOrEmpty(attributes.getSimilarityThreshold()));
        System.out.printf("vectorDistanceMetric: %s%n", valueOrEmpty(attributes.getVectorDistanceMetric()));
        System.out.printf("vectorDbProvider: %s%n", valueOrEmpty(attributes.getVectorDbProvider()));
        System.out.printf("vectorDimension: %s%n", valueOrEmpty(attributes.getVectorDimension()));
        System.out.printf("vectorTableName: %s%n", valueOrEmpty(attributes.getVectorTableName()));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
