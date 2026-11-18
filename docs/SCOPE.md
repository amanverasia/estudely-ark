# Estudely Ark: Scope

Status: v0.7, 18 November 2026. **v1 released 16 Nov 2026. v2 (Android): 2.0 and 2.0.1 released on GitHub 17 Nov 2026; 2.1 (SD-card storage, phone-friendly DevDocs) released on GitHub 18 Nov 2026; F-Droid under review; Google Play next (account accepted 18 Nov).** Prototype results: [PROTOTYPE-RESULTS.md](PROTOTYPE-RESULTS.md). Owner: Aman. Name: Estudely Ark (chosen 16 Nov 2026).

This document says what we are building, in what order, and what "done" means. It replaces the earlier brief's "proposals" where Aman has since decided. Exact byte figures live in [`manifest/candidates.tsv`](../manifest/candidates.tsv). This file only summarises them.

## 1. Goal

A free, publicly downloadable collection of useful offline knowledge, software and local AI. It must stay useful after the internet stops working. It is spread first by BitTorrent, then by any other channel that can carry the same verified files (USB drives, local Wi-Fi, phone apps).

Audience: ordinary people in India first, English-first content.

It is **not** a hotspot appliance, a Raspberry Pi build or a new internet service.

## 2. Decisions made

| Topic | Decision | Date |
|---|---|---|
| Packaging | One complete torrent per major release, with independently selectable modules | 13 Nov 2026 |
| Core size | Aim for about 10 GB of core library | 13 Nov 2026 |
| AI | Gemma 4 E4B and Gemma 4 12B as separate optional modules, **including** image/audio companion files | 14 Nov 2026 |
| Maps | **Full India** | 14 Nov 2026 |
| Desktop platforms | **Windows, macOS, Linux on x86-64 and ARM64** | 14 Nov 2026 |
| Licences | Not a gate for inclusion. The pack is free and non-commercial. We still ship a credits file per item, and avoid material likely to trigger takedowns of the torrent, hosting or app listing | 14 Nov 2026 |
| Updates | Fixed releases. New models ship as an optional upgrade torrent, folded into the next major release | 13 Nov 2026 |
| Order | Desktop pack and torrent first; Android app second; mesh/radio third | 14 Nov 2026 |
| Mobile | Android app that downloads modules, reads them and seeds (with user permission for Wi-Fi/mobile data). iOS is read-and-import only | 14 Nov 2026 |
| iFixit | Included as a separate optional Extras module in the same torrent (3.57 GB) | 16 Nov 2026 |
| Desktop map detail | Zoom 14, every street (about 2.6 GB) | 16 Nov 2026 |
| Health depth | Full text-only Wikipedia Medicine (1.05 GB) replaces the mini edition | 16 Nov 2026 |
| Education | OpenStax sets A (school basics) + B (nursing) + C (life and work) and LibreTexts K-12, about 1.65 GB | 16 Nov 2026 |
| Internet Archive | Yes, but as the very last step, after the release is final and Aman approves the upload | 16 Nov 2026 |
| Signing key | Aman creates it on their own machine just before the first public release | 16 Nov 2026 |

## 3. Phases

### v1: Desktop pack (released 16 Nov 2026)

Done: `Estudely-Ark-v1.0-2026-11`, 32.02 GB, signed (`SHA256:PAm9fPRpBLPaPXZVXO3g+ixxl4QW/LSjIhvWDe7uZ4E`), hybrid v1+v2 torrent (info-hash `e2b95e10be196280c33fe85cb7c679f77f906fb5`), page https://ark.esdy.cc, always-on always-on seeder (full pack). Internet Archive copy uploaded and MD5-checked (1,122/1,122 files), made dark by IA on 17 Nov, **live again on 18 Nov** (IA restored it; re-checked: all 1,122 names, sizes and MD5s match the local pack, and a peerless libtorrent run fetched files through the web seed alone). Still untested: the v1 Android steps, macOS Gatekeeper, Windows on real hardware (CI only).

