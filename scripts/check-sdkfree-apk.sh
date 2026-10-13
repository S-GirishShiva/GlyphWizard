#!/usr/bin/env bash
# The sdkFree APK must contain no Nothing SDK classes, ENABLE permission or NothingKey (D18/D26); a positive control on
# the full APK proves each check can fail. That sdkFree never even resolves the SDK is check-glyph-sdk-fetch.sh's job.
# Every grep reads a file: under pipefail, an early `grep -q` match on a pipe can SIGPIPE the producer and hide a hit.
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "FAIL: $*" >&2; exit 1; }
sdk=${ANDROID_HOME:-$HOME/Android/Sdk}
bt=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)
aapt2="$bt/aapt2"
test -x "$aapt2" || fail "aapt2 not found under $sdk/build-tools"
one_apk() {
  mapfile -t apks < <(find "$1" -name '*.apk' -type f)
  [ "${#apks[@]}" -eq 1 ] || fail "expected one APK in $1, got ${#apks[@]}"
  echo "${apks[0]}"
}
# <apk> <out-dir>: dex strings, permissions and the manifest tree (binary XML, so read through aapt2) into files
inspect() {
  unzip -qo "$1" 'classes*.dex' -d "$2"
  strings "$2"/classes*.dex > "$2/strings.txt"
  "$aapt2" dump permissions "$1" > "$2/permissions.txt"
  "$aapt2" dump xmltree --file AndroidManifest.xml "$1" > "$2/manifest.txt"
}

./gradlew -q :app:assembleSdkFreeRelease
apk=$(one_apk app/build/outputs/apk/sdkFree/release) # a plain assignment, so set -e sees one_apk fail
d=$(mktemp -d)
inspect "$apk" "$d"
if grep -q 'Lcom/nothing/' "$d/strings.txt"; then fail "SDK classes in sdkFree"; fi
if grep -q 'com.nothing.ketchum.permission.ENABLE' "$d/permissions.txt"; then fail "ENABLE permission in sdkFree"; fi
if grep -q '"NothingKey"' "$d/manifest.txt"; then fail "NothingKey in sdkFree"; fi

./gradlew -q :app:assembleFullDebug
apk=$(one_apk app/build/outputs/apk/full/debug)
e=$(mktemp -d)
inspect "$apk" "$e"
grep -q 'Lcom/nothing/ketchum/' "$e/strings.txt" || fail "control: full lacks SDK classes"
grep -q 'com.nothing.ketchum.permission.ENABLE' "$e/permissions.txt" || fail "control: full lacks ENABLE permission"
# without NothingKey the glyph service accepts frames but lights nothing (hardware-spike-results.md, Task 5)
grep -q '"NothingKey"' "$e/manifest.txt" || fail "control: full lacks NothingKey"
echo OK
