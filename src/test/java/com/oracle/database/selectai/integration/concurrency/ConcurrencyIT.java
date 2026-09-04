/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.integration.concurrency;

import com.oracle.database.selectai.Conversation;
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.VectorIndex;
import com.oracle.database.selectai.model.ConversationAttributes;
import com.oracle.database.selectai.model.GenerateAction;
import com.oracle.database.selectai.model.SelectAIException;
import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused concurrency coverage for the Java SDK public synchronous APIs.
 *
 * <p>Every worker creates its own SDK client and JDBC connection. The tests do
 * not assume that one mutable SDK object is thread-safe; that behavior is not
 * part of the current public contract.</p>
 *
 * <p>Concurrent duplicate profile creation is intentionally not asserted here.
 * The Java {@code replace=false} flow uses a pre-check, while the database
 * package does not guarantee atomic duplicate rejection across sessions.
 * Sequential duplicate rejection is covered by {@code ProfileIT}.</p>
 */
class ConcurrencyIT extends ConcurrencyIntegrationFixture {

    private static final int WORKER_TIMEOUT_SECONDS = 30;

    @Override
    protected String profileObjectList() {
        return objectListFor("people", "gymnast");
    }

    /**
     * Test: Starts four barrier-synchronized workers: three create independent clients with the
     * configured username/password and call {@code listProfiles()}, while the fourth uses the
     * same username with the configured password plus {@code _INVALID} and calls
     * {@code listProfiles()}.
     * Expected: Four outcomes are returned; exactly three succeed with the boolean value
     * {@code true}, and the invalid-password worker fails with {@link SelectAIException} whose
     * {@link SQLException} cause starts with {@code ORA-01017: invalid credential or not
     * authorized; logon denied}. Every worker closes its own client.
     */
    @Test
    void test60000IndependentClientsIsolateConnectionFailures() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            tasks.add(() -> withIndependentClient(client ->
                    client.listProfiles() != null));
        }

        tasks.add(() -> {
            SelectAI client = null;
            try {
                client = SelectAI.create(connectionConfigFor(
                        dbConfig.getDbUser(), dbConfig.getDbPassword() + "_INVALID"));
                client.listProfiles();
                return true;
            } finally {
                closeClient(client);
            }
        });

        List<TaskOutcome<Boolean>> outcomes = runConcurrently(tasks);

        assertThat(outcomes).hasSize(4);
        assertThat(outcomes.stream().filter(TaskOutcome::succeeded).count()).isEqualTo(3);
        assertThat(outcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .findFirst()
                .orElseThrow()
                .failure())
                .isInstanceOfSatisfying(SelectAIException.class, exception -> {
                    assertThat(exception.getCause()).isInstanceOf(SQLException.class);
                    assertThat(exception.getCause().getMessage())
                            .startsWith("ORA-01017: invalid credential or not authorized; logon denied");
                });
        assertThat(outcomes.stream()
                .filter(TaskOutcome::succeeded)
                .allMatch(outcome -> Boolean.TRUE.equals(outcome.value())))
                .isTrue();
    }

    /**
     * Test: Runs three barrier-synchronized operations on separate clients: a {@code runsql}
     * request asking for the number of gymnasts, a German translation of {@code thank you}
     * using English as the source and German as the target language, and a second {@code runsql}
     * request asking for the number of people.
     * Expected: All three workers complete without failure and return the action labels
     * {@code runsql-gymnasts}, {@code translate}, and {@code runsql-people}. Every response is
     * non-blank; the two count responses contain {@code 5} or {@code five}, and the translation
     * contains {@code Danke}. Each independent
     * client is closed after its operation.
     */
    @Test
    void test60001ConcurrentProfileReadsAndActionsRemainIsolated() throws Exception {
        List<Callable<ActionResult>> tasks = List.of(
                () -> withIndependentClient(client -> {
                    Profile target = client.profile(profileName);
                    String response = target.generate(
                            "How many gymnasts in database?", GenerateAction.runsql);
                    return new ActionResult("runsql-gymnasts", response);
                }),
                () -> withIndependentClient(client -> {
                    Profile target = client.profile(profileName);
                    String response = target.translate(TRANSLATE_TEXT, SOURCE_LANGUAGE,
                            TARGET_LANGUAGE);
                    return new ActionResult("translate", response);
                }),
                () -> withIndependentClient(client -> {
                    Profile target = client.profile(profileName);
                    String response = target.generate(
                            "How many people are there in the database?", GenerateAction.runsql);
                    return new ActionResult("runsql-people", response);
                }));

        List<TaskOutcome<ActionResult>> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.failure()).isNull();
            assertThat(outcome.value()).isNotNull();
            assertThat(outcome.value().response())
                    .as("response for " + outcome.value().action())
                    .isInstanceOf(String.class)
                    .isNotBlank();
        });

        assertThat(outcomes).extracting(outcome -> outcome.value().action())
                .containsExactlyInAnyOrder("runsql-gymnasts", "translate", "runsql-people");

        ActionResult gymnasts = outcomes.stream()
                .map(TaskOutcome::value)
                .filter(result -> result.action().equals("runsql-gymnasts"))
                .findFirst()
                .orElseThrow();
        assertThat(gymnasts.response().toLowerCase()).containsAnyOf("5", "five");

        ActionResult translated = outcomes.stream()
                .map(TaskOutcome::value)
                .filter(result -> result.action().equals("translate"))
                .findFirst()
                .orElseThrow();
        assertThat(translated.response().toLowerCase())
                .contains(EXPECTED_TRANSLATION.toLowerCase());

        ActionResult people = outcomes.stream()
                .map(TaskOutcome::value)
                .filter(result -> result.action().equals("runsql-people"))
                .findFirst()
                .orElseThrow();
        assertThat(people.response().toLowerCase()).containsAnyOf("5", "five");
    }

    /**
     * Test: Wraps an {@link OracleDataSource} in a counting DataSource, injects one connection-
     * borrow failure, calls {@code listProfiles()}, then performs one successful list and four
     * concurrent {@code listProfiles().size()} calls through one DataSource-backed client.
     * Expected: The injected call throws {@link SelectAIException} with the exact message
     * {@code Failed to execute user_cloud_ai_profiles}, no connection remains active, the next
     * list succeeds, all four concurrent results are non-negative, at least six borrows are
     * recorded, and the active-connection count returns to {@code 0}.
     */
    @Test
    void test60002PooledConnectionsRecoverAndReturnAfterConcurrentFailures() throws Exception {
        OracleDataSource delegate = new OracleDataSource();
        delegate.setURL(dbConfig.getJdbcUrl());
        delegate.setUser(dbConfig.getDbUser());
        delegate.setPassword(dbConfig.getDbPassword());

        CountingDataSource dataSource = new CountingDataSource(delegate);
        SelectAI pooledClient = SelectAI.create(dataSource);

        dataSource.failNextBorrow();
        assertThatThrownBy(pooledClient::listProfiles)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Failed to execute user_cloud_ai_profiles");
        assertThat(dataSource.activeConnections()).isZero();

        assertThat(pooledClient.listProfiles()).isNotNull();

        List<Callable<Integer>> tasks = List.of(
                () -> pooledClient.listProfiles().size(),
                () -> pooledClient.listProfiles().size(),
                () -> pooledClient.listProfiles().size(),
                () -> pooledClient.listProfiles().size());
        List<TaskOutcome<Integer>> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.failure()).isNull();
            assertThat(outcome.value()).isNotNegative();
        });
        assertThat(dataSource.borrowedConnections()).isGreaterThanOrEqualTo(6);
        assertThat(dataSource.activeConnections()).isZero();
    }

    /**
     * Test: Runs four operations concurrently through one counting DataSource-backed client: a
     * {@code showprompt} generation request, a translation of {@code thank you}, creation and
     * reload of a uniquely titled conversation followed by {@code drop(true)}, and a vector
     * index listing using a unique name pattern. It then injects one prepared-call failure and
     * calls {@code listProfiles()} again.
     * Expected: The four outcomes have the labels {@code generation}, {@code translation},
     * {@code conversation}, and {@code vector}; each has no failure and a non-blank response,
     * and the reloaded conversation retains its generated title. Before and after the injected
     * failure, no connection remains active; the failed list operation throws
     * {@link SelectAIException} with the exact message
     * {@code Failed to execute user_cloud_ai_profiles}, and the borrow count increases after
     * the failure.
     */
    @Test
    void test60003SharedDataSourceClientIsolatesConcurrentOperationsAndReturnsConnectionsAfterFailure()
            throws Exception {
        OracleDataSource delegate = new OracleDataSource();
        delegate.setURL(dbConfig.getJdbcUrl());
        delegate.setUser(dbConfig.getDbUser());
        delegate.setPassword(dbConfig.getDbPassword());

        CountingDataSource dataSource = new CountingDataSource(delegate);
        SelectAI sharedClient = SelectAI.create(dataSource);

        String conversationTitle = uniqueName("SHARED_CONVERSATION");
        String vectorPattern = uniqueName("SHARED_VECTOR");

        List<Callable<ActionResult>> tasks = List.of(
                () -> {
                    Profile profile = sharedClient.profile(profileName);
                    String response = profile.generate(
                            "Return the number of people in the database.",
                            GenerateAction.showprompt);
                    return new ActionResult("generation", response);
                },
                () -> {
                    Profile profile = sharedClient.profile(profileName);
                    String response = profile.translate(TRANSLATE_TEXT, SOURCE_LANGUAGE,
                            TARGET_LANGUAGE);
                    return new ActionResult("translation", response);
                },
                () -> {
                    Conversation conversation = sharedClient.conversation(
                            ConversationAttributes.builder()
                                    .title(conversationTitle)
                                    .description("Shared DataSource concurrency test")
                                    .build());
                    boolean created = false;
                    try {
                        String conversationId = conversation.create();
                        created = true;
                        Conversation loaded = sharedClient.conversation(conversationId);
                        assertThat(loaded.getConversationAttributes().getTitle())
                                .isEqualTo(conversationTitle);
                        return new ActionResult("conversation", conversationId);
                    } finally {
                        if (created) {
                            conversation.drop(true);
                        }
                    }
                },
                () -> {
                    List<VectorIndex> indexes = sharedClient.listVectorIndexes(vectorPattern);
                    return new ActionResult("vector", String.valueOf(indexes.size()));
                });

        List<TaskOutcome<ActionResult>> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.failure()).isNull();
            assertThat(outcome.value()).isNotNull();
            assertThat(outcome.value().response())
                    .as("response for " + outcome.value().action())
                    .isNotBlank();
        });
        assertThat(outcomes).extracting(outcome -> outcome.value().action())
                .containsExactlyInAnyOrder("generation", "translation", "conversation", "vector");

        assertThat(dataSource.activeConnections()).isZero();
        int borrowedBeforeFailure = dataSource.borrowedConnections();

        dataSource.failNextOperation();
        assertThatThrownBy(sharedClient::listProfiles)
                .isInstanceOf(SelectAIException.class)
                .hasMessage("Failed to execute user_cloud_ai_profiles");

        assertThat(dataSource.borrowedConnections()).isGreaterThan(borrowedBeforeFailure);
        assertThat(dataSource.activeConnections()).isZero();
    }

    private <T> T withIndependentClient(ClientOperation<T> operation) throws Exception {
        SelectAI client = null;
        try {
            client = SelectAI.create(connectionConfigFor(
                    dbConfig.getDbUser(), dbConfig.getDbPassword()));
            return operation.apply(client);
        } finally {
            closeClient(client);
        }
    }

    private static void closeClient(SelectAI client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (SelectAIException ignored) {
            // Preserve the primary worker result.
        }
    }

    private <T> List<TaskOutcome<T>> runConcurrently(
            List<? extends Callable<T>> tasks) throws Exception {
        if (tasks.isEmpty()) {
            return List.of();
        }

        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TaskOutcome<T>>> futures = new ArrayList<>();

        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                try {
                    ready.countDown();
                    if (!ready.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            || !start.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new TimeoutException("Concurrency test barrier timed out");
                    }
                    return TaskOutcome.success(task.call());
                } catch (Exception e) {
                    return TaskOutcome.failure(e);
                }
            }));
        }

        try {
            if (!ready.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new TimeoutException("Concurrency workers did not become ready");
            }
            start.countDown();

            List<TaskOutcome<T>> outcomes = new ArrayList<>();
            for (Future<TaskOutcome<T>> future : futures) {
                outcomes.add(future.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            start.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new TimeoutException("Concurrency workers did not terminate");
            }
        }
    }

    private static String uniqueName(String stem) {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        return "JSAI_CONC_" + stem + "_" + suffix;
    }

    @FunctionalInterface
    private interface ClientOperation<T> {
        T apply(SelectAI client) throws Exception;
    }

    private record ActionResult(String action, String response) {
    }

    private record TaskOutcome<T>(T value, Exception failure) {
        private static <T> TaskOutcome<T> success(T value) {
            return new TaskOutcome<>(value, null);
        }

        private static <T> TaskOutcome<T> failure(Exception failure) {
            return new TaskOutcome<>(null, failure);
        }

        private boolean succeeded() {
            return failure == null;
        }
    }

    /**
     * DataSource decorator used to verify that operation-scoped connections
     * are returned after both successful and failed SDK calls.
     */
    private static final class CountingDataSource implements DataSource {
        private final DataSource delegate;
        private final AtomicInteger borrowed = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();
        private final AtomicBoolean failNextOperation = new AtomicBoolean();

        private CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        private void failNextBorrow() {
            failNext.set(true);
        }

        private void failNextOperation() {
            failNextOperation.set(true);
        }

        private int borrowedConnections() {
            return borrowed.get();
        }

        private int activeConnections() {
            return active.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            borrowed.incrementAndGet();
            Connection connection = delegate.getConnection();
            if (failNext.compareAndSet(true, false)) {
                try {
                    connection.close();
                } finally {
                    throw new SQLException("Injected one-shot connection failure");
                }
            }
            return track(connection);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            borrowed.incrementAndGet();
            return track(delegate.getConnection(username, password));
        }

        private Connection track(Connection connection) {
            active.incrementAndGet();
            AtomicBoolean closed = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")
                                && method.getParameterCount() == 0) {
                            if (closed.compareAndSet(false, true)) {
                                try {
                                    return method.invoke(connection, args);
                                } finally {
                                    active.decrementAndGet();
                                }
                            }
                            return null;
                        }
                        if ((method.getName().equals("prepareCall")
                                || method.getName().equals("prepareStatement"))
                                && failNextOperation.compareAndSet(true, false)) {
                            throw new SQLException("Injected one-shot operation failure", "08006", 17002);
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
