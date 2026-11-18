# Phase B prototype: results

Date: 16 November 2026. Prototype pack: `prototype/Offline-Pack-proto` (6.63 GB, 1,162 files, not for distribution).
Built with `scripts/build_pack.py src/proto-spec.json prototype/downloads prototype/Offline-Pack-proto`.

"Offline" on macOS means the servers ran under `sandbox-exec` with `scripts/offline.sb`, which blocks every network connection except to this machine. Any hidden download would have failed. On the VM, servers were bound to 127.0.0.1 but not network-isolated.

## What was tested

| # | Test | macOS 15+ Apple Silicon (M1, 8 GB) | Linux ARM64 (Ubuntu 24.04, Neoverse-N1, VM) |
|---|---|---|---|
| 1 | All downloads match upstream checksums (SHA-256 for Kiwix ZIMs, models and Organic Maps APK; MD5 for Kiwix releases) | PASS | PASS |
| 2 | `Start-Library` starts kiwix-serve + miniserve with no internet | PASS (sandboxed) | PASS (localhost) |
| 3 | `START-HERE.html` catalogue, search, links; zero external resources | PASS (browser resource list empty of non-local URLs) | PASS (HTTP probes) |
| 4 | ZIM content opens via undated alias URLs (`/content/zimgit-water_en/`) | PASS | PASS |
| 5 | Full-text search inside a ZIM | PASS | PASS |
| 6 | Hesperian PDFs served | PASS | PASS |
| 7 | Desktop map: Delhi renders with tiles, fonts (incl. Devanagari) and icons, all local | PASS after fix (see bugs) | HTTP probes only, no browser |
| 8 | E4B text chat via `Start-AI`, offline | PASS | PASS |
| 9 | E4B image input (identified New Delhi, India Gate, Rashtrapati Bhavan from a map screenshot) | PASS, slow | IMPRACTICAL: >10 min on 2 CPU threads; stopped to protect production |
| 10 | Sign manifest, verify, detect a changed byte, detect an edited manifest | PASS | not run |
| 11 | Torrent: select one module only (Health) in Transmission from an aria2 full seed | PASS (files match manifest) | not run |
| 12 | Torrent: forced recheck | PASS (23.07 MB verified) | not run |
| 13 | Torrent: a partial peer (Health only) uploads that module to a third client with no full seed online | PASS | not run |
| 14 | Torrent: resume after a hard crash (kill -9) | INCONCLUSIVE: aria2 restarted from 0% because it saves state every 60 s; Transmission's result was ambiguous | not run |
| 15 | Android: Organic Maps loads pre-downloaded India files offline | NOT TESTED (needs a phone) | |
| 16 | macOS Gatekeeper on a pack downloaded via a browser/torrent app | NOT TESTED (needs a clean Mac) | |
| 17 | Windows, Intel Mac, Linux x64 | NOT TESTED | |

## AI measurements (Gemma 4 E4B QAT Q4_0 + image/audio projector, 8,192 context)

| Machine | Load time | Prompt speed | Generation speed | Memory |
|---|---|---|---|---|
| M1, 8 GB, ordinary desktop apps open | about 20-24 s | 7-30 tokens/s | 8.3 tokens/s first run; 1.7-4 tokens/s once memory was tight (about 3 GB of swap in use) | Free memory fell from 42% to 10% during the first answer |
| Neoverse-N1 VM, 2 of 4 threads, lowest priority | about 14 s | 16.4 tokens/s | 4.8 tokens/s | About 4 GB extra used; RSS 9.1 GB including the memory-mapped model file |

The VM figures were measured with only 2 threads, so a dedicated 4-core ARM machine should be faster. That hasn't been measured. 12B was not tested anywhere: the Mac has too little RAM and the VM too little free disk.

## Bugs found and fixed

1. **Map fonts failed to load.** `new URL()` percent-encoded the `{fontstack}`/`{range}` placeholders. Fixed in `src/mapviewer/index.html`.
2. **Gemma 4 thinks before every answer by default.** About 1,700 characters of hidden reasoning per question, roughly doubling wait time, and with a token limit set it can use the whole budget before answering. `Start-AI.sh` now passes `--reasoning off`; `AI_THINK=on` turns it back on.
3. **Start-AI passed the image/audio file even when absent.** Fixed.
4. **Verify printed no key fingerprint.** Wrong `awk` column. Fixed.

## Findings that change the plan

1. **Use piece-aligned (hybrid v1+v2, BEP 47 pad files) torrents.** With a plain v1 torrent, choosing only Health also created 13 neighbouring files from other modules (small llama.cpp binaries, partial `.mwm` and `.part` files). Worse, `Verify.sh` then correctly reports those partial files as "damaged", which would alarm users. `transmission-create` only makes v1 torrents, so the release needs a libtorrent-based creator. Decision needed in Phase C: tool choice, and checking that target clients handle hybrid torrents.
2. **macOS binaries are unsigned (ad-hoc).** `spctl` rejects llama-server and kiwix-serve, so Gatekeeper will block them if quarantined. The launchers clear quarantine on our own folders, but the `.command` launcher itself may be blocked first. Must be tested on a clean Mac with a browser- or torrent-app download, and the instructions written from what actually happens.
3. **Transmission refuses peer connections when it finds no route to the internet.** It logged "no network connections possible" inside the sandbox. A real Wi-Fi network without internet still has a router route, so this is probably a sandbox artefact. Test LAN-only sharing on a real offline network before promising it.
4. **Image input is a fast-machine feature.** It's fine on Apple Silicon and impractical on modest CPUs. Describe it that way.
5. **8 GB machines are borderline for E4B** with other apps open. Advise closing other apps; publish measured speeds, not minimums.
6. **Kiwix release checksums are MD5**, so they only detect download corruption. Our own signed SHA-256 manifest provides the real authenticity check.
7. **Undated ZIM aliases work**, so catalogue links won't break when an archive is updated to a newer edition.
8. **miniserve (2-6 MB per platform) works** as the static/range server for the map viewer. Desktop maps need the library server running; the page explains this if opened as a plain file.

