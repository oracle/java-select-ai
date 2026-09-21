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

/**
 * Sample for dropping a Select AI vector index.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a
 * {@link VectorIndex} object for an existing vector index, call
 * {@link VectorIndex#drop(boolean, boolean)}, and print whether the drop
 * operation succeeded.</p>
 *
 * <p>This sample changes database state by dropping the vector index identified
 * by {@code SELECTAI_VECTOR_INDEX_NAME}. Verify the vector index name before
 * running it.</p>
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
 *   <li>SELECTAI_VECTOR_INDEX_DROP_INCLUDE_DATA - set to {@code false} to drop
 *       only vector-index metadata and keep backing vector data. Defaults to
 *       {@code true}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_DROP_FORCE - set to {@code true} to request force
 *       drop. Defaults to {@code false}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/vectorindex/DropVectorIndexSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.vectorindex.DropVectorIndexSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class DropVectorIndexSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(DropVectorIndexSample.class);

    private DropVectorIndexSample() {
    }

    public static void main(String[] args) {
        String indexName = requiredEnv("SELECTAI_VECTOR_INDEX_NAME");
        boolean includeData = Boolean.parseBoolean(
                envOrDefault("SELECTAI_VECTOR_INDEX_DROP_INCLUDE_DATA", "true"));
        boolean force = Boolean.parseBoolean(envOrDefault("SELECTAI_VECTOR_INDEX_DROP_FORCE", "false"));

        try {
            SelectAI selectAI = createSelectAI();
            VectorIndex vectorIndex = selectAI.vectorIndex(indexName);
            boolean dropped = vectorIndex.drop(includeData, force);

            System.out.printf("Drop vector index '%s' completed: %s%n", indexName, dropped);
            System.out.printf("Include backing data : %s%n", includeData);
            System.out.printf("Force               : %s%n", force);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to drop Select AI vector index '{}'.", indexName, e);
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
}
