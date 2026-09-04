/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.databaseadmin;

import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for creating a DatabaseAdmin client with SDK execution options.
 *
 * <p>This sample demonstrates how to build connection configuration, build
 * {@link SelectAIOptions}, create an SDK-owned {@link DatabaseAdmin} client,
 * and close the client deterministically by using try-with-resources.
 * Administrative methods on {@code DatabaseAdmin} require a database user
 * authorized to perform the requested setup operation.</p>
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
 *   <li>SELECTAI_QUERY_TIMEOUT_SECONDS - query timeout applied to SDK JDBC statements.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/databaseadmin/CreateDatabaseAdminWithOptionsSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.databaseadmin.CreateDatabaseAdminWithOptionsSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CreateDatabaseAdminWithOptionsSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateDatabaseAdminWithOptionsSample.class);

    private CreateDatabaseAdminWithOptionsSample() {
    }

    public static void main(String[] args) {
        SelectAIOptions options = options();
        try (DatabaseAdmin ignored = DatabaseAdmin.create(dbConnectionConfig(), options)) {
            System.out.printf("Created DatabaseAdmin client with query timeout: %s%n",
                    valueOrDefault(options.getQueryTimeoutSeconds(), "not configured"));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to create DatabaseAdmin client with options.", e);
        }
    }

    static DbConnectionConfig dbConnectionConfig() {
        return DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();
    }

    static SelectAIOptions options() {
        String timeout = optionalEnv("SELECTAI_QUERY_TIMEOUT_SECONDS");
        return SelectAIOptions.builder()
                .queryTimeoutSeconds(timeout == null ? null : Integer.valueOf(timeout))
                .build();
    }

    static String requiredEnv(String name) {
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

    static String valueOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }
}
