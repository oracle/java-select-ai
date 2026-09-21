#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/

set -u
set -e
set -o pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/run-all-samples-integration.sh [options] <db-user> <db-password> <jdbc-url>
  scripts/run-all-samples-integration.sh [options]

Options:
  --mvn <file>     Maven executable path.
  --java <file>    Java executable path.
  --javac <file>   Javac executable path. Optional; defaults to javac next to JAVA or on PATH.
  -h, --help       Show this help.

Or export these variables before running:
  SELECTAI_DB_USER
  SELECTAI_DB_PASSWORD
  SELECTAI_JDBC_URL
  SELECTAI_IT_VECTOR_LOCATION  Object Storage URI used by vector-index samples.
  SELECTAI_IT_COMPARTMENT_ID   OCI compartment OCID used by profile-creation samples.
  MVN
  JAVA
  JAVAC

The DB variables, SELECTAI_IT_VECTOR_LOCATION, and SELECTAI_IT_COMPARTMENT_ID
are required for the default run. The script supplies the remaining sample
inputs with integration defaults for javaselectai26ai.

Database prerequisites for the default run:
  - Database users AI_TEST1 and AI_TEST2 must exist.
  - Database credential OCI_GEN_AI_CRED must exist.
  - The default profile object list uses SH.CUSTOMERS and SH.COUNTRIES.
    If the SH sample schema is not installed, update SELECTAI_PROFILE_OBJECT_LIST
    and SELECTAI_PROFILE_PROMPT in this script to use tables visible to
    SELECTAI_DB_USER.
  - SELECTAI_IT_VECTOR_LOCATION must reference readable document content for
    vector-index creation. URI-based summarization, if enabled by sample
    variables, also requires a readable URI and matching database credential.
  - SELECTAI_DB_USER must have privileges required to create/drop Select AI
    resources, grant/revoke package privileges, and manage network ACLs.

Example setup SQL. Replace masked values before running in your database:

  CREATE USER AI_TEST1 IDENTIFIED BY "<password>";
  CREATE USER AI_TEST2 IDENTIFIED BY "<password>";

  GRANT CREATE SESSION TO AI_TEST1;
  GRANT CREATE SESSION TO AI_TEST2;

  ALTER USER AI_TEST1 QUOTA UNLIMITED ON USERS;
  ALTER USER AI_TEST2 QUOTA UNLIMITED ON USERS;

  BEGIN
    C##CLOUD$SERVICE.DBMS_CLOUD.CREATE_CREDENTIAL (
        credential_name => 'OCI_GEN_AI_CRED',
        user_ocid       => '<oci-user-ocid>',
        tenancy_ocid    => '<oci-tenancy-ocid>',
        private_key     => '<private-key-without-passphrase>',
        fingerprint     => '<fingerprint>');
  END;
  /
USAGE
}

