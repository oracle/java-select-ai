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
 * Sample for translating text with a Select AI profile.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, obtain a {@link Profile} object, call the translate methods, and print the translated response.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 *   <li>SELECTAI_TRANSLATE_TEXT</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_TRANSLATE_SOURCE_LANGUAGE</li>
 *   <li>SELECTAI_TRANSLATE_TARGET_LANGUAGE</li>
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
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/profile/TranslateProfileSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.TranslateProfileSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class TranslateProfileSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslateProfileSample.class);

    private TranslateProfileSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String text = requiredEnv("SELECTAI_TRANSLATE_TEXT");
        String sourceLanguage = optionalEnv("SELECTAI_TRANSLATE_SOURCE_LANGUAGE");
        String targetLanguage = optionalEnv("SELECTAI_TRANSLATE_TARGET_LANGUAGE");

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            String translated = translate(profile, text, sourceLanguage, targetLanguage);

            System.out.printf("Translated response:%n%s%n", valueOrEmpty(translated));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to translate text for Select AI profile '{}'.", profileName, e);
        }
    }

    private static String translate(Profile profile,
                                    String text,
                                    String sourceLanguage,
                                    String targetLanguage) throws SelectAIException {
        if (sourceLanguage == null && targetLanguage == null) {
            return profile.translate(text);
        }
        if (sourceLanguage == null) {
            return profile.translate(text, targetLanguage);
        }
        return profile.translate(text, sourceLanguage, targetLanguage);
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

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

}
