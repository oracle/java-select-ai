#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/
#
# Generates source HTML for runnable samples and copies only the src-html pages
# into the main API documentation tree. The main Maven Javadoc configuration
# intentionally excludes sample packages from the public package index, while
# API method comments may still link directly to sample source pages.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SAMPLES_SRC="${REPO_ROOT}/samples/src/main/java"
API_DOCS="${REPO_ROOT}/doc/apidocs"
TARGET_CLASSES="${REPO_ROOT}/target/classes"
MVN="${MVN:-mvn}"
POM_FILE="${POM_FILE:-pom.xml}"

if [[ ! -d "${API_DOCS}" ]]; then
    echo "API Javadocs not found at ${API_DOCS}."
    echo "Run 'mvn javadoc:javadoc' before generating sample source pages."
    exit 1
fi

if [[ ! -d "${TARGET_CLASSES}" ]]; then
    echo "Compiled SDK classes not found at ${TARGET_CLASSES}."
    echo "Run 'mvn clean install' before generating sample source pages."
    exit 1
fi

TMP_DIR="$(mktemp -d)"
SOURCE_LIST="${TMP_DIR}/sample-sources.txt"
CLASSPATH_FILE="${TMP_DIR}/classpath.txt"

find "${SAMPLES_SRC}" -name "*.java" | sort > "${SOURCE_LIST}"

"${MVN}" \
    -q \
    -f "${REPO_ROOT}/${POM_FILE}" \
    -DincludeScope=compile \
    -Dmdep.outputFile="${CLASSPATH_FILE}" \
    dependency:build-classpath

SAMPLE_CLASSPATH="${TARGET_CLASSES}:$(cat "${CLASSPATH_FILE}")"

javadoc \
    -quiet \
    -Xdoclint:none \
    --release 17 \
    -classpath "${SAMPLE_CLASSPATH}" \
    -sourcepath "${SAMPLES_SRC}" \
    -d "${TMP_DIR}/javadocs" \
    -linksource \
    @"${SOURCE_LIST}"

mkdir -p "${API_DOCS}/src-html/com/oracle/database/selectai"
cp -R "${TMP_DIR}/javadocs/src-html/com/oracle/database/selectai/samples" \
    "${API_DOCS}/src-html/com/oracle/database/selectai/"

echo "Generated sample source pages under ${API_DOCS}/src-html/com/oracle/database/selectai/samples"
