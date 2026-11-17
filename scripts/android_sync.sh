#!/bin/sh
# Copy android/ from this repo to the build machine (the source of truth stays in the repo), then run a Gradle task there.
#   scripts/android_sync.sh [gradle args...]     e.g. scripts/android_sync.sh assembleDebug
set -e
cd "$(dirname "$0")/.."
. "$(dirname "$0")/local.env" 2>/dev/null || { echo "missing scripts/local.env (see local.env.example)" >&2; exit 1; }
W="$ARK_BUILD_HOST"; K="$ARK_SSH_KEY"
COPYFILE_DISABLE=1 tar --no-mac-metadata --exclude build --exclude .gradle --exclude local.properties -C android -cf - . \
  | ssh -o BatchMode=yes -i "$K" "$W" 'mkdir -p ~/estudely-ark-android && cd ~/estudely-ark-android &&
      find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name local.properties -exec rm -rf {} + && tar -xf - 2>/dev/null'
# Mirror, not overlay: the old tree (sources and build output) is removed first, so every build is clean.
# An overlay once left a deleted class inside a release build.
[ $# -eq 0 ] && exit 0
ssh -o BatchMode=yes -i "$K" "$W" "cd ~/estudely-ark-android && export JAVA_HOME=~/estudely-android/jdk ANDROID_HOME=~/Android/Sdk && echo sdk.dir=\$ANDROID_HOME > local.properties && ~/estudely-android/gradle/bin/gradle --console=plain -q $*"
