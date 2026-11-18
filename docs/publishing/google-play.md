# Google Play: first release (Estudely Ark 2.1)

Personal developer account, accepted into Play Console 18 Nov 2026. Play is the last channel; GitHub and F-Droid come first.
Everything below is a proposal for Aman to check and paste. Aman clicks every button in Play Console; the keystore password is only ever typed by Aman.

## 0. Rules that shape the plan

- **New personal accounts can't publish to production straight away.** A closed test must run with **at least 12 testers opted in for 14 days in a row**, then Aman applies for production access (Play asks a few questions about the test).
- **The package name `cc.esdy.ark` is permanent** once uploaded.
- **Same signing key everywhere:** Play must sign with Aman's existing key (certificate SHA-256 `9cf9432f…447d`), so Play, GitHub and F-Droid installs update one another. That means choosing "use my own key" **before the first upload**: once Play generates a key for the app, it can't be swapped back.
- Free, no ads, no in-app purchases (required by the non-commercial content licences).

## 1. Create the app

Play Console → Create app:
- App name: `Estudely Ark`
- Default language: English (United States), en-US
- App or game: App
- Free or paid: Free
- Tick the declarations (Developer Program Policies, US export laws).

## 2. App signing with Aman's own key (before any upload)

**Done 18 Nov 2026.** App `cc.esdy.ark` created (en-US? check default language), Play had generated its own key (SHA-256 `ED:3D:B2:9F…74:5E`) on creation; replaced before any upload via Protected with Play → App signing → Change key → "Provide a copy of your app signing key" (PEPK). Play's app signing key is now Aman's: `9C:F9:43:2F:…:44:7D`. **Automatic protection turned off** by Aman (Protected with Play), 18 Nov: it blocks copies not installed from Play, which would break phone-to-phone APK sharing. Keep it off.

Test and release → App integrity → App signing → choose to use your own key, "Export and upload a key from Java keystore". Play shows a download link for `pepk.jar` and for this app's `encryption_public_key.pem`. Save both into `~/.estudely/` and run this on the Mac (it asks for the keystore and key passwords):

```sh
cd ~/.estudely
java -jar pepk.jar --keystore=ark-release.jks --alias=ark --output=ark-play-signing-key.zip \
  --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
```

Upload `ark-play-signing-key.zip` on that page. Delete the zip afterwards (it holds the key, encrypted for Google). If Play shows a slightly different command, use Play's.

**Upload key:** the bundle `release/android/v2.1/EstudelyArk-2.1.aab` is already signed with the same key, so the upload key is the app signing key. That's simplest. (A separate upload key, which can be reset if lost, can be added later under App integrity.)

## 3. Store listing (Grow users → Store presence → Main store listing)

- App name, short description, full description: `fastlane/metadata/android/en-US/` (`title.txt`, `short_description.txt`, `full_description.txt`). Lengths checked: 12/30, 79/80, 1785/4000.
- App icon 512 × 512: `fastlane/metadata/android/en-US/images/icon.png`
- Feature graphic 1024 × 500: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Phone screenshots: `release/play/screenshots/` (5 files, 1140 × 2280, taken from 2.1 on the moto g52 on 18 Nov: bookshelf, subjects, Computing subject, DevDocs reader, settings). Status bar trimmed and 30 px of the app background added on each side, because Play rejects a long side more than twice the short side (the phone's 1080 × 2400 is 2.22). The same five files are now also the F-Droid set in `fastlane/.../phoneScreenshots/` (byte-identical, checked 18 Nov).
- Category: Education (alternative: Books & Reference). Tags: education, reference, offline.
- Contact email: required by Play (shown publicly). Website: https://ark.esdy.cc

## 4. App content (Policy → App content)

- **Privacy policy:** https://ark.esdy.cc/privacy
- **App access:** all functionality is available without special access (no login).
- **Ads:** no ads.
- **Content rating (IARC questionnaire):** category "Reference, news or educational". No violence, sexual content, profanity, drugs or gambling created by the app. Users can't talk to each other and the app has no user-generated content or social features. It does **not** give unrestricted web access: pages come only from the downloaded archives, and every other address is blocked. Expected result: Everyone / 3+ to Teen; the health books (childbirth, sexual health) and Wikipedia may push it to Teen. Answer the questions truthfully; the rating follows.
- **Content rating: done 18 Nov 2026** (IARC, contact support@estudely.com). Answered honestly for the downloadable library (Simple English Wikipedia with pictures has articles on profanity, slurs and drugs, and war photos): online content yes; violence yes, visual yes, not gory, not the focus; sexuality no (only natural/scientific); language yes incl. strong, discriminatory and sexual expletives (as encyclopedia articles); drugs yes, visual yes; no gambling, purchases, location sharing or browser; primarily educational yes. Result: IARC generic 12+ (India), ESRB Everyone 10+, PEGI Parental guidance, USK 6+, ClassInd L, Russia/Korea 12+.
- **Target audience:** 13 and over (13-15, 16-17, 18+). Not under 13: that brings in the Families policy and its extra requirements.
- **News app:** no.
- **Data safety:**
  - Data collected: none. The app has no accounts, analytics, ads SDK or crash reporting, and sends nothing to the developer.
  - Data shared: none. BitTorrent shows the device's IP address to other peers and to public trackers, and Internet Archive sees it for direct downloads. These are user-started transfers and disclosed in the privacy policy. (Aman's call: if in doubt, declaring "IP address… shared for app functionality" is the cautious answer.)
  - Encrypted in transit: partly (Internet Archive over HTTPS; BitTorrent traffic is not). Answer "No" to "all user data encrypted in transit" if Play requires all; with no user data collected the question may not appear.
  - Users can request data deletion: not applicable (nothing is collected).
