#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/

set -euo pipefail

# The runner invokes this file as a fake java executable in the test cases.
if [[ "${1:-}" == "-cp" ]]; then
  sample="${3##*.}"
  if [[ "${SELECTAI_AGENT_TOOL_NAME:-}" == *_INP ]]; then
    case "$sample" in
      LoadToolSample) sample=LoadToolWithInputsSample ;;
      DropToolSample) sample=DropInputToolSample ;;
    esac
  fi
  printf '%s\n' "$sample" >> "$SELECTAI_AGENT_TEST_CALLS"
  if [[ "$sample" == "${SELECTAI_AGENT_TEST_FAIL_SAMPLE:-}" ]]; then
    echo "${SELECTAI_AGENT_TEST_FAIL_MESSAGE:-ERROR: forced sample failure}" >&2
    exit "${SELECTAI_AGENT_TEST_FAIL_EXIT:-1}"
  fi
  case "$sample" in
    CreateCredentialSample)
      [[ "$SELECTAI_CREDENTIAL_NAME" == "$SELECTAI_PROFILE_CREDENTIAL_NAME" ]]
      echo "Create credential '${SELECTAI_CREDENTIAL_NAME}' completed: true"
      ;;
    CreateProfileSample)
      [[ "$SELECTAI_CREDENTIAL_NAME" == "$SELECTAI_PROFILE_CREDENTIAL_NAME" ]]
      [[ "$SELECTAI_PROFILE_OBJECT_LIST_MODE" == all ]]
      [[ "$SELECTAI_PROFILE_OBJECT_LIST" == '[{"owner":"SH","name":"customers"},{"owner":"SH","name":"countries"}]' ]]
      echo "Create profile '${SELECTAI_PROFILE_NAME}' completed: true"
      ;;
    ToolParamsSample) echo '{"tool_params":{"profile_name":"SAMPLE_PROFILE"}}' ;;
    ToolInputSample) echo '{"tool_inputs":[{"name":"data_guard"}]}' ;;
    RunTeamParamsSample) echo 'Precise runtime parameters: {"state_variables":{"amount":1.234567890123456789}}' ;;
    AgentWorkflowSample)
      [[ "$SELECTAI_AGENT_WORKFLOW_TEAM_NAME" == *_WTEM ]]
      echo 'First team response: First'
      echo 'Continued team response: Continued'
      if [[ "${SELECTAI_AGENT_TEST_OMIT_CLEANUP:-false}" != true ]]; then
        echo 'Workflow cleanup completed.'
      fi
      ;;
    CreateSqlToolSample|SetSqlToolParamsSample)
      [[ "$SELECTAI_AGENT_TOOL_NAME" == *_TOL ]]
      [[ "$SELECTAI_AGENT_TOOL_TYPE" == SQL ]]
      [[ -z "${SELECTAI_AGENT_TOOL_FUNCTION:-}" ]]
      [[ -z "${SELECTAI_AGENT_TOOL_INPUT_NAME:-}" ]]
      [[ -z "${SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION:-}" ]]
      [[ -z "${SELECTAI_AGENT_TOOL_PARAMS_JSON:-}" ]]
      if [[ "$sample" == CreateSqlToolSample ]]; then
        echo "Create tool '${SELECTAI_AGENT_TOOL_NAME}' completed: true"
      else
        echo "Set parameters on SQL tool '${SELECTAI_AGENT_TOOL_NAME}' completed: true"
      fi
      ;;
    CreateToolWithInputsSample|SetToolInputsSample|LoadToolWithInputsSample)
      [[ "$SELECTAI_AGENT_TOOL_NAME" == *_INP ]]
      [[ "$SELECTAI_AGENT_TOOL_FUNCTION" == APP.TEST_INPUT_FN ]]
      [[ "$SELECTAI_AGENT_TOOL_INSTRUCTION" == 'Use the configured input.' ]]
      [[ "$SELECTAI_AGENT_TOOL_INPUT_NAME" == data_guard ]]
      [[ "$SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION" == 'Only "Enabled" or "Disabled".' ]]
      [[ -z "${SELECTAI_AGENT_TOOL_TYPE:-}" ]]
      [[ -z "${SELECTAI_AGENT_TOOL_PARAMS_JSON:-}" ]]
      case "$sample" in
        CreateToolWithInputsSample) echo "Create tool '${SELECTAI_AGENT_TOOL_NAME}' completed: true" ;;
        SetToolInputsSample) echo "Set inputs on tool '${SELECTAI_AGENT_TOOL_NAME}' completed: true" ;;
        LoadToolWithInputsSample) echo 'Tool inputs: 1' ;;
      esac
      ;;
    CreateTaskSample)
      [[ "$SELECTAI_AGENT_TASK_TOOLS" == "$SELECTAI_AGENT_TOOL_NAME" ]]
      echo "Create task '${SELECTAI_AGENT_TASK_NAME}' completed: true"
      ;;
    CreateAgentSample)
      [[ "$SELECTAI_AGENT_TOOLS" == "$SELECTAI_AGENT_TOOL_NAME" ]]
      echo "Create agent '${SELECTAI_AGENT_NAME}' completed: true"
      ;;
    CreateTeamSample) echo "Create team '${SELECTAI_AGENT_TEAM_NAME}' completed: true" ;;
    ListToolsSample) echo "Tool: ${SELECTAI_AGENT_TOOL_NAME}; description: test" ;;
    ListTasksSample) echo "Task: ${SELECTAI_AGENT_TASK_NAME}; description: test" ;;
    ListAgentsSample) echo "Agent: ${SELECTAI_AGENT_NAME}; description: test" ;;
    ListAgentTeamsSample) echo "${SELECTAI_AGENT_TEAM_NAME}: test" ;;
    GrantTeamAccessSample) echo 'Team access grant completed: true' ;;
    RevokeTeamAccessSample) echo 'Team access revoke completed: true' ;;
    GrantTeamHandleAccessSample) echo 'Team handle access grant completed: true' ;;
    RevokeTeamHandleAccessSample) echo 'Team handle access revoke completed: true' ;;
    LoadToolSample) echo "Tool: ${SELECTAI_AGENT_TOOL_NAME}" ;;
    LoadTaskSample) echo "Task: ${SELECTAI_AGENT_TASK_NAME}" ;;
    LoadAgentSample) echo "Agent: ${SELECTAI_AGENT_NAME}" ;;
    LoadTeamSample) echo "Team: ${SELECTAI_AGENT_TEAM_NAME}" ;;
    CreateConversationSample) echo 'Created conversation ID: TEST-CONVERSATION-ID' ;;
    RunTeamSample)
      [[ "$SELECTAI_AGENT_TEAM_CONVERSATION_ID" == 'TEST-CONVERSATION-ID' ]]
      echo 'Team run completed'
      ;;
    GetDefinitionSample) echo 'CREATE_TEAM(...)' ;;
    DropConversationSample)
      [[ "$SELECTAI_CONVERSATION_ID" == 'TEST-CONVERSATION-ID' ]]
      echo "Drop conversation '${SELECTAI_CONVERSATION_ID}' completed: true"
      ;;
    DropTeamSample) echo "Drop team '${SELECTAI_AGENT_TEAM_NAME}' completed: true" ;;
    DropAgentSample) echo "Drop agent '${SELECTAI_AGENT_NAME}' completed: true" ;;
    DropTaskSample) echo "Drop task '${SELECTAI_AGENT_TASK_NAME}' completed: true" ;;
    DropToolSample) echo "Drop tool '${SELECTAI_AGENT_TOOL_NAME}' completed: true" ;;
    DropInputToolSample) echo "Drop tool '${SELECTAI_AGENT_TOOL_NAME}' completed: true" ;;
    DropProfileSample) echo "Drop profile '${SELECTAI_PROFILE_NAME}' completed: true" ;;
    DropCredentialSample) echo "Drop credential '${SELECTAI_CREDENTIAL_NAME}' completed: true" ;;
    *) echo 'sample result' ;;
  esac
  exit 0
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/../.." && pwd)"
test_script="${script_dir}/run-agent-samples-integration-test.sh"
runner="${project_root}/scripts/run-ai-agent-samples-integration.sh"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
: > "${test_dir}/fake.jar"

