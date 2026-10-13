#!/usr/bin/env bash
# ktlint-gradle < 14.1 registers no Android tasks under built-in Kotlin, and Kover skips a bound at 0 lines,
# so both can pass while checking nothing. `full` checks only the full-flavour report (needs the fetched SDK).
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "FAIL: $*" >&2; exit 1; }
out=$(mktemp -d)
assert_counts_lines() {
  test -f "$1" || fail "missing $1"
  # the report-level LINE counter is the last one in the file
  grep -o '<counter type="LINE" missed="[0-9]*" covered="[0-9]*"/>' "$1" > "$out/lines.txt" || fail "no LINE counter in $1"
  tail -n1 "$out/lines.txt" > "$out/last.txt"
  if grep -q 'missed="0" covered="0"' "$out/last.txt"; then fail "$1 counts zero lines"; fi
}

if [ "${1:-}" = full ]; then
  ./gradlew -q :glyph-sdk:koverXmlReportFullDebug
  assert_counts_lines glyph-sdk/build/reports/kover/reportFullDebug.xml
  echo OK
  exit 0
fi

for m in designsystem glyph-sdk app spike; do
  ./gradlew -q ":$m:tasks" --all > "$out/$m.txt"
  grep -q 'ktlintMainSourceSetCheck' "$out/$m.txt" || fail ":$m has no ktlint main source-set task"
done
./gradlew -q :core:koverXmlReport :designsystem:koverXmlReportDebug :glyph-sdk:koverXmlReportSdkFreeDebug \
  :app:koverXmlReportSdkFreeDebug
# :agent is left out until it has executable code (M5): a const-only placeholder has 0 lines by construction
for r in core/build/reports/kover/report.xml designsystem/build/reports/kover/reportDebug.xml \
  glyph-sdk/build/reports/kover/reportSdkFreeDebug.xml app/build/reports/kover/reportSdkFreeDebug.xml; do
  assert_counts_lines "$r"
done
echo OK
