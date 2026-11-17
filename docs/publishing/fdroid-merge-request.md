# F-Droid submission (fdroiddata merge request)

1. Sign in to gitlab.com, fork https://gitlab.com/fdroid/fdroiddata, create a branch `cc.esdy.ark`.
2. Add `metadata/cc.esdy.ark.yml` with the contents of `docs/publishing/fdroid-cc.esdy.ark.yml` (drop the two comment lines at the top if you like).
3. Open a merge request to fdroiddata `master` with the title and text below. Pick the "App inclusion" template if GitLab offers it and paste this into it.

**Title:** New app: Estudely Ark (cc.esdy.ark)

**Text:**

Estudely Ark is a free, offline library for India: health books (Hesperian), Simple English Wikipedia, a full map of India with place search, repair guides, school science and programming manuals. The app downloads the parts the user chooses over BitTorrent (the same torrent as the desktop pack at https://ark.esdy.cc), checks every file against a signed manifest, and reads them offline (ZIM via libkiwix, PDFs, a MapLibre/PMTiles map).

* Source: https://github.com/amanverasia/estudely-ark (GPL-3.0-or-later), app in `android/`
* No ads, no tracking, no proprietary dependencies (libkiwix, libtorrent4j, BouncyCastle from Maven Central)
* Store metadata and screenshots: `fastlane/metadata/android/en-US/`
* **Reproducible:** `fdroid build` of tag `v2.0` (versionCodes 2001-2003, one per ABI) is byte-identical to the developer's builds; `apksigcopier compare` passes for all three against the signed APKs on the GitHub release, so the recipe uses `binary:` and `AllowedAPKSigningKeys`.
* Network use: BitTorrent (trackers, DHT, peers) and an HTTPS fallback to archive.org; downloads wait for Wi-Fi unless the user allows mobile data; sharing is opt-in (Wi-Fi and charging only). Privacy policy: https://ark.esdy.cc/privacy
* Content licences: the app ships no content. Some downloadable content is CC BY-NC (Hesperian, PhET, iFixit); the app is free, which those licences require.
* Tested locally with fdroidserver 2.4.5: `fdroid lint` clean, scanner clean, builds succeed.

Possible anti-feature for reviewers to judge: none known. (NonFreeNet doesn't apply: BitTorrent and archive.org are open protocols/services.)
