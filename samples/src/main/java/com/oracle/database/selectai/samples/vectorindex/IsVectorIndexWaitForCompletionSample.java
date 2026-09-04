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
 * Sample for reading the configured Select AI vector-index wait flag.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, initialize
 * a {@link VectorIndex} object from {@link VectorIndexConfig}, call
 * {@link VectorIndex#isWaitForCompletion()}, and print the configured flag.</p>
 *
 * <p>This sample does not call {@link VectorIndex#create()}. It reads the
 * wait-for-completion value configured on the local vector-index object.</p>
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
 *   <li>SELECTAI_VECTOR_INDEX_WAIT_FOR_COMPLETION - defaults to {@code false}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_STATUS - {@code Enabled} or {@code Disabled}.
 *       Defaults to {@code Disabled}.</li>
 *   <li>SELECTAI_VECTOR_INDEX_DESCRIPTION - vector index description.</li>
 *   <li>SELECTAI_VECTOR_DB_PROVIDER - defaults to {@code oracle}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/vectorindex/IsVectorIndexWaitForCompletionSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.vectorindex.IsVectorIndexWaitForCompletionSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class IsVectorIndexWaitForCompletionSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsVectorIndexWaitForCompletionSample.class);

    private IsVectorIndexWaitForCompletionSample() {
    }

    public static void main(String[] args) {
        String indexName = requiredEnv("SELECTAI_VECTOR_INDEX_NAME");

        try {
            SelectAI selectAI = createSelectAI();
            VectorIndex vectorIndex = selectAI.vectorIndex(buildVectorIndexConfig(indexName));

            System.out.printf("Configured wait for completion: %s%n",
                    valueOrEmpty(vectorIndex.isWaitForCompletion()));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to initialize Select AI vector index '{}'.", indexName, e);
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
        VectorIndexAttributes attributes = VectorIndexAttributes.builder()
                .location(requiredEnv("SELECTAI_VECTOR_INDEX_LOCATION"))
                .objectStorageCredentialName(requiredEnv("SELECTAI_CREDENTIAL_NAME"))
                .profileName(profileName)
                .vectorDbProvider(envOrDefault("SELECTAI_VECTOR_DB_PROVIDER", "oracle"))
                .build();

        return VectorIndexConfig.builder(indexName)
                .description(envOrDefault("SELECTAI_VECTOR_INDEX_DESCRIPTION",
                        "Vector index initialized by the Java Select AI SDK sample."))
                .status(envOrDefault("SELECTAI_VECTOR_INDEX_STATUS", "Disabled"))
                .waitForCompletion(Boolean.parseBoolean(
                        envOrDefault("SELECTAI_VECTOR_INDEX_WAIT_FOR_COMPLETION", "false")))
                .vectorIndexAttributes(attributes)
                .build();
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
