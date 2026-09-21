/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.datasource;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.model.SelectAIException;
import oracle.jdbc.pool.OracleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

/**
 * Sample for listing Select AI profiles using DataSource-backed connection mode.
 *
 * <p>This sample demonstrates how to create an Oracle JDBC {@link DataSource},
 * pass it to {@link SelectAI#create(DataSource)}, call
 * {@link SelectAI#listProfiles()}, and print profile metadata returned by the
 * SDK.</p>
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
 * javac --release 17 -cp "target/select-ai-1.0.0.jar:target/dependency/*" \
 *   -d samples/out \
 *   samples/src/main/java/com/oracle/database/selectai/samples/datasource/ListProfilesWithDataSourceSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.datasource.ListProfilesWithDataSourceSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ListProfilesWithDataSourceSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListProfilesWithDataSourceSample.class);

    private ListProfilesWithDataSourceSample() {
    }

    public static void main(String[] args) {
        try {
            DataSource dataSource = createDataSource();

            SelectAI selectAI = SelectAI.create(dataSource);

            List<Profile> profiles = selectAI.listProfiles();

            if (profiles.isEmpty()) {
                System.out.println("No Select AI profiles found.");
                return;
            }

            System.out.printf("Found %d Select AI profile(s):%n", profiles.size());
            for (Profile profile : profiles) {
                System.out.println("----------------------------------------");
                System.out.printf("Profile name : %s%n", valueOrEmpty(profile.getProfileName()));
                System.out.printf("Status       : %s%n", valueOrEmpty(profile.getStatus()));
                System.out.printf("Description  : %s%n", valueOrEmpty(profile.getDescription()));
            }
        } catch (SelectAIException e) {
            LOGGER.error("Failed to list Select AI profiles using DataSource mode.", e);
        } catch (SQLException e) {
            LOGGER.error("Failed to configure OracleDataSource.", e);
        }
    }

    private static DataSource createDataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(requiredEnv("SELECTAI_JDBC_URL"));
        dataSource.setUser(requiredEnv("SELECTAI_DB_USER"));
        dataSource.setPassword(requiredEnv("SELECTAI_DB_PASSWORD"));
        return dataSource;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
