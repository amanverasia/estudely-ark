#!/bin/sh
# Screenshot a URL with headless Brave inside the offline sandbox (no internet). Usage: offline_shot.sh OUT.png URL [WAIT_MS]
B="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"
PROFILE="${SHOT_PROFILE:?set SHOT_PROFILE to a throwaway dir}"
SB="$(dirname "$0")/offline.sb"
sandbox-exec -f "$SB" "$B" --headless=new --no-sandbox --user-data-dir="$PROFILE" --no-first-run --disable-extensions \
  --disable-crash-reporter --window-size=1200,900 --timeout="${3:-12000}" --screenshot="$1" "$2" > "$1.log" 2>&1 &
pid=$!; n=0
while kill -0 $pid 2>/dev/null && [ $n -lt 45 ]; do sleep 1; n=$((n+1)); done
kill $pid 2>/dev/null; pkill -f "user-data-dir=$PROFILE" 2>/dev/null
[ -s "$1" ] && echo "OK $1 $(stat -f%z "$1")" || echo "FAIL $1 (see $1.log)"
