/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.profile;

import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for setting a floating-point Select AI profile attribute.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a {@link Profile} object, call the {@link Profile#setAttribute(String, Float)} overload, and print whether the attribute update succeeded.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 *   <li>SELECTAI_PROFILE_ATTRIBUTE_NAME</li>
 *   <li>SELECTAI_PROFILE_ATTRIBUTE_VALUE</li>
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
 * javac --release 17 -cp "target/select-ai-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/profile/SetProfileFloatAttributeSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.SetProfileFloatAttributeSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class SetProfileFloatAttributeSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetProfileFloatAttributeSample.class);

    private SetProfileFloatAttributeSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String attributeName = requiredEnv("SELECTAI_PROFILE_ATTRIBUTE_NAME");
        Float attributeValue = Float.valueOf(requiredEnv("SELECTAI_PROFILE_ATTRIBUTE_VALUE"));

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            boolean updated = profile.setAttribute(attributeName, attributeValue);

            System.out.printf("Set attribute '%s' on profile '%s' completed: %s%n",
                    attributeName, profileName, updated);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to set attribute '{}' for Select AI profile '{}'.",
                    attributeName, profileName, e);
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

}