## Phase C follow-up (16 November 2026): piece-aligned torrent

Tool: `scripts/make_torrent.py` (libtorrent 2.1.1 in `tools/lt-venv`), hybrid v1+v2 with BEP 47 pad files. Test peers: `scripts/lt_peer.py` (libtorrent) and Transmission 4.1.4, all on 127.0.0.1.

| # | Test | Result |
|---|---|---|
| 18 | libtorrent client, Health only, from a libtorrent seed | PASS: 4 files, **0 files from other modules** (the v1 torrent leaked 13) |
| 19 | Transmission 4.1.4 opens a libtorrent hybrid torrent | **FAIL as made**: "duplicate path .pad/1025936". libtorrent names pads by size, so equal-size pads collide. Fixed by giving each pad a unique name (`.pad/<index>`); hashes unchanged |
| 20 | Transmission, Health only, after the fix | PASS: 4 Health files, all match the manifest; no partial files from other modules |
| 21 | Transmission and pad files | Transmission doesn't honour BEP 47: it downloads and **stores** the zero-filled pads in a `.pad` folder. README tells users it is safe to delete |

Padding cost depends on piece size and the number of small files. The prototype had 934 files under 64 KB (map fonts), giving 2.4 GB of nominal padding at 2 MiB pieces. Fix: `scripts/pack_fonts.py` packs the 1,024 glyph files into one `glyphs.bin` that the map page reads with HTTP Range requests. Tested offline with the full India z14 map: Delhi street level renders with Latin and Devanagari labels; zero non-local requests.

Other notes:
- `transmission-daemon` crashes on macOS when it forks into the background (Objective-C fork-safety abort). Run it with `-f` for tests.
- download.kiwix.org sends aria2 a Metalink file instead of the data (aria2 advertises Metalink in Accept). `make_release_urls.py` resolves Kiwix URLs to a mirror first; the SHA-256 check caught the bad files.

## Full v1 test build (16 November 2026)

`release/Offline-Pack`: 1,094 files, 30,785,360,460 bytes (30.79 GB), built from `src/release-spec.json` (generated by `scripts/make_release_spec.py`). Hardlinked from `release/downloads`, so it costs under 1 GB of extra disk. Signed with a throwaway key only.

Downloads: all checked. SHA-256 where upstream publishes it (ZIMs, models, miniserve, Organic Maps APK); Kiwix MD5 for Kiwix programs; upstream sizes for Organic Maps regions. Problems found and handled:
- Kiwix Android APK 3.14.1: Kiwix's `.md5` file doesn't match the APK served by both the main server and the dotsrc mirror (both serve identical bytes). Probably a stale checksum after re-signing. Accepted; Android checks the APK signature on install.
- Two Hesperian chapters (`en_hcwd_2026_06/07.pdf`) arrived truncated through aria2 (the server sends no Content-Length). Caught by comparing with the ETag size estimates; re-fetched with curl. All 432 PDFs now end in `%%EOF`.
- Two Hesperian back-matter PDFs (`en_hcwd_2026_bm.pdf`, `en_midw_2026_bm.pdf`) are 404 upstream. Left out.

| # | Test (macOS, M1, sandboxed with no network) | Result |
|---|---|---|
| 22 | `sh Start-Library.sh` serves all 31 ZIMs | PASS |
| 23 | All 50 catalogue links in START-HERE resolve | PASS |
| 24 | Full-text search (Wikipedia Medicine, "malaria": 1,871 results) | PASS |
| 25 | Desktop map, full India z14: Delhi and Kochi at street level; zero non-local requests | PASS. One icon ("townhall") missing from the sprite set |
| 26 | Verify on the full pack | PASS: 1,094 OK, about 28 s |
| 27 | Release torrent: 512 KiB pieces, 59,394 pieces, 3.3 MB `.torrent`, 0.35 GB nominal padding | Made |
| 28 | Transmission 4.1.4 downloads top-level files + Health only from a libtorrent seed (2.22 GB) | PASS: 449 files, nothing from other modules, Verify GOOD. 115 MB of `.pad` files stored |
| 29 | Executable bits after a Transmission download | **FAIL**: Transmission ignores the torrent's executable flag, so `.command`/`.sh` can't be double-clicked. README now gives `sh Start-Library.sh` as the always-works route |

## Windows and Linux on GitHub Actions (16 November 2026)

