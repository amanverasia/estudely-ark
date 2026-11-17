#!/usr/bin/env python3
"""Pack MapLibre glyph files (fonts/<fontstack>/<range>.pbf) into one file plus an index.

  python3 -I scripts/pack_fonts.py FONTS_DIR OUT_DIR

Writes OUT_DIR/glyphs.bin and OUT_DIR/glyphs.json ({"<fontstack>/<range>": [offset, length]}).
Why: about 1,000 tiny files each need their own piece in the release torrent, and clients without
pad-file support (Transmission 4.1) store the padding on disk. One file avoids both. The map page
reads the pieces back with HTTP Range requests (miniserve supports them).
Non-.pbf files (licence text) are copied alongside unchanged.
"""
import json, os, shutil, sys

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
index, off = {}, 0
with open(os.path.join(out, "glyphs.bin"), "wb") as b:
    for stack in sorted(os.listdir(src)):
        d = os.path.join(src, stack)
        if not os.path.isdir(d):
            shutil.copy(d, os.path.join(out, stack))
            continue
        for name in sorted(os.listdir(d)):
            if not name.endswith(".pbf"):
                continue
            data = open(os.path.join(d, name), "rb").read()
            index[f"{stack}/{name[:-4]}"] = [off, len(data)]
            b.write(data)
            off += len(data)
json.dump(index, open(os.path.join(out, "glyphs.json"), "w", encoding="utf-8"), separators=(",", ":"))
print(f"{len(index)} glyph files, {off} bytes -> {out}/glyphs.bin")
