# Android release checklist

For each release (released so far: 2.0, 2.0.1, 2.1 and 2.1.1, 17-18 Nov 2026; 2.1.2 prepared 18 Nov 2026). Public copy and uploads need Aman's approval of the text and the destination first.

## 1. Version
- `android/app/build.gradle.kts`: raise `baseVersionCode` (2.0 = 200, 2.0.1 = 201, 2.1 = 210) and `versionName`. Version codes come out as base x 10 + ABI: 0 universal, 1 armeabi-v7a, 2 arm64-v8a, 3 x86_64.
- Store changelog: `fastlane/metadata/android/en-US/changelogs/<code>.txt` for each of the four codes (under 500 characters).
- Release notes: `docs/publishing/release-notes-v<ver>.md`.

## 2. Build and sign
1. `scripts/android_sync.sh assembleRelease bundleRelease` (build machine). The sync mirrors `android/` (it deletes the old tree first), so the build is clean; an overlay once compiled a deleted class into a release.
2. `scripts/sign_apk.sh` on a Mac: asks for the keystore password, signs with `--alignment-preserved` (keeps the build's bytes, so F-Droid's reproducibility check passes; the build already aligns native libraries to 16 KB), verifies, writes `release/android/v<ver>/` + `SHA256SUMS`.
3. Checks: every APK carries certificate `9cf9432f…447d`; `apksigcopier compare` of each signed APK against its unsigned build passes; install over the previous release on the emulator (or a phone) keeps downloaded subjects; open a subject.

## 3. GitHub release (public repository)
1. Sync the public repository: `scripts/export_public.py` into an empty folder, copy over `~/Projects/estudely-ark-public` (keep its `.git`), review, commit, push. Check its `android/` matches the source that was built.
2. Tag `v<ver>` there and push the tag.
3. `gh release create v<ver>` with the four APKs and an APK-only `SHA256SUMS` (the signing run's file also lists the AAB, which isn't attached), notes from step 1. Compare GitHub's asset digests with the local files.
4. Site: point the direct-download link in `site/public/index.html` at the new universal APK, deploy, check the live page.

## 4. F-Droid
- Submitted 17 Nov 2026: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57820 (recipe `docs/publishing/fdroid-cc.esdy.ark.yml`, describes 2.0; pipeline green).
- After acceptance, F-Droid finds new `v*` tags itself (`UpdateCheckMode: Tags`, `UpdateCheckData` reads `baseVersionCode`, `VercodeOperation` adds the ABI digit) and builds them; `binary:` + `AllowedAPKSigningKeys` let it ship our signed APKs when its build matches.
- Before tagging, a local check is cheap: on the build machine, `fdroid build cc.esdy.ark:<code>` in `~/estudely-android/fdroiddata` (point the recipe at the new commit), then `apksigcopier compare` against the signed APKs.
- Recipe edits must match `fdroid rewritemeta` from fdroidserver master byte for byte (including trailing spaces), so push files rather than paste them in the web editor.

## 5. Google Play (personal account accepted into Play Console 18 Nov 2026)
- Play App Signing: upload Aman's own key as the app signing key (Play Console offers "use existing key", via its PEPK tool) so Play, GitHub and F-Droid installs update each other. The same key signs uploads.
- Upload `release/android/v<ver>/EstudelyArk-<ver>.aab`.
- New personal accounts: closed test with at least 12 testers for 14 days before production access.
- Store listing: text, screenshots, 512 x 512 icon and 1024 x 500 feature graphic from `fastlane/metadata/android/en-US/`.
- Privacy policy URL: https://ark.esdy.cc/privacy (`site/public/privacy.html`, live since 17 Nov 2026).
- Data safety: no data collected or shared; BitTorrent exposes the IP address to peers and trackers (disclosed in the policy).
- Foreground service declaration: `dataSync`, for user-started downloads and storage moves of the library; short video of starting a download and moving the library to an SD card.
- Health apps declaration: the app contains health reference books (not a medical device, no health data).
- Content rating questionnaire; target audience 13+ or all ages.
- Free, no ads, no in-app purchases (required by the non-commercial content licences).
