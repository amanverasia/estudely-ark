#!/usr/bin/env python3
"""Assemble an Estudely Ark folder from a spec and a downloads directory.

Usage: python3 -I scripts/build_pack.py SPEC.json DOWNLOADS_DIR OUT_DIR

- Hardlinks payload files from DOWNLOADS_DIR (falls back to copying), so building costs no extra disk.
- Unpacks runner/reader archives (tar.gz/zip), stripping the archive's top folder.
- Copies src/pack-root launchers and renders START-HERE.html from the template + catalogue.
- Writes MANIFEST.tsv (path, bytes, sha256) for every file. Sign it separately (scripts/sign_manifest.sh).

Refuses to write into an existing OUT_DIR so a build is never mixed with an older one.
"""
import hashlib, json, os, shutil, stat, sys, tarfile, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PACK_ROOT_SRC = REPO / "src" / "pack-root"
MAPVIEWER_SRC = REPO / "src" / "mapviewer"


def place(src: Path, dest: Path, mode=None):
    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        os.link(src, dest)
    except OSError:
        shutil.copy2(src, dest)
    if mode:
        os.chmod(dest, int(mode, 8))


def unpack(archive: Path, dest: Path, only=None):
    dest.mkdir(parents=True, exist_ok=True)
    if archive.name.endswith((".tar.gz", ".tgz")):
        links = []
        with tarfile.open(archive) as t:
            for m in t.getmembers():
                parts = Path(m.name).parts
                if len(parts) < 2 or not (m.isfile() or m.issym()):
                    continue
                rel = Path(*parts[1:])
                if only and rel.name not in only:
                    continue
                if ".." in rel.parts:
                    raise SystemExit(f"unsafe path in {archive}: {m.name}")
                out = dest / rel
                out.parent.mkdir(parents=True, exist_ok=True)
                if m.issym():
                    if os.path.isabs(m.linkname) or ".." in Path(m.linkname).parts:
                        raise SystemExit(f"unsafe symlink in {archive}: {m.name} -> {m.linkname}")
                    links.append((out, m.linkname))
                else:
                    with t.extractfile(m) as f, open(out, "wb") as o:
                        shutil.copyfileobj(f, o)
                    os.chmod(out, 0o755 if m.mode & 0o111 else 0o644)
        # Symlinks become real copies: torrents, USB sticks (FAT/exFAT) and Windows handle them badly.
        for _ in range(5):
            for out, target in list(links):
                real = out.parent / target
                if real.is_file() and not real.is_symlink():
                    shutil.copy2(real, out); links.remove((out, target))
        if links:
            raise SystemExit(f"unresolved symlinks in {archive}: {links}")
    elif archive.name.endswith(".zip"):
        with zipfile.ZipFile(archive) as z:
            for info in z.infolist():
                parts = Path(info.filename).parts
                if info.is_dir() or not parts:
                    continue
                rel = Path(*parts[1:]) if len(parts) > 1 else Path(parts[0])
                if only and rel.name not in only:
                    continue
                if ".." in rel.parts or rel.is_absolute():
                    raise SystemExit(f"unsafe path in {archive}: {info.filename}")
                out = dest / rel
                out.parent.mkdir(parents=True, exist_ok=True)
                with z.open(info) as f, open(out, "wb") as o:
                    shutil.copyfileobj(f, o)
    else:
        raise SystemExit(f"don't know how to unpack {archive}")


