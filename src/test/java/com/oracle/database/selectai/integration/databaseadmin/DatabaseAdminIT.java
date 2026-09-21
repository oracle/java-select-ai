/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.selectai.integration.databaseadmin;

import com.oracle.database.selectai.DatabaseAdmin;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.SyntheticDataBatchRequest;
import com.oracle.database.selectai.model.SyntheticDataObjectList;
import com.oracle.database.selectai.model.SyntheticDataParams;
import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live integration coverage for the privileged DatabaseAdmin API.
 *
 * <p>The standard SelectAI client runs as the configured feature-test user, while
 * the common schema tables are owned by {@code ADMIN}. This suite uses a
 * separate admin-capable connection for database-wide data-access procedures
 * and verifies the public AutoCloseable lifecycle.</p>
 */
class DatabaseAdminIT extends DatabaseAdminIntegrationFixture {

    /**
     * Test: Calls the database-wide {@code enableDataAccess()} procedure, calls
     * {@code disableDataAccess()}, and then calls {@code enableDataAccess()} in the
     * {@code finally} block to restore the setting for later suites.
     * Expected: Both explicit procedure calls return {@code true}, and data access is enabled
     * again even if the disable assertion fails.
     */
    @Test
    void test13000EnableAndDisableDataAccess() throws Exception {
        assertThat(databaseAdmin.enableDataAccess()).isTrue();
        try {
            assertThat(databaseAdmin.disableDataAccess()).isTrue();
        } finally {
            // The setting is database-wide; do not leave later integration
            // suites with data access disabled.
            databaseAdmin.enableDataAccess();
        }
    }

    /**
     * Test: Builds a synthetic-data request for {@code ADMIN.people} with one generated record,
     * {@code sampleRows = 1}, and {@code priority = HIGH}. It enables data access, verifies that
     * the fixture profile can narrate a gymnast question and generate synthetic data, disables
     * access, repeats both operations while disabled, restores access in {@code finally}, and
     * repeats both operations once more.
     * Expected: Enabled access produces a non-blank narration and {@code true} from
     * {@code generateSyntheticData(request)}. Disabled access makes both calls throw
     * {@link SelectAIException} with a SQL cause containing {@code ORA-20000:}. After the
     * restoration call, narration is non-blank and synthetic generation again returns
     * {@code true}.
     */
    @Test
    void test13001DataAccessControlsNarrateAndSyntheticData() throws Exception {
        SyntheticDataBatchRequest request = SyntheticDataBatchRequest.builder()
                .addObject(SyntheticDataObjectList.builder("ADMIN", "people")
                        .recordCount(1)
                        .build())
                .params(SyntheticDataParams.builder()
                        .sampleRows(1)
                        .priority("HIGH")
                        .build())
                .build();

        assertThat(databaseAdmin.enableDataAccess()).isTrue();
        try {
            // The profile was created by this suite with the same
            // people/gymnast object list used by this request.
            assertThat(profile.narrate("How many gymnasts are in the database?"))
                    .isNotBlank();
            assertThat(profile.generateSyntheticData(request)).isTrue();

            assertThat(databaseAdmin.disableDataAccess()).isTrue();

            assertThatThrownBy(() ->
                    profile.narrate("How many gymnasts are in the database?"))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-20000:"));
            assertThatThrownBy(() -> profile.generateSyntheticData(request))
                    .isInstanceOf(SelectAIException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining("ORA-20000:"));
        } finally {
            // ENABLE_DATA_ACCESS is database-wide and must be restored for the
            // remaining integration suites.
            databaseAdmin.enableDataAccess();
        }

        // Verify that restoring the setting makes both data-dependent
        // operations usable again, rather than only verifying the admin call.
        assertThat(profile.narrate("How many gymnasts are in the database?"))
                .isNotBlank();
        assertThat(profile.generateSyntheticData(request)).isTrue();
    }

    /**
     * Test: Creates {@link DatabaseAdmin} from an {@link OracleDataSource}, enables and disables
     * data access, restores the enabled state in {@code finally}, closes the admin in
     * try-with-resources, and then obtains a connection directly from the caller-owned
     * DataSource.
     * Expected: Both data-access procedure calls return {@code true}; closing
     * {@code DatabaseAdmin} does not close the DataSource, so a new DataSource connection can be
     * opened and reports {@code isClosed() == false}.
     */
    @Test
    void test13002DataSourceBackedAdminSupportsOperationsAndLifecycle() throws Exception {
        OracleDataSource dataSource = oracleDataSource();

        try (DatabaseAdmin dataSourceAdmin = DatabaseAdmin.create(dataSource)) {
            assertThat(dataSourceAdmin.enableDataAccess()).isTrue();
            try {
                assertThat(dataSourceAdmin.disableDataAccess()).isTrue();
            } finally {
                dataSourceAdmin.enableDataAccess();
            }
        }

        // DatabaseAdmin.close() must not close the caller-owned DataSource.
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isClosed()).isFalse();
        }
    }

    /**
     * Test: Builds {@link SelectAIOptions} with {@code queryTimeoutSeconds = 30}, creates
     * {@link DatabaseAdmin} from an {@link OracleDataSource} and those options, and invokes
     * {@code enableDataAccess()}.
     * Expected: The options-bearing DataSource factory creates a usable admin and
     * {@code enableDataAccess()} returns {@code true}; the admin is closed by the resource
     * block.
     */
    @Test
    void test13003DataSourceFactoryAcceptsExecutionOptions() throws Exception {
        OracleDataSource dataSource = oracleDataSource();
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(30)
                .build();

        try (DatabaseAdmin dataSourceAdmin = DatabaseAdmin.create(dataSource, options)) {
            assertThat(dataSourceAdmin.enableDataAccess()).isTrue();
        }
    }

    /**
     * Test: Creates {@link DatabaseAdmin} from the configured {@code DbConnectionConfig} inside
     * a try-with-resources block and checks the returned object.
     * Expected: The factory returns a non-null admin and the resource block closes it when the
     * block exits.
     */
    @Test
    void test13004DatabaseAdminSupportsTryWithResources() throws Exception {
        try (DatabaseAdmin admin = DatabaseAdmin.create(adminDbConfig())) {
            assertThat(admin).isNotNull();
        }
    }

    /**
     * Test: Builds {@link SelectAIOptions} with {@code queryTimeoutSeconds = 30}, creates
     * {@link DatabaseAdmin} from the configured {@code DbConnectionConfig} and those options,
     * and invokes {@code enableDataAccess()}.
     * Expected: The options-bearing config factory creates a usable admin and
     * {@code enableDataAccess()} returns {@code true}; the admin is closed by the resource
     * block.
     */
    @Test
    void test13005ConfigFactoryAcceptsExecutionOptions() throws Exception {
        SelectAIOptions options = SelectAIOptions.builder()
                .queryTimeoutSeconds(30)
                .build();

        try (DatabaseAdmin admin = DatabaseAdmin.create(adminDbConfig(), options)) {
            assertThat(admin.enableDataAccess()).isTrue();
        }
    }

    private OracleDataSource oracleDataSource() throws SQLException {
        var adminConfig = adminDbConfig();
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(adminConfig.getJdbcUrl());
        dataSource.setUser(adminConfig.getDbUser());
        dataSource.setPassword(adminConfig.getDbPassword());
        return dataSource;
    }

}
