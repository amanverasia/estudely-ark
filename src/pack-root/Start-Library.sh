#!/bin/sh
# Estudely Ark: start the library on this computer (macOS and Linux).
#
#   ./Start-Library.sh           only this computer can use the library
#   ./Start-Library.sh --share   other phones/computers on the same Wi-Fi or hotspot can use it too
#
# Starts two small programs from Shared-Readers/ and opens START-HERE in your web browser:
#   kiwix-serve  reads the .zim archives (Wikipedia, guides, docs)   port 8470
#   miniserve    serves everything else (PDFs, maps, this page)      port 8471
# Close this window or press Ctrl+C to stop both. Nothing here uses the internet.

cd "$(dirname "$0")" || exit 1
ROOT="$(pwd)"

case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)                P=macos-arm64 ;;
  Darwin-x86_64)               P=macos-x64 ;;
  Linux-x86_64)                P=linux-x64 ;;
  Linux-aarch64|Linux-arm64)   P=linux-arm64 ;;
  *) echo "Sorry, this system is not supported: $(uname -sm)"; exit 1 ;;
esac

LIB_PORT=${LIB_PORT:-8470}
FILE_PORT=${FILE_PORT:-8471}
if [ "${1:-}" = "--share" ]; then KIWIX_ADDR=ipv4; FILE_ADDR=0.0.0.0; else KIWIX_ADDR=127.0.0.1; FILE_ADDR=127.0.0.1; fi

KIWIX="$ROOT/Shared-Readers/kiwix-serve/$P/kiwix-serve"
MINI="$ROOT/Shared-Readers/miniserve/$P/miniserve"
for b in "$KIWIX" "$MINI"; do
  [ -f "$b" ] || { echo "Missing $b"; echo "Download the Shared-Readers folder too."; exit 1; }
done
# Files copied from the internet are "quarantined" on macOS; clear that for our own programs only.
[ "$P" = macos-arm64 ] || [ "$P" = macos-x64 ] && xattr -dr com.apple.quarantine "$ROOT/Shared-Readers" 2>/dev/null
chmod +x "$KIWIX" "$MINI" 2>/dev/null

# Collect every .zim archive in the modules that were downloaded (paths may contain spaces).
set --
for f in Core/*/*.zim Core/*/*/*.zim Extras/*/*.zim; do [ -f "$f" ] && set -- "$@" "$f"; done
if [ $# -eq 0 ]; then echo "No .zim archives found under Core/ or Extras/. Only PDFs and maps will be available."; fi

PIDS=""
cleanup() { [ -n "$PIDS" ] && kill $PIDS 2>/dev/null; exit 0; }
trap cleanup INT TERM EXIT

if [ $# -gt 0 ]; then
  "$KIWIX" --address="$KIWIX_ADDR" --port="$LIB_PORT" --nodatealiases --blockexternal --attachToProcess=$$ "$@" &
  PIDS="$PIDS $!"
fi
"$MINI" --interfaces "$FILE_ADDR" --port "$FILE_PORT" --index START-HERE.html --title "Estudely Ark" --quiet "$ROOT" &
PIDS="$PIDS $!"

sleep 2
URL="http://127.0.0.1:$FILE_PORT/START-HERE.html?lib=$LIB_PORT"
echo
echo "  Library is running. Open this in your web browser:  $URL"
if [ "${1:-}" = "--share" ]; then
  IP=$( (ipconfig getifaddr en0 || ipconfig getifaddr en1 || hostname -I | cut -d' ' -f1) 2>/dev/null )
  [ -n "$IP" ] && echo "  Other devices on the same Wi-Fi can open:          http://$IP:$FILE_PORT/START-HERE.html?lib=$LIB_PORT"
fi
echo "  Press Ctrl+C or close this window to stop."
echo
if [ -n "${NO_BROWSER:-}" ]; then :; elif command -v open >/dev/null 2>&1; then open "$URL"; elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$URL" >/dev/null 2>&1; fi
wait
