#!/usr/bin/env python3
"""Generate the v1 release build spec (for scripts/build_pack.py) from what is in the downloads dir.

  python3 -I scripts/make_release_spec.py DOWNLOADS_DIR OUT_SPEC.json [RELEASE_NAME]

Only items listed in manifest/candidates.tsv with tier core/extra/ai are placed. Missing downloads
are marked optional, so a partial download still builds (and the build prints what was skipped).
"""
import csv, json, os, re, sys
from pathlib import Path

dl, out = Path(sys.argv[1]), Path(sys.argv[2])
release = sys.argv[3] if len(sys.argv) > 3 else "v1 test build (not for distribution)"

H = "Core/02-Health-and-Sanitation"
MAPS = "Core/05-Maps"
MODULES = [
    ("general", "Core/01-General-Knowledge", "General knowledge"),
    ("health", H, "Health and sanitation"),
    ("food", "Core/03-Food-and-Growing", "Food and growing"),
    ("repair", "Core/04-Repair-and-Practical-Skills", "Repair and practical skills"),
    ("maps", MAPS, "Maps"),
    ("edu", "Core/06-Education-and-Books", "Education"),
    ("computing", "Core/07-Computing-and-Documentation", "Computing"),
    ("extras", "Extras", "Extras (optional)"),
    ("ai", "AI", "AI assistant (optional)"),
    ("readers", "Shared-Readers", "Reader programs"),
]
MOD_BY_DIR = {d: i for i, d, _ in MODULES}

# Catalogue text per ZIM (key: name without date).
ZIM_INFO = {
    "wikipedia_en-simple_all_maxi": ("Simple English Wikipedia", "Over 250,000 encyclopedia articles in plain English, with pictures. Search for almost anything.", ["encyclopedia", "wikipedia", "history", "science", "geography", "people"]),
    "theworldfactbook_en_all": ("CIA World Factbook", "Facts about every country: people, government, economy, maps and flags.", ["countries", "geography", "flags", "population"]),
    "wikipedia_en_medicine_nopic": ("Wikipedia Medicine", "About 70,000 medical articles: diseases, symptoms, medicines, anatomy and first aid. Text only.", ["medicine", "disease", "symptoms", "drugs", "first aid", "health"]),
    "zimgit-medicine_en": ("Medicine library", "Practical medical guides and manuals for low-resource settings.", ["medicine", "first aid", "health", "nursing"]),
    "zimgit-water_en": ("Water Treatment Library", "Guides to making water safe to drink: filtering, boiling, disinfecting, storing.", ["water", "sanitation", "purification", "hygiene"]),
    "zimgit-post-disaster_en": ("Post-disaster library", "Shelter, sanitation, health and recovery after floods, earthquakes and other disasters.", ["disaster", "emergency", "shelter", "flood", "earthquake", "survival"]),
    "zimgit-food-preparation_en": ("Food preparation library", "Cooking, preserving and storing food safely.", ["food", "cooking", "preserving", "storage", "nutrition"]),
    "gardenology.org_en_all": ("Gardenology", "Plant encyclopedia: growing vegetables, fruit, herbs and flowers.", ["gardening", "plants", "farming", "vegetables", "growing"]),
    "zimgit-knots_en": ("Knots", "How to tie useful knots, step by step.", ["knots", "rope", "practical"]),
    "restarters_en_all_maxi": ("Restarters repair wiki", "Fixing everyday electrical and electronic things.", ["repair", "electronics", "fixing"]),
    "appropedia_en_all_maxi": ("Appropedia", "Appropriate technology: solar, water, building, cooking, farming and more, for low-cost living.", ["technology", "solar", "building", "energy", "diy", "sustainability"]),
    "energypedia_en_all_maxi": ("Energypedia", "Renewable energy in practice: solar, biogas, cooking stoves, small hydro.", ["energy", "solar", "biogas", "electricity"]),
    "ifixit_en_all": ("iFixit repair guides", "Illustrated step-by-step repair guides for phones, laptops, appliances, cars and more.", ["repair", "phone", "laptop", "appliance", "car", "fixing"]),
    "phet_en_all": ("PhET science simulations", "Interactive maths and science simulations from the University of Colorado.", ["science", "physics", "chemistry", "maths", "school"]),
    "wikipedia_en_mathematics_mini": ("Wikipedia Mathematics", "Introductions to mathematics topics.", ["maths", "mathematics", "school"]),
    "wikipedia_en_physics_mini": ("Wikipedia Physics", "Introductions to physics topics.", ["physics", "science", "school"]),
    "wikipedia_en_chemistry_mini": ("Wikipedia Chemistry", "Introductions to chemistry topics.", ["chemistry", "science", "school"]),
    "freecodecamp_en_all": ("freeCodeCamp", "Learn to program: web development, JavaScript, Python and more, with exercises.", ["programming", "coding", "learn", "web"]),
    "libretexts.org_en_k12": ("LibreTexts school textbooks (K-12)", "Free school textbooks: maths, science, English and social studies, up to class 12 level.", ["school", "textbook", "maths", "science", "class", "study"]),
    "archlinux_en_all_maxi": ("ArchWiki", "Detailed Linux documentation, useful for any Linux system.", ["linux", "computer", "system"]),
}
DEVDOCS = {"python": "Python", "html": "HTML", "css": "CSS", "javascript": "JavaScript", "dom": "Web DOM APIs", "git": "Git",
           "sqlite": "SQLite", "bash": "Bash", "man": "Linux man pages", "http": "HTTP", "markdown": "Markdown", "c": "C language"}

