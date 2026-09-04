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
import com.oracle.database.selectai.model.Feedback;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for submitting Select AI feedback for a profile.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a
 * {@link Feedback} request, open a {@link Profile}, call
 * {@link Profile#feedback(Feedback)}, and print whether the submission
 * succeeded.</p>
 *
 * <p>This sample changes database state by adding or deleting feedback guidance
 * for generated SQL.</p>
 *
 * <p>The supplied SQL identifier or SQL text must refer to a Select AI generated
 * SQL statement that {@code DBMS_CLOUD_AI.FEEDBACK} can find. For repeatable
 * integration testing, prefer {@code SELECTAI_FEEDBACK_SQL_ID} from a known
 * Select AI statement.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_PROFILE_NAME</li>
 *   <li>Exactly one of SELECTAI_FEEDBACK_SQL_ID or SELECTAI_FEEDBACK_SQL_TEXT</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_FEEDBACK_TYPE - defaults to {@code positive}.</li>
 *   <li>SELECTAI_FEEDBACK_CONTENT - defaults to sample feedback text.</li>
 *   <li>SELECTAI_FEEDBACK_RESPONSE - required by the model when feedback type is negative.</li>
 *   <li>SELECTAI_FEEDBACK_OPERATION - optional; when omitted,
 *       {@code DBMS_CLOUD_AI.FEEDBACK} applies its database default.</li>
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
 * javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/profile/SubmitFeedbackProfileSample.java
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.SubmitFeedbackProfileSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class SubmitFeedbackProfileSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmitFeedbackProfileSample.class);

    private SubmitFeedbackProfileSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");

        try {
            SelectAI selectAI = createSelectAI();
            Profile profile = selectAI.profile(profileName);
            Feedback feedback = buildFeedback();
            boolean submitted = profile.feedback(feedback);

            System.out.printf("Submit feedback completed: %s%n", submitted);
            System.out.printf("Profile: %s%n", profileName);
            System.out.printf("Feedback type: %s%n", feedback.getFeedbackType());
            System.out.printf("Operation: %s%n", feedback.getOperation());
        } catch (SelectAIException e) {
            LOGGER.error("Failed to submit feedback for Select AI profile '{}'.", profileName, e);
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

    private static Feedback buildFeedback() {
        String sqlId = optionalEnv("SELECTAI_FEEDBACK_SQL_ID");
        String sqlText = optionalEnv("SELECTAI_FEEDBACK_SQL_TEXT");
        boolean hasSqlId = hasText(sqlId);
        boolean hasSqlText = hasText(sqlText);

        if (hasSqlId == hasSqlText) {
            throw new IllegalStateException(
                    "Provide exactly one of SELECTAI_FEEDBACK_SQL_ID or SELECTAI_FEEDBACK_SQL_TEXT");
        }

        Feedback.FeedbackType feedbackType = Feedback.FeedbackType.from(
                envOrDefault("SELECTAI_FEEDBACK_TYPE", "positive"));
        Feedback.Builder builder = Feedback.builder()
                .feedbackType(feedbackType)
                .feedbackContent(envOrDefault("SELECTAI_FEEDBACK_CONTENT", "Approved by Java SDK sample."))
                .operation(optionalEnv("SELECTAI_FEEDBACK_OPERATION"));

        if (hasSqlId) {
            builder.sqlId(sqlId);
        } else {
            builder.sqlText(sqlText);
        }

        String response = optionalEnv("SELECTAI_FEEDBACK_RESPONSE");
        if (hasText(response)) {
            builder.response(response);
        }

        return builder.build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