run_case() {
  local case_name="$1" credential_name="$2" fail_sample="$3" expected_status="$4"
  local fail_message="${5:-}"
  local calls="${test_dir}/${case_name}.calls" output="${test_dir}/${case_name}.out" status=0
  : > "$calls"
  env -i PATH="$PATH" \
    SELECTAI_DB_USER=test SELECTAI_DB_PASSWORD=test \
    SELECTAI_JDBC_URL=jdbc:oracle:thin:@example \
    SELECTAI_PROFILE_REGION=us-chicago-1 \
    SELECTAI_PROFILE_OCI_COMPARTMENT_ID=ocid1.compartment.example \
    SELECTAI_PROFILE_CREDENTIAL_NAME="$credential_name" \
    SELECTAI_USER_OCID=user SELECTAI_TENANCY_OCID=tenancy \
    SELECTAI_PRIVATE_KEY=key SELECTAI_FINGERPRINT=fingerprint \
    SELECTAI_AGENT_TEST_CALLS="$calls" \
    SELECTAI_AGENT_TEST_FAIL_SAMPLE="$fail_sample" \
    SELECTAI_AGENT_TEST_FAIL_MESSAGE="$fail_message" \
    SELECTAI_AGENT_TEST_FAIL_EXIT="${SELECTAI_AGENT_TEST_FAIL_EXIT:-1}" \
    SELECTAI_AGENT_TEST_OMIT_CLEANUP="${SELECTAI_AGENT_TEST_OMIT_CLEANUP:-false}" \
    SELECTAI_AGENT_RUN_WORKFLOW_SAMPLE="${SELECTAI_AGENT_TEST_WORKFLOW:-false}" \
    SELECTAI_AGENT_EXTRA_TOOL_SAMPLES="${SELECTAI_AGENT_TEST_EXTRA_TOOLS:-}" \
    SELECTAI_AGENT_SHARED_TEAM_GRANTEE="${SELECTAI_AGENT_TEST_GRANTEE:-}" \
    SELECTAI_AGENT_RUN_TOOL_INPUT_SAMPLES="${SELECTAI_AGENT_TEST_RUN_INPUTS:-false}" \
    SELECTAI_AGENT_TOOL_FUNCTION="${SELECTAI_AGENT_TEST_INPUT_FUNCTION-APP.TEST_INPUT_FN}" \
    SELECTAI_AGENT_TOOL_INSTRUCTION='Use the configured input.' \
    SELECTAI_AGENT_TOOL_INPUT_NAME=data_guard \
    SELECTAI_AGENT_TOOL_INPUT_DESCRIPTION='Only "Enabled" or "Disabled".' \
    SELECTAI_AGENT_TOOL_PARAMS_JSON='{"profile_name":"SHOULD_NOT_BE_USED"}' \
    MVN=/usr/bin/true JAVAC=/usr/bin/true JAVA="$test_script" \
    SDK_JAR="${test_dir}/fake.jar" \
    bash "$runner" > "$output" 2>&1 || status=$?
  if [[ "$expected_status" == success && "$status" -ne 0 ]] ||
     [[ "$expected_status" == failure && "$status" -eq 0 ]]; then
    cat "$output" >&2
    echo "Unexpected status for ${case_name}: ${status}" >&2
    exit 1
  fi
}

