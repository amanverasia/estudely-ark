#!/bin/sh
# Download everything for a v1 release build into release/downloads (resumable; re-run after a stop).
#   scripts/fetch_release.sh
# Reuses prototype downloads by hardlink. Skips files already present at the expected size.
set -u
cd "$(dirname "$0")/.." || exit 1
D=release/downloads
mkdir -p "$D"
python3 -I scripts/make_release_urls.py release/aria2-input.txt || exit 1

# Reuse prototype downloads (same bytes, no extra disk).
P=prototype/downloads
if [ -d "$P" ]; then
  for f in zim ai readers maps; do
    [ -d "$P/$f" ] && (cd "$P" && find "$f" -type f) | while read -r rel; do
      [ -e "$D/$rel" ] || { mkdir -p "$D/$(dirname "$rel")"; ln "$P/$rel" "$D/$rel"; }
    done
  done
  for pdf in "$P"/hesperian/*.pdf; do
    [ -f "$pdf" ] || continue
    b=$(basename "$pdf"); book=${b%_*}
    [ -e "$D/hesperian/$book/$b" ] || { mkdir -p "$D/hesperian/$book"; ln "$pdf" "$D/hesperian/$book/$b"; }
  done
fi

# Drop entries whose file is already complete (expected size known and matching, or no .aria2 control file).
python3 -I - "$D" release/aria2-input.txt <<'EOF'
import os, sys
d, inp = sys.argv[1], sys.argv[2]
sizes = dict(l.rstrip("\n").split("\t") for l in open(inp + ".sizes.tsv"))
blocks, cur = [], []
for line in open(inp):
    if not line.startswith(" ") and cur:
        blocks.append(cur); cur = []
    cur.append(line)
if cur: blocks.append(cur)
keep = []
for b in blocks:
    rel = [l.split("=", 1)[1].strip() for l in b if l.strip().startswith("out=")][0]
    p = os.path.join(d, rel)
    done = os.path.exists(p) and not os.path.exists(p + ".aria2") and (not sizes.get(rel) or os.path.getsize(p) == int(sizes[rel]))
    if not done: keep.append(b)
open(inp + ".todo", "w").write("".join("".join(b) for b in keep))
print(f"{len(blocks)-len(keep)} already present, {len(keep)} to fetch")
EOF

[ -f "$D/mapviewer/maplibre-gl.js" ] || scripts/fetch_mapviewer.sh "$D" || exit 1
# Place names for desktop map search (GeoNames, CC BY 4.0; updated daily upstream, so no fixed checksum).
if [ ! -f "$D/geonames/IN.txt" ]; then
  mkdir -p "$D/geonames"
  curl -fsSL -o "$D/geonames/IN.zip" https://download.geonames.org/export/dump/IN.zip &&
  curl -fsSL -o "$D/geonames/admin1CodesASCII.txt" https://download.geonames.org/export/dump/admin1CodesASCII.txt &&
  unzip -o -q "$D/geonames/IN.zip" IN.txt -d "$D/geonames" || exit 1
fi
# Desktop map tiles: India, zoom 14 (Aman's choice). Needs the pmtiles CLI (PMTILES=path).
[ -f "$D/maps/india-z14.pmtiles" ] || "${PMTILES:-prototype/tools/pmtiles}" extract https://build.protomaps.com/20261114.pmtiles "$D/maps/india-z14.pmtiles" \
  --bbox=68.0,6.5,97.5,37.1 --maxzoom=14 --download-threads=4 || exit 1

aria2c -i release/aria2-input.txt.todo -d "$D" --ca-certificate="${CA_BUNDLE:-/etc/ssl/cert.pem}" -j 4 -x 4 -s 4 -c \
  --auto-file-renaming=false --allow-overwrite=true --retry-wait=10 --max-tries=20 \
  --auto-save-interval=10 --summary-interval=120 --console-log-level=warn \
  --file-allocation=none
echo "aria2 exit $?"
