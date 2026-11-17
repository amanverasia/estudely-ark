#!/usr/bin/env python3
"""Create the release torrent: hybrid BitTorrent v1+v2 with BEP 47 pad files.

Every file starts on a piece boundary, so choosing one module downloads only that module
(a plain v1 torrent leaked partial neighbouring files in the prototype).

Needs libtorrent 2.x:  uv venv --python 3.13 tools/lt-venv && uv pip install --python tools/lt-venv/bin/python libtorrent==2.1.1
Usage:
  tools/lt-venv/bin/python scripts/make_torrent.py PACK_DIR OUT.torrent [--piece-kib 512]
      (--release-key SHA256:FP | --test) [--tracker URL ...] [--web-seed URL ...] [--comment TEXT]
PACK_DIR's folder name becomes the torrent's top-level folder. Hidden files (.DS_Store etc.) are skipped.
Prints the v1 and v2 info-hashes and a magnet link.
"""
import argparse, os, sys, time
import libtorrent as lt

ap = argparse.ArgumentParser()
ap.add_argument("pack")
ap.add_argument("out")
ap.add_argument("--piece-kib", type=int, default=512, help="piece size in KiB (power of two, >= 16). 512 keeps padding near 1% for the v1 pack")
ap.add_argument("--tracker", action="append", default=[])
ap.add_argument("--web-seed", action="append", default=[], help="BEP 19 URL; must end with / and serve the pack folder")
ap.add_argument("--comment", default="")
g = ap.add_mutually_exclusive_group(required=True)
g.add_argument("--release-key", metavar="SHA256:...", help="fingerprint of the real release key; refuses unless the pack is signed with it")
g.add_argument("--test", action="store_true", help="test torrent: skip the signing-key check")
a = ap.parse_args()

pack = os.path.abspath(a.pack.rstrip("/"))
if not os.path.isdir(pack):
    sys.exit(f"not a directory: {pack}")

if a.release_key:
    # Guard against publishing a pack still signed with a throwaway test key.
    import subprocess, tempfile
    signers = os.path.join(pack, "KEYS", "allowed_signers")
    line = open(signers, encoding="utf-8").read().split()
    with tempfile.NamedTemporaryFile("w", suffix=".pub") as k:
        k.write(" ".join(line[2:4]) + "\n"); k.flush()
        fp = subprocess.run(["ssh-keygen", "-lf", k.name], capture_output=True, text=True).stdout.split()
    if len(fp) < 2 or fp[1] != a.release_key:
        sys.exit(f"REFUSED: pack is signed by {fp[1] if len(fp) > 1 else '?'}, not the release key {a.release_key}")
    with open(os.path.join(pack, "MANIFEST.tsv"), "rb") as m:
        v = subprocess.run(["ssh-keygen", "-Y", "verify", "-f", signers, "-I", line[0], "-n", "estudely-ark",
                            "-s", os.path.join(pack, "MANIFEST.tsv.sig")], stdin=m, capture_output=True, text=True)
    if v.returncode != 0:
        sys.exit("REFUSED: MANIFEST.tsv.sig does not verify: " + (v.stderr or v.stdout).strip())
    print(f"release key OK: {a.release_key}; MANIFEST.tsv signature verifies")
psize = a.piece_kib * 1024
if psize & (psize - 1):
    sys.exit("--piece-kib must be a power of two")

fs = lt.file_storage()
skipped = 0
def want(path):
    global skipped
    name = os.path.basename(path)
    if name.startswith(".") or name.endswith((".aria2", ".part")):
        skipped += 1
        return False
    return True
lt.add_files(fs, pack, want)
if fs.num_files() == 0:
    sys.exit("no files")

# Default flags in libtorrent 2.x produce a hybrid v1+v2 torrent with pad files.
ct = lt.create_torrent(fs, psize)
ct.set_creator("Estudely Ark make_torrent.py (libtorrent %s)" % lt.__version__)
ct.set_creation_date(int(time.time()))
if a.comment:
    ct.set_comment(a.comment)
for i, t in enumerate(a.tracker):
    ct.add_tracker(t, i)
for w in a.web_seed:
    ct.add_url_seed(w)

t0 = time.time()
def progress(piece):
    if piece % 2000 == 0:
        print(f"  hashing piece {piece}/{ct.num_pieces()}", flush=True)
lt.set_piece_hashes(ct, os.path.dirname(pack), progress)
d = ct.generate()
# libtorrent names pad files ".pad/<size>", so two pads of the same size share a path.
# Transmission 4.1.4 rejects such torrents ("duplicate path"). Give each pad a unique name;
# pad contents are zeros either way, so piece hashes are unchanged.
for i, f in enumerate(d[b"info"].get(b"files", [])):
    if b"p" in f.get(b"attr", b""):
        f[b"path"] = [b".pad", b"%d" % i]
data = lt.bencode(d)
with open(a.out, "wb") as f:
    f.write(data)

ti = lt.torrent_info(a.out)
ih = ti.info_hashes()
pads = sum(1 for i in range(ti.files().num_files()) if ti.files().file_flags(i) & lt.file_storage.flag_pad_file)
real = ti.files().num_files() - pads
print(f"wrote {a.out}: {real} files, {pads} pad files, {ti.num_pieces()} pieces of {psize // 1024} KiB, "
      f"{ti.total_size() / 1e9:.3f} GB incl. padding, torrent file {len(data) / 1e6:.2f} MB, {time.time() - t0:.0f} s"
      + (f", skipped {skipped} hidden/partial files" if skipped else ""))
print("v1 info-hash:", ih.v1)
print("v2 info-hash:", ih.v2)
print("magnet:", lt.make_magnet_uri(ti))
