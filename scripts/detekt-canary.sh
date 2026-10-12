#!/usr/bin/env bash
set -euo pipefail
fail() { echo "FAIL: $*" >&2; exit 1; }
rc=0; bash scripts/run-detekt.sh :core:detektNoSuchTask >/dev/null 2>&1 || rc=$?
[ "$rc" -eq 2 ] || fail "crash path: expected exit 2, got $rc"
rc=0; bash scripts/run-detekt.sh -PwithCanary :detekt-canary:detektMain || rc=$?   # becomes `ci.sh canary` in Task 7
[ "$rc" -eq 1 ] || fail "findings path: expected exit 1, got $rc"
mapfile -t reports < <(find detekt-canary/build/reports/detekt -name '*.sarif' -type f)
((${#reports[@]})) || fail "no canary SARIF report"
for id in UnsafeCallOnNullableType GlobalCoroutineUsage ForbiddenMethodCall; do
  grep -q "$id" "${reports[@]}" || fail "detekt no longer reports $id"
done
echo OK
