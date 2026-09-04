# Development guide

This document describes the repository layout, local development workflow, and
engineering guidelines for the Java Select AI SDK. Customer-facing usage is
documented in [`README.md`](README.md).

## Table of Contents

- [Repository layout](#repository-layout)
- [Connection provider internals](#connection-provider-internals)
- [Build](#build)
- [Unit tests](#unit-tests)
- [Integration tests](#integration-tests)
- [Javadoc](#javadoc)
- [Samples](#samples)
- [Java code guidelines](#java-code-guidelines)
- [Generated diagrams](#generated-diagrams)
- [Change checklist](#change-checklist)

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/oracle/database/selectai` | Public SDK interfaces and entry points. |
| `src/main/java/com/oracle/database/selectai/model` | Public request, configuration, attribute, enum, and exception models. |
| `src/main/java/com/oracle/database/selectai/impl` | Internal implementations, JDBC execution helpers, connection providers, and SQL/PLSQL constants. |
| `src/main/java/com/oracle/database/selectai/util` | Internal SDK utility helpers. |
| `src/test/java/com/oracle/database/selectai` | Unit tests and database integration tests. |
| `samples/src/main/java/com/oracle/database/selectai/samples` | Standalone usage samples organized by feature. |
| `scripts` | Build, sample, and documentation helper scripts. |
| `doc/diagrams` | Source diagrams and rendered PNG diagrams. |
| `doc/apidocs` | Checked-in generated Javadoc, including sample source pages. |

Public customer-facing contracts belong in `com.oracle.database.selectai` and
`com.oracle.database.selectai.model`. Classes under `com.oracle.database.selectai.impl` are
internal implementation details and are not supported customer-facing APIs.
Use public interfaces and factory methods such as `SelectAI.create(...)`,
`DatabaseAdmin.create(...)`, and `DbConnection.create(...)` in samples and
documentation.

## Connection provider internals

`ConnectionProvider` and `ConnectionCallback` are public-package interfaces
used internally to compose SDK JDBC execution. They are not supported
application extension points or an additional public client construction mode;
public clients are created through `SelectAI.create(...)` and
`DatabaseAdmin.create(...)` with `DbConnectionConfig` or `DataSource`.

The SDK-provided connection implementations use these contracts as follows:

- A DataSource-backed provider borrows a connection for each SDK operation and
  closes it after the operation, normally returning pooled connections to the
  pool.
- A single-connection provider reuses the SDK-owned connection for the client
  lifetime. The owning `SelectAI` or `DatabaseAdmin` client closes it.
- A callback receives the connection for the current SDK operation. Internal
  callbacks must not close it; the provider owns the connection lifecycle.

When changing provider behavior, preserve the existing transaction and
threading contracts documented in `doc/datasource-connection-support.md` and
the public connection-owner Javadocs.

## Build

Use `pom.xml` for normal development and public builds:

```bash
mvn clean install
```

This compiles the SDK, runs unit tests, packages the JAR, and installs it in the
local Maven repository.

The project targets Java 17. Do not use language features newer than Java 17.

## Unit tests

Run the unit-test suite with:

```bash
mvn test
```

Run a specific test class or method with quoted Surefire patterns when using a
shell that expands `*`:

```bash
mvn -Dtest='DefaultProfileTest' test
mvn -Dtest='DefaultProfileTest#testName' test
```

Unit tests should cover validation, model serialization, JDBC binding behavior,
exception wrapping, connection ownership, logging-sensitive behavior, and
resource lifecycle changes as applicable.

## Integration tests

Integration tests require a configured Oracle database and feature-specific
provider, credential, Object Storage, or schema resources. See the integration
test instructions in
[`src/test/java/com/oracle/database/selectai/integration/README.md`](src/test/java/com/oracle/database/selectai/integration/README.md).

Run all integration test classes explicitly:

```bash
mvn -Dtest='**/*IT' test
```

Run one integration class or method with:

```bash
mvn -Dtest='CreateProfileIT' test
mvn -Dtest='CreateProfileIT#test12000CreateBasicProfile' test
```

Do not run integration tests against production or shared data. The fixtures
create and remove database objects and may change profile, credential,
conversation, vector-index, privilege, ACL, or synthetic-data state.

## Javadoc

Generate the public API documentation and sample source pages from the
repository root:

```bash
MVN="${MVN:-mvn}"
"$MVN" -Dforce=true clean compile javadoc:javadoc && \
MVN="$MVN" scripts/generate-sample-source-javadocs.sh
```

The generated documentation is written to `doc/apidocs`. The clean build avoids
stale incremental Javadoc output, and the compiled classes are required by the
sample-source generator. The Maven Javadoc configuration publishes the public
API packages while excluding internal implementation and sample packages from
the public package index. Sample source pages remain available so public API
comments can link directly to runnable sample code.

Generated Javadoc is checked into the repository. Do not edit generated HTML
manually; update the source comments or generator configuration and regenerate
the output. Review generated changes before submitting a pull request.

## Samples

Samples are standalone Java sources and are not part of the Maven main source
set. Build the SDK, copy the sample runtime dependencies, and compile samples:

```bash
mvn clean install
mvn -Psamples -DincludeScope=runtime -DoutputDirectory=target/dependency dependency:copy-dependencies
javac --release 17 -cp "target/select-ai-java-1.0.0.jar:target/dependency/*" \
  -d samples/out $(find samples/src/main/java -name "*.java")
```

The `samples` Maven profile adds `slf4j-simple` only for standalone sample
execution. The SDK itself depends on `slf4j-api`; SDK consumers must select and
configure their own SLF4J logging provider.

Review [`samples/README.md`](samples/README.md) for sample prerequisites,
environment variables, external resources, and state-changing operations.

## Java code guidelines

- Keep public APIs in `com.oracle.database.selectai` and `com.oracle.database.selectai.model`.
- Keep implementation classes under `com.oracle.database.selectai.impl` internal.
- Prefer public factory methods over direct implementation construction.
- Preserve source and binary compatibility for released public APIs.
- Use typed request and configuration models when options are expected to grow.
- Validate required inputs before opening JDBC connections or executing SQL/PLSQL.
- Use `IllegalArgumentException` for invalid arguments and
  `IllegalStateException` for invalid SDK object state.
- Wrap JDBC and database failures in `SelectAIException`, preserving the cause,
  JDBC error code, and SQLState when available.
- Bind user-controlled values through JDBC parameters. Do not concatenate them
  into executable SQL or PL/SQL.
- Use `Locale.ROOT` for case normalization.
- Use character streams for DBMS `CLOB` parameters.
- Never log passwords, private keys, access tokens, full credential
  configurations, prompts, generated responses, SQL text, or customer content.
- Use `DEBUG` for operation details, `INFO` for high-value successful state
  changes, and `ERROR` for failures.
- Keep samples runnable without source edits by using documented environment
  variables.
- Update relevant unit tests, integration tests, samples, documentation,
  diagrams, and generated Javadoc when public behavior changes.

## Generated diagrams

When a public API changes, review the corresponding diagrams under
`doc/diagrams`. Update the editable diagram source first, render the PNG, and
verify that labels, method relationships, and inheritance are readable at the
published image size.

## Change checklist

Before submitting a public API change:

1. Confirm the API shape and compatibility impact.
2. Update public Javadoc, including validation and exception behavior.
3. Add or update focused unit tests.
4. Update integration tests when database behavior is affected.
5. Update corresponding samples and environment-variable documentation.
6. Update README or supporting documentation when customer usage changes.
7. Update the relevant diagram and regenerate checked-in Javadoc if needed.
8. Run `git diff --check` and the applicable Maven tests.
