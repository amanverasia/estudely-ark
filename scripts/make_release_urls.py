#!/usr/bin/env python3
"""Write an aria2c input file for every v1 release download (tiers core, extra, ai).

Usage: python3 -I scripts/make_release_urls.py OUT_FILE
Run from the repo root. Each entry gets out= (path under the downloads dir) and, where
upstream publishes one, checksum=sha-256=... so aria2 refuses corrupt files.
Also writes OUT_FILE.sizes.tsv (relpath, expected bytes or empty) for a later size check.
Kiwix URLs are resolved to a mirror (needs curl and network).
Protomaps India is not here: it is cut with `pmtiles extract` (see scripts/fetch_release.sh).
"""
import csv, json, subprocess, sys


def resolve(url):
    """download.kiwix.org answers aria2 with a metalink file instead of the data. Resolve to the mirror first."""
    if "download.kiwix.org/" not in url:
        return url
    r = subprocess.run(["curl", "-sIL", "-o", "/dev/null", "-w", "%{url_effective}", url], capture_output=True, text=True)
    return r.stdout.strip() or url

# Listed by Hesperian but 404 upstream (checked 16 Nov 2026).
MISSING_UPSTREAM = ("en_hcwd_2026_bm.pdf", "en_midw_2026_bm.pdf")
OM_VER = "261110"  # must equal "v" in the APK's assets/countries.json (research/om_countries.json is that file)
LLAMA = "b12379"
MINISERVE = "0.35.0"
MINISERVE_ASSETS = {  # pack platform -> (asset, sha256 from the GitHub release API)
    "macos-arm64": ("aarch64-apple-darwin", "8e8dc916b1dc3bc2a46bf5a44308caf7db153e6940db178668929e1de40d1fbb"),
    "macos-x64": ("x86_64-apple-darwin", "63a99448bf7c450f1264a1af313646df30251bdbc3284b8f4d2d55cee2b98c55"),
    "linux-x64": ("x86_64-unknown-linux-musl", "c630ee030d5d9d83c88c5cc72f43ae215b0c214d64fd7afc92244fe369af2964"),
    "linux-arm64": ("aarch64-unknown-linux-musl", "732b1d5b30a3903580cee9ca24b2ff04ba2a5c78fa1ba6579dc5154fc431b40a"),
    "windows-x64": ("x86_64-pc-windows-msvc.exe", "3229e75b40b3870382d23c9a0ecec880b9bd5f6e49160ce602bb15d00f275b6a"),
}

out_path = sys.argv[1]
entries = []  # (relpath, url, sha256 or "", bytes or "")

zim_sha = {}
for line in open("research/zim_sizes.tsv", encoding="utf-8"):
    p, b, s = line.rstrip("\n").split("\t")[:3]
    zim_sha[p.split("/")[-1]] = (s, b)

rows = list(csv.DictReader(open("manifest/candidates.tsv", encoding="utf-8"), delimiter="\t"))
for r in rows:
    if r["tier"] not in ("core", "extra", "ai"):
        continue
    url, i = r["source_url"], r["id"]
    if url.endswith(".zim"):
        name = url.rsplit("/", 1)[1]
        s, b = zim_sha.get(name, ("", r["bytes"]))
        entries.append((f"zim/{name}", url, s, b))
    elif i.startswith("kiwix_") or i.startswith("llamacpp_"):
        name = url.rsplit("/", 1)[1]
        sub = "readers" if i.startswith("kiwix_") else "ai"
        entries.append((f"{sub}/{name}", url, "", r["bytes"]))
    elif i.startswith("gemma4_"):
        url = url.replace("/blob/", "/resolve/")
        entries.append((f"ai/{url.rsplit('/', 1)[1]}", url, r["sha256"], r["bytes"]))
    elif i.startswith("openstax_"):
        entries.append((f"openstax/{i[len('openstax_'):]}.pdf", url, "", r["bytes"]))
    elif i == "organicmaps_apk":
        name = "OrganicMaps-26111139-web-release.apk"
        entries.append((f"maps/{name}", url.replace("/tag/", "/download/") + "/" + name, r["sha256"], r["bytes"]))
    elif i == "organicmaps_india":
        d = json.load(open("research/om_countries.json", encoding="utf-8"))
        def walk(n, ind=False):
            ind = ind or n.get("id") == "India"
            for c in n.get("g", []):
                yield from walk(c, ind)
            if "g" not in n and ind:
                yield n
        for n in walk(d):
            entries.append((f"maps/{OM_VER}/{n['id']}.mwm", f"https://cdn.organicmaps.app/maps/{OM_VER}/{n['id'].replace(' ', '%20')}.mwm", "", str(n["s"])))
        for w in ("World", "WorldCoasts"):
            entries.append((f"maps/{OM_VER}/{w}.mwm", f"https://cdn.organicmaps.app/maps/{OM_VER}/{w}.mwm", "", ""))
    elif i == "hesperian_en_all":
        for u in open("research/hesperian_urls.txt", encoding="utf-8"):
            u = u.strip()
            if u and not u.endswith(MISSING_UPSTREAM):
                book = u.rstrip("/").split("/")[-2]
                entries.append((f"hesperian/{book}/{u.rsplit('/', 1)[1]}", u, "", ""))

for plat, (asset, sha) in MINISERVE_ASSETS.items():
    name = f"miniserve-{MINISERVE}-{asset}"
    entries.append((f"readers/{name}", f"https://github.com/svenstaro/miniserve/releases/download/v{MINISERVE}/{name}", sha, ""))

seen = set()
with open(out_path, "w", encoding="utf-8") as f, open(out_path + ".sizes.tsv", "w", encoding="utf-8") as g:
    for rel, url, sha, b in entries:
        if rel in seen:
            continue
        seen.add(rel)
        f.write(f"{resolve(url)}\n  out={rel}\n")
        if sha:
            f.write(f"  checksum=sha-256={sha}\n")
        g.write(f"{rel}\t{b}\n")
print(f"{len(seen)} downloads -> {out_path}")
