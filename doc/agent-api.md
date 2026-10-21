# Select AI Agent API

The Select AI Agent API exposes Java wrappers for Oracle Database
`DBMS_CLOUD_AI_AGENT` operations.

## Package Layout

| Package | Purpose |
| --- | --- |
| `com.oracle.database.selectai.agent` | Public Agent API interfaces and entry point. Includes `SelectAIAgent`, `Agent`, `Task`, `Tool`, and `Team`. |
| `com.oracle.database.selectai.model.agent` | Public Agent API model classes for attributes, enums, team-run, import/export, and execution-state requests. |
| `com.oracle.database.selectai.impl.agent` | Internal implementation package. Application code should use `SelectAIAgent.create(...)` and public interfaces instead of importing implementation classes. |

## Database Compatibility

Select AI for Java `1.1.0` was validated for the agent API on Oracle
Autonomous AI Database 26ai and Oracle Autonomous AI Database 19c. These are
the supported database/service environments for this release. Other Oracle AI
Database or Oracle Database releases are not certified for Java `1.1.0`, even
when the `DBMS_CLOUD_AI_AGENT` package is present.

The [Oracle Select AI capability matrix](https://docs.oracle.com/en/database/oracle/oracle-database/26/saicm/index.html)
defines support for individual agent subprograms, Select AI profile
attributes, providers, and built-in tools by database release. The Java SDK
does not override those database capabilities.

`SelectAIAgent.create(DbConnectionConfig)` opens the configured JDBC
connection, while `SelectAIAgent.create(DataSource)` defers connection access
until an operation runs. Neither factory probes the complete
`DBMS_CLOUD_AI_AGENT` capability set. Missing package or view objects,
privileges, or an unsupported operation are therefore reported when the
affected operation is invoked, as `SelectAIException` wrapping the JDBC or
Oracle Database error.

Agent discovery and existing-resource loading also require the documented
`USER_AI_AGENTS`, `USER_AI_AGENT_TASKS`, `USER_AI_AGENT_TOOLS`, and
`USER_AI_AGENT_TEAMS` views and their corresponding attribute views. The SDK
uses these views internally; applications should use the public Java
interfaces rather than query them directly. The internal package owner is an
implementation detail and is not a customer prerequisite.

## Connection Modes

`SelectAIAgent` supports the same connection modes as `SelectAI`:

```java
SelectAIAgent agentClient = SelectAIAgent.create(dbConnectionConfig);
SelectAIAgent pooledAgentClient = SelectAIAgent.create(dataSource);
```

`DbConnectionConfig` mode creates one SDK-owned JDBC connection and releases it
when `SelectAIAgent.close()` is called. `DataSource` mode borrows a JDBC
connection for each operation and closes it after the operation completes.

Use try-with-resources for deterministic cleanup:

```java
try (SelectAIAgent agentClient = SelectAIAgent.create(dbConnectionConfig)) {
    List<Team> teams = agentClient.listTeams();
}
```

## Resource Lifecycle

Agent framework resources follow the same configured-versus-created pattern as
other SDK resources.

```java
Agent agent = agentClient.agent(
        "SUPPORT_AGENT",
        AgentAttributes.builder()
                .profileName("GENAI_PROFILE")
                .role("Answer support questions")
                .build(),
        "Support agent",
        AgentStatus.ENABLED);

agent.create();
```

Objects created with only a name represent existing database resources:

```java
Team team = agentClient.team("SUPPORT_TEAM");
TeamRunResult result = team.run(RunTeamRequest.builder()
        .userPrompt("Summarize open support issues.")
        .build());
System.out.println(result.getRawValue());
```

The name-only `team(...)` factory and `listTeams()` load team metadata from
the current user's team view. To run a team granted by another owner, use the
client-level method instead of trying to load a `Team` handle:

```java
TeamRunResult result = agentClient.runTeam("TEAM_OWNER.SHARED_SUPPORT_TEAM",
        RunTeamRequest.builder()
                .userPrompt("Summarize open support issues.")
                .build());
System.out.println(result.getRawValue());
```

The owner must grant the consumer run access with
`DBMS_CLOUD_AI_AGENT.GRANT_TEAM_ACCESS` using the owner's unqualified team
name. The database verifies the consumer's access when `runTeam(...)` executes.
The grant permits running the team as a unit, not managing it or independently
running its agents, tasks, or tools. The consumer may also need separate
privileges for resources used by the workflow. See the
[Oracle team-sharing prerequisites](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/prerequisites-sharing-select-ai-agent-teams.html).

For discovery, `listAvailableTeams()` delegates to
`DBMS_CLOUD_AI_AGENT.LIST_TEAMS()` and returns read-only `AvailableTeam`
snapshots. This view can include shared teams available to the caller and is
separate from the owner-scoped lifecycle handles returned by `listTeams()`.

### End-to-end agent workflow

[`AgentWorkflowSample`](../samples/src/main/java/com/oracle/database/selectai/samples/agent/AgentWorkflowSample.java)
is a standalone sample that demonstrates the complete resource flow:

1. Create a SQL `Tool`.
2. Create a `Task` that uses the tool.
3. Create an `Agent` that uses the tool and the configured Select AI profile.
4. Create a `Team` containing the agent and task.
5. Create a database conversation and run the team with its conversation ID.
6. Continue the conversation by sending another `RunTeamRequest` with the same ID.

The sample removes the team, agent, task, tool, and conversation that it creates.
It attempts every registered cleanup even if another cleanup fails. Execution
and cleanup failures produce a nonzero process exit status. When both fail,
the execution failure remains primary and cleanup failures are retained as
suppressed exceptions.
The profile supplied through `SELECTAI_PROFILE_NAME` must already exist. See the
class-level JavaDoc on `AgentWorkflowSample` for the required environment
variables and compile and run commands.

### Resuming a waiting workflow through a pool

[`PooledTeamResumeSample`](../samples/src/main/java/com/oracle/database/selectai/samples/agent/PooledTeamResumeSample.java)
demonstrates an actual `WAITING_FOR_HUMAN` transition rather than ordinary
conversation continuation. It uses an existing human-input team and Oracle
UCP, included only in the sample and test dependencies:

1. Run `start` to create a conversation, print its ID, and invoke the team.
2. The sample verifies that the database reports `WAITING_FOR_HUMAN`, then
   closes its pool and exits. A different state fails the sample.
3. Preserve the team name and conversation ID. Run `resume` in a new process
   with the same database identity and the human response.
4. A new pool and client check the waiting state and submit the response with
   the original ID. The sample reports `SUCCEEDED` or another
   `WAITING_FOR_HUMAN` pause; other states fail explicitly.

The two processes cannot share a JDBC session. Within each process, each SDK
operation borrows and returns a connection, and no connection is retained
between human interactions. The sample does not retry failed runs or force a
state transition. Keep the conversation for inspection or further input, then
remove it with `DropConversationSample` once the workflow is finished and no
longer needed. See the [sample setup and commands](../samples/README.md#pooled-human-input-resume).

### Managing Shared-Team Access

The team owner can manage consumer access from Java through the owner-scoped
`SelectAIAgent` client:

```java
import com.oracle.database.selectai.agent.SelectAIAgent;

try (SelectAIAgent agentClient = SelectAIAgent.create(ownerConfig)) {
    agentClient.grantTeamAccess("SHARED_SUPPORT_TEAM", "TEAM_CONSUMER");
    // Later, when access is no longer required:
    agentClient.revokeTeamAccess("SHARED_SUPPORT_TEAM", "TEAM_CONSUMER");
}
```

`grantTeamAccess(...)` and `revokeTeamAccess(...)` pass the owner's unqualified
team name to `DBMS_CLOUD_AI_AGENT`. The connected user must own the team; a
database administrator is not inherently required. The grantee runs the shared team
with `SelectAIAgent.runTeam("OWNER.SHARED_SUPPORT_TEAM", request)`. Access is
limited to running the team as a unit; it does not grant permission to modify
the team or independently run its agents, tasks, or tools. Revocation blocks
new runs and resumes but does not terminate a process already in progress.

When the owner already has a database-backed `Team` handle, the equivalent
operations are `Team#grantAccess(String)` and `Team#revokeAccess(String)`.
`DatabaseAdmin` remains reserved for setup and administrative operations.

### Conversation workflow

For a new run without established session context, create a database
conversation and pass its returned ID. An arbitrary client-generated UUID is
not a substitute. To resume a team waiting for human input, retain the ID and
supply it with both the initial prompt and the later response. This also works
when the calls borrow different pooled connections.

The conversation lifecycle is managed by the core `SelectAI` API, while team
execution is managed by `SelectAIAgent`. The Java workflow is:

1. Create or load a database conversation through `SelectAI` and retain its
   database-generated ID.
2. Pass that ID to `RunTeamRequest.conversationId(...)` and call
   `Team#run(RunTeamRequest)` or `SelectAIAgent#runTeam(String, RunTeamRequest)`.
3. Reuse the same ID for a later human response,
   `getTeamStateByConversationId(...)`, or `setTeamExecutionState(...)` call.
4. Drop the conversation through the core `Conversation` API when it is no
   longer needed.

`runTeam(...)` returns a `TeamRunResult` containing the database result; it does
not create or return a conversation ID. For a shared team, the state operations use the
same owner-qualified team name passed to `runTeam(...)`.

Team execution is synchronous. A long-running workflow can retain the
operation connection until the database returns a result. Applications should
size connection pools for expected concurrent workflow count, configure an
appropriate SDK query timeout, and retry only when the workflow is safe to
repeat. A JDBC statement timeout does not guarantee cancellation of work that
is already running in the database or at an external model provider.

Assuming `selectAI` is an open `SelectAI` client and `team` is an existing
`Team` handle:

```java
Conversation conversation = selectAI.conversation(
        ConversationAttributes.builder().title("Support request").build());
String conversationId = conversation.create();
team.run(RunTeamRequest.builder()
        .userPrompt("I want to return a chair")
        .conversationId(conversationId)
        .build());
TeamRunResult result = team.run(RunTeamRequest.builder()
        .userPrompt("My order number is 1234")
        .conversationId(conversationId)
        .build());
System.out.println(result.getRawValue());
```

Call `conversation.drop(false)` when it is no longer needed. If neither a conversation
ID nor session context is present, the database can reject the run with
`ORA-20053`.

`RunTeamRequest` exposes the typed `RunTeamParams` model for the documented
`RUN_TEAM` runtime parameters. It includes `conversationId`,
`instructionVariables`, `attributeVariables`, and `stateVariables`. The
convenience methods on `RunTeamRequest.Builder`, such as
`conversationId(...)` and `stateVariables(...)`, remain available for the
common case. Do not combine those individual methods with `params(...)` in the
same request. The prompt is optional, as in
`DBMS_CLOUD_AI_AGENT.RUN_TEAM`. The same `RunTeamRequest` can be used with
`SelectAIAgent#runTeam(String, RunTeamRequest)` across pooled connections.
Builders can be reused for subsequent prompts without changing earlier
requests. Runtime-variable maps are immutable snapshots, including nested
maps and lists; `BigDecimal` values retain their decimal precision. See
[`RunTeamParamsSample`](../samples/src/main/java/com/oracle/database/selectai/samples/agent/RunTeamParamsSample.java)
for an offline example.

For an owner-scoped team handle, prefer
`Team#getStateByConversationId(String)` and
`Team#setExecutionState(ExecutionStateRequest)` with the same
`conversation_id`. Use `ExecutionStateParams.forConversationId(String)` when
constructing typed `SET_EXECUTION_STATE` parameters. Use
`ExecutionStateRequest.Builder#params(ExecutionStateParams)` when the request
should expose those parameters explicitly. The convenience
`ExecutionStateRequest.Builder#conversationId(String)` method is equivalent
for the common case. The typed execution-state model currently supports the
required `conversation_id` parameter explicitly.
For a team granted by another owner, use
`SelectAIAgent#getTeamStateByConversationId(String, String)` and
`SelectAIAgent#setTeamExecutionState(String, ExecutionStateRequest)` with the
owner-qualified team name. These top-level methods do not
load owner-only team metadata and are intended for shared-team state
inspection and management.

### Runtime Variables

`SelectAIAgent` exposes the session-scoped
`DBMS_CLOUD_AI_AGENT.GET_VARIABLE` and `SET_VARIABLE` operations:

```java
agentClient.setVariable("state_variables", "request_id", "REQ-123");
String requestId = agentClient.getVariable("state_variables", "request_id");
agentClient.setVariable("state_variables", "request_id", null); // clear
```

The default scope is `ephemeral`; overloads accepting an explicit scope are
also available. A missing variable is returned as `null`, and a null value
clears the variable. Runtime variables belong to the current database session.
In `DataSource` mode, separate operations may borrow different pooled
connections, so applications should use a connection mode with session
affinity when a variable must be read by a later operation.

Operations that require an existing database resource reject configured objects
that have not completed `create()`.

For an agent, task, tool, or team pending creation, `getStatus()` returns its
configured status (or `null` if none was supplied) without a database query.
For a database-backed resource, each call fetches only current status from the
corresponding `USER_AI_AGENTS`, `USER_AI_AGENT_TASKS`, `USER_AI_AGENT_TOOLS`, or
`USER_AI_AGENT_TEAMS` view. The description is supplied or loaded when the
handle is initialized and does not change on status refresh. If the resource no
longer exists or the lookup fails, it throws `SelectAIException`.

## Resource Operations

The name-only `agent(...)`, `task(...)`, `tool(...)`, and `team(...)` factories
load existing resources and their attributes before returning. Factories that
take attributes configure a resource in memory; call `create()` to persist it.

| Resource | Common operations | Additional operations |
| --- | --- | --- |
| [`Agent`](apidocs/com/oracle/database/selectai/agent/Agent.html) | `create()`, `enable()`, `disable()`, `drop(boolean)`, `setAttribute(...)`, `setAttributes(AgentAttributes)`, `getAgentName()`, `getDescription()`, `getStatus()`, `getAgentAttributes()` | None |
| [`Task`](apidocs/com/oracle/database/selectai/agent/Task.html) | `create()`, `enable()`, `disable()`, `drop(boolean)`, `setAttribute(...)`, `setAttributes(TaskAttributes)`, `getTaskName()`, `getDescription()`, `getStatus()`, `getTaskAttributes()` | None |
| [`Tool`](apidocs/com/oracle/database/selectai/agent/Tool.html) | `create()`, `enable()`, `disable()`, `drop(boolean)`, `setAttribute(...)`, `setAttributes(ToolAttributes)`, `getToolName()`, `getDescription()`, `getStatus()`, `getToolAttributes()` | `describe()`, `run(String)` returns `ToolRunResult` |
| [`Team`](apidocs/com/oracle/database/selectai/agent/Team.html) | `create()`, `enable()`, `disable()`, `drop(boolean)`, `setAttribute(...)`, `setAttributes(TeamAttributes)`, `getTeamName()`, `getDescription()`, `getStatus()`, `getTeamAttributes()` | `run(RunTeamRequest)` returns `TeamRunResult`, `describe()` returns `TeamDescription`, `getStateByConversationId(String)`, `setExecutionState(ExecutionStateRequest)`, `exportDefinition(...)` |

`TeamDescription` exposes the database-reported team name, description, and
optional version through typed accessors. `TeamDescription#getSkills()` returns
the A2A-compatible skills representation as JSON, preserving fields introduced
by newer database releases. `Team.describe()` returns `null` if the database
returns no value. A non-null response must be a single JSON object; malformed
or non-object responses result in `SelectAIException` rather than empty
metadata. Unknown fields are tolerated, and absent optional fields remain null.

Team state methods return the `TeamExecutionState`
enum directly. Known states are available as constants; database state values
introduced by a newer release map to `TeamExecutionState.UNKNOWN`.

`SelectAIAgent#getTeamStateByConversationId(String, String)` and
`SelectAIAgent#setTeamExecutionState(String, ExecutionStateRequest)` provide
the corresponding state operations for owner-qualified shared teams that
cannot be opened through `SelectAIAgent#team(String)`.

### Tool type compatibility

`ToolType` is a value object rather than a closed enum. The SDK provides
constants for tool types known by this release, and `ToolType.of(String)` also
accepts values introduced by newer database releases. When a tool is loaded,
the database value is preserved in `ToolAttributes#getToolType()` and can be
read directly with `getToolTypeValue()`. Unknown values remain available for
inspection and JSON round trips instead of preventing the tool from loading.

For a resource pending creation, `get*Attributes()` returns its configured
attributes without querying the database. For a database-backed resource, it
fetches current attributes on each call. If the attribute view returns no rows,
the getter checks whether the resource still exists; a missing resource raises
`SelectAIException`, while an existing resource may have empty attributes.
`getDescription()` returns the value
supplied or loaded when the handle was initialized; `getStatus()` does not
change it. See the linked interface Javadocs for arguments and failure
behavior. Runnable examples are listed in the
[samples guide](../samples/README.md#finding-a-sample).

### Tool parameters

`ToolAttributes.Builder.toolParams(ToolParams)` and
`ToolAttributes.getToolParams()` use immutable parameter models instead of
untyped `Object` values. These configure a built-in tool, not the input supplied
to `Tool.run(String)`.

| Model | Tool type | Typed builder fields |
| --- | --- | --- |
| `SqlToolParams` | `SQL` | `profileName` |
| `RagToolParams` | `RAG` | `profileName` |
| `WebSearchToolParams` | `WEBSEARCH` | `credentialName` |
| `SlackNotificationToolParams` | `NOTIFICATION` | `credentialName`, `channel`; sets `notification_type` to `slack` |
| `EmailNotificationToolParams` | `NOTIFICATION` | `credentialName`, `recipient`, `sender`, `smtpHost`, `subject`; sets `notification_type` to `email` |
| `GenericToolParams` | Any database-defined type | JSON-compatible parameters through `parameter(name, value)` or `fromMap(...)` |

```java
ToolAttributes attributes = ToolAttributes.builder()
        .toolType(ToolType.SQL)
        .toolParams(SqlToolParams.builder()
                .profileName("SALES_PROFILE")
                .build())
        .build();
```

The SDK serializes this as
`{"tool_type":"SQL","tool_params":{"profile_name":"SALES_PROFILE"}}`.
Pass the attributes to `SelectAIAgent.tool(...)` before `create()`, or to an
existing tool's `setAttributes(...)` method. Required fields and tool-specific
rules remain database-validated; builders permit partial configurations.

Tool-specific builders support `additionalParameter(name, value)` for fields
introduced by newer database releases. Use the typed setters for known fields.
For existing map-based code, replace `toolParams(map)` with
`toolParams(GenericToolParams.fromMap(map))` or use
`ToolParams.fromMap(toolType, map)` to select a typed model.

Loading through `tool(name)`, `listTools()`, or `getToolAttributes()` selects a
model using `tool_type` and, for notifications, `notification_type`. Unknown
fields are retained. Unknown types or incompatible field shapes use
`GenericToolParams` instead of preventing the tool from loading. Every model
exposes the complete parameter object through `toMap()`, with immutable nested
maps and lists. Caller-owned values are defensively copied.

See [ToolParamsSample](../samples/src/main/java/com/oracle/database/selectai/samples/agent/ToolParamsSample.java)
for all parameter models, getters, map conversion, and additional parameters.
It runs without a database connection. For database-backed creation, use
`CreateSqlToolSample`, `CreateRagToolSample`, `CreateWebSearchToolSample`,
`CreateSlackNotificationToolSample`, or
`CreateEmailNotificationToolSample`. `SetSqlToolParamsSample` demonstrates a
typed update, and `LoadToolSample` reads typed or generic parameters. See the
[typed tool sample instructions](../samples/README.md#typed-tool-parameter-samples)
for required variables, commands, and cleanup.

### Tool inputs

`ToolAttributes.Builder.toolInputs(List<ToolInput>)` and
`ToolAttributes.getToolInputs()` use immutable input metadata instead of
untyped maps. Each `ToolInput` has a name and optional description:

```java
ToolInput input = ToolInput.builder()
        .name("data_guard")
        .description("Only supported values are \"Enabled\" and \"Disabled\".")
        .build();
ToolAttributes attributes = ToolAttributes.builder()
        .toolInputs(List.of(input))
        .build();
```

The serialized attribute is:

```json
{"tool_inputs":[{"name":"data_guard","description":"Only supported values are \"Enabled\" and \"Disabled\"."}]}
```

The description guides the model; it does not create Java-side allowed-value
validation. Input metadata is distinct from the actual values sent to
`Tool.run(...)`. The database validates the input names and their compatibility
with the configured tool. No `mandatory`, `default_value`, or allowed-values
fields are modeled here.

The builder copies the list when it is supplied, and the getter returns an
immutable list. `null` omits the attribute, while an empty list sends an empty
JSON array. Null list entries are rejected locally. Loading a tool through
`tool(name)`, `listTools()`, or `getToolAttributes()` maps the database array
into the same typed entries.

See [ToolInputSample](../samples/src/main/java/com/oracle/database/selectai/samples/agent/ToolInputSample.java)
for an offline example, [CreateToolWithInputsSample](../samples/src/main/java/com/oracle/database/selectai/samples/agent/CreateToolWithInputsSample.java)
for custom-tool creation, [SetToolInputsSample](../samples/src/main/java/com/oracle/database/selectai/samples/agent/SetToolInputsSample.java)
for a persistent update, and [LoadToolSample](../samples/src/main/java/com/oracle/database/selectai/samples/agent/LoadToolSample.java)
for reading input metadata. See the [sample instructions](../samples/README.md#typed-tool-input-samples)
for required variables, Bash/PowerShell lifecycle commands, cleanup, and the
integration runner's optional typed-input workflow. The custom function must
already exist; the samples do not create, execute, or drop it.

### Updating Agent, Task, Tool, and Team Attributes

An existing database-backed `Agent`, `Task`, `Tool`, or `Team` can update one
attribute with a typed `setAttribute(...)` overload or update multiple
attributes with its corresponding `setAttributes(...)` method. These methods
invoke `DBMS_CLOUD_AI_AGENT.SET_ATTRIBUTE` and `SET_ATTRIBUTES` using JDBC
bind variables and the appropriate object type. The database validates
supported attribute names, values, and database-version-specific restrictions.

Attribute updates require a created or loaded resource. A
creation-configured resource must be created first; its initial attributes are
supplied through the corresponding `SelectAIAgent` creation factory. After a
successful update, the corresponding `get*Attributes()` method reads the
current values from the database. Database errors are reported as
`SelectAIException`.

Runnable examples:

- `SetAgentAttributeSample` demonstrates `Agent#setAttribute(String, String)`.
- `SetAgentAttributesSample` demonstrates `Agent#setAttributes(AgentAttributes)`.

The same typed update operations are available on `Task`, `Tool`, and `Team`
through their corresponding `setAttribute(...)` overloads and
`setAttributes(TaskAttributes)`, `setAttributes(ToolAttributes)`, or
`setAttributes(TeamAttributes)`. Each operation supplies the resource name
and object type to `DBMS_CLOUD_AI_AGENT`, uses JDBC bind variables, and reloads
the typed attributes from the database after success. Runnable examples are
available as `SetTaskAttributeSample`, `SetTaskAttributesSample`,
`SetToolAttributeSample`, `SetToolAttributesSample`, `SetTeamAttributeSample`,
and `SetTeamAttributesSample`.

### Team definition import and export

`ImportTeamRequest` accepts native JSON-compatible Java maps for team
specifications. `ExportTeamRequest` uses the typed `ExportTeamParams` model;
the documented export option is `overwrite`. The database validates
definitions, destinations, referenced resources, and caller privileges.

```java
import com.oracle.database.selectai.model.agent.ExportTeamRequest;
import com.oracle.database.selectai.model.agent.ExportTeamParams;
import com.oracle.database.selectai.model.agent.ImportTeamRequest;
import java.util.List;
import java.util.Map;

ImportTeamRequest request = ImportTeamRequest.builder(
                "GENAI_PROFILE", "SUPPORT_TEAM")
        .specification(Map.of("agents", List.of()))
        .force(true)
        .build();

ExportTeamRequest exportRequest = ExportTeamRequest.builder(
                "https://objectstorage.example/team.json")
        .params(ExportTeamParams.builder().overwrite(true).build())
        .build();
```

Use `Team.exportDefinition()` to receive the exported definition directly as
JSON using database defaults, or use
`Team.exportDefinition(ExportTeamParams)` to supply the typed `overwrite`
option. The `ExportTeamRequest` overload writes the definition to Object
Storage or a database directory.

## Team Attributes

`TeamAttributes` follows the `CREATE_TEAM` attributes documented by
`DBMS_CLOUD_AI_AGENT`:

| Java property | JSON attribute | Meaning |
| --- | --- | --- |
| `agents` | `agents` | Required agent-to-task mappings. |
| `process` | `process` | Team task execution process, such as `sequential`. |
| `longTermMemoryLength` | `long_term_memory_length` | Maximum number of prior task summaries retained as memory. A null value uses the database default of 30. |
| `supervisorAgent` | `supervisor_agent` | Name of an agent created with `AgentAttributes.Builder.supervisor(true)` and assigned to supervise this team. |

Each agent mapping contains an agent name and its task name. There are no
top-level `tasks` or `version` attributes in the `CREATE_TEAM` payload.

```java
TeamAttributes attributes = TeamAttributes.builder()
        .agents(List.of(TeamAttributes.TeamMember.builder("SUPPORT_AGENT")
                .task("SUPPORT_TASK")
                .build()))
        .process("sequential")
        .build();
```

### Supervisor-led Teams

The agent and team settings work together: `AgentAttributes.Builder.supervisor(true)`
designates an agent as a supervisor at creation time, while
`TeamAttributes.Builder.supervisorAgent(name)` assigns that agent to a team.
The supervisor flag cannot be changed after agent creation. The team's `agents`
list contains worker agent-task pairs only; do not include the supervisor there.
Use `process("sequential")` so the supervisor delegates one worker subtask at a
time. Keep worker-specific instructions on the worker tasks.

See [SupervisorTeamSample](../samples/src/main/java/com/oracle/database/selectai/samples/agent/SupervisorTeamSample.java)
for a complete example that creates a SQL tool, revenue and margin workers,
their tasks, a supervisor, a team, and a conversation. It runs a synthetic
analysis request and cleans up the resources it creates. An existing profile
and a database supporting supervisor-led teams are required. See the
[sample run instructions](../samples/README.md#supervisor-led-team).

## Logging And Security

The Agent API follows the SDK logging policy:

- `DEBUG` logs operation starts and diagnostics.
- `INFO` logs successful state-changing operations.
- `ERROR` logs failed database operations with resource identifiers.

Agent operation messages do not explicitly include prompt text, tool input
JSON, generated output, full attribute payloads, credentials, passwords,
private keys, or connection secret material. However, current `ERROR` logs
include the original JDBC exception. Its message, causes, and suppressed
exceptions can contain customer data or secrets echoed by the database or
driver; these logs are not automatically redacted. The original cause is also
preserved in `SelectAIException`, so application-level exception logging needs
the same review. Sample programs may print result payloads to standard output for
test evidence; production applications should apply their own redaction,
retention, and access-control policy.

## Method Summary

| Java type | Method signature | Purpose from Java comments | Key validation / failure semantics |
| --- | --- | --- | --- |
| `SelectAIAgent` | `static SelectAIAgent create(DbConnectionConfig dbConnectionConfig) throws SelectAIException` | Creates a SelectAIAgent client from database connection configuration. | Requires non-null connection configuration. Throws `SelectAIException` when the database connection cannot be initialized. |
| `SelectAIAgent` | `static SelectAIAgent create(DbConnectionConfig dbConnectionConfig, SelectAIOptions options) throws SelectAIException` | Creates a SelectAIAgent client from database connection configuration and SDK execution options. | Requires non-null connection configuration. `options` may be null to use defaults. Throws `SelectAIException` when the database connection cannot be initialized. |
| `SelectAIAgent` | `static SelectAIAgent create(DataSource dataSource)` | Creates a DataSource-backed SelectAIAgent client. | Requires non-null `DataSource`. |
| `SelectAIAgent` | `static SelectAIAgent create(DataSource dataSource, SelectAIOptions options)` | Creates a DataSource-backed SelectAIAgent client with SDK execution options. | Requires non-null `DataSource`. `options` may be null to use defaults. |
| `SelectAIAgent` | `Agent agent(String agentName) throws SelectAIException` | Loads an existing agent and its attributes. | Requires non-null, non-blank agent name; fails if missing or metadata cannot be loaded. |
| `SelectAIAgent` | `Agent agent(String agentName, AgentAttributes attributes, String description, AgentStatus status)` | Initializes an agent object for explicit creation. | Requires non-null, non-blank agent name and non-null attributes. `description` and `status` are optional. |
| `SelectAIAgent` | `Task task(String taskName) throws SelectAIException` | Loads an existing task and its attributes. | Requires non-null, non-blank task name; fails if missing or metadata cannot be loaded. |
| `SelectAIAgent` | `Task task(String taskName, TaskAttributes attributes, String description, TaskStatus status)` | Initializes a task object for explicit creation. | Requires non-null, non-blank task name and non-null attributes. `description` and `status` are optional. |
| `SelectAIAgent` | `Tool tool(String toolName) throws SelectAIException` | Loads an existing tool and its attributes. | Requires non-null, non-blank tool name; fails if missing or metadata cannot be loaded. |
| `SelectAIAgent` | `Tool tool(String toolName, ToolAttributes attributes, String description, ToolStatus status)` | Initializes a tool object for explicit creation. | Requires non-null, non-blank tool name and non-null attributes. `description` and `status` are optional. |
| `SelectAIAgent` | `Team team(String teamName) throws SelectAIException` | Loads an owned team and its attributes. | Requires non-null, non-blank team name; fails if missing or metadata cannot be loaded. |
| `SelectAIAgent` | `Team team(String teamName, TeamAttributes attributes, String description, TeamStatus status)` | Initializes a team object for explicit creation. | Requires non-null, non-blank team name and non-null attributes. `description` and `status` are optional. |
| `SelectAIAgent` | `TeamRunResult runTeam(String teamName, RunTeamRequest request) throws SelectAIException` | Runs an owned or granted team without loading owner-only metadata. | Requires a non-blank name and non-null request; a granted team uses `OWNER.TEAM_NAME`. The database checks access. |
| `SelectAIAgent` | `TeamExecutionState getTeamStateByConversationId(String teamName, String conversationId) throws SelectAIException` | Returns the latest state for a team run identified by a database conversation. | Supports owner-qualified shared-team names; requires non-blank arguments. |
| `SelectAIAgent` | `boolean setTeamExecutionState(String teamName, ExecutionStateRequest request) throws SelectAIException` | Updates the state of an owned or granted team run. | Requires a non-blank team name and non-null request; the database validates the state and conversation. |
| `SelectAIAgent` | `String getVariable(String namespace, String name) throws SelectAIException` / `getVariable(String, String, String)` | Reads a session-scoped runtime variable. | Requires non-blank namespace, name, and explicit scope; separate pooled connections may not share session state. |
| `SelectAIAgent` | `boolean setVariable(String namespace, String name, String value) throws SelectAIException` / `setVariable(String, String, String, String)` | Sets or clears a session-scoped runtime variable. | A null value clears the variable; session affinity is required when a later operation must read it. |
| `SelectAIAgent` | `List<Agent> listAgents() throws SelectAIException` | Lists agents owned by the current user from `USER_AI_AGENTS`. | Loads attributes for each handle; database query failures raise `SelectAIException`. |
| `SelectAIAgent` | `List<Agent> listAgents(String agentNamePattern) throws SelectAIException` | Lists owned agents whose names match a case-insensitive database regular expression. | Rejects null or blank patterns locally; binds the pattern in the database query. |
| `SelectAIAgent` | `List<Task> listTasks() throws SelectAIException` | Lists tasks owned by the current user from `USER_AI_AGENT_TASKS`. | Loads attributes for each handle; database query failures raise `SelectAIException`. |
| `SelectAIAgent` | `List<Task> listTasks(String taskNamePattern) throws SelectAIException` | Lists owned tasks whose names match a case-insensitive database regular expression. | Rejects null or blank patterns locally; binds the pattern in the database query. |
| `SelectAIAgent` | `List<Tool> listTools() throws SelectAIException` | Lists tools owned by the current user from `USER_AI_AGENT_TOOLS`. | Loads attributes for each handle; database query failures raise `SelectAIException`. |
| `SelectAIAgent` | `List<Tool> listTools(String toolNamePattern) throws SelectAIException` | Lists owned tools whose names match a case-insensitive database regular expression. | Rejects null or blank patterns locally; binds the pattern in the database query. |
| `SelectAIAgent` | `List<Team> listTeams() throws SelectAIException` | Lists teams owned by the current database user. | Loads metadata from `USER_AI_AGENT_TEAMS` and attributes from `USER_AI_AGENT_TEAM_ATTRIBUTES`; database failures raise `SelectAIException`. |
| `SelectAIAgent` | `List<Team> listTeams(String teamNamePattern) throws SelectAIException` | Lists owned teams whose names match a case-insensitive database regular expression. | Rejects null or blank patterns locally; granted teams owned by others are not listed. |
| `SelectAIAgent` | `List<AvailableTeam> listAvailableTeams() throws SelectAIException` | Discovers teams available to the calling user, including shared teams. | Delegates to `DBMS_CLOUD_AI_AGENT.LIST_TEAMS()` and returns read-only discovery snapshots rather than mutable `Team` handles. |
| `SelectAIAgent` | `String getDefinition(AgentObjectType objectType, String objectName, boolean includeDependentObjects) throws SelectAIException` | Returns the canonical PL/SQL definition for an agent framework object. | Requires object type and non-null, non-blank object name. Throws `SelectAIException` when the database operation fails. |
| `SelectAIAgent` | `boolean importTeam(ImportTeamRequest request) throws SelectAIException` | Imports an agent team definition into the database. | Requires non-null request. Request validates import source. Throws `SelectAIException` when import fails. |
| `SelectAIAgent` | `void close() throws SelectAIException` | Releases resources owned by this client. | Throws `SelectAIException` when an owned JDBC connection cannot be closed. |
