#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
hits=$(mktemp)
# grep exits 1 for "no match" (fine here) and 2 for an error, which must fail the guard
no_match_ok() { [ "$?" -eq 1 ]; }
mapfile -t files < <(find . \( -path ./detekt-canary -o -path '*/build' -o -path '*/src/test*' -o -path '*/src/androidTest*' \) -prune -o -path '*/src/*' -name '*.kt' -print)
((${#files[@]})) || { echo "FAIL: no sources found" >&2; exit 1; }
grep -nHE '!!|\bGlobalScope\b|\brunBlocking\b|\bLiveData\b|\bAsyncTask\b|\bfindViewById\b|\bThread\.sleep\b' "${files[@]}" >>"$hits" || no_match_ok
mapfile -t builds < <(find . -path '*/build' -prune -o -name '*.gradle.kts' -print)
grep -nH 'kapt' "${builds[@]}" >>"$hits" || no_match_ok
# mutable state in an `object` / `companion object` body (patterns #26); heuristic, review is the backstop
awk '
  /^[[:space:]]*([a-z]+[[:space:]]+)*object([[:space:]]|\{)/ && /\{/ { inobj = 1; depth = 0 }
  inobj {
    if (depth == 1 && /(^|[[:space:]])var[[:space:]]/) print FILENAME ":" FNR ": var in object"
    depth += gsub(/\{/, "{") - gsub(/\}/, "}")
    if (depth <= 0) inobj = 0
  }' "${files[@]}" >>"$hits"
# #17 no XML layouts
find . -path '*/build' -prune -o -type d -path '*/src/*/res/layout*' -print >>"$hits"
# #12, #20 banned plugins and dependencies (kapt is above)
grep -nHE 'legacy-kapt|com\.google\.dagger|io\.insert-koin|io\.reactivex' "${builds[@]}" gradle/libs.versions.toml >>"$hits" || no_match_ok
if [ -s "$hits" ]; then cat "$hits" >&2; echo "FAIL: banned constructs" >&2; exit 1; fi
echo OK
