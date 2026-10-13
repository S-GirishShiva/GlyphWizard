#!/usr/bin/env bash
# Review Focus 1-2: the Nothing SDK resolves only for `full`, from the pinned commit, under dependency verification;
# an unreachable, offline or tampered SDK fails loudly, and sdkFree never needs it.
#
# Runs against a fresh Gradle user home: Gradle also caches the *transformed* SDK, so removing the downloaded AAR from
# an existing home never reproduces a machine that lacks it. The SDK-absent cases therefore run before the clean fetch.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/fresh-gradle-home.sh
fail() { echo "FAIL: $*" >&2; exit 1; }

home=build/sdk-fetch-test/gradle-home
fresh_gradle_home "$home"
gw() { ./gradlew --gradle-user-home "$home" --no-daemon --no-configuration-cache "$@"; }
sdk_cached() {
  find "$home/caches" \( -path '*com.nothing*' -o -name '*glyph-matrix*' \) -print > "$home/sdk-files.txt"
  [ -s "$home/sdk-files.txt" ]
}

# assembling the full app dexes the SDK, so it must resolve (an empty module's compile is NO-SOURCE and resolves nothing)
resolve=(:app:assembleFullDebug --rerun-tasks)
sha=be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29
metadata=gradle/verification-metadata.xml
backup=$(mktemp)
cp "$metadata" "$backup"
trap 'cp "$backup" "$metadata"' EXIT

# sdkFree builds and never resolves the SDK (this run also caches every other dependency)
gw -q :app:assembleSdkFreeDebug || fail "sdkFree build"
if sdk_cached; then fail "sdkFree resolved the SDK"; fi

# offline with the SDK not cached fails
out=$(gw --offline "${resolve[@]}" 2>&1) && fail "offline resolution passed"
grep -q 'No cached version of com.nothing:glyph-matrix-sdk-2.0' <<<"$out" || fail "offline message"

# an unreachable repository fails resolution
out=$(gw "${resolve[@]}" -PglyphSdk.repository=https://127.0.0.1:9 2>&1) && fail "unreachable repository passed"
grep -q 'Could not resolve com.nothing:glyph-matrix-sdk-2.0' <<<"$out" || fail "unreachable message"

# a tampered checksum (upstream changed or compromised) fails verification
sed -i "s/$sha/$(printf '0%.0s' {1..64})/" "$metadata"
out=$(gw "${resolve[@]}" 2>&1) && fail "tampered checksum passed"
grep -q 'One artifact failed verification: glyph-matrix-sdk-2.0' <<<"$out" || fail "verification message"
cp "$backup" "$metadata"

# a clean resolution downloads exactly the pinned file
gw -q "${resolve[@]}" || fail "clean resolution"
mapfile -t aars < <(find "$home/caches/modules-2/files-2.1/com.nothing" -name '*.aar' -type f)
[ "${#aars[@]}" -eq 1 ] || fail "expected one cached SDK AAR, got ${#aars[@]}"
sha256sum "${aars[0]}" > "$home/sdk.sha"
grep -q "^$sha " "$home/sdk.sha" || fail "cached SDK hash differs from the pin"

rm -rf "$home"
echo OK
