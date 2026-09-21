#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/
#
# Generates local checksum sidecars for the artifacts that would be published
# for this Maven project. This script never uploads or publishes artifacts.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ARTIFACT_ID="${ARTIFACT_ID:-select-ai}"
VERSION="${VERSION:-1.0.0}"
TARGET_DIR="${REPO_ROOT}/target"

POM_FILE="${TARGET_DIR}/${ARTIFACT_ID}-${VERSION}.pom"
FILES=(
    "${POM_FILE}"
    "${TARGET_DIR}/${ARTIFACT_ID}-${VERSION}.jar"
    "${TARGET_DIR}/${ARTIFACT_ID}-${VERSION}-sources.jar"
    "${TARGET_DIR}/${ARTIFACT_ID}-${VERSION}-javadoc.jar"
)

for file in "${FILES[@]:1}"; do
    if [[ ! -f "${file}" ]]; then
        echo "Required artifact not found: ${file}" >&2
        echo "Run 'mvn clean package' first." >&2
        exit 1
    fi
done

cp "${REPO_ROOT}/pom.xml" "${POM_FILE}"

for file in "${FILES[@]}"; do
    if command -v md5 >/dev/null 2>&1; then
        md5 -q "${file}" > "${file}.md5"
    elif command -v md5sum >/dev/null 2>&1; then
        md5sum "${file}" | awk '{print $1}' > "${file}.md5"
    else
        echo "Required checksum command not found: md5 or md5sum" >&2
        exit 1
    fi

    if command -v shasum >/dev/null 2>&1; then
        shasum -a 1 "${file}" | awk '{print $1}' > "${file}.sha1"
        shasum -a 256 "${file}" | awk '{print $1}' > "${file}.sha256"
        shasum -a 512 "${file}" | awk '{print $1}' > "${file}.sha512"
    else
        for algorithm in 1 256 512; do
            command_name="sha${algorithm}sum"
            if ! command -v "${command_name}" >/dev/null 2>&1; then
                echo "Required checksum command not found: shasum or ${command_name}" >&2
                exit 1
            fi
            "${command_name}" "${file}" | awk '{print $1}' > "${file}.sha${algorithm}"
        done
    fi
done

echo "Generated checksums for ${#FILES[@]} publication files under ${TARGET_DIR}."
