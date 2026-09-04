# Select AI for Java

## Table of Contents

- [Overview](#overview)
- [Supported environments](#supported-environments)
- [Installation](#installation)
- [Prerequisites and database setup](#prerequisites-and-database-setup)
- [Quick start](#quick-start)
- [Connection management](#connection-management)
  - [DataSource](#datasource)
  - [DbConnectionConfig](#dbconnectionconfig)
- [Examples](#examples)
- [Resource lifecycle](#resource-lifecycle)
- [Error handling and timeouts](#error-handling-and-timeouts)
- [Security](#security)
- [Documentation](#documentation)
- [Samples](#samples)
- [Help](#help)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Overview

Java SDK for Oracle Autonomous AI Database Select AI operations backed by
`DBMS_CLOUD_AI`.

Select AI for Java enables Java developers to use Oracle Autonomous AI Database
Select AI from Java applications. It supports natural-language-to-SQL (NL2SQL) query
generation over database data, also known as text-to-SQL, generative AI
responses over trusted content through retrieval augmented generation (RAG),
synthetic-data generation using large language models, context-aware
conversations, summarization, translation, and related Select AI functionality.
The SDK provides Java interfaces, typed request models, and runnable samples
that bridge Java applications with the `DBMS_CLOUD_AI` PL/SQL package.

The public API includes clients and models for:

- Profiles, credentials, conversations, and vector indexes
- Natural-language SQL generation, execution, narration, and explanation, as
  well as context-aware chat
- Retrieval augmented generation and vector-index management
- Summarization, translation, feedback, and synthetic-data generation
- Connection management through `DataSource` or SDK-owned JDBC connections
- Administrative package privileges, network ACLs, and Select AI data access

## Supported environments

Select AI for Java version `1.0.0` has been validated with Oracle Autonomous AI
Database 26ai and Oracle Autonomous AI Database 19c. Other Oracle Database
platforms may work with the SDK but have not been validated.

The SDK requires JDK 17 or later. Maven 3.8 or later is recommended for
building the project.

## Installation

The SDK is currently distributed as a source-based release. It is not yet
published to Maven Central, an Oracle Maven repository, or a GitHub package or
release asset. The source repository is [Oracle Java Select AI on GitHub](https://github.com/oracle/java-select-ai).

There is currently no approved downloadable release JAR, SHA-256 checksum
file, or public/internal artifact repository location for this release. The
JAR produced by the build is a local build artifact. When an approved binary
distribution is available, this section will provide its release asset,
checksum file, POM, sources, and Javadoc locations.

Build and install the SDK locally from the repository root:

```bash
mvn clean install
```

The local Maven coordinates are:

```text
com.oracle.database.selectai:select-ai-java:1.0.0
```

After the local install, an application can declare:

```xml
<dependency>
    <groupId>com.oracle.database.selectai</groupId>
    <artifactId>select-ai-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

The dependency declaration resolves only after the artifact has been installed
locally or made available through an application-managed repository. The SDK
JAR is also available at `target/select-ai-java-1.0.0.jar` after the build.

### Direct JAR usage and runtime dependencies

The SDK JAR is a plain JAR and does not bundle third-party dependencies. For
direct JAR execution, copy the runtime dependencies resolved from `pom.xml`
into `target/dependency`:

```bash
mvn clean install
mvn -DincludeScope=runtime \
    -DoutputDirectory=target/dependency \
    dependency:copy-dependencies
```

The SDK runtime classpath includes:

| Dependency | Purpose |
| --- | --- |
| `com.oracle.database.jdbc:ojdbc11:23.26.1.0.0` | Oracle JDBC Thin driver |
| `com.oracle.database.security:oraclepki:23.26.1.0.0` | Oracle wallet and security support |
| `org.slf4j:slf4j-api:2.0.17` | Logging API used by the SDK |
| `com.fasterxml.jackson.core:jackson-databind:2.22.0` | JSON serialization and deserialization |
| Dependencies resolved transitively by Maven | Additional libraries required by the direct dependencies, including Jackson core/annotations and Oracle security support dependencies |

Use the SDK JAR and the copied dependencies on the application classpath:

```bash
java -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" \
    com.example.Application
```

On Windows PowerShell, use `;` instead of `:` in the classpath:

```powershell
java -cp "target/select-ai-java-1.0.0.jar;target/dependency/*" `
    com.example.Application
```

The SDK does not include an SLF4J logging provider. Applications must provide
and configure their preferred SLF4J 2.x provider. Standalone samples use the
`samples` Maven profile, which adds `slf4j-simple` for sample execution:

```bash
mvn -Psamples \
    -DincludeScope=runtime \
    -DoutputDirectory=target/dependency \
    dependency:copy-dependencies
```

The equivalent PowerShell command is:

```powershell
mvn -Psamples `
    -DincludeScope=runtime `
    -DoutputDirectory=target/dependency `
    dependency:copy-dependencies
```

For local artifact verification only, calculate a SHA-256 checksum after
building. Use the command available on your platform:

```bash
# macOS
shasum -a 256 target/select-ai-java-1.0.0.jar

# Linux and systems with GNU Coreutils
sha256sum target/select-ai-java-1.0.0.jar
```

On Windows PowerShell:

```powershell
Get-FileHash target/select-ai-java-1.0.0.jar -Algorithm SHA256
```

## Prerequisites and database setup

Before using the SDK, configure an Oracle Autonomous AI Database or another
supported Oracle Database environment. The required setup depends on the
database deployment and selected feature; do not apply every administrative
configuration to every application.

| Feature | Typical setup | Configuration owner |
| --- | --- | --- |
| Profile, conversation, generation, summarization, translation, and feedback | Access to the applicable `DBMS_CLOUD_AI` APIs and referenced database objects | Database administrator |
| Credential creation and deletion | Access to the required `DBMS_CLOUD` APIs | Database administrator |
| Profile-based SQL generation | Access to the tables or views referenced by the profile | Database administrator or schema owner |
| Vector-index creation | Readable source documents, Object Storage access, and a valid provider credential when required | Application owner and database administrator |
| OCI, Object Storage, or other external providers | Valid database credential and provider-specific configuration | Application owner and database administrator |
| `DatabaseAdmin` operations | Explicit administrative privileges for package grants, network ACL changes, or Select AI data-access configuration | Authorized database administrator |

### Database access

The runtime database user must have:

- JDBC connection access.
- Permission to execute the required `DBMS_CLOUD_AI` APIs.
- Permission to execute `DBMS_CLOUD` APIs when the application creates or
  deletes database credentials through the SDK.
- Access to the database tables, views, directories, or other objects
  referenced by profiles, prompts, or vector indexes.

The exact privileges vary by Oracle Database release, deployment, schema
ownership, and selected operation. A database administrator should grant only
the permissions required by the application.

### External provider and network configuration

Operations that call an external AI provider, Object Storage, or another
endpoint may require:

- A database credential created with `DBMS_CLOUD.CREATE_CREDENTIAL` or through
  the SDK `Credential` API.
- Provider-specific configuration such as an OCI compartment, model, region,
  endpoint, or Object Storage URI.
- Outbound connectivity configuration appropriate for the deployment.

In Oracle Autonomous AI Database, some service and network configuration may be
managed by the service. In other Oracle Database deployments, a database
administrator may need to configure network ACLs, wallets, TLS, DNS, or
outbound access. The SDK does not require every application to manage network
ACLs directly.

Follow the applicable Oracle documentation for the database deployment and
`DBMS_CLOUD_AI` feature being used:

- [DBMS_CLOUD_AI package documentation](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html)
- [Select AI Retrieval Augmented Generation and vector indexes](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/select-ai-retrieval-augmented-generation.html)

### Administrative operations

`DatabaseAdmin` is not required for normal profile, conversation, credential,
vector-index, or generation operations. Use it only in explicitly authorized
setup or administrative workflows.

`DatabaseAdmin` operations can:

- Grant or revoke execution privileges on Select AI-related packages.
- Grant or revoke HTTP or network ACL access.
- Enable or disable Select AI data access.

These operations may affect other database users or database-wide security
configuration. Use a separate least-privilege runtime user for normal
application access.

### Sample prerequisites

Some samples require additional database objects, credentials, provider
access, or external documents. See [`samples/README.md`](samples/README.md)
before running a sample. Samples that reference Oracle sample schema objects,
such as `SH.CUSTOMERS` or `SH.COUNTRIES`, require those objects to be installed
and visible to the configured database user. The `SH` schema is not guaranteed
to exist in every Oracle Autonomous AI Database or Oracle Database instance.

## Quick start

The following complete example opens an existing database profile, sends a chat
prompt, prints the response, and handles SDK validation/database failures. The
profile named by `SELECTAI_PROFILE_NAME` must already exist and be accessible to
the configured database user. To create a profile first, see the
[CreateProfileSample source](doc/apidocs/src-html/com/oracle/database/selectai/samples/profile/CreateProfileSample.html).

> **Connection ownership:** This example uses `DbConnectionConfig` mode. In
> this mode, `SelectAI.create(...)` opens and owns one JDBC connection, and
> try-with-resources closes that SDK-owned connection. When
> `SelectAI.create(DataSource)` is used instead, the SDK borrows a connection
> for each operation and returns it by closing it after use; closing `SelectAI`
> does not close the caller-owned `DataSource`.

Set the environment variables before running the example.

```java
import com.oracle.database.selectai.Profile;
import com.oracle.database.selectai.SelectAI;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;

public final class QuickStart {
    private QuickStart() {
    }

    public static void main(String[] args) {
        try {
            DbConnectionConfig connectionConfig = DbConnectionConfig.builder()
                    .dbUser(required("SELECTAI_DB_USER"))
                    .dbPassword(required("SELECTAI_DB_PASSWORD"))
                    .jdbcUrl(required("SELECTAI_JDBC_URL"))
                    .build();

            try (SelectAI selectAI = SelectAI.create(connectionConfig)) {
                Profile profile = selectAI.profile(required("SELECTAI_PROFILE_NAME"));
                String response = profile.chat("What is Select AI?");
                System.out.println(response);
            }
        } catch (IllegalArgumentException | IllegalStateException | SelectAIException exception) {
            System.err.println("Select AI request failed: " + exception.getMessage());
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
```

Required environment variables:

Bash:

```bash
export SELECTAI_DB_USER="<database-user>"
export SELECTAI_DB_PASSWORD="<database-password>"
export SELECTAI_JDBC_URL="jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"
export SELECTAI_PROFILE_NAME="<existing-profile-name>"
```

PowerShell:

```powershell
$env:SELECTAI_DB_USER = "<database-user>"
$env:SELECTAI_DB_PASSWORD = "<database-password>"
$env:SELECTAI_JDBC_URL = "jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"
$env:SELECTAI_PROFILE_NAME = "<existing-profile-name>"
```

Build and run the Quick Start class from the repository root. The commands
below use the `samples` Maven profile so the standalone class has an SLF4J
logging provider. Place the Java example above in a file named `QuickStart.java`
before running these commands. SDK applications should provide their own SLF4J
provider.

Bash:

```bash
mvn clean install
mvn -Psamples \
    -DincludeScope=runtime \
    -DoutputDirectory=target/dependency \
    dependency:copy-dependencies

mkdir -p quickstart-out
javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" \
    -d quickstart-out QuickStart.java
java -cp "quickstart-out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
    QuickStart
```

PowerShell:

```powershell
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies

New-Item -ItemType Directory -Force quickstart-out | Out-Null
javac --release 17 -cp "target/select-ai-java-1.0.0.jar;target/dependency/*" `
    -d quickstart-out QuickStart.java
java -cp "quickstart-out;target/select-ai-java-1.0.0.jar;target/dependency/*" `
    QuickStart
```

The response may contain customer data or generated content. Applications
should apply their own output handling, redaction, and logging policies.

## Connection management

`SelectAI` supports two connection modes: `DataSource` and
`DbConnectionConfig`. `DbConnection` is a separate standalone
connection-owner API.

Resource objects such as `Profile`, `Credential`, `Conversation`, and
`VectorIndex` do not own a JDBC connection; they use the connection behavior of
their parent client.

### DataSource

Use `SelectAI.create(DataSource)` for application servers, Spring/Jakarta
applications, and multi-threaded services. Pass an application-managed or
pooled `DataSource` to the factory.

For each SDK operation, the SDK borrows a connection from the `DataSource`,
executes the operation, and closes that operation connection. With a connection
pool, closing normally returns the connection to the pool. The SDK does not
own or close the `DataSource`.

`SelectAI.close()` is safe and idempotent for a DataSource-backed client. It
does not close or modify the application-owned `DataSource`; operation-level
connections are already closed after each SDK operation.

Connections are obtained lazily from the `DataSource` when an SDK operation
needs one.

### DbConnectionConfig

Use `DbConnectionConfig` when the SDK should open and own one JDBC connection.
Use environment variables for credentials and connection values:

```java
DbConnectionConfig config = DbConnectionConfig.builder()
        .dbUser(System.getenv("SELECTAI_DB_USER"))
        .dbPassword(System.getenv("SELECTAI_DB_PASSWORD"))
        .jdbcUrl(System.getenv("SELECTAI_JDBC_URL"))
        .build();

try (SelectAI selectAI = SelectAI.create(config)) {
    Profile profile = selectAI.profile(System.getenv("SELECTAI_PROFILE_NAME"));
    System.out.println(profile.chat("What is Select AI?"));
}
```

The SDK opens one connection when the client is created and reuses it for
operations from that client. The SDK owns the connection and closes it when
`SelectAI.close()` is called. This mode should be treated as single-threaded
unless the application synchronizes access externally.

`SelectAI.getConnection()` is available only in this mode. It returns the
SDK-owned connection for custom SQL or PL/SQL. Applications must not close the
returned connection directly or share it concurrently; close the owning
`SelectAI` client instead.

`DbConnection.create(DbConnectionConfig)` is a separate standalone
connection-owner API. It eagerly opens one SDK-owned JDBC connection when
`DbConnection.create(...)` is called. It follows the same ownership rule:
close the returned `DbConnection` when it is no longer needed.

### Session and callback behavior

`Profile.chatSession(...)` creates a session that reuses the profile's
connection mode. A `Session` does not retain a separate JDBC connection.
Each session operation uses the profile's connection provider. Closing a
session marks it closed and, when created with `deleteOnClose=true`, drops the
associated conversation. Closing a session does not close the parent
`SelectAI` connection or the application-owned `DataSource`.

### Transaction boundaries

The SDK does not explicitly perform commit or rollback operations and does not
manage transaction boundaries. It does not change the connection's
`autoCommit` setting.

When `autoCommit=false`, transaction behavior remains the responsibility of the
connection owner. State-changing operations may remain part of the current
transaction, or an underlying Oracle API may commit or roll back internally;
the SDK does not add a commit or rollback around the operation.

In DataSource mode, closing a borrowed connection normally returns it to the
pool. The pool may reset or roll back connection state according to its own
configuration, so applications must not rely on uncommitted work being visible
across separate SDK operations. In `DbConnectionConfig` mode, operations reuse
the same connection until `SelectAI.close()`.

### SDK execution options

Use `SelectAIOptions` to configure SDK-level JDBC execution behavior without
embedding those settings in connection identity. Configure query timeout with
`SelectAIOptions.queryTimeoutSeconds(...)`. See
[Error handling and timeouts](#error-handling-and-timeouts) for timeout
semantics and limitations.

```java
SelectAIOptions options = SelectAIOptions.builder()
        .queryTimeoutSeconds(300)
        .build();

SelectAI selectAI = SelectAI.create(config, options);
SelectAI pooledSelectAI = SelectAI.create(dataSource, options);
```

### Threading guidance

For multi-threaded applications, prefer DataSource mode. It can share one
`SelectAI` instance across threads for independent operations because each
operation borrows its own connection. Do not concurrently mutate or run
state-changing operations on the same resource object without caller-side
synchronization. `DbConnectionConfig` mode uses one connection and should be
treated as single-threaded unless access is synchronized externally.

For more detail, see [`doc/datasource-connection-support.md`](doc/datasource-connection-support.md).

## Examples

Runnable examples are organized under [`samples/`](samples/) by feature:

- `profile`: generation, chat sessions, attributes, summarization, translation,
  feedback, and synthetic data
- `conversation`: lifecycle, attributes, and conversation prompts
- `vectorindex`: lifecycle, metadata, and updates
- `credential`: database credential creation and deletion
- `databaseadmin`: privileged package, network ACL, and data-access operations
- `datasource`: DataSource-backed clients

See [`samples/README.md`](samples/README.md) for prerequisites and commands.

## Resource lifecycle

Factories that receive a resource name or ID open an existing database
resource. Factories that receive creation attributes return an in-memory
configured object; they do not persist it until `create()` is called.

After successful creation, resource-specific operations can be used. Database
metadata getters such as profile status/attributes, conversation attributes,
and vector-index status/attributes refresh their values from the database as
documented in the API Javadoc.

## Error handling and timeouts

The SDK uses:

- `IllegalArgumentException` for invalid method arguments.
- `IllegalStateException` when a resource is not in the state required by an
  operation, such as a pending resource used before `create()`.
- `SelectAIException` for JDBC, database, and Select AI execution failures.

`SelectAIOptions.queryTimeoutSeconds(...)` configures the JDBC statement query
timeout applied internally through `Statement.setQueryTimeout()`. The default
is no SDK-level query timeout. A `null` timeout leaves the statement timeout
unchanged, `0` uses JDBC no-timeout behavior, and negative values are rejected.

This is a JDBC/database statement timeout. It is not necessarily equivalent to
an LLM endpoint timeout, HTTP connect/read timeout, cancellation of a remote
model request, or cancellation of a long-running `DBMS_CLOUD_AI` operation.
Whether and when Oracle JDBC interrupts a PL/SQL call depends on the Oracle
JDBC driver and database behavior. Timeout failures are reported through
`SelectAIException` with the underlying JDBC exception preserved when supplied
by the driver.

## Security

Use least-privilege database users and do not expose administrative credentials
to normal application runtimes.

The SDK does not intentionally log passwords, private keys, access tokens,
credential secret material, full credential configurations, prompts, generated
responses, summary content, or SQL text. Application owners are responsible
for log retention, access control, export, monitoring, and alerting policy.

`CredentialConfig` and `DbConnectionConfig` retain secret values in memory as
strings because Oracle JDBC and database APIs require them. Do not log,
persist, serialize, or include those objects in diagnostics. Configure wallet,
TLS, provider credentials, and network ACLs according to Oracle security
requirements.

> Warning: `DatabaseAdmin` can grant or revoke package privileges, modify
> network ACL access, and change Select AI data-access settings. Restrict its
> use to explicitly authorized setup or administrative workflows.

For vulnerability reporting, see [`SECURITY.md`](SECURITY.md).

## Documentation

The generated API documentation is available at [`doc/apidocs/index.html`](doc/apidocs/index.html).
It documents the public interfaces, models, validation behavior, exceptions,
connection ownership, and resource lifecycle.

Classes under `com.oracle.database.selectai.impl` are internal implementation details and
are not supported as customer-facing APIs. Applications should use public
interfaces and factory methods such as `SelectAI.create(...)`,
`DatabaseAdmin.create(...)`, and `DbConnection.create(...)`.

## Samples

Samples are standalone Java sources and require a Bash-compatible shell for
the commands shown below. The SDK does not bundle runtime dependencies, and
the SDK itself depends on `slf4j-api` rather than a logging provider.

Build the SDK, copy sample runtime dependencies, and compile the samples:

```bash
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies
javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" \
  -d samples/out $(find samples/src/main/java -name "*.java")
```

The `samples` Maven profile adds `slf4j-simple` only for standalone sample
execution. Applications using the SDK should select their own SLF4J backend.

Set the common variables and run a sample:

```bash
export SELECTAI_DB_USER="<database-user>"
export SELECTAI_DB_PASSWORD="<database-password>"
export SELECTAI_JDBC_URL="jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.profile.ListProfilesSample
```

The commands above use Bash and Unix-style classpath separators. On Windows,
use PowerShell and a semicolon (`;`) in the classpath:

```powershell
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies

$sampleSources = Get-ChildItem samples/src/main/java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
javac --release 17 -cp "target/select-ai-java-1.0.0.jar;target/dependency/*" `
    -d samples/out $sampleSources

$env:SELECTAI_DB_USER = "<database-user>"
$env:SELECTAI_DB_PASSWORD = "<database-password>"
$env:SELECTAI_JDBC_URL = "jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"

java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" `
    com.oracle.database.selectai.samples.profile.ListProfilesSample
```

Each sample class documents additional environment variables and external
dependencies. Some samples require existing profiles, credentials, visible
schema objects, network access, Object Storage content, or administrative
privileges. Review [`samples/README.md`](samples/README.md) before running
state-changing samples.

## Help

- [Generated API documentation](doc/apidocs/index.html)
- [Sample instructions](samples/README.md)
- [Connection and transaction guidance](doc/datasource-connection-support.md)
- [Supported Oracle Select AI documentation](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html)
- [Issue reporting](https://github.com/oracle/java-select-ai/issues)
- [Releases and release notes](https://github.com/oracle/java-select-ai/releases)

For non-security issues, use GitHub Issues with the SDK version, database
version, relevant operation, sanitized error details, and reproducible steps.
Do not include passwords, private keys, access tokens, or customer data.

## Development

Repository layout, build and test workflows, Javadoc generation, sample
development, and Java implementation guidelines are documented in
[`DEVELOPMENT.md`](DEVELOPMENT.md).

## Contributing

This project welcomes contributions from the community. Before submitting a
pull request, please review the [contribution guide](CONTRIBUTING.md), including
the Oracle Contributor Agreement requirements.

## License

Copyright (c) 2026, Oracle and/or its affiliates.

This project is licensed under the Universal Permissive License v1.0. See
[LICENSE.txt](LICENSE.txt) for details. Third-party notices are available in
[THIRD_PARTY_LICENSES.txt](THIRD_PARTY_LICENSES.txt).
