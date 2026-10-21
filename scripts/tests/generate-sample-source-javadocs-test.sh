#!/usr/bin/env bash
#
# Copyright (c) 2026, Oracle and/or its affiliates.
#
# Licensed under the Universal Permissive License Version 1.0 as shown at
# https://oss.oracle.com/licenses/upl/

set -euo pipefail

# Stub the build tools so the generator can be tested without Java or Maven.
case "${0##*/}" in
  mvn)
    [[ "$LC_ALL" == "$TEST_LOCALE" ]]
    for arg in "$@"; do
      if [[ "$arg" == -Dmdep.outputFile=* ]]; then
        printf 'test-classpath\n' > "${arg#*=}"
        exit 0
      fi
    done
    exit 1
    ;;
  javadoc)
    [[ "$LC_ALL" == "$TEST_LOCALE" ]]
    output_dir=''
    source_list=''
    while [[ $# -gt 0 ]]; do
      case "$1" in
        -d) output_dir="$2"; shift ;;
        @*) source_list="${1#@}" ;;
      esac
      shift
    done
    [[ -n "$output_dir" && -f "$source_list" ]]
    cp "$source_list" "$TEST_SOURCE_LIST"
    while IFS= read -r source_file; do
      relative_path="${source_file#"$TEST_SOURCES/"}"
      page="$output_dir/src-html/${relative_path%.java}.html"
      mkdir -p "$(dirname "$page")"
      touch "$page"
    done < "$source_list"
    exit 0
    ;;
esac

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
repo="$test_dir/repo"
sources="$repo/samples/src/main/java"
package_path="com/oracle/database/selectai/samples"
mkdir -p "$repo/scripts" "$repo/doc/apidocs" "$repo/target/classes" \
  "$sources/$package_path/agent" "$sources/$package_path/vectorindex" \
  "$test_dir/bin" "$test_dir/tmp"
cp "$script_dir/../generate-sample-source-javadocs.sh" "$repo/scripts/"
cp "$script_dir/generate-sample-source-javadocs-test.sh" "$test_dir/bin/mvn"
cp "$script_dir/generate-sample-source-javadocs-test.sh" "$test_dir/bin/javadoc"
chmod +x "$test_dir/bin/mvn" "$test_dir/bin/javadoc"

pages=(
  agent/ListAgentTeamsSample
  agent/ListAgentsSample
  vectorindex/UpdateVectorIndexAttributeSample
  vectorindex/UpdateVectorIndexAttributeWithClobSample
  vectorindex/UpdateVectorIndexAttributesSample
)
for page in "${pages[@]}"; do
  touch "$sources/$package_path/$page.java"
  printf '%s.html\n' "$page"
done > "$test_dir/expected-links"
find "$sources" -name '*.java' | LC_ALL=C sort > "$test_dir/expected-sources"

locales=(C)
locale -a > "$test_dir/locales"
if grep -Eiq '^en_US\.(UTF-8|utf8)$' "$test_dir/locales"; then
  locales+=("$(grep -Ei '^en_US\.(UTF-8|utf8)$' "$test_dir/locales" | head -n 1)")
else
  printf 'SKIP: en_US UTF-8 locale is not installed.\n'
fi

for test_locale in "${locales[@]}"; do
  for run in 1 2; do
    env -i PATH="$test_dir/bin:$PATH" TMPDIR="$test_dir/tmp" \
      LC_ALL="$test_locale" TEST_LOCALE="$test_locale" \
      TEST_SOURCES="$sources" TEST_SOURCE_LIST="$test_dir/actual-sources" \
      MVN="$test_dir/bin/mvn" \
      bash "$repo/scripts/generate-sample-source-javadocs.sh" \
      > "$test_dir/generator.out" 2>&1 || {
        cat "$test_dir/generator.out" >&2
        exit 1
      }
    index="$repo/doc/apidocs/src-html/$package_path/index.html"
    sed -n 's@^  <li><a href="\([^"]*\)".*@\1@p' "$index" > "$test_dir/actual-links"
    diff -u "$test_dir/expected-links" "$test_dir/actual-links"
    diff -u "$test_dir/expected-sources" "$test_dir/actual-sources"
    if [[ -f "$test_dir/baseline.html" ]]; then
      cmp "$test_dir/baseline.html" "$index"
    else
      cp "$index" "$test_dir/baseline.html"
    fi
    printf 'PASS: sample index ordering (%s, run %s)\n' "$test_locale" "$run"
  done
done
