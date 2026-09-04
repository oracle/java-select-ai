/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.databaseadmin;

import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for closing an SDK-owned DatabaseAdmin client.
 *
 * <p>This sample demonstrates how to create a {@link DatabaseAdmin} client and
 * close the SDK-owned JDBC connection deterministically by using
 * try-with-resources. Administrative methods on {@code DatabaseAdmin} require a
 * database user authorized to perform the requested setup operation.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/databaseadmin/CloseDatabaseAdminSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.databaseadmin.CloseDatabaseAdminSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CloseDatabaseAdminSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CloseDatabaseAdminSample.class);

    private CloseDatabaseAdminSample() {
    }

    public static void main(String[] args) {
        try (DatabaseAdmin ignored = DatabaseAdmin.create(CreateDatabaseAdminWithOptionsSample.dbConnectionConfig())) {
            System.out.println("DatabaseAdmin client created; try-with-resources will close it.");
        } catch (SelectAIException e) {
            LOGGER.error("Failed to create or close DatabaseAdmin client.", e);
        }
    }
}
