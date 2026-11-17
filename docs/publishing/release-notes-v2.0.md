Estudely Ark for Android puts a library on your phone that keeps working with no internet: health books, an encyclopedia, a map of all of India, repair guides, school science and programming manuals. Pick the parts you want, download them once, and read them offline.

This is the first release of the Android app. It downloads the same library as the desktop pack ([ark.esdy.cc](https://ark.esdy.cc)).

## What it does

- Choose parts of the library (Health 2.6 GB, Map of India 2.6 GB, Food 210 MB, Computing 111 MB and more) and download only those.
- Downloads use BitTorrent and wait for Wi-Fi unless you allow mobile data.
- Every part is checked against the library's signed file list before it opens.
- Read offline: books with full-text search, PDFs, and a map of India with search for towns, villages, hospitals and stations.
- Optional sharing: on Wi-Fi while charging, your phone passes the library on to others. Off until you switch it on.
- Free, no ads, no accounts, no tracking. [Privacy policy](https://ark.esdy.cc/privacy).

## Which file to download

Android 8.0 or newer.

| File | For |
|---|---|
| `EstudelyArk-2.0-arm64-v8a.apk` (76 MB) | Most phones from the last few years |
| `EstudelyArk-2.0-armeabi-v7a.apk` (69 MB) | Older or low-cost phones (32-bit) |
| `EstudelyArk-2.0-universal.apk` (207 MB) | Any phone; good for passing on to others |
| `EstudelyArk-2.0-x86_64.apk` (75 MB) | Emulators and x86 tablets |

Not sure? Take the universal one.

## Check the download

Signing certificate (SHA-256): `9c:f9:43:2f:42:70:f8:c3:a5:4c:67:7b:34:9b:31:9e:a2:98:1d:93:fb:22:7e:1f:48:3f:a4:7d:e6:80:44:7d`

```sh
sha256sum -c SHA256SUMS
apksigner verify --print-certs EstudelyArk-2.0-universal.apk
```

Later updates are signed with the same certificate.

## Known limits

- The Internet Archive copy of the library is currently unavailable; downloads come from other people and the always-on seeder.
- No turn-by-turn directions on the map.
- English only for now.

Licence: GPL-3.0-or-later. The library's content keeps its own licences; some of it (Hesperian, PhET, iFixit) may only be shared for free.
