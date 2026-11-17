# Estudely Ark

A free, offline collection of useful knowledge for India: encyclopedia, health books, water and food guides, repair manuals, full India maps, school science, programming manuals, and an optional local AI. It's spread by BitTorrent, USB and local Wi-Fi, and keeps working with no internet.

This repository holds the build tooling, not the content (about 32 GB, downloaded from upstream sources).

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

## Android app (v2, in progress)

Source in `android/` (Java; libkiwix, libtorrent4j, BouncyCastle). It downloads chosen modules of the v1 torrent, verifies them against the signed manifest with the built-in release key, and reads them offline (ZIM, PDF, India map). Built with Gradle on a Linux machine: `scripts/android_sync.sh assembleDebug`; tests: `./gradlew test`. Results: [docs/PROTOTYPE-RESULTS.md](docs/PROTOTYPE-RESULTS.md).

Status: v1.0 released 16 November 2026 (https://ark.esdy.cc). Repository private. Android app: debug builds only, no release yet.

## Licence

Code in this repository: GNU General Public License v3.0 or later ([LICENSE](LICENSE)); the Android app links libkiwix (GPL-3.0). Copyright 2026 Aman Verasia.

The pack's content is not in this repository and keeps its own licences (Wikipedia CC BY-SA, Hesperian, PhET and iFixit non-commercial, OpenStreetMap ODbL, and others), listed in the pack's credits. The pack and the app are free and stay free.
