#!/bin/sh
# Fetch the desktop map viewer's libraries, fonts and icons at pinned versions into DEST/mapviewer.
#   scripts/fetch_mapviewer.sh DEST
# Library files are checked against SHA-256. Fonts/sprites come from a pinned basemaps-assets commit
# (identical to the files tested in the prototype on 16 Nov 2026).
set -eu
DEST=$1/mapviewer
ASSETS_COMMIT=028c18f713baecad011301ff7a69acc39bcc2ae7
mkdir -p "$DEST"
get() {  # url file sha256
  curl -fsSL --retry 3 -o "$DEST/$2" "$1"
  got=$( (sha256sum "$DEST/$2" 2>/dev/null || shasum -a 256 "$DEST/$2") | cut -c1-64)
  [ "$got" = "$3" ] || { echo "CHECKSUM MISMATCH $2"; rm -f "$DEST/$2"; exit 1; }
}
J=https://cdn.jsdelivr.net/npm
get $J/maplibre-gl@5.24.1/dist/maplibre-gl.js maplibre-gl.js a9c9e2aa36d4d5b63b131f27c1272f1a28883eeecc0eddd1e0dbfae4033bc7c9
get $J/maplibre-gl@5.24.1/dist/maplibre-gl.css maplibre-gl.css ab1e70d59ec40465bae7e7030da2f3ccf28133fd502e62bd598eefbadfd7a732
get $J/pmtiles@4.5.0/dist/pmtiles.js pmtiles.js caf981bc46f6327ee7e65d5dc964d89d38a69f60edca2bd4c5c890c21b554c6c
get $J/@protomaps/basemaps@5.7.2/dist/basemaps.js basemaps.js 551864f01da1bfe3005e99987197f22f56d0c35f9a10fa1727a889e2434af16d
TMP="$DEST/.assets-tmp"
rm -rf "$TMP" && mkdir -p "$TMP"
curl -fsSL --retry 3 "https://codeload.github.com/protomaps/basemaps-assets/tar.gz/$ASSETS_COMMIT" | tar xz -C "$TMP"
rm -rf "$DEST/fonts" "$DEST/sprites"
mkdir -p "$DEST/sprites"
mv "$TMP/basemaps-assets-$ASSETS_COMMIT/fonts" "$DEST/fonts"
mv "$TMP/basemaps-assets-$ASSETS_COMMIT/sprites/v4" "$DEST/sprites/v4"
rm -rf "$TMP"
printf 'maplibre-gl 5.24.1\npmtiles 4.5.0\n@protomaps/basemaps 5.7.2\nbasemaps-assets %s\n' "$ASSETS_COMMIT" > "$DEST/VERSIONS.txt"
echo "map viewer assets ready in $DEST"
