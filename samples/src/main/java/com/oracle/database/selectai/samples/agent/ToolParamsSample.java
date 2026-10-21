/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.model.agent.EmailNotificationToolParams;
import com.oracle.database.selectai.model.agent.GenericToolParams;
import com.oracle.database.selectai.model.agent.RagToolParams;
import com.oracle.database.selectai.model.agent.SlackNotificationToolParams;
import com.oracle.database.selectai.model.agent.SqlToolParams;
import com.oracle.database.selectai.model.agent.ToolAttributes;
import com.oracle.database.selectai.model.agent.ToolParams;
import com.oracle.database.selectai.model.agent.ToolType;
import com.oracle.database.selectai.model.agent.WebSearchToolParams;

import java.util.Map;

/**
 * Builds typed tool parameters and prints their database attribute JSON.
 *
 * <p>No database connection or environment variables are required. This sample
 * does not create resources or call tools. Profile and credential names are
 * illustrative; the database validates them when creating or updating a tool.</p>
 *
 * <p>Pass the resulting {@link ToolAttributes} to
 * {@link com.oracle.database.selectai.agent.SelectAIAgent#tool(String, ToolAttributes,
 * String, com.oracle.database.selectai.model.agent.ToolStatus)} for creation,
 * or to {@link com.oracle.database.selectai.agent.Tool#setAttributes(ToolAttributes)}
 * for an update. See {@link CreateSqlToolSample} and
 * {@link SetSqlToolParamsSample} for database-backed examples.</p>
 *
 * <p>The future parameter names below are illustrative, not claims that a
 * particular database release supports them. They are never submitted to a
 * database. Only the literal, non-sensitive example values are printed.</p>
 */
public final class ToolParamsSample {
    private ToolParamsSample() {
    }

    /**
     * Prints configurations for the supported parameter models.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        SqlToolParams sql = SqlToolParams.builder().profileName("NL2SQL_PROFILE").build();
        print(ToolType.SQL, sql);
        print(ToolType.RAG, RagToolParams.builder().profileName("RAG_PROFILE").build());
        print(ToolType.WEBSEARCH, WebSearchToolParams.builder()
                .credentialName("WEBSEARCH_CRED").build());
        print(ToolType.NOTIFICATION, SlackNotificationToolParams.builder()
                .credentialName("SLACK_CRED").channel("support").build());
        print(ToolType.NOTIFICATION, EmailNotificationToolParams.builder()
                .credentialName("EMAIL_CRED").recipient("recipient@example.com")
                .sender("sender@example.com").smtpHost("smtp.example.com")
                .subject("Support update").build());

        // The generic form preserves parameters introduced by newer databases.
        ToolParams future = GenericToolParams.builder()
                .parameter("future_options", Map.of("mode", "example"))
                .build();
        print(ToolType.of("FUTURE_TOOL"), future);

        // Adapt an existing map without losing database-defined fields.
        GenericToolParams generic = GenericToolParams.fromMap(
                Map.of("profile_name", "NL2SQL_PROFILE", "future_options", Map.of("enabled", true)));
        print(ToolType.SQL, generic);

        // Known models also accept additional parameters for newer databases.
        SqlToolParams extended = SqlToolParams.builder()
                .profileName("NL2SQL_PROFILE")
                .additionalParameter("future_options", Map.of("enabled", true))
                .build();
        print(ToolType.SQL, extended);

        ToolParams loaded = ToolParams.fromMap(ToolType.SQL, sql.toMap());
        if (loaded instanceof SqlToolParams loadedSql) {
            System.out.println("SQL profile: " + loadedSql.getProfileName());
        }

        ToolParams unknown = ToolParams.fromMap(ToolType.of("FUTURE_TOOL"), future.toMap());
        System.out.println("Unknown tool parameter model: " + unknown.getClass().getSimpleName());
    }

    private static void print(ToolType toolType, ToolParams params) {
        System.out.println(ToolAttributes.builder().toolType(toolType).toolParams(params).build().toJson());
        System.out.println("Parameter names: " + params.toMap().keySet());
        if (params instanceof SqlToolParams sql) {
            System.out.println("SQL profile: " + sql.getProfileName());
        } else if (params instanceof RagToolParams rag) {
            System.out.println("RAG profile: " + rag.getProfileName());
        } else if (params instanceof WebSearchToolParams webSearch) {
            System.out.println("Web-search credential: " + webSearch.getCredentialName());
        } else if (params instanceof SlackNotificationToolParams slack) {
            System.out.printf("Notification: %s; credential: %s; channel: %s%n",
                    slack.getNotificationType(), slack.getCredentialName(), slack.getChannel());
        } else if (params instanceof EmailNotificationToolParams email) {
            System.out.printf("Notification: %s; credential: %s; recipient: %s; sender: %s; SMTP: %s; subject: %s%n",
                    email.getNotificationType(), email.getCredentialName(), email.getRecipient(),
                    email.getSender(), email.getSmtpHost(), email.getSubject());
        }
    }
}
