/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.samples;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

/** Isolated subprocess entry point that cannot make a real JDBC connection. */
public final class AgentSampleFailureProcess {
    private AgentSampleFailureProcess() {
    }

    /**
     * Invokes a sample with JDBC connections replaced by a deterministic failure.
     *
     * @param args simple sample class name
     * @throws Throwable the original sample failure, making the process fail
     */
    public static void main(String[] args) throws Throwable {
        for (Driver driver : Collections.list(DriverManager.getDrivers())) {
            DriverManager.deregisterDriver(driver);
        }
        DriverManager.registerDriver(new FailingDriver());
        try {
            Class.forName("com.oracle.database.selectai.samples.agent." + args[0])
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static final class FailingDriver implements Driver {
        @Override
        public Connection connect(String url, Properties properties) throws SQLException {
            throw new SQLException("Offline sample JDBC failure", "08001", 17002);
        }

        @Override
        public boolean acceptsURL(String url) {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
