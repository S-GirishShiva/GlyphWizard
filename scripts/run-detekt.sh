#!/usr/bin/env bash
# Gradle exits 1 for detekt findings and for crashes alike, so classify by the SARIF reports.
set -euo pipefail
find . -path ./build-logic -prune -o -path '*/build/reports/detekt' -type d -prune -exec rm -rf {} +
if ./gradlew "$@"; then echo "detekt: clean"; exit 0; fi
mapfile -t reports < <(find . -path '*/build/reports/detekt/*.sarif' -type f)
if ((${#reports[@]})) && grep -q '"ruleId"' "${reports[@]}"; then
  echo "detekt findings: ${reports[*]}" >&2
  exit 1
fi
echo "detekt crashed: tool error, not findings" >&2
exit 2
