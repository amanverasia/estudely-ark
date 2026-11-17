#!/bin/sh
# Build-machine emulator helper for the Estudely Ark AVD only ("ark-test").
# Other agents run their own emulators on the same machine: every adb call here targets ark-test by serial.
#   scripts/android_emu.sh start|stop|serial|adb ARGS...|shot OUT.png
. "$(dirname "$0")/local.env" 2>/dev/null || { echo "missing scripts/local.env (see local.env.example)" >&2; exit 1; }
W="$ARK_BUILD_HOST"; K="$ARK_SSH_KEY"
R() {
  ssh -o BatchMode=yes -i "$K" "$W" "export ANDROID_HOME=~/Android/Sdk; ADB=\$ANDROID_HOME/platform-tools/adb
    S=''; for d in \$(\$ADB devices | awk 'NR>1 && \$2==\"device\" {print \$1}'); do
      [ \"\$(\$ADB -s \$d emu avd name 2>/dev/null | head -1 | tr -d '\r')\" = ark-test ] && S=\$d; done
    A() { \$ADB -s \$S \"\$@\"; }; $1"
}
case "$1" in
  start) R 'if [ -n "$S" ]; then echo "already running ($S)"; else
      setsid nohup $ANDROID_HOME/emulator/emulator -avd ark-test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-save > ~/estudely-android/emulator.log 2>&1 < /dev/null &
      for i in $(seq 1 60); do sleep 2; for d in $($ADB devices | awk "NR>1 {print \$1}"); do [ "$($ADB -s $d emu avd name 2>/dev/null | head -1 | tr -d "\r")" = ark-test ] && S=$d; done; [ -n "$S" ] && break; done
      fi
      A wait-for-device; until [ "$(A shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = 1 ]; do sleep 2; done; echo "booted ($S)"' ;;
  stop) R '[ -n "$S" ] && A emu kill && echo "stopped ($S)" || echo "ark-test not running"' ;;
  serial) R 'echo $S' ;;
  shot) R 'A exec-out screencap -p' > "$2" && echo "saved $2" ;;
  adb) shift; R "[ -n \"\$S\" ] || { echo 'ark-test not running' >&2; exit 1; }; A $*" ;;
esac
