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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Sample for listing Select AI vector indexes.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, call
 * {@link SelectAI#listVectorIndexes(String)}, and print vector-index metadata
 * returned by the SDK.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_VECTOR_INDEX_NAME_PATTERN - regular-expression pattern used to
 *       filter vector index names. Defaults to {@code .*}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/vectorindex/ListVectorIndexesSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.vectorindex.ListVectorIndexesSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ListVectorIndexesSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListVectorIndexesSample.class);

    private ListVectorIndexesSample() {
    }

    public static void main(String[] args) {
        try {
            String indexNamePattern = envOrDefault("SELECTAI_VECTOR_INDEX_NAME_PATTERN", ".*");
            SelectAI selectAI = createSelectAI();
            List<VectorIndex> vectorIndexes = selectAI.listVectorIndexes(indexNamePattern);

            if (vectorIndexes.isEmpty()) {
                System.out.printf("No Select AI vector indexes found for pattern '%s'.%n", indexNamePattern);
                return;
            }

            System.out.printf("Found %d Select AI vector index(es) for pattern '%s':%n",
                    vectorIndexes.size(), indexNamePattern);
            for (VectorIndex vectorIndex : vectorIndexes) {
                System.out.println("----------------------------------------");
                System.out.printf("Index name    : %s%n", valueOrEmpty(vectorIndex.getIndexName()));
                System.out.printf("Description   : %s%n", valueOrEmpty(vectorIndex.getDescription()));
                System.out.printf("Status        : %s%n", valueOrEmpty(vectorIndex.getStatus()));
            }
        } catch (SelectAIException e) {
            LOGGER.error("Failed to list Select AI vector indexes.", e);
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
}
