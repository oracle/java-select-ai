# Select AI Java SDK Samples

This directory contains standalone Java samples for the Select AI Java SDK.
Each sample class is self-contained: it creates its own database connection
configuration, creates or opens the SDK object it needs, invokes one public API
method, and prints the result.

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

## Prerequisites

Set the database connection environment variables before compiling or running
samples:

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

Each sample class documents the additional environment variables required for
that specific method. For example:

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
- The integration script recreates its synthetic-data validation tables before
  running synthetic-data samples. When running those samples individually,
  create the target table first.

Some samples change database state. Review the class comments before running
samples that create, update, enable, disable, drop, grant, revoke, submit
feedback, or generate data.

### External dependencies by sample area

Some samples require database objects, cloud credentials, provider access, or
external content in addition to the common database connection variables.

| Sample area | External dependency | How to configure |
| --- | --- | --- |
| Profile creation and generation | Tables visible to `SELECTAI_DB_USER` that match the configured object list and prompts. If using Oracle sample schema objects such as `SH.CUSTOMERS` and `SH.COUNTRIES`, ensure the `SH` schema is installed and visible. `SH` is not guaranteed to exist in every Oracle Autonomous AI Database or Oracle Database instance. | Set `SELECTAI_PROFILE_OBJECT_LIST` and `SELECTAI_PROFILE_PROMPT` for your schema, or omit `SELECTAI_PROFILE_OBJECT_LIST` and rely on the configured object-list mode. |
| OCI profile and generation | OCI Generative AI profile attributes and a database credential that can authenticate to OCI. | Set `SELECTAI_PROFILE_CREDENTIAL_NAME`, `SELECTAI_PROFILE_OCI_COMPARTMENT_ID`, and other provider attributes required by the database profile. |
| Credential creation | Cloud credential material. | Set either `SELECTAI_CREDENTIAL_USERNAME`/`SELECTAI_CREDENTIAL_PASSWORD` or OCI signing-key variables such as `SELECTAI_USER_OCID`, `SELECTAI_TENANCY_OCID`, `SELECTAI_PRIVATE_KEY`, and `SELECTAI_FINGERPRINT`. Do not commit or log secret values. |
| Database administration | Administrative database privileges. | Use a database user authorized to grant/revoke package privileges, configure data access, and manage network ACLs. |
| Vector index creation | Readable document location for embedding. | Set `SELECTAI_VECTOR_INDEX_LOCATION` when running vector-index samples directly, or `SELECTAI_IT_VECTOR_LOCATION` when running `scripts/run-all-samples-integration.sh`. Ensure the referenced Object Storage URI is readable by the configured credential/profile. |
| URI-based summarization | Readable external document URI and credential. | Set `SELECTAI_SUMMARIZE_LOCATION_URI` and `SELECTAI_SUMMARIZE_CREDENTIAL_NAME` when using URI-based summarization. Inline-content summarization does not require an external URI. |
| Synthetic data generation | Target table or object list for generated rows. | The integration script creates its synthetic-data validation table. When running synthetic-data samples individually, create the target table first or point the sample variables to an existing table. |
| Conversation prompt deletion | Existing conversation prompt ID. | Set `SELECTAI_CONVERSATION_PROMPT_ID`. The integration script skips prompt deletion when no prompt ID is available. |

## Integration Script Prerequisites

The integration script `scripts/run-all-samples-integration.sh` uses default
sample inputs for the javaselectai26ai validation database. Before running the
default script flow, ensure these database objects are available:

The integration script is a Bash script. PowerShell users should run it from
Git Bash, WSL, or another Bash-compatible environment; it cannot be executed
directly as a native PowerShell script.

- `AI_TEST1` database user
- `AI_TEST2` database user
- `OCI_GEN_AI_CRED` database credential
- `SH.CUSTOMERS` and `SH.COUNTRIES`, or equivalent visible tables configured
  through the script object-list and prompt variables

Example setup SQL. Replace masked values before running in your database:

```sql
CREATE USER AI_TEST1 IDENTIFIED BY "<password>";
CREATE USER AI_TEST2 IDENTIFIED BY "<password>";

GRANT CREATE SESSION TO AI_TEST1;
GRANT CREATE SESSION TO AI_TEST2;

ALTER USER AI_TEST1 QUOTA UNLIMITED ON USERS;
ALTER USER AI_TEST2 QUOTA UNLIMITED ON USERS;

BEGIN
  DBMS_CLOUD.CREATE_CREDENTIAL (
      credential_name => 'OCI_GEN_AI_CRED',
      user_ocid       => '<oci-user-ocid>',
      tenancy_ocid    => '<oci-tenancy-ocid>',
      private_key     => '<private-key-without-passphrase>',
      fingerprint     => '<fingerprint>');
END;
/
```

The database user supplied through `SELECTAI_DB_USER` must have the privileges
required to create/drop Select AI resources, grant/revoke package privileges,
and manage network ACLs.

The default profile and generation samples in the integration script use this
object list and prompt:

```bash
export SELECTAI_PROFILE_OBJECT_LIST='[{"owner":"SH","name":"customers"},{"owner":"SH","name":"countries"}]'
export SELECTAI_PROFILE_PROMPT="how many customers"
```

`SH` is an Oracle sample schema and is not guaranteed to exist in every Oracle
Autonomous AI Database or Oracle Database instance. If your database does not
have those tables, change the object list and prompt in the script or export
equivalent values for tables that exist and are visible to `SELECTAI_DB_USER`.

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

## Compile Samples

Compile all active samples:

```bash
javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" -d samples/out $(find samples/src/main/java -name "*.java")
```

PowerShell:

```powershell
$sampleSources = Get-ChildItem samples/src/main/java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
javac --release 17 -cp "target/select-ai-java-1.0.0.jar;target/dependency/*" `
    -d samples/out $sampleSources
```

## Run Samples

Run a sample by using its fully qualified class name:

```bash
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.ListProfilesSample
```

PowerShell:

```powershell
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" `
    com.oracle.database.selectai.samples.profile.ListProfilesSample
```

More examples:

```bash
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.credential.CreateCredentialSample
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationsSample
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationPromptsSample
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.vectorindex.ListVectorIndexesSample
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.SummarizeProfileSample
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.profile.ChatSessionProfileSample
java -cp "samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*" com.oracle.database.selectai.samples.datasource.ListProfilesWithDataSourceSample
```

PowerShell:

```powershell
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" com.oracle.database.selectai.samples.credential.CreateCredentialSample
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationsSample
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" com.oracle.database.selectai.samples.conversation.ListConversationPromptsSample
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" com.oracle.database.selectai.samples.vectorindex.ListVectorIndexesSample
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" com.oracle.database.selectai.samples.profile.SummarizeProfileSample
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" com.oracle.database.selectai.samples.profile.ChatSessionProfileSample
java -cp "samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*" com.oracle.database.selectai.samples.datasource.ListProfilesWithDataSourceSample
```

## Runtime Classpath

The runtime classpath must include the compiled sample classes, the SDK jar, and
runtime dependencies copied by Maven:

```bash
samples/out:target/select-ai-java-1.0.0.jar:target/dependency/*
```

On PowerShell and Windows, use semicolons as classpath separators:

```text
samples/out;target/select-ai-java-1.0.0.jar;target/dependency/*
```

Key runtime libraries include:

- `com.oracle.database.jdbc:ojdbc11`
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
- `ListProfilesWithDataSourceSample` demonstrates creating `SelectAI` with a `DataSource`.

For exact environment variables and behavior, open the sample class and review
its class-level comments.
