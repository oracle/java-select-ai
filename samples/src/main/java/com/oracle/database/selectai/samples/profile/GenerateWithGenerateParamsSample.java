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
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Sample for calling {@link Profile#generate(String, GenerateAction, GenerateParams)}.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, open a
 * {@link Profile}, build {@link GenerateParams} with an existing conversation
 * identifier, call {@link Profile#generate(String, GenerateAction, GenerateParams)},
 * and print the generated response.</p>
 *
 * <p>To run this sample with actions and prompts such as {@code showsql} and
 * {@code how many customers}, ensure the profile object list contains tables
 * that match the prompt. For example, if the profile object list references
 * the Oracle sample schema objects {@code SH.CUSTOMERS} and {@code SH.COUNTRIES},
 * ensure the {@code SH} schema is installed and visible to
 * {@code SELECTAI_DB_USER}. The {@code SH} schema is not guaranteed to exist in
 * every Oracle Autonomous AI Database or Oracle Database instance.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 *   <li>SELECTAI_PROFILE_PROMPT</li>
 *   <li>SELECTAI_CONVERSATION_ID</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_PROFILE_GENERATE_ACTION - generation action. Defaults to {@code chat}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/GenerateWithGenerateParamsSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.GenerateWithGenerateParamsSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class GenerateWithGenerateParamsSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateWithGenerateParamsSample.class);

    private GenerateWithGenerateParamsSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String prompt = requiredEnv("SELECTAI_PROFILE_PROMPT");
        String conversationId = requiredEnv("SELECTAI_CONVERSATION_ID");
        GenerateAction action = GenerateAction.valueOf(
                envOrDefault("SELECTAI_PROFILE_GENERATE_ACTION", "chat").trim().toLowerCase(Locale.ROOT));

        try {
            Profile profile = createSelectAI().profile(profileName);
            GenerateParams params = GenerateParams.builder().conversationId(conversationId).build();
            String response = profile.generate(prompt, action, params);

            System.out.printf("Generate action '%s' with params response:%n%s%n", action, valueOrEmpty(response));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to generate response with params for Select AI profile '{}'.", profileName, e);
        }
    }

    private static SelectAI createSelectAI() throws SelectAIException {
        return SelectAI.create(DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build());
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

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
