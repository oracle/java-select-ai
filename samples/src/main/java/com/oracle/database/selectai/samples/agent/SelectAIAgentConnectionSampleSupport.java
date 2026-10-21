/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples.agent;

import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIOptions;
import oracle.jdbc.pool.OracleDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;

/** Shared connection configuration helpers for SelectAIAgent factory samples. */
final class SelectAIAgentConnectionSampleSupport {
    private SelectAIAgentConnectionSampleSupport() {
    }

    static DbConnectionConfig dbConnectionConfig() {
        return DbConnectionConfig.builder()
                .dbUser(requiredEnv("SELECTAI_DB_USER"))
                .dbPassword(requiredEnv("SELECTAI_DB_PASSWORD"))
                .jdbcUrl(requiredEnv("SELECTAI_JDBC_URL"))
                .build();
    }

    static DataSource dataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(requiredEnv("SELECTAI_JDBC_URL"));
        dataSource.setUser(requiredEnv("SELECTAI_DB_USER"));
        dataSource.setPassword(requiredEnv("SELECTAI_DB_PASSWORD"));
        return dataSource;
    }

    static SelectAIOptions options() {
        String timeout = optionalEnv("SELECTAI_QUERY_TIMEOUT_SECONDS");
        return SelectAIOptions.builder()
                .queryTimeoutSeconds(timeout == null ? null : Integer.valueOf(timeout))
                .build();
    }

    static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