In scope:
- Content modules in section 4, as files in a torrent.
- Readers and runners for every supported desktop platform, shipped inside the pack.
- Desktop maps: a browser map viewer (MapLibre + PMTiles) served by miniserve. Proven with Delhi in the prototype.
- `START-HERE.html`: a static landing page and searchable title/topic catalogue. It needs no network, scripts from the internet, fonts or analytics.
- A **local library server** launcher: one double-click starts `kiwix-serve` (and the map viewer), so the whole pack can be browsed from any browser. Other devices on the same Wi-Fi or hotspot can use it too. This is the cheapest "after the internet" feature and it comes free with Kiwix.
- AI launchers using llama.cpp's `llama-server` (built-in chat page), local files only, `--offline`, thinking off by default. Text chat is the supported experience. Image/audio input ships but is described as experimental until tested.
- A signed manifest (section 6).
- Torrent with HTTP web seeds, plus a `.torrent` file, magnet link, checksums and selection instructions.
- Publishing a copy to Internet Archive as a permanent web seed. Approved in principle; done last, after the release is final, with Aman approving exactly what goes up.

Out of scope for v1:
- Mobile apps (v2).
- AI searching the library with citations (candidate for v1.x or v2).
- Mesh/radio features (v3).
- Full-text search across every file. v1 promises title/topic catalogue search plus the search built into each Kiwix archive.
- Video, operating system images, full Gutenberg, Khan Academy, worldwide maps.

### v2: Android app

- Downloads chosen modules over BitTorrent (libtorrent), reads ZIM files (libkiwix), shows maps (Organic Maps for routing/search, or bundled viewer), runs E4B where the phone can.
- Seeds in the background with explicit user settings: Wi-Fi only / mobile data allowed, charging only, storage cap.
- **Local sharing** matters more than internet seeding once the internet is gone: phone-to-phone over Wi-Fi Direct / hotspot, and acting as a local library server.
- Verifies every module against the signed manifest, whatever channel it came from.
- The APK ships inside the pack and on the local server, so it can spread without Play Store.
- iOS: read and import only. iOS suspends background networking and Apple has historically rejected BitTorrent clients.

