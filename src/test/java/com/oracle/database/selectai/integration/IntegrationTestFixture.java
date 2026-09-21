/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.DatabaseAdmin;
import java.util.List;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.ProfileAttributes;
import com.oracle.database.selectai.model.ProfileStatus;
import com.oracle.database.selectai.model.SelectAIException;
import java.util.Locale;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared lifecycle and configuration fixture for live Select AI integration tests.
 *
 * <p>The fixture reads {@code SELECT_AI_IT_*} values from the process
 * environment inherited by the Maven test JVM. The shared database connection
 * is intended for ordinary feature tests. Table-backed feature fixtures create
 * the shared tables in the {@code ADMIN} schema through the admin connection.
 * Suites that perform database-wide administration or create users must explicitly call
 * {@link #adminDbConfig()} to obtain the separate admin connection configuration.
 * Profile-based suites create an isolated OCI signing-key GenAI credential
 * from the four
 * SELECT_AI_IT_OCI_* key fields, use it when creating the profile, and drop it
 * after the profile is removed.</p>
 */
public abstract class IntegrationTestFixture {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestFixture.class);

    private static final String PROFILE_PREFIX = "JSAI_IT";
    private static final String PROFILE_DESCRIPTION = "Java Select AI integration test profile";
    private static final String TEST_SCHEMA_OWNER = "ADMIN";
    protected static final String DEFAULT_PROVIDER = "oci";
    private static final String[] SHARED_TABLE_NAMES = {
        "gymnast", "movie", "actor", "people", "director"
    };
    private static final String[] SELECT_AI_PACKAGES = {
        "DBMS_CLOUD",
        "DBMS_CLOUD_AI",
        "DBMS_CLOUD_AI_AGENT",
        "DBMS_CLOUD_PIPELINE"
    };

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private String currentTestMethod = "<setup>";
    protected SelectAI selectAI;
    protected Profile profile;
    protected String profileName;
    protected DbConnectionConfig dbConfig;
    private Credential isolatedGenAiCredential;
    private String isolatedGenAiCredentialName;
    private final Set<String> managedProfileNames = new HashSet<>();
    private final Set<Credential> managedCredentials = new HashSet<>();

    /**
     * Recreates the common integration schema for a table-backed feature
     * fixture. A table-backed feature fixture calls this once before its
     * concrete integration test class.
     *
     * @param testClassName concrete integration test class being initialized
     * @throws Exception when the configured database cannot be initialized
     */
    protected static void createFreshIntegrationSchema(String testClassName) throws Exception {
        if (!hasConfiguredEnvironment()) {
            LOG.warn("SKIPPED {}: missing SELECT_AI_IT_* environment variables", testClassName);
            assumeTrue(false,
                    "Set SELECT_AI_IT_* environment variables to run integration tests.");
        }

        DbConnectionConfig adminConfig = adminDbConfigFromEnvironment(testClassName);
        DbConnectionConfig featureConfig = dbConfigFromEnvironmentForSchema(testClassName);

        SelectAI schemaClient = SelectAI.create(adminConfig);
        try (Connection connection = schemaClient.getConnection()) {
            ensureFeatureUser(connection, adminConfig, featureConfig);
            recreateIntegrationSchema(connection);
            grantFeatureUserPrivileges(connection, featureConfig.getDbUser());
        } finally {
            schemaClient.close();
        }
        
        try (DatabaseAdmin adminClient = DatabaseAdmin.create(adminConfig)) {
            grantProviderHttpAccess(
                    adminClient,
                    featureConfig.getDbUser());
        }
    }

    private static void recreateIntegrationSchema(Connection connection) throws SQLException {
        for (String tableName : SHARED_TABLE_NAMES) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE " + qualifiedTestTable(tableName)
                        + " CASCADE CONSTRAINTS");
            } catch (SQLException ignored) {
                // Match shared setup behavior when the table does not exist.
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE ADMIN.people (
                        id NUMBER PRIMARY KEY,
                        name VARCHAR2(50),
                        age NUMBER,
                        height NUMBER,
                        hometown VARCHAR2(100)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE ADMIN.gymnast (
                        id NUMBER PRIMARY KEY,
                        floor_ex_points NUMBER,
                        rings_points NUMBER,
                        parallel_bars_points NUMBER,
                        horizontal_bar_points NUMBER,
                        total_points NUMBER,
                        FOREIGN KEY (id) REFERENCES ADMIN.people (id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE ADMIN.director (
                        director_id INT PRIMARY KEY,
                        name VARCHAR(10)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE ADMIN.movie (
                        movie_id INT PRIMARY KEY,
                        title VARCHAR(100),
                        release_date DATE,
                        genre VARCHAR(50),
                        director_id INT,
                        FOREIGN KEY (director_id) REFERENCES ADMIN.director (director_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE ADMIN.actor (
                        actor_id INT PRIMARY KEY,
                        name VARCHAR(100)
                    )
                    """);
        }

        try (PreparedStatement peopleInsert = connection.prepareStatement(
                "INSERT INTO ADMIN.people (id, name, age, height, hometown) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            addPeopleRow(peopleInsert, 1, "John Smith", 22, 170, "New York");
            addPeopleRow(peopleInsert, 2, "Emma Johnson", 20, 165, "Los Angeles");
            addPeopleRow(peopleInsert, 3, "Michael Brown", 24, 180, "Chicago");
            addPeopleRow(peopleInsert, 4, "Sophia Lee", 19, 160, "Houston");
            addPeopleRow(peopleInsert, 5, "William Kim", 21, 175, "San Francisco");
            peopleInsert.executeBatch();
        }

        try (PreparedStatement gymnastInsert = connection.prepareStatement(
                "INSERT INTO ADMIN.gymnast (id, floor_ex_points, rings_points, "
                        + "parallel_bars_points, horizontal_bar_points, total_points) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            addGymnastRow(gymnastInsert, 1, 9.5, 8.8, 9.2, 9.0, 36.5);
            addGymnastRow(gymnastInsert, 2, 8.7, 9.0, 8.5, 8.9, 35.1);
            addGymnastRow(gymnastInsert, 3, 9.0, 9.2, 9.1, 9.3, 36.6);
            addGymnastRow(gymnastInsert, 4, 8.5, 8.0, 8.7, 8.3, 33.5);
            addGymnastRow(gymnastInsert, 5, 9.2, 8.5, 8.9, 9.1, 35.7);
            gymnastInsert.executeBatch();
        }

    }

    private static void ensureFeatureUser(Connection connection,
                                        DbConnectionConfig adminConfig,
                                        DbConnectionConfig featureConfig)
            throws SQLException {
        String adminUser = sqlIdentifier(adminConfig.getDbUser());
        String featureUser = sqlIdentifier(featureConfig.getDbUser());

        // ADMIN already has the required privileges.
        if (adminUser.equalsIgnoreCase(featureUser)) {
            return;
        }

        boolean userExists;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM ALL_USERS WHERE USERNAME = ?")) {
            statement.setString(1, featureUser.toUpperCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                userExists = resultSet.getInt(1) > 0;
            }
        }

        try (Statement statement = connection.createStatement()) {
            if (userExists) {
                statement.execute(
                        "ALTER USER " + featureUser + " ACCOUNT UNLOCK");
            } else {
                String escapedPassword = featureConfig.getDbPassword()
                        .replace("\"", "\"\"");
                statement.execute(
                        "CREATE USER " + featureUser
                                + " IDENTIFIED BY \"" + escapedPassword + "\"");
            }

            statement.execute(
                    "GRANT CREATE SESSION, CREATE TABLE, UNLIMITED TABLESPACE TO "
                            + featureUser);
        }
    }

    private static void grantFeatureUserPrivileges(Connection connection,
                                                   String featureUser)
            throws SQLException {
        String user = sqlIdentifier(featureUser);

        if ("ADMIN".equalsIgnoreCase(user)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            for (String packageName : SELECT_AI_PACKAGES) {
                statement.execute(
                        "GRANT EXECUTE ON " + packageName + " TO " + user);
            }

            for (String tableName : SHARED_TABLE_NAMES) {
                statement.execute(
                        "GRANT SELECT, INSERT, UPDATE, DELETE ON "
                                + qualifiedTestTable(tableName)
                                + " TO " + user);
            }
        }
    }

    private static void grantProviderHttpAccess(
            DatabaseAdmin databaseAdmin,
            String featureUser)
            throws SelectAIException {

        List<String> users = List.of(featureUser);

        String awsRegion = configuredEnvironmentValue(
                "SELECT_AI_IT_PROVIDER_AWS_REGION");
        if (awsRegion == null) {
            awsRegion = "us-east-1";
        }

        String azureResource = configuredEnvironmentValue(
                "SELECT_AI_IT_PROVIDER_AZURE_RESOURCE_NAME");
        if (azureResource == null) {
            azureResource = "ADBST-AI-RESOURCE-JAPAN-EAST";
        }

        databaseAdmin.grantHttpAccess(
                users,
                "bedrock-runtime."
                        + awsRegion.toLowerCase(Locale.ROOT)
                        + ".amazonaws.com");

        databaseAdmin.grantHttpAccess(users, "api.openai.com");
        databaseAdmin.grantHttpAccess(users, "api.anthropic.com");

        databaseAdmin.grantHttpAccess(
                users,
                azureResource.toLowerCase(Locale.ROOT)
                        + ".openai.azure.com");

        databaseAdmin.grantHttpAccess(
                users,
                "generativelanguage.googleapis.com");
    }

    private static void addPeopleRow(PreparedStatement statement, int id, String name,
                                     int age, int height, String hometown) throws SQLException {
        statement.setInt(1, id);
        statement.setString(2, name);
        statement.setInt(3, age);
        statement.setInt(4, height);
        statement.setString(5, hometown);
        statement.addBatch();
    }

    private static void addGymnastRow(PreparedStatement statement, int id, double floorExPoints,
                                      double ringsPoints, double parallelBarsPoints,
                                      double horizontalBarPoints, double totalPoints)
            throws SQLException {
        statement.setInt(1, id);
        statement.setDouble(2, floorExPoints);
        statement.setDouble(3, ringsPoints);
        statement.setDouble(4, parallelBarsPoints);
        statement.setDouble(5, horizontalBarPoints);
        statement.setDouble(6, totalPoints);
        statement.addBatch();
    }

    protected void createIsolatedProfile() throws Exception {
        dbConfig = dbConfigFromEnvironment();
        selectAI = SelectAI.create(dbConfig);
        if (requiresProfile()) {
            createIsolatedGenAiCredential();
            profileName = PROFILE_PREFIX + "_" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 16).toUpperCase();
            try {
                profile = selectAI.profile(profileName, profileAttributesFromEnvironment(),
                        PROFILE_DESCRIPTION, null);
                profile.create();
            } catch (Exception e) {
                dropIsolatedGenAiCredential();
                throw e;
            }
        }
        logger.info("Initialized integration fixture for {}", testId());
    }

    /**
     * Opens the connection and resources required by a feature fixture.
     * Feature fixtures call this explicitly from their own {@code @BeforeEach}
     * method so suites without this dependency do not pay for it.
     *
     * @param testInfo current JUnit test information
     * @throws Exception when the configured database or profile cannot be initialized
     */
    protected final void openIsolatedConnection(TestInfo testInfo) throws Exception {
        setCurrentTestMethod(testInfo.getTestMethod()
                .map(method -> method.getName())
                .orElse("<setup>"));
        createIsolatedProfile();
    }

    /**
     * Closes the connection and resources opened by {@link #openIsolatedConnection(TestInfo)}.
     *
     * @throws Exception when managed resources cannot be removed
     */
    protected final void closeIsolatedConnection() throws Exception {
        dropIsolatedProfile();
    }

    /**
     * Returns the concrete test class name supplied by JUnit.
     *
     * @param testInfo current JUnit test information
     * @return concrete test class name, or a fallback when unavailable
     */
    protected static final String testClassName(TestInfo testInfo) {
        return testInfo.getTestClass()
                .map(testClass -> testClass.getSimpleName())
                .orElse("integration suite");
    }

    protected void dropIsolatedProfile() throws Exception {
        try {
            dropManagedProfiles();
            if (profile != null) {
                profile.drop(true);
            }
        } finally {
            try {
                dropManagedCredentials();
            } finally {
                try {
                    dropIsolatedGenAiCredential();
                } finally {
                    if (selectAI != null) {
                        selectAI.close();
                    }
                }
            }
        }
        selectAI = null;
        dbConfig = null;
        profile = null;
        profileName = null;
    }

    /**
     * Returns the credential created for the current isolated profile.
     *
     * <p>The integration fixture creates one OCI credential for the profile test
     * fixture and uses the OCI credential fields from the environment, but gives
     * the database object a unique name so parallel or repeated runs do not share
     * mutable resources.</p>
     *
     * @return current isolated credential name
     */
    protected final String isolatedCredentialName() {
        assumeTrue(isolatedGenAiCredentialName != null,
                "The isolated OCI credential has not been initialized.");
        return isolatedGenAiCredentialName;
    }

    /**
     * Returns the attributes used by the current isolated profile.
     *
     * @return profile attribute snapshot
     * @throws SelectAIException when the profile attributes cannot be fetched
     */
    protected final ProfileAttributes isolatedProfileAttributes() throws SelectAIException {
        assumeTrue(profile != null && profile.getProfileAttributes() != null,
                "The current integration test does not have an isolated profile.");
        return profile.getProfileAttributes();
    }

    /**
     * Creates an additional profile and registers it for fixture cleanup.
     *
     * @param name profile name
     * @param attributes profile attributes
     * @param description profile description
     * @return created profile
     * @throws Exception when the Java SDK cannot create the profile
     */
    protected final Profile createManagedProfile(String name,
                                                  ProfileAttributes attributes,
                                                  String description) throws Exception {
        return createManagedProfile(name, attributes, description, null);
    }

    /**
     * Creates an additional profile with an explicit initial status and
     * registers it for fixture cleanup.
     *
     * @param name profile name
     * @param attributes profile attributes
     * @param description profile description
     * @param status optional initial profile status
     * @return created profile
     * @throws Exception when the Java SDK cannot create the profile
     */
    protected final Profile createManagedProfile(String name,
                                                  ProfileAttributes attributes,
                                                  String description,
                                                  ProfileStatus status) throws Exception {
        Profile created = selectAI.profile(name, attributes, description, status);
        created.create();
        managedProfileNames.add(name);
        return created;
    }

    /**
     * Creates a credential and registers it for cleanup after the profiles
     * created by the current test have been removed.
     *
     * @param credentialConfig credential definition, including its secret values
     * @return created credential object
     * @throws Exception when credential creation fails
     */
    protected final Credential createManagedCredential(CredentialConfig credentialConfig)
            throws Exception {
        Credential created = selectAI.credential(credentialConfig);
        created.create();
        managedCredentials.add(created);
        return created;
    }

    private void dropManagedProfiles() {
        if (selectAI == null) {
            managedProfileNames.clear();
            return;
        }
        for (String name : new HashSet<>(managedProfileNames)) {
            try {
                selectAI.profile(name).drop(true);
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            }
        }
        managedProfileNames.clear();
    }

    private void dropManagedCredentials() {
        for (Credential credential : new HashSet<>(managedCredentials)) {
            try {
                credential.drop();
            } catch (Exception ignored) {
                // Preserve the primary test failure; cleanup is best effort.
            }
        }
        managedCredentials.clear();
    }

    /**
     * Returns a configured value, or {@code null} when the optional key is absent.
     *
     * @param name environment variable name
     * @return configured value
     */
    protected final String env(String name) {
        return configuredEnvironmentValue(name);
    }

    protected final void setCurrentTestMethod(String testMethod) {
        currentTestMethod = testMethod;
    }

    protected final String testId() {
        return getClass().getSimpleName() + "#" + currentTestMethod;
    }

    protected final void logSkipped(String missingRequirement) {
        logger.warn("SKIPPED {}: missing {}", testId(), missingRequirement);
    }

    /**
     * Requires an optional integration prerequisite for a particular test.
     *
     * @param name environment variable name
     * @param reason explanation shown when the test is skipped
     * @return configured value
     */
    protected final String requiredFeatureValue(String name, String reason) {
        String value = env(name);
        if (value == null) {
            logSkipped(name + " (" + reason + ")");
            assumeTrue(false, reason + " (missing " + name + ")");
        }
        return value;
    }

    /**
     * Returns an environment value or a test default.
     *
     * @param name environment variable name
     * @param defaultValue fallback value
     * @return configured value or fallback
     */
    protected final String envOrDefault(String name, String defaultValue) {
        String value = env(name);
        return value == null ? defaultValue : value;
    }

    /**
     * Allows a feature suite to use the common integration support without
     * creating an unrelated profile resource.
     *
     * @return {@code true} when the default isolated profile should be created
     */
    protected boolean requiresProfile() {
        return true;
    }

    /**
     * Returns the object list for the default profile created by this suite.
     * Each profile-based integration suite must declare its own list so the
     * database objects used by the suite are visible in that suite's source.
     *
     * @return object-list JSON, or {@code null} when the profile does not use
     *         an object list
     */
    protected String profileObjectList() {
        if (requiresProfile()) {
            throw new IllegalStateException(getClass().getSimpleName()
                    + " must override profileObjectList()");
        }
        return null;
    }

    /**
     * Builds an object list for the common tables owned by the ADMIN schema.
     *
     * @param objectNames object names; an empty list creates an owner-only
     *                    descriptor
     * @return object-list JSON referencing the ADMIN-owned common tables
     */
    protected final String objectListFor(String... objectNames) {
        return objectListForOwner(TEST_SCHEMA_OWNER, objectNames);
    }

    /**
     * Builds an object list for an explicit owner.
     *
     * @param owner object owner/schema
     * @param objectNames object names; an empty list creates an owner-only
     *                    descriptor
     * @return object-list JSON
     */
    protected final String objectListForOwner(String owner, String... objectNames) {
        assumeTrue(isSqlIdentifier(owner),
                "Object-list owner must be an ordinary Oracle identifier.");

        StringBuilder json = new StringBuilder("[");
        if (objectNames.length == 0) {
            json.append("{\"owner\":\"").append(owner).append("\"}");
        } else {
            for (int index = 0; index < objectNames.length; index++) {
                String objectName = objectNames[index];
                assumeTrue(isSqlIdentifier(objectName),
                        "Object-list names must be ordinary Oracle identifiers.");
                if (index > 0) {
                    json.append(',');
                }
                json.append("{\"owner\":\"").append(owner)
                        .append("\",\"name\":\"").append(objectName).append("\"}");
            }
        }
        return json.append(']').toString();
    }

    private void createIsolatedGenAiCredential() throws Exception {
        isolatedGenAiCredentialName = "GENAI_CRED_JSAI_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        isolatedGenAiCredential = selectAI.credential(CredentialConfig.builder(isolatedGenAiCredentialName)
                .userOcid(required("SELECT_AI_IT_OCI_USER_OCID"))
                .tenancyOcid(required("SELECT_AI_IT_OCI_TENANCY_OCID"))
                .privateKey(required("SELECT_AI_IT_OCI_PRIVATE_KEY"))
                .fingerprint(required("SELECT_AI_IT_OCI_FINGERPRINT"))
                .build());
        isolatedGenAiCredential.create();
    }

    private void dropIsolatedGenAiCredential() throws Exception {
        if (isolatedGenAiCredential != null) {
            try {
                isolatedGenAiCredential.drop();
            } finally {
                isolatedGenAiCredential = null;
                isolatedGenAiCredentialName = null;
            }
        }
    }

    /**
     * Creates a connection configuration for another database user while
     * preserving the configured JDBC URL.
     *
     * @param user database username
     * @param password database password
     * @return connection configuration for the supplied database user
     */
    protected final DbConnectionConfig connectionConfigFor(String user, String password) {
        assumeTrue(dbConfig != null, "The shared database configuration is not initialized.");
        return DbConnectionConfig.builder()
                .dbUser(user)
                .dbPassword(password)
                .jdbcUrl(dbConfig.getJdbcUrl())
                .build();
    }

    /**
     * Loads the connection configuration reserved for database administration
     * integration tests.
     *
     * <p>The admin credentials are deliberately separate from the shared
     * feature-test credentials. Both accounts use the shared JDBC URL because
     * the admin operations and feature operations target the same database.</p>
     *
     * @return admin-capable database connection configuration
     */
    protected final DbConnectionConfig adminDbConfig() {
        assumeTrue(dbConfig != null,
                "The shared database configuration is not initialized.");

        return DbConnectionConfig.builder()
                .dbUser(required("SELECT_AI_IT_ADMIN_DB_USER"))
                .dbPassword(required("SELECT_AI_IT_ADMIN_DB_PASSWORD"))
                .jdbcUrl(dbConfig.getJdbcUrl())
                .build();
    }

    /**
     * Returns the live JDBC connection used by the Select AI client.
     *
     * @return active JDBC connection
     */
    protected final Connection jdbcConnection() {
        assumeTrue(selectAI != null,
                "Integration tests require an active Select AI client.");
        Connection connection = selectAI.getConnection();
        assumeTrue(connection != null, "Integration tests require an active JDBC connection.");
        return connection;
    }

    /**
     * Counts rows in a configured database object for post-operation assertions.
     *
     * <p>Identifiers are restricted to ordinary unquoted Oracle identifiers before
     * being placed in the SQL statement. Environment values therefore cannot
     * become arbitrary SQL fragments.</p>
     *
     * @param owner object owner/schema
     * @param objectName table or view name
     * @return current row count
     * @throws SQLException when the count query fails
     */
    protected final long rowCount(String owner, String objectName) throws SQLException {
        String qualifiedObject = sqlIdentifier(owner) + "." + sqlIdentifier(objectName);
        try (PreparedStatement statement = jdbcConnection().prepareStatement(
                "SELECT COUNT(*) FROM " + qualifiedObject);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private DbConnectionConfig dbConfigFromEnvironment() {
        String dbUser = required("SELECT_AI_IT_DB_USER");
        String dbPassword = required("SELECT_AI_IT_DB_PASSWORD");
        String dbUrl = env("SELECT_AI_IT_DB_URL");
        String dbName = env("SELECT_AI_IT_DB_NAME");
        String walletLocation = env("SELECT_AI_IT_WALLET_LOCATION");

        DbConnectionConfig.Builder builder = DbConnectionConfig.builder()
                .dbUser(dbUser)
                .dbPassword(dbPassword);
        if (dbUrl != null) {
            builder.jdbcUrl(dbUrl);
        } else {
            if (dbName == null || walletLocation == null) {
                logSkipped("SELECT_AI_IT_DB_URL or both SELECT_AI_IT_DB_NAME and "
                        + "SELECT_AI_IT_WALLET_LOCATION");
                assumeTrue(false,
                        "Set SELECT_AI_IT_DB_URL or both SELECT_AI_IT_DB_NAME and "
                                + "SELECT_AI_IT_WALLET_LOCATION.");
            }
            builder.jdbcUrl(walletJdbcUrl(dbName, walletLocation));
        }
        return builder.build();
    }

    private static DbConnectionConfig dbConfigFromEnvironmentForSchema(String testClassName) {
        String dbUser = requiredEnvironmentValue("SELECT_AI_IT_DB_USER", testClassName);
        String dbPassword = requiredEnvironmentValue("SELECT_AI_IT_DB_PASSWORD", testClassName);
        String dbUrl = configuredEnvironmentValue("SELECT_AI_IT_DB_URL");
        String dbName = configuredEnvironmentValue("SELECT_AI_IT_DB_NAME");
        String walletLocation = configuredEnvironmentValue("SELECT_AI_IT_WALLET_LOCATION");

        DbConnectionConfig.Builder builder = DbConnectionConfig.builder()
                .dbUser(dbUser)
                .dbPassword(dbPassword);
        if (dbUrl != null) {
            builder.jdbcUrl(dbUrl);
        } else {
            if (dbName == null || walletLocation == null) {
                LOG.warn("SKIPPED {}: missing SELECT_AI_IT_DB_URL or both "
                        + "SELECT_AI_IT_DB_NAME and SELECT_AI_IT_WALLET_LOCATION",
                        testClassName);
                assumeTrue(false,
                        "Set SELECT_AI_IT_DB_URL or both SELECT_AI_IT_DB_NAME and "
                                + "SELECT_AI_IT_WALLET_LOCATION.");
            }
            builder.jdbcUrl(walletJdbcUrl(dbName, walletLocation));
        }
        return builder.build();
    }

    private static DbConnectionConfig adminDbConfigFromEnvironment(String testClassName) {
        String dbUser = requiredEnvironmentValue("SELECT_AI_IT_ADMIN_DB_USER", testClassName);
        String dbPassword = requiredEnvironmentValue("SELECT_AI_IT_ADMIN_DB_PASSWORD", testClassName);
        String dbUrl = configuredEnvironmentValue("SELECT_AI_IT_DB_URL");
        String dbName = configuredEnvironmentValue("SELECT_AI_IT_DB_NAME");
        String walletLocation = configuredEnvironmentValue("SELECT_AI_IT_WALLET_LOCATION");

        DbConnectionConfig.Builder builder = DbConnectionConfig.builder()
                .dbUser(dbUser)
                .dbPassword(dbPassword);
        if (dbUrl != null) {
            builder.jdbcUrl(dbUrl);
        } else {
            if (dbName == null || walletLocation == null) {
                LOG.warn("SKIPPED {}: missing SELECT_AI_IT_DB_URL or both "
                        + "SELECT_AI_IT_DB_NAME and SELECT_AI_IT_WALLET_LOCATION",
                        testClassName);
                assumeTrue(false,
                        "Set SELECT_AI_IT_DB_URL or both SELECT_AI_IT_DB_NAME and "
                                + "SELECT_AI_IT_WALLET_LOCATION.");
            }
            builder.jdbcUrl(walletJdbcUrl(dbName, walletLocation));
        }
        return builder.build();
    }

    private static String qualifiedTestTable(String tableName) {
        return TEST_SCHEMA_OWNER + "." + tableName;
    }

    private static String walletJdbcUrl(String dbName, String walletLocation) {
        return "jdbc:oracle:thin:@" + dbName + "_high?TNS_ADMIN=" + walletLocation;
    }

    private static String requiredEnvironmentValue(String name, String testClassName) {
        String value = configuredEnvironmentValue(name);
        if (value == null) {
            LOG.warn("SKIPPED {}: missing {}", testClassName, name);
            assumeTrue(false, "Set " + name + " in the environment.");
        }
        return value;
    }

    private static String configuredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (!isConfiguredValue(value)) {
            return null;
        }
        return normalizeEnvironmentValue(name, value);
    }

    private ProfileAttributes profileAttributesFromEnvironment() {
        String provider = envOrDefault("SELECT_AI_IT_PROVIDER", DEFAULT_PROVIDER);
        ProfileAttributes.Builder builder = ProfileAttributes.builder()
                .credentialName(isolatedGenAiCredentialName)
                .provider(provider)
                .maxTokens(4096);

        if ("oci".equalsIgnoreCase(provider)) {
            builder.ociCompartmentId(required("SELECT_AI_IT_OCI_COMPARTMENT_ID"));
        }

        if (env("SELECT_AI_IT_REGION") != null) {
            builder.region(env("SELECT_AI_IT_REGION"));
        }
        if (env("SELECT_AI_IT_OCI_MODEL") != null) {
            builder.model(env("SELECT_AI_IT_OCI_MODEL"));
        }

        String objectList = profileObjectList();
        if (objectList != null) {
            builder.objectList(objectList).enforceObjectList(true);
        }
        builder.comments(false).constraints(true);
        return builder.build();
    }

    private String required(String name) {
        String value = env(name);
        if (value == null) {
            logSkipped(name);
            assumeTrue(false, "Set " + name + " in the environment.");
        }
        return value;
    }

    private static String sqlIdentifier(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_$#]*")) {
            throw new IllegalArgumentException("Unsupported SQL identifier: " + value);
        }
        return value;
    }

    private static boolean isSqlIdentifier(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_$#]*");
    }

    private static boolean hasConfiguredEnvironment() {
        return System.getenv().entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith("SELECT_AI_IT_")
                        && isConfiguredValue(entry.getValue()));
    }

    private static boolean isConfiguredValue(String value) {
        return value != null && !value.isBlank() && !"<set>".equalsIgnoreCase(value);
    }

    private static String normalizeEnvironmentValue(String key, String value) {
        if ("SELECT_AI_IT_OCI_PRIVATE_KEY".equals(key)) {
            return value.replace("\\n", "\n").replace("\\r", "\r");
        }
        return value;
    }

}
