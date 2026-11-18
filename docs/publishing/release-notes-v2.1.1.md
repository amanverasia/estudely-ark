Estudely Ark 2.1.1 makes PDFs easier to read on a phone and shows the titles stored inside offline archives.

## What's new

- **PDF zoom:** pinch, double-tap, or use the − and + buttons. Drag a zoomed page sideways to read across it.
- **Smoother, resumable books:** pages render in the background, and Ark remembers the page you stopped on in each book.
- **Real archive titles:** subject lists show the title stored in the archive, such as “C Docs” or “WikiMed Medical Encyclopedia”, instead of the file name.

Updating from 2.1 or 2.0 keeps your downloaded subjects and settings.

Android 8.0 or newer. Tested on a moto g52 (Android 16): PDF paging, zoom by buttons, double-tap and pinch, horizontal panning, remembered page, and archive titles in the reader.

## Which file to download

| File | For |
|---|---|
| `EstudelyArk-2.1.1-arm64-v8a.apk` (76 MB) | Most phones from the last few years |
| `EstudelyArk-2.1.1-armeabi-v7a.apk` (69 MB) | Older or low-cost phones (32-bit) |
| `EstudelyArk-2.1.1-universal.apk` (207 MB) | Any phone; good for passing on to others |
| `EstudelyArk-2.1.1-x86_64.apk` (75 MB) | Emulators and x86 tablets |

## Check the download

Signing certificate (SHA-256), unchanged: `9c:f9:43:2f:42:70:f8:c3:a5:4c:67:7b:34:9b:31:9e:a2:98:1d:93:fb:22:7e:1f:48:3f:a4:7d:e6:80:44:7d`

```sh
sha256sum -c SHA256SUMS
apksigner verify --print-certs EstudelyArk-2.1.1-universal.apk
```

## Known limits

- No turn-by-turn directions on the map.
- English only for now.

Licence: GPL-3.0-or-later. [Privacy policy](https://ark.esdy.cc/privacy).
