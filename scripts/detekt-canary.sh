#!/usr/bin/env bash
# D47: detekt alpha must still report each canary violation. The SARIF also lists every active rule's definition, so
# only `ruleId` entries (results) count; an unexpected result fails too, so it can't stand in for a missing one.
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "FAIL: $*" >&2; exit 1; }
rc=0; bash scripts/run-detekt.sh :core:detektNoSuchTask >/dev/null 2>&1 || rc=$?
[ "$rc" -eq 2 ] || fail "crash path: expected exit 2, got $rc"
rc=0; bash scripts/ci.sh canary || rc=$?
[ "$rc" -eq 1 ] || fail "findings path: expected exit 1, got $rc"
mapfile -t reports < <(find detekt-canary/build/reports/detekt -name '*.sarif' -type f)
((${#reports[@]})) || fail "no canary SARIF report"
results=$(mktemp)
grep -ohE '"ruleId": *"[^"]*"' "${reports[@]}" | sed -E 's/.*"detekt\.[a-z-]+\.([A-Za-z]+)"/\1/' | sort > "$results" || true
printf '%s\n' ForbiddenMethodCall GlobalCoroutineUsage UnsafeCallOnNullableType > "$results.expected"
diff "$results.expected" "$results" >&2 || fail "canary results differ from the expected rules (< expected, > reported)"
echo OK
