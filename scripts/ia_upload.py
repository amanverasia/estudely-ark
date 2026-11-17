#!/usr/bin/env python3
"""Upload a signed pack to the Internet Archive so it can act as the torrent's web seed (BEP 19).

  ~/.local/share/uv/tools/internetarchive/bin/python -I scripts/ia_upload.py PACK_DIR IDENTIFIER [--jobs=4] [--done-log=FILE ...] [--dry-run]

Needs `uv tool install internetarchive` and S3 keys in ~/.config/internetarchive/ia.ini.
Every file goes to IDENTIFIER/<pack folder name>/<path inside the pack>, with names unchanged, because a
web seed of https://archive.org/download/IDENTIFIER/ is fetched as <that>/<torrent name>/<path>.
(`ia upload --keep-directories` got this wrong: it dropped the slash after the folder name.)
Resumable: files already in the item with the same MD5 are skipped. Uploads --jobs files at a time (default 4). Derive is off (no OCR copies of PDFs).
"""
import os, sys, threading, time
from internetarchive import get_item

pack, ident = os.path.abspath(sys.argv[1].rstrip("/")), sys.argv[2]
dry = "--dry-run" in sys.argv
JOBS = int(next((x.split("=", 1)[1] for x in sys.argv if x.startswith("--jobs=")), "4"))
top = os.path.basename(pack)

files = {}
for root, dirs, names in os.walk(pack):
    dirs[:] = sorted(d for d in dirs if not d.startswith("."))
    for n in sorted(names):
        if n.startswith("."):
            continue
        p = os.path.join(root, n)
        files[top + "/" + os.path.relpath(p, pack).replace(os.sep, "/")] = p
total = sum(os.path.getsize(p) for p in files.values())
print(f"{len(files)} files, {total / 1e9:.2f} GB -> https://archive.org/details/{ident}/{top}/", flush=True)

metadata = {
    "mediatype": "data",
    "collection": "opensource_media",
    "title": "Estudely Ark v1.0 (November 2026): free offline knowledge for India",
    "description": (
        "A free, offline collection of useful knowledge for India: Simple English Wikipedia, Wikipedia Medicine, "
        "Hesperian health books, water and food guides, repair manuals, full India maps for computers and Android, "
        "school science and textbooks, programming manuals, and an optional local AI assistant. Works with no internet "
        "on Windows, macOS and Linux. Open README.txt first. Run Verify to check your copy against the project key "
        "SHA256:PAm9fPRpBLPaPXZVXO3g+ixxl4QW/LSjIhvWDe7uZ4E. Non-commercial: some included works (Hesperian, PhET, "
        "iFixit, most OpenStax books) may not be sold. Licences and attribution: CREDITS.txt. Website: https://ark.esdy.cc"
    ),
    "subject": ["offline", "india", "kiwix", "wikipedia", "health", "maps", "education", "estudely ark"],
    "language": "eng",
    "creator": "Estudely Ark",
}

if dry:
    for k in list(files)[:5]:
        print(" ", k)
    sys.exit(0)

item = get_item(ident)
have = {f["name"]: f.get("md5") for f in (item.files or [])}
import hashlib
def md5(p):
    h = hashlib.md5()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 22), b""):
            h.update(b)
    return h.hexdigest()

done = sent = 0
t0 = time.time()
first = not item.exists
todo = []
# Files an earlier run logged as accepted (HTTP 200) count as done even before the Archive lists them:
# it adds uploads to the item's file list only after its own processing queue catches up.
accepted = set()
for log in (x.split("=", 1)[1] for x in sys.argv if x.startswith("--done-log=")):
    for line in open(log, encoding="utf-8"):
        k = line.rstrip("\n").rsplit("  ", 1)[-1]
        if k.startswith(top + "/") and "GB" in line:
            accepted.add(k)
for key, path in files.items():
    size = os.path.getsize(path)
    if key in accepted or (key in have and have[key] == md5(path)):
        done += size
    else:
        todo.append((key, path, size))
print(f"already on the Archive: {done / 1e9:.2f} GB; to upload: {len(todo)} files, {sum(s for _, _, s in todo) / 1e9:.2f} GB, {JOBS} at a time", flush=True)

def put(key, path, meta):
    it = get_item(ident)  # one session per thread
    for attempt in range(1, 6):
        try:
            r = it.upload({key: path}, metadata=meta, queue_derive=False,
                          retries=20, retries_sleep=30, verbose=False, validate_identifier=True)
            if r and all(x.status_code == 200 for x in r):
                return
            print(f"  retry {attempt} {key}: {[x.status_code for x in r]}", flush=True)
        except Exception as e:
            print(f"  retry {attempt} {key}: {e}", flush=True)
        time.sleep(60)
    raise RuntimeError(f"FAILED {key}")

if first and todo:  # the first upload creates the item and sets its metadata
    k, p, s = todo.pop(0)
    put(k, p, metadata); done += s; sent += s
    print(f"{done / 1e9:7.2f}/{total / 1e9:.2f} GB  {k}", flush=True)

from concurrent.futures import ThreadPoolExecutor, as_completed
lock = threading.Lock()
failed = []
with ThreadPoolExecutor(max_workers=JOBS) as pool:
    futs = {pool.submit(put, k, p, None): (k, s) for k, p, s in todo}
    for f in as_completed(futs):
        k, s = futs[f]
        try:
            f.result()
        except Exception as e:
            failed.append(k); print(e, flush=True); continue
        with lock:
            done += s; sent += s
            rate = sent / max(time.time() - t0, 1)
            print(f"{done / 1e9:7.2f}/{total / 1e9:.2f} GB  {rate / 1e6:5.2f} MB/s  {k}", flush=True)
if failed:
    sys.exit(f"{len(failed)} files failed; run again to retry them")
print("upload complete", flush=True)
