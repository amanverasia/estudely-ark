#!/usr/bin/env python3
"""Build the desktop map's offline place-search index from GeoNames (CC BY 4.0).

  python3 -I scripts/make_places.py IN.txt admin1CodesASCII.txt OUT/places.tsv

Keeps populated places plus a few landmark types people search for (hospitals, clinics,
railway/bus stations, airports). Output: one line per place, most important first:
  name <TAB> lat <TAB> lon <TAB> state <TAB> kind <TAB> zoom
"""
import sys

src, admin1, out = sys.argv[1], sys.argv[2], sys.argv[3]
states = {}
for line in open(admin1, encoding="utf-8"):
    c = line.rstrip("\n").split("\t")
    if c[0].startswith("IN."):
        states[c[0][3:]] = c[1]

KIND = {  # feature code -> (label, zoom, importance tier). Score = population + tier * 100,000,
    # so administrative centres outrank same-named villages, and landmarks never outrank a real town.
    "PPLC": ("capital", 10, 50), "PPLA": ("state capital", 11, 40), "PPLA2": ("district HQ", 12, 30),
    "PPLA3": ("town", 13, 8), "PPLA4": ("town", 13, 6), "PPL": ("place", 14, 0), "PPLX": ("area", 15, 0),
    "PPLL": ("village", 15, 0), "PPLF": ("village", 15, 0), "PPLR": ("place", 15, 0), "PPLS": ("place", 15, 0),
    "HSP": ("hospital", 16, 1), "HSPC": ("clinic", 16, 1), "HSPD": ("dispensary", 16, 1), "CLIN": ("clinic", 16, 1),
    "RSTN": ("railway station", 16, 1), "RSTP": ("railway stop", 16, 0), "BUSTN": ("bus station", 16, 0),
    "AIRP": ("airport", 13, 3), "AIRF": ("airfield", 14, 0),
}
rows = []
for line in open(src, encoding="utf-8"):
    c = line.rstrip("\n").split("\t")
    code = c[7]
    if code not in KIND:
        continue
    label, zoom, bonus = KIND[code]
    name = c[1].replace("\t", " ").strip()
    if not name:
        continue
    pop = int(c[14] or 0)
    if pop:  # zoom out further for bigger places, whatever GeoNames calls them
        zoom = min(zoom, 11 if pop >= 1_000_000 else 12 if pop >= 100_000 else 13 if pop >= 10_000 else zoom)
    rows.append((-(pop + bonus * 100_000), name, round(float(c[4]), 4), round(float(c[5]), 4),
                 states.get(c[10], ""), label, zoom))
rows.sort()
with open(out, "w", encoding="utf-8", newline="\n") as f:
    for _, name, lat, lon, st, label, zoom in rows:
        f.write(f"{name}\t{lat}\t{lon}\t{st}\t{label}\t{zoom}\n")
print(f"{len(rows)} places -> {out}")
