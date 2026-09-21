/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.profile;

import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.model.GenerateParams;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for calling {@link Profile#explainsql(String, GenerateParams)}.
 *
 * <p>This sample demonstrates how to open a {@link Profile}, build
 * {@link GenerateParams} with an existing conversation identifier, call
 * {@link Profile#explainsql(String, GenerateParams)}, and print the generated
 * explanation.</p>
 *
 * <p>To run this sample with prompts such as {@code how many customers}, ensure
 * the profile object list contains tables that match the prompt. For example,
 * if the profile object list references the Oracle sample schema objects
 * {@code SH.CUSTOMERS} and {@code SH.COUNTRIES}, ensure the {@code SH} schema
 * is installed and visible to {@code SELECTAI_DB_USER}. The {@code SH} schema
 * is not guaranteed to exist in every Oracle Autonomous AI Database or Oracle
 * Database instance.</p>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/ExplainSqlWithGenerateParamsSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.ExplainSqlWithGenerateParamsSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class ExplainSqlWithGenerateParamsSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplainSqlWithGenerateParamsSample.class);

    private ExplainSqlWithGenerateParamsSample() {
    }

    public static void main(String[] args) {
        String profileName = RunSqlWithGenerateParamsSample.requiredEnv("SELECTAI_PROFILE_NAME");
        String prompt = RunSqlWithGenerateParamsSample.requiredEnv("SELECTAI_PROFILE_PROMPT");
        String conversationId = RunSqlWithGenerateParamsSample.requiredEnv("SELECTAI_CONVERSATION_ID");

        try {
            Profile profile = RunSqlWithGenerateParamsSample.createSelectAI().profile(profileName);
            GenerateParams params = GenerateParams.builder().conversationId(conversationId).build();
            String response = profile.explainsql(prompt, params);

            System.out.printf("Explainsql with params response:%n%s%n",
                    RunSqlWithGenerateParamsSample.valueOrEmpty(response));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to run explainsql with params for Select AI profile '{}'.", profileName, e);
        }
    }
}
