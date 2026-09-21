/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlTest {

    /**
     * Test: Read the SQL template associated with a statement enum constant.
     * Expected: The exact profile-list SQL template is returned.
     */
    @Test
    void getReturnsSqlTemplateForEnumConstant() {
        assertThat(Sql.LIST_PROFILES.get())
                .isEqualTo("SELECT * FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILES ORDER BY profile_name");
    }

    /**
     * Test: Apply arguments to SQL template placeholders.
     * Expected: The formatted network ACL SQL contains the supplied privileges.
     */
    @Test
    void formatAppliesArgumentsToTemplatePlaceholders() {
        assertThat(Sql.GRANT_NETWORK_ACCESS_ACL.format("'http', 'http_proxy'"))
                .contains("privilege_list => SYS.XS$NAME_LIST('http', 'http_proxy')");
    }

    /**
     * Test: Expose the named JDBC user bind in the package privilege statement.
     * Expected: The template uses :user and does not contain the old hard-coded user.
     */
    @Test
    void grantPackagePrivilegesUsesNamedJdbcUserBind() {
        assertThat(Sql.GRANT_SELECT_AI_PACKAGE_PRIVILEGES.get())
                .contains(":user")
                .contains("SYS.DBMS_ASSERT.SCHEMA_NAME(:user)")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD'")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD_AI'")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT'")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD_PIPELINE'")
                .doesNotContain("SELECTAI_USER");

        assertThat(Sql.REVOKE_SELECT_AI_PACKAGE_PRIVILEGES.get())
                .contains("SYS.DBMS_ASSERT.SCHEMA_NAME(:user)")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD'")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD_AI'")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT'")
                .contains("'C##CLOUD$SERVICE.DBMS_CLOUD_PIPELINE'");
    }

    /**
     * Test: Expose all bind placeholders required by feedback statements.
     * Expected: Both feedback templates contain SQL ID or text, type, and operation binds.
     */
    @Test
    void feedbackTemplatesExposeExpectedBindPlaceholders() {
        assertThat(Sql.FEEDBACK_SQL_ID.get())
                .contains("sql_id => ?")
                .contains("feedback_type => ?")
                .contains("operation => ?");

        assertThat(Sql.FEEDBACK_SQL_TEXT.get())
                .contains("sql_text => ?")
                .contains("feedback_type => ?")
                .contains("operation => ?");
    }

    /**
     * Test: Expose all bind placeholders required by synthetic-data procedures.
     * Expected: Single-object and multi-object templates contain every documented argument.
     */
    @Test
    void syntheticDataTemplatesExposeExpectedBindPlaceholders() {
        assertThat(Sql.GENERATE_SYNTHETIC_DATA_SINGLE.get())
                .contains("DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA")
                .contains("profile_name => ?")
                .contains("object_name => ?")
                .contains("owner_name => ?")
                .contains("record_count => ?")
                .contains("user_prompt => ?")
                .contains("params => ?");

        assertThat(Sql.GENERATE_SYNTHETIC_DATA_MULTI.get())
                .contains("DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA")
                .contains("profile_name => ?")
                .contains("object_list => ?")
                .contains("params => ?");
    }

    /**
     * Test: Expose the owned statement text for vector-index creation.
     * Expected: The template contains the procedure call, index-name bind, and wait conversion.
     */
    @Test
    void vectorIndexTemplatesExposeOwnedStatementText() {
        assertThat(Sql.CREATE_VECTOR_INDEX.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI.CREATE_VECTOR_INDEX")
                .contains("index_name => ?")
                .contains("wait_for_completion => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END");
    }

    /**
     * Test: Verify that ACL templates use the owners of the Oracle packages and types.
     * Expected: ACL administration, assertion, and XS type references are fully qualified.
     */
    @Test
    void aclTemplatesUseQualifiedOracleObjects() {
        assertThat(Sql.GRANT_NETWORK_ACCESS_ACL.get())
                .contains("SYS.DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE")
                .contains("SYS.XS$ACE_TYPE")
                .contains("SYS.XS$NAME_LIST")
                .contains("SYS.XS_ACL.PTYPE_DB");

        assertThat(Sql.REVOKE_NETWORK_ACCESS_ACL.get())
                .contains("SYS.DBMS_NETWORK_ACL_ADMIN.REMOVE_HOST_ACE")
                .contains("SYS.XS$ACE_TYPE")
                .contains("SYS.XS$NAME_LIST")
                .contains("SYS.XS_ACL.PTYPE_DB");
    }

    /**
     * Test: Verify that Select AI metadata views use their owning schema.
     * Expected: Vector-index and conversation metadata templates reference C##CLOUD$SERVICE.
     */
    @Test
    void metadataTemplatesUseQualifiedCloudServiceViews() {
        assertThat(Sql.LIST_PROFILES_BY_PATTERN.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILES");
        assertThat(Sql.GET_PROFILE.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILES");
        assertThat(Sql.LIST_VECTOR_INDEXES.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_VECTOR_INDEXES");
        assertThat(Sql.GET_VECTOR_INDEX.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_VECTOR_INDEXES");
        assertThat(Sql.GET_VECTOR_INDEX_ATTRIBUTES.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_VECTOR_INDEX_ATTRIBUTES");
        assertThat(Sql.LIST_CONVERSATIONS.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_AI_CONVERSATIONS");
        assertThat(Sql.GET_CONVERSATION.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_AI_CONVERSATIONS");
        assertThat(Sql.LIST_CONVERSATION_PROMPTS.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_AI_CONVERSATION_PROMPTS");
        assertThat(Sql.GET_PROFILE_ATTRIBUTES.get())
                .contains("FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILE_ATTRIBUTES");
    }

    /**
     * Test: Verify credential templates use the owning database schemas.
     * Expected: DBMS_CLOUD uses C##CLOUD$SERVICE and USER_CREDENTIALS uses SYS.
     */
    @Test
    void credentialTemplatesUseQualifiedOracleObjects() {
        assertThat(Sql.CREATE_USERNAME_PASSWORD_CREDENTIAL.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD.CREATE_CREDENTIAL");
        assertThat(Sql.CREATE_OCI_KEY_CREDENTIAL.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD.CREATE_CREDENTIAL");
        assertThat(Sql.DROP_CREDENTIAL.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD.DROP_CREDENTIAL");
        assertThat(Sql.CREDENTIAL_EXISTS.get())
                .contains("FROM SYS.USER_CREDENTIALS");
    }

    /**
     * Test: Verify every Select AI package template uses the owning schema.
     * Expected: Each template that invokes DBMS_CLOUD_AI uses the fully qualified package name.
     */
    @Test
    void selectAiPackageTemplatesUseQualifiedPackageName() {
        for (Sql sql : new Sql[]{
                Sql.CREATE_PROFILE,
                Sql.DROP_PROFILE,
                Sql.ENABLE_PROFILE,
                Sql.DISABLE_PROFILE,
                Sql.GENERATE,
                Sql.SET_ATTRIBUTE,
                Sql.SET_ATTRIBUTES,
                Sql.TRANSLATE,
                Sql.ENABLE_DATA_ACCESS,
                Sql.DISABLE_DATA_ACCESS,
                Sql.SUMMARIZE,
                Sql.SUMMARIZE_LOCATION,
                Sql.FEEDBACK_SQL_ID,
                Sql.FEEDBACK_SQL_TEXT,
                Sql.GENERATE_SYNTHETIC_DATA_SINGLE,
                Sql.GENERATE_SYNTHETIC_DATA_MULTI,
                Sql.DROP_VECTOR_INDEX,
                Sql.ENABLE_VECTOR_INDEX,
                Sql.DISABLE_VECTOR_INDEX,
                Sql.UPDATE_VECTOR_INDEX_ATTRIBUTES,
                Sql.UPDATE_VECTOR_INDEX_ATTRIBUTE,
                Sql.CREATE_VECTOR_INDEX,
                Sql.DROP_CONVERSATION,
                Sql.UPDATE_CONVERSATION,
                Sql.CREATE_CONVERSATION,
                Sql.DELETE_CONVERSATION_PROMPT}) {
            assertThat(sql.get())
                    .as("SQL template %s", sql.name())
                    .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI.");
        }
    }
}
