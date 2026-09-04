/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.impl;

import com.oracle.database.selectai.ConnectionCallback;
import com.oracle.database.selectai.ConnectionProvider;
import com.oracle.database.selectai.model.SelectAIOptions;
import com.oracle.database.selectai.model.SelectAIException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConnectionProvider decorator that applies SDK JDBC execution options to
 * statements created by SDK operations.
 */
final class JdbcExecutionConnectionProvider implements ConnectionProvider {
    /** Logger for JDBC execution option diagnostics. */
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcExecutionConnectionProvider.class);
    /** Delegate provider that owns connection acquisition and release. */
    private final ConnectionProvider delegate;
    /** SDK execution options applied to statements. */
    private final SelectAIOptions options;

    JdbcExecutionConnectionProvider(ConnectionProvider delegate, SelectAIOptions options) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate connectionProvider must not be null");
        }
        this.delegate = delegate;
        this.options = options == null ? SelectAIOptions.defaults() : options;
    }

    @Override
    public <T> T withConnection(ConnectionCallback<T> callback) throws SQLException, SelectAIException {
        if (!hasStatementOptions()) {
            return delegate.withConnection(callback);
        }
        return delegate.withConnection(connection -> callback.execute(wrapConnection(connection)));
    }

    private boolean hasStatementOptions() {
        return options.getQueryTimeoutSeconds() != null;
    }

    private Connection wrapConnection(Connection connection) {
        InvocationHandler handler = new JdbcExecutionConnectionInvocationHandler(connection, options);
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[] {Connection.class},
                handler);
    }

    /**
     * Applies SDK statement options to statements returned from connection
     * factory methods.
     */
    private static final class JdbcExecutionConnectionInvocationHandler implements InvocationHandler {
        /** Wrapped JDBC connection. */
        private final Connection connection;
        /** SDK execution options applied to created statements. */
        private final SelectAIOptions options;

        private JdbcExecutionConnectionInvocationHandler(Connection connection, SelectAIOptions options) {
            this.connection = connection;
            this.options = options;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(connection, args);
                if (result instanceof Statement statement && createsStatement(method)) {
                    applyStatementOptions(statement);
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private void applyStatementOptions(Statement statement) throws SQLException {
            Integer queryTimeoutSeconds = options.getQueryTimeoutSeconds();
            if (queryTimeoutSeconds != null) {
                statement.setQueryTimeout(queryTimeoutSeconds);
                LOGGER.debug("Applied JDBC statement query timeout: {} second(s)", queryTimeoutSeconds);
            }
        }

        private static boolean createsStatement(Method method) {
            String name = method.getName();
            return "prepareCall".equals(name)
                    || "prepareStatement".equals(name)
                    || "createStatement".equals(name);
        }
    }
}
