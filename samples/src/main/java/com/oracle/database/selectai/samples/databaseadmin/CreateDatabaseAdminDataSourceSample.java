/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.databaseadmin;

import com.oracle.database.selectai.DatabaseAdmin;
import oracle.jdbc.pool.OracleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Sample for creating a DataSource-backed DatabaseAdmin client.
 *
 * <p>This sample demonstrates how to configure an Oracle JDBC {@link DataSource},
 * create a DataSource-backed {@link DatabaseAdmin} client, and close it after
 * use. Administrative methods on {@code DatabaseAdmin} require a database user
 * authorized to perform the requested setup operation.</p>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/databaseadmin/CreateDatabaseAdminDataSourceSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.databaseadmin.CreateDatabaseAdminDataSourceSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CreateDatabaseAdminDataSourceSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateDatabaseAdminDataSourceSample.class);

    private CreateDatabaseAdminDataSourceSample() {
    }

    public static void main(String[] args) {
        try (DatabaseAdmin ignored = DatabaseAdmin.create(dataSource())) {
            System.out.println("Created DataSource-backed DatabaseAdmin client.");
        } catch (Exception e) {
            LOGGER.error("Failed to create DataSource-backed DatabaseAdmin client.", e);
        }
    }

    static DataSource dataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(CreateDatabaseAdminWithOptionsSample.requiredEnv("SELECTAI_JDBC_URL"));
        dataSource.setUser(CreateDatabaseAdminWithOptionsSample.requiredEnv("SELECTAI_DB_USER"));
        dataSource.setPassword(CreateDatabaseAdminWithOptionsSample.requiredEnv("SELECTAI_DB_PASSWORD"));
        return dataSource;
    }
}
