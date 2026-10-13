#!/usr/bin/env bash
# One entry point for CI jobs and the verification metadata, both read from scripts/ci-tasks.txt so they can't drift.
set -euo pipefail
cd "$(dirname "$0")/.."
job=${1:?usage: ci.sh <checks|detekt|detekt-full|canary|full|verification-metadata>}
if [ "$job" = verification-metadata ]; then
  source scripts/fresh-gradle-home.sh
  home=build/verification-metadata/gradle-home
  fresh_gradle_home "$home"
  mapfile -t all < <(grep -Ev '^(#|$)' scripts/ci-tasks.txt | sed 's/^[a-z-]*: //' | tr ' ' '\n' | grep -v '^$' | sort -u)
  # the configuration cache would replay resolved classpaths and skip recording them
  ./gradlew --gradle-user-home "$home" --no-daemon --no-configuration-cache --write-verification-metadata sha256 \
    -Pcanary.ignoreFailures "${all[@]}"
  rm -rf "$home"
  exit 0
fi
read -ra tasks <<<"$(sed -n "s/^$job: //p" scripts/ci-tasks.txt)"
((${#tasks[@]})) || { echo "unknown job: $job" >&2; exit 1; }
case "$job" in
  detekt | detekt-full | canary) exec bash scripts/run-detekt.sh "${tasks[@]}" ;;
  *) exec ./gradlew "${tasks[@]}" ;;
esac
