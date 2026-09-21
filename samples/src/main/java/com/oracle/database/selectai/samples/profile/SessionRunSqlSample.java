/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.profile;

import com.oracle.database.selectai.Session;

/**
 * Sample for calling {@link Session#runsql(String)}.
 *
 * <p>This sample delegates to {@link SessionNarrateSample} to create a
 * {@link Session}, execute the {@code runsql} session operation, and print the
 * generated SQL result.</p>
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
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_SESSION_PROMPT_1 - session prompt. Defaults to a sample prompt.</li>
 *   <li>SELECTAI_CONVERSATION_TITLE - conversation title.</li>
 *   <li>SELECTAI_CONVERSATION_DESCRIPTION - conversation description.</li>
 *   <li>SELECTAI_CONVERSATION_RETENTION_DAYS - retention period. Defaults to {@code 7}.</li>
 *   <li>SELECTAI_CONVERSATION_LENGTH - conversation length. Defaults to {@code 20}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/SessionRunSqlSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.SessionRunSqlSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal.</p>
 */
public final class SessionRunSqlSample {
    private SessionRunSqlSample() {
    }

    public static void main(String[] args) {
        SessionNarrateSample.run("runsql");
    }
}