run_case success '' '' success
printf '%s\n' DropConversationSample DropTeamSample DropAgentSample DropTaskSample DropToolSample \
  DropProfileSample DropCredentialSample > "${test_dir}/expected"
tail -n 7 "${test_dir}/success.calls" | diff - "${test_dir}/expected"
grep -Fq 'CreateConversationSample' "${test_dir}/success.calls"
grep -Fq 'RunTeamSample' "${test_dir}/success.calls"
for sample in \
  ToolParamsSample ToolInputSample RunTeamParamsSample CreateSqlToolSample SetSqlToolParamsSample \
  CreateSelectAIAgentWithOptionsSample \
  CreateSelectAIAgentDataSourceSample \
  CreateSelectAIAgentDataSourceWithOptionsSample \
  DisableAgentSample EnableAgentSample \
  DisableTaskSample EnableTaskSample \
  DisableToolSample EnableToolSample \
  DisableTeamSample EnableTeamSample \
  SetAgentAttributeSample SetTaskAttributeSample \
  SetToolAttributeSample SetTeamAttributeSample \
  SetAgentAttributesSample SetTaskAttributesSample \
  SetToolAttributesSample SetTeamAttributesSample \
  ListAvailableTeamsSample \
  DescribeTeamSample \
  GetTeamStateSample GetOwnedTeamStateSample \
  SetVariableSample GetVariableSample; do
  grep -Fq "${sample}" "${test_dir}/success.calls" || {
    echo "Missing latest agent sample invocation: ${sample}" >&2
    exit 1
  }
