#!/usr/bin/env bash
# ktlint-gradle < 14.1 registers no Android tasks under built-in Kotlin, and Kover skips a bound at 0 lines,
# so both can pass while checking nothing.
set -euo pipefail
fail() { echo "FAIL: $*" >&2; exit 1; }
out=$(mktemp -d)
for m in designsystem glyph-sdk app spike; do
  ./gradlew -q ":$m:tasks" --all > "$out/$m.txt"
  grep -q 'ktlintMainSourceSetCheck' "$out/$m.txt" || fail ":$m has no ktlint main source-set task"
done
./gradlew -q :core:koverXmlReport :agent:koverXmlReport :designsystem:koverXmlReportDebug \
  :app:koverXmlReportSdkFreeDebug
# :agent is left out until it has executable code (M5): a const-only placeholder has 0 lines by construction
for r in core/build/reports/kover/report.xml \
         designsystem/build/reports/kover/reportDebug.xml app/build/reports/kover/reportSdkFreeDebug.xml; do
  test -f "$r" || fail "missing $r"
  # the report-level LINE counter is the last one in the file
  grep -o '<counter type="LINE" missed="[0-9]*" covered="[0-9]*"/>' "$r" > "$out/lines.txt" || fail "no LINE counter in $r"
  tail -n1 "$out/lines.txt" > "$out/last.txt"
  if grep -q 'missed="0" covered="0"' "$out/last.txt"; then fail "$r counts zero lines"; fi
done
echo OK