HESPERIAN = {
    "en_wtnd_2025": "Where There Is No Doctor", "en_nwtnd_2011": "New Where There Is No Doctor",
    "en_wwhnd_2024": "Where Women Have No Doctor", "en_dent_2024": "Where There Is No Dentist",
    "en_midw_2026": "A Book for Midwives", "en_cgeh_2012": "A Community Guide to Environmental Health",
    "en_dvc_2025": "Disabled Village Children", "en_easb_2026": "Epilepsy and Seizures",
    "en_haw_2015": "Health Actions for Women", "en_hclhiv_2019": "Helping Children Live with HIV",
    "en_hcwb_2000": "Helping Children Who Are Blind", "en_hcwd_2026": "Helping Children Who Are Deaf",
    "en_hhwl_2026": "Helping Health Workers Learn", "en_pcmh_2025": "Promoting Community Mental Health",
    "en_wgthas_2015": "Workers' Guide to Health and Safety", "en_wwd_2008": "A Health Handbook for Women with Disabilities",
}
HESPERIAN_TOPICS = {"en_wtnd_2025": ["doctor", "first aid", "medicine", "illness"], "en_nwtnd_2011": ["doctor", "first aid", "medicine"],
                    "en_wwhnd_2024": ["women", "pregnancy", "health"], "en_dent_2024": ["teeth", "dentist"],
                    "en_midw_2026": ["birth", "pregnancy", "midwife", "baby"], "en_cgeh_2012": ["water", "sanitation", "toilets", "environment"],
                    "en_dvc_2025": ["disability", "children"], "en_easb_2026": ["epilepsy", "seizures", "fits"],
                    "en_haw_2015": ["women", "community"], "en_hclhiv_2019": ["hiv", "children"], "en_hcwb_2000": ["blind", "children"],
                    "en_hcwd_2026": ["deaf", "children"], "en_hhwl_2026": ["teaching", "health worker"],
                    "en_pcmh_2025": ["mental health", "trauma", "depression"], "en_wgthas_2015": ["work", "safety", "factory"],
                    "en_wwd_2008": ["disability", "women"]}

OM_VER = "261110"  # Organic Maps data version; must match scripts/make_release_urls.py and the APK
LLAMA = {"macos-arm64": "macos-arm64.tar.gz", "macos-x64": "macos-x64.tar.gz", "linux-x64": "ubuntu-x64.tar.gz",
         "linux-arm64": "ubuntu-arm64.tar.gz", "linux-vulkan-x64": "ubuntu-vulkan-x64.tar.gz",
         "linux-vulkan-arm64": "ubuntu-vulkan-arm64.tar.gz", "windows-cpu-x64": "win-cpu-x64.zip",
         "windows-cpu-arm64": "win-cpu-arm64.zip", "windows-vulkan-x64": "win-vulkan-x64.zip",
         "windows-vulkan-arm64": "win-vulkan-arm64.zip"}
