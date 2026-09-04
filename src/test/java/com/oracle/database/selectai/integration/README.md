# Java Select AI integration tests

These tests execute the Java SDK against a live Oracle Database. They are under
`src/test/java/com/oracle/database/selectai/integration` and use JUnit 5 with Maven
Surefire.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Configure Environment](#configure-environment)
- [Required Configuration](#required-configuration)
- [Feature Minimal Configuration](#feature-minimal-configuration)
  - [Connection](#connection)
  - [Profile, Generate, Translate, Conversation, Feedback](#profile-generate-translate-conversation-feedback)
  - [Credential](#credential)
  - [Provider](#provider)
  - [Vector Index](#vector-index)
  - [Summarize](#summarize)
  - [Synthetic Data](#synthetic-data)
- [Variable Catalog](#variable-catalog)
  - [Shared database settings](#shared-database-settings)
  - [OCI settings used by the default profile fixture](#oci-settings-used-by-the-default-profile-fixture)
  - [Credential tests](#credential-tests)
  - [Provider tests](#provider-tests)
  - [Summarize, translate, and vector-index tests](#summarize-translate-and-vector-index-tests)
- [Run the tests](#run-the-tests)
- [Integration test number ranges](#integration-test-number-ranges)
- [Test behavior and troubleshooting](#test-behavior-and-troubleshooting)

## Prerequisites

- JDK 17 or newer
- Maven 3.8 or newer
- A test Oracle Database with `DBMS_CLOUD_AI` installed and accessible to the
  configured user
- An Oracle wallet when using a wallet-based JDBC URL
- The database privileges required by the selected suite. Some tests create
  tables, credentials, profiles, indexes, users, and database links, so use an
  isolated test database rather than a production database.
- Valid credentials for any external provider or object-storage location used
  by the selected tests

## Configure Environment

Set the required `SELECT_AI_IT_*` environment variables before running Maven.
Maven passes the process environment to the test JVM, and the integration
fixture reads those variables directly with `System.getenv()`. The fixture does
not read a project-local `.env` file.

Keep real secrets in your shell environment, ADE setup, CI secret store, or
another approved secret-management mechanism. Do not commit local files that
contain real credentials. `SELECT_AI_IT_OCI_PRIVATE_KEY` may contain either
real multiline PEM text or escaped `\n`/`\r` sequences; the fixture converts
escaped newlines for that private-key variable.

## Required Configuration

Every integration test needs database connectivity because the shared fixture
initializes a live test schema before each integration class.

Set these for any integration run:

```bash
export SELECT_AI_IT_DB_USER=<database user>
export SELECT_AI_IT_DB_PASSWORD=<database password>
```

Then choose one connection style:

```bash
# Walletless JDBC URL.
export SELECT_AI_IT_DB_URL=<walletless JDBC URL>
```

or:

```bash
# Wallet-derived JDBC URL.
export SELECT_AI_IT_DB_NAME=<database/TNS service name>
export SELECT_AI_IT_WALLET_LOCATION=<wallet directory>
```

For the full `ConnectionIT` suite, set both styles: a walletless
`SELECT_AI_IT_DB_URL`, plus `SELECT_AI_IT_DB_NAME` and
`SELECT_AI_IT_WALLET_LOCATION`.

Most profile-backed suites also need an OCI signing-key credential because the
shared profile fixture creates an isolated GenAI credential per test:

```bash
export SELECT_AI_IT_OCI_USER_OCID=<OCI user OCID>
export SELECT_AI_IT_OCI_TENANCY_OCID=<OCI tenancy OCID>
export SELECT_AI_IT_OCI_PRIVATE_KEY="<private key content>"
export SELECT_AI_IT_OCI_FINGERPRINT=<OCI key fingerprint>
export SELECT_AI_IT_OCI_COMPARTMENT_ID=<OCI compartment OCID>
```

`SELECT_AI_IT_OCI_MODEL` is optional for the shared profile fixture, but set it when
you want a specific OCI GenAI model or when running `ProviderIT` OCI coverage.

## Feature Minimal Configuration

Start with the required database variables above, then add the smallest feature
block that matches the suite you want to run.

### Connection

Run basic walletless connection coverage:

```bash
export SELECT_AI_IT_DB_URL=<walletless JDBC URL>
mvn -Dtest='ConnectionIT#test10101ConnectionWithoutWallet' test
```

Run wallet coverage:

```bash
export SELECT_AI_IT_DB_NAME=<database/TNS service name>
export SELECT_AI_IT_WALLET_LOCATION=<wallet directory>
mvn -Dtest='ConnectionIT#test10100ConnectionSuccessWithWallet' test
```

Optional connection variables:

```bash
export SELECT_AI_IT_WALLET_PASSWORD=<wallet password>
```

### Profile, Generate, Translate, Conversation, Feedback

Use the database variables plus the OCI profile fixture variables:

```bash
export SELECT_AI_IT_OCI_USER_OCID=<OCI user OCID>
export SELECT_AI_IT_OCI_TENANCY_OCID=<OCI tenancy OCID>
export SELECT_AI_IT_OCI_PRIVATE_KEY="<private key content>"
export SELECT_AI_IT_OCI_FINGERPRINT=<OCI key fingerprint>
export SELECT_AI_IT_OCI_COMPARTMENT_ID=<OCI compartment OCID>
export SELECT_AI_IT_OCI_MODEL=<OCI GenAI model>
```

Example:

```bash
mvn -Dtest='CreateProfileIT' test
```

### Credential

Credential lifecycle tests need only the required database variables for the
basic create/drop coverage.

Add these when running username/password credential scenarios with real values:

```bash
export SELECT_AI_IT_CRED_USERNAME=<credential username>
export SELECT_AI_IT_CRED_PASSWORD=<credential password>
```

Local-user credential coverage creates a temporary database user with
`SELECT_AI_IT_DB_PASSWORD`; no additional password variable is required.

### Provider

Provider tests need the required database variables plus the provider-specific
credentials for the provider under test. For OCI provider coverage, also set:

```bash
export SELECT_AI_IT_OCI_USER_OCID=<OCI user OCID>
export SELECT_AI_IT_OCI_TENANCY_OCID=<OCI tenancy OCID>
export SELECT_AI_IT_OCI_PRIVATE_KEY="<private key content>"
export SELECT_AI_IT_OCI_FINGERPRINT=<OCI key fingerprint>
export SELECT_AI_IT_OCI_COMPARTMENT_ID=<OCI compartment OCID>
export SELECT_AI_IT_OCI_MODEL=<OCI GenAI model>
```

For non-OCI providers, use the provider variable table below.

### Vector Index

Vector-index creation tests need the required database variables, OCI signing
credential variables, and an embedding location:

```bash
export SELECT_AI_IT_OCI_USER_OCID=<OCI user OCID>
export SELECT_AI_IT_OCI_TENANCY_OCID=<OCI tenancy OCID>
export SELECT_AI_IT_OCI_PRIVATE_KEY="<private key content>"
export SELECT_AI_IT_OCI_FINGERPRINT=<OCI key fingerprint>
export SELECT_AI_IT_OCI_COMPARTMENT_ID=<OCI compartment OCID>
export SELECT_AI_IT_VECTOR_INDEX_EMBEDDING_LOCATION=<embedding model or object-storage location>
```

### Summarize

Prompt-only summarization uses the profile-backed configuration. URI-based
summarization also needs:

```bash
export SELECT_AI_IT_SUMMARIZE_LOCATION_URI=<readable object-storage URI>
export SELECT_AI_IT_SUMMARIZE_CREDENTIAL_NAME=<credential for the URI>
```

`SELECT_AI_IT_SUMMARIZE_CREDENTIAL_NAME` can be omitted when
`SELECT_AI_IT_OCI_CREDENTIAL_NAME` points to a suitable existing credential.

### Synthetic Data

Synthetic-data tests use the required database variables and the shared test
schema created by the fixture. Add profile-backed OCI variables only for tests
that generate through a profile-backed operation.

## Variable Catalog

Use this catalog when a feature requires extra settings beyond its minimal
configuration.

### Shared database settings

| Variable | Required value | Description |
| --- | --- | --- |
| `SELECT_AI_IT_DB_USER` | Yes | Database user used by the integration tests. |
| `SELECT_AI_IT_DB_PASSWORD` | Yes | Password for the database user. |
| `SELECT_AI_IT_DB_URL` | One connection style | Explicit JDBC URL. For `ConnectionIT`'s no-wallet test, this must be a walletless URL and must not contain `TNS_ADMIN`. |
| `SELECT_AI_IT_DB_NAME` | Required for wallet-specific tests | TNS service/database name. The wallet URL is built as `jdbc:oracle:thin:@<DB_NAME>_high?TNS_ADMIN=<WALLET_LOCATION>`. |
| `SELECT_AI_IT_WALLET_LOCATION` | Required for wallet-specific tests | Directory containing the wallet. |
| `SELECT_AI_IT_WALLET_PASSWORD` | Optional | Password for the password-protected-wallet scenario. |

For the shared fixture, provide `SELECT_AI_IT_DB_URL`, or provide both
`SELECT_AI_IT_DB_NAME` and `SELECT_AI_IT_WALLET_LOCATION`. To run all connection
variants, provide all three URL-related values: a walletless `DB_URL`, plus
`DB_NAME` and `WALLET_LOCATION`.

### OCI settings used by the default profile fixture

Profile-based suites create a unique OCI signing-key credential for each test.
These values are therefore required for the normal profile-based integration
tests, as well as OCI provider and vector-index tests.

| Variable | Required value | Description |
| --- | --- | --- |
| `SELECT_AI_IT_OCI_USER_OCID` | Yes for profile, OCI provider, credential, and vector-index coverage | OCI user OCID. |
| `SELECT_AI_IT_OCI_TENANCY_OCID` | Yes for profile, OCI provider, credential, and vector-index coverage | OCI tenancy OCID. |
| `SELECT_AI_IT_OCI_PRIVATE_KEY` | Yes for profile, OCI provider, credential, and vector-index coverage | OCI signing private key. This can be a multiline value or use escaped `\n` sequences. |
| `SELECT_AI_IT_OCI_FINGERPRINT` | Yes for profile, OCI provider, credential, and vector-index coverage | Fingerprint for the OCI signing key. |
| `SELECT_AI_IT_OCI_COMPARTMENT_ID` | Required when the profile provider is OCI | OCI compartment used by the profile and OCI provider tests. |
| `SELECT_AI_IT_PROVIDER` | Optional; defaults to `oci` | Provider used by the shared profile fixture. |
| `SELECT_AI_IT_REGION` | Optional | OCI region override. The OCI provider tests default to `us-chicago-1` when this is absent. |
| `SELECT_AI_IT_OCI_MODEL` | Optional for the shared fixture; required by OCI provider tests | OCI GenAI model name. |
| `SELECT_AI_IT_OCI_APIFORMAT` | Optional; OCI provider tests default to `GENERIC` | OCI API format. |
| `SELECT_AI_IT_OCI_ENDPOINT_ID` | Only for the OCI endpoint profile test | OCI inference endpoint OCID. |
| `SELECT_AI_IT_OCI_RUNTIMETYPE` | Only for the OCI endpoint profile test | Endpoint runtime type; defaults to `COHERE`. |
| `SELECT_AI_IT_OCI_CREDENTIAL_NAME` | Optional | Existing credential name used as a fallback by URI summarization tests. |

Example OCI private-key export:

```bash
export SELECT_AI_IT_OCI_PRIVATE_KEY="<private key content>"
```

### Credential tests

These values are optional in the source because the tests provide placeholder
defaults, but set them to valid values when running the username/password
credential scenarios.

| Variable | Description |
| --- | --- |
| `SELECT_AI_IT_CRED_USERNAME` | Username stored in created username/password credentials. |
| `SELECT_AI_IT_CRED_PASSWORD` | Password stored in created username/password credentials. |

### Provider tests

`ProviderIT` skips a provider's tests when that provider's required values are
missing. The model/deployment overrides marked optional have source-code
defaults, but should be set when the provider account uses different values.

| Provider | Required variables | Optional overrides |
| --- | --- | --- |
| OpenAI | `SELECT_AI_IT_PROVIDER_OPENAI_API_KEY` | `SELECT_AI_IT_PROVIDER_OPENAI_MODEL` (default `gpt-5.6-luna`) |
| OpenAI-compatible | `SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_API_KEY`, `SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_ENDPOINT` | `SELECT_AI_IT_PROVIDER_OPENAI_COMPATIBLE_MODEL` (default `gpt-5.6-luna`) |
| Cohere | `SELECT_AI_IT_PROVIDER_COHERE_API_KEY` | `SELECT_AI_IT_PROVIDER_COHERE_MODEL` (default `command-a-03-2025`) |
| Azure | `SELECT_AI_IT_PROVIDER_AZURE_API_KEY` | `SELECT_AI_IT_PROVIDER_AZURE_RESOURCE_NAME`, `SELECT_AI_IT_PROVIDER_AZURE_DEPLOYMENT_NAME`, `SELECT_AI_IT_PROVIDER_AZURE_EMBEDDING_DEPLOYMENT_NAME` |
| AWS | `SELECT_AI_IT_PROVIDER_AWS_ACCESS_KEY_ID`, `SELECT_AI_IT_PROVIDER_AWS_SECRET_ACCESS_KEY`, `SELECT_AI_IT_PROVIDER_AWS_REGION` | `SELECT_AI_IT_PROVIDER_AWS_MODEL` (default `meta.llama3-70b-instruct-v1:0`) |
| Google | `SELECT_AI_IT_PROVIDER_GOOGLE_API_KEY` | `SELECT_AI_IT_PROVIDER_GOOGLE_MODEL` (default `gemini-3-flash-preview`) |
| Anthropic | `SELECT_AI_IT_PROVIDER_ANTHROPIC_API_KEY` | `SELECT_AI_IT_PROVIDER_ANTHROPIC_MODEL` (default `claude-opus-4-6`) |
| Hugging Face | `SELECT_AI_IT_PROVIDER_HUGGINGFACE_API_KEY`, `SELECT_AI_IT_PROVIDER_HUGGINGFACE_MODEL` | None |
| OCI | The OCI settings above, plus `SELECT_AI_IT_OCI_COMPARTMENT_ID` and `SELECT_AI_IT_OCI_MODEL` for `ProviderIT` | `SELECT_AI_IT_REGION`, `SELECT_AI_IT_OCI_APIFORMAT` |

### Summarize, translate, and vector-index tests

| Variable | Required when | Description |
| --- | --- | --- |
| `SELECT_AI_IT_SUMMARIZE_LOCATION_URI` | Running URI-based `SummarizeIT` tests | Readable object-storage URI containing the source document. |
| `SELECT_AI_IT_SUMMARIZE_CREDENTIAL_NAME` | URI summarization needs an explicit credential | Credential for `SELECT_AI_IT_SUMMARIZE_LOCATION_URI`; falls back to `SELECT_AI_IT_OCI_CREDENTIAL_NAME`. |
| `SELECT_AI_IT_VECTOR_INDEX_EMBEDDING_LOCATION` | Required for vector-index creation tests | Embedding model/object-storage location used by vector-index configuration. |

Summarization and translation tests use the defaults defined in the test source
for their prompt, text, languages, expected translation, and generated missing
location. No environment overrides are required for those values.

## Run the tests

From the repository root, run all integration test classes explicitly:

```bash
mvn -Dtest='**/*IT' test
```

If Maven or Java is not on `PATH` in the ADE environment:

```bash
export JAVA_HOME=<path to JDK 17>
export PATH="$JAVA_HOME/bin:<path to Maven bin>:$PATH"
mvn -Dtest='**/*IT' test
```

Run one suite:

```bash
mvn -Dtest='CreateProfileIT' test
```

Run one test method:

```bash
mvn -Dtest='CreateProfileIT#test12000CreateBasicProfile' test
```

## Integration test number ranges

Integration test numbers are stable identifiers owned by their Java test file.
Each file has a reserved range of 100 IDs, so adding a test does not renumber
tests in another file. Commented test declarations use the same range as their
owning file.

| File | Reserved range |
| --- | ---: |
| `ConnectionIT.java` | `10100–10199` |
| `PrivilegeIT.java` | `11500–11599` |
| `CreateCredentialIT.java` | `22000–22099` |
| `DropCredentialIT.java` | `23000–23099` |
| `CreateProfileIT.java` | `12000–12099` |
| `ListProfilesIT.java` | `12100–12199` |
| `ProfileAttributesIT.java` | `12200–12299` |
| `ProfileValidationIT.java` | `12300–12399` |
| `ProfileLifecycleIT.java` | `12400–12499` |
| `DatabaseAdminIT.java` | `13000–13099` |
| `CreateConversationIT.java` | `14000–14099` |
| `ConversationAttributesIT.java` | `14100–14199` |
| `ConversationReferencesIT.java` | `14200–14299` |
| `ConversationPromptsIT.java` | `14300–14399` |
| `TranslateIT.java` | `15000–15099` |
| `GenerateIT.java` | `16000–16099` |
| `ChatSessionIT.java` | `16300–16399` |
| `SummarizeIT.java` | `17000–17099` |
| `SyntheticDataIT.java` | `18000–18099` |
| `FeedbackIT.java` | `19000–19099` |
| `ConcurrencyIT.java` | `60000–60099` |
| `ProviderIT.java` | `30000–30099` |
| `CreateVectorIndexIT.java` | `50000–50099` |
| `DropVectorIndexIT.java` | `51000–51099` |
| `UpdateVectorIndexIT.java` | `52000–52099` |
| `GetVectorIndexAttributesIT.java` | `53000–53099` |
| `ListVectorIndexIT.java` | `54000–54099` |
| `EnableDisableVectorIndexIT.java` | `55000–55099` |

The exact method name depends on the test class. Maven writes individual
results and diagnostic output under `target/surefire-reports`.

## Test behavior and troubleshooting

- A plain `mvn test` uses Maven's normal test selection. Use
  `-Dtest='**/*IT'` to select the integration classes explicitly.
- The shared fixture recreates the common integration schema before each
  concrete integration class and removes managed profiles, credentials, and
  indexes after each test. Do not run these tests against shared or production
  data.
- Missing feature-specific settings generally cause only that feature's test
  to be skipped. Missing shared database settings prevent the suite from
  starting.
- If the database reports authentication or wallet errors, verify the user,
  password, JDBC URL, wallet directory, and wallet password independently.
- If a provider test is skipped, check the required provider variables in the
  table above and verify that the configured model/deployment is available to
  that provider account.
