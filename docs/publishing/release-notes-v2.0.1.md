A new look for Estudely Ark: your downloaded subjects now sit on a bookshelf, and choosing and downloading new ones is clearer.

## What's new

- **Bookshelf:** each subject you've downloaded appears as a book cover. Tap it to open.
- **Subjects:** browse every part of the library, see sizes, and tick what you want before you download.
- **Downloads:** progress for each subject, and a pause/resume button that stays paused even if you close the app.
- **Settings and storage:** Wi-Fi only, sharing, and how much space the library uses, in one place.
- **Dark mode and larger text:** follows your phone's settings; with large text the bookshelf switches to one column.
- **Map:** the map now opens under the app's own toolbar.

Updating from 2.0 keeps everything you've already downloaded.

## Which file to download

Android 8.0 or newer.

| File | For |
|---|---|
| `EstudelyArk-2.0.1-arm64-v8a.apk` (76 MB) | Most phones from the last few years |
| `EstudelyArk-2.0.1-armeabi-v7a.apk` (69 MB) | Older or low-cost phones (32-bit) |
| `EstudelyArk-2.0.1-universal.apk` (207 MB) | Any phone; good for passing on to others |
| `EstudelyArk-2.0.1-x86_64.apk` (75 MB) | Emulators and x86 tablets |

## Check the download

Signing certificate (SHA-256), the same as 2.0: `9c:f9:43:2f:42:70:f8:c3:a5:4c:67:7b:34:9b:31:9e:a2:98:1d:93:fb:22:7e:1f:48:3f:a4:7d:e6:80:44:7d`

```sh
sha256sum -c SHA256SUMS
apksigner verify --print-certs EstudelyArk-2.0.1-universal.apk
```

## Known limits

- The Internet Archive copy of the library is currently unavailable; downloads come from other people and the always-on seeder.
- No turn-by-turn directions on the map.
- English only for now.

Licence: GPL-3.0-or-later. [Privacy policy](https://ark.esdy.cc/privacy).
