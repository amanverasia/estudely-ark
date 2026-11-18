Estudely Ark 2.1 lets you move the library between phone storage and an SD card, and makes long documents easier to read on a phone.

## What's new

- **SD-card storage:** choose Phone storage or SD card under Settings → Library → Storage location. Ark copies and checks every file before switching and removing the old copy. A move can continue in the background and can be cancelled during copying.
- **Safe interruption handling:** if a move is interrupted, Ark checks the active copy before deleting anything. If the card is removed, the app explains that it is unavailable instead of starting a replacement library elsewhere.
- **Reading:** programming documentation (DevDocs) fits phone screens: articles use the full width, code keeps its layout and scrolls sideways, and a full-screen Contents panel opens at the current page. Archive pages follow the app's dark theme, and reading text size is adjustable.
- **Appearance:** choose System, Light or Dark.
- **Library checks and app information:** re-check saved subjects against the signed file list, and open the new About screen for the version, signing certificate, source, privacy, licences and credits.

Updating keeps your downloaded subjects. Moving them to an SD card is optional. Ark uses its own app folder on the card; Android deletes that folder if you uninstall Ark.

Android 8.0 or newer. SD moves have been tested both ways with a FAT32 card on an Android 16 emulator, including cold restart, signed verification and card removal. Physical SD-card hardware has not been tested.

## Which file to download

| File | For |
|---|---|
| `EstudelyArk-2.1-arm64-v8a.apk` (76 MB) | Most phones from the last few years |
| `EstudelyArk-2.1-armeabi-v7a.apk` (69 MB) | Older or low-cost phones (32-bit) |
| `EstudelyArk-2.1-universal.apk` (207 MB) | Any phone; good for passing on to others |
| `EstudelyArk-2.1-x86_64.apk` (75 MB) | Emulators and x86 tablets |

## Check the download

Signing certificate (SHA-256), unchanged: `9c:f9:43:2f:42:70:f8:c3:a5:4c:67:7b:34:9b:31:9e:a2:98:1d:93:fb:22:7e:1f:48:3f:a4:7d:e6:80:44:7d`

```sh
sha256sum -c SHA256SUMS
apksigner verify --print-certs EstudelyArk-2.1-universal.apk
```

## Known limits

- No turn-by-turn directions on the map.
- English only for now.

Licence: GPL-3.0-or-later. [Privacy policy](https://ark.esdy.cc/privacy).
