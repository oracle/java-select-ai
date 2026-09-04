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
 * Sample for revoking network access from Select AI users.
 *
 * <p>This sample demonstrates how to create a {@link DatabaseAdmin} client and call
 * {@link DatabaseAdmin#revokeNetworkAccess(List, String, List, Integer, Integer)}.</p>
 *
 * <p>This sample changes database state by removing network ACL entries for
 * the supplied users, host, privileges, and optional port range. It does not
 * revoke package privileges; use {@code RevokePrivilegesSample} when package
 * privileges should be revoked. Use {@code RevokeHttpAccessSample} to exercise
 * the separate {@link DatabaseAdmin#revokeHttpAccess(List, String)} public API.</p>
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
 *   <li>SELECTAI_NETWORK_USERS - comma-separated database users</li>
 *   <li>SELECTAI_NETWORK_HOST</li>
 *   <li>SELECTAI_NETWORK_PRIVILEGES - comma-separated ACL privileges, for example {@code http,connect}</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_NETWORK_LOWER_PORT</li>
 *   <li>SELECTAI_NETWORK_UPPER_PORT</li>
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
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/databaseadmin/RevokeNetworkAccessSample.java
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.databaseadmin.RevokeNetworkAccessSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class RevokeNetworkAccessSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(RevokeNetworkAccessSample.class);

    private RevokeNetworkAccessSample() {
    }

    public static void main(String[] args) {
        List<String> users = parseList(requiredEnv("SELECTAI_NETWORK_USERS"), "SELECTAI_NETWORK_USERS");
        String host = requiredEnv("SELECTAI_NETWORK_HOST");
        List<String> privileges = parseList(requiredEnv("SELECTAI_NETWORK_PRIVILEGES"),
                "SELECTAI_NETWORK_PRIVILEGES");
        Integer lowerPort = optionalIntegerEnv("SELECTAI_NETWORK_LOWER_PORT");
        Integer upperPort = optionalIntegerEnv("SELECTAI_NETWORK_UPPER_PORT");

        try (DatabaseAdmin databaseAdmin = createDatabaseAdmin()) {
            boolean disabled = databaseAdmin.revokeNetworkAccess(users, host, privileges, lowerPort, upperPort);

            System.out.printf("Revoke network access completed: %s%n", disabled);
            System.out.printf("Users: %s%n", users);
            System.out.printf("Host: %s%n", host);
            System.out.printf("Privileges: %s%n", privileges);
            System.out.printf("Lower port: %s%n", valueOrEmpty(lowerPort));
            System.out.printf("Upper port: %s%n", valueOrEmpty(upperPort));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to revoke Select AI network access for users {}.", users, e);
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

    private static List<String> parseList(String value, String envName) {
        List<String> values = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        if (values.isEmpty()) {
            throw new IllegalStateException(envName + " must contain at least one value");
        }
        return values;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static Integer optionalIntegerEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    private static String valueOrEmpty(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}
