/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.selectai;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.SelectAIOptions;
import oracle.jdbc.pool.OracleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Sample for creating a DataSource-backed Select AI client with SDK execution options.
 *
 * <p>This sample demonstrates how to configure an Oracle JDBC {@link DataSource},
 * build {@link SelectAIOptions}, create a DataSource-backed {@link SelectAI}
 * client, and close the client after use.</p>
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
 * javac --release 17 -cp "target/select-ai-1.0.0.jar:target/dependency/*" \
 *   -d samples/out \
 *   samples/src/main/java/com/oracle/database/selectai/samples/selectai/CreateSelectAIDataSourceWithOptionsSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.selectai.CreateSelectAIDataSourceWithOptionsSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CreateSelectAIDataSourceWithOptionsSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateSelectAIDataSourceWithOptionsSample.class);

    private CreateSelectAIDataSourceWithOptionsSample() {
    }

    public static void main(String[] args) {
        try {
            SelectAIOptions options = options();
            SelectAI selectAI = SelectAI.create(dataSource(), options);

            System.out.printf("Created DataSource-backed SelectAI client with query timeout: %s%n",
                    valueOrDefault(options.getQueryTimeoutSeconds(), "not configured"));
            selectAI.close();
        } catch (Exception e) {
            LOGGER.error("Failed to create DataSource-backed SelectAI client with options.", e);
        }
    }

    private static DataSource dataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(requiredEnv("SELECTAI_JDBC_URL"));
        dataSource.setUser(requiredEnv("SELECTAI_DB_USER"));
        dataSource.setPassword(requiredEnv("SELECTAI_DB_PASSWORD"));
        return dataSource;
    }

    private static SelectAIOptions options() {
        String timeout = optionalEnv("SELECTAI_QUERY_TIMEOUT_SECONDS");
        return SelectAIOptions.builder()
                .queryTimeoutSeconds(timeout == null ? null : Integer.valueOf(timeout))
                .build();
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

    private static String valueOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }
}
