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
