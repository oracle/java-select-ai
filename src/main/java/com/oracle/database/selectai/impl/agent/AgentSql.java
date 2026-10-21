/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

/**
 * SQL and PL/SQL templates for {@code DBMS_CLOUD_AI_AGENT}.
 * <p>
 * The metadata queries use the documented {@code USER_AI_*} views and the
 * package owner exposed by the certified Autonomous Database environments.
 * This class is internal; applications should use the public agent interfaces
 * rather than depend on these SQL templates or view names.
 */
enum AgentSql {
    /** Creates an agent. */
    CREATE_AGENT("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.CREATE_AGENT(?, ?, ?, ?); END;"),
    /** Enables an agent. */
    ENABLE_AGENT("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.ENABLE_AGENT(?); END;"),
    /** Disables an agent. */
    DISABLE_AGENT("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DISABLE_AGENT(?); END;"),
    /** Drops an agent. */
    DROP_AGENT("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DROP_AGENT(?, ?); END;"),
    /** Sets one agent attribute. */
    SET_AGENT_ATTRIBUTE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE(?, ?, ?, ?); END;"),
    /** Sets multiple agent attributes. */
    SET_AGENT_ATTRIBUTES("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES(?, ?, ?); END;"),
    /** Loads current metadata for an agent owned by the current user. */
    GET_AGENT("SELECT agent_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENTS WHERE agent_name = ?"),
    /** Loads current metadata for a task owned by the current user. */
    GET_TASK("SELECT task_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS WHERE task_name = ?"),
    /** Loads current metadata for a tool owned by the current user. */
    GET_TOOL("SELECT tool_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOLS WHERE tool_name = ?"),
    /** Loads current metadata for a team owned by the current user. */
    GET_TEAM("SELECT agent_team_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS WHERE agent_team_name = ?"),
    /** Loads only the current agent status. */
    GET_AGENT_STATUS("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENTS WHERE agent_name = ?"),
    /** Loads only the current task status. */
    GET_TASK_STATUS("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS WHERE task_name = ?"),
    /** Loads only the current tool status. */
    GET_TOOL_STATUS("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOLS WHERE tool_name = ?"),
    /** Loads only the current team status. */
    GET_TEAM_STATUS("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS WHERE agent_team_name = ?"),
    /** Loads attributes for an agent owned by the current user. */
    GET_AGENT_ATTRIBUTES(
            "SELECT attribute_name, attribute_value "
                    + "FROM C##CLOUD$SERVICE.USER_AI_AGENT_ATTRIBUTES "
                    + "WHERE UPPER(agent_name) = UPPER(?)"),
    /** Loads task attributes for a task owned by the current user. */
    GET_TASK_ATTRIBUTES(
            "SELECT attribute_name, attribute_value "
                    + "FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASK_ATTRIBUTES "
                    + "WHERE UPPER(task_name) = UPPER(?)"),
    /** Loads tool attributes for a tool owned by the current user. */
    GET_TOOL_ATTRIBUTES(
            "SELECT attribute_name, attribute_value "
                    + "FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOL_ATTRIBUTES "
                    + "WHERE UPPER(tool_name) = UPPER(?)"),
    /** Loads team attributes for a team owned by the current user. */
    GET_TEAM_ATTRIBUTES(
            "SELECT attribute_name, attribute_value "
                    + "FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAM_ATTRIBUTES "
                    + "WHERE UPPER(agent_team_name) = UPPER(?)"),

    /** Lists agents owned by the current user. */
    LIST_AGENTS("SELECT agent_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENTS ORDER BY agent_name"),
    /** Lists owned agents matching a database regular expression. */
    LIST_AGENTS_BY_PATTERN("SELECT agent_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENTS "
            + "WHERE REGEXP_LIKE(agent_name, ?, 'i') ORDER BY agent_name"),
    /** Lists tasks owned by the current user. */
    LIST_TASKS("SELECT task_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS ORDER BY task_name"),
    /** Lists owned tasks matching a database regular expression. */
    LIST_TASKS_BY_PATTERN("SELECT task_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS "
            + "WHERE REGEXP_LIKE(task_name, ?, 'i') ORDER BY task_name"),
    /** Lists tools owned by the current user. */
    LIST_TOOLS("SELECT tool_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOLS ORDER BY tool_name"),
    /** Lists owned tools matching a database regular expression. */
    LIST_TOOLS_BY_PATTERN("SELECT tool_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOLS "
            + "WHERE REGEXP_LIKE(tool_name, ?, 'i') ORDER BY tool_name"),
    /** Lists teams owned by the current user. */
    LIST_TEAMS("SELECT agent_team_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS ORDER BY agent_team_name"),
    /** Lists owned teams matching a database regular expression. */
    LIST_TEAMS_BY_PATTERN("SELECT agent_team_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS "
            + "WHERE REGEXP_LIKE(agent_team_name, ?, 'i') ORDER BY agent_team_name"),
    /** Lists teams available to the calling user for discovery. */
    LIST_AVAILABLE_TEAMS("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.LIST_TEAMS(); END;"),

