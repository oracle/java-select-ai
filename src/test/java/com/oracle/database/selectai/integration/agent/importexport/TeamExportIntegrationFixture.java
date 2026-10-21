/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.agent.importexport;

import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.agent.Team;
import com.oracle.database.selectai.integration.agent.AgentIntegrationFixture;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.agent.AgentObjectType;
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Owns the credentials, directory, and files used by live team-export tests. */
abstract class TeamExportIntegrationFixture extends AgentIntegrationFixture {
    protected enum ExportDestination {
        CLOB, OBJECT_STORAGE, DIRECTORY
    }

    protected record ExportTarget(ExportDestination destination, String location,
                                  String credentialName, String fileName) {
        ExportTeamRequest request(Boolean overwrite) {
            ExportTeamRequest.Builder builder = ExportTeamRequest.builder(location)
                    .objectStorageCredentialName(credentialName);
            if (overwrite != null) {
                builder.params(ExportTeamParams.builder().overwrite(overwrite).build());
            }
            return builder.build();
        }
    }

    protected record ExecutionProbe(String functionName, String tableName, String expectedResult) { }

    private record ImportedComponent(AgentObjectType type, String name) { }

    private final List<ExportTarget> managedTargets = new ArrayList<>();
    private final List<String> importedTeamNames = new ArrayList<>();
    private final List<ImportedComponent> importedComponents = new ArrayList<>();
    private final List<String> importedProfileNames = new ArrayList<>();
    private String objectStorageCredentialName;
    private String directoryName;
    private Path physicalDirectory;
    private SelectAI directoryAdmin;
    private boolean directoryCreated;

    protected final ExportTarget createExportTarget(ExportDestination destination)
            throws Exception {
        String fileName = "team_" + UUID.randomUUID().toString().replace("-", "") + ".json";
        ExportTarget target;
        if (destination == ExportDestination.OBJECT_STORAGE) {
            String prefix = requiredFeatureValue("SELECT_AI_IT_AGENT_TEAM_EXPORT_LOCATION",
                    "Object Storage export tests require a writable object URI prefix");
            String location = prefix + (prefix.endsWith("/") ? "" : "/") + fileName;
            target = new ExportTarget(destination, location, exportCredentialName(), fileName);
        } else if (destination == ExportDestination.DIRECTORY) {
            target = new ExportTarget(destination, exportDirectoryName() + ":" + fileName,
                    null, fileName);
        } else {
            throw new IllegalArgumentException("CLOB export does not have a file target");
        }
        // Register before writing, including a file partially written by a failing export.
        managedTargets.add(target);
        return target;
    }

    protected final String exportAndRead(Team team, ExportDestination destination)
            throws Exception {
        if (destination == ExportDestination.CLOB) {
            return team.exportDefinition();
        }
        ExportTarget target = createExportTarget(destination);
        assertThat(team.exportDefinition(target.request(null))).isTrue();
        return readExport(target);
    }

    protected final Team importExportedDefinition(String specification) throws Exception {
        String name = uniqueName("EXP_IMPORTED_TEAM");
        return importExportedDefinition(ImportTeamRequest.builder(profileName, name)
                .specification(specification).force(true).build());
    }

    protected final Team importExportedDefinition(ImportTeamRequest request) throws Exception {
        // Track the name before importing so a partial import is cleaned up too.
        importedTeamNames.add(request.getTeamName());
        assertThat(agentClient.importTeam(request)).isTrue();
        return agentClient.team(request.getTeamName());
    }

    protected final void trackImportedComponents(String agentName, String taskName, String toolName) {
        trackImportedComponent(AgentObjectType.AGENT, agentName);
        trackImportedComponent(AgentObjectType.TASK, taskName);
        trackImportedComponent(AgentObjectType.TOOL, toolName);
    }

    /** Tracks explicit or generated imported components, including partial imports. */
    protected final void trackImportedComponent(AgentObjectType type, String name) {
        ImportedComponent component = new ImportedComponent(type, name);
        if (!importedComponents.contains(component)) {
            importedComponents.add(component);
        }
    }

