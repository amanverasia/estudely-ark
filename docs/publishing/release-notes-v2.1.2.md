Estudely Ark 2.1.2 makes search reliable in every offline archive.

## What's new

- **Search no longer shows a raw error.** Some archives, such as the food-preparation guide, have no title index. Searching them used to show libzim's “Cannot get entry for end iterator” message. Ark now ends the list cleanly and says “Nothing found. Try another word.”
- **PDF search results open properly.** If a search result is a PDF, it opens in the PDF viewer instead of a blank page.
- **DevDocs names are readable.** Where the programming manuals list two names, such as `qsort` and `qsort_s`, they now stack on separate lines instead of running together.
- Everything else is unchanged: PDF zoom and remembered pages, real archive titles, SD-card storage, themes and the offline map.

Updating from 2.1.1 or earlier keeps your downloaded subjects and settings. The signing key is unchanged.

Android 8.0 or newer. Tested on a moto g52 (Android 16): search in an archive without a title index, a PDF inside an archive, and DevDocs C search.

## Which file to download

| File | For |
|---|---|
| `EstudelyArk-2.1.2-arm64-v8a.apk` (76 MB) | Most phones from the last few years |
| `EstudelyArk-2.1.2-armeabi-v7a.apk` (69 MB) | Older or low-cost phones (32-bit) |
| `EstudelyArk-2.1.2-universal.apk` (207 MB) | Any phone; good for passing on to others |
| `EstudelyArk-2.1.2-x86_64.apk` (75 MB) | Emulators and x86 tablets |

## Check the download

Signing certificate (SHA-256), unchanged: `9c:f9:43:2f:42:70:f8:c3:a5:4c:67:7b:34:9b:31:9e:a2:98:1d:93:fb:22:7e:1f:48:3f:a4:7d:e6:80:44:7d`

```sh
sha256sum -c SHA256SUMS
apksigner verify --print-certs EstudelyArk-2.1.2-universal.apk
```

## Known limits

- No turn-by-turn directions on the map.
- English only for now.

Licence: GPL-3.0-or-later. [Privacy policy](https://ark.esdy.cc/privacy).
