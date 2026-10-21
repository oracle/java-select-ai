# Select AI Java SDK Samples

This directory contains standalone Java samples for the Select AI Java SDK.
Each sample class is self-contained: it creates its own database connection
configuration, creates or opens the SDK object it needs, demonstrates a public
API workflow, and prints the result.

Normal sample output is written to standard output so it is easy to read from a
terminal. Errors and diagnostics are written through SLF4J.

Sample programs intentionally print method results and selected request or
metadata values so they can be used as runnable examples and test evidence.
Some sample output can include prompts, generated responses, profile
attributes, vector-index locations, conversation prompt history, or other
customer-controlled values. Do not use this output policy directly in
production applications; apply application-specific logging, redaction,
retention, and access-control requirements.

Commands are shown separately for Bash and PowerShell where shell syntax
differs. Maven commands are the same in both shells. Bash uses `:` as the Java
classpath separator; PowerShell and Windows use `;`.

## Table of Contents

- [Sample Packages](#sample-packages)
- [Prerequisites](#prerequisites)
  - [External dependencies by sample area](#external-dependencies-by-sample-area)
  - [Agent samples](#agent-samples)
  - [Supervisor-led team](#supervisor-led-team)
- [Integration Script Prerequisites](#integration-script-prerequisites)
- [Transaction Behavior](#transaction-behavior)
- [Build the SDK](#build-the-sdk)
- [Copy Runtime Dependencies](#copy-runtime-dependencies)
- [Compile Samples](#compile-samples)
- [Run Samples](#run-samples)
- [Runtime Classpath](#runtime-classpath)
- [Finding a Sample](#finding-a-sample)

## Sample Packages

| Package | Purpose |
| --- | --- |
| `com.oracle.database.selectai.samples.credential` | Samples for `Credential#create()` and `Credential#drop()` |
| `com.oracle.database.selectai.samples.profile` | Samples for `Profile` lifecycle, generation, context-aware chat sessions, attributes, metadata, translation, summarization, and feedback methods |
| `com.oracle.database.selectai.samples.conversation` | Samples for `Conversation` create, list, drop, metadata, attribute, prompt-list, and prompt-delete methods |
| `com.oracle.database.selectai.samples.vectorindex` | Samples for `VectorIndex` create, list, lifecycle, metadata, and update methods |
| `com.oracle.database.selectai.samples.selectai` | Samples for top-level application-facing `SelectAI` factory and resource-opening methods |
| `com.oracle.database.selectai.samples.databaseadmin` | Samples for administrative `DatabaseAdmin` data-access, package-privilege, and network ACL methods |
| `com.oracle.database.selectai.samples.datasource` | Samples for creating `SelectAI` with DataSource-backed connection mode |
| `com.oracle.database.selectai.samples.agent` | Samples for `SelectAIAgent`, `Agent`, `Task`, `Tool`, and `Team` operations backed by `DBMS_CLOUD_AI_AGENT` |

## Prerequisites

Set the database connection environment variables before compiling or running
samples. The agent samples in version `1.1.0` were validated with Oracle
Autonomous AI Database 26ai and Oracle Autonomous AI Database 19c. Other
database releases are not certified for this sample set and may fail when an
operation requires a package, view, privilege, or feature that is unavailable.

```bash
export SELECTAI_DB_USER="<database-user>"
export SELECTAI_DB_PASSWORD="<database-password>"
export SELECTAI_JDBC_URL="jdbc:oracle:thin:@mydb_high?TNS_ADMIN=/path/to/wallet"
```

PowerShell:

```powershell
$env:SELECTAI_DB_USER = "<database-user>"
$env:SELECTAI_DB_PASSWORD = "<database-password>"
$env:SELECTAI_JDBC_URL = "jdbc:oracle:thin:@mydb_high?TNS_ADMIN=C:\path\to\wallet"
```

Samples read additional environment variables for their operations. Common
examples include:

- Profile samples typically use `SELECTAI_PROFILE_NAME`. Profile creation and
  bulk attribute samples also accept optional
  `SELECTAI_PROFILE_ADDITIONAL_INSTRUCTIONS` and `SELECTAI_PROFILE_ROLE`.
- Profile creation samples accept optional `SELECTAI_PROFILE_PROVIDER_ENDPOINT`.
  The SDK does not derive provider endpoints from provider name, region, or
  Azure resource name; set this variable when the database profile requires
  `provider_endpoint`.
- Profile creation samples accept optional `SELECTAI_PROFILE_OBJECT_LIST`.
  `SELECTAI_PROFILE_OBJECT_LIST_MODE` can be supplied without object-list JSON;
  when the object list is omitted, object selection follows database behavior
  for the supplied mode.
- Credential samples use `SELECTAI_CREDENTIAL_NAME` and either
  `SELECTAI_CREDENTIAL_USERNAME`/`SELECTAI_CREDENTIAL_PASSWORD` or OCI signing
  key variables such as `SELECTAI_USER_OCID`.
- `DropCredentialSample` accepts optional `SELECTAI_CREDENTIAL_DROP_FORCE`.
- Conversation samples use `SELECTAI_CONVERSATION_ID`; prompt deletion also uses
  `SELECTAI_CONVERSATION_PROMPT_ID`.
- Vector index samples use `SELECTAI_VECTOR_INDEX_NAME`.
- Vector index create/config samples use `SELECTAI_VECTOR_DB_PROVIDER`; samples
  default it to `oracle` when unset.
- `ListVectorIndexesSample` accepts optional
  `SELECTAI_VECTOR_INDEX_NAME_PATTERN`; it defaults to `.*`.
- `DropVectorIndexSample` accepts optional
  `SELECTAI_VECTOR_INDEX_DROP_INCLUDE_DATA`; it defaults to `true`.
- Synthetic data samples use `SELECTAI_SYNTHETIC_OBJECT_NAME`.
- The Select AI and combined integration runners recreate their synthetic-data
  validation tables before running synthetic-data samples. When running those
  samples individually, create the target table first.

### Agent samples

Database-backed agent samples require access to `DBMS_CLOUD_AI_AGENT` for the selected database
user. Their creation samples use these required variables in addition to the
common connection variables above:

`ToolParamsSample`, `ToolInputSample`, and `RunTeamParamsSample` are offline
exceptions: they need no database access or environment variables.

Agent sample entry points propagate execution and cleanup failures, producing
a nonzero process exit status. Callers should check that status rather than
searching generated responses for error keywords.

Agent discovery and existing-resource loading also require the documented
`USER_AI_AGENTS`, `USER_AI_AGENT_TASKS`, `USER_AI_AGENT_TOOLS`, and
`USER_AI_AGENT_TEAMS` views and their corresponding attribute views. The SDK
uses these views internally; applications should not query the internal view
owner directly. Availability depends on the certified database release and
the privileges granted to the configured user.

| Sample | Required variables | Existing resources or setup |
| --- | --- | --- |
| `CreateToolSample` | `SELECTAI_AGENT_TOOL_NAME` | Supply tool attributes appropriate for the selected tool type. A SQL tool needs `SELECTAI_AGENT_TOOL_TYPE=SQL` and `SELECTAI_AGENT_TOOL_PARAMS_JSON='{"profile_name":"MY_PROFILE"}'`. |
| `ToolParamsSample` | None | Builds and serializes typed SQL, RAG, web search, Slack, email, and generic parameters. Does not connect to the database. |
| `ToolInputSample` | None | Builds and reads typed input names and descriptions, then prints their JSON. Does not connect to the database. |
| `RunTeamParamsSample` | None | Preserves precise decimals in runtime-variable maps and reuses a request builder without changing earlier requests. Does not connect to the database. |
| `CreateToolWithInputsSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_AGENT_TOOL_FUNCTION`, `SELECTAI_AGENT_TOOL_INSTRUCTION`, `SELECTAI_AGENT_TOOL_INPUT_NAME` | Creates a custom tool using an existing accessible PL/SQL function and typed input metadata. Optionally set `SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION` and `SELECTAI_AGENT_TOOL_DESCRIPTION`. Does not execute the function. |
| `SetToolInputsSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_AGENT_TOOL_INPUT_NAME` | Updates an existing tool's input metadata. Optionally set `SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION`. Submits a one-entry array; does not run the tool or restore the previous metadata. |
| `CreateSqlToolSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_PROFILE_NAME` | Creates a SQL tool using `SqlToolParams`. The profile must already exist. |
| `CreateRagToolSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_PROFILE_NAME` | Creates a RAG tool using `RagToolParams`. The profile and its required vector index must be configured. |
| `CreateWebSearchToolSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_AGENT_TOOL_CREDENTIAL_NAME` | Creates a web-search tool using an existing compatible database credential. |
| `CreateSlackNotificationToolSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_AGENT_TOOL_CREDENTIAL_NAME`, `SELECTAI_AGENT_TOOL_CHANNEL` | Creates a Slack notification tool. The credential must authorize the selected channel. Does not send a message. |
| `CreateEmailNotificationToolSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_AGENT_TOOL_CREDENTIAL_NAME`, `SELECTAI_AGENT_TOOL_RECIPIENT`, `SELECTAI_AGENT_TOOL_SENDER`, `SELECTAI_AGENT_TOOL_SMTP_HOST` | Creates an email notification tool. Optionally set `SELECTAI_AGENT_TOOL_SUBJECT`. Does not send email. |
| `SetSqlToolParamsSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_PROFILE_NAME` | Updates an existing SQL tool's `profile_name` using `Tool.setAttributes(...)`. Does not run the tool or restore its previous parameters. |
| `CreateTaskSample` | `SELECTAI_AGENT_TASK_NAME`, `SELECTAI_AGENT_TASK_INSTRUCTION` | Any tools named by `SELECTAI_AGENT_TASK_TOOLS` must exist. |
| `CreateAgentSample` | `SELECTAI_AGENT_NAME`, `SELECTAI_PROFILE_NAME`, `SELECTAI_AGENT_ROLE` | The named AI profile and any tools named by `SELECTAI_AGENT_TOOLS` must exist. |
| `CreateTeamSample` | `SELECTAI_AGENT_TEAM_NAME`, `SELECTAI_AGENT_TEAM_AGENTS`, `SELECTAI_AGENT_TEAM_PROCESS` | The named agents and their tasks must exist. `SELECTAI_AGENT_TEAM_AGENTS` contains comma-separated `agentName:taskName` pairs. |
| `AgentWorkflowSample` | Common connection variables and `SELECTAI_PROFILE_NAME` | Creates a SQL tool, task, agent, team, and conversation; runs the team twice with the same conversation ID; and cleans up all resources it creates. |
| `SupervisorTeamSample` | Common connection variables and `SELECTAI_PROFILE_NAME` | Requires supervisor-led team support. Creates a SQL tool, two worker-task pairs, a supervisor, a team, and a conversation; runs a synthetic analysis request and cleans up its resources. |
| `PooledTeamResumeSample` | Common connection variables, `SELECTAI_AGENT_TEAM_NAME`; `start` also needs `SELECTAI_AGENT_TEAM_PROMPT`; `resume` needs `SELECTAI_AGENT_TEAM_CONVERSATION_ID` and `SELECTAI_AGENT_HUMAN_RESPONSE` | Existing human-input team. Uses Oracle UCP; verifies `WAITING_FOR_HUMAN` and resumes in a separate process with the same conversation ID. Retains the conversation for later cleanup. |
| `CreateSelectAIAgentWithOptionsSample` | Common connection variables; optionally `SELECTAI_QUERY_TIMEOUT_SECONDS` | Creates an SDK-owned `SelectAIAgent` client with `SelectAIOptions`. |
| `CreateSelectAIAgentDataSourceSample` | Common connection variables | Creates a `DataSource`-backed `SelectAIAgent` client. The application owns the `DataSource`. |
| `CreateSelectAIAgentDataSourceWithOptionsSample` | Common connection variables; optionally `SELECTAI_QUERY_TIMEOUT_SECONDS` | Creates a `DataSource`-backed `SelectAIAgent` client with `SelectAIOptions`. |
| `EnableAgentSample` / `DisableAgentSample` | `SELECTAI_AGENT_NAME` | The named agent must already exist and be accessible to the database user. |
| `SetAgentAttributeSample` | `SELECTAI_AGENT_NAME`, `SELECTAI_AGENT_ATTRIBUTE_NAME`, `SELECTAI_AGENT_ATTRIBUTE_VALUE` | The named agent must already exist. Set optional `SELECTAI_AGENT_ATTRIBUTE_TYPE` to `string`, `boolean`, `integer`, or `float` to select the corresponding overload. |
| `SetAgentAttributesSample` | `SELECTAI_AGENT_NAME`, `SELECTAI_PROFILE_NAME`, `SELECTAI_AGENT_ROLE` | The named agent must already exist. Optional agent attribute variables can be supplied for the update. |
| `EnableTaskSample` / `DisableTaskSample` | `SELECTAI_AGENT_TASK_NAME` | The named task must already exist and be accessible to the database user. |
| `SetTaskAttributeSample` | `SELECTAI_AGENT_TASK_NAME`, `SELECTAI_AGENT_ATTRIBUTE_NAME`, `SELECTAI_AGENT_ATTRIBUTE_VALUE` | The named task must already exist. Set optional `SELECTAI_AGENT_ATTRIBUTE_TYPE` to `string`, `boolean`, `integer`, or `float` to select the corresponding overload. |
| `SetTaskAttributesSample` | `SELECTAI_AGENT_TASK_NAME`, `SELECTAI_AGENT_TASK_INSTRUCTION` | The named task must already exist. |
| `EnableToolSample` / `DisableToolSample` | `SELECTAI_AGENT_TOOL_NAME` | The named tool must already exist and be accessible to the database user. |
| `SetToolAttributeSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_AGENT_ATTRIBUTE_NAME`, `SELECTAI_AGENT_ATTRIBUTE_VALUE` | The named tool must already exist. Set optional `SELECTAI_AGENT_ATTRIBUTE_TYPE` to `string`, `boolean`, `integer`, or `float` to select the corresponding overload. |
| `SetToolAttributesSample` | `SELECTAI_AGENT_TOOL_NAME`, `SELECTAI_AGENT_TOOL_TYPE` | The named tool must already exist. The database validates the supplied tool attributes. |
| `LoadToolSample` | `SELECTAI_AGENT_TOOL_NAME` | Loads a tool, reads typed parameter getters, and handles absent or unknown parameter models without printing arbitrary values. |
| `EnableTeamSample` / `DisableTeamSample` | `SELECTAI_AGENT_TEAM_NAME` | The named team must already exist and be accessible to the database user. |
| `SetTeamAttributeSample` | `SELECTAI_AGENT_TEAM_NAME`, `SELECTAI_AGENT_ATTRIBUTE_NAME`, `SELECTAI_AGENT_ATTRIBUTE_VALUE` | The named team must already exist. Set optional `SELECTAI_AGENT_ATTRIBUTE_TYPE` to `string`, `boolean`, `integer`, or `float` to select the corresponding overload. |
| `SetTeamAttributesSample` | `SELECTAI_AGENT_TEAM_NAME`, `SELECTAI_AGENT_TEAM_PROCESS` | The named team must already exist. |
| `GrantTeamAccessSample` / `RevokeTeamAccessSample` | `SELECTAI_AGENT_SHARED_TEAM_NAME`, `SELECTAI_AGENT_SHARED_TEAM_GRANTEE` | Run as the team owner to exercise the client-level team-sharing methods. |
| `GrantTeamHandleAccessSample` / `RevokeTeamHandleAccessSample` | `SELECTAI_AGENT_SHARED_TEAM_NAME`, `SELECTAI_AGENT_SHARED_TEAM_GRANTEE` | Run as the team owner to exercise team-handle sharing methods. |
| `DescribeTeamSample` | `SELECTAI_AGENT_TEAM_NAME` | Loads an existing team and prints the database-generated team definition and aggregated capabilities. |
| `ListAvailableTeamsSample` | None beyond the common connection variables | Calls `DBMS_CLOUD_AI_AGENT.LIST_TEAMS()` and lists read-only discovery snapshots, including shared teams available to the caller. |
| `GetTeamStateSample` | `SELECTAI_AGENT_TEAM_NAME`, `SELECTAI_AGENT_TEAM_CONVERSATION_ID` | Reads shared-team state through `SelectAIAgent` using the explicit conversation ID. |
| `GetOwnedTeamStateSample` | `SELECTAI_AGENT_TEAM_NAME`, `SELECTAI_AGENT_TEAM_CONVERSATION_ID` | Loads an owner-scoped `Team` handle and reads state using the explicit conversation ID. |
| `SetTeamExecutionStateSample` | `SELECTAI_AGENT_TEAM_NAME`, `SELECTAI_AGENT_TEAM_CONVERSATION_ID` | Updates execution state for an existing run. Optionally set `SELECTAI_AGENT_TEAM_EXECUTION_STATE`; the default is `SUCCEEDED`. |
| `SetOwnedTeamExecutionStateSample` | `SELECTAI_AGENT_TEAM_NAME`, `SELECTAI_AGENT_TEAM_CONVERSATION_ID` | Loads an owner-scoped `Team` handle and updates execution state. Optionally set `SELECTAI_AGENT_TEAM_EXECUTION_STATE`; the default is `SUCCEEDED`. |
| `GetVariableSample` | `SELECTAI_AGENT_VARIABLE_NAMESPACE`, `SELECTAI_AGENT_VARIABLE_NAME` | Reads a session-scoped runtime variable. Set `SELECTAI_AGENT_VARIABLE_SCOPE` to exercise the explicit-scope overload; omit it to use the default-scope overload. |
| `SetVariableSample` | `SELECTAI_AGENT_VARIABLE_NAMESPACE`, `SELECTAI_AGENT_VARIABLE_NAME`, `SELECTAI_AGENT_VARIABLE_VALUE` | Sets and reads a session-scoped variable. Set `SELECTAI_AGENT_VARIABLE_SCOPE` to exercise the explicit-scope overload, or omit it for the default-scope overload. Set `SELECTAI_AGENT_VARIABLE_CLEAR=true` to clear it afterward. |

#### Typed tool parameter samples

The five typed creation samples above set their tool type in Java and use typed
builders. They do not require `SELECTAI_AGENT_TOOL_TYPE` or
`SELECTAI_AGENT_TOOL_PARAMS_JSON`. All accept optional
`SELECTAI_AGENT_TOOL_INSTRUCTION` and `SELECTAI_AGENT_TOOL_DESCRIPTION`.
`SELECTAI_AGENT_TOOL_CREDENTIAL_NAME` identifies an existing database credential,
not its secret value. These samples do not create profiles, credentials, or ACLs.

Each creation sample creates a persistent tool without running it. Closing the
SDK client does not delete that tool. Use a fresh tool name, and run
`DropToolSample` with the same database connection variables and
`SELECTAI_AGENT_TOOL_NAME` for cleanup. Slack, email, and web-search setup
is environment-specific; these creation samples are not automatically added to
the integration runner.

After [building the SDK](#build-the-sdk) and [compiling samples](#compile-samples),
the offline example needs no environment variables:

```bash
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.ToolParamsSample
```

For a database-backed SQL example, set the common connection variables first,
then use an existing accessible profile:

```bash
export SELECTAI_AGENT_TOOL_NAME=JAVA_SQL_TOOL_SAMPLE
export SELECTAI_PROFILE_NAME=MY_EXISTING_PROFILE

java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.CreateSqlToolSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.LoadToolSample

# Optional: select another existing profile and update the tool parameters.
export SELECTAI_PROFILE_NAME=MY_OTHER_EXISTING_PROFILE
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.SetSqlToolParamsSample

# Cleanup uses the same tool name and database connection settings.
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.DropToolSample
```

On PowerShell, set variables with `$env:SELECTAI_AGENT_TOOL_NAME = 'JAVA_SQL_TOOL_SAMPLE'`
and `$env:SELECTAI_PROFILE_NAME = 'MY_EXISTING_PROFILE'`. Use semicolons in the
classpath, for example:

```powershell
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.CreateSqlToolSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.DropToolSample
```

`ToolParamsSample` also demonstrates `GenericToolParams.fromMap(...)`,
`ToolParams.fromMap(...)`, typed getters, and `additionalParameter(...)`. Its
future parameter names are illustrative and are not sent to a database.

#### Typed tool input samples

`ToolInputSample` demonstrates `ToolInput.builder().name(...).description(...)`
and `ToolAttributes.builder().toolInputs(List.of(input))`. It covers one or
multiple inputs, optional descriptions, serialization, and typed getters. Run
it after compiling the samples; it needs no database configuration:

```bash
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.ToolInputSample
```

For a database-backed example, use an existing accessible PL/SQL function that
accepts a `data_guard` input. Replace the function placeholder, set the common
database connection variables, and choose a new tool name. These commands
create a tool, update its input metadata, read it, and remove the tool:

```bash
export SELECTAI_AGENT_TOOL_NAME='INPUT_METADATA_SAMPLE_TOOL'
export SELECTAI_AGENT_TOOL_FUNCTION='<existing PL/SQL function accepting data_guard>'
export SELECTAI_AGENT_TOOL_INSTRUCTION='Call the configured function with the supplied data_guard value.'
export SELECTAI_AGENT_TOOL_INPUT_NAME='data_guard'
export SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION='Only supported values are "Enabled" and "Disabled".'
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.CreateToolWithInputsSample

export SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION='Provide either "Enabled" or "Disabled" for data_guard.'
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.SetToolInputsSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.LoadToolSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.DropToolSample
```

PowerShell equivalents:

```powershell
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.ToolInputSample
$env:SELECTAI_AGENT_TOOL_NAME = 'INPUT_METADATA_SAMPLE_TOOL'
$env:SELECTAI_AGENT_TOOL_FUNCTION = '<existing PL/SQL function accepting data_guard>'
$env:SELECTAI_AGENT_TOOL_INSTRUCTION = 'Call the configured function with the supplied data_guard value.'
$env:SELECTAI_AGENT_TOOL_INPUT_NAME = 'data_guard'
$env:SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION = 'Only supported values are "Enabled" and "Disabled".'
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.CreateToolWithInputsSample
$env:SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION = 'Provide either "Enabled" or "Disabled" for data_guard.'
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.SetToolInputsSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.LoadToolSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.DropToolSample
```

Creation and updates are persistent. Run `DropToolSample` even if a later sample
fails, but only for the tool you created. Use the same name and connection
settings for cleanup. The existing function is never executed or dropped by
these examples. `CreateToolWithInputsSample` deliberately ignores built-in
tool-type and raw tool-parameter variables.

`SetToolInputsSample` can also be run independently against an existing tool.
It submits a one-entry `tool_inputs` array. Include
all entries that must remain configured; do not assume array entries are
merged. The sample does not create resources or restore the previous metadata.
Descriptions guide the model and do not enforce input values in Java.

`CreateToolSample` also accepts the two input variables as optional creation
attributes, in addition to the tool's required function/type configuration.
Use an existing compatible function and a new tool name. It creates a
persistent tool; clean up with `DropToolSample` using the same tool name and
connection settings. `LoadToolSample` reads the typed input metadata.

#### Other agent samples

`GrantTeamAccessSample` and `RevokeTeamAccessSample` use the common database
connection variables plus `SELECTAI_AGENT_SHARED_TEAM_NAME` (the owner's
unqualified team name) and `SELECTAI_AGENT_SHARED_TEAM_GRANTEE` (the database
user or role). Run these samples with the team owner account. They demonstrate
the client-level `SelectAIAgent#grantTeamAccess(String, String)` and
`SelectAIAgent#revokeTeamAccess(String, String)` methods. The matching
`GrantTeamHandleAccessSample` and `RevokeTeamHandleAccessSample` demonstrate
`Team#grantAccess(String)` and `Team#revokeAccess(String)` through an
owner-scoped team handle. All four samples require the owner's authority to
grant or revoke team access; the grantee runs the shared team separately with
the qualified `OWNER.TEAM_NAME`.

When setting up a new example, create any referenced profile first, then tools,
tasks, agents, and finally the team. Load, run, describe, export, and definition
samples require the corresponding named resource to exist. List samples need
only the common connection variables and database access. For optional
attributes, import/export locations, and run inputs, review the environment
variable names in the sample source. See the [Agent API guide](../doc/agent-api.md)
for resource behavior.

`AgentWorkflowSample` is the standalone end-to-end example. It generates
resource names by default, but accepts `SELECTAI_AGENT_WORKFLOW_PREFIX` or
individual `SELECTAI_AGENT_WORKFLOW_*_NAME` variables when stable names are
required. It requires an existing `SELECTAI_PROFILE_NAME`; the tool, task,
agent, team, and conversation are created and cleaned up by the sample.
It reuses a `RunTeamRequest.Builder` for the two prompts. Cleanup attempts every
successfully created resource even if another drop fails. Cleanup failures are
attached as suppressed exceptions to an execution failure, or propagated on
their own when execution succeeded. `Workflow cleanup completed.` is printed
only when resource cleanup succeeds.
It demonstrates conversation continuation. For an actual human-input pause
resumed using a new connection pool, see [Pooled human-input resume](#pooled-human-input-resume).

The `DropAgentSample`, `DropTaskSample`, `DropToolSample`, and `DropTeamSample`
examples require the corresponding name variable from the table above and
delete that existing resource. Confirm the name before running a drop sample.
Each accepts an optional force setting, respectively
`SELECTAI_AGENT_DROP_FORCE`, `SELECTAI_AGENT_TASK_DROP_FORCE`,
`SELECTAI_AGENT_TOOL_DROP_FORCE`, and `SELECTAI_AGENT_TEAM_DROP_FORCE`;
all default to `false`. For a team-based workflow, drop the team first, then
its agent, task, and tool resources when no longer referenced. The samples do
not automatically remove resources created by other samples.

For `RunTeamSample`, first create a database conversation, for example with
`CreateConversationSample`, and set `SELECTAI_AGENT_TEAM_CONVERSATION_ID` to
the returned ID. An arbitrary UUID does not identify a database conversation.
Reuse that ID and change `SELECTAI_AGENT_TEAM_PROMPT` to the response when
resuming the team. When it is no longer needed, set
`SELECTAI_CONVERSATION_ID` to the same ID and run `DropConversationSample`.

`RunSharedTeamSample` runs a team granted by another owner without loading an
owner-only `Team` handle. Use the consumer database account in the common
connection variables, set `SELECTAI_AGENT_SHARED_TEAM_NAME` to
`OWNER.TEAM_NAME`, and set `SELECTAI_AGENT_TEAM_PROMPT` to a prompt for that
team. The owner can grant run access from Java with
`GrantTeamAccessSample` and revoke it with `RevokeTeamAccessSample`; these
samples call `SelectAIAgent#grantTeamAccess(String, String)` and
`SelectAIAgent#revokeTeamAccess(String, String)`. The owner must use an
unqualified team name for the grant or revoke, while the consumer runs the
qualified `OWNER.TEAM_NAME`. The consumer must also have any privileges
required by the workflow. Set
`SELECTAI_AGENT_TEAM_CONVERSATION_ID` to an existing database conversation ID;
reuse it to resume on another connection.

For state inspection, use the explicit conversation-ID methods
`Team#getStateByConversationId(String)` and
`SelectAIAgent#getTeamStateByConversationId(String, String)`.

`GetTeamStateSample` and `GetOwnedTeamStateSample` read a team state, while
`SetTeamExecutionStateSample` and `SetOwnedTeamExecutionStateSample` update a
team execution state using the database conversation ID. These
operations can use an owner-qualified team name for a shared team. The state
value is validated by the database and the update sample should be used only
with a run for which the caller is authorized.

The state samples require `SELECTAI_AGENT_TEAM_CONVERSATION_ID` and always use
the explicit conversation-ID methods. This avoids relying on an implicit
session conversation when connections are pooled.

`GetVariableSample` reads a session-scoped runtime variable and
`SetVariableSample` sets, reads, and optionally clears one. Runtime variables
are session-scoped; use a retained connection or explicit session affinity
when a later operation must observe a value written by an earlier operation.

`ListAgentsSample`, `ListTasksSample`, `ListToolsSample`, and
`ListAgentTeamsSample` list all owned resources by default. Set the corresponding
optional variable to filter names with a case-insensitive Oracle regular
expression: `SELECTAI_AGENT_NAME_PATTERN`,
`SELECTAI_AGENT_TASK_NAME_PATTERN`, `SELECTAI_AGENT_TOOL_NAME_PATTERN`, or
`SELECTAI_AGENT_TEAM_NAME_PATTERN`. For example, `^SUPPORT_.*` matches names
beginning with `SUPPORT_`. These lists do not include teams granted by another owner.

Choose the integration runner for the sample areas you need:

| Runner | Scope | Required environment |
| --- | --- | --- |
| `scripts/run-ai-samples-integration.sh` | Select AI samples, excluding AI Agent | Database variables, `SELECTAI_IT_VECTOR_LOCATION`, `SELECTAI_IT_COMPARTMENT_ID`, and four OCI API-key variables below |
| `scripts/run-ai-agent-samples-integration.sh` | AI Agent samples only | Database variables, four OCI API-key variables, `SELECTAI_PROFILE_REGION`, and `SELECTAI_PROFILE_OCI_COMPARTMENT_ID` |
| `scripts/run-all-samples-integration.sh` | Runs both suites and reports each result | Select AI runner variables above; agent region and compartment have defaults |

For the Select AI-only or combined runner, set the common database variables
shown above and these additional values:

```bash
export SELECTAI_IT_VECTOR_LOCATION='<readable Object Storage URI>'
export SELECTAI_IT_COMPARTMENT_ID='<OCI compartment OCID>'
export SELECTAI_USER_OCID='<OCI user OCID>'
export SELECTAI_TENANCY_OCID='<OCI tenancy OCID>'
export SELECTAI_PRIVATE_KEY='<OCI private key>'
export SELECTAI_FINGERPRINT='<OCI key fingerprint>'

scripts/run-ai-samples-integration.sh  # Select AI only
scripts/run-all-samples-integration.sh # Select AI and AI Agent
```

Each runner creates an OCI credential from the four key variables and drops it
after its samples finish. By default, each run generates a unique credential
name. Optionally, set `SELECTAI_PROFILE_CREDENTIAL_NAME` to name the credential
that each runner will create and drop. The name must not already exist; a failed
create does not authorize cleanup of that name. The agent runner also creates a
uniquely named profile, SQL tool, task, agent, team, and conversation and cleans
them up after a sample failure. It passes the database-created conversation ID
to `RunTeamSample`. Its profile uses `object_list_mode=all` with
`SH.CUSTOMERS` and `SH.COUNTRIES`. Install the `SH` sample schema and make those
tables visible to `SELECTAI_DB_USER` before running the agent or combined suite.

For an OCI agent profile, set the common database variables, OCI profile region
and compartment, and all four OCI API-key values. For example, in Bash:

```bash
export SELECTAI_DB_USER='<database-user>'
export SELECTAI_DB_PASSWORD='<database-password>'
export SELECTAI_JDBC_URL='<Oracle JDBC Thin URL>'
export SELECTAI_PROFILE_REGION='<OCI Generative AI region>'
export SELECTAI_PROFILE_OCI_COMPARTMENT_ID='<compartment OCID>'
export SELECTAI_USER_OCID='<OCI user OCID>'
export SELECTAI_TENANCY_OCID='<OCI tenancy OCID>'
export SELECTAI_PRIVATE_KEY='<OCI private key>'
export SELECTAI_FINGERPRINT='<OCI key fingerprint>'
export SELECTAI_PROFILE_CREDENTIAL_NAME='<new credential name>' # optional
scripts/run-ai-agent-samples-integration.sh
```

`SELECTAI_PROFILE_MODEL` is optional when the database/provider has a suitable
default. Set `SELECTAI_AGENT_IT_PROMPT` to change the team-run prompt, and
optionally set `SELECTAI_AGENT_TOOL_INPUT_JSON` to exercise the SQL tool's run
sample. Use `--dry-run` to check required variables and the planned
resource order without building or connecting. Maven, Java, and Javac can be
selected with `MVN`, `JAVA`, and `JAVAC`.

The agent runner executes the offline `ToolParamsSample`, `ToolInputSample`,
and `RunTeamParamsSample`,
creates its SQL tool through `CreateSqlToolSample`, and updates its profile
through `SetSqlToolParamsSample`. It clears inherited custom-tool input
configuration before configuring the SQL tool.

To include the database-backed typed-input samples, additionally set:

```bash
export SELECTAI_AGENT_RUN_TOOL_INPUT_SAMPLES=true
export SELECTAI_AGENT_TOOL_FUNCTION='<existing accessible PL/SQL function>'
export SELECTAI_AGENT_TOOL_INSTRUCTION='Call the configured function with the supplied data_guard value.'
export SELECTAI_AGENT_TOOL_INPUT_NAME='data_guard' # must match the function
export SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION='Only supported values are "Enabled" and "Disabled".' # optional
scripts/run-ai-agent-samples-integration.sh --dry-run
scripts/run-ai-agent-samples-integration.sh
```

This opt-in path runs `CreateToolWithInputsSample`, `SetToolInputsSample`, and
`LoadToolSample` against a separate generated tool name, then drops that tool
during cleanup, including after an update or later sample failure. It neither
creates nor executes nor drops the supplied function. The caller's tool name
is not used. Unset `SELECTAI_AGENT_RUN_TOOL_INPUT_SAMPLES` to return to the
default run. The combined runner passes the same opt-in settings to its agent
suite.

Set `SELECTAI_AGENT_RUN_WORKFLOW_SAMPLE=true` to also run `AgentWorkflowSample`
against the run-owned profile. It creates a separate tool, task, agent, team,
and conversation, runs two prompts with the same conversation ID, and cleans up
its resources. The runner supplies isolated resource names and treats reported
workflow cleanup failures as failures using process exit status and the cleanup
completion marker. The extra team calls can incur provider
charges. Optionally set `SELECTAI_AGENT_WORKFLOW_FIRST_PROMPT` and
`SELECTAI_AGENT_WORKFLOW_CONTINUATION_PROMPT` to customize the prompts.

Set `SELECTAI_AGENT_EXTRA_TOOL_SAMPLES` to a space-separated selection such as
`'rag websearch'` to include additional built-in tool samples. Each selected tool is
created with a generated name, loaded, described, and dropped during cleanup;
these tools are not executed. Supply the prerequisites for each selection:

| Selection | Required environment |
| --- | --- |
| `rag` | `SELECTAI_AGENT_RAG_PROFILE_NAME`: existing RAG profile with its vector index |
| `websearch` | `SELECTAI_AGENT_WEBSEARCH_CREDENTIAL_NAME` |
| `slack` | `SELECTAI_AGENT_SLACK_CREDENTIAL_NAME`, `SELECTAI_AGENT_TOOL_CHANNEL` |
| `email` | `SELECTAI_AGENT_EMAIL_CREDENTIAL_NAME`, `SELECTAI_AGENT_TOOL_RECIPIENT`, `SELECTAI_AGENT_TOOL_SENDER`, `SELECTAI_AGENT_TOOL_SMTP_HOST`; optionally `SELECTAI_AGENT_TOOL_SUBJECT` |

These service credentials must already exist and be suitable for their service.
Unlike the run-owned OCI profile credential, they are not created or dropped by
the runner. The existing RAG profile and vector index are also left unchanged.
Use `--dry-run` to check configuration without building or connecting. The
combined runner and the legacy agent-script alias support the same options.

The combined runner invokes the two specialized runners separately. It maps
`SELECTAI_IT_COMPARTMENT_ID` to the agent compartment, defaults the agent region
to `us-chicago-1`. Each suite creates and drops a credential independently.
`SELECTAI_PROFILE_MODEL` and
`SELECTAI_PROFILE_OCI_API_FORMAT` can be set for the agent profile when required
by the selected OCI model. The old `run-agent-samples-integration.sh` command
remains as an alias for `run-ai-agent-samples-integration.sh`.

This runner requires permission to create and drop these objects and access to
the selected AI provider. It does not create users, grant privileges, or change
network ACLs. It leaves a per-run log directory under
`target/agent-sample-reports/`. If cleanup reports a failure, inspect that
directory and remove the generated names manually. It does not run
`RunSharedTeamSample` or `ImportTeamSample`: those require external grants or
can create objects beyond this runner's cleanup set.

Some samples change database state. Review the class comments before running
samples that create, update, enable, disable, drop, grant, revoke, submit
feedback, or generate data.

### Supervisor-led team

[`SupervisorTeamSample`](src/main/java/com/oracle/database/selectai/samples/agent/SupervisorTeamSample.java)
demonstrates the two related supervisor settings in one runnable workflow:

| Setting | Responsibility |
| --- | --- |
| `AgentAttributes.Builder.supervisor(true)` | Designates the supervisor when creating the agent. This flag cannot be changed afterward. |
| `TeamAttributes.Builder.supervisorAgent(name)` | Assigns that supervisor agent to the team. |
| `TeamAttributes.Builder.agents(...)` | Lists only worker agent-task pairs, excluding the supervisor. |
| `TeamAttributes.Builder.process("sequential")` | Runs delegated worker subtasks one at a time. |

The sample creates a SQL tool, revenue and margin tasks, two worker agents,
a supervisor agent, a team, and a database conversation. It uses synthetic
figures supplied in the prompt rather than a sales schema. The database must
support supervisor-led teams, and `SELECTAI_PROFILE_NAME` must name an existing
accessible profile suitable for the SQL tool. Model calls may incur provider
charges; responses vary by model.

Set the common `SELECTAI_DB_USER`, `SELECTAI_DB_PASSWORD`, and
`SELECTAI_JDBC_URL` variables. After [building the SDK](#build-the-sdk),
[copying dependencies](#copy-runtime-dependencies), and
[compiling the samples](#compile-samples), run in Bash:

```bash
export SELECTAI_PROFILE_NAME="<existing-profile-name>"
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.SupervisorTeamSample
```

PowerShell:

```powershell
$env:SELECTAI_PROFILE_NAME = "<existing-profile-name>"
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" `
  com.oracle.database.selectai.samples.agent.SupervisorTeamSample
```

Resource names use a unique prefix by default. Optionally set
`SELECTAI_AGENT_SUPERVISOR_PREFIX` to an unused prefix or
`SELECTAI_AGENT_SUPERVISOR_PROMPT` to change the request. Cleanup attempts every
successfully created resource, including after a failure, without deleting
existing profiles or credentials. Execution and cleanup failures produce a
nonzero process exit status. If cleanup fails, inspect the reported resource
names before retrying. The integration scripts compile this sample but do not
run it automatically; execute it explicitly on a database with supervisor support.

## Using the Published SDK

Applications can consume the released SDK from Maven Central instead of
building the SDK source repository:

```xml
<dependency>
    <groupId>com.oracle.database.selectai</groupId>
    <artifactId>select-ai</artifactId>
    <version>1.1.0</version>
</dependency>
```

The build commands later in this document use the locally built project JAR
because they are intended for running samples from the source repository. The
SDK does not include an SLF4J logging provider. The `samples` Maven profile adds
`slf4j-simple` for standalone sample execution; application projects should
configure their own SLF4J 2.x provider.

### External dependencies by sample area

Some samples require database objects, cloud credentials, provider access, or
external content in addition to the common database connection variables.

| Sample area | External dependency | How to configure |
| --- | --- | --- |
| Profile creation and generation | Tables visible to `SELECTAI_DB_USER` that match the configured object list and prompts. If using Oracle sample schema objects such as `SH.CUSTOMERS` and `SH.COUNTRIES`, ensure the `SH` schema is installed and visible. `SH` is not guaranteed to exist in every Oracle Autonomous AI Database or Oracle Database instance. | Set `SELECTAI_PROFILE_OBJECT_LIST` and `SELECTAI_PROFILE_PROMPT` for your schema, or omit `SELECTAI_PROFILE_OBJECT_LIST` and rely on the configured object-list mode. |
| OCI profile and generation | OCI Generative AI profile attributes and a database credential that can authenticate to OCI. | Set `SELECTAI_PROFILE_CREDENTIAL_NAME`, `SELECTAI_PROFILE_OCI_COMPARTMENT_ID`, and other provider attributes required by the database profile. |
| Credential creation | Cloud credential material. | Set either `SELECTAI_CREDENTIAL_USERNAME`/`SELECTAI_CREDENTIAL_PASSWORD` or OCI signing-key variables such as `SELECTAI_USER_OCID`, `SELECTAI_TENANCY_OCID`, `SELECTAI_PRIVATE_KEY`, and `SELECTAI_FINGERPRINT`. Do not commit or log secret values. |
| Database administration | Administrative database privileges. | Use a database user authorized to grant/revoke package privileges, configure data access, and manage network ACLs. |
| Vector index creation | Readable document location for embedding. | Set `SELECTAI_VECTOR_INDEX_LOCATION` when running vector-index samples directly, or `SELECTAI_IT_VECTOR_LOCATION` when running the Select AI or combined integration runner. Ensure the referenced Object Storage URI is readable by the configured credential/profile. |
| URI-based summarization | Readable external document URI and credential. | Set `SELECTAI_SUMMARIZE_LOCATION_URI` and `SELECTAI_SUMMARIZE_CREDENTIAL_NAME` when using URI-based summarization. Inline-content summarization does not require an external URI. |
| Synthetic data generation | Target table or object list for generated rows. | The Select AI runner creates its synthetic-data validation table. When running synthetic-data samples individually, create the target table first or point the sample variables to an existing table. |
| Conversation prompt deletion | Existing conversation prompt ID. | Set `SELECTAI_CONVERSATION_PROMPT_ID`. The Select AI runner skips prompt deletion when no prompt ID is available. |

### Pooled human-input resume

[`PooledTeamResumeSample`](src/main/java/com/oracle/database/selectai/samples/agent/PooledTeamResumeSample.java)
has two commands, `start` and `resume`, each run in its own Java process with a
new Oracle UCP pool. The `samples` Maven profile includes
`com.oracle.database.jdbc:ucp:23.26.1.0.0`; UCP is not an SDK runtime dependency.
Use the normal [build](#build-the-sdk), [dependency-copy](#copy-runtime-dependencies),
and [sample compilation](#compile-samples) steps below.

First prepare a team whose task has `enable_human_tool=true` and instructions
requiring a missing input or approval before completing. `CreateTaskSample`
accepts `SELECTAI_AGENT_TASK_ENABLE_HUMAN_TOOL=true`. Use a first prompt that
omits that required input. A prompt alone does not guarantee a pause: `start`
fails unless the database reports `WAITING_FOR_HUMAN`. This sample uses the
existing team without changing its profile, agents, tasks, or tools. An
owner-qualified granted team can also be used.

With the common connection variables already set, run in Bash:

```bash
export SELECTAI_AGENT_TEAM_NAME='HUMAN_INPUT_TEAM'
export SELECTAI_AGENT_TEAM_PROMPT='Start the review and ask me for the required approval.'
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.PooledTeamResumeSample start

# After start reports WAITING_FOR_HUMAN, use the printed Conversation ID.
export SELECTAI_AGENT_TEAM_CONVERSATION_ID='<conversation-id-from-start>'
export SELECTAI_AGENT_HUMAN_RESPONSE='Approved. Continue the review.'
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.agent.PooledTeamResumeSample resume
```

PowerShell:

```powershell
$env:SELECTAI_AGENT_TEAM_NAME = "HUMAN_INPUT_TEAM"
$env:SELECTAI_AGENT_TEAM_PROMPT = "Start the review and ask me for the required approval."
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" `
  com.oracle.database.selectai.samples.agent.PooledTeamResumeSample start

$env:SELECTAI_AGENT_TEAM_CONVERSATION_ID = "<conversation-id-from-start>"
$env:SELECTAI_AGENT_HUMAN_RESPONSE = "Approved. Continue the review."
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" `
  com.oracle.database.selectai.samples.agent.PooledTeamResumeSample resume
```

Use the same database identity, team name, and conversation ID for resume.
`resume` checks the waiting state before submitting the human response and
reports the state afterward. If another human input is requested, run `resume`
again with the new answer and the same ID. Do not send concurrent responses
for one conversation. In production, persist the team name and conversation ID
in authorized application state, not merely in a session-local JDBC setting.

Both commands close their pools. The conversation is intentionally retained,
including on failure, to preserve recovery context. No failed run is
automatically retried. Optional `SELECTAI_QUERY_TIMEOUT_SECONDS` limits JDBC
statement waiting, but does not guarantee cancellation of database or provider
work. Once the workflow is finished and no longer needed, clean up in Bash:

```bash
export SELECTAI_CONVERSATION_ID="$SELECTAI_AGENT_TEAM_CONVERSATION_ID"
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" \
  com.oracle.database.selectai.samples.conversation.DropConversationSample
```

PowerShell:

```powershell
$env:SELECTAI_CONVERSATION_ID = $env:SELECTAI_AGENT_TEAM_CONVERSATION_ID
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" `
  com.oracle.database.selectai.samples.conversation.DropConversationSample
```

The integration runner compiles this sample but does not execute it: the
human-input team and the response require deliberate setup. See Oracle's
[human-input resume example](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/examples-using-select-ai-agent.html)
for the database workflow.

## Integration Script Prerequisites

The Select AI runner `scripts/run-ai-samples-integration.sh` uses default
sample inputs for the javaselectai26ai validation database. The combined runner
`scripts/run-all-samples-integration.sh` invokes it before the agent runner.
Before running either of these script flows, ensure these database objects are
available:

The integration runners are Bash scripts. PowerShell users should run them from
Git Bash, WSL, or another Bash-compatible environment; they cannot be executed
directly as a native PowerShell script.

- `AI_TEST1` database user
- `AI_TEST2` database user
- `SH.CUSTOMERS` and `SH.COUNTRIES`, visible to `SELECTAI_DB_USER`; both runners
  use these tables in their default profile object lists

Example setup SQL. Replace masked values before running in your database:

```sql
CREATE USER AI_TEST1 IDENTIFIED BY "<password>";
CREATE USER AI_TEST2 IDENTIFIED BY "<password>";

GRANT CREATE SESSION TO AI_TEST1;
GRANT CREATE SESSION TO AI_TEST2;

ALTER USER AI_TEST1 QUOTA UNLIMITED ON USERS;
ALTER USER AI_TEST2 QUOTA UNLIMITED ON USERS;

```

The database user supplied through `SELECTAI_DB_USER` must have the privileges
required to create/drop Select AI resources, grant/revoke package privileges,
and manage network ACLs.

The default profile and generation samples in the Select AI runner use this
object list and prompt:

```bash
export SELECTAI_PROFILE_OBJECT_LIST='[{"owner":"SH","name":"customers"},{"owner":"SH","name":"countries"}]'
export SELECTAI_PROFILE_PROMPT="how many customers"
```

`SH` is an Oracle sample schema and is not guaranteed to exist in every Oracle
Autonomous AI Database or Oracle Database instance. If your database does not
have those tables, edit the object list in each runner you plan to use and
the Select AI runner's prompt to match tables visible to `SELECTAI_DB_USER`.
Exporting `SELECTAI_PROFILE_OBJECT_LIST` before running a script will not
override its integration defaults.

## Transaction Behavior

The SDK does not explicitly perform commit or rollback operations and does not
manage transaction boundaries.

Samples that create a `SelectAI` client from database connection configuration
use a single SDK-owned JDBC connection for that client. Samples that create a
`SelectAI` client from a `DataSource` borrow a JDBC connection for each public
SDK operation and close it when the operation completes. If the `DataSource` is
backed by a connection pool, closing the connection normally returns it to the
pool.

The SDK does not explicitly modify the connection's auto-commit setting. Any
implicit commit or rollback performed by the underlying Oracle Database APIs is
outside the SDK's transaction management. If an SDK operation fails, the SDK
propagates the underlying JDBC or Oracle error through `SelectAIException`
without explicitly issuing commit or rollback as part of failure handling.

## Build the SDK

From the project root, build the SDK JAR. This Maven command is the same in Bash
and PowerShell:

```bash
mvn clean install
```

## Copy Runtime Dependencies

The SDK jar does not bundle runtime dependencies. Copy them into
`target/dependency` after building the SDK. This Maven command is the same in
Bash and PowerShell:

```bash
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies
```

Run this command after `mvn clean install`, because Maven `clean` removes the
entire `target/` directory, including `target/dependency`.

The `samples` Maven profile adds `slf4j-simple` as the runtime logging provider
for standalone samples. The SDK itself depends only on `slf4j-api`; applications
using the SDK should choose their own SLF4J logging backend.
The profile also includes Oracle UCP for `PooledTeamResumeSample`. UCP is not
required by the SDK itself; applications own and choose their connection pool.

## Compile Samples

Compile all active samples:

```bash
javac --release 17 -cp "target/select-ai-1.1.0.jar:target/dependency/*" -d samples/out $(find samples/src/main/java -name "*.java")
```

PowerShell:

```powershell
$sampleSources = Get-ChildItem samples/src/main/java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
javac --release 17 -cp "target/select-ai-1.1.0.jar;target/dependency/*" `
    -d samples/out $sampleSources
```

## Run Samples

Run a sample by using its fully qualified class name:

```bash
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.ListProfilesSample
```

PowerShell:

```powershell
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" `
    com.oracle.database.selectai.samples.profile.ListProfilesSample
```

More examples:

```bash
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.credential.CreateCredentialSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationsSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationPromptsSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.vectorindex.ListVectorIndexesSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.SummarizeProfileSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.ChatSessionProfileSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.datasource.ListProfilesWithDataSourceSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.agent.ListAgentsSample
java -cp "samples/out:target/select-ai-1.1.0.jar:target/dependency/*" com.oracle.database.selectai.samples.agent.LoadAgentSample
```

PowerShell:

```powershell
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.credential.CreateCredentialSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationsSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationPromptsSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.vectorindex.ListVectorIndexesSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.profile.SummarizeProfileSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.profile.ChatSessionProfileSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.datasource.ListProfilesWithDataSourceSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.ListAgentsSample
java -cp "samples/out;target/select-ai-1.1.0.jar;target/dependency/*" com.oracle.database.selectai.samples.agent.LoadAgentSample
```

## Runtime Classpath

The runtime classpath must include the compiled sample classes, the SDK jar, and
runtime dependencies copied by Maven:

```bash
samples/out:target/select-ai-1.1.0.jar:target/dependency/*
```

On PowerShell and Windows, use semicolons as classpath separators:

```text
samples/out;target/select-ai-1.1.0.jar;target/dependency/*
```

Key runtime libraries include:

- `com.oracle.database.jdbc:ojdbc11`
- `com.oracle.database.jdbc:ucp` - sample-only pool used by `PooledTeamResumeSample`
- `com.oracle.database.security:oraclepki`
- `org.slf4j:slf4j-api` - logging API used by SDK and samples
- `org.slf4j:slf4j-simple` - sample-only runtime logging provider added by the `samples` Maven profile
- `com.fasterxml.jackson.core:jackson-databind`

## Finding a Sample

Sample class names follow the SDK method they demonstrate. Examples:

- `ListProfilesSample` demonstrates `SelectAI#listProfiles()`.
- `ListProfilesByPatternSample` demonstrates `SelectAI#listProfiles(String)`.
- `CreateProfileSample` demonstrates initializing a profile object with `SelectAI#profile(...)` and persisting it with `Profile#create()`.
- `SetProfileStringAttributeSample` demonstrates `Profile#setAttribute(String, String)`.
- `ListConversationsSample` demonstrates `SelectAI#listConversations()`.
- `ListConversationPromptsSample` demonstrates `Conversation#listPrompts()`.
- `DeleteConversationPromptSample` demonstrates `Conversation#deletePrompt(String, boolean)`.
- `ListVectorIndexesSample` demonstrates `SelectAI#listVectorIndexes(String)`.
- `CreateConversationSample` demonstrates `Conversation#create()`.
- `GetVectorIndexAttributesSample` demonstrates `VectorIndex#getVectorIndexAttributes()`.
- `UpdateVectorIndexAttributeWithClobSample` demonstrates `VectorIndex#update(String, String, boolean)`.
- `SummarizeProfileSample` demonstrates `Profile#summarize(...)` with typed `SummaryParams`.
- `SubmitFeedbackProfileSample` demonstrates `Profile#feedback(Feedback)`.
- `EnableSelectAIDataAccessSample` demonstrates `DatabaseAdmin#enableDataAccess()`.
- `DisableSelectAIDataAccessSample` demonstrates `DatabaseAdmin#disableDataAccess()`.
- `GrantPrivilegesSample` demonstrates `DatabaseAdmin#grantPrivileges(List<String>)`.
- `RevokePrivilegesSample` demonstrates `DatabaseAdmin#revokePrivileges(List<String>)`.
- `GrantHttpAccessSample` demonstrates `DatabaseAdmin#grantHttpAccess(List<String>, String)`.
- `RevokeHttpAccessSample` demonstrates `DatabaseAdmin#revokeHttpAccess(List<String>, String)`.
- `GrantNetworkAccessSample` demonstrates `DatabaseAdmin#grantNetworkAccess(List<String>, String, List<String>, Integer, Integer)`.
- `RevokeNetworkAccessSample` demonstrates `DatabaseAdmin#revokeNetworkAccess(List<String>, String, List<String>, Integer, Integer)`.
- `GrantTeamAccessSample` demonstrates `SelectAIAgent#grantTeamAccess(String, String)`.
- `RevokeTeamAccessSample` demonstrates `SelectAIAgent#revokeTeamAccess(String, String)`.
- `GrantTeamHandleAccessSample` demonstrates `Team#grantAccess(String)`.
- `RevokeTeamHandleAccessSample` demonstrates `Team#revokeAccess(String)`.
- `ListProfilesWithDataSourceSample` demonstrates creating `SelectAI` with a `DataSource`.
- `CreateSelectAIAgentWithOptionsSample`, `CreateSelectAIAgentDataSourceSample`, and `CreateSelectAIAgentDataSourceWithOptionsSample` demonstrate all `SelectAIAgent.create(...)` connection factories.
- `CreateAgentSample` demonstrates `Agent#create()`.
- `EnableAgentSample` and `DisableAgentSample` demonstrate `Agent#enable()` and `Agent#disable()`.
- `SetAgentAttributeSample` demonstrates all typed `Agent#setAttribute(...)` overloads using `SELECTAI_AGENT_ATTRIBUTE_TYPE`.
- `SetAgentAttributesSample` demonstrates `Agent#setAttributes(AgentAttributes)`.
- `SetTaskAttributeSample` and `SetTaskAttributesSample` demonstrate task attribute updates; the single-attribute sample covers all typed setter overloads.
- `SetToolAttributeSample` and `SetToolAttributesSample` demonstrate tool attribute updates; the single-attribute sample covers all typed setter overloads.
- `SetTeamAttributeSample` and `SetTeamAttributesSample` demonstrate team attribute updates; the single-attribute sample covers all typed setter overloads.
- `AgentWorkflowSample` demonstrates the complete Tool -> Task -> Agent -> Team -> Run -> Continue conversation workflow.
- `SupervisorTeamSample` connects the supervisor-agent flag with team assignment, runs two worker-task pairs, and cleans up its created resources.
- `PooledTeamResumeSample` verifies `WAITING_FOR_HUMAN` and resumes through a new Oracle UCP pool in a separate process, preserving the conversation ID.
- `CreateTaskSample` demonstrates `Task#create()`.
- `EnableTaskSample` and `DisableTaskSample` demonstrate `Task#enable()` and `Task#disable()`.
- `CreateToolSample` demonstrates `Tool#create()`.
- `CreateSqlToolSample`, `CreateRagToolSample`, `CreateWebSearchToolSample`, `CreateSlackNotificationToolSample`, and `CreateEmailNotificationToolSample` demonstrate database-backed tool creation using each built-in `ToolParams` model.
- `SetSqlToolParamsSample` demonstrates updating a SQL tool's profile with `Tool#setAttributes(ToolAttributes)` and `SqlToolParams`.
- `ToolInputSample` demonstrates immutable input names and descriptions without a database. `CreateToolWithInputsSample` creates a custom tool with typed input metadata; `SetToolInputsSample` updates it, `LoadToolSample` reads it, and `DropToolSample` removes it. `CreateToolSample` also accepts optional input metadata.
- `ToolParamsSample` demonstrates immutable `ToolParams` builders, database JSON serialization, and typed parameter loading. `CreateToolSample` converts its JSON environment variable into these models; `AgentWorkflowSample` uses `SqlToolParams` directly.
- `RunTeamParamsSample` demonstrates exact decimal snapshots and reusable team-run request builders without connecting to a database.
- `EnableToolSample` and `DisableToolSample` demonstrate `Tool#enable()` and `Tool#disable()`.
- `CreateTeamSample` demonstrates `Team#create()`.
- `EnableTeamSample` and `DisableTeamSample` demonstrate `Team#enable()` and `Team#disable()`.
- `DropAgentSample`, `DropTaskSample`, `DropToolSample`, and `DropTeamSample` demonstrate the corresponding `drop(boolean)` methods.
- `LoadAgentSample`, `LoadTaskSample`, `LoadToolSample`, and `LoadTeamSample` load existing resources and read their status, description, and attributes.
- `ListAgentsSample`, `ListTasksSample`, and `ListToolsSample` demonstrate the typed `SelectAIAgent` list methods, optionally filtered by name pattern.
- `ListAgentTeamsSample` demonstrates owner-scoped `SelectAIAgent#listTeams()` and its pattern overload.
- `ListAvailableTeamsSample` demonstrates `SelectAIAgent#listAvailableTeams()` for shared and cross-schema team discovery.
- `GetTeamStateSample` demonstrates `SelectAIAgent#getTeamStateByConversationId(...)`.
- `GetOwnedTeamStateSample` demonstrates `Team#getStateByConversationId(...)`.
- `SetTeamExecutionStateSample` demonstrates `SelectAIAgent#setTeamExecutionState(...)`.
- `SetOwnedTeamExecutionStateSample` demonstrates `Team#setExecutionState(...)`.
- `GetVariableSample` and `SetVariableSample` demonstrate session runtime-variable access.
- `RunTeamSample` demonstrates `Team#run(RunTeamRequest)`.
- `RunSharedTeamSample` demonstrates `SelectAIAgent#runTeam(String, RunTeamRequest)` for a granted team.
- `DescribeToolSample` demonstrates `Tool#describe()`.
- `DescribeTeamSample` demonstrates `Team#describe()`.
- `RunToolSample` demonstrates `Tool#run(String)` and reads the returned `ToolRunResult` with `getRawValue()`.
- `GetDefinitionSample` demonstrates `SelectAIAgent#getDefinition(...)`.
- `ImportTeamSample` demonstrates `SelectAIAgent#importTeam(...)`.
- `ExportTeamSample` demonstrates the typed `Team#exportDefinition()` and
  `Team#exportDefinition(ExportTeamParams)` overloads, as well as the
  destination-based `ExportTeamRequest` overload.

For exact environment variables and behavior, review the sample source and its
class-level comments where provided.
