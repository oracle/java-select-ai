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
import com.oracle.database.selectai.model.ProfileAttributes;

/**
 * Sample for setting multiple Select AI profile attributes.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a {@link ProfileAttributes} payload, call {@link Profile#setAttributes(ProfileAttributes)}, and print whether the bulk update succeeded.</p>
 *
 * <p>To run this sample with an object list that references the Oracle
 * sample schema objects {@code SH.CUSTOMERS} and {@code SH.COUNTRIES}, ensure
 * the {@code SH} schema is installed and visible to {@code SELECTAI_DB_USER}.
 * The {@code SH} schema is not guaranteed to exist in every Oracle Autonomous
 * AI Database or Oracle Database instance. If those objects are not available,
 * set {@code SELECTAI_PROFILE_OBJECT_LIST} to objects visible to
 * {@code SELECTAI_DB_USER}, or omit the object list and rely on the configured
 * object-list mode.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_PROFILE_COMMENTS - defaults to {@code true}.</li>
 *   <li>SELECTAI_PROFILE_CONSTRAINTS - defaults to {@code true}.</li>
 *   <li>SELECTAI_PROFILE_CASE_SENSITIVE_VALUES - defaults to {@code false}.</li>
 *   <li>SELECTAI_PROFILE_MAX_TOKENS - defaults to {@code 1024}.</li>
 *   <li>SELECTAI_PROFILE_ADDITIONAL_INSTRUCTIONS</li>
 *   <li>SELECTAI_PROFILE_ROLE</li>
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
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/profile/SetProfileAttributesSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.SetProfileAttributesSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class SetProfileAttributesSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetProfileAttributesSample.class);

    private SetProfileAttributesSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        ProfileAttributes.Builder builder = ProfileAttributes.builder()
                .comments(Boolean.parseBoolean(envOrDefault("SELECTAI_PROFILE_COMMENTS", "true")))
                .constraints(Boolean.parseBoolean(envOrDefault("SELECTAI_PROFILE_CONSTRAINTS", "true")))
                .caseSensitiveValues(Boolean.parseBoolean(envOrDefault("SELECTAI_PROFILE_CASE_SENSITIVE_VALUES", "false")))
                .maxTokens(Integer.valueOf(envOrDefault("SELECTAI_PROFILE_MAX_TOKENS", "1024")));
        applyOptionalString(builder::additionalInstructions, "SELECTAI_PROFILE_ADDITIONAL_INSTRUCTIONS");
        applyOptionalString(builder::role, "SELECTAI_PROFILE_ROLE");
        ProfileAttributes profileAttributes = builder.build();

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            boolean updated = profile.setAttributes(profileAttributes);

            System.out.printf("Set attributes on profile '%s' completed: %s%n", profileName, updated);
            System.out.printf("Attribute payload: %s%n", profileAttributes.toJson());
        } catch (SelectAIException e) {
            LOGGER.error("Failed to set attributes for Select AI profile '{}'.", profileName, e);
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

    private static void applyOptionalString(StringAttributeSetter setter, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            setter.set(value);
        }
    }

    @FunctionalInterface
    private interface StringAttributeSetter {
        void set(String value);
    }

}
