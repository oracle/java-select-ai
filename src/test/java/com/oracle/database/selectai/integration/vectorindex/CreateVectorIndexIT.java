/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

/**
 * Live integration coverage for vector-index operations.
 *
 * <p>The suite uses isolated indexes and credentials from the shared integration
 * fixture. Required vector-index credentials are supplied through the
 * SELECT_AI_IT_* configuration keys.</p>
 *
 * <p>The suite verifies creation, update, metadata, listing, lifecycle, and
 * Java-specific resource behavior through the public synchronous SDK APIs.</p>
 */
package com.oracle.database.selectai.integration.vectorindex;

import com.oracle.database.selectai.Credential;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.CredentialConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.VectorIndexAttributes;
import com.oracle.database.selectai.model.VectorIndexConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integration coverage for CreateVectorIndex. */
class CreateVectorIndexIT extends VectorIndexIntegrationFixture {

    /**
     * Test: Build an enabled vector index with the shared embedding location, match limit 1,
     * object-storage credential, isolated profile, and Oracle vector database provider, then
     * call VectorIndex.create().
     * Expected: create() returns true and a pattern lookup for the generated name returns the
     * newly created index.
     */
    @Test
    void test50000SuccessfulVectorIndexCreation() throws Exception {
        String name = uniqueIndexName("CREATE_5001");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(createForIntegration(index)).isTrue();
        assertIndexPresent(name);
    }

    /**
     * Test: Read getStatus() and getVectorIndexAttributes() from an index configured but not yet
     * created, then create it and read the same metadata from a database-backed instance.
     * Expected: Before creation, status is Enabled and the configured location, credential name,
     * profile name, and vector database provider are returned without a database lookup; after
     * creation, the reloaded index exposes those same persisted values.
     */
    @Test
    void test50001PendingVectorIndexGettersPreserveConfigurationBeforeCreate() throws Exception {
        String name = uniqueIndexName("PENDING_GETTERS");
        VectorIndex pending = createConfiguredIndex(name);
        VectorIndexAttributes configuredAttributes = pending.getVectorIndexAttributes();

        assertThat(pending.getStatus())
                .isEqualToIgnoringCase(VectorIndexConfig.Status.ENABLED.getValue());
        assertThat(configuredAttributes.getLocation()).isEqualTo(embeddingLocation());
        assertThat(configuredAttributes.getObjectStorageCredentialName())
                .isEqualTo(objectStorageCredentialName);
        assertThat(configuredAttributes.getProfileName()).isEqualTo(profileName);
        assertThat(configuredAttributes.getVectorDbProvider()).isEqualTo("oracle");

        assertThat(pending.create()).isTrue();
        VectorIndex reloaded = selectAI.vectorIndex(name);
        assertThat(reloaded.getStatus())
                .isEqualToIgnoringCase(VectorIndexConfig.Status.ENABLED.getValue());
        assertThat(reloaded.getVectorIndexAttributes().getLocation())
                .isEqualTo(embeddingLocation());
        assertThat(reloaded.getVectorIndexAttributes().getObjectStorageCredentialName())
                .isEqualTo(objectStorageCredentialName);
        assertThat(reloaded.getVectorIndexAttributes().getProfileName()).isEqualTo(profileName);
        assertThat(reloaded.getVectorIndexAttributes().getVectorDbProvider()).isEqualTo("oracle");
    }

    /**
     * Test: Create an enabled index with the standard vector attributes and an empty description.
     * Expected: create() returns true and listing by the generated name finds the index, showing
     * that an empty description does not prevent database creation.
     */
    @Test
    void test50002CreateWithEmptyDescription() throws Exception {
        String name = uniqueIndexName("CREATE_5003");
        VectorIndex index = createConfiguredIndex(name, "");
        assertThat(createForIntegration(index)).isTrue();
        assertIndexPresent(name);
    }

