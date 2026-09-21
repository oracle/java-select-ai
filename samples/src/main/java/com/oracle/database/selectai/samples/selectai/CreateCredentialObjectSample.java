/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.selectai;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for creating a {@link Credential} object from {@link SelectAI}.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a
 * {@link CredentialConfig}, and call {@link SelectAI#credential(CredentialConfig)}.
 * It does not call {@link Credential#create()}.</p>
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>SELECTAI_DB_USER</li>
 *   <li>SELECTAI_DB_PASSWORD</li>
 *   <li>SELECTAI_JDBC_URL</li>
 *   <li>SELECTAI_CREDENTIAL_NAME</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_CREDENTIAL_USERNAME</li>
 *   <li>SELECTAI_CREDENTIAL_PASSWORD</li>
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
 * <p>Compile and run this sample:</p>
 * <pre>{@code
 * javac --release 17 -cp "target/select-ai-1.0.0.jar:target/dependency/*" -d samples/out samples/src/main/java/com/oracle/database/selectai/samples/selectai/CreateCredentialObjectSample.java
 * java -cp "samples/out:target/select-ai-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.selectai.CreateCredentialObjectSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CreateCredentialObjectSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateCredentialObjectSample.class);

    private CreateCredentialObjectSample() {
    }

    public static void main(String[] args) {
        try {
            SelectAI selectAI = createSelectAI();
            CredentialConfig credentialConfig = buildCredentialConfig();
            Credential credential = selectAI.credential(credentialConfig);

            System.out.printf("Credential object created for '%s'.%n", credentialConfig.getCredentialName());
            System.out.printf("Credential implementation: %s%n", credential.getClass().getName());
        } catch (SelectAIException e) {
            LOGGER.error("Failed to create Select AI client for credential object sample.", e);
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

    private static CredentialConfig buildCredentialConfig() {
        CredentialConfig.Builder builder = CredentialConfig.builder(requiredEnv("SELECTAI_CREDENTIAL_NAME"));
        applyOptionalString(builder::username, "SELECTAI_CREDENTIAL_USERNAME");
        applyOptionalString(builder::password, "SELECTAI_CREDENTIAL_PASSWORD");
        applyOptionalString(builder::userOcid, "SELECTAI_USER_OCID");
        applyOptionalString(builder::tenancyOcid, "SELECTAI_TENANCY_OCID");
        applyOptionalString(builder::privateKey, "SELECTAI_PRIVATE_KEY");
        applyOptionalString(builder::fingerprint, "SELECTAI_FINGERPRINT");
        return builder.build();
    }

    private static void applyOptionalString(StringAttributeSetter setter, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            setter.set(value);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    @FunctionalInterface
    private interface StringAttributeSetter {
        void set(String value);
    }
}
