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
                .isEqualTo("SELECT * FROM user_cloud_ai_profiles ORDER BY profile_name");
    }

    /**
     * Test: Apply arguments to SQL template placeholders.
     * Expected: The formatted network ACL SQL contains the supplied privileges.
     */
    @Test
    void formatAppliesArgumentsToTemplatePlaceholders() {
        assertThat(Sql.GRANT_NETWORK_ACCESS_ACL.format("'http', 'http_proxy'"))
                .contains("privilege_list => xs$name_list('http', 'http_proxy')");
    }

    /**
     * Test: Expose the named JDBC user bind in the package privilege statement.
     * Expected: The template uses :user and does not contain the old hard-coded user.
     */
    @Test
    void grantPackagePrivilegesUsesNamedJdbcUserBind() {
        assertThat(Sql.GRANT_SELECT_AI_PACKAGE_PRIVILEGES.get())
                .contains(":user")
                .contains("DBMS_ASSERT.SCHEMA_NAME(:user)")
                .doesNotContain("SELECTAI_USER");
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
                .contains("DBMS_CLOUD_AI.CREATE_VECTOR_INDEX")
                .contains("index_name => ?")
                .contains("wait_for_completion => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END");
    }
}
