# DataSource Connection Support

This SDK supports `javax.sql.DataSource` as the recommended connection source
for application and multi-threaded environments.

The SDK represents its internal connection lifecycle contracts with interfaces
in `com.oracle.database.selectai`:

- `ConnectionProvider`
- `ConnectionCallback`

These interfaces are not supported application extension points or an
additional public client construction mode. Application code should use the
public factory methods described below.

Concrete provider implementations live under `com.oracle.database.selectai.impl` as
internal SDK details. Application code should use public factory methods such
as `SelectAI.create(...)` and `DatabaseAdmin.create(...)`.

## Why DataSource

`DataSource` is the standard JDBC abstraction for obtaining connections. It can
represent a simple connection factory, an Oracle UCP pool, a HikariCP pool, or an
application-server-managed pool. The SDK does not need a separate public
`ConnectionPool` API.

## Connection Modes

### DataSource mode

`SelectAI.create(DataSource)` and `DatabaseAdmin.create(DataSource)` use a
connection provider that:

1. Borrows a connection from the DataSource for each SDK operation.
2. Executes the SDK operation.
3. Closes the connection in a `try-with-resources` block.

For pooled DataSources, `Connection.close()` normally returns the connection to
the pool. The SDK does not own or close the DataSource itself.
Connections are obtained lazily when an SDK operation needs one. Calling
`SelectAI.close()` or `DatabaseAdmin.close()` is safe and idempotent in this
mode; it does not close or modify the caller-owned DataSource because the
operation-level connections have already been closed.

### DbConnectionConfig mode

`SelectAI.create(DbConnectionConfig)` and `DatabaseAdmin.create(DbConnectionConfig)`
use the configuration-based connection behavior:

1. Opens one JDBC connection through `DriverManager`.
2. Reuses that same connection for SDK objects created from the SelectAI
   instance.
3. Does not close the connection after each operation.

This mode is useful for simple command-line or sample-style programs. It should
not be treated as a thread-safe shared client mode.
The `SelectAI` or `DatabaseAdmin` client owns this connection and should be
closed when the caller is finished with SDK operations.

```java
try (SelectAI selectAI = SelectAI.create(dbConnectionConfig)) {
    Profile profile = selectAI.profile("MY_PROFILE");
    String response = profile.chat("What is Select AI?");
}
```

The retained JDBC connection can be accessed for custom SQL or PL/SQL:

```java
try (SelectAI selectAI = SelectAI.create(dbConnectionConfig);
     Statement statement = selectAI.getConnection().createStatement();
     ResultSet resultSet = statement.executeQuery("SELECT 1 FROM dual")) {
    // process resultSet
}
```

`getConnection()` is not available in DataSource mode because that mode borrows
and closes a connection for each SDK operation instead of retaining one shared
connection.

`DbConnection` is a separate standalone connection-owner API. It eagerly opens
one SDK-owned JDBC connection when `DbConnection.create(...)` is called. Close
the returned `DbConnection` when it is no longer needed.

## Transaction Boundaries

The SDK does not explicitly perform commit or rollback operations and does not
manage transaction boundaries.

In DataSource mode, the SDK borrows a JDBC connection for each public SDK
operation and closes the connection when the operation completes. If the
DataSource is backed by a connection pool, closing the connection normally
returns it to the pool.

The SDK does not explicitly modify the connection's auto-commit setting.
Transaction behavior is therefore determined by the connection configuration
and the underlying Oracle Database APIs invoked by the SDK. Any implicit commit
or rollback performed by an underlying database API is outside the SDK's
transaction management.

If an SDK operation fails, the SDK propagates the underlying JDBC or Oracle
error through `SelectAIException`. The SDK does not explicitly issue a commit or
rollback as part of failure handling.

In `DbConnectionConfig` mode, the SDK reuses the SDK-owned single JDBC
connection without explicitly committing or rolling back transactions.
Connection lifecycle is controlled by `SelectAI.close()` or
`DatabaseAdmin.close()`. Transaction behavior continues to be determined by the
connection configuration and the database APIs invoked by the SDK.

## SDK Execution Options

`SelectAIOptions` configures SDK-level JDBC execution behavior for both
connection modes and both public clients, `SelectAI` and `DatabaseAdmin`.
Query timeout is applied internally to statements created by SDK operations
using `Statement.setQueryTimeout`.

```java
SelectAIOptions options = SelectAIOptions.builder()
        .queryTimeoutSeconds(300)
        .build();

SelectAI selectAI = SelectAI.create(dbConnectionConfig, options);
SelectAI pooledSelectAI = SelectAI.create(dataSource, options);
DatabaseAdmin databaseAdmin = DatabaseAdmin.create(dbConnectionConfig, options);
DatabaseAdmin pooledDatabaseAdmin = DatabaseAdmin.create(dataSource, options);
```

The default is no SDK-level query timeout. A `null` timeout leaves statement
timeout unchanged, `0` uses JDBC's no-timeout behavior, and negative values are
rejected.

For password-protected wallets, set `DbConnectionConfig.Builder.walletPassword(...)`.
The SDK passes this value to Oracle JDBC as `oracle.net.wallet_password`; it is
not appended to the JDBC URL.

`DbConnectionConfig.Builder.jdbcUrl(...)` requires an Oracle JDBC Thin URL with
a connect target after `jdbc:oracle:thin:@`. This supports Easy Connect/Easy
Connect Plus, TCPS, TNS aliases, full connection descriptors, and wallet/TNS_ADMIN
based Autonomous Database URLs. The SDK does not configure Kerberos, RADIUS,
OCI IAM token, OAuth token, direct access-token, or client-certificate
authentication by itself; provide the required Oracle JDBC and environment
configuration when using those authentication modes.

## Threading Guidance

Threading guidance applies to both connection modes.

Use DataSource mode when sharing one `SelectAI` or `DatabaseAdmin` instance
across multiple threads for independent operations. DataSource mode is suitable
for this because each operation borrows its own connection.

The DataSource change removes the shared JDBC `Connection` bottleneck, but it
does not make mutable resource handles fully immutable. Do not concurrently
mutate or run state-changing operations on the same `Profile`, `Credential`,
`Conversation`, or `VectorIndex` object unless caller-side synchronization is
used.

`DbConnectionConfig` mode uses a single connection and should be treated as
single-threaded unless the caller serializes access externally.

## Public Usage

```java
DataSource dataSource = /* application-managed DataSource */;

SelectAI selectAI = SelectAI.create(dataSource);
DatabaseAdmin databaseAdmin = DatabaseAdmin.create(dataSource);
```

Single-connection mode remains:

```java
SelectAI selectAI = SelectAI.create(dbConnectionConfig);
DatabaseAdmin databaseAdmin = DatabaseAdmin.create(dbConnectionConfig);
```