KIWIX_TOOLS = {"macos-arm64": "macos-arm64", "macos-x64": "macos-x86_64", "linux-x64": "linux-x86_64", "linux-arm64": "linux-aarch64"}
MINISERVE = {"macos-arm64": "aarch64-apple-darwin", "macos-x64": "x86_64-apple-darwin", "linux-x64": "x86_64-unknown-linux-musl",
             "linux-arm64": "aarch64-unknown-linux-musl", "windows-x64": "x86_64-pc-windows-msvc.exe"}

files, unpack, catalogue = [], [], []
def f(src, dest, mode=None):
    e = {"src": src, "dest": dest}
    if mode: e["mode"] = mode
    if not (dl / src).exists(): e["optional"] = True
    files.append(e)
def u(archive, dest, only=None):
    e = {"archive": archive, "dest": dest}
    if only: e["only"] = only
    if not (dl / archive).exists(): e["optional"] = True
    unpack.append(e)
def cat(module, title, desc, topics, paths, opens):
    catalogue.append({"module": module, "title": title, "description": desc, "topics": topics, "paths": paths, "open": opens})

# ZIM archives.
rows = list(csv.DictReader(open("manifest/candidates.tsv", encoding="utf-8"), delimiter="\t"))
for r in rows:
    if r["tier"] not in ("core", "extra") or not r["source_url"].endswith(".zim"):
        continue
    name = r["source_url"].rsplit("/", 1)[1]
    base = re.sub(r"_\d{4}-\d{2}\.zim$", "", name)
    if r["tier"] == "extra":
        d = "Extras/" + {"ifixit_en_all": "iFixit", "energypedia_en_all_maxi": "Energypedia"}[base]
        mod = "extras"
    else:
        d = "Core/" + r["module"]; mod = MOD_BY_DIR[d]
    f(f"zim/{name}", f"{d}/{name}")
    if base.startswith("devdocs_en_"):
        k = base[len("devdocs_en_"):]
        title, desc, topics = (f"{DEVDOCS[k]} documentation", f"Reference manual for {DEVDOCS[k]}.", ["programming", "coding", k])
    else:
        title, desc, topics = ZIM_INFO[base]
    cat(mod, title, desc, topics, [f"{d}/{name}"], [{"label": "Open", "via": "library", "target": base}])

# OpenStax textbooks (PDF, one file per book).
OPENSTAX_TOPICS = {
    "prealgebra-2e": ["maths", "arithmetic", "fractions", "school"], "elementary-algebra-2e": ["maths", "algebra", "school"],
    "introductory-statistics-2e": ["maths", "statistics", "data"], "physics": ["physics", "science", "school"],
    "chemistry-2e": ["chemistry", "science"], "concepts-biology": ["biology", "science", "life"],
    "fundamentals-nursing": ["nursing", "patient care", "health"], "clinical-nursing-skills": ["nursing", "procedures", "health"],
    "pharmacology": ["medicines", "drugs", "dose", "nursing"], "maternal-newborn-nursing": ["pregnancy", "birth", "baby", "nursing"],
    "nutrition-nurses": ["nutrition", "food", "diet", "health"], "population-health-nurses": ["public health", "community", "nursing"],
    "psychiatric-mental-health": ["mental health", "nursing"], "medical-surgical-nursing": ["nursing", "illness", "surgery"],
    "principles-economics-3e": ["economics", "money", "business"], "psychology-2e": ["psychology", "mind", "behaviour"],
    "writing-guide": ["writing", "english", "essay"], "introduction-python-programming": ["python", "programming", "coding"],
}
for r in rows:
    if not r["id"].startswith("openstax_") or r["tier"] != "core":
        continue
    slug = r["id"][len("openstax_"):]
    title = r["title"].split(": ", 1)[1]
    d = "Core/" + r["module"]
    dest = f"{d}/OpenStax/{re.sub(r'[^A-Za-z0-9]+', '-', title).strip('-')}.pdf"
    f(f"openstax/{slug}.pdf", dest)
    nursing = r["module"].startswith("02")
    cat(MOD_BY_DIR[d], f"{title} (OpenStax)", ("Free nursing textbook (PDF)." if nursing else "Free textbook (PDF).") + " From OpenStax, Rice University.",
        OPENSTAX_TOPICS.get(slug, []) + ["textbook", "book"], [dest], [{"label": "Open book (PDF)", "via": "files", "target": dest}])

