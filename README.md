# Select AI for Java

## Table of Contents

- [Overview](#overview)
- [What's new in 1.1.0](#whats-new-in-110)
- [Supported environments](#supported-environments)
- [Installation](#installation)
- [Prerequisites and database setup](#prerequisites-and-database-setup)
- [Quick start](#quick-start)
- [Run team quick start](#run-team-quick-start)
- [Connection management](#connection-management)
  - [DataSource](#datasource)
  - [DbConnectionConfig](#dbconnectionconfig)
- [Resource lifecycle](#resource-lifecycle)
- [Error handling and timeouts](#error-handling-and-timeouts)
- [Security](#security)
- [Documentation](#documentation)
- [Examples](#examples)
- [Help](#help)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Overview

Oracle Select AI for Java provides Java APIs for Select AI capabilities exposed
through `DBMS_CLOUD_AI` and `DBMS_CLOUD_AI_AGENT`.

Version `1.1.0` is supported for Oracle Autonomous AI Database 26ai and 19c.

The SDK supports natural-language-to-SQL (NL2SQL) query
generation over database data, also known as text-to-SQL, generative AI
responses over trusted content through retrieval augmented generation (RAG),
synthetic-data generation using large language models, context-aware
conversations, summarization, translation, and related Select AI functionality.
It provides Java interfaces, typed request models, and runnable samples for
integrating these capabilities into Java applications.

The public API includes clients and models for:

- Profiles, credentials, conversations, and vector indexes
- Agents, tasks, tools, and teams through `SelectAIAgent`
- Natural-language SQL generation, execution, narration, and explanation, as
  well as context-aware chat
- Retrieval augmented generation and vector-index management
- Summarization, translation, feedback, and synthetic-data generation
- Connection management through `DataSource` or SDK-owned JDBC connections
- Administrative package privileges, network ACLs, and Select AI data-access
  configuration through `DatabaseAdmin`
- Owner-scoped shared-team access management through
  `SelectAIAgent.grantTeamAccess(...)` / `revokeTeamAccess(...)` and
  `Team.grantAccess(...)` / `revokeAccess(...)`

## What's new in 1.1.0

Version `1.1.0` adds Java support for the Oracle Autonomous AI Database
`DBMS_CLOUD_AI_AGENT` package:

- **Agent Framework resources:** Create and manage agents, tasks, tools, and
  teams through `SelectAIAgent`.
- **Team runs and conversations:** Execute teams, preserve conversation IDs
  across calls, and inspect or update execution state.
- **Shared teams:** Discover available teams, manage owner-scoped access
  grants, and run teams shared by other users.
- **Team definition import and export:** Import and export team definitions
  using Java APIs.
- **Typed tool configuration and runtime parameters:** Configure tool inputs
  and parameters, and supply instruction, attribute, and state variables for
  team runs.

The existing `SelectAI` APIs for profiles, credentials, conversations, vector
indexes, and other `DBMS_CLOUD_AI` operations remain available.

See [Releases and release notes](https://github.com/oracle/java-select-ai/releases)
for the complete change history.

## Supported environments

Select AI for Java version `1.1.0` is supported for Oracle Autonomous AI
Database 26ai and 19c.

Support for additional Oracle AI Database deployment options is planned.

The [Oracle Select AI capability matrix](https://docs.oracle.com/en/database/oracle/oracle-database/26/saicm/index.html)
shows database-release differences for individual Select AI features and
`DBMS_CLOUD_AI_AGENT` subprograms. A database release having a listed package
capability does not by itself mean that release is supported by this Java SDK.
Individual providers, profile attributes, vector-index features, built-in
tools, and agent features remain subject to the capability matrix and the
database service configuration.

The SDK does not probe `DBMS_CLOUD_AI_AGENT` support during client
initialization. With `DbConnectionConfig`, client creation validates the
configuration and opens the JDBC connection; with `DataSource`, client
creation validates the `DataSource` and defers JDBC access until an operation
needs a connection. If the connected database does not provide the required
package, views, privileges, or operation-specific capability, the affected
operation fails with `SelectAIException` from JDBC or Oracle Autonomous AI
Database. A successful client initialization therefore does not certify that
every agent operation is supported.

The SDK requires JDK 17 or later. Maven 3.8 or later is recommended for
building the project.

## Installation

The source code and development instructions are available in the
[Java Select AI GitHub repository](https://github.com/oracle/java-select-ai).

Select AI for Java version `1.1.0` is published to
[Maven Central](https://central.sonatype.com/artifact/com.oracle.database.selectai/select-ai/1.1.0).

Add the SDK to a Maven application with:

```xml
<dependency>
    <groupId>com.oracle.database.selectai</groupId>
    <artifactId>select-ai</artifactId>
    <version>1.1.0</version>
</dependency>
```

The published JAR, POM, sources, Javadoc, signatures, and repository checksums
are available from the
[Maven Central artifact page](https://central.sonatype.com/artifact/com.oracle.database.selectai/select-ai/1.1.0).

Applications should use the published Maven dependency so Maven resolves the
SDK's transitive runtime dependencies. The SDK provides the SLF4J API but does
not include an SLF4J logging provider; configure the logging provider required
by the application.

### Build from source

Build and install the SDK locally from the repository root when contributing to
the project, running source-based samples, or verifying a local change:

```bash
mvn clean install
```

The local Maven coordinates are:

```text
com.oracle.database.selectai:select-ai:1.1.0
```

The locally built JAR is available at `target/select-ai-1.1.0.jar` after the
build.

For runtime dependencies, explicit classpath commands, and checksum
verification, see
[Running with an explicit classpath](DEVELOPMENT.md#running-with-an-explicit-classpath).

## Prerequisites and database setup

Before using the SDK, configure a supported Oracle Autonomous AI Database
environment. The required setup depends on the
database deployment and selected feature; do not apply every administrative
configuration to every application.

| Feature | Typical setup | Configuration owner |
| --- | --- | --- |
| Profile, conversation, generation, summarization, translation, and feedback | Access to the applicable `DBMS_CLOUD_AI` APIs and referenced database objects | Database administrator |
| Agent, task, tool, and team operations | Access to the applicable `DBMS_CLOUD_AI_AGENT` APIs, required AI profiles, and referenced database objects | Database administrator and application owner |
| Credential creation and deletion | Access to the required `DBMS_CLOUD` APIs | Database administrator |
| Profile-based SQL generation | Access to the tables or views referenced by the profile | Database administrator or schema owner |
| Vector-index creation | Readable source documents, Object Storage access, and a valid provider credential when required | Application owner and database administrator |
| OCI, Object Storage, or other external providers | Valid database credential and provider-specific configuration | Application owner and database administrator |
| `DatabaseAdmin` operations | Explicit administrative privileges for package grants, network ACL changes, or Select AI data-access configuration | Authorized database administrator |

### Database access

The runtime database user must have:

- JDBC connection access.
- Permission to execute the required `DBMS_CLOUD_AI` APIs.
- Permission to execute the required `DBMS_CLOUD_AI_AGENT` APIs when using
  agents, tasks, tools, or teams.
- Permission to execute `DBMS_CLOUD` APIs when the application creates or
  deletes database credentials through the SDK.
- Access to the database tables, views, directories, or other objects
  referenced by profiles, prompts, or vector indexes.

The exact privileges vary by Oracle Autonomous AI Database release, deployment,
schema ownership, and selected operation. A database administrator should grant
only the permissions required by the application.

Agent discovery and existing-resource loading also require the documented
`USER_AI_AGENTS`, `USER_AI_AGENT_TASKS`, `USER_AI_AGENT_TOOLS`, and
`USER_AI_AGENT_TEAMS` metadata views and their corresponding attribute views.
The SDK uses these views internally; application code should use the public
`SelectAIAgent` interfaces rather than query the views directly. The exact
package and view availability remains subject to the supported database
release and capability matrix.

### External provider and network configuration

Operations that call an external AI provider, Object Storage, or another
endpoint may require:

- A database credential created with `DBMS_CLOUD.CREATE_CREDENTIAL` or through
  the SDK `Credential` API.
- Provider-specific configuration such as an OCI compartment, model, region,
  endpoint, or Object Storage URI.
- Outbound connectivity configuration appropriate for the deployment.

In Oracle Autonomous AI Database, some service and network configuration may be
managed by the service. Depending on the deployment and service configuration,
a database administrator may need to configure network ACLs, wallets, TLS,
DNS, or outbound access. The SDK does not require every application to manage
network ACLs directly.

When an existing profile is loaded or profiles are listed, the SDK preserves
database-defined values for `provider`, `oci_apiformat`, and
`object_list_mode`, including values introduced by a newer database release.
Profile creation and update builders continue to validate values supported by
the SDK release; the database remains responsible for version-specific
semantic validation.

Follow the applicable Oracle documentation for the database deployment and
`DBMS_CLOUD_AI` feature being used:

- [DBMS_CLOUD_AI package documentation](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html)
- [DBMS_CLOUD_AI_AGENT package documentation](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-agent-package.html)
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

Team owners can grant or revoke execution access to an agent team through the
owner-scoped `SelectAIAgent` and `Team` APIs. Team sharing is authorized by
the database for the team owner and does not inherently require a DBA account.

### Sample prerequisites

Some samples require additional database objects, credentials, provider
access, or external documents. See [`samples/README.md`](samples/README.md)
before running a sample. Samples that reference Oracle sample schema objects,
such as `SH.CUSTOMERS` or `SH.COUNTRIES`, require those objects to be installed
and visible to the configured database user. The `SH` schema is not guaranteed
to exist in every Oracle Autonomous AI Database instance.

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
javac --release 17 -cp "target/select-ai-1.1.0.jar:target/dependency/*" \
    -d quickstart-out QuickStart.java
java -cp "quickstart-out:target/select-ai-1.1.0.jar:target/dependency/*" \
    QuickStart
```

PowerShell:

```powershell
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies

New-Item -ItemType Directory -Force quickstart-out | Out-Null
javac --release 17 -cp "target/select-ai-1.1.0.jar;target/dependency/*" `
    -d quickstart-out QuickStart.java
java -cp "quickstart-out;target/select-ai-1.1.0.jar;target/dependency/*" `
    QuickStart
```

The response may contain customer data or generated content. Applications
should apply their own output handling, redaction, and logging policies.

## Run team quick start

This example runs an existing AI Agent team, prints its response, and handles
SDK validation and database failures. The team named by
`SELECTAI_AGENT_TEAM_NAME` and its referenced agents, tasks, tools, and Select
AI profile must already exist and be accessible to the configured database user.

Create or reuse a database conversation through the core `SelectAI` API and
supply its database-generated ID as `SELECTAI_AGENT_TEAM_CONVERSATION_ID`.
Do not substitute an arbitrary UUID. Preserve this ID for subsequent runs,
including calls using different pooled connections. `runTeam()` returns a
`TeamRunResult` containing the team result, not a conversation ID.

```java
import com.oracle.database.selectai.agent.SelectAIAgent;
import com.oracle.database.selectai.model.DbConnectionConfig;
import com.oracle.database.selectai.model.SelectAIException;
import com.oracle.database.selectai.model.agent.RunTeamRequest;
import com.oracle.database.selectai.model.agent.TeamRunResult;

public final class RunTeamQuickStart {
    private RunTeamQuickStart() {
    }

    public static void main(String[] args) {
        try {
            DbConnectionConfig connectionConfig = DbConnectionConfig.builder()
                    .dbUser(required("SELECTAI_DB_USER"))
                    .dbPassword(required("SELECTAI_DB_PASSWORD"))
                    .jdbcUrl(required("SELECTAI_JDBC_URL"))
                    .build();

            RunTeamRequest request = RunTeamRequest.builder()
                    .userPrompt(required("SELECTAI_AGENT_TEAM_PROMPT"))
                    .conversationId(required("SELECTAI_AGENT_TEAM_CONVERSATION_ID"))
                    .build();

            try (SelectAIAgent selectAIAgent = SelectAIAgent.create(connectionConfig)) {
                TeamRunResult result = selectAIAgent.runTeam(
                        required("SELECTAI_AGENT_TEAM_NAME"), request);
                System.out.println(result.getRawValue());
            }
        } catch (IllegalArgumentException | IllegalStateException | SelectAIException exception) {
            System.err.println("Team run failed: " + exception.getMessage());
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

This example does not create or delete a team, agent, task, tool, profile, or
conversation. For setup, see the
[CreateTeamSample source](samples/src/main/java/com/oracle/database/selectai/samples/agent/CreateTeamSample.java)
and the
[CreateConversationSample source](samples/src/main/java/com/oracle/database/selectai/samples/conversation/CreateConversationSample.java).

Before running it, ensure the configured database user has the required
execution privileges. For a shared team, the owner must grant the consumer
access before the consumer runs the owner-qualified team name. See
[Managing Shared-Team Access](doc/agent-api.md#managing-shared-team-access).

For conversation lifecycle, state management, and resuming a workflow waiting
for human input, see [Conversation workflow](doc/agent-api.md#conversation-workflow).

Required environment variables:

Bash:

```bash
export SELECTAI_DB_USER="<database-user>"
export SELECTAI_DB_PASSWORD="<database-password>"
export SELECTAI_JDBC_URL="jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"
export SELECTAI_AGENT_TEAM_NAME="<existing-team-name>"
export SELECTAI_AGENT_TEAM_PROMPT="<team-prompt>"
export SELECTAI_AGENT_TEAM_CONVERSATION_ID="<existing-database-conversation-id>"
```

PowerShell:

```powershell
$env:SELECTAI_DB_USER = "<database-user>"
$env:SELECTAI_DB_PASSWORD = "<database-password>"
$env:SELECTAI_JDBC_URL = "jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"
$env:SELECTAI_AGENT_TEAM_NAME = "<existing-team-name>"
$env:SELECTAI_AGENT_TEAM_PROMPT = "<team-prompt>"
$env:SELECTAI_AGENT_TEAM_CONVERSATION_ID = "<existing-database-conversation-id>"
```

Save the Java example above as `RunTeamQuickStart.java` in the repository root.
Build and run it from the repository root. The commands below compile the
example from a clean checkout and copy the runtime dependencies required by the
SDK.

Bash:

```bash
mvn clean install
mvn -Psamples \
    -DincludeScope=runtime \
    -DoutputDirectory=target/dependency \
    dependency:copy-dependencies

mkdir -p run-team-quickstart-out
javac --release 17 \
    -cp "target/select-ai-1.1.0.jar:target/dependency/*" \
    -d run-team-quickstart-out RunTeamQuickStart.java
java -cp "run-team-quickstart-out:target/select-ai-1.1.0.jar:target/dependency/*" \
    RunTeamQuickStart
```

PowerShell:

```powershell
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies

New-Item -ItemType Directory -Force run-team-quickstart-out | Out-Null
javac --release 17 `
    -cp "target/select-ai-1.1.0.jar;target/dependency/*" `
    -d run-team-quickstart-out RunTeamQuickStart.java
java -cp "run-team-quickstart-out;target/select-ai-1.1.0.jar;target/dependency/*" `
    RunTeamQuickStart
```

This example uses `DbConnectionConfig` mode. The SDK-owned JDBC connection
closes when the try-with-resources block ends. No database-resource cleanup is
required because the example uses existing resources.

Team execution is synchronous and retains a JDBC connection until the call
completes. See the [Agent API guide](doc/agent-api.md) for execution guidance,
runtime parameters, team discovery, and tool configuration.

## Connection management

`SelectAI` and `SelectAIAgent` support two connection modes: `DataSource` and
`DbConnectionConfig`. `DbConnection` is a separate standalone connection-owner
API.

Resource objects such as `Profile`, `Credential`, `Conversation`, `VectorIndex`,
`Agent`, `Task`, `Tool`, and `Team` do not own a JDBC connection; they use the
connection behavior of their parent client.

### DataSource

Use `SelectAI.create(DataSource)` or `SelectAIAgent.create(DataSource)` for
application servers, Spring/Jakarta applications, and multi-threaded services.
Pass an application-managed or pooled `DataSource` to the factory.

For each SDK operation, the SDK borrows a connection from the `DataSource`,
executes the operation, and closes that operation connection. With a connection
pool, closing normally returns the connection to the pool. The SDK does not
own or close the `DataSource`.

With a `DataSource`, each SDK operation may use a different pooled connection.
Applications should not depend on uncommitted session state being available to
a later operation.

`SelectAI.close()` is safe and idempotent for a DataSource-backed client. It
does not close or modify the application-owned `DataSource`; operation-level
connections are already closed after each SDK operation.
`SelectAIAgent.close()` likewise does not close the application-owned
`DataSource`.

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
`SelectAI.close()` or `SelectAIAgent.close()` is called. This mode should be
treated as single-threaded unless the application synchronizes access
externally.

`SelectAI.getConnection()` is available only in this mode. It returns the
SDK-owned connection for custom SQL or PL/SQL. Applications must not close the
returned connection directly or share it concurrently; close the owning
`SelectAI` client instead.

`SelectAIAgent` does not expose `getConnection()`. For custom JDBC work used
with an agent client, applications should use their application-managed
`DataSource` directly or create a separate `DbConnection` when appropriate.

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

## Resource lifecycle

Factories that receive a resource name or ID open an existing database
resource. Factories that receive creation attributes return an in-memory
configured object; they do not persist it until `create()` is called.

After successful creation, resource-specific operations can be used. Database
metadata getters such as profile status/attributes, conversation attributes,
vector-index status/attributes, and agent-resource status/attributes refresh
their values from the database as documented in the API Javadoc. For an agent,
task, tool, or team, `getDescription()` returns the description supplied or
loaded when the handle was initialized; `getStatus()` refreshes only status.

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

SQL generated or executed through Select AI runs with the privileges of the
connected database user. The SDK does not elevate privileges or bypass Oracle
Autonomous AI Database authorization. Access to schemas, tables, views, PL/SQL
functions, and other referenced objects is determined by Oracle Autonomous AI
Database.

Depending on the requested operation and Select AI profile configuration,
prompts, retrieved content, database metadata, SQL context, agent instructions,
task definitions, tool inputs, and runtime parameters may be sent to the
configured AI provider. Generated responses are returned from that provider
through Oracle Autonomous AI Database. Applications should configure profiles
and providers according to their data-classification and privacy requirements.

Passwords supplied through environment variables are suitable for local
examples only. Production applications should use an approved secret-management
solution, wallet-based authentication, or another secure credential-management
mechanism instead of storing secrets in shell history, deployment scripts, or
plain-text environment files.

Applications should review and sanitize logging configuration before logging
prompts, retrieved content, generated responses, SQL, credentials, JDBC URLs,
or JDBC/Oracle exception details. JDBC and database exceptions may contain SQL
text, provider URLs, object names, or other diagnostic information. Raw
exceptions and stack traces should not be exposed to end users or written to
unrestricted logs.

For AI Agent operations, database authorization also applies to agents, tasks,
tools, teams, custom PL/SQL tools, referenced profiles, Object Storage
locations, and dependent database objects. Access to a shared team does not
automatically grant access to its custom functions, credentials, or referenced
resources.

> Warning: `DatabaseAdmin` can grant or revoke package privileges, modify
> network ACL access, and change Select AI data-access settings. Restrict its
> use to explicitly authorized setup or administrative workflows.

For vulnerability reporting, see [`SECURITY.md`](SECURITY.md).

## Documentation

The generated API documentation is available at [`doc/apidocs/index.html`](doc/apidocs/index.html).
It documents the public interfaces, models, validation behavior, exceptions,
connection ownership, and resource lifecycle. The
[`Select AI Agent API guide`](doc/agent-api.md) covers agent resources, including:

- [Team definition import and export](doc/agent-api.md#team-definition-import-and-export)
- [Agent runtime variables](doc/agent-api.md#runtime-variables)

Classes under `com.oracle.database.selectai.impl` are internal implementation details and
are not supported as customer-facing APIs. Applications should use public
interfaces and factory methods such as `SelectAI.create(...)`,
`SelectAIAgent.create(...)`, `DatabaseAdmin.create(...)`, and
`DbConnection.create(...)`.

## Examples

Runnable examples are organized under [`samples/`](samples/) by feature:

- `profile`: generation, chat sessions, attributes, summarization, translation,
  feedback, and synthetic data
- `conversation`: lifecycle, attributes, and conversation prompts
- `vectorindex`: lifecycle, metadata, and updates
- `credential`: database credential creation and deletion
- `databaseadmin`: privileged package, network ACL, and data-access operations
- `datasource`: DataSource-backed clients
- `agent`: agent, task, tool, and team lifecycle and execution

See [`samples/README.md`](samples/README.md) for prerequisites and commands.
For the agent API, see [`doc/agent-api.md`](doc/agent-api.md).

Samples are standalone Java sources and require a Bash-compatible shell for
the commands shown below. The SDK does not bundle runtime dependencies, and
the SDK itself depends on `slf4j-api` rather than a logging provider.

Build the SDK, copy sample runtime dependencies, and compile the samples:

```bash
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies
javac --release 17 -cp "target/select-ai-1.1.0.jar:target/dependency/*" \
  -d samples/out $(find samples/src/main/java -name "*.java")
```

The `samples` Maven profile adds `slf4j-simple` only for standalone sample
execution. Applications using the SDK should select their own SLF4J backend.
It also includes Oracle UCP for the
[`PooledTeamResumeSample`](samples/src/main/java/com/oracle/database/selectai/samples/agent/PooledTeamResumeSample.java),
which resumes a `WAITING_FOR_HUMAN` workflow in a new process using the saved
conversation ID. UCP is a sample dependency, not an SDK runtime requirement.

Set the common variables and run a sample:

```bash
export SELECTAI_DB_USER="<database-user>"
export SELECTAI_DB_PASSWORD="<database-password>"
export SELECTAI_JDBC_URL="jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.profile.ListProfilesSample
```

The commands above use Bash and Unix-style classpath separators. On Windows,
use PowerShell and a semicolon (`;`) in the classpath:

```powershell
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies

$sampleSources = Get-ChildItem samples/src/main/java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
javac --release 17 -cp "target/select-ai-1.1.0.jar;target/dependency/*" `
    -d samples/out $sampleSources

$env:SELECTAI_DB_USER = "<database-user>"
$env:SELECTAI_DB_PASSWORD = "<database-password>"
$env:SELECTAI_JDBC_URL = "jdbc:oracle:thin:@<service_name>?TNS_ADMIN=<wallet-directory>"

java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" `
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

The test-only SLF4J configuration keeps normal Maven test output quiet. Some
tests intentionally exercise database and validation failure paths; when logs
are enabled, their expected `ERROR` messages may include exception stack
traces. A final `BUILD SUCCESS` means the tests passed. To enable Java
implementation logs at `WARN` during a full build, run:

```bash
mvn -Dorg.slf4j.simpleLogger.log.com.oracle.database.selectai.impl=warn clean install
```

See [`DEVELOPMENT.md`](DEVELOPMENT.md) for selected unit-test and
integration-test logging commands and guidance for distinguishing expected
diagnostics from actual test failures.

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
