# Android release checklist (v2.0)

Public copy and uploads need Aman's approval of the text and the destination first.

## Build and sign
1. `scripts/android_sync.sh assembleRelease bundleRelease` (build machine; unsigned per-ABI + universal APKs, and the AAB).
2. `scripts/sign_apk.sh` on a Mac: asks for the keystore password, signs, verifies, writes `release/android/v2.0/` + `SHA256SUMS`.
3. Install the arm64 APK on a real phone; download one small module; open it.

## GitHub release (public repository)
- Tag `v2.0` on the public repository (made with `scripts/export_public.py`).
- Attach the four APKs and `SHA256SUMS`; release notes from `fastlane/metadata/android/en-US/changelogs/2000.txt`; give the signing certificate's SHA-256 so people can check updates.

## F-Droid
- Merge request to fdroiddata with `docs/publishing/fdroid-cc.esdy.ark.yml` (fill in the repository URL).
- Listing text and screenshots: `fastlane/metadata/android/en-US/` (in the repository; F-Droid reads it from there).
- F-Droid signs with its own key: its build and the GitHub/Play builds can't update each other unless reproducible builds are set up later.

## Google Play (personal account)
- Play App Signing: upload Aman's own key as the app signing key (Play Console offers "use existing key", via its PEPK tool) so Play, GitHub and signed APKs update each other. The same key signs uploads.
- Upload `EstudelyArk-2.0.aab`.
- New personal accounts: closed test with at least 12 testers for 14 days before production access.
- Store listing: text from `fastlane/`, screenshots from `fastlane/.../phoneScreenshots/`, 512 x 512 icon and 1024 x 500 feature graphic (still to make).
- Privacy policy URL: https://ark.esdy.cc/privacy (`site/public/privacy.html`, live since 17 Nov 2026).
- Data safety: no data collected or shared; BitTorrent exposes the IP address to peers and trackers (disclosed in the policy).
- Foreground service declaration: `dataSync`, for user-started downloads of the library; short video of starting a download.
- Health apps declaration: the app contains health reference books (not a medical device, no health data).
- Content rating questionnaire; target audience 13+ or all ages.
- Free, no ads, no in-app purchases (required by the non-commercial content licences).