    /**
     * Test: Create an index with the documented default status and description by omitting both
     * values from VectorIndexConfig.
     * Expected: the database creates the index as Disabled and retains a null description.
     */
    @Test
    void test50003CreateUsesDocumentedDefaultStatusAndDescription() throws Exception {
        String name = uniqueIndexName("CREATE_DEFAULTS");
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(requiredCreateAttributes())
                .build();
        VectorIndex index = selectAI.vectorIndex(config);

        assertThat(index.isWaitForCompletion()).isFalse();
        assertThat(index.create()).isTrue();

        VectorIndex loaded = selectAI.vectorIndex(name);
        assertThat(loaded.getStatus())
                .isEqualToIgnoringCase(VectorIndexConfig.Status.DISABLED.getValue());
        assertThat(loaded.getDescription()).isNull();
    }

    /**
     * Test: Create an index, drop it with includeData=true, and invoke create() again on the same
     * configured object and name.
     * Expected: Both create calls and the drop return true, and the second creation is visible in
     * the managed-index listing.
     */
    @Test
    void test50004DeleteAndRecreate() throws Exception {
        String name = uniqueIndexName("CREATE_5007");
        VectorIndex index = createConfiguredIndex(name);
        assertThat(index.create()).isTrue();
        assertThat(index.drop(true)).isTrue();
        assertThat(index.create()).isTrue();
        assertIndexPresent(name);
    }

    /**
     * Test: Attempt to create an index whose object-storage credential name is
     * JSAI_IT_MISSING_CREDENTIAL instead of the fixture's created credential.
     * Expected: create() throws SelectAIException and its database cause contains ORA-20004,
     * identifying the missing credential.
     */
    @Test
    void test50005CreateWithInvalidCredential() throws Exception {
        VectorIndex index = createConfiguredIndexWithCredential(uniqueIndexName("CREATE_5008"),
                "JSAI_IT_MISSING_CREDENTIAL");
        assertThatThrownBy(() -> index.create())
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20004:"));
    }

    /**
     * Test: Attempt to create an index with location "invalid_location" while retaining the
     * valid fixture credential, profile, match limit 1, and Oracle provider.
     * Expected: create() throws SelectAIException and the database cause contains ORA-20006 for
     * the invalid embedding location.
     */
    @Test
    void test50006CreateWithInvalidLocation() throws Exception {
        String name = uniqueIndexName("CREATE_5009");
        VectorIndex index = createConfiguredIndexWithLocation(name, "invalid_location");
        assertThatThrownBy(() -> index.create())
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20006:"));
    }

