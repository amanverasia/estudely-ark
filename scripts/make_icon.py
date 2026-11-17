"""Usage: python scripts/make_icon.py REPO OUTDIR   (needs fonttools, brotli, pillow; copy OUTDIR/res into android/app/src/main/res)
   Estudely Ark icon: paper-coloured serif "A" (Source Serif 4, wght 600) over a rust ark hull, on the site's blue.
Writes the Android adaptive icon (vector), a 512 px Play icon, a 1024x500 feature graphic and an SVG preview."""
import io, os, sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
from PIL import Image, ImageDraw, ImageFont

repo, out = sys.argv[1], sys.argv[2]
BLUE, PAPER, RUST, INK = "#2B4C7E", "#F4F1EA", "#C2410C", "#1C1B19"

f = TTFont(os.path.join(repo, "site/public/fonts/source-serif-4-latin-opsz-normal.woff2"))
axes = {a.axisTag for a in f["fvar"].axes}
f = instantiateVariableFont(f, {k: v for k, v in {"wght": 600, "opsz": 36}.items() if k in axes})
f.flavor = None
ttf = os.path.join(out, "SourceSerif4-Semibold.ttf")
f.save(ttf)

gs = f.getGlyphSet()
gname = f.getBestCmap()[ord("A")]
bp = BoundsPen(gs); gs[gname].draw(bp)
xmin, ymin, xmax, ymax = bp.bounds

# Adaptive icon canvas 108 x 108 dp; keep everything inside the 66 dp safe circle around (54, 54).
H = 40.0                       # glyph height in dp
s = H / (ymax - ymin)
cx, base = 54.0, 67.0          # horizontal centre, baseline (y down)
tx = cx - (xmin + xmax) / 2 * s
sp = SVGPathPen(gs)
gs[gname].draw(TransformPen(sp, (s, 0, 0, -s, tx, base + ymin * s)))
A_PATH = sp.getCommands()
HULL = "M33,72 L75,72 Q54,90 33,72 Z"   # ark hull under the letter

def vec(fill_a, fill_h):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="{fill_a}" android:pathData="{A_PATH}"/>
    <path android:fillColor="{fill_h}" android:pathData="{HULL}"/>
</vector>
'''
res = os.path.join(out, "res")
for d in ["drawable", "mipmap-anydpi-v26", "values"]: os.makedirs(os.path.join(res, d), exist_ok=True)
open(os.path.join(res, "drawable/ic_launcher_foreground.xml"), "w").write(vec(PAPER, RUST))
open(os.path.join(res, "drawable/ic_launcher_monochrome.xml"), "w").write(vec("#FFFFFFFF", "#FFFFFFFF"))
open(os.path.join(res, "values/ic_launcher.xml"), "w").write(
    f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{BLUE}</color>\n</resources>\n')
open(os.path.join(res, "mipmap-anydpi-v26/ic_launcher.xml"), "w").write('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
''')

# SVG preview (rounded like a launcher mask) for review.
open(os.path.join(out, "icon-preview.svg"), "w").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" rx="24" fill="{BLUE}"/>'
    f'<path d="{A_PATH}" fill="{PAPER}"/><path d="{HULL}" fill="{RUST}"/></svg>\n')

# Raster: draw the same geometry with Pillow at any size.
def draw_mark(img, ox, oy, size):
    """Draw the A + hull for a 108-unit icon box placed at (ox, oy) with side `size` px."""
    k = size / 108.0
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(ttf, size=int(round(f["head"].unitsPerEm * s * k)))
    # Pillow places text by its ascender box; align the glyph's ink bbox to where the vector puts it.
    l, t, r, b = d.textbbox((0, 0), "A", font=font)
    gx0 = (tx + xmin * s) * k + ox; gy0 = (base - ymax * s) * k + oy
    d.text((gx0 - l, gy0 - t), "A", font=font, fill=PAPER)
    pts = [(33, 72), (75, 72)] + [((1 - u) ** 2 * 75 + 2 * (1 - u) * u * 54 + u * u * 33, (1 - u) ** 2 * 72 + 2 * (1 - u) * u * 90 + u * u * 72) for u in [i / 40 for i in range(41)]]
    d.polygon([(ox + x * k, oy + y * k) for x, y in pts], fill=RUST)

ss = 4  # supersample for smooth edges
img = Image.new("RGB", (512 * ss, 512 * ss), BLUE)
draw_mark(img, -512 * ss * 0.08, -512 * ss * 0.08, 512 * ss * 1.16)  # Play masks the icon; fill a bit more of it
img.resize((512, 512), Image.LANCZOS).save(os.path.join(out, "play-icon-512.png"))

W, Hh = 1024 * ss, 500 * ss
fg = Image.new("RGB", (W, Hh), BLUE)
draw_mark(fg, 40 * ss, 70 * ss, 360 * ss)
d = ImageDraw.Draw(fg)
title = ImageFont.truetype(ttf, 92 * ss)
d.text((410 * ss, 150 * ss), "Estudely Ark", font=title, fill=PAPER)
f2 = TTFont(os.path.join(repo, "site/public/fonts/source-serif-4-latin-opsz-normal.woff2"))
f2 = instantiateVariableFont(f2, {k: v for k, v in {"wght": 400, "opsz": 20}.items() if k in axes}); f2.flavor = None
reg = os.path.join(out, "SourceSerif4-Regular.ttf"); f2.save(reg)
sub = ImageFont.truetype(reg, 40 * ss)
d.text((414 * ss, 280 * ss), "A library that works", font=sub, fill=PAPER)
d.text((414 * ss, 332 * ss), "without the internet.", font=sub, fill=PAPER)
fg.resize((1024, 500), Image.LANCZOS).save(os.path.join(out, "feature-graphic-1024x500.png"))
print("glyph bounds", bp.bounds, "scale", round(s, 4), "path chars", len(A_PATH))