def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    spec_path, dl, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    if out.exists():
        raise SystemExit(f"{out} already exists; remove it or choose another OUT_DIR")
    out.mkdir(parents=True)
    skipped = []

    for f in spec.get("files", []):
        src = dl / f["src"]
        if not src.exists():
            if f.get("optional"):
                skipped.append(f["src"]); continue
            raise SystemExit(f"missing download: {src}")
        place(src, out / f["dest"], f.get("mode"))

    for u in spec.get("unpack", []):
        src = dl / u["archive"]
        if not src.exists():
            if u.get("optional"):
                skipped.append(u["archive"]); continue
            raise SystemExit(f"missing download: {src}")
        unpack(src, out / u["dest"], u.get("only"))

    for d in spec.get("dirs", []):
        src = dl / d["src"]
        if src.is_dir():
            for p in src.rglob("*"):
                if p.is_file():
                    place(p, out / d["dest"] / p.relative_to(src))
        else:
            place(src, out / d["dest"])

    for rel, data in spec.get("generated", {}).items():
        (out / rel).parent.mkdir(parents=True, exist_ok=True)
        (out / rel).write_text(json.dumps(data, indent=1), encoding="utf-8")

    # Map viewer page.
    maps_mod = next((m for m in spec["modules"] if m["id"] == "maps"), None)
    if maps_mod:
        shutil.copy2(MAPVIEWER_SRC / "index.html", out / maps_mod["dir"] / "Desktop-Map" / "index.html")

    # Map fonts: one packed file instead of ~1,000 tiny ones (see scripts/pack_fonts.py).
    if spec.get("map_fonts") and maps_mod:
        import subprocess
        subprocess.run([sys.executable, "-I", str(REPO / "scripts" / "pack_fonts.py"), str(dl / spec["map_fonts"]),
                        str(out / maps_mod["dir"] / "Desktop-Map" / "fonts")], check=True)

    # Map place search index (GeoNames), see scripts/make_places.py.
    if spec.get("places") and maps_mod and (dl / spec["places"]["src"]).exists():
        import subprocess
        subprocess.run([sys.executable, "-I", str(REPO / "scripts" / "make_places.py"), str(dl / spec["places"]["src"]),
                        str(dl / spec["places"]["admin1"]), str(out / maps_mod["dir"] / "Desktop-Map" / "places.tsv")], check=True)

    # Launchers and pack-root documents. .command files let macOS users double-click the .sh launchers.
    for src in sorted(PACK_ROOT_SRC.iterdir()):
        if src.name.endswith(".template.html") or src.name.startswith("."):
            continue
        dst = out / src.name
        if src.suffix in (".bat", ".ps1", ".txt"):
            # Windows line endings: cmd.exe mis-handles labels in LF-only .bat files; Notepad on old Windows needs CRLF.
            dst.write_bytes(src.read_bytes().replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))
        else:
            shutil.copy2(src, dst)
        if src.suffix == ".sh":
            os.chmod(dst, 0o755)
            cmd = out / src.name.replace(".sh", ".command")
            cmd.write_text(f'#!/bin/sh\nexec "$(dirname "$0")/{src.name}" "$@"\n', encoding="utf-8", newline="\n")
            os.chmod(cmd, 0o755)

    # Catalogue: keep only entries whose files are actually present in this build.
    entries = []
    for e in spec["catalogue"]:
        paths = [out / p for p in e["paths"]]
        if not all(p.exists() for p in paths):
            skipped.append("catalogue: " + e["title"]); continue
        total = sum((q.stat().st_size for p in paths for q in ([p] if p.is_file() else p.rglob("*")) if q.is_file()))
        entries.append({k: e[k] for k in ("module", "title", "description", "topics", "open")} | {"bytes": total})
    mods = [{"id": m["id"], "title": m["title"]} for m in spec["modules"]]
    tpl = (PACK_ROOT_SRC / "START-HERE.template.html").read_text(encoding="utf-8")
    html = (tpl.replace("__CATALOGUE__", json.dumps(entries, ensure_ascii=False).replace("</", "<\\/"))
               .replace("__MODULES__", json.dumps(mods))
               .replace("__RELEASE__", spec["release"]))
    (out / "START-HERE.html").write_text(html, encoding="utf-8")

    # Manifest of every file (excluding the manifest and its signature).
    rows = []
    for p in sorted(out.rglob("*")):
        if p.is_symlink() or not p.is_file():
            continue
        rel = p.relative_to(out).as_posix()
        if rel.startswith("MANIFEST.tsv"):
            continue
        rows.append(f"{rel}\t{p.stat().st_size}\t{sha256(p)}")
    (out / "MANIFEST.tsv").write_text("# path\tbytes\tsha256\n" + "\n".join(rows) + "\n", encoding="utf-8", newline="\n")

    total = sum(int(r.split("\t")[1]) for r in rows)
    print(f"built {out}: {len(rows)} files, {total} bytes ({total/1e9:.2f} GB)")
    for s in skipped:
        print("  skipped (not downloaded):", s)


if __name__ == "__main__":
    main()