MVN_INPUT="${MVN:-}"
JAVA_INPUT="${JAVA:-}"
JAVAC_INPUT="${JAVAC:-}"
POSITIONAL_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mvn)
      if [[ $# -lt 2 ]]; then
        echo "--mvn requires an executable path" >&2
        exit 2
      fi
      MVN_INPUT="$2"
      shift 2
      ;;
    --java)
      if [[ $# -lt 2 ]]; then
        echo "--java requires an executable path" >&2
        exit 2
      fi
      JAVA_INPUT="$2"
      shift 2
      ;;
    --javac)
      if [[ $# -lt 2 ]]; then
        echo "--javac requires an executable path" >&2
        exit 2
      fi
      JAVAC_INPUT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      while [[ $# -gt 0 ]]; do
        POSITIONAL_ARGS+=("$1")
        shift
      done
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      POSITIONAL_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ ${#POSITIONAL_ARGS[@]} -ne 0 && ${#POSITIONAL_ARGS[@]} -ne 3 ]]; then
  usage
  exit 2
fi

if [[ ${#POSITIONAL_ARGS[@]} -eq 3 ]]; then
  export SELECTAI_DB_USER="${POSITIONAL_ARGS[0]}"
  export SELECTAI_DB_PASSWORD="${POSITIONAL_ARGS[1]}"
  export SELECTAI_JDBC_URL="${POSITIONAL_ARGS[2]}"
fi

required_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    usage >&2
    exit 2
  fi
}

required_env SELECTAI_DB_USER
required_env SELECTAI_DB_PASSWORD
required_env SELECTAI_JDBC_URL
required_env SELECTAI_IT_VECTOR_LOCATION
required_env SELECTAI_IT_COMPARTMENT_ID

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}" || exit 2

resolve_command() {
  local label="$1"
  local value="$2"
  local fallback="$3"
  local env_name="$4"
  local resolved=""

  if [[ -n "${value}" ]]; then
    if [[ "${value}" == */* ]]; then
      if [[ ! -x "${value}" ]]; then
        echo "Invalid ${label} executable '${value}'." >&2
        exit 2
      fi
      printf '%s\n' "${value}"
      return
    fi

    resolved="$(command -v "${value}" || true)"
    if [[ -z "${resolved}" ]]; then
      echo "${label} command '${value}' was not found on PATH." >&2
      exit 2
    fi
    printf '%s\n' "${resolved}"
    return
  fi

  resolved="$(command -v "${fallback}" || true)"
  if [[ -z "${resolved}" ]]; then
    echo "${label} command '${fallback}' was not found on PATH." >&2
    echo "Set ${env_name}=/path/to/${fallback} or pass --${fallback} /path/to/${fallback}." >&2
    exit 2
  fi
  printf '%s\n' "${resolved}"
}

MVN_CMD="$(resolve_command "Maven" "${MVN_INPUT}" "mvn" "MVN")"
JAVA_CMD="$(resolve_command "Java" "${JAVA_INPUT}" "java" "JAVA")"

if [[ -n "${JAVAC_INPUT}" ]]; then
  JAVAC_CMD="$(resolve_command "Javac" "${JAVAC_INPUT}" "javac" "JAVAC")"
else
  JAVA_DIR="$(dirname "${JAVA_CMD}")"
  if [[ -x "${JAVA_DIR}/javac" ]]; then
    JAVAC_CMD="${JAVA_DIR}/javac"
  else
    JAVAC_CMD="$(resolve_command "Javac" "" "javac" "JAVAC")"
  fi
fi

POM_FILE="${POM_FILE:-pom.xml}"
SDK_VERSION="1.0.0"
SDK_JAR="target/select-ai-${SDK_VERSION}.jar"
COMPILE_CP="${SDK_JAR}:target/dependency/*"
RUNTIME_CP="samples/out:${SDK_JAR}:target/dependency/*"
REPORT_DIR="target/integration-sample-reports/$(date +%Y%m%d-%H%M%S)"
SOURCE_LIST="${REPORT_DIR}/sample-sources.txt"

mkdir -p "${REPORT_DIR}"

echo "Project root : ${PROJECT_ROOT}"
echo "Maven POM    : ${POM_FILE}"
echo "Maven binary : ${MVN_CMD}"
echo "Java binary  : ${JAVA_CMD}"
echo "Javac binary : ${JAVAC_CMD}"
echo "Report dir   : ${REPORT_DIR}"
echo "DB user      : ${SELECTAI_DB_USER}"
echo "JDBC URL     : configured"
echo

echo "Building SDK jar..."
"${MVN_CMD}" -f "${POM_FILE}" -DskipTests clean install

echo
echo "Copying runtime dependencies..."
"${MVN_CMD}" -f "${POM_FILE}" \
  -Psamples \
  -DincludeScope=runtime \
  -DoutputDirectory=target/dependency \
  dependency:copy-dependencies

echo
echo "Compiling samples..."
mkdir -p "${REPORT_DIR}"
rm -rf samples/out
mkdir -p samples/out
find samples/src/main/java -name "*.java" | sort > "${SOURCE_LIST}"
"${JAVAC_CMD}" --release 17 -cp "${COMPILE_CP}" -d samples/out @"${SOURCE_LIST}"

SETUP_SOURCE="${REPORT_DIR}/IntegrationDatabaseSetup.java"
cat > "${SETUP_SOURCE}" <<'JAVA'
/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class IntegrationDatabaseSetup {
    private IntegrationDatabaseSetup() {
    }

    public static void main(String[] args) throws Exception {
        String user = requiredEnv("SELECTAI_DB_USER");
        String password = requiredEnv("SELECTAI_DB_PASSWORD");
        String jdbcUrl = requiredEnv("SELECTAI_JDBC_URL");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             Statement statement = connection.createStatement()) {
            recreateSyntheticDataTables(statement);
        }
    }

    private static void recreateSyntheticDataTables(Statement statement) throws SQLException {
        dropTableIfExists(statement, "SYN_EMPLOYEES");
        dropTableIfExists(statement, "SYN_DEPARTMENTS");
        dropTableIfExists(statement, "SYN_CUSTOMERS");

        statement.execute("""
                CREATE TABLE SYN_CUSTOMERS (
                    customer_id     NUMBER GENERATED BY DEFAULT ON NULL AS IDENTITY,
                    full_name       VARCHAR2(100),
                    email_address   VARCHAR2(150),
                    city            VARCHAR2(50),
                    customer_since  DATE,
                    credit_limit    NUMBER(10,2),
                    customer_status VARCHAR2(20),
                    CONSTRAINT syn_customers_pk PRIMARY KEY (customer_id)
                )
                """);
        statement.execute("COMMENT ON TABLE SYN_CUSTOMERS IS "
                + "'Fictional customers used for application testing'");
        statement.execute("COMMENT ON COLUMN SYN_CUSTOMERS.CUSTOMER_STATUS IS "
                + "'Valid business values are ACTIVE, INACTIVE, and SUSPENDED'");
        statement.execute("COMMENT ON COLUMN SYN_CUSTOMERS.CREDIT_LIMIT IS "
                + "'Customer credit limit in US dollars'");

        statement.execute("""
                CREATE TABLE SYN_DEPARTMENTS (
                    department_id   NUMBER,
                    department_name VARCHAR2(100),
                    office_city     VARCHAR2(50),
                    annual_budget   NUMBER(12,2),
                    CONSTRAINT syn_departments_pk PRIMARY KEY (department_id)
                )
                """);
        statement.execute("""
                CREATE TABLE SYN_EMPLOYEES (
                    employee_id    NUMBER,
                    full_name      VARCHAR2(100),
                    email_address  VARCHAR2(150),
                    job_title      VARCHAR2(100),
                    hire_date      DATE,
                    salary         NUMBER(10,2),
                    department_id  NUMBER,
                    CONSTRAINT syn_employees_pk PRIMARY KEY (employee_id),
                    CONSTRAINT syn_employees_department_fk
                        FOREIGN KEY (department_id)
                        REFERENCES SYN_DEPARTMENTS(department_id)
                )
                """);
        statement.execute("COMMENT ON TABLE SYN_DEPARTMENTS IS "
                + "'Fictional departments used for application testing'");
        statement.execute("COMMENT ON TABLE SYN_EMPLOYEES IS "
                + "'Fictional employees used for application testing'");
        statement.execute("COMMENT ON COLUMN SYN_EMPLOYEES.DEPARTMENT_ID IS "
                + "'Must reference an existing department in SYN_DEPARTMENTS'");
        statement.execute("COMMENT ON COLUMN SYN_EMPLOYEES.EMAIL_ADDRESS IS "
                + "'Fictional email address using the example.com domain'");
    }

    private static void dropTableIfExists(Statement statement, String tableName) throws SQLException {
        try {
            statement.execute("DROP TABLE " + tableName + " PURGE");
        } catch (SQLException e) {
            if (e.getErrorCode() != 942) {
                throw e;
            }
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
JAVA
"${JAVAC_CMD}" -cp "target/classes:target/dependency/*" -d samples/out "${SETUP_SOURCE}"

db_schema="$(printf '%s' "${SELECTAI_DB_USER}" | tr '[:lower:]' '[:upper:]')"

# Integration defaults for the javaselectai26ai test database.
IT_PROFILE_NAME="SELECTAIJAVAITPROFILE"
IT_SELECTAI_PROFILE_NAME="SELECTAIJAVAITPROFILE2"
IT_NO_OBJECT_LIST_PROFILE_NAME="SELECTAIJAVAITPROFILE3"
VECTOR_PROFILE_NAME="${SELECTAI_IT_VECTOR_PROFILE_NAME:-${IT_PROFILE_NAME}}"
OBJECT_STORAGE_CREDENTIAL_NAME="OCI_GEN_AI_CRED"
IT_VECTOR_INDEX_NAME="HR_POLICY_INDEX_JAVA_SAMPLE"
IT_CREDENTIAL_DDL_NAME="JAVA_SELECTAI_IT_CRED"
IT_PROVIDER_USERS="AI_TEST1,AI_TEST2"
IT_PROVIDER_ENDPOINT="*.openai.azure.com"
IT_NETWORK_PRIVILEGES="http,connect"
IT_VECTOR_LOCATION="${SELECTAI_IT_VECTOR_LOCATION}"
IT_COMPARTMENT_ID="${SELECTAI_IT_COMPARTMENT_ID}"
IT_FEEDBACK_SQL_ID="${SELECTAI_IT_FEEDBACK_SQL_ID:-}"

export SELECTAI_CREDENTIAL_NAME="${OBJECT_STORAGE_CREDENTIAL_NAME}"
export SELECTAI_PROFILE_NAME="${IT_PROFILE_NAME}"
export SELECTAI_PROFILE_CREDENTIAL_NAME="${OBJECT_STORAGE_CREDENTIAL_NAME}"
export SELECTAI_PROFILE_PROVIDER="oci"
export SELECTAI_PROFILE_REGION="us-chicago-1"
export SELECTAI_PROFILE_OCI_API_FORMAT="GENERIC"
export SELECTAI_PROFILE_OCI_COMPARTMENT_ID="${IT_COMPARTMENT_ID}"
export SELECTAI_PROFILE_OBJECT_LIST_MODE="all"
export SELECTAI_PROFILE_OBJECT_LIST='[{"owner":"SH","name":"customers"},{"owner":"SH","name":"countries"}]'
export SELECTAI_PROFILE_DESCRIPTION="Integration profile created by Java Select AI SDK samples."
export SELECTAI_PROFILE_REPLACE="true"
export SELECTAI_PROFILE_DROP_FORCE="true"
export SELECTAI_PROFILE_PROMPT="how many customers"
export SELECTAI_PROFILE_GENERATE_ACTION="showsql"
export SELECTAI_PROFILE_NAME_PATTERN="${SELECTAI_PROFILE_NAME_PATTERN:-^SELECTAIJAVAITPROFILE.*$}"
export SELECTAI_PROFILE_COMMENTS="true"
export SELECTAI_PROFILE_CONSTRAINTS="true"
export SELECTAI_PROFILE_CASE_SENSITIVE_VALUES="false"
export SELECTAI_PROFILE_MAX_TOKENS="1024"
unset SELECTAI_PROFILE_MODEL

export SELECTAI_SUMMARIZE_CONTENT="Full-time employees receive 24 working days of paid annual leave per calendar year. Annual leave accrues at two days per completed month. Employees may carry forward up to 10 unused days. Employees receive 12 working days of paid sick leave each year."
export SELECTAI_SUMMARIZE_PROMPT="Summarize as a short list."
export SELECTAI_SUMMARIZE_PARAMS='{"min_words":20,"max_words":80,"summary_style":"list","extractiveness_level":"medium"}'

export SELECTAI_TRANSLATE_TEXT="${SELECTAI_TRANSLATE_TEXT:-Employees receive paid annual leave.}"
# Keep source/target language optional so TranslateProfileSample exercises the
# profile.translate(text) path by default. If callers export these variables
# before running the script, the sample uses the target-only or fully explicit
# translate overload.
if [[ -n "${SELECTAI_TRANSLATE_SOURCE_LANGUAGE:-}" ]]; then
  export SELECTAI_TRANSLATE_SOURCE_LANGUAGE
else
  unset SELECTAI_TRANSLATE_SOURCE_LANGUAGE
fi
if [[ -n "${SELECTAI_TRANSLATE_TARGET_LANGUAGE:-}" ]]; then
  export SELECTAI_TRANSLATE_TARGET_LANGUAGE
else
  unset SELECTAI_TRANSLATE_TARGET_LANGUAGE
fi

if [[ -n "${IT_FEEDBACK_SQL_ID}" ]]; then
  export SELECTAI_FEEDBACK_SQL_ID="${IT_FEEDBACK_SQL_ID}"
  unset SELECTAI_FEEDBACK_SQL_TEXT
else
  unset SELECTAI_FEEDBACK_SQL_ID
  export SELECTAI_FEEDBACK_SQL_TEXT="select ai showsql how many customers"
fi
export SELECTAI_FEEDBACK_TYPE="positive"
export SELECTAI_FEEDBACK_CONTENT="Approved by integration sample runner."
export SELECTAI_FEEDBACK_OPERATION="add"

export SELECTAI_SYNTHETIC_OBJECT_NAME="SYN_CUSTOMERS"
export SELECTAI_SYNTHETIC_OWNER_NAME="${db_schema}"
export SELECTAI_SYNTHETIC_RECORD_COUNT="1"
export SELECTAI_SYNTHETIC_USER_PROMPT="Generate one realistic but completely fictional customer. Use example.com email addresses."
export SELECTAI_SYNTHETIC_SAMPLE_ROWS="0"
export SELECTAI_SYNTHETIC_TABLE_STATISTICS="false"
export SELECTAI_SYNTHETIC_PRIORITY="LOW"
export SELECTAI_SYNTHETIC_COMMENTS="true"
unset SELECTAI_SYNTHETIC_OBJECT_NAME_2
unset SELECTAI_SYNTHETIC_OWNER_NAME_2
unset SELECTAI_SYNTHETIC_RECORD_COUNT_2
unset SELECTAI_SYNTHETIC_USER_PROMPT_2

export SELECTAI_CONVERSATION_TITLE="Java SDK Integration Conversation"
export SELECTAI_CONVERSATION_DESCRIPTION="Conversation created by the Java Select AI SDK integration runner."
export SELECTAI_CONVERSATION_RETENTION_DAYS="7"
export SELECTAI_CONVERSATION_LENGTH="20"
export SELECTAI_CONVERSATION_DROP_FORCE="true"
export SELECTAI_SESSION_PROMPT_1="What is the importance of history of science?"
export SELECTAI_SESSION_PROMPT_2="Elaborate more on learning from past mistakes."
export SELECTAI_SESSION_DELETE_ON_CLOSE="true"

export SELECTAI_VECTOR_INDEX_NAME="${IT_VECTOR_INDEX_NAME}"
export SELECTAI_VECTOR_INDEX_LOCATION="${IT_VECTOR_LOCATION}"
export SELECTAI_VECTOR_INDEX_REPLACE="true"
export SELECTAI_VECTOR_INDEX_DESCRIPTION="Integration vector index created by Java Select AI SDK samples."
export SELECTAI_VECTOR_INDEX_STATUS="Enabled"
export SELECTAI_VECTOR_INDEX_WAIT_FOR_COMPLETION="true"
export SELECTAI_VECTOR_INDEX_CHUNK_SIZE="1024"
export SELECTAI_VECTOR_INDEX_CHUNK_OVERLAP="128"
export SELECTAI_VECTOR_INDEX_MATCH_LIMIT="5"
export SELECTAI_VECTOR_INDEX_REFRESH_RATE="1440"
export SELECTAI_VECTOR_INDEX_SIMILARITY_THRESHOLD="0"
export SELECTAI_VECTOR_INDEX_DISTANCE_METRIC="COSINE"
export SELECTAI_VECTOR_DB_PROVIDER="oracle"
export SELECTAI_VECTOR_INDEX_DROP_FORCE="true"
unset SELECTAI_VECTOR_INDEX_ENABLE_SOURCES
unset SELECTAI_VECTOR_INDEX_DIMENSION
unset SELECTAI_VECTOR_INDEX_TABLE_NAME

export SELECTAI_PROVIDER_USERS="${IT_PROVIDER_USERS}"
export SELECTAI_PROVIDER_ENDPOINT="${IT_PROVIDER_ENDPOINT}"
export SELECTAI_NETWORK_USERS="${IT_PROVIDER_USERS}"
export SELECTAI_NETWORK_HOST="${IT_PROVIDER_ENDPOINT}"
export SELECTAI_NETWORK_PRIVILEGES="${IT_NETWORK_PRIVILEGES}"
unset SELECTAI_NETWORK_LOWER_PORT
unset SELECTAI_NETWORK_UPPER_PORT

passed=0
failed=0
skipped=0
LAST_LOG=""
failures=()
skips=()

safe_file_name() {
  printf '%s' "$1" | tr ' /' '__' | tr -cd '[:alnum:]_.-'
}

sample_failed() {
  local log_file="$1"
  grep -Eq 'Exception in thread|Missing required environment variable|(^|\])[[:space:]]*ERROR[[:space:]]|SelectAIException|ORA-[0-9]+' "${log_file}"
}

run_sample() {
  local label="$1"
  local class_name="$2"
  shift 2

  local log_file="${REPORT_DIR}/$(safe_file_name "${label}").log"
  LAST_LOG="${log_file}"

  echo "==> ${label}"
  set +e
  env "$@" "${JAVA_CMD}" -cp "${RUNTIME_CP}" "${class_name}" >"${log_file}" 2>&1
  local rc=$?
  set -e

  if [[ ${rc} -ne 0 ]] || sample_failed "${log_file}"; then
    echo "    FAILED (log: ${log_file})"
    tail -n 80 "${log_file}" | sed 's/^/    | /'
    failed=$((failed + 1))
    failures+=("${label}")
    return 1
  fi

  echo "    OK (log: ${log_file})"
  if [[ "${SELECTAI_IT_PRINT_OUTPUT:-false}" == "true" ]]; then
    sed 's/^/    | /' "${log_file}"
  fi
  passed=$((passed + 1))
  return 0
}

run_setup_sample() {
  local label="$1"
  local class_name="$2"
  shift 2

  local log_file="${REPORT_DIR}/$(safe_file_name "${label}").log"

  echo "==> ${label}"
  set +e
  env "$@" "${JAVA_CMD}" -cp "${RUNTIME_CP}" "${class_name}" >"${log_file}" 2>&1
  local rc=$?
  set -e

  if [[ ${rc} -ne 0 ]]; then
    echo "    SETUP completed with non-zero exit code ${rc}; continuing (log: ${log_file})"
    tail -n 20 "${log_file}" | sed 's/^/    | /'
    return 0
  fi

  echo "    SETUP completed (log: ${log_file})"
}

skip_sample() {
  local label="$1"
  local reason="$2"
  echo "==> ${label}"
  echo "    SKIPPED: ${reason}"
  skipped=$((skipped + 1))
  skips+=("${label}: ${reason}")
}

run_optional_credential_ddl_samples() {
  if [[ "${SELECTAI_RUN_CREDENTIAL_DDL:-false}" != "true" ]]; then
    skip_sample "CreateCredentialSample" "requires valid OCI key material; default run requires only DB connection variables"
    skip_sample "DropCredentialSample" "requires SELECTAI_RUN_CREDENTIAL_DDL=true after CreateCredentialSample"
    return
  fi

  local missing=()
  for var_name in SELECTAI_USER_OCID SELECTAI_TENANCY_OCID SELECTAI_PRIVATE_KEY SELECTAI_FINGERPRINT; do
    if [[ -z "${!var_name:-}" ]]; then
      missing+=("${var_name}")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    skip_sample "CreateCredentialSample" "missing ${missing[*]}"
    skip_sample "DropCredentialSample" "missing ${missing[*]}"
    return
  fi

  run_sample "CreateCredentialSample" \
    "com.oracle.database.selectai.samples.credential.CreateCredentialSample" \
    "SELECTAI_CREDENTIAL_NAME=${IT_CREDENTIAL_DDL_NAME}" || true
  run_sample "DropCredentialSample" \
    "com.oracle.database.selectai.samples.credential.DropCredentialSample" \
    "SELECTAI_CREDENTIAL_NAME=${IT_CREDENTIAL_DDL_NAME}" || true
}

echo
echo "Running integration samples..."
echo

run_sample "CreateCredentialObjectSample" "com.oracle.database.selectai.samples.selectai.CreateCredentialObjectSample" || true
run_optional_credential_ddl_samples

run_sample "ConfigureConversationSample" "com.oracle.database.selectai.samples.selectai.ConfigureConversationSample" || true
run_sample "ConfigureVectorIndexSample" "com.oracle.database.selectai.samples.selectai.ConfigureVectorIndexSample" "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true

run_sample "GrantPrivilegesSample" "com.oracle.database.selectai.samples.databaseadmin.GrantPrivilegesSample" || true
run_sample "GrantHttpAccessSample" "com.oracle.database.selectai.samples.databaseadmin.GrantHttpAccessSample" || true
run_sample "RevokeHttpAccessSample" "com.oracle.database.selectai.samples.databaseadmin.RevokeHttpAccessSample" || true
run_sample "GrantNetworkAccessSample" "com.oracle.database.selectai.samples.databaseadmin.GrantNetworkAccessSample" || true
run_sample "RevokeNetworkAccessSample" "com.oracle.database.selectai.samples.databaseadmin.RevokeNetworkAccessSample" || true
run_sample "RevokePrivilegesSample" "com.oracle.database.selectai.samples.databaseadmin.RevokePrivilegesSample" || true

run_sample "EnableSelectAIDataAccessSample" "com.oracle.database.selectai.samples.databaseadmin.EnableSelectAIDataAccessSample" || true

run_setup_sample "Setup-DropProfileSample" \
  "com.oracle.database.selectai.samples.profile.DropProfileSample" \
  "SELECTAI_PROFILE_NAME=${IT_PROFILE_NAME}" || true
run_setup_sample "Setup-DropProfileSample-NoObjectList" \
  "com.oracle.database.selectai.samples.profile.DropProfileSample" \
  "SELECTAI_PROFILE_NAME=${IT_NO_OBJECT_LIST_PROFILE_NAME}" || true
run_setup_sample "Setup-DropProfileSample-SelectAI" \
  "com.oracle.database.selectai.samples.profile.DropProfileSample" \
  "SELECTAI_PROFILE_NAME=${IT_SELECTAI_PROFILE_NAME}" || true

run_sample "CreateProfileSample" "com.oracle.database.selectai.samples.profile.CreateProfileSample" || true
run_sample "CreateProfileSample-NoObjectList" \
  "com.oracle.database.selectai.samples.profile.CreateProfileSample" \
  "SELECTAI_PROFILE_NAME=${IT_NO_OBJECT_LIST_PROFILE_NAME}" \
  "SELECTAI_PROFILE_OBJECT_LIST=" \
  "SELECTAI_PROFILE_OBJECT_LIST_MODE=all" || true
run_sample "CreateProfileWithSelectAISample" \
  "com.oracle.database.selectai.samples.selectai.CreateProfileWithSelectAISample" \
  "SELECTAI_PROFILE_NAME=${IT_SELECTAI_PROFILE_NAME}" || true
run_sample "OpenProfileSample" "com.oracle.database.selectai.samples.selectai.OpenProfileSample" || true
run_sample "ListProfilesSample" "com.oracle.database.selectai.samples.profile.ListProfilesSample" || true
run_sample "ListProfilesByPatternSample" "com.oracle.database.selectai.samples.profile.ListProfilesByPatternSample" || true
run_sample "ListProfilesWithDataSourceSample" "com.oracle.database.selectai.samples.datasource.ListProfilesWithDataSourceSample" || true
run_sample "GetProfileNameSample" "com.oracle.database.selectai.samples.profile.GetProfileNameSample" || true
run_sample "GetProfileStatusSample" "com.oracle.database.selectai.samples.profile.GetProfileStatusSample" || true
run_sample "GetProfileDescriptionSample" "com.oracle.database.selectai.samples.profile.GetProfileDescriptionSample" || true
run_sample "GetProfileAttributesSample" "com.oracle.database.selectai.samples.profile.GetProfileAttributesSample" || true
run_sample "DisableProfileSample-PrepareForEnable" "com.oracle.database.selectai.samples.profile.DisableProfileSample" || true
run_sample "EnableProfileSample" "com.oracle.database.selectai.samples.profile.EnableProfileSample" || true
run_sample "SetProfileAttributesSample" "com.oracle.database.selectai.samples.profile.SetProfileAttributesSample" || true
run_sample "SetProfileStringAttributeSample" \
  "com.oracle.database.selectai.samples.profile.SetProfileStringAttributeSample" \
  "SELECTAI_PROFILE_ATTRIBUTE_NAME=target_language" \
  "SELECTAI_PROFILE_ATTRIBUTE_VALUE=French" || true
run_sample "SetProfileBooleanAttributeSample" \
  "com.oracle.database.selectai.samples.profile.SetProfileBooleanAttributeSample" \
  "SELECTAI_PROFILE_ATTRIBUTE_NAME=comments" \
  "SELECTAI_PROFILE_ATTRIBUTE_VALUE=true" || true
run_sample "SetProfileIntegerAttributeSample" \
  "com.oracle.database.selectai.samples.profile.SetProfileIntegerAttributeSample" \
  "SELECTAI_PROFILE_ATTRIBUTE_NAME=max_tokens" \
  "SELECTAI_PROFILE_ATTRIBUTE_VALUE=1024" || true
run_sample "SetProfileFloatAttributeSample" \
  "com.oracle.database.selectai.samples.profile.SetProfileFloatAttributeSample" \
  "SELECTAI_PROFILE_ATTRIBUTE_NAME=temperature" \
  "SELECTAI_PROFILE_ATTRIBUTE_VALUE=0.1" || true

run_sample "GenerateProfileSample" "com.oracle.database.selectai.samples.profile.GenerateProfileSample" || true
run_sample "RunSqlProfileSample" "com.oracle.database.selectai.samples.profile.RunSqlProfileSample" || true
run_sample "ShowPromptProfileSample" "com.oracle.database.selectai.samples.profile.ShowPromptProfileSample" || true
run_sample "NarrateProfileSample" "com.oracle.database.selectai.samples.profile.NarrateProfileSample" || true
run_sample "ExplainSqlProfileSample" "com.oracle.database.selectai.samples.profile.ExplainSqlProfileSample" || true
run_sample "ShowSqlProfileSample" "com.oracle.database.selectai.samples.profile.ShowSqlProfileSample" || true
run_sample "ChatProfileSample" "com.oracle.database.selectai.samples.profile.ChatProfileSample" \
  "SELECTAI_PROFILE_PROMPT=Answer in one short sentence: what is Select AI?" || true
run_sample "ChatSessionProfileSample" "com.oracle.database.selectai.samples.profile.ChatSessionProfileSample" \
  "SELECTAI_CONVERSATION_TITLE=Java SDK Integration Chat Session" \
  "SELECTAI_CONVERSATION_DESCRIPTION=Conversation created by the chat session integration sample." || true
run_sample "SummarizeProfileSample" "com.oracle.database.selectai.samples.profile.SummarizeProfileSample" || true
run_sample "TranslateProfileSample" "com.oracle.database.selectai.samples.profile.TranslateProfileSample" || true
run_sample "SubmitFeedbackProfileSample" "com.oracle.database.selectai.samples.profile.SubmitFeedbackProfileSample" \
  "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true
run_setup_sample "Setup-SyntheticDataTables" \
  "IntegrationDatabaseSetup" || true
run_sample "GenerateSyntheticDataSingleRequestSample" \
  "com.oracle.database.selectai.samples.profile.GenerateSyntheticDataSingleRequestSample" \
  "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true
run_sample "GenerateSyntheticDataBatchRequestSample" \
  "com.oracle.database.selectai.samples.profile.GenerateSyntheticDataBatchRequestSample" \
  "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true

run_sample "CreateConversationSample" "com.oracle.database.selectai.samples.conversation.CreateConversationSample" || true
if [[ -f "${LAST_LOG}" ]]; then
  created_conversation_id="$(sed -n 's/^Created conversation ID: //p' "${LAST_LOG}" | tail -n 1 | tr -d '\r')"
  if [[ -n "${created_conversation_id}" ]]; then
    export SELECTAI_CONVERSATION_ID="${created_conversation_id}"
    echo "Captured conversation ID: ${SELECTAI_CONVERSATION_ID}"
  else
    failed=$((failed + 1))
    failures+=("Capture conversation ID")
    echo "Failed to capture conversation ID from ${LAST_LOG}"
  fi
fi

if [[ -n "${SELECTAI_CONVERSATION_ID:-}" ]]; then
  run_sample "OpenConversationSample" "com.oracle.database.selectai.samples.selectai.OpenConversationSample" || true
  run_sample "ListConversationsSample" "com.oracle.database.selectai.samples.conversation.ListConversationsSample" || true
  run_sample "GetConversationIdSample" "com.oracle.database.selectai.samples.conversation.GetConversationIdSample" || true
  run_sample "GetConversationAttributesSample" "com.oracle.database.selectai.samples.conversation.GetConversationAttributesSample" || true
  run_sample "ListConversationPromptsSample" "com.oracle.database.selectai.samples.conversation.ListConversationPromptsSample" || true
  if [[ -n "${SELECTAI_CONVERSATION_PROMPT_ID:-}" ]]; then
    run_sample "DeleteConversationPromptSample" "com.oracle.database.selectai.samples.conversation.DeleteConversationPromptSample" || true
  else
    skip_sample "DeleteConversationPromptSample" "requires SELECTAI_CONVERSATION_PROMPT_ID"
  fi
  run_sample "SetConversationAttributesSample" "com.oracle.database.selectai.samples.conversation.SetConversationAttributesSample" \
    "SELECTAI_CONVERSATION_TITLE=Java SDK Integration Conversation Updated" \
    "SELECTAI_CONVERSATION_DESCRIPTION=Conversation updated by the integration runner." || true
  run_sample "DropConversationSample" "com.oracle.database.selectai.samples.conversation.DropConversationSample" || true
else
  skip_sample "Conversation dependent samples" "CreateConversationSample did not produce a conversation ID"
fi

run_sample "ConfigureVectorIndexSample-ForCreate" "com.oracle.database.selectai.samples.selectai.ConfigureVectorIndexSample" "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true
if run_sample "CreateVectorIndexSample" "com.oracle.database.selectai.samples.vectorindex.CreateVectorIndexSample" "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}"; then
  run_sample "OpenVectorIndexSample" "com.oracle.database.selectai.samples.selectai.OpenVectorIndexSample" || true
  run_sample "ListVectorIndexesSample" "com.oracle.database.selectai.samples.vectorindex.ListVectorIndexesSample" || true
  run_sample "GetVectorIndexNameSample" "com.oracle.database.selectai.samples.vectorindex.GetVectorIndexNameSample" || true
  run_sample "GetVectorIndexDescriptionSample" "com.oracle.database.selectai.samples.vectorindex.GetVectorIndexDescriptionSample" || true
  run_sample "GetVectorIndexAttributesSample" "com.oracle.database.selectai.samples.vectorindex.GetVectorIndexAttributesSample" "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true
  run_sample "GetVectorIndexStatusSample" "com.oracle.database.selectai.samples.vectorindex.GetVectorIndexStatusSample" "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true
  run_sample "IsVectorIndexWaitForCompletionSample" "com.oracle.database.selectai.samples.vectorindex.IsVectorIndexWaitForCompletionSample" "SELECTAI_PROFILE_NAME=${VECTOR_PROFILE_NAME}" || true
  run_sample "DisableVectorIndexSample-PrepareForEnable" "com.oracle.database.selectai.samples.vectorindex.DisableVectorIndexSample" || true
  run_sample "EnableVectorIndexSample" "com.oracle.database.selectai.samples.vectorindex.EnableVectorIndexSample" || true
  run_sample "UpdateVectorIndexAttributesSample" "com.oracle.database.selectai.samples.vectorindex.UpdateVectorIndexAttributesSample" \
    "SELECTAI_VECTOR_INDEX_MATCH_LIMIT=6" \
    "SELECTAI_VECTOR_INDEX_REFRESH_RATE=720" \
    "SELECTAI_VECTOR_INDEX_SIMILARITY_THRESHOLD=0" || true
  run_sample "UpdateVectorIndexAttributeSample" "com.oracle.database.selectai.samples.vectorindex.UpdateVectorIndexAttributeSample" \
    "SELECTAI_VECTOR_INDEX_ATTRIBUTE_NAME=match_limit" \
    "SELECTAI_VECTOR_INDEX_ATTRIBUTE_VALUE=5" || true
  run_sample "UpdateVectorIndexAttributeWithClobSample" "com.oracle.database.selectai.samples.vectorindex.UpdateVectorIndexAttributeWithClobSample" \
    "SELECTAI_VECTOR_INDEX_ATTRIBUTE_NAME=match_limit" \
    "SELECTAI_VECTOR_INDEX_ATTRIBUTE_VALUE=7" \
    "SELECTAI_VECTOR_INDEX_ATTRIBUTE_USE_CLOB=true" || true
  run_sample "DisableVectorIndexSample" "com.oracle.database.selectai.samples.vectorindex.DisableVectorIndexSample" || true
  run_sample "DropVectorIndexSample" "com.oracle.database.selectai.samples.vectorindex.DropVectorIndexSample" || true
else
  skip_sample "Vector index dependent samples" "CreateVectorIndexSample did not create ${SELECTAI_VECTOR_INDEX_NAME}"
fi

run_sample "DisableProfileSample" "com.oracle.database.selectai.samples.profile.DisableProfileSample" || true
run_sample "EnableProfileSample-RestoreBeforeDrop" "com.oracle.database.selectai.samples.profile.EnableProfileSample" || true
run_sample "DropProfileSample" "com.oracle.database.selectai.samples.profile.DropProfileSample" || true
run_sample "DropProfileSample-NoObjectList" \
  "com.oracle.database.selectai.samples.profile.DropProfileSample" \
  "SELECTAI_PROFILE_NAME=${IT_NO_OBJECT_LIST_PROFILE_NAME}" || true
run_sample "DropProfileSample-SelectAI" \
  "com.oracle.database.selectai.samples.profile.DropProfileSample" \
  "SELECTAI_PROFILE_NAME=${IT_SELECTAI_PROFILE_NAME}" || true

run_sample "DisableSelectAIDataAccessSample" "com.oracle.database.selectai.samples.databaseadmin.DisableSelectAIDataAccessSample" || true
run_sample "EnableSelectAIDataAccessSample-Restore" "com.oracle.database.selectai.samples.databaseadmin.EnableSelectAIDataAccessSample" || true

echo
echo "Integration sample summary"
echo "  Passed : ${passed}"
echo "  Failed : ${failed}"
echo "  Skipped: ${skipped}"
echo "  Logs   : ${REPORT_DIR}"

if [[ ${#skips[@]} -gt 0 ]]; then
  echo
  echo "Skipped samples:"
  for item in "${skips[@]}"; do
    echo "  - ${item}"
  done
fi

if [[ ${#failures[@]} -gt 0 ]]; then
  echo
  echo "Failed samples:"
  for item in "${failures[@]}"; do
    echo "  - ${item}"
  done
  exit 1
fi

exit 0