    /** Tracks an AI profile created by IMPORT_TEAM from exported LLM metadata. */
    protected final void trackImportedProfile(String name) {
        if (!importedProfileNames.contains(name)) {
            importedProfileNames.add(name);
        }
    }

    /** The marker is available only through the function result, never in the LLM prompt. */
    protected final ExecutionProbe createExecutionProbe() throws Exception {
        String tableName = uniqueName("EXP_AUDIT");
        String functionName = uniqueName("EXP_FUNC");
        String marker = "EXPORT_ROUND_TRIP_" + UUID.randomUUID().toString().replace("-", "");
        createManagedTable(tableName, "CREATE TABLE " + tableName
                + " (marker VARCHAR2(96) NOT NULL, request_text VARCHAR2(2000))");
        createManagedPlsqlFunction(functionName, """
                CREATE OR REPLACE FUNCTION %s(p_request VARCHAR2) RETURN CLOB AUTHID DEFINER IS
                    PRAGMA AUTONOMOUS_TRANSACTION;
                BEGIN
                    INSERT INTO %s(marker, request_text) VALUES ('%s', p_request);
                    COMMIT;
                    RETURN TO_CLOB('%s');
                END;
                """.formatted(functionName, tableName, marker, marker));
        return new ExecutionProbe(functionName, tableName, marker);
    }