done

if grep -Eq 'CreateToolWithInputsSample|SetToolInputsSample|DropInputToolSample' "${test_dir}/success.calls"; then
  echo 'Ran database-backed input samples without explicit opt-in' >&2
  exit 1
fi

SELECTAI_AGENT_TEST_RUN_INPUTS=true run_case typed_inputs '' '' success
for sample in CreateToolWithInputsSample SetToolInputsSample LoadToolWithInputsSample DropInputToolSample; do
  grep -Fxq "$sample" "${test_dir}/typed_inputs.calls" || {
    echo "Missing typed-input sample invocation: ${sample}" >&2
    exit 1
  }
done
printf '%s\n' DropConversationSample DropTeamSample DropAgentSample DropTaskSample DropToolSample \
  DropInputToolSample DropProfileSample DropCredentialSample > "${test_dir}/expected-inputs"
tail -n 8 "${test_dir}/typed_inputs.calls" | diff - "${test_dir}/expected-inputs"

SELECTAI_AGENT_TEST_RUN_INPUTS=true run_case input_update_failure '' SetToolInputsSample failure
printf '%s\n' DropInputToolSample DropProfileSample DropCredentialSample > "${test_dir}/expected-input-failure"
tail -n 3 "${test_dir}/input_update_failure.calls" | diff - "${test_dir}/expected-input-failure"

SELECTAI_AGENT_TEST_RUN_INPUTS=true run_case input_create_failure '' CreateToolWithInputsSample failure
if grep -Fq DropInputToolSample "${test_dir}/input_create_failure.calls"; then
  echo 'Dropped an input tool that was not created' >&2
  exit 1
fi

SELECTAI_AGENT_TEST_RUN_INPUTS=true run_case input_cleanup_failure '' DropInputToolSample failure
tail -n 8 "${test_dir}/input_cleanup_failure.calls" | diff - "${test_dir}/expected-inputs"
grep -Fq 'Cleanup was incomplete' "${test_dir}/input_cleanup_failure.out"

SELECTAI_AGENT_TEST_RUN_INPUTS=true SELECTAI_AGENT_TEST_INPUT_FUNCTION='' \
  run_case missing_input_function '' '' failure
grep -Fq 'Missing required environment variable: SELECTAI_AGENT_TOOL_FUNCTION' \
  "${test_dir}/missing_input_function.out"
[[ ! -s "${test_dir}/missing_input_function.calls" ]]

SELECTAI_AGENT_TEST_RUN_INPUTS=maybe run_case invalid_input_flag '' '' failure
grep -Fq 'SELECTAI_AGENT_RUN_TOOL_INPUT_SAMPLES must be true or false' \
  "${test_dir}/invalid_input_flag.out"
[[ ! -s "${test_dir}/invalid_input_flag.calls" ]]

SELECTAI_AGENT_TEST_EXTRA_TOOLS=http run_case unsupported_http_tool '' '' failure
grep -Fq 'Unsupported additional tool sample: http (use rag websearch slack email).' \
  "${test_dir}/unsupported_http_tool.out"
[[ ! -s "${test_dir}/unsupported_http_tool.calls" ]]

SELECTAI_AGENT_TEST_GRANTEE=TEST_GRANTEE run_case team_access '' '' success
for sample in GrantTeamAccessSample RevokeTeamHandleAccessSample \
  GrantTeamHandleAccessSample RevokeTeamAccessSample; do
  grep -Fq "${sample}" "${test_dir}/team_access.calls" || {
    echo "Missing team-access sample invocation: ${sample}" >&2
    exit 1
  }
