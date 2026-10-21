#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/

set -euo pipefail

case "${0##*/}" in
  run-ai-samples-integration.sh)
    printf 'ai\n' >> "$SELECTAI_TEST_CALLS"
    [[ "$SELECTAI_DB_USER" == "$SELECTAI_TEST_DB_USER" ]]
    [[ "$SELECTAI_DB_PASSWORD" == "$SELECTAI_TEST_DB_PASSWORD" ]]
    [[ "$SELECTAI_JDBC_URL" == "$SELECTAI_TEST_JDBC_URL" ]]
    if [[ -n "${SELECTAI_TEST_MVN:-}" ]]; then
      [[ "$MVN" == "$SELECTAI_TEST_MVN" ]]
      [[ "$JAVA" == "$SELECTAI_TEST_JAVA" ]]
      [[ "$JAVAC" == "$SELECTAI_TEST_JAVAC" ]]
    fi
    exit "${SELECTAI_TEST_AI_STATUS:-0}"
    ;;
  run-ai-agent-samples-integration.sh)
    printf 'agent\n' >> "$SELECTAI_TEST_CALLS"
    [[ "$SELECTAI_DB_USER" == "$SELECTAI_TEST_DB_USER" ]]
    [[ "$SELECTAI_PROFILE_PROVIDER" == oci ]]
    [[ "${SELECTAI_PROFILE_CREDENTIAL_NAME:-}" == "$SELECTAI_TEST_CREDENTIAL" ]]
    [[ "$SELECTAI_PROFILE_OCI_COMPARTMENT_ID" == "$SELECTAI_TEST_COMPARTMENT" ]]
    [[ "$SELECTAI_PROFILE_REGION" == "$SELECTAI_TEST_REGION" ]]
    if [[ "${SELECTAI_AGENT_RUN_TOOL_INPUT_SAMPLES:-false}" == true ]]; then
      [[ "$SELECTAI_AGENT_TOOL_FUNCTION" == APP.TEST_INPUT_FN ]]
      [[ "$SELECTAI_AGENT_TOOL_INSTRUCTION" == 'Use the configured input.' ]]
      [[ "$SELECTAI_AGENT_TOOL_INPUT_NAME" == data_guard ]]
      [[ "$SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION" == 'Only "Enabled" or "Disabled".' ]]
    fi
    if [[ -n "${SELECTAI_TEST_MVN:-}" ]]; then
      [[ "$MVN" == "$SELECTAI_TEST_MVN" ]]
      [[ "$JAVA" == "$SELECTAI_TEST_JAVA" ]]
      [[ "$JAVAC" == "$SELECTAI_TEST_JAVAC" ]]
    fi
    exit "${SELECTAI_TEST_AGENT_STATUS:-0}"
    ;;
esac

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runner="${script_dir}/../run-all-samples-integration.sh"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
cp "$runner" "$test_dir/run-all-samples-integration.sh"
ln -s "$script_dir/run-all-samples-integration-test.sh" \
  "$test_dir/run-ai-samples-integration.sh"
ln -s "$script_dir/run-all-samples-integration-test.sh" \
  "$test_dir/run-ai-agent-samples-integration.sh"

run_case() {
  local name="$1" ai_status="$2" agent_status="$3" expected_status="$4"
  local status=0 calls="$test_dir/$name.calls" output="$test_dir/$name.out"
  : > "$calls"
  env -i PATH="$PATH" \
    SELECTAI_DB_USER=db-user SELECTAI_DB_PASSWORD=db-password \
    SELECTAI_JDBC_URL=jdbc:oracle:thin:@example \
    SELECTAI_IT_VECTOR_LOCATION=object-storage-location \
    SELECTAI_IT_COMPARTMENT_ID=compartment-from-ai \
    SELECTAI_TEST_DB_USER=db-user SELECTAI_TEST_DB_PASSWORD=db-password \
    SELECTAI_TEST_JDBC_URL=jdbc:oracle:thin:@example \
    SELECTAI_USER_OCID=user SELECTAI_TENANCY_OCID=tenancy \
    SELECTAI_PRIVATE_KEY=key SELECTAI_FINGERPRINT=fingerprint \
    SELECTAI_TEST_CREDENTIAL='' \
    SELECTAI_TEST_COMPARTMENT=compartment-from-ai \
    SELECTAI_TEST_REGION=us-chicago-1 \
    SELECTAI_TEST_CALLS="$calls" \
    SELECTAI_TEST_AI_STATUS="$ai_status" \
    SELECTAI_TEST_AGENT_STATUS="$agent_status" \
    bash "$test_dir/run-all-samples-integration.sh" > "$output" 2>&1 || status=$?
  if [[ "$status" -ne "$expected_status" ]]; then
    cat "$output" >&2
    printf 'Unexpected exit code for %s: %s\n' "$name" "$status" >&2
    exit 1
  fi
  printf 'ai\nagent\n' | diff - "$calls"
}

run_case success 0 0 0
run_case ai_failure 4 0 1
run_case agent_failure 0 5 1
run_case both_failure 4 5 1

calls="$test_dir/overrides.calls"
: > "$calls"
env -i PATH="$PATH" \
  SELECTAI_IT_VECTOR_LOCATION=object-storage-location \
  SELECTAI_IT_COMPARTMENT_ID=compartment-from-ai \
  SELECTAI_PROFILE_CREDENTIAL_NAME=OTHER_CRED \
  SELECTAI_PROFILE_OCI_COMPARTMENT_ID=agent-compartment \
  SELECTAI_PROFILE_REGION=eu-frankfurt-1 \
  SELECTAI_AGENT_RUN_TOOL_INPUT_SAMPLES=true \
  SELECTAI_AGENT_TOOL_FUNCTION=APP.TEST_INPUT_FN \
  SELECTAI_AGENT_TOOL_INSTRUCTION='Use the configured input.' \
  SELECTAI_AGENT_TOOL_INPUT_NAME=data_guard \
  SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION='Only "Enabled" or "Disabled".' \
  SELECTAI_USER_OCID=user SELECTAI_TENANCY_OCID=tenancy \
  SELECTAI_PRIVATE_KEY=key SELECTAI_FINGERPRINT=fingerprint \
  SELECTAI_TEST_DB_USER=cli-user SELECTAI_TEST_DB_PASSWORD=cli-password \
  SELECTAI_TEST_JDBC_URL=jdbc:oracle:thin:@cli \
  SELECTAI_TEST_CREDENTIAL=OTHER_CRED \
  SELECTAI_TEST_COMPARTMENT=agent-compartment \
  SELECTAI_TEST_REGION=eu-frankfurt-1 \
  SELECTAI_TEST_MVN=custom-mvn \
  SELECTAI_TEST_JAVA=custom-java \
  SELECTAI_TEST_JAVAC=custom-javac \
  SELECTAI_TEST_CALLS="$calls" \
  bash "$test_dir/run-all-samples-integration.sh" \
    --mvn custom-mvn --java custom-java --javac custom-javac \
    cli-user cli-password jdbc:oracle:thin:@cli > "$test_dir/overrides.out" 2>&1
printf 'ai\nagent\n' | diff - "$calls"

env -i PATH="$PATH" bash "$test_dir/run-all-samples-integration.sh" --help \
  > "$test_dir/help.out"
grep -Fq 'Runs the Select AI samples, then the Select AI Agent samples.' \
  "$test_dir/help.out"

echo 'Combined sample runner tests passed.'
