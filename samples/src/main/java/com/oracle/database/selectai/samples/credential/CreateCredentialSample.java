/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.credential;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for creating a database credential used by Select AI.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a
 * {@link CredentialConfig}, obtain a {@link Credential} object from
 * {@link SelectAI#credential(CredentialConfig)}, call
 * {@link Credential#create()}, and print whether the create operation
 * succeeded.</p>
 *
 * <p>This sample changes database state by creating the credential identified
 * by {@code SELECTAI_CREDENTIAL_NAME}. Verify the credential name and secret
 * values before running it.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_CREDENTIAL_NAME</li>
 * </ul>
 *
 * <p>For username/password credentials, set:</p>
 * <ul>
 *   <li>SELECTAI_CREDENTIAL_USERNAME</li>
 *   <li>SELECTAI_CREDENTIAL_PASSWORD (optional)</li>
 * </ul>
 *
 * <p>If {@code SELECTAI_CREDENTIAL_USERNAME} is not set, this sample creates
 * an OCI signing-key credential and requires:</p>
 * <ul>
 *   <li>SELECTAI_USER_OCID</li>
 *   <li>SELECTAI_TENANCY_OCID</li>
 *   <li>SELECTAI_PRIVATE_KEY</li>
 *   <li>SELECTAI_FINGERPRINT</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/credential/CreateCredentialSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.credential.CreateCredentialSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CreateCredentialSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateCredentialSample.class);

    private CreateCredentialSample() {
    }

    public static void main(String[] args) {
        String credentialName = requiredEnv("SELECTAI_CREDENTIAL_NAME");

        try {
            SelectAI selectAI = createSelectAI();
            Credential credential = selectAI.credential(buildCredentialConfig(credentialName));
            boolean created = credential.create();

            System.out.printf("Create credential '%s' completed: %s%n", credentialName, created);
        } catch (SelectAIException e) {
            LOGGER.error("Failed to create Select AI credential '{}'.", credentialName, e);
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

    private static CredentialConfig buildCredentialConfig(String credentialName) {
        CredentialConfig.Builder builder = CredentialConfig.builder(credentialName);
        String credentialUsername = optionalEnv("SELECTAI_CREDENTIAL_USERNAME");
        if (credentialUsername != null) {
            return builder
                    .username(credentialUsername)
                    .password(optionalEnv("SELECTAI_CREDENTIAL_PASSWORD"))
                    .build();
        }
        return builder
                .userOcid(requiredEnv("SELECTAI_USER_OCID"))
                .tenancyOcid(requiredEnv("SELECTAI_TENANCY_OCID"))
                .privateKey(requiredEnv("SELECTAI_PRIVATE_KEY"))
                .fingerprint(requiredEnv("SELECTAI_FINGERPRINT"))
                .build();
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

}
