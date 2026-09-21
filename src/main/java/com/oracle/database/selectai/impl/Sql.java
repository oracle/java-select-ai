/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

/**
 * SQL and PL/SQL templates used by the default SDK implementations.
 */
public enum Sql {
    // Credential operations.
    CREATE_USERNAME_PASSWORD_CREDENTIAL(
            "BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD.CREATE_CREDENTIAL(?, ?, ?); END;"),
    CREATE_OCI_KEY_CREDENTIAL(
            "BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD.CREATE_CREDENTIAL(?, ?, ?, ?, ?); END;"),
    DROP_CREDENTIAL(
            "BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD.DROP_CREDENTIAL(?); END;"),
    CREDENTIAL_EXISTS(
            "SELECT COUNT(*) FROM SYS.USER_CREDENTIALS WHERE CREDENTIAL_NAME = UPPER(?)"),

    // Profile, generation, summarization, data-access, and admin support statements.
    LIST_PROFILES("SELECT * FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILES ORDER BY profile_name"),
    LIST_PROFILES_BY_PATTERN("SELECT * FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILES WHERE REGEXP_LIKE(profile_name, ?, 'i') ORDER BY profile_name"),
    GET_PROFILE("SELECT profile_name, status, description FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILES WHERE profile_name = ?"),
    CREATE_PROFILE("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.CREATE_PROFILE(?, ?, ?, ?); END;"),
    DROP_PROFILE("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.DROP_PROFILE(     profile_name => ?,      force        => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END  ); END;"),
    ENABLE_PROFILE("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.ENABLE_PROFILE(?); END;"),
    DISABLE_PROFILE("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.DISABLE_PROFILE(?); END;"),
    GENERATE("BEGIN   ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI.GENERATE( prompt => ?, profile_name => ?, action => ?, attributes => ?, params => ? ); END;"),
    SET_ATTRIBUTE("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.SET_ATTRIBUTE(      profile_name    => ?,      attribute_name  => ?,      attribute_value => ?  ); END;"),
    SET_ATTRIBUTES("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.SET_ATTRIBUTES(      profile_name => ?,      attributes   => ?  ); END;"),
    TRANSLATE("BEGIN   ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI.TRANSLATE(?, ?, ?, ?); END;"),
    ENABLE_DATA_ACCESS("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.ENABLE_DATA_ACCESS(); END;"),
    DISABLE_DATA_ACCESS("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.DISABLE_DATA_ACCESS(); END;"),
    GET_PROFILE_ATTRIBUTES("SELECT attribute_name, attribute_value " +
            "FROM C##CLOUD$SERVICE.USER_CLOUD_AI_PROFILE_ATTRIBUTES " +
            "WHERE profile_name = ?"),
    REVOKE_SELECT_AI_PACKAGE_PRIVILEGES("DECLARE\n" +
            "    TYPE array_t IS VARRAY(4) OF VARCHAR2(60);\n" +
            "    v_packages array_t;\n" +
            "    v_user VARCHAR2(261);\n" +
            "BEGIN\n" +
            "    v_user := SYS.DBMS_ASSERT.ENQUOTE_NAME(\n" +
            "        SYS.DBMS_ASSERT.SCHEMA_NAME(:user),\n" +
            "        FALSE\n" +
            "    );\n" +
            "    v_packages := array_t(\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD',\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD_AI',\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT',\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD_PIPELINE'\n" +
            "    );\n" +
            "    FOR i in 1..v_packages.count LOOP\n" +
            "        EXECUTE IMMEDIATE\n" +
            "            'REVOKE EXECUTE ON ' || v_packages(i) || ' FROM ' || v_user;\n" +
            "    END LOOP;\n" +
            "END;"),
    REVOKE_NETWORK_ACCESS_ACL("BEGIN\n" +
            "    SYS.DBMS_NETWORK_ACL_ADMIN.REMOVE_HOST_ACE(\n" +
            "        host => ?,\n" +
            "        lower_port => ?,\n" +
            "        upper_port => ?,\n" +
            "        ace  => SYS.XS$ACE_TYPE(\n" +
            "            privilege_list => SYS.XS$NAME_LIST(%s),\n" +
            "            principal_name => ?,\n" +
            "            principal_type => SYS.XS_ACL.PTYPE_DB\n" +
            "        )\n" +
            "    );\n" +
            "END;"),
    GRANT_SELECT_AI_PACKAGE_PRIVILEGES("DECLARE\n" +
            "    TYPE array_t IS VARRAY(4) OF VARCHAR2(60);\n" +
            "    v_packages array_t;\n" +
            "    v_user VARCHAR2(261);\n" +
            "BEGIN\n" +
            "    v_user := SYS.DBMS_ASSERT.ENQUOTE_NAME(\n" +
            "        SYS.DBMS_ASSERT.SCHEMA_NAME(:user),\n" +
            "        FALSE\n" +
            "    );\n" +
            "    v_packages := array_t(\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD',\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD_AI',\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT',\n" +
            "        'C##CLOUD$SERVICE.DBMS_CLOUD_PIPELINE'\n" +
            "    );\n" +
            "    FOR i in 1..v_packages.count LOOP\n" +
            "        EXECUTE IMMEDIATE\n" +
            "            'GRANT EXECUTE ON ' || v_packages(i) || ' TO ' || v_user;\n" +
            "    END LOOP;\n" +
            "END;"),
    GRANT_NETWORK_ACCESS_ACL("BEGIN\n" +
            "    SYS.DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(\n" +
            "        host => ?,\n" +
            "        lower_port => ?,\n" +
            "        upper_port => ?,\n" +
            "        ace  => SYS.XS$ACE_TYPE(\n" +
            "            privilege_list => SYS.XS$NAME_LIST(%s),\n" +
            "            principal_name => ?,\n" +
            "            principal_type => SYS.XS_ACL.PTYPE_DB\n" +
            "        )\n" +
            "    );\n" +
            "END;"),
    SUMMARIZE("BEGIN   ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI.SUMMARIZE(         content       => ?,          profile_name  => ?,          user_prompt   => ?,          params        => ?  ); END;"),
    SUMMARIZE_LOCATION("BEGIN   ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI.SUMMARIZE(         location_uri    => ?,          credential_name => ?,          profile_name     => ?,          user_prompt      => ?,          params           => ?  ); END;"),
    FEEDBACK_SQL_ID("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI.FEEDBACK( profile_name => ?, sql_id => ?, feedback_type => ?, response => ?, feedback_content => ?, operation => ? ); END;"),
    FEEDBACK_SQL_TEXT("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI.FEEDBACK( profile_name => ?, sql_text => ?, feedback_type => ?, response => ?, feedback_content => ?, operation => ? ); END;"),
    GENERATE_SYNTHETIC_DATA_SINGLE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA( profile_name => ?, object_name => ?, owner_name => ?, record_count => ?, user_prompt => ?, params => ? ); END;"),
    GENERATE_SYNTHETIC_DATA_MULTI("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI.GENERATE_SYNTHETIC_DATA( profile_name => ?, object_list => ?, params => ? ); END;"),

