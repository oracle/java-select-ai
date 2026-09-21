#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/
#
# Verifies the source and Javadoc artifacts produced by the Maven build and
# checks that API links to sample source pages resolve in both generated docs
# and the attached Javadoc JAR.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ARTIFACT_ID="${ARTIFACT_ID:-select-ai}"
VERSION="${VERSION:-1.0.0}"
TARGET_DIR="${REPO_ROOT}/target"
API_DOCS="${REPO_ROOT}/doc/apidocs"
MAIN_JAR="${TARGET_DIR}/${ARTIFACT_ID}-${VERSION}.jar"
SOURCES_JAR="${TARGET_DIR}/${ARTIFACT_ID}-${VERSION}-sources.jar"
JAVADOC_JAR="${TARGET_DIR}/${ARTIFACT_ID}-${VERSION}-javadoc.jar"

for command in jar find grep sort; do
    if ! command -v "${command}" >/dev/null 2>&1; then
        echo "Required command not found: ${command}" >&2
        exit 1
    fi
done

for artifact in "${MAIN_JAR}" "${SOURCES_JAR}" "${JAVADOC_JAR}"; do
    if [[ ! -f "${artifact}" ]]; then
        echo "Required artifact not found: ${artifact}" >&2
        echo "Run 'mvn clean install' first." >&2
        exit 1
    fi
done

if [[ ! -d "${API_DOCS}" ]]; then
    echo "Generated API documentation not found: ${API_DOCS}" >&2
    echo "Run 'mvn javadoc:javadoc' and the sample-source Javadoc generator first." >&2
    exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

SOURCE_LIST="${TMP_DIR}/source-entries.txt"
JAVADOC_LIST="${TMP_DIR}/javadoc-entries.txt"
MAIN_LIST="${TMP_DIR}/main-entries.txt"

jar tf "${MAIN_JAR}" > "${MAIN_LIST}"
jar tf "${SOURCES_JAR}" > "${SOURCE_LIST}"
jar tf "${JAVADOC_JAR}" > "${JAVADOC_LIST}"

for artifact_and_list in \
    "${MAIN_JAR}|${MAIN_LIST}" \
    "${SOURCES_JAR}|${SOURCE_LIST}" \
    "${JAVADOC_JAR}|${JAVADOC_LIST}"; do
    artifact="${artifact_and_list%%|*}"
    entries="${artifact_and_list#*|}"
    for legal_file in "META-INF/LICENSE" "META-INF/THIRD-PARTY_LICENSE"; do
        if ! grep -Fxq "${legal_file}" "${entries}"; then
            echo "$(basename "${artifact}") is missing ${legal_file}" >&2
            exit 1
        fi
    done
done

required_source="com/oracle/database/selectai/SelectAI.java"
if ! grep -Fxq "${required_source}" "${SOURCE_LIST}"; then
    echo "Source JAR is missing ${required_source}" >&2
    exit 1
fi

for required_javadoc in \
    "com/oracle/database/selectai/SelectAI.html" \
    "com/oracle/database/selectai/model/ProfileAttributes.html" \
    "overview-summary.html"; do
    if ! grep -Fxq "${required_javadoc}" "${JAVADOC_LIST}"; then
        echo "Javadoc JAR is missing ${required_javadoc}" >&2
        exit 1
    fi
done

sample_links=()
while IFS= read -r link; do
    sample_links+=("${link}")
done < <(
    find "${REPO_ROOT}/src/main/java" -type f -name '*.java' \
        -exec grep -hEo \
        'src-html/com/oracle/database/selectai/samples/[^" ]+\.html' {} + | sort -u
)

if [[ "${#sample_links[@]}" -eq 0 ]]; then
    echo "No sample-source Javadoc links were found in the public API sources." >&2
    exit 1
fi

for relative_path in "${sample_links[@]}"; do
    if [[ ! -f "${API_DOCS}/${relative_path}" ]]; then
        echo "Generated Javadoc is missing ${relative_path}" >&2
        exit 1
    fi
    if ! grep -Fxq "${relative_path}" "${JAVADOC_LIST}"; then
        echo "Javadoc JAR is missing ${relative_path}" >&2
        exit 1
    fi
done

echo "Verified ${#sample_links[@]} sample-source Javadoc links."
echo "Verified legal files in ${MAIN_JAR}, ${SOURCES_JAR}, and ${JAVADOC_JAR}."
echo "Verified ${SOURCES_JAR} and ${JAVADOC_JAR}."
