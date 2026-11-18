#!/bin/sh
# Copy android/ from this repo to the build machine (the source of truth stays in the repo), then run a Gradle task there.
#   scripts/android_sync.sh [gradle args...]     e.g. scripts/android_sync.sh assembleDebug
# Another build machine: set ARK_BUILD_HOST, and ARK_BUILD_JDK / ARK_BUILD_SDK / ARK_BUILD_GRADLE if its paths differ
# (environment wins over local.env; ARK_BUILD_GRADLE=./gradlew uses the project's wrapper).
set -e
cd "$(dirname "$0")/.."
_h="$ARK_BUILD_HOST" _j="$ARK_BUILD_JDK" _s="$ARK_BUILD_SDK" _g="$ARK_BUILD_GRADLE"
. "$(dirname "$0")/local.env" 2>/dev/null || { echo "missing scripts/local.env (see local.env.example)" >&2; exit 1; }
W="${_h:-$ARK_BUILD_HOST}"; K="$ARK_SSH_KEY"
# Defaults are literal $HOME paths, expanded by the build machine's shell (not this Mac's).
JDK="${_j:-${ARK_BUILD_JDK:-\$HOME/estudely-android/jdk}}"; SDK="${_s:-${ARK_BUILD_SDK:-\$HOME/Android/Sdk}}"
GRADLE="${_g:-${ARK_BUILD_GRADLE:-\$HOME/estudely-android/gradle/bin/gradle}}"
COPYFILE_DISABLE=1 tar --no-mac-metadata --exclude build --exclude .gradle --exclude local.properties -C android -cf - . \
  | ssh -o BatchMode=yes -i "$K" "$W" 'mkdir -p ~/estudely-ark-android && cd ~/estudely-ark-android &&
      find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name local.properties -exec rm -rf {} + && tar -xf - 2>/dev/null'
# Mirror, not overlay: the old tree (sources and build output) is removed first, so every build is clean.
# An overlay once left a deleted class inside a release build.
[ $# -eq 0 ] && exit 0
ssh -o BatchMode=yes -i "$K" "$W" "cd ~/estudely-ark-android && export JAVA_HOME=$JDK ANDROID_HOME=$SDK && echo sdk.dir=\$ANDROID_HOME > local.properties && $GRADLE --console=plain -q $*"