    /** Creates a task. */
    CREATE_TASK("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.CREATE_TASK(?, ?, ?, ?); END;"),
    /** Enables a task. */
    ENABLE_TASK("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.ENABLE_TASK(?); END;"),
    /** Disables a task. */
    DISABLE_TASK("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DISABLE_TASK(?); END;"),
    /** Drops a task. */
    DROP_TASK("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DROP_TASK(?, ?); END;"),
    /** Sets one task attribute. */
    SET_TASK_ATTRIBUTE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE(?, ?, ?, ?); END;"),
    /** Sets multiple task attributes. */
    SET_TASK_ATTRIBUTES("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES(?, ?, ?); END;"),

    /** Creates a tool. */
    CREATE_TOOL("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.CREATE_TOOL(?, ?, ?, ?); END;"),
    /** Enables a tool. */
    ENABLE_TOOL("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.ENABLE_TOOL(?); END;"),
    /** Disables a tool. */
    DISABLE_TOOL("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DISABLE_TOOL(?); END;"),
    /** Drops a tool. */
    DROP_TOOL("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DROP_TOOL(?, ?); END;"),
    /** Sets one tool attribute. */
    SET_TOOL_ATTRIBUTE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE(?, ?, ?, ?); END;"),
    /** Sets multiple tool attributes. */
    SET_TOOL_ATTRIBUTES("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES(?, ?, ?); END;"),
    /** Describes a tool. */
    DESCRIBE_TOOL("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DESCRIBE_TOOL(?); END;"),
    /** Runs a tool. */
    RUN_TOOL("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.RUN_TOOL(?, ?); END;"),

    /** Creates a team. */
    CREATE_TEAM("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.CREATE_TEAM(?, ?, ?, ?); END;"),
    /** Enables a team. */
    ENABLE_TEAM("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.ENABLE_TEAM(?); END;"),
    /** Disables a team. */
    DISABLE_TEAM("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DISABLE_TEAM(?); END;"),
    /** Drops a team. */
    DROP_TEAM("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DROP_TEAM(?, ?); END;"),
    /** Sets one team attribute. */
    SET_TEAM_ATTRIBUTE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE(?, ?, ?, ?); END;"),
    /** Sets multiple team attributes. */
    SET_TEAM_ATTRIBUTES("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES(?, ?, ?); END;"),
    /** Grants a database user or role access to run an owner-managed team. */
    GRANT_TEAM_ACCESS(
            "BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.GRANT_TEAM_ACCESS(?, ?); END;"),
    /** Revokes a database user or role access to run an owner-managed team. */
    REVOKE_TEAM_ACCESS(
            "BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.REVOKE_TEAM_ACCESS(?, ?); END;"),
    /** Runs a team. */
    RUN_TEAM("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.RUN_TEAM(?, ?, ?); END;"),
    /** Describes a team. */
    DESCRIBE_TEAM("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.DESCRIBE_TEAM(?); END;"),
    /** Gets team state. */
    GET_TEAM_STATE("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.GET_TEAM_STATE(?, ?); END;"),
    /** Sets team execution state. */
    SET_EXECUTION_STATE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_EXECUTION_STATE(?, ?, ?); END;"),
    /** Exports team definition as CLOB. */
    EXPORT_TEAM_FUNCTION("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.EXPORT_TEAM(?, ?); END;"),
    /** Exports team definition to a location. */
    EXPORT_TEAM_PROCEDURE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.EXPORT_TEAM(?, ?, ?, ?); END;"),
    /** Imports a team. */
    IMPORT_TEAM("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.IMPORT_TEAM(?, ?, ?, ?, ?, ?); END;"),
    /** Gets canonical object definition. */
    GET_DEFINITION("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.GET_DEFINITION(?, ?, ?); END;"),
    /** Gets a namespaced runtime variable. */
    GET_VARIABLE("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.GET_VARIABLE(?, ?, ?); END;"),
    /** Sets or clears a namespaced runtime variable. */
    SET_VARIABLE("BEGIN C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_VARIABLE(?, ?, ?, ?); END;");

    /** SQL/PLSQL template bound to this enum constant. */
    private final String statement;

    /** Creates an enum constant with its SQL/PLSQL template. */
    AgentSql(String statement) {
        this.statement = statement;
    }

    /** Returns the SQL/PLSQL template for this operation. */
    public String get() {
        return statement;
    }
}