Workflow `.github/workflows/pack-test.yml` (private repo `estudely-ark`) builds a partial pack (water + Python docs ZIMs, 3 Hesperian PDFs, map assets, place index, Windows/Linux readers, llama.cpp, E4B), signs it with a throwaway key, and runs the launchers. Windows tests run in Windows PowerShell 5.1.

| Check | Windows x64 (Server 2025, 4 vCPU) | Windows ARM64 (Win 11) | Linux x64 (Ubuntu) |
|---|---|---|---|
| Verify GOOD; detects a changed byte; Verify.bat double-click route | PASS | PASS | PASS (via `sh`, exec bits removed) |
| Start-Library: START-HERE, kiwix-serve (needs ICU DLLs), ZIM alias, search, PDF | PASS | PASS | PASS |
| Map page, packed fonts (Range 206), place index | PASS | PASS | PASS |
| Library bound to 127.0.0.1 by default; `--share` reachable on the LAN address | PASS | PASS | not run |
| Start-AI: E4B (text only) loads and answers "New Delhi" | PASS: ~27 s load, prompt 15.6 / gen 6.6 tok/s | PASS: ~39 s load, prompt 23.9 / gen 6.2 tok/s | PASS |

Bugs found by these runs: Windows kiwix-serve missing its ICU DLLs (fixed in the spec); `timeout` fails without a console (replaced with `ping`). Test-harness issues (not product bugs): PowerShell 7 to 5.1 hand-off, Range header in PS 5.1, `cmd /c` quote stripping.

## Desktop map place search (16 November 2026)

`scripts/make_places.py` turns GeoNames India (CC BY 4.0) into `places.tsv` (557,822 entries: towns, villages, hospitals, clinics, stations, airports; 26.6 MB, loaded on first search). Tested offline: "Varanasi", "Leh", "Kochi", "Tirupati", "hospital" give sensible top results; clicking flies to the place and drops a pin. Missing sprite icons now draw as a grey dot.

## Not yet built

- Windows launchers and `README.txt`/`CREDITS.txt` are now written (16 Nov) but untested on Windows.

## Clean-up done

- the Linux ARM64 test server: `~/offline-pack-test` deleted; no processes or ports left; free disk back to 15 GB; all 11 containers running as before.
- Mac: all test servers and torrent clients stopped. `aria2` and `transmission-cli` remain installed via Homebrew (approved).

## Final pre-release check (16 Nov 2026), build Estudely-Ark-v1.0-2026-11