    /**
     * Test: Omit each documented mandatory create attribute while keeping the other required
     * attributes valid.
     * Expected: DBMS_CLOUD_AI rejects every incomplete create payload.
     */
    @ParameterizedTest(name = "missing {0}")
    @MethodSource("requiredCreateAttributeNames")
    void test50007CreateWithoutRequiredAttributeIsRejected(String missingAttribute) throws Exception {
        String name = uniqueIndexName("CREATE_MISSING_" + missingAttribute.toUpperCase(Locale.ROOT));
        VectorIndexAttributes.Builder attributes = VectorIndexAttributes.builder()
                .location(embeddingLocation())
                .objectStorageCredentialName(objectStorageCredentialName)
                .profileName(profileName)
                .vectorDbProvider("oracle");

        switch (missingAttribute) {
            case "location" -> attributes.location(null);
            case "object_storage_credential_name" -> attributes.objectStorageCredentialName(null);
            case "profile_name" -> attributes.profileName(null);
            default -> throw new IllegalArgumentException("Unknown required attribute: " + missingAttribute);
        }

        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(attributes.build())
                .status(VectorIndexConfig.Status.ENABLED)
                .description("Missing required attribute")
                .waitForCompletion(true)
                .build();

        assertThatThrownBy(() -> selectAI.vectorIndex(config).create())
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Build VectorIndexConfig with a generated name but without VectorIndexAttributes and
     * call VectorIndex.create().
     * Expected: Java allows the documented nullable attributes parameter to reach
     * DBMS_CLOUD_AI. If the database rejects the missing create attributes, the SDK wraps the
     * database error in SelectAIException rather than throwing a Java validation error.
     */
    @Test
    void test50008CreateWithoutAttributesIsHandledByDatabase() throws Exception {
        String name = uniqueIndexName("CREATE_5010");
        VectorIndex index = selectAI.vectorIndex(VectorIndexConfig.builder(name)
                .build());
        assertThatThrownBy(() -> index.create()).isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Pass an empty string to VectorIndexConfig.builder(String) instead of a database
     * object name.
     * Expected: the Java builder throws IllegalArgumentException immediately and no index is
     * created.
     */
    @Test
    void test50009CreateWithEmptyNameIsRejectedByJavaBuilder() throws Exception {
        assertThatThrownBy(() -> VectorIndexConfig.builder(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test: Build a named VectorIndexConfig without vector index attributes.
     * Expected: Java permits the optional DBMS_CLOUD_AI.CREATE_VECTOR_INDEX attributes
     * parameter to remain null.
     */
    @Test
    void test50010CreateConfigAllowsNullAttributes() {
        String name = uniqueIndexName("CREATE_5015");
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(null)
                .build();

        assertThat(config.getIndexName()).isEqualTo(name);
        assertThat(config.getVectorIndexAttributes()).isNull();
    }

    /**
     * Test: Invoke both update overloads, enable(), disable(), and both drop overloads on a
     * configured VectorIndex before create() has been called.
     * Expected: Every database-dependent API throws IllegalStateException with its corresponding
     * "requires a created vector index" message, and no live update, lifecycle, or drop request is
     * attempted.
     */
    @Test
    void test50011VectorIndexRejectsDatabaseOperationsBeforeCreate() throws Exception {
        VectorIndex pendingIndex = createConfiguredIndex(uniqueIndexName("PENDING_CREATE"));

        assertThatThrownBy(() -> pendingIndex.update("match_limit", "10"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("update requires a created vector index; call create() first");
        assertThatThrownBy(() -> pendingIndex.update(VectorIndexAttributes.updateBuilder()
                .matchLimit(10)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("update requires a created vector index; call create() first");
        assertThatThrownBy(pendingIndex::enable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("enable requires a created vector index; call create() first");
        assertThatThrownBy(pendingIndex::disable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disable requires a created vector index; call create() first");
        assertThatThrownBy(() -> pendingIndex.drop(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created vector index; call create() first");
        assertThatThrownBy(() -> pendingIndex.drop(true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drop requires a created vector index; call create() first");
    }

    /**
     * Test: Submit a 150-character index name with otherwise valid attributes and enabled status
     * to VectorIndex.create().
     * Expected: Java passes the name to the database, which rejects it with SelectAIException
     * whose cause contains ORA-20008; Java does not silently truncate the name.
     */
    @Test
    void test50012CreateWithOverlongNameDelegatesToDatabase() throws Exception {
        String name = "X".repeat(150);
        managedIndexNames.add(name);
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(createAttributes())
                .status(VectorIndexConfig.Status.ENABLED)
                .description("Overlong vector index name")
                .waitForCompletion(true)
                .build();

        assertThatThrownBy(() -> selectAI.vectorIndex(config).create())
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20008:"));
    }

    /**
     * Test: Create an index whose name starts with a digit and therefore is not a valid unquoted
     * Oracle SQL identifier.
     * Expected: the database rejects the invalid identifier.
     */
    @Test
    void test50013CreateRejectsInvalidSqlIdentifier() throws Exception {
        String name = "1JSAI_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase(Locale.ROOT);
        managedIndexNames.add(name);
        VectorIndexConfig config = VectorIndexConfig.builder(name)
                .vectorIndexAttributes(requiredCreateAttributes())
                .status(VectorIndexConfig.Status.ENABLED)
                .description("Invalid SQL identifier")
                .waitForCompletion(true)
                .build();

        assertThatThrownBy(() -> selectAI.vectorIndex(config).create())
                .isInstanceOf(SelectAIException.class);
    }

    /**
     * Test: Submit a valid index configuration whose description contains 5,000 D characters.
     * Expected: the database rejects the oversized description with SelectAIException and an
     * ORA-20045 cause.
     */
    @Test
    void test50014CreateWithOverlongDescription() throws Exception {
        VectorIndex index = createConfiguredIndex(uniqueIndexName("CREATE_5015"), "D".repeat(5000));
        assertThatThrownBy(() -> index.create())
                .isInstanceOfSatisfying(SelectAIException.class,
                        exception -> assertThat(exception.getCause())
                                .hasMessageContaining("ORA-20045:"));
    }
}
