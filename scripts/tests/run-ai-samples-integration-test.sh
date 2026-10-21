#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/

set -euo pipefail

if [[ "${1:-}" == "-cp" ]]; then
  sample="${3##*.}"
  printf '%s\n' "$sample" >> "$SELECTAI_TEST_CALLS"
  if [[ "$sample" == "${SELECTAI_TEST_FAIL_SAMPLE:-}" ]]; then
    echo 'ERROR: forced sample failure' >&2
    exit 1
  fi
  case "$sample" in
    CreateCredentialSample|DropCredentialSample)
      [[ "$SELECTAI_CREDENTIAL_NAME" == "$SELECTAI_PROFILE_CREDENTIAL_NAME" ]]
      ;;
    CreateProfileSample)
      [[ "$SELECTAI_PROFILE_CREDENTIAL_NAME" == "$SELECTAI_TEST_CREDENTIAL" ]]
      ;;
  esac
  if [[ "$sample" == CreateConversationSample ]]; then
    echo 'Created conversation ID: TEST-CONVERSATION-ID'
  else
    echo 'sample result'
  fi
  exit 0
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
runner="${script_dir}/../run-ai-samples-integration.sh"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/project/scripts" "$test_dir/project/samples/src/main/java"
cp "$runner" "$test_dir/project/scripts/run-ai-samples-integration.sh"

run_case() {
  local name="$1" fail_sample="$2" expected_status="$3"
  local calls="$test_dir/$name.calls" output="$test_dir/$name.out" status=0
  : > "$calls"
  env -i PATH="$PATH" \
    SELECTAI_DB_USER=test SELECTAI_DB_PASSWORD=test \
    SELECTAI_JDBC_URL=jdbc:oracle:thin:@example \
    SELECTAI_IT_VECTOR_LOCATION=example SELECTAI_IT_COMPARTMENT_ID=compartment \
    SELECTAI_USER_OCID=user SELECTAI_TENANCY_OCID=tenancy \
    SELECTAI_PRIVATE_KEY=key SELECTAI_FINGERPRINT=fingerprint \
    SELECTAI_PROFILE_CREDENTIAL_NAME=NEW_CREDENTIAL \
    SELECTAI_TEST_CREDENTIAL=NEW_CREDENTIAL \
    SELECTAI_TEST_CALLS="$calls" SELECTAI_TEST_FAIL_SAMPLE="$fail_sample" \
    MVN=/usr/bin/true JAVAC=/usr/bin/true JAVA="$script_dir/run-ai-samples-integration-test.sh" \
    bash "$test_dir/project/scripts/run-ai-samples-integration.sh" > "$output" 2>&1 || status=$?
  if [[ "$status" -ne "$expected_status" ]]; then
    cat "$output" >&2
    printf 'Unexpected exit code for %s: %s\n' "$name" "$status" >&2
    exit 1
  fi
}

run_case success '' 0
[[ "$(grep -Fc CreateCredentialSample "$test_dir/success.calls")" -eq 1 ]]
[[ "$(grep -Fc DropCredentialSample "$test_dir/success.calls")" -eq 1 ]]
[[ "$(tail -n 1 "$test_dir/success.calls")" == DropCredentialSample ]]

run_case profile_failure CreateProfileSample 1
[[ "$(tail -n 1 "$test_dir/profile_failure.calls")" == DropCredentialSample ]]

run_case create_failure CreateCredentialSample 1
if grep -Eq 'CreateProfileSample|DropCredentialSample' "$test_dir/create_failure.calls"; then
  echo 'Used or dropped a credential that was not created' >&2
  exit 1
fi

echo 'Select AI sample runner tests passed.'