# Hesperian books.
books = {}
for line in open("research/hesperian_urls.txt", encoding="utf-8"):
    line = line.strip()
    if line:
        code = line.split("/")[-2]; fn = line.rsplit("/", 1)[1]
        books.setdefault(code, []).append(fn)
FIRST = ["en_wtnd_2025", "en_wwhnd_2024", "en_midw_2026", "en_dent_2024", "en_cgeh_2012", "en_nwtnd_2011"]  # most-needed first
for code, fns in sorted(books.items(), key=lambda kv: (FIRST.index(kv[0]) if kv[0] in FIRST else len(FIRST), HESPERIAN[kv[0]])):
    title = HESPERIAN[code]
    year = code.rsplit("_", 1)[1]
    folder = f"{H}/Hesperian/{re.sub(r'[^A-Za-z0-9]+', '-', title).strip('-')}-{year}"
    for fn in fns:
        if (dl / "hesperian" / code / fn).exists():
            f(f"hesperian/{code}/{fn}", f"{folder}/{fn}")
    cat("health", f"{title} ({year})", f"Hesperian health guide, one PDF per chapter.", HESPERIAN_TOPICS[code] + ["health", "hesperian", "book"],
        [folder], [{"label": "Chapters (PDF)", "via": "files", "target": folder + "/"}])

# Maps.
om = sorted(p.name for p in (dl / f"maps/{OM_VER}").glob("*.mwm"))
for n in om:
    f(f"maps/{OM_VER}/{n}", f"{MAPS}/Android-Organic-Maps/{OM_VER}/{n}")
f("maps/india-z14.pmtiles", f"{MAPS}/Desktop-Map/india.pmtiles")
cat("maps", "Map of India (computer)", "Street map of all of India that works in your web browser. Zoom in to any town.",
    ["map", "india", "streets", "roads", "town", "city"], [f"{MAPS}/Desktop-Map/india.pmtiles"],
    [{"label": "Open map", "via": "needs-files-server", "target": f"{MAPS}/Desktop-Map/index.html"}])
cat("maps", "Map of India for Android (Organic Maps)", "Install Organic Maps from Shared-Readers/android, then copy these map files to the phone (see README.txt). Search and directions work offline.",
    ["map", "android", "phone", "directions", "routing", "navigation"], [f"{MAPS}/Android-Organic-Maps"],
    [{"label": "Map files", "via": "files", "target": f"{MAPS}/Android-Organic-Maps/"}])

# Readers.
for plat, a in KIWIX_TOOLS.items():
    u(f"readers/kiwix-tools_{a}-3.8.1.tar.gz", f"Shared-Readers/kiwix-serve/{plat}", ["kiwix-serve"])
u("readers/kiwix-tools_win-x86_64-3.8.1.zip", "Shared-Readers/kiwix-serve/windows-x64",
  ["kiwix-serve.exe", "icudt74.dll", "icuin74.dll", "icuio74.dll", "icutu74.dll", "icuuc74.dll"])  # .exe needs the ICU DLLs beside it
for plat, a in MINISERVE.items():
    exe = ".exe" if a.endswith(".exe") else ""
    f(f"readers/miniserve-0.35.0-{a}", f"Shared-Readers/miniserve/{plat}/miniserve{exe}", None if exe else "755")
