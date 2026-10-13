# shellcheck shell=bash
# Sourced, not run. fresh_gradle_home <dir>: recreate <dir> as an empty Gradle user home.
# Builds against it must pass --no-daemon: a daemon outlives the build and keeps writing into the home, racing its removal.
# A warm home hides what a clean machine downloads: Gradle caches parsed metadata (parent POMs, .module files) and
# transformed artifacts, so dependency verification and SDK-resolution tests must start from an empty home.
fresh_gradle_home() {
  local home=$1
  rm -rf "$home"
  mkdir -p "$home"
  # reuse the wrapper distribution and provisioned JDKs; only dependencies start empty
  local shared
  for shared in wrapper jdks; do
    if [ -d "$HOME/.gradle/$shared" ]; then ln -s "$HOME/.gradle/$shared" "$home/$shared"; fi
  done
}

