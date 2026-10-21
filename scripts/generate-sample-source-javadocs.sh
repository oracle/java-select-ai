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
SAMPLE_SOURCE_DOCS="${API_DOCS}/src-html/com/oracle/database/selectai/samples"
SAMPLE_SOURCE_INDEX="${SAMPLE_SOURCE_DOCS}/index.html"

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

find "${SAMPLES_SRC}" -name "*.java" | LC_ALL=C sort > "${SOURCE_LIST}"

"${MVN}" \
    -q \
    -f "${REPO_ROOT}/${POM_FILE}" \
    -Psamples \
    -DincludeScope=runtime \
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

rm -rf "${API_DOCS}/src-html/com/oracle/database/selectai/samples"
mkdir -p "${API_DOCS}/src-html/com/oracle/database/selectai"
cp -R "${TMP_DIR}/javadocs/src-html/com/oracle/database/selectai/samples" \
    "${API_DOCS}/src-html/com/oracle/database/selectai/"

(
    # Keep wildcard ordering reproducible without changing Javadoc's locale.
    export LC_ALL=C

    printf '%s\n' '<!DOCTYPE HTML>' '<html lang="en">' '<head>'
    printf '%s\n' '<meta charset="UTF-8">' '<title>Sample source</title>'
    printf '%s\n' '<meta name="viewport" content="width=device-width, initial-scale=1">'
    printf '%s\n' '<meta name="description" content="Select AI for Java sample source">'
    printf '%s\n' '<link rel="stylesheet" type="text/css" href="../../../../../../stylesheet.css" title="Style">'
    printf '%s\n' '</head>' '<body>' '<main role="main">'
    printf '%s\n' '<h1>Sample source</h1>'
    printf '%s\n' '<p>Runnable Java sample source organized by feature package.</p>'

    for package_dir in "${SAMPLE_SOURCE_DOCS}"/*; do
        [[ -d "${package_dir}" ]] || continue
        package_name="${package_dir##*/}"
        printf '<h2>%s</h2>\n<ul>\n' "${package_name}"
        for source_page in "${package_dir}"/*.html; do
            [[ -f "${source_page}" ]] || continue
            file_name="${source_page##*/}"
            [[ "${file_name}" == "index.html" ]] && continue
            class_name="${file_name%.html}"
            printf '  <li><a href="%s/%s">%s</a></li>\n' \
                "${package_name}" "${file_name}" "${class_name}"
        done
        printf '%s\n' '</ul>'
    done

    printf '%s\n' '</main>' '</body>' '</html>'
) > "${SAMPLE_SOURCE_INDEX}"

echo "Generated sample source pages under ${SAMPLE_SOURCE_DOCS}"
echo "Generated sample source index at ${SAMPLE_SOURCE_INDEX}"
