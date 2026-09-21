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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Sample for granting HTTP access to Select AI users.
 *
 * <p>This sample demonstrates how to create a {@link DatabaseAdmin} client and call
 * {@link DatabaseAdmin#grantHttpAccess(List, String)}.</p>
 *
 * <p>This sample changes database state by appending HTTP network ACL entries
 * for the supplied users. It does not grant package privileges; use
 * {@code GrantPrivilegesSample} when package privileges are required.</p>
 *
 * <p>This sample requires a database user authorized to perform administrative
 * setup operations such as package privilege changes, data-access
 * configuration, or network ACL updates. Do not run administrative samples with
 * normal application runtime credentials.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROVIDER_USERS - comma-separated database users</li>
 *   <li>SELECTAI_PROVIDER_ENDPOINT</li>
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
 * <p>Compile and run this sample:</p>
 * <pre>{@code
 * javac --release 17 -cp "target/select-ai-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/databaseadmin/GrantHttpAccessSample.java
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.databaseadmin.GrantHttpAccessSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class GrantHttpAccessSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrantHttpAccessSample.class);

    private GrantHttpAccessSample() {
    }

    public static void main(String[] args) {
        List<String> users = parseUsers(requiredEnv("SELECTAI_PROVIDER_USERS"));
        String providerEndpoint = requiredEnv("SELECTAI_PROVIDER_ENDPOINT");

        try (DatabaseAdmin databaseAdmin = createDatabaseAdmin()) {
            boolean enabled = databaseAdmin.grantHttpAccess(users, providerEndpoint);

            System.out.printf("Grant HTTP access completed: %s%n", enabled);
            System.out.printf("Users: %s%n", users);
            System.out.printf("Provider endpoint: %s%n", providerEndpoint);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to grant Select AI HTTP access for users {}.", users, e);
        }
    }

    private static DatabaseAdmin createDatabaseAdmin() throws SelectAIException {
        DbConnectionConfig dbConnectionConfig = DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();

        return DatabaseAdmin.create(dbConnectionConfig);
    }

    private static List<String> parseUsers(String value) {
        List<String> users = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(user -> !user.isEmpty())
                .toList();
        if (users.isEmpty()) {
            throw new IllegalStateException("SELECTAI_PROVIDER_USERS must contain at least one user");
        }
        return users;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