    // Vector index operations.
    DROP_VECTOR_INDEX("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.DROP_VECTOR_INDEX(     index_name => ?,      include_data => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END,      force        => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END  ); END;"),
    ENABLE_VECTOR_INDEX("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.ENABLE_VECTOR_INDEX(index_name => ?); END;"),
    DISABLE_VECTOR_INDEX("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.DISABLE_VECTOR_INDEX(index_name => ?); END;"),
    UPDATE_VECTOR_INDEX_ATTRIBUTES("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX(     index_name => ?,      attributes => ?  ); END;"),
    UPDATE_VECTOR_INDEX_ATTRIBUTE("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.UPDATE_VECTOR_INDEX(     index_name      => ?,      attribute_name  => ?,      attribute_value => ?  ); END;"),
    LIST_VECTOR_INDEXES("SELECT v.index_name, v.status, v.description FROM C##CLOUD$SERVICE.USER_CLOUD_VECTOR_INDEXES v WHERE REGEXP_LIKE(v.index_name, ?, 'i')"),
    CREATE_VECTOR_INDEX("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.CREATE_VECTOR_INDEX(     index_name => ?,      attributes => ?,      status => ?,      description => ?,      wait_for_completion => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END  ); END;"),
    GET_VECTOR_INDEX("SELECT v.index_name, v.status, v.description FROM C##CLOUD$SERVICE.USER_CLOUD_VECTOR_INDEXES v WHERE UPPER(v.index_name) = UPPER(?)"),
    GET_VECTOR_INDEX_ATTRIBUTES("SELECT attribute_name, attribute_value FROM C##CLOUD$SERVICE.USER_CLOUD_VECTOR_INDEX_ATTRIBUTES WHERE UPPER(index_name) = UPPER(?)"),

    // Conversation operations.
    DROP_CONVERSATION("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.DROP_CONVERSATION(     conversation_id => ?,      force           => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END  ); END;"),
    UPDATE_CONVERSATION("BEGIN   C##CLOUD$SERVICE.DBMS_CLOUD_AI.UPDATE_CONVERSATION(     conversation_id => ?,      attributes      => ?  ); END;"),
    LIST_CONVERSATIONS("SELECT conversation_id, conversation_title, description, retention_days, conversation_length FROM C##CLOUD$SERVICE.USER_CLOUD_AI_CONVERSATIONS ORDER BY conversation_id"),
    CREATE_CONVERSATION("BEGIN   ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI.CREATE_CONVERSATION(attributes => ?); END;"),
    GET_CONVERSATION("SELECT conversation_id, conversation_title, description, retention_days, conversation_length FROM C##CLOUD$SERVICE.USER_CLOUD_AI_CONVERSATIONS WHERE conversation_id = ?"),
    LIST_CONVERSATION_PROMPTS("SELECT conversation_prompt_id, conversation_id, conversation_title, profile_name, prompt_action, prompt, prompt_response, created, modified, client_identifier, client_ip, sid, serial# FROM C##CLOUD$SERVICE.USER_CLOUD_AI_CONVERSATION_PROMPTS WHERE conversation_id = ? ORDER BY created"),
    DELETE_CONVERSATION_PROMPT("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI.DELETE_CONVERSATION_PROMPT( conversation_prompt_id => ?, force => CASE WHEN ? = 1 THEN TRUE ELSE FALSE END ); END;");

    /** SQL/PLSQL template bound to this enum constant. */
    private final String statement;

    /**
     * Creates a SQL enum constant with its SQL/PLSQL template text.
     *
     * @param statement SQL/PLSQL template text
     */
    Sql(String statement) {
        this.statement = statement;
    }

    /**
     * Returns SQL/PLSQL template text for the enum constant.
     *
     * @return SQL/PLSQL template text
     */
    public String get() {
        return statement;
    }

    /**
     * Formats SQL template with supplied arguments.
     *
     * @param args format arguments
     * @return formatted SQL/PLSQL template text
     */
    public String format(Object... args) {
        return String.format(statement, args);
    }
}