- **Government app:** no. **Financial features:** none.
- **Health apps declaration:** the app has health content: select medical / health reference and education. It is not a medical device, gives no diagnosis and collects no health data.
- **Foreground service permissions** (targetSdk 36): type `dataSync`.
  - Use: user-started downloads of library subjects (BitTorrent / Internet Archive) and user-started moves of the library between phone storage and an SD card. Both show a persistent notification with progress and a cancel/pause action, and stop when the work ends.
  - Impact if deferred: a download of several GB, or a move, would be killed when the user leaves the app, leaving partial files to redo.
  - Video: `release/play/ark-foreground-service-download.mp4` (32 s, release 2.1 on the moto g52: pick Food & growing → Add to downloads → notification permission prompt → Downloads screen with progress → the ongoing "Downloading: 16% of 0.2 GB" notification). Upload to YouTube as **unlisted** and paste the link (Play wants a link, not a file).
- Other permissions: `RECEIVE_BOOT_COMPLETED` (re-schedules the optional sharing job), `POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE` (Wi-Fi-only downloads). No sensitive permissions (no storage, location, contacts, camera or install-packages).

## 5. Closed test (Test and release → Testing → Closed testing)

- Create a track (for example "Closed testing - Estudely community").
- Testers: an email list or a Google Group with **12 or more** people who actually opt in through the link and keep the app installed. Aim for 15 or more in case some drop out. The 14 days count from when 12 have opted in.
- Countries: India (add others as wanted).
- Create release → upload `release/android/v2.1/EstudelyArk-2.1.aab` → release name `2.1` → release notes (en-US) from `fastlane/metadata/android/en-US/changelogs/2100.txt` (337/500 characters).
- Send for review (first review can take several days).
- After 14 days with 12+ opted-in testers: Dashboard → Apply for production. Then a production release of the same or newer bundle.

## Status

- **18 Nov 2026: first closed-test release sent for review.** Track "Closed testing - Alpha", release 2100 (2.1) from `release/android/v2.1/EstudelyArk-2.1.aab`, full rollout to the track, 177 countries plus rest of world, testers from the email list "Cyber". Store listing (en-US), content rating (12+), target audience 13+, data safety (none collected or shared), health (medical reference and education), financial features (none), advertising ID (no; checked: the APK has no AD_ID permission and no Play Services/Firebase/ads libraries), foreground service declaration (dataSync: network "Other" for downloads, with the unlisted YouTube video) submitted with it. **Local processing (SD-card moves via `LibraryMoveService`) was left unticked** for lack of an SD-card video; if review asks about it, record a move on a phone with a microSD card or an emulator with a virtual SD card and add "Local processing → Other".
- Next: testers must accept the opt-in link and keep the app installed; the 14 days start once 12 have opted in. Then Dashboard → Apply for production.

## 5b. Publishing 2.1.1 to the closed test (when the first review is done, or in parallel)

- Test and release → Testing → Closed testing → the existing track → Create release.
- Upload `release/android/v2.1.1/EstudelyArk-2.1.1.aab` (already signed with Aman's key; SHA-256 of the AAB is in its `SHA256SUMS`, and GitHub's v2.1.1 release carries the same-named APKs from the same build).
- Release name `2.1.1`; release notes (en-US) from `fastlane/metadata/android/en-US/changelogs/2112.txt` (338/500 characters): PDF zoom, remembered page, archive titles.
- Full rollout to the same track and testers. 2.1.1 installs over 2.1 with the same signing key, keeping downloaded subjects.
- The 14-day clock is not reset by a new release; production access still depends on 12+ testers opted in.
- Note for the next version (not in 2.1.1): the ZIM-search fix for archives without a title index (`ZimActivity`, moto g52, 18 Nov) ships after this; upload then as usual.

## 6. Takedown risks to keep in mind

- Content licences: Hesperian, PhET and iFixit are non-commercial. The app is free with no ads, which keeps it compatible; never add paid features.
- BitTorrent apps are allowed on Play; the policy issue would be copyrighted content. Everything in the catalogue is openly licensed and listed with credits in the About screen.
- The app never installs APKs and doesn't ask for `REQUEST_INSTALL_PACKAGES`. (The desktop pack contains Android APKs for Kiwix and Organic Maps, but the app doesn't offer them.)