    protected final long executionProbeCalls(ExecutionProbe probe) throws Exception {
        try (PreparedStatement statement = jdbcConnection().prepareStatement(
                "SELECT COUNT(*) FROM " + probe.tableName() + " WHERE marker = ? AND request_text = ?")) {
            statement.setString(1, probe.expectedResult());
            statement.setString(2, "roundtrip-check");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    protected final String exportCredentialName() throws Exception {
        if (objectStorageCredentialName == null) {
            String name = uniqueName("EXP_CRED");
            createManagedCredential(CredentialConfig.ociSigningKey(name,
                    requiredFeatureValue("SELECT_AI_IT_OCI_USER_OCID", "OCI user OCID is required"),
                    requiredFeatureValue("SELECT_AI_IT_OCI_TENANCY_OCID", "OCI tenancy OCID is required"),
                    requiredFeatureValue("SELECT_AI_IT_OCI_PRIVATE_KEY", "OCI private key is required"),
                    requiredFeatureValue("SELECT_AI_IT_OCI_FINGERPRINT", "OCI key fingerprint is required")));
            objectStorageCredentialName = name;
        }
        return objectStorageCredentialName;
    }

    protected final String exportDirectoryName() throws Exception {
        if (!directoryCreated) {
            String base = requiredFeatureValue("SELECT_AI_IT_AGENT_TEAM_EXPORT_DIRECTORY_BASE_PATH",
                    "Directory export tests require a database-accessible filesystem base path");
            Path basePath = Files.createDirectories(Path.of(base).toAbsolutePath().normalize());
            physicalDirectory = Files.createTempDirectory(basePath, "run_");
            if (Files.getFileStore(physicalDirectory).supportsFileAttributeView("posix")) {
                // The database may run as a different OS user. Only this newly created,
                // isolated folder is shared; configured parent permissions are untouched.
                Files.setPosixFilePermissions(physicalDirectory,
                        PosixFilePermissions.fromString("rwxrwxrwx"));
            }
            directoryName = uniqueName("EXP_DIR");
            directoryAdmin = SelectAI.create(adminDbConfig());
            try (Statement statement = directoryAdmin.getConnection().createStatement()) {
                statement.executeUpdate("CREATE DIRECTORY " + directoryName + " AS '"
                        + physicalDirectory.toString().replace("'", "''") + "'");
                directoryCreated = true;
                String user = jdbcConnection().getMetaData().getUserName();
                statement.executeUpdate("GRANT READ, WRITE ON DIRECTORY " + directoryName
                        + " TO \"" + user.replace("\"", "\"\"") + "\"");
            }
        }
        return directoryName;
    }

    /** Reads through the database, independently of the SDK export implementation. */
    protected final String readExport(ExportTarget target) throws Exception {
        if (target.destination() == ExportDestination.OBJECT_STORAGE) {
            try (CallableStatement statement = jdbcConnection().prepareCall(
                    "BEGIN ? := DBMS_CLOUD.GET_OBJECT(credential_name => ?, object_uri => ?); END;")) {
                statement.registerOutParameter(1, Types.BLOB);
                statement.setString(2, target.credentialName());
                statement.setString(3, target.location());
                statement.execute();
                return readUtf8(statement.getBlob(1));
            }
        }
        try (CallableStatement statement = jdbcConnection().prepareCall("""
                DECLARE
                    l_file BFILE := BFILENAME(?, ?);
                    l_content BLOB;
                BEGIN
                    DBMS_LOB.CREATETEMPORARY(l_content, TRUE);
                    DBMS_LOB.FILEOPEN(l_file, DBMS_LOB.FILE_READONLY);
                    DBMS_LOB.LOADFROMFILE(l_content, l_file, DBMS_LOB.GETLENGTH(l_file));
                    DBMS_LOB.FILECLOSE(l_file);
                    ? := l_content;
                EXCEPTION WHEN OTHERS THEN
                    IF DBMS_LOB.FILEISOPEN(l_file) = 1 THEN
                        DBMS_LOB.FILECLOSE(l_file);
                    END IF;
                    IF DBMS_LOB.ISTEMPORARY(l_content) = 1 THEN
                        DBMS_LOB.FREETEMPORARY(l_content);
                    END IF;
                    RAISE;
                END;
                """)) {
            statement.setString(1, directoryName);
            statement.setString(2, target.fileName());
            statement.registerOutParameter(3, Types.BLOB);
            statement.execute();
            return readUtf8(statement.getBlob(3));
        }
    }

    /** Writes deliberately invalid import content only to a registered, unique test target. */
    protected final void writeImportContent(ExportTarget target, String content) throws Exception {
        assertThat(managedTargets).contains(target);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String sql = target.destination() == ExportDestination.OBJECT_STORAGE
                ? "BEGIN DBMS_CLOUD.PUT_OBJECT(credential_name => ?, object_uri => ?, contents => ?); END;"
                : """
                    DECLARE
                        l_file UTL_FILE.FILE_TYPE;
                        l_content BLOB := ?;
                        l_offset PLS_INTEGER := 1;
                    BEGIN
                        l_file := UTL_FILE.FOPEN(?, ?, 'wb', 32767);
                        WHILE l_offset <= DBMS_LOB.GETLENGTH(l_content) LOOP
                            UTL_FILE.PUT_RAW(l_file, DBMS_LOB.SUBSTR(l_content, 32767, l_offset), TRUE);
                            l_offset := l_offset + 32767;
                        END LOOP;
                        UTL_FILE.FCLOSE(l_file);
                    EXCEPTION WHEN OTHERS THEN
                        IF UTL_FILE.IS_OPEN(l_file) THEN UTL_FILE.FCLOSE(l_file); END IF;
                        RAISE;
                    END;
                    """;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             CallableStatement statement = jdbcConnection().prepareCall(sql)) {
            if (target.destination() == ExportDestination.OBJECT_STORAGE) {
                statement.setString(1, target.credentialName());
                statement.setString(2, target.location());
                statement.setBlob(3, input, bytes.length);
            } else {
                statement.setBlob(1, input, bytes.length);
                statement.setString(2, directoryName);
                statement.setString(3, target.fileName());
            }
            statement.execute();
        }
    }

    /** Changes READ only on the directory created by this fixture; callers restore it in finally. */
    protected final void setExportDirectoryReadAccess(boolean granted) throws Exception {
        String directory = exportDirectoryName();
        String user = jdbcConnection().getMetaData().getUserName().replace("\"", "\"\"");
        String sql = granted ? "GRANT READ ON DIRECTORY " + directory + " TO \"" + user + "\""
                : "REVOKE READ ON DIRECTORY " + directory + " FROM \"" + user + "\"";
        try (Statement statement = directoryAdmin.getConnection().createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String readUtf8(Blob content) throws Exception {
        assertThat(content).as("exported file content").isNotNull();
        try (InputStream stream = content.getBinaryStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            content.free();
        }
    }

    // Subclass cleanup runs before AgentIntegrationFixture closes JDBC and drops credentials.
    @AfterEach
    final void cleanExportResources() throws Exception {
        Exception failure = null;
        for (String name : importedTeamNames) {
            try (CallableStatement statement = jdbcConnection().prepareCall("""
                    BEGIN
                        DBMS_CLOUD_AI_AGENT.DROP_TEAM(team_name => ?, force => TRUE);
                    END;
                    """)) {
                statement.setString(1, name);
                statement.execute();
            } catch (Exception exception) {
                failure = collectFailure(failure, exception);
            }
        }
        importedTeamNames.clear();
        for (ImportedComponent component : importedComponents) {
            try (CallableStatement statement = jdbcConnection().prepareCall(
                    "BEGIN DBMS_CLOUD_AI_AGENT.DROP_" + component.type().name() + "(?, TRUE); END;")) {
                statement.setString(1, component.name());
                statement.execute();
            } catch (Exception exception) {
                failure = collectFailure(failure, exception);
            }
        }
        importedComponents.clear();
        for (String name : importedProfileNames) {
            try {
                selectAI.profile(name).drop(true);
            } catch (Exception exception) {
                failure = collectFailure(failure, exception);
            }
        }
        importedProfileNames.clear();
        for (ExportTarget target : managedTargets) {
            try {
                deleteExport(target);
            } catch (Exception exception) {
                failure = collectFailure(failure, exception);
            }
        }
        managedTargets.clear();
        if (directoryCreated) {
            try (Statement statement = directoryAdmin.getConnection().createStatement()) {
                statement.executeUpdate("DROP DIRECTORY " + directoryName);
            } catch (Exception exception) {
                failure = collectFailure(failure, exception);
            }
        }
        if (directoryAdmin != null) {
            try {
                directoryAdmin.close();
            } catch (Exception exception) {
                failure = collectFailure(failure, exception);
            }
        }
        if (physicalDirectory != null) {
            try {
                // Delete only the unique test folder, and only after its files are removed.
                Files.deleteIfExists(physicalDirectory);
            } catch (Exception exception) {
                failure = collectFailure(failure, exception);
            }
        }
        objectStorageCredentialName = null;
        directoryName = null;
        physicalDirectory = null;
        directoryAdmin = null;
        directoryCreated = false;
        if (failure != null) {
            throw failure;
        }
    }

    private void deleteExport(ExportTarget target) throws Exception {
        if (target.destination() == ExportDestination.OBJECT_STORAGE) {
            try (CallableStatement statement = jdbcConnection().prepareCall("""
                    BEGIN
                        DBMS_CLOUD.DELETE_OBJECT(credential_name => ?, object_uri => ?, force => TRUE);
                    END;
                    """)) {
                statement.setString(1, target.credentialName());
                statement.setString(2, target.location());
                statement.execute();
            }
        } else {
            try (CallableStatement statement = jdbcConnection().prepareCall("""
                    BEGIN
                        IF DBMS_LOB.FILEEXISTS(BFILENAME(?, ?)) = 1 THEN
                            UTL_FILE.FREMOVE(?, ?);
                        END IF;
                    END;
                    """)) {
                statement.setString(1, directoryName);
                statement.setString(2, target.fileName());
                statement.setString(3, directoryName);
                statement.setString(4, target.fileName());
                statement.execute();
            }
        }
    }

    private static Exception collectFailure(Exception failure, Exception exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }
}
