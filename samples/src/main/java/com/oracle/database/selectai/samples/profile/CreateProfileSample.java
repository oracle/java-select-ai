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
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample for creating a Select AI profile.
 *
 * <p>This sample demonstrates how to create a {@link SelectAI} client, build a
 * {@link ProfileAttributes} payload, call
 * {@link SelectAI#profile(String, ProfileAttributes, String, ProfileStatus)},
 * invoke {@link Profile#create()}, and print metadata for the created
 * profile.</p>
 *
 * <p>This sample changes database state by creating the profile identified by
 * {@code SELECTAI_PROFILE_NAME}. Verify the profile name before running it.
 * Use uppercase profile names, such as
 * {@code SELECTAIJAVAPROFILENAME}, because Oracle stores normal unquoted
 * identifiers in uppercase.</p>
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
 *   <li>SELECTAI_PROFILE_CREDENTIAL_NAME</li>
 *   <li>SELECTAI_PROFILE_PROVIDER</li>
 * </ul>
 *
 * <p>Optional environment variables:</p>
 * <ul>
 *   <li>SELECTAI_PROFILE_DESCRIPTION - profile description. Defaults to a
 *       sample description.</li>
 *   <li>SELECTAI_PROFILE_STATUS - initial profile status, such as
 *       {@code enabled} or {@code disabled}. Defaults to the database default.</li>
 *   <li>SELECTAI_PROFILE_PROVIDER_ENDPOINT - provider endpoint host/path.
 *       The SDK does not derive provider endpoints from provider name, region,
 *       or Azure resource name; set this variable when the profile requires
 *       {@code provider_endpoint}.</li>
 *   <li>SELECTAI_PROFILE_REGION - provider region.</li>
 *   <li>SELECTAI_PROFILE_OBJECT_LIST_MODE - object selection mode, such as
 *       {@code automated} or {@code all}. Defaults to {@code automated}.</li>
 *   <li>SELECTAI_PROFILE_OBJECT_LIST - optional object-list JSON used when
 *       constraining profile metadata. This value is not required when
 *       {@code SELECTAI_PROFILE_OBJECT_LIST_MODE} is set; when omitted, object
 *       selection follows database behavior for the supplied mode.</li>
 *   <li>SELECTAI_PROFILE_MODEL - provider model identifier.</li>
 *   <li>SELECTAI_PROFILE_OCI_API_FORMAT - OCI API format, such as {@code GENERIC}
 *       or {@code COHERE}.</li>
 *   <li>SELECTAI_PROFILE_OCI_COMPARTMENT_ID - OCI compartment OCID.</li>
 *   <li>SELECTAI_PROFILE_OCI_ENDPOINT_ID - OCI dedicated endpoint OCID.</li>
 *   <li>SELECTAI_PROFILE_OCI_RUNTIME_TYPE - OCI runtime type.</li>
 *   <li>SELECTAI_PROFILE_COMMENTS - defaults to {@code true}.</li>
 *   <li>SELECTAI_PROFILE_CONSTRAINTS - defaults to {@code true}.</li>
 *   <li>SELECTAI_PROFILE_MAX_TOKENS - defaults to {@code 1024}.</li>
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
 *   samples/src/main/java/com/oracle/database/selectai/samples/profile/CreateProfileSample.java
 * }</pre>
 *
 * <p>Run this sample:</p>
 * <pre>{@code
 * java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
 *   com.oracle.database.selectai.samples.profile.CreateProfileSample
 * }</pre>
 *
 * <p>This sample prints normal result data to standard output so it is easy to
 * read when run from a terminal. It uses the logger only for error reporting
 * and diagnostics.</p>
 */
public final class CreateProfileSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateProfileSample.class);

    private CreateProfileSample() {
    }

    public static void main(String[] args) {
        String profileName = requiredEnv("SELECTAI_PROFILE_NAME");
        String description = envOrDefault("SELECTAI_PROFILE_DESCRIPTION",
                "Profile created by the Java Select AI SDK sample.");

        try {
            SelectAI selectAI = createSelectAI();
            ProfileAttributes profileAttributes = buildProfileAttributes();
            Profile profile = selectAI.profile(profileName, profileAttributes, description, profileStatus());
            boolean created = profile.create();

            System.out.printf("Create profile '%s' completed: %s%n",
                    valueOrEmpty(profile.getProfileName()), created);
            System.out.printf("Status      : %s%n", valueOrEmpty(profile.getStatus()));
            System.out.printf("Description : %s%n", valueOrEmpty(profile.getDescription()));
        } catch (SelectAIException e) {
            LOGGER.error("Failed to create Select AI profile '{}'.", profileName, e);
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

    private static ProfileAttributes buildProfileAttributes() {
        ProfileAttributes.Builder builder = ProfileAttributes.builder()
                .credentialName(requiredEnv("SELECTAI_PROFILE_CREDENTIAL_NAME"))
                .provider(requiredEnv("SELECTAI_PROFILE_PROVIDER"))
                .objectListMode(envOrDefault("SELECTAI_PROFILE_OBJECT_LIST_MODE", "automated"))
                .comments(Boolean.parseBoolean(envOrDefault("SELECTAI_PROFILE_COMMENTS", "true")))
                .constraints(Boolean.parseBoolean(envOrDefault("SELECTAI_PROFILE_CONSTRAINTS", "true")))
                .maxTokens(Integer.valueOf(envOrDefault("SELECTAI_PROFILE_MAX_TOKENS", "1024")));

        applyOptionalString(builder::model, "SELECTAI_PROFILE_MODEL");
        applyOptionalString(builder::additionalInstructions, "SELECTAI_PROFILE_ADDITIONAL_INSTRUCTIONS");
        applyOptionalString(builder::role, "SELECTAI_PROFILE_ROLE");
        applyOptionalString(builder::providerEndpoint, "SELECTAI_PROFILE_PROVIDER_ENDPOINT");
        applyOptionalString(builder::region, "SELECTAI_PROFILE_REGION");
        applyOptionalString(builder::objectList, "SELECTAI_PROFILE_OBJECT_LIST");
        applyOptionalString(builder::ociApiformat, "SELECTAI_PROFILE_OCI_API_FORMAT");
        applyOptionalString(builder::ociCompartmentId, "SELECTAI_PROFILE_OCI_COMPARTMENT_ID");
        applyOptionalString(builder::ociEndpointId, "SELECTAI_PROFILE_OCI_ENDPOINT_ID");
        applyOptionalString(builder::ociRuntimetype, "SELECTAI_PROFILE_OCI_RUNTIME_TYPE");

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

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static ProfileStatus profileStatus() {
        String value = System.getenv("SELECTAI_PROFILE_STATUS");
        return value == null || value.isBlank() ? null : ProfileStatus.fromValue(value);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface StringAttributeSetter {
        void set(String value);
    }
}
