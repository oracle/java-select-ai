#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/run-all-samples-integration.sh [options] <db-user> <db-password> <jdbc-url>
  scripts/run-all-samples-integration.sh [options]

Runs the Select AI samples, then the Select AI Agent samples. Each runner
reports and cleans up its own resources. Both suites run even when one fails.

Options:
  --mvn <file>     Maven executable path.
  --java <file>    Java executable path.
  --javac <file>   Javac executable path.
  -h, --help       Show this help.

Required environment when database arguments are not supplied:
  SELECTAI_DB_USER, SELECTAI_DB_PASSWORD, SELECTAI_JDBC_URL

Also required for the Select AI samples:
  SELECTAI_IT_VECTOR_LOCATION, SELECTAI_IT_COMPARTMENT_ID

Both suites create and drop their own OCI credentials. Set SELECTAI_USER_OCID,
SELECTAI_TENANCY_OCID, SELECTAI_PRIVATE_KEY, and SELECTAI_FINGERPRINT. Optionally
set SELECTAI_PROFILE_CREDENTIAL_NAME to name each run-owned credential; it must
not already exist. The agent region defaults to us-chicago-1 and its compartment
to SELECTAI_IT_COMPARTMENT_ID. SH.CUSTOMERS and SH.COUNTRIES must be visible to
SELECTAI_DB_USER. See each runner's --help for other prerequisites.

Agent runner options are inherited from the environment, including:
  SELECTAI_AGENT_RUN_WORKFLOW_SAMPLE=true   Run the end-to-end conversation sample.
  SELECTAI_AGENT_EXTRA_TOOL_SAMPLES         Space-separated rag websearch slack email.
  SELECTAI_AGENT_RUN_TOOL_INPUT_SAMPLES=true
                                          Run typed input-metadata samples.
  SELECTAI_AGENT_SHARED_TEAM_GRANTEE        Run owner-managed team-sharing samples.
Service-specific tools and input metadata require additional configuration;
see scripts/run-ai-agent-samples-integration.sh --help. Referenced service
credentials, RAG profiles, vector indexes, and PL/SQL functions are not deleted.
USAGE
}

positional_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mvn|--java|--javac)
      if [[ $# -lt 2 ]]; then
        printf '%s requires an executable path\n' "$1" >&2
        exit 2
      fi
      case "$1" in
        --mvn) export MVN="$2" ;;
        --java) export JAVA="$2" ;;
        --javac) export JAVAC="$2" ;;
      esac
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      while [[ $# -gt 0 ]]; do
        positional_args+=("$1")
        shift
      done
      ;;
    -*)
      printf 'Unknown option: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
    *)
      positional_args+=("$1")
      shift
      ;;
  esac
done

if [[ ${#positional_args[@]} -ne 0 && ${#positional_args[@]} -ne 3 ]]; then
  usage >&2
  exit 2
fi
if [[ ${#positional_args[@]} -eq 3 ]]; then
  export SELECTAI_DB_USER="${positional_args[0]}"
  export SELECTAI_DB_PASSWORD="${positional_args[1]}"
  export SELECTAI_JDBC_URL="${positional_args[2]}"
fi

for name in SELECTAI_DB_USER SELECTAI_DB_PASSWORD SELECTAI_JDBC_URL \
            SELECTAI_IT_VECTOR_LOCATION SELECTAI_IT_COMPARTMENT_ID \
            SELECTAI_USER_OCID SELECTAI_TENANCY_OCID \
            SELECTAI_PRIVATE_KEY SELECTAI_FINGERPRINT; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Missing required environment variable: %s\n' "$name" >&2
    exit 2
  fi
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

printf '%s\n' 'Running Select AI samples...'
ai_status=0
if "$script_dir/run-ai-samples-integration.sh"; then
  printf '%s\n' 'Select AI samples passed.'
else
  ai_status=$?
  printf 'Select AI samples failed (exit %s).\n' "$ai_status" >&2
fi

if [[ "$ai_status" -eq 130 || "$ai_status" -eq 143 ]]; then
  exit "$ai_status"
fi

printf '%s\n' 'Running Select AI Agent samples...'
agent_status=0
if SELECTAI_PROFILE_PROVIDER=oci \
   SELECTAI_PROFILE_REGION="${SELECTAI_PROFILE_REGION:-us-chicago-1}" \
   SELECTAI_PROFILE_OCI_COMPARTMENT_ID="${SELECTAI_PROFILE_OCI_COMPARTMENT_ID:-$SELECTAI_IT_COMPARTMENT_ID}" \
   "$script_dir/run-ai-agent-samples-integration.sh"; then
  printf '%s\n' 'Select AI Agent samples passed.'
else
  agent_status=$?
  printf 'Select AI Agent samples failed (exit %s).\n' "$agent_status" >&2
fi

printf '\nCombined integration result: AI=%s, AI Agent=%s\n' \
  "$ai_status" "$agent_status"
if [[ "$ai_status" -ne 0 || "$agent_status" -ne 0 ]]; then
  exit 1
fi
exit 0
