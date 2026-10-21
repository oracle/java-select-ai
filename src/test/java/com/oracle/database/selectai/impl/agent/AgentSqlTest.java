/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSqlTest {
    @Test
    void packageCallsUseOwningSchema() {
        int packageCalls = 0;
        for (AgentSql template : AgentSql.values()) {
            String sql = template.get();
            if (sql.contains("DBMS_CLOUD_AI_AGENT.")) {
                packageCalls++;
                assertThat(sql).as(template.name())
                        .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.");
            }
        }
        assertThat(packageCalls).isPositive();
    }

    @Test
    void agentAttributeUpdatesUseTheAgentObjectTypeAndBindValues() {
        assertThat(AgentSql.SET_AGENT_ATTRIBUTE.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE")
                .contains("?, ?, ?, ?");
        assertThat(AgentSql.SET_AGENT_ATTRIBUTES.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES")
                .contains("?, ?, ?");
    }

    @Test
    void taskToolAndTeamAttributeUpdatesUseAgentPackageTemplates() {
        for (AgentSql sql : new AgentSql[] {
                AgentSql.SET_TASK_ATTRIBUTE, AgentSql.SET_TOOL_ATTRIBUTE, AgentSql.SET_TEAM_ATTRIBUTE
        }) {
            assertThat(sql.get())
                    .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE")
                    .contains("?, ?, ?, ?");
        }
        for (AgentSql sql : new AgentSql[] {
                AgentSql.SET_TASK_ATTRIBUTES, AgentSql.SET_TOOL_ATTRIBUTES, AgentSql.SET_TEAM_ATTRIBUTES
        }) {
            assertThat(sql.get())
                    .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTES")
                    .contains("?, ?, ?");
        }
    }

    @Test
    void teamAccessUsesAgentPackageTemplates() {
        assertThat(AgentSql.GRANT_TEAM_ACCESS.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.GRANT_TEAM_ACCESS")
                .contains("?, ?");
        assertThat(AgentSql.REVOKE_TEAM_ACCESS.get())
                .contains("C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.REVOKE_TEAM_ACCESS")
                .contains("?, ?");
    }

    @Test
    void metadataLookupsRetainTheirSql() {
        assertThat(AgentSql.GET_AGENT.get())
                .isEqualTo("SELECT agent_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENTS WHERE agent_name = ?");
        assertThat(AgentSql.GET_TASK.get())
                .isEqualTo("SELECT task_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS WHERE task_name = ?");
        assertThat(AgentSql.GET_TOOL.get())
                .isEqualTo("SELECT tool_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOLS WHERE tool_name = ?");
        assertThat(AgentSql.GET_TEAM.get())
                .isEqualTo("SELECT agent_team_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS WHERE agent_team_name = ?");
        assertThat(AgentSql.GET_AGENT_STATUS.get())
                .isEqualTo("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENTS WHERE agent_name = ?");
        assertThat(AgentSql.GET_TASK_STATUS.get())
                .isEqualTo("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS WHERE task_name = ?");
        assertThat(AgentSql.GET_TOOL_STATUS.get())
                .isEqualTo("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOLS WHERE tool_name = ?");
        assertThat(AgentSql.GET_TEAM_STATUS.get())
                .isEqualTo("SELECT status FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS WHERE agent_team_name = ?");
        assertThat(AgentSql.GET_TEAM_ATTRIBUTES.get())
                .contains("FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAM_ATTRIBUTES")
                .contains("WHERE UPPER(agent_team_name) = UPPER(?)");
        assertThat(AgentSql.LIST_TASKS.get())
                .isEqualTo("SELECT task_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS ORDER BY task_name");
        assertThat(AgentSql.LIST_TEAMS.get())
                .isEqualTo("SELECT agent_team_name, status, description FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS ORDER BY agent_team_name");
        assertThat(AgentSql.LIST_AVAILABLE_TEAMS.get())
                .isEqualTo("BEGIN ? := C##CLOUD$SERVICE.DBMS_CLOUD_AI_AGENT.LIST_TEAMS(); END;");
    }

    @Test
    void patternListsUseQualifiedViewsAndBindVariables() {
        assertThat(AgentSql.LIST_AGENTS_BY_PATTERN.get())
                .contains("FROM C##CLOUD$SERVICE.USER_AI_AGENTS")
                .contains("REGEXP_LIKE(agent_name, ?, 'i')");
        assertThat(AgentSql.LIST_TASKS_BY_PATTERN.get())
                .contains("FROM C##CLOUD$SERVICE.USER_AI_AGENT_TASKS")
                .contains("REGEXP_LIKE(task_name, ?, 'i')");
        assertThat(AgentSql.LIST_TOOLS_BY_PATTERN.get())
                .contains("FROM C##CLOUD$SERVICE.USER_AI_AGENT_TOOLS")
                .contains("REGEXP_LIKE(tool_name, ?, 'i')");
        assertThat(AgentSql.LIST_TEAMS_BY_PATTERN.get())
                .contains("FROM C##CLOUD$SERVICE.USER_AI_AGENT_TEAMS")
                .contains("REGEXP_LIKE(agent_team_name, ?, 'i')");
    }
}