Found and fixed before signing:
- The front page said "Release v1 test build (not for distribution)" (the spec's default label). The build now passes the release name: "v1.0, November 2026".
- The phone maps were data version 260714 (July), but the bundled Organic Maps app (2026.11.11-39) expects 261110. Re-downloaded all 34 India regions plus World and WorldCoasts at 261110 (1.66 GB). Every size matches the app's own `assets/countries.json`, which is now `research/om_countries.json`.

Checked on the rebuilt pack (32,018,016,876 bytes, 1,119 files, test-signed):
- Verify: signature GOOD, 1,119 files OK.
- 41 upstream SHA-256 values match (32 ZIMs, models, Organic Maps APK, miniserve), plus 93 upstream sizes.
- Kiwix programs match the mirror MD5s. The Kiwix Android APK matches GitHub's SHA-256 for the v3.14.1 universal standalone build (the .md5 on the Kiwix server is stale). llama.cpp b12379 (10 archives) and miniserve 0.35.0 (6 builds) match GitHub release digests.
- All 450 PDFs open in PDFKit and pass a full `pdftotext` run with no errors.
- `pmtiles verify` passes on the desktop map (zoom 0-14, India bounds).
- Offline (sandboxed) library: 32 ZIMs served, all 69 catalogue links open, full-text search works, map Range requests return 206, place index and phone map folder are served.
- File names: no Windows-illegal characters, reserved names, case collisions or non-ASCII. Longest path 142 characters.
- No personal data, local paths, private keys or stray files.
- Test torrent: 61,757 pieces of 512 KiB, 3.47 MB torrent file, built in 34 s.
- README named the wrong phone folder. The bundled web APK is `app.organicmaps.web` (checked in the APK's manifest and in Organic Maps' build.gradle at tag 2026.11.11-39-android), so the folder is `Android/data/app.organicmaps.web/files/`. Fixed, and README now suggests copying over USB from a computer when the phone's file app is blocked from Android/data.
- `make_torrent.py` now refuses to build unless the pack is signed with the given release-key fingerprint and the signature verifies (tested: wrong key, missing flag and edited manifest are refused).
- Still untested: Android sideload (copying into `Android/data/app.organicmaps/files/`), macOS Gatekeeper on a clean Mac, and a Windows volunteer.

## v1.0 release torrent (16 Nov 2026), not yet published

- Signed by Aman's release key `SHA256:PAm9fPRpBLPaPXZVXO3g+ixxl4QW/LSjIhvWDe7uZ4E`. Verify: GOOD, 1,119 files OK. The key comment (computer name) is not in the pack.
- `release/Estudely-Ark-v1.0-2026-11.torrent`: v1 `e2b95e10be196280c33fe85cb7c679f77f906fb5`, v2 `926678ea8c7523abf6104605ca16a72adb8df5bfd49e471cd83b8b30147271d3`. 61,757 pieces of 512 KiB.
- Trackers: opentrackr, open.demonii.com, torrent.eu.org, open.stealth.si, exodus.desync.com. Web seed `https://archive.org/download/estudely-ark-v1.0-2026-11/`. Changing either means a new torrent and new hashes.

## v2 Android prototype (16-17 Nov 2026)

Code: `android/` (Java, AGP 9.4.1, Gradle 9.9.1, compileSdk 36, minSdk 26; libkiwix 2.7.0, libtorrent4j 2.1.0-39, BouncyCastle 1.86). Built on the build machine with `scripts/android_sync.sh`; emulator helper `scripts/android_emu.sh`. Debug APK: `release/android/EstudelyArk-prototype-0.1-debug.apk` (not for distribution; it dials the always-on seeder directly in debug builds).

| Test (emulator, Android 16 x86_64) | Result |
|---|---|
| Signature check (JVM unit tests on the real v1.0 manifest): valid passes; changed manifest byte, changed signature byte, other key, wrong namespace all rejected; built-in key fingerprint = release fingerprint | PASS (6/6) |
| libkiwix opens a ZIM and the WebView renders it with the network off (Gardenology, 44,083 articles; Food Preparation) | PASS |
| Torrent download of only the Food module + signed manifest (210.5 MB), with the always-on seeder as a direct debug peer | PASS, 27 s; no other files or pad files written |
| Same, with no direct peer (public trackers/DHT only, like a stranger's phone) | PASS after fix, 147 s |
| Downloaded module verified against the signed manifest inside the app | PASS (signature GOOD, 2/2 files) |
| **Real phone** (a Pixel 9a, Android 17, arm64): Food module via public trackers only, app in the background behind another app, as a foreground service | PASS, 78 s, 2 seeds (the always-on seeder over uTP, a Mac over home Wi-Fi); signature GOOD, 2/2 files |

Findings for the real app:
- **libtorrent4j 2.1's `SessionManager.download(TorrentInfo, ...)` drops the torrent's trackers and web seeds** (libtorrent 2.1 keeps them outside `torrent_info`). Without a fix the app announced to no tracker and would never use the Internet Archive web seed. The prototype re-reads `announce-list`/`url-list` from the .torrent and adds them to the handle.
- Android blocks route enumeration (`enum_route ... Operation not supported`); libtorrent handles it by keeping wildcard sockets, and IPv4 announces work. IPv6 announces are skipped ("unreachable") on the emulator.
- uTP (BitTorrent over UDP) connections dropped repeatedly on the emulator, but worked fine on the real Pixel (the always-on seeder served the whole module over uTP). Treat it as an emulator networking artefact.
- **Android freezes a background app and cuts its network** (Pixel 9a: `ActivityManager: freezing` about 75 s after leaving the screen; connections failed with "Operation not permitted"). Downloading and seeding must run in a foreground service (prototype: `DownloadService`, type `dataSync`). Android 15+ limits `dataSync` services to about 6 hours a day, so long-term seeding needs a different plan (for example seed only while charging on Wi-Fi, in shorter sessions).
- **The Internet Archive web seed fails inside libtorrent4j on Android**: HTTPS connections to archive.org fail with `asio.ssl init fail (BIO routines)` (Pixel 3/3, emulator too). It isn't the certificates: pointing `SSL_CERT_DIR` at Android's store (`/apex/com.android.conscrypt/cacerts`, 149 certs) didn't change it; the error is in the OpenSSL 3.5.5 set-up inside libtorrent4j's Android build. **Workaround, tested 17 Nov:** after 45 s without progress the app fetches the missing wanted files from the web seed with Android's own HTTPS, then libtorrent re-checks them against the torrent hashes and seeds them (`LibraryEngine.fetchFromWebSeed`). Web-seed-only test on the emulator: FINISHED in 282 s (including the 45 s wait), signature GOOD, 2/2 files.
- Files the shell creates under `Android/data/<app>` aren't readable by the app on Android 16; the prototype keeps the pack in internal storage. Exposing the library to other apps (file managers, Kiwix) needs a decision for the real app.
- PDFs inside some ZIMs (zimgit) can't be shown by a WebView; the real app needs a PDF viewer.

### Real app, stages 2–3 (emulator, 17 Nov)

Library screen (pick modules, sizes, free-space check, "Free space" removes unticked modules), foreground download service, module screen, ZIM reader, PDF viewer, map viewer. All on the `ark-test` emulator.

- **Web seed with a network cut**: Food + Map (2.8 GB) fetched with the web seed only. Wi-Fi and data cut for 15 s at 487 MB into the 2.6 GB map file; the fetch logged the errors, waited, and resumed with a Range request (no restart from 0). The rest of the file arrived later, in spite of another connection drop.
- **Crash found and fixed**: libtorrent4j aborted (`Scudo ERROR: invalid chunk state`, in its hashing thread) 7 s into `forceRecheck` of files replaced while the torrent was paused. Now the session stops, resume data is deleted, files are fetched, and the session restarts and checks the disk from scratch. Tested: deleted one Food ZIM, fallback fetched it (112.5 MB), restart check, Verify GOOD; no crash. Full start-up check of the 2.8 GB on disk: under 10 s.
- **Verification**: signature GOOD with the built-in key; Food 2/2 files, Map 31/31 files OK (SHA-256). Modules found incomplete on disk lose their verified mark and are checked again after download.
- **Map viewer offline** (Wi-Fi and data off): first attempt showed an empty map. Cause: WebView applies the Range header itself to intercepted responses, cutting our 206 bodies again ("Failed to fetch"); handing it the whole file doesn't work either (it sizes the file with an int). Fixed with an injected fetch wrapper (`ark-range` query parameter). Now tiles at all zooms, fonts, place search ("Shimla": 7 results, zooms to 3 km scale) all work offline. The viewer's "Back" link closes the map.
- **ZIM reader**: Gardenology opens, full-text search ("tomato") lists results, articles and images load.
- **Not yet tested**: a PDF inside a ZIM (no PDFs in the Food archives); real-phone runs of the new build; `onTimeout`.

### Stage 4: sharing (emulator, 17 Nov)

- "Share with others on Wi-Fi while charging" switch on the library screen, **off by default**. On: a platform JobScheduler job (no AndroidX) every 30 min, unmetered network + charging, persisted across reboots; sessions of up to 9 minutes.
- The job also checks the conditions itself every 5 s: a job started with `cmd jobscheduler run` kept running after the charger was pulled even though the scheduler marked CHARGING unmet, so the app doesn't rely on the scheduler alone.
- Tested: pulled charger → sharing stopped within 5 s; Wi-Fi off → stopped; mobile data only → session refused to start; `jobscheduler stop` → engine stopped; starting a download while sharing → sharing paused, download took over, then finished and verified.
- Upload count (`allTimeUpload`) is saved and shown on the library screen. **Not yet tested**: actual uploading to a peer (nobody on the emulator's network needed data; the always-on seeder already has everything).
- Title bar removed (NoActionBar theme, light and dark); the module screen shows its own heading.

### Pixel 9a, Android 17 (17 Nov, new build)

Phone on a second machine (USB), on mobile data (Wi-Fi on but not connected). 

- Install over the prototype: fine; the earlier Food files were kept.
- Food (files already on the phone): engine start, libtorrent check from disk, signature GOOD with the built-in key, 2/2 files OK. No crash. Almost no mobile data used.
- **Not yet done on the phone** (need the phone's screen): map, ZIM search, PDF in a ZIM, the sharing switch and job (would refuse on mobile data), the web-seed fallback with its restart check.

Pixel run with Map, Health, Computing and Food selected (5.6 GB, Wi-Fi, 17 Nov), bugs found and fixed:

- **Selection change ignored**: resume data saved for the Food-only run kept its file priorities, overriding the new selection after libtorrent loaded it; the status said "100% of 0.2 GB" and nothing downloaded. Worse, the old completion test (`totalWanted`) said "done", so the app verified four empty modules (0/444, 0/31, 0/14 files). Fixed: completion and progress come from per-file progress; resume data saved for a different selection is dropped (check from disk instead).
- **Web seed failure ended the download**: see below; now logged, and the download carries on with peers. The web seed is probed with a 1-byte request before the session is stopped.
- The always-on seeder delivered about 3 MB/s to the Pixel (seeder log 04:58 UTC).

**Internet Archive item went dark (17 Nov, after the morning MD5 check passed 1,122/1,122):** `archive.org/metadata/estudely-ark-v1.0-2026-11` reports `"is_dark": true`, the details page is 404 and every file 403. The torrent's web seed is unusable until Aman sorts it out with IA. Peers (the always-on seeder, the Mac) still work.

**Native crash, root cause narrowed (17 Nov):** the Scudo "invalid chunk state" abort came back on the Pixel 8 s after every engine start while pieces were arriving (twice), with two threads freeing the same address at once. Not the file swap (no swap happened), not libkiwix (the prototype loaded it too and ran fine), not only the HTTPS web seed (removed from libtorrent; still crashed). The emulator crash's backtrace ran from an asio thread pool (libtorrent's disk/hash pool). With `SessionParams.setPosixDiskIO()` (no pool) the same Pixel run passed 75 s and kept downloading. libtorrent no longer gets the HTTPS web seed either (its bundled OpenSSL fails on Android anyway); the app's own HTTPS fallback uses it.

**Pixel 9a, Android 17, readers and sharing (17 Nov, 12:00):** with the POSIX disk build the download ran about 40 min (to 1.6 GB of 5.6 GB) with no crash; it was stopped when the phone fell back to mobile data (no data used: no peer by then). New: downloads wait for Wi-Fi unless "Download over mobile data too" is ticked (off by default); tested on the emulator (Wi-Fi off → "waiting", back on → resumed, Computing 14/14 verified).
To avoid a 5.6 GB download, the readers were tested with small files copied over USB and opened by debug-only intents (`openMap`, `openPdf`, `openZim`):
- Map: a 2.5 MB Shimla extract of `india.pmtiles` with the full viewer. All map and font reads went through the `ark-range` wrapper as 206, no errors on Android 17's WebView; place search ("Shimla", 7 results) and zoom to 3 km scale with detailed tiles.
- PDF: a Hesperian chapter (1 MB) renders sharply and scrolls.
- ZIM: Gardenology full-text search ("tomato") lists results.
- Sharing: turning the switch on made Android start the job by itself (no forced run) on Wi-Fi + USB power; a simulated unplug stopped it ("Charger or Wi-Fi gone"); battery state restored. Switch turned off again afterwards (job cancelled), test files removed; Food stays on the phone (226 MB).
- Don't use `uiautomator dump` on this phone: it can clash with other UI automation on a shared phone.

**Emulator end-to-end download, Repair (627 MB), 17 Nov 12:06–12:12:** with Food, Map and Computing already on disk; selection change → check from disk → peers only (archive.org web seed probed, 403, logged, carried on without restarting) → Verify Repair: signature GOOD, 3/3 files OK. No crash (POSIX disk I/O build). About 6 minutes.

**Seeder review (read-only, 17 Nov):** not the bottleneck. It has sent 3 MB/s and 1.6–1.9 MB/s to the Pixel; load 0.7, 19 GB memory available, disk I/O pressure under 0.4%. The slow and dropped periods match the Pixel's Wi-Fi falling back to LTE. `ionice -c 3` in the start script has no effect (the volume's I/O scheduler is `none`); harmless, left as is. No change made or needed.

### Release build and Android's time limit (emulator, 17 Nov)

- Release build: app ID `cc.esdy.ark` (debug builds are `cc.esdy.ark.debug`, so both install side by side), version 2.0. One APK per ABI (arm64-v8a 76 MB, armeabi-v7a 69 MB, x86_64 75 MB; 32-bit ARM added for low-cost phones) plus a universal APK (207 MB; libkiwix's 32-bit x86 libraries left out, libtorrent4j has none). Version codes 2000 (universal), 2001-2003 (per ABI). Signed only when the maintainer's properties file exists (`~/.estudely/ark-release.properties`, `-ParkSigning=` or `$ARK_SIGNING`); otherwise unsigned. A test-signed (debug key) x86_64 release APK installed and ran; the debug intents (download, openPdf) did nothing, as intended.
- `onTimeout` (data_sync_fgs_timeout_duration shortened to 60 s on the emulator, restored afterwards): the service stops within a second, posts "Download paused. Android limits background downloads. Open Estudely Ark to continue.", and opening the app continues the download by itself (Android resets the budget when the app comes to the front). Notifications need the POST_NOTIFICATIONS permission, which the app asks for when the user taps Download.
- **Sharing uploads for real (emulator, 17 Nov):** the debug app shared Repair (verified) in a sharing session; a libtorrent client on the Mac with no trackers, no web seeds and no DHT connected only to the emulator (adb forward + SSH tunnel) and downloaded all of Repair, 626.6 MB, in 45 s (up to 17.6 MB/s), every piece hash-checked. The app recorded 626,655,232 bytes uploaded ("shared" on the library screen).
- Bug found and fixed: a force-stop cancels scheduled jobs, so sharing stayed "on" with no job. The app now re-schedules the sharing job whenever it opens with sharing switched on.
- UI polish (17 Nov): progress bar and "Downloading: X of Y" per module; first-run hint when nothing is ticked; messages in plain English ("Checking the files already on this phone", "Internet Archive copy not available; carrying on with other copies", "Computing: signature GOOD, 14 file(s) checked, all good"); release builds show only the latest message instead of the developer log. Tested on the emulator (Computing download, no crash). App name and icon unchanged, pending Aman.


## Android 2.0.1 Bookshelf redesign (17 Nov 2026)

Aman chose Option B from the interactive shortlist. Implemented as native Java views: cover-based library; available/saved subject catalogue; draft choices separate from saved/download selection; live progress dial and per-subject download rows; persistent manual pause/resume; native settings and storage sheets; matching subject and reader controls; system dark mode and larger-text bookshelf layout.

Workstation `ark-test` emulator, Android 16 x86_64: existing Repair and Computing subjects remained ready (738 MB total). Passed subject filtering/selection, selected-byte total, Wi-Fi-only waiting with no new subject downloaded, pause/resume controls, and pause preserved after force-stop/reopen. Settings switches work; no subject deletion was performed. At font scale 1.4 the bookshelf uses one column and the catalogue/footer stay reachable; system dark mode works. Real local Computing archive opens and Bash full-text search returns results and opens an article.

Automated checks: 13 JVM tests passed (4 catalogue, 6 signature, 3 selection-retention tests). Debug APKs, all four unsigned release APKs, and the Play app bundle built. Preview package is separate (`cc.esdy.ark.debug`); release signing and publication have not been performed. Additional reader fixture checks are recorded with the preview artifacts.

Map check completed afterwards (emulator, debug 2.0.1): the map opens under the new "Map of India" toolbar, tiles draw, the toolbar back button returns to the bookshelf, no crash. Leftover: the map page's own "← Back" link now duplicates the toolbar's back button. PDF reader: 12-page fixture renders, next/previous page controls work.

## Android 2.0.2 reader/settings fixes (17 Nov 2026)

These changes are folded into 2.1 rather than released separately. On the `ark-test` Android 16 emulator: System/Light/Dark selection applies without restarting; reading text size persists; DevDocs uses the full phone width with one article scroll and a separate full-screen Contents panel; dark theme also covers archive pages (including DevDocs' `color-scheme: light only` override); freeCodeCamp works after enabling DOM storage. About, signing-certificate fingerprint, credits and licences open correctly.

The library re-check was tested by changing one byte in the DevDocs Bash archive without changing its length. The check removed Computing from the ready shelf; Resume let libtorrent fetch the damaged piece again, and Computing passed 14/14 files. The repaired archive's SHA-256 exactly matched its original (`ea4afb04…`). Release lint and debug/release builds passed at that checkpoint.

## Android 2.1 SD-card storage (17 Nov 2026)

Implementation: app-specific internal/removable roots (no storage permission); a foreground move service with notification, progress and cancellation; copy-time and read-back SHA-256 checks; durable journal plus hash manifest and directory/fsync barriers; background crash recovery that validates the active tree before deletion; no-follow symlink handling; downloads, sharing, removal and reading blocked while moving, recovering or the configured card is absent. FAT32's 4 GiB file limit is checked conservatively for removable cards. The phone-selectable v1 files fit it (largest 3.57 GB decimal).

Automated checks at the device-test checkpoint: **27 JVM tests, 0 failures/errors**, including 14 move tests (nested move, cancellation, insufficient space, pre/post-commit recovery, unavailable storage, truncated target, inconsistent/malformed journal, commit failure and symlink sentinel). Release lint completed with no errors. A final review subsequently moved startup recovery off the UI thread and tightened post-recovery success checks; the clean rebuild after that refactor is recorded below only when available.

Real UI/device sequence on `ark-test`, Android 16 x86_64, using the existing 738 MB Repair + Computing library and a temporary 2 GB FAT32 card (`0000-0000`):

1. **No card attached:** storage screen says so and offers no fake target. Existing library remains ready.
2. **Phone → SD:** Android exposes the card with no permission prompt; move completes through the foreground service. `storagePath` points to the SD app folder and the internal pack folder is gone. Log: `Library moved to SD card`.
3. **Cold start and signed verification on SD:** Repair signature GOOD, 3/3 files; Computing signature GOOD, 14/14 files; UI says "2 subjects checked against the signed file list. All good." DevDocs C opens from the card, full-width article, dark theme, larger text and Contents all work.
4. **SD → phone:** `storagePath` returns to internal storage and the SD app folder is empty. SHA-256 after returning exactly matches the pre-move values: manifest `b7ad6a2136ffd6c93c5be2844e5e601d81f117476b1ab3fa0cb40b5b10f3a5fb`; DevDocs C `5b5c00cd83c0cbfc7e89220721afbbc15ce82210d62229ac3639a169a3352431`.
5. **Card removal:** after moving to SD again, rebooting without the card shows "SD card unavailable" and explains that saved subjects were not deleted or replaced. No internal pack directory is created and no fallback download starts.
6. **Reattach and restore:** rebooting with the same card restores access; moving back to phone storage succeeds and both independent hashes again match the originals.

This is emulator evidence, not a physical SD-card endurance/removal test. Simulated journal/crash states are covered in JVM tests; an actual phone power cut during a move remains untested. The temporary card image is a test artifact, not part of the app or release.


## DevDocs phone layout, second pass (18 Nov 2026)

Aman's 17 Nov phone screenshot (DevDocs C, `bsearch`, before the first fix) showed the sidebar taking half the screen and code broken one word per line. The first fix gave the article the full width, but code inside the C declaration tables still had only 238 of 412 px and wrapped word by word. Second pass (`ZimWebClient.PHONE_LAYOUT`): code keeps its lines and indentation and scrolls sideways; tables holding code stack their cells (code full width, "(1)" / "(since C11)" labels under it); article text 1rem instead of DevDocs' 0.875rem (the reader text size still scales it); Contents rows at least 44 px and the panel opens on the current page's entry; attribution URLs no longer stick out.

Tested in **desktop Chromium**, not Android WebView: the release DevDocs archives were served by `kiwix-serve` (offline sandbox) through a loopback proxy that inserts the app's `PHONE_LAYOUT` string from the Java source right after `<head>`, as the app does. At 412 px: 48 pages (index, first, middle and last linked article of each of the 12 DevDocs archives); 47 had no page-wide overflow and the phone layout applied; SQLite's `keyword_index` is a dead link inside the archive (404). At 360 and 412 px, C/Python/HTTP/SQLite samples: no overflow, all code blocks within the screen. Arch Wiki and freeCodeCamp unaffected. Every archive's HTML has a literal `<head>`, so the injection lands inside it. `:has()` needs WebView 105+; older WebViews fall back to the first fix's scrolling table.

**Real phone (18 Nov):** debug 2.1 built on a second machine (own JDK 21 and SDK 36 in `~/estudely-android`; unit tests 27/27) and installed on Aman's moto g52 test phone (LineageOS, Android 16, arm64, wireless adb), DevDocs C opened through the debug `openZim` hook, dark theme. Full-width article; Contents full screen with 44 px rows and centred folder arrows, Algorithms expands, `bsearch` opens; declarations keep their layout, each code block swipes sideways on its own (the page doesn't move), "(1)" / "(since C11)" under each; the empty header cells that showed as grey boxes are hidden. Not tried on the phone yet: the Larger text sizes, light theme, other DevDocs archives.

## Android 2.1.1 document viewers (moto g52, 18 Nov 2026)

Debug 2.1.1 built on a second machine and installed beside the signed release on the moto g52 (LineageOS, Android 16, arm64, wireless adb). The phone was on the launcher; the test app was removed afterwards and the release app left installed.

PDF viewer, real Hesperian chapter (`en_hcwb_2000_07.pdf`, 12 pages), opened through the debug `openPdf` hook:

- Renders two pages at once, sharp at 1x; the −/+ buttons step 1x → 1.5x → 2x → 3x and back, double-tap toggles 1x/2x, and a zoomed page pans sideways while the list still scrolls vertically.
- Page arrows move the counter (checked 1 → 3 → 5). Pages render on a background thread; no jank or crash while paging or zooming.
- **Bug found and fixed:** the remembered page was saved (`pdf-positions.xml` held `2` for page 3) but never restored — `applyWidth()` calls `notifyDataSetChanged()`, which discards the `setSelection()` made in `onCreate`. The jump now runs in `pages.post()` after the width is applied. Retested: page 5, Home, force-stop, reopen → "Page 5 of 12".
- Pinch zoom, later with rooted debugging: injected real multitouch through the goodix touchscreen (`sendevent`, adb root). Two fingers 200 px apart spreading to 600 px magnified page 2 to about 3x (page wider than the screen, right edge cut); pinching back from 600 px to 200 px returned it to 1x with both pages visible again. Single-pointer injection was checked first (tapped Next page, 1 → 2).

Archive titles: with the real signed `MANIFEST.tsv`, `MANIFEST.tsv.sig` and `devdocs_en_c_2026-10.zim` placed as a downloaded Computing subject, the list shows **C Docs** from the ZIM's `Title` metadata (with `2026-10` in the detail line) instead of the file-name label. Opening it shows the same title in the reader toolbar, the full-width DevDocs layout and the Contents button.

Automated: 27/27 JVM tests, debug lint 0 errors (33 warnings). The signed 2.1.1 APKs in `release/android/v2.1.1/` were built before this fix and must be rebuilt and re-signed before publishing.

## ZIM reader: PDFs and the zimgit search crash (moto g52, debug 2.1.1, 18 Nov)

Tested with the real `zimgit-food-preparation_en_2025-04.zim` (93 MB, 17 PDFs inside, no title index):

- **A PDF tapped inside an archive opens in the PDF viewer** ("Food Preparation (1).pdf", 44 pages), so the existing link hand-off (`shouldOverrideUrlLoading` → `openPdf`) works on a real phone, not just the emulator.
- **Bug found and fixed: searching such an archive failed with libzim's raw error "Cannot get entry for end iterator".** Libzim's Java iterators report `hasNext()` on an empty result set and then throw while fetching the entry; zimgit archives have full-text results that behave this way and no title index at all. `ZimActivity` now treats that exception as end-of-results, logs it, and shows "Nothing found. Try another word."; search results that are PDFs also open in the PDF viewer instead of a blank WebView.
- Regression: DevDocs C search still lists and opens results ("bsearch" → Algorithms, qsort_s, stdlib.h) — the handling fix only changes the broken-archive path. The food-prep archive's full-text index itself returns zero matches for common words ("bread", "jam") in libzim too; that is the upstream archive's index, not the reader.
- Not testable with pack archives: a *search result* that is a PDF (the zimgit full-text index returns nothing to list). The route is one new branch in the result click: check the entry's mimetype is `application/pdf` and hand it to the PDF viewer, same as the link path tested above.

Fix is in `ZimActivity`, committed after v2.1.1 was published, so it ships in the next version.

## Android 2.1.2 search and DevDocs fix (moto g52, 18 Nov 2026)

Debug 2.1.2 built on a second machine (JDK 21, SDK 36) and installed beside the signed 2.1.1 release on the moto g52 (LineageOS, Android 16, arm64, wireless adb). Unit tests 27/27, release lint 0 errors (33 warnings); version codes 2120-2123.

- Opened `zimgit-food-preparation_en_2025-04.zim` (93 MB, no title index) through the debug `openZim` hook. Searching "bread" shows "Nothing found. Try another word." instead of libzim's "Cannot get entry for end iterator"; no crash in logcat.
- Tapped the "Philmont Trail Cookbook" PDF (44 pages): it opens in the PDF viewer; the + button zooms the page.
- DevDocs C search "bsearch" still lists Algorithms, qsort_s and Standard library header <stdlib.h>; opening Algorithms renders. Its overview table now shows `qsort`, `qsort_s` and `(C11)` on separate lines instead of `qsortqsort_s`; the same rule covers See also rows.
- Signed release check (Aman signed with `scripts/sign_apk.sh`, 18 Nov): all five SHA-256 sums pass; APK and AAB certificates are Aman's `9cf9432f…447d`; `jarsigner -verify` passes on the AAB. The signed arm64 APK installed over 2.1.1 on the moto g52 (2112 → 2122, 2.1.1 → 2.1.2) and launched without a crash; logcat clean. Reproducibility (arm64 APK only; the other ABIs were not re-checked): `apksigcopier` 1.1.1 on Python 3.12 copies the signature onto the unsigned build from a second machine and the result is byte-identical to the signed APK (`compare` passes). Run under Python 3.15.2 the same tool reports a false `CHUNKED_SHA512 digest mismatch`: it writes the three v1 signature entries without the UTF-8 name flag (0x0800), the only bytes that differ. Use an older Python (3.12 here) for this check.