done

run_case failure '' RunTeamSample failure
tail -n 7 "${test_dir}/failure.calls" | diff - "${test_dir}/expected"

# Process status, not text in a generated response, determines agent failure.
run_case failure_without_error_log '' RunTeamSample failure 'Operation did not complete'
tail -n 7 "${test_dir}/failure_without_error_log.calls" | diff - "${test_dir}/expected"
SELECTAI_AGENT_TEST_FAIL_EXIT=0 run_case response_contains_error '' RunTeamSample success \
  'The report contains ERROR records.'

SELECTAI_AGENT_TEST_WORKFLOW=true run_case workflow_success '' '' success
grep -Fxq AgentWorkflowSample "${test_dir}/workflow_success.calls"
SELECTAI_AGENT_TEST_WORKFLOW=true run_case workflow_failure '' AgentWorkflowSample failure \
  'Workflow cleanup failed'
printf '%s\n' DropProfileSample DropCredentialSample > "${test_dir}/expected-workflow-failure"
tail -n 2 "${test_dir}/workflow_failure.calls" | diff - "${test_dir}/expected-workflow-failure"
SELECTAI_AGENT_TEST_WORKFLOW=true SELECTAI_AGENT_TEST_OMIT_CLEANUP=true \
  run_case workflow_incomplete '' '' failure
tail -n 2 "${test_dir}/workflow_incomplete.calls" | diff - "${test_dir}/expected-workflow-failure"

run_case conversation_failure '' CreateConversationSample failure
grep -Fq 'Conversation creation was attempted, but no ID was returned for cleanup.' \
  "${test_dir}/conversation_failure.out"
if grep -Fq 'DropConversationSample' "${test_dir}/conversation_failure.calls"; then
  echo 'Dropped a conversation without a database-returned ID' >&2
  exit 1
fi

run_case cleanup '' DropTeamSample failure 'Cleanup failed'
tail -n 7 "${test_dir}/cleanup.calls" | diff - "${test_dir}/expected"
grep -Fq 'Cleanup was incomplete' "${test_dir}/cleanup.out"

run_case partial '' CreateTaskSample failure
printf '%s\n' DropToolSample DropProfileSample DropCredentialSample \
  > "${test_dir}/expected-partial"
tail -n 3 "${test_dir}/partial.calls" | diff - "${test_dir}/expected-partial"
if grep -Eq 'DropTaskSample|DropAgentSample|DropTeamSample' "${test_dir}/partial.calls"; then
  echo 'Cleaned a resource that was not attempted' >&2
  exit 1
fi

run_case tool_create_failure '' CreateSqlToolSample failure
printf '%s\n' DropProfileSample DropCredentialSample > "${test_dir}/expected-tool-failure"
tail -n 2 "${test_dir}/tool_create_failure.calls" | diff - "${test_dir}/expected-tool-failure"
if grep -Eq 'DropToolSample|DropTaskSample|DropAgentSample|DropTeamSample' \
  "${test_dir}/tool_create_failure.calls"; then
  echo 'Cleaned a tool or dependent resource that was not created' >&2
  exit 1
fi

run_case named NEW_CREDENTIAL '' success
grep -Fq 'CreateCredentialSample' "${test_dir}/named.calls"
tail -n 7 "${test_dir}/named.calls" | diff - "${test_dir}/expected"
grep -Fq 'Run-owned credential: NEW_CREDENTIAL' "${test_dir}/named.out"

run_case create_failure NEW_CREDENTIAL CreateCredentialSample failure
if grep -Eq 'CreateProfileSample|DropCredentialSample' "${test_dir}/create_failure.calls"; then
  echo 'Used or dropped a credential that was not created' >&2
  exit 1
fi

run_case missing_view NEW_CREDENTIAL DropTaskSample failure \
  'ORA-00942: table or view does not exist'
grep -Fq 'Cleanup was incomplete' "${test_dir}/missing_view.out"
if grep -Fq 'Already absent: DropTaskSample' "${test_dir}/missing_view.out"; then
  echo 'A missing metadata view was mistaken for an absent task' >&2
  exit 1
fi

echo 'Agent sample runner tests passed.'
