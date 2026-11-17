#!/bin/sh
# Resumable fetch of "<relpath>\t<url>" lines into a destination dir. Usage: fetch.sh urls.tsv destdir
set -u
dest=$2
while IFS="$(printf '\t')" read -r rel url; do
  mkdir -p "$dest/$(dirname "$rel")"
  echo "$(date '+%H:%M:%S') START $rel"
  curl -fL --retry 5 --retry-delay 5 -C - -sS -o "$dest/$rel" "$url" && echo "$(date '+%H:%M:%S') OK $rel $(stat -f%z "$dest/$rel")" || echo "$(date '+%H:%M:%S') FAIL $rel"
done < "$1"
echo DONE
