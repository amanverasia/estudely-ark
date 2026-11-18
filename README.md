# Estudely Ark

A free, offline collection of useful knowledge for India: encyclopedia, health books, water and food guides, repair manuals, full India maps, school science, programming manuals, and an optional local AI. It's spread by BitTorrent, USB and local Wi-Fi, and keeps working with no internet. Get it at https://ark.esdy.cc: the desktop pack by torrent, or the Android app.

This repository holds the build tooling for the desktop pack and the Android app's source, not the content (about 32 GB, downloaded from upstream sources).

- What we're building and why: [docs/SCOPE.md](docs/SCOPE.md)
- Test results so far: [docs/PROTOTYPE-RESULTS.md](docs/PROTOTYPE-RESULTS.md)
- Candidate content with exact sizes and checksums: [manifest/candidates.tsv](manifest/candidates.tsv)

## Build

```sh
scripts/fetch_release.sh                                        # about 32 GB into release/downloads (resumable)
python3 -I scripts/make_release_spec.py release/downloads src/release-spec.json "v1.0, November 2026"
python3 -I scripts/build_pack.py src/release-spec.json release/downloads release/Estudely-Ark-v1.0-2026-11
scripts/sign_manifest.sh KEY release/Estudely-Ark-v1.0-2026-11                # release key: maintainer only
tools/lt-venv/bin/python scripts/make_torrent.py release/Estudely-Ark-v1.0-2026-11 Estudely-Ark-v1.0-2026-11.torrent --release-key SHA256:...
```

`.github/workflows/pack-test.yml` builds a small partial pack and tests the launchers on Windows and Linux.

## Android app

Download: [GitHub releases](https://github.com/amanverasia/estudely-ark/releases/latest) (current: 2.0.1). An F-Droid listing is under review; Google Play later. Android 8.0 or newer. Signing certificate SHA-256 `9cf9432f4270f8c3a54c677b349b319ea2981d93fb227e1f483fa47de680447d`.

The app (`cc.esdy.ark`, source in `android/`, Java with libkiwix, libtorrent4j and BouncyCastle) downloads the chosen subjects of the v1 torrent, checks every file against the signed manifest with the release key built into the app, and reads them offline: ZIM archives with full-text search, PDFs, and the India map with place search. Downloads wait for Wi-Fi unless the user allows mobile data; sharing is opt-in (Wi-Fi and charging only).

Build (a Linux machine with JDK 21 and the Android SDK; copy `scripts/local.env.example` to `scripts/local.env` first):

```sh
scripts/android_sync.sh assembleDebug                    # debug build, app ID cc.esdy.ark.debug
scripts/android_sync.sh testDebugUnitTest                 # unit tests
scripts/android_sync.sh assembleRelease bundleRelease     # unsigned per-ABI + universal APKs, and the Play bundle
scripts/sign_apk.sh                                       # maintainer only: signs with the release key
```

Builds are reproducible: F-Droid's build of a release tag is byte-identical to ours, so F-Droid can ship the developer-signed APKs. Store text and screenshots: `fastlane/metadata/android/en-US/`. Release steps: [docs/publishing/CHECKLIST.md](docs/publishing/CHECKLIST.md). Test results: [docs/PROTOTYPE-RESULTS.md](docs/PROTOTYPE-RESULTS.md). Privacy policy: https://ark.esdy.cc/privacy

## Status

- Desktop pack v1.0: released 16 November 2026.
- Android app: 2.0 released 17 November 2026; 2.0.1 (bookshelf redesign) the same day. In development: 2.1, with verified moves between phone and SD-card storage and the 2.0.2 reader/settings improvements.
- Bugs and questions: [GitHub issues](https://github.com/amanverasia/estudely-ark/issues).

## Licence

Code in this repository: GNU General Public License v3.0 or later ([LICENSE](LICENSE)); the Android app links libkiwix (GPL-3.0). Copyright 2026 Aman Verasia.

The pack's content is not in this repository and keeps its own licences (Wikipedia CC BY-SA, Hesperian, PhET and iFixit non-commercial, OpenStreetMap ODbL, and others), listed in the pack's credits. The pack and the app are free and stay free.
