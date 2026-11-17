#!/bin/sh
# A test phone attached by USB to another machine (ARK_PIXEL_HOST in local.env). If others use the phone too,
# never tap its screen, put its foreground app back afterwards, and install only our own app.
#   scripts/android_pixel.sh adb ARGS... | shot OUT.png | push LOCAL REMOTE_TMP
. "$(dirname "$0")/local.env" 2>/dev/null || { echo "missing scripts/local.env (see local.env.example)" >&2; exit 1; }
O="$ARK_PIXEL_HOST"; K="$ARK_SSH_KEY"; SER="$ARK_PIXEL_SERIAL"
R() { ssh -o BatchMode=yes -i "$K" "$O" "A() { \$HOME/Android/Sdk/platform-tools/adb -s $SER \"\$@\"; }; $1"; }
case "$1" in
  adb) shift; R "A $*" ;;
  shot) R 'A exec-out screencap -p' > "$2" && echo "saved $2" ;;
  push) scp -q -i "$K" "$2" "$O:$3" && echo "copied to $O:$3" ;;
esac
