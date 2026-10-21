# Select AI for Java

## Table of Contents

- [Overview](#overview)
- [What's new in 1.1.0](#whats-new-in-110)
- [Supported environments](#supported-environments)
- [Installation](#installation)
- [Prerequisites and database setup](#prerequisites-and-database-setup)
- [Quick start](#quick-start)
- [Run team quick start](#run-team-quick-start)
- [Team definition import and export](#team-definition-import-and-export)
- [Agent runtime variables](#agent-runtime-variables)
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
`DBMS_CLOUD_AI` and `DBMS_CLOUD_AI_AGENT`.

Select AI for Java enables Java developers to use Oracle Autonomous AI Database
Select AI from Java applications. It supports natural-language-to-SQL (NL2SQL) query
generation over database data, also known as text-to-SQL, generative AI
responses over trusted content through retrieval augmented generation (RAG),
synthetic-data generation using large language models, context-aware
conversations, summarization, translation, and related Select AI functionality.
The SDK provides Java interfaces, typed request models, and runnable samples
that bridge Java applications with the `DBMS_CLOUD_AI` and
`DBMS_CLOUD_AI_AGENT` PL/SQL packages.

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

Version `1.1.0` adds Java support for Oracle Database `DBMS_CLOUD_AI_AGENT`:

- A dedicated `SelectAIAgent` client for agent orchestration.
- Typed Java APIs for agents, tasks, tools, and teams, including their
  attributes and status values.
- Agent, task, tool, and team attribute updates through typed
  `setAttribute(...)` and `setAttributes(...)` operations.
- Agent resource lifecycle operations, metadata loading, listing, and
  enable/disable or drop operations.
- Team and tool execution through `RUN_TEAM` and `RUN_TOOL`, including runtime
  parameters and conversation identifiers for resumable team runs.
- Shared-team access management through `SelectAIAgent` and `Team` grant and
  revoke operations.
- Team state management, including typed conversation-ID convenience methods,
  and team definition export/import operations.
- Team import specifications supplied as JSON-compatible Java maps or raw JSON
  strings, and typed export parameters through `ExportTeamParams`.
- Session runtime-variable access through `getVariable(...)` and
  `setVariable(...)`.
- Agent samples, API documentation, class diagrams, and integration-test
  support under the agent sample and documentation areas.

The existing `SelectAI` APIs for profiles, credentials, conversations, vector
indexes, and other `DBMS_CLOUD_AI` operations remain available.

## Supported environments

Select AI for Java version `1.1.0` has been validated for the following
database and service environments:

| Database or service environment | Java 1.1.0 release status | Scope |
| --- | --- | --- |
| Oracle Autonomous AI Database 26ai | Validated | Select AI and `DBMS_CLOUD_AI_AGENT` operations covered by the release test matrix. |
| Oracle Autonomous AI Database 19c | Validated | Select AI and `DBMS_CLOUD_AI_AGENT` operations covered by the release test matrix, subject to the capabilities enabled by the database service. |
| Other Oracle AI Database or Oracle Database releases | Not certified for Java 1.1.0 | The package may be available on some releases, but those combinations are outside this release's validation scope. |

The [Oracle Select AI capability matrix](https://docs.oracle.com/en/database/oracle/oracle-database/26/saicm/index.html)
shows database-release differences for individual Select AI features and
`DBMS_CLOUD_AI_AGENT` subprograms. A database release having a listed package
capability does not by itself make that release certified for this Java SDK.
Individual providers, profile attributes, vector-index features, built-in
tools, and agent features remain subject to the capability matrix and the
database service configuration.

The SDK does not probe `DBMS_CLOUD_AI_AGENT` support during client
initialization. With `DbConnectionConfig`, client creation validates the
configuration and opens the JDBC connection; with `DataSource`, client
creation validates the `DataSource` and defers JDBC access until an operation
needs a connection. If the connected database does not provide the required
package, views, privileges, or operation-specific capability, the affected
operation fails with `SelectAIException` from JDBC or Oracle Database. A
successful client initialization therefore does not certify that every agent
operation is supported.

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
| `com.fasterxml.jackson.core:jackson-databind:2.22.3` | JSON serialization and deserialization |
| Dependencies resolved transitively by Maven | Additional libraries required by the direct dependencies, including Jackson core/annotations and Oracle security support dependencies |

Use the SDK JAR and the copied dependencies on the application classpath:

```bash
java -cp "target/select-ai-1.1.0.jar:target/dependency/*" \
    com.example.Application
```

On Windows PowerShell, use `;` instead of `:` in the classpath:

```powershell
java -cp "target/select-ai-1.1.0.jar;target/dependency/*" `
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

For a published artifact, use the Maven Central artifact page to review the
available repository checksums and signatures. For local artifact verification
only, calculate a SHA-256 checksum after building. Use the command available
on your platform:

```bash
# macOS
shasum -a 256 target/select-ai-1.1.0.jar

# Linux and systems with GNU Coreutils
sha256sum target/select-ai-1.1.0.jar
```

On Windows PowerShell:

```powershell
Get-FileHash target/select-ai-1.1.0.jar -Algorithm SHA256
```

## Prerequisites and database setup

Before using the SDK, configure an Oracle Autonomous AI Database or another
supported Oracle Database environment. The required setup depends on the
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

The exact privileges vary by Oracle Database release, deployment, schema
ownership, and selected operation. A database administrator should grant only
the permissions required by the application.

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
managed by the service. In other Oracle Database deployments, a database
administrator may need to configure network ACLs, wallets, TLS, DNS, or
outbound access. The SDK does not require every application to manage network
ACLs directly.

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

The following complete example opens an existing AI Agent team, sends a team
prompt with a conversation ID, prints the team response, and handles SDK
validation and database failures. The team named by
`SELECTAI_AGENT_TEAM_NAME` must already exist and be accessible to the
configured database user. Its referenced agents, tasks, tools, and Select AI
profile must also exist and be accessible. The conversation ID named by
`SELECTAI_AGENT_TEAM_CONVERSATION_ID` must identify an existing database
conversation. To create the team, see the
[CreateTeamSample source](samples/src/main/java/com/oracle/database/selectai/samples/agent/CreateTeamSample.java).
To create a conversation, see the
[CreateConversationSample source](samples/src/main/java/com/oracle/database/selectai/samples/conversation/CreateConversationSample.java).

This example does not create or delete a team, agent, task, tool, profile, or
conversation. Before running it, grant the configured database user the
required execution privileges. For a team owned by another database user, the
team owner must grant the consumer access before the consumer runs the
qualified team name. This can be done from Java with
`SelectAIAgent#grantTeamAccess(String, String)` or
`Team#grantAccess(String)`, or with the corresponding
`DBMS_CLOUD_AI_AGENT.GRANT_TEAM_ACCESS` procedure. The connected owner must
own the team; a database administrator is not inherently required.

The conversation identifier must identify an existing database conversation
created with `Conversation#create()` or `DBMS_CLOUD_AI.CREATE_CONVERSATION`.
An arbitrary client-generated UUID is not sufficient. Reuse the same identifier
when resuming a team run that is waiting for human input, including when the
next call uses a different pooled database connection.

The conversation lifecycle is separate from team execution: create or reuse a
database conversation through the core `SelectAI` and `Conversation` APIs,
pass its database-generated ID to `RunTeamRequest.conversationId(...)`, and
reuse that ID for later `runTeam(...)`,
`getTeamStateByConversationId(...)`, or
`setTeamExecutionState(...)` calls. `runTeam(...)` returns a `TeamRunResult`
containing the database result; it does not create or return a conversation ID.
Drop the conversation
through the core `Conversation` API when it is no longer needed.

Create a new conversation with `SelectAI.conversation(ConversationAttributes)`
followed by `Conversation.create()`. To reuse an existing conversation, open it
with `SelectAI.conversation(String)` and retain the returned database-generated
conversation ID. Do not replace that ID with an arbitrary client-generated
value.

For an owner-scoped team handle, prefer
`Team#getStateByConversationId(String)` and
`Team#setExecutionState(ExecutionStateRequest)`. Use
`ExecutionStateParams.forConversationId(String)` or
`ExecutionStateRequest.Builder#params(ExecutionStateParams)` when constructing
typed `SET_EXECUTION_STATE` parameters. The convenience
`ExecutionStateRequest.Builder#conversationId(String)` method covers the
common case. The typed execution-state model currently supports the required
`conversation_id` parameter explicitly.
For a team granted by another owner, prefer
`SelectAIAgent#getTeamStateByConversationId(String, String)` and
`SelectAIAgent#setTeamExecutionState(String, ExecutionStateRequest)` with the
owner-qualified team name. These methods inspect or update
the run by `conversation_id` without requiring access to the owner's team
metadata.

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

The run uses `DbConnectionConfig` mode. `SelectAIAgent.create(...)` owns one
JDBC connection and closes it when the try-with-resources block ends. Because
the example uses existing database resources, no cleanup command is required.

Team execution is synchronous. A long-running workflow can retain the
operation connection until the database returns a result. Size application
connection pools for the expected concurrent workflow count, configure an
appropriate `SelectAIOptions` query timeout, and retry only when the operation
is known to be safe to repeat. A JDBC statement timeout does not guarantee
cancellation of work already running in the database or at an external model
provider.

`RunTeamRequest` exposes the typed `RunTeamParams` model for the documented
`RUN_TEAM` runtime parameters. It includes `conversationId`,
`instructionVariables`, `attributeVariables`, and `stateVariables`. The
convenience methods on `RunTeamRequest.Builder`, such as
`conversationId(...)` and `stateVariables(...)`, remain available for the
common case. Do not combine those individual methods with `params(...)` in the
same request.

`listTeams()` lists teams owned by the current database user and returns mutable
`Team` handles. A team granted by another owner is not
returned as an owned `Team` handle; use `listAvailableTeams()` to discover
teams available to the calling user, then run a shared team with its
owner-qualified name through `SelectAIAgent.runTeam(...)`. `AvailableTeam`
objects are read-only discovery snapshots backed by
`DBMS_CLOUD_AI_AGENT.LIST_TEAMS()`.

`ToolAttributes` accepts immutable `ToolParams` models for SQL, RAG, web search,
Slack, and email tools. Use `GenericToolParams` for additional tool types;
unknown database parameters are preserved when tools are loaded. See
[Tool parameters](doc/agent-api.md#tool-parameters) and the standalone
[ToolParamsSample](samples/src/main/java/com/oracle/database/selectai/samples/agent/ToolParamsSample.java).
Database-backed creation, update, and loading examples are listed in the
[typed tool parameter samples guide](samples/README.md#typed-tool-parameter-samples).

Tool input metadata uses `List<ToolInput>`, with typed `name` and `description`
fields. See [Tool inputs](doc/agent-api.md#tool-inputs) and the offline
[ToolInputSample](samples/src/main/java/com/oracle/database/selectai/samples/agent/ToolInputSample.java).
For custom-tool creation, updates, loading, and cleanup, see the
[typed tool input samples guide](samples/README.md#typed-tool-input-samples).

## Team definition import and export

`ImportTeamRequest` accepts native JSON-compatible Java maps for team
specifications. `ExportTeamRequest` uses the typed `ExportTeamParams` model;
the documented export option is `overwrite`:

```java
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import java.util.List;
import java.util.Map;

ImportTeamRequest importRequest = ImportTeamRequest.builder(
                "GENAI_PROFILE", "SUPPORT_TEAM")
        .specification(Map.of("agents", List.of()))
        .force(true)
        .build();

ExportTeamRequest exportRequest = ExportTeamRequest.builder(
                "https://objectstorage.example/team.json")
        .params(ExportTeamParams.builder().overwrite(true).build())
        .build();
```

To receive the exported team definition directly as a JSON string, use
`Team.exportDefinition()` for database defaults or
`Team.exportDefinition(ExportTeamParams)` for the typed `overwrite` option.
The `ExportTeamRequest` overload writes the definition to Object Storage or a
database directory instead of returning it to the application.

The database validates the team definition, destination, referenced resources,
and caller privileges.

## Agent runtime variables

`SelectAIAgent` exposes `DBMS_CLOUD_AI_AGENT.GET_VARIABLE` and
`SET_VARIABLE` for namespaced runtime variables. The default scope is
`ephemeral`; passing `null` to `setVariable(...)` clears the variable.

```java
try (SelectAIAgent agentClient = SelectAIAgent.create(connectionConfig)) {
    agentClient.setVariable("state_variables", "request_id", "REQ-123");
    String requestId = agentClient.getVariable("state_variables", "request_id");
    System.out.println(requestId);
    agentClient.setVariable("state_variables", "request_id", null);
}
```

Runtime variables are associated with the current database session. In
`DataSource` mode, separate SDK operations may use different pooled
connections, so applications must not depend on a variable remaining visible
to a later operation unless they control session affinity. The database
validates supported namespaces, names, scopes, and values. See
[GetVariableSample](samples/src/main/java/com/oracle/database/selectai/samples/agent/GetVariableSample.java)
and [SetVariableSample](samples/src/main/java/com/oracle/database/selectai/samples/agent/SetVariableSample.java)
for complete runnable examples.

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
Database authorization. Access to schemas, tables, views, PL/SQL functions, and
other referenced objects is determined by Oracle Database.

Depending on the requested operation and Select AI profile configuration,
prompts, retrieved content, database metadata, SQL context, agent instructions,
task definitions, tool inputs, and runtime parameters may be sent to the
configured AI provider. Generated responses are returned from that provider
through Oracle Database. Applications should configure profiles and providers
according to their data-classification and privacy requirements.

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
[`Select AI Agent API guide`](doc/agent-api.md) covers agent resources.

Classes under `com.oracle.database.selectai.impl` are internal implementation details and
are not supported as customer-facing APIs. Applications should use public
interfaces and factory methods such as `SelectAI.create(...)`,
`SelectAIAgent.create(...)`, `DatabaseAdmin.create(...)`, and
`DbConnection.create(...)`.

## Samples

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