**Decided 17 Nov 2026 (Aman), after the prototype (see PROTOTYPE-RESULTS):**
- App ID `cc.esdy.ark` (matches ark.esdy.cc). Built on libkiwix + libtorrent4j; downloads the existing v1 torrent, so no re-release is needed.
- The library stays private to the app (app storage). Sharing with file managers or the Kiwix app can come later.
- Seeding: only on Wi-Fi and while charging, in short sessions inside a foreground service (Android freezes background apps and caps `dataSync` services at about 6 hours a day), with an on/off switch. Downloads also run in a foreground service.
- Maps: our own India vector map (the desktop `india.pmtiles` with the same viewer and place search), not Organic Maps. No turn-by-turn directions at first. The torrent's `Android-Organic-Maps` folder is simply not selected.
- Release order: v2.0 = pick modules, download, verify, read (ZIM, PDF), map, seed; **v2.1 = SD-card storage (changed and approved by Aman on 17 Nov)**. Phone-to-phone sharing and acting as a library server remain later work, as does AI.
- First fixes from the prototype: libtorrent4j drops trackers and web seeds (fixed in the prototype); the Internet Archive web seed fails over HTTPS on Android (fixed: the app fetches it with Android's HTTPS).
- Downloads wait for Wi-Fi unless the user ticks "Download over mobile data too" (off by default). Sharing is off by default.

**v2.0 released on GitHub 17 Nov 2026** (https://github.com/amanverasia/estudely-ark/releases/tag/v2.0; signing certificate SHA-256 `9cf9432f4270f8c3a54c677b349b319ea2981d93fb227e1f483fa47de680447d`). F-Droid and Google Play to follow. **v2.0.1 released 17 Nov 2026** (Bookshelf redesign, https://github.com/amanverasia/estudely-ark/releases/tag/v2.0.1). F-Droid submission: merge request 57820, pipeline green, awaiting review.

**v2.0 status (17 Nov 2026), emulator and Pixel 9a (Android 17):** done and tested: module picker with sizes and free-space check, foreground download service, Wi-Fi-only downloads, web-seed fallback, signature + SHA-256 verification with the built-in key, ZIM reader with full-text search, PDF viewer, offline India map with place search, sharing on Wi-Fi while charging (JobScheduler). Details and the bugs fixed on the way: [PROTOTYPE-RESULTS.md](PROTOTYPE-RESULTS.md).
Done since: release build (`cc.esdy.ark`, per-ABI + universal APKs and a Play bundle, debug hooks inert), reproducible builds (F-Droid's build of each tag matches the signed APKs), signing on a Mac, `onTimeout` handling, a real upload test, app icon and store listing, the 2.0.1 bookshelf redesign. Still open for v2: Google Play (Aman's account, last); F-Droid review (merge request 57820); 2.0.1 on a real phone (tested on the emulator only); a full multi-GB download on a real phone; PDFs inside ZIM archives.

### Android design update (approved 17 Nov 2026)

Aman selected **Option B / Bookshelf** from the interactive design shortlist (`design/ark-alternative/`). Implement it as native Android views: blue actions, serif subject covers, a reading-first Library, a filterable subject catalogue, and a separate live Downloads screen. Settings and storage use native sheets. Keep saved subjects and ongoing downloads when new subjects are selected; make manual pause persist across reopening. No AI or account features are part of this update. Version 2.0.1 is being prepared and tested separately from the published 2.0 release.

### v2.1 storage and reader update (approved 17 Nov 2026)

Aman asked for the 2.0.2 settings/reader fixes and the larger SD-card job to be built immediately as v2.1. The 2.0.2 work (System/Light/Dark theme, reading text size, phone-friendly DevDocs with collapsible contents, dark archive pages, library re-check/repair, About/licences) is folded into 2.1 rather than published separately.

The library can stay in internal app storage or in Ark's app-specific folder on a removable SD card; no broad storage permission is used. A foreground-service move copies every file, reads back and SHA-256-checks the copy, fsyncs the journal and copied directory entries, then durably switches location before deleting the source. Cancellation preserves the source. A journal and hash manifest support background crash/power-loss recovery; recovery validates the active copy before deleting the other one and follows no symlinks. A missing card blocks reading/downloads/sharing with an explanation rather than silently creating an internal replacement library. Physical SD-card hardware remains to be tested; emulator evidence is in [PROTOTYPE-RESULTS.md](PROTOTYPE-RESULTS.md).

### v3: Mesh and radio

LoRa (Meshtastic) carries about 1 kbps raw. Sending Simple Wikipedia over it would take roughly 9 months of continuous airtime, so radio will never carry the files. It is for:
- **Ask the library:** a node holding the pack and a model answers short text questions over the mesh, with a citation.
- Coordination: who nearby has which module, and announcing new release hashes.
- Evaluate **Reticulum** (NomadNet pages, LXMF messaging) as the "small web over radio" layer.
- Long-range Wi-Fi links (community mesh) for moving bulk files between towns.

## 4. v1 content and budget

Sizes in decimal GB (1 GB = 10^9 bytes). "Measured" means exact byte counts from the upstream server or API, not downloaded and checked yet.

### Core (target was about 10 GB; now about 13.9 GB after the map, health and education choices)

| Module | Contents | Size | Basis |
|---|---|---:|---|
| 01 General Knowledge | Simple English Wikipedia with images (2026-09), CIA World Factbook | 3.53 GB | measured |
| 02 Health & Sanitation | All 16 English Hesperian books (432 chapter PDFs); Wikipedia Medicine (full, no images); zimgit medicine, water, post-disaster; 8 OpenStax nursing textbooks | 2.60 GB | Hesperian estimated from ETags, rest measured |
| 03 Food & Growing | zimgit food preparation, Gardenology | 0.21 GB | measured |
| 04 Repair & Practical | Appropedia, zimgit knots, Restarters repair wiki | 0.63 GB | measured |
| 05 Maps | Organic Maps India, all 34 regions + world base maps (for phones), data version 261110 to match the bundled app | 1.66 GB | app's own index, checked |
| 05 Maps (desktop) | Protomaps India vector tiles, zoom 14 (every street), viewed in browser | about 2.6 GB | dry-run extract |
| 06 Education & Books | PhET simulations, Wikipedia maths/physics/chemistry (mini), LibreTexts K-12, 10 OpenStax textbooks (maths, science, economics, psychology, writing, Python). Gutenberg left out (smallest useful archive 1.2 GB) | 1.40 GB | measured |
| 07 Computing | DevDocs (Python, HTML, CSS, JS, DOM, Git, SQLite, Bash, man, HTTP, Markdown, C), freeCodeCamp, Arch Wiki | 0.11 GB | measured |
| Shared-Readers | Kiwix desktop/server for all platforms, Kiwix Android, Organic Maps APK | 0.72 GB | measured |
| **Core total** | | **13.53 GB** | measured on the v1.0 build |

The maps module alone is about 4.7 GB. That's well over the brief's 1 GB, because Aman chose full India at full street detail. Health is about 2.1 GB because Aman chose the full medical encyclopedia.

### Optional modules

| Module | Size | Basis |
|---|---:|---|
| AI runner (llama.cpp, CPU and Vulkan builds for all six desktop targets) | 0.24 GB | measured |
| Gemma 4 E4B + image/audio projector | 6.15 GB | measured, sha256 known |
| Gemma 4 12B + image/audio projector | 7.15 GB | measured, sha256 known |
| Extras: iFixit (repair guides), optional module | 3.57 GB | measured |
| Extras: Energypedia | 0.80 GB | measured |

**Complete collection without Extras: 27.65 GB.** With both Extras: 32.02 GB (v1.0 build, verified 16 Nov 2026). Everyone picks modules, so nobody has to take it all.

### Compression

**Decided 16 Nov 2026: no extra compression or archives.** Measured with zstd -19 on the built pack: Wikipedia/iFixit ZIMs 0-1%, desktop map tiles 0.3%, 12B model about 5%, E4B 10-20%, PDFs 15-20%, phone map files 36%, AI image/audio projectors 54%, runner programs 75%. Whole pack about 3.5-4 GB saved of 32.5 GB (about 11%; measured before the map update, which made the pack 32.0 GB). Not worth it: users would need double disk space, an unpack step (Windows can't open zstd), per-file Verify and module selection would break, and unpacked copies stop seeding. The one worthwhile case (shipping llama.cpp as its upstream archives, -0.6 GB, 533 files to 10) was offered and declined by Aman.

## 5. Platform support

| Platform | Library reader | Maps | AI runner |
|---|---|---|---|
| Windows x64 | Kiwix Desktop + kiwix-serve | Browser map viewer | llama.cpp CPU / Vulkan |
| Windows ARM64 | kiwix-serve x64 under emulation (unverified) | Browser map viewer | llama.cpp CPU / Vulkan ARM64 |
| macOS Apple Silicon | Kiwix macOS app + kiwix-serve | Browser map viewer | llama.cpp (Metal) |
| macOS Intel | Kiwix macOS app + kiwix-serve | Browser map viewer | llama.cpp |
| Linux x64 | Kiwix AppImage + kiwix-serve | Browser map viewer | llama.cpp CPU / Vulkan |
| Linux ARM64 | kiwix-serve | Browser map viewer | llama.cpp CPU / Vulkan |
| Android | Kiwix APK | Organic Maps APK + India files | v2 app |

Every cell is **untested** until Phase D records a pass. Publish only tested configurations. Do not guess minimum RAM for the models; measure it.

## 6. Integrity and signing

- Every release has `MANIFEST.tsv`: path, bytes, SHA-256 for every file.
- The manifest is signed with the project's Ed25519 key (`ssh-keygen -Y sign`, because OpenSSH ships with Windows 10+, macOS and Linux; minisign is the fallback). The result is `MANIFEST.tsv.sig`.
- The public key is printed in `README.txt`, `START-HERE.html`, the release page and on paper.
- Verification instructions are given for every platform. The v2 app verifies automatically.
- Aman holds the private key offline, with a paper/offline backup. Never in the repo or the pack.

Why: a torrent only proves your copy matches *that torrent*. It doesn't prove the torrent came from us, and it proves nothing once files move by USB or phone. Anyone can publish an "Estudely Ark v1" with altered medicine doses. A signature lets any copy, from any channel, prove it is ours and unaltered.

## 7. Distribution

1. **BitTorrent**, hybrid v1+v2 if testing confirms target clients handle it. One complete torrent per major release, modules as top-level folders.
2. **HTTP web seeds (BEP 19)** so the torrent works even with zero peers.
3. **Internet Archive** copy as a permanent seed (last step of the release).
4. **Syncthing** among trusted full-mirror custodians.
5. **USB/SD campaign**: "make copies and hand them out". Instructions in the pack.
6. **Local library server** for neighbourhoods once the internet is gone.

Partial seeders (one module only) are welcome and counted separately from full seeds.

## 8. Definition of done for v1

Tested on a clean profile, offline, for every platform marked supported:
- `START-HERE.html` opens and every link resolves locally.
- Content in every module can be found and read; Wikipedia search works in the bundled reader.
- The local library server starts with one action and is reachable from a second device.
- The map viewer shows India; on Android, Organic Maps loads the bundled India files and routes.
- Each model starts independently via its launcher with no network access; RAM, speed and context are recorded.
- `MANIFEST.tsv.sig` verifies with the documented commands.
- Torrent tests between two independent clients: select one module, interrupt and resume, force recheck, upload a module to a second client.
- Every number in public copy matches the final manifest.

## 9. Open decisions (Aman)

- **Google Play:** Aman's personal developer account was accepted into Play Console (18 Nov 2026); Play is still the last channel. Next: Play App Signing with Aman's own key, a closed test with 12+ testers for 14 days, store listing and declarations (`docs/publishing/CHECKLIST.md`).
- Decided 17 Nov 2026: name Estudely Ark, icon (serif A over an ark hull), channels GitHub releases + F-Droid + Google Play, public repository `amanverasia/estudely-ark` (GPL-3.0-or-later), APK signing key kept by Aman.
- Later: the OpenStax and Gutenberg picks once sized.

## 10. Known risks and unverified items

- **Internet Archive web seed (dark 17 Nov, restored 18 Nov):** IA made the item `estudely-ark-v1.0-2026-11` dark on 17 Nov and restored it on 18 Nov. Re-checked 18 Nov: not dark, details page 200, 1,122/1,122 files with matching names, sizes and MD5s; byte-identical downloads (incl. a name with spaces, and a 1 MB range from the middle of the largest ZIM); libtorrent with no peers fetched files through the web seed alone. It can go dark again: if so, another host needs no new torrent (`url-list` is outside the info dictionary, so the info-hash stays; magnets can add `ws=`).
- **Torrent leakage (found in prototype, fixed 16 Nov):** release torrents are hybrid v1+v2 with pad files (`scripts/make_torrent.py`); tested leak-free in libtorrent and Transmission 4.1.4. Transmission stores the padding on disk as a `.pad` folder; small files are bundled to keep that small. qBittorrent, Deluge, aria2 and mobile clients still to test.
- **macOS Gatekeeper (found in prototype):** our macOS binaries are unsigned; behaviour on a fresh Mac is untested.
- **AI on 8 GB machines (measured):** E4B works on an 8 GB M1 but slows to 2-4 tokens/s once memory is tight. Image input is impractical on modest CPUs.
- **Organic Maps sideloading (v1 pack, untested):** the pack's Android steps still rely on it. The v2 app uses its own map viewer instead (decided 17 Nov).
- **libtorrent4j on Android (found 17 Nov):** its default threaded disk back end aborted the app while hashing, and its bundled OpenSSL can't do HTTPS. Worked around (POSIX disk I/O, no HTTPS in libtorrent); watch for this when upgrading libtorrent4j.
- **Kiwix macOS app:** the `.dmg` is only 6 MB. Confirm in the prototype that it is self-contained.
- **Windows ARM64:** no native Kiwix build; relies on x64 emulation.
- **Non-commercial content (checked 16 Nov):** Hesperian books, PhET simulations (CC BY-NC 4.0 in every sim file) and iFixit (CC BY-NC-SA) may only be shared non-commercially. Fine for a free pack; the v2 app listing must be free and ad-free.
- **Hesperian:** sizes are estimates (no Content-Length served), 2 of 434 files unsized. Hesperian asks that people who can afford it buy the books; the pack should credit and link them.
- **zimgit packs:** contents not yet reviewed for quality or duplication.
- **Simple Wikipedia** shrank from 3.50 GB (June) to 3.12 GB (September). Check what changed before choosing.
- **llama.cpp** builds daily and published `v1.0.0` on 13 Nov 2026 with no binaries attached yet. Pin one tested build.