f("readers/kiwix-desktop_windows_x64_2.5.1.zip", "Shared-Readers/kiwix-app/kiwix-desktop_windows_x64_2.5.1.zip")
f("readers/kiwix-desktop_x86_64_2.5.1.appimage", "Shared-Readers/kiwix-app/kiwix-desktop_x86_64_2.5.1.appimage", "755")
f("readers/kiwix-macos_3.9.0.dmg", "Shared-Readers/kiwix-app/kiwix-macos_3.9.0.dmg")
f("readers/org.kiwix.kiwixmobile.standalone-v3.14.1.apk", "Shared-Readers/android/org.kiwix.kiwixmobile.standalone-v3.14.1.apk")
f("maps/OrganicMaps-26111139-web-release.apk", "Shared-Readers/android/OrganicMaps-26111139-web-release.apk")
cat("readers", "Reader apps (Kiwix, Organic Maps)", "Install these to read the library without the browser: Kiwix for Windows, Mac, Linux and Android; Organic Maps for Android.",
    ["app", "install", "android", "kiwix", "reader"], ["Shared-Readers"], [{"label": "Programs folder", "via": "files", "target": "Shared-Readers/"}])

# AI.
for plat, a in LLAMA.items():
    u(f"ai/llama-b12379-bin-{a}", f"AI/Runner-and-Instructions/llama.cpp/{plat}")
f("ai/gemma-4-E4B_q4_0-it.gguf", "AI/Gemma-4-E4B/gemma-4-E4B_q4_0-it.gguf")
f("ai/gemma-4-E4B-it-mmproj.gguf", "AI/Gemma-4-E4B/gemma-4-E4B-it-mmproj.gguf")
f("ai/gemma-4-12b-it-qat-q4_0.gguf", "AI/Gemma-4-12B/gemma-4-12b-it-qat-q4_0.gguf")
f("ai/mmproj-gemma-4-12b-it-qat-q4_0.gguf", "AI/Gemma-4-12B/mmproj-gemma-4-12b-it-qat-q4_0.gguf")
cat("ai", "AI assistant: Gemma 4 E4B (smaller)", "A small AI that runs on this computer. Start it with Start-AI, then open it here. It can be wrong: check health and safety answers in the books.",
    ["ai", "assistant", "chat", "questions"], ["AI/Gemma-4-E4B/gemma-4-E4B_q4_0-it.gguf"], [{"label": "Open AI chat (after Start-AI)", "via": "ai", "target": ""}])
cat("ai", "AI assistant: Gemma 4 12B (larger)", "A larger, slower AI for computers with plenty of memory. Start it with Start-AI 12b. It can be wrong.",
    ["ai", "assistant", "chat", "questions"], ["AI/Gemma-4-12B/gemma-4-12b-it-qat-q4_0.gguf"], [{"label": "Open AI chat (after Start-AI 12b)", "via": "ai", "target": ""}])

spec = {
    "release": release,
    "modules": [{"id": i, "dir": d, "title": t} for i, d, t in MODULES],
    "files": files, "unpack": unpack,
    "dirs": [
        {"src": "mapviewer/sprites/v4", "dest": f"{MAPS}/Desktop-Map/sprites/v4"},
        {"src": "mapviewer/maplibre-gl.js", "dest": f"{MAPS}/Desktop-Map/lib/maplibre-gl.js"},
        {"src": "mapviewer/maplibre-gl.css", "dest": f"{MAPS}/Desktop-Map/lib/maplibre-gl.css"},
        {"src": "mapviewer/pmtiles.js", "dest": f"{MAPS}/Desktop-Map/lib/pmtiles.js"},
        {"src": "mapviewer/basemaps.js", "dest": f"{MAPS}/Desktop-Map/lib/basemaps.js"},
    ],
    "map_fonts": "mapviewer/fonts",
    "places": {"src": "geonames/IN.txt", "admin1": "geonames/admin1CodesASCII.txt"},
    "generated": {f"{MAPS}/Desktop-Map/maps.json": [{"file": "india.pmtiles", "name": "India", "center": [78.96, 22.59], "zoom": 4.5}]},
    "catalogue": catalogue,
}
out.write_text(json.dumps(spec, indent=1, ensure_ascii=False), encoding="utf-8")
miss = sum(1 for e in files + unpack if e.get("optional"))
print(f"{len(files)} files, {len(unpack)} archives, {len(catalogue)} catalogue entries, {miss} not downloaded yet -> {out}")
