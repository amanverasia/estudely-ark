#!/bin/sh
# Sign the Android release APKs and the Play bundle (AAB) on the maintainer's machine. The key never leaves it
# and the password is typed here, never stored. Unsigned files come from the build machine:
#   scripts/android_sync.sh assembleRelease bundleRelease
#
#   scripts/sign_apk.sh [KEYSTORE] [ALIAS]     default ~/.estudely/ark-release.jks, alias "ark"
#
# Output: release/android/v<version>/EstudelyArk-<version>-<abi>.apk, EstudelyArk-<version>.aab, SHA256SUMS
# Needs: java + jarsigner, tools/android/apksigner.jar (copied from Android build-tools; it's plain Java).
set -eu
KS="${1:-$HOME/.estudely/ark-release.jks}"
ALIAS="${2:-ark}"
cd "$(dirname "$0")/.."   # repo root, whatever folder this is run from
. scripts/local.env 2>/dev/null || { echo "missing scripts/local.env (see local.env.example)" >&2; exit 1; }
W="$ARK_BUILD_HOST"; K="$ARK_SSH_KEY"
SRC="estudely-ark-android/app/build/outputs/apk/release"
SIGNER="tools/android/apksigner.jar"
[ -f "$KS" ] || { echo "keystore not found: $KS" >&2; exit 1; }
[ -f "$SIGNER" ] || { echo "missing $SIGNER (copy build-tools/<ver>/lib/apksigner.jar)" >&2; exit 1; }

VER=$(ssh -n -o BatchMode=yes -i "$K" "$W" "grep -o '\"versionName\": \"[^\"]*' $SRC/output-metadata.json | head -1" | sed 's/.*"//')
OUT="release/android/v$VER"
mkdir -p "$OUT/unsigned"
scp -q -i "$K" "$W:$SRC/*-release-unsigned.apk" "$OUT/unsigned/"
AAB="estudely-ark-android/app/build/outputs/bundle/release/app-release.aab"
scp -q -i "$K" "$W:$AAB" "$OUT/unsigned/" 2>/dev/null || echo "(no AAB on the build machine; run bundleRelease for Play)"

printf "Keystore password for %s: " "$KS"; stty -echo 2>/dev/null || true; read -r ARK_KS_PASS; stty echo 2>/dev/null || true; echo
export ARK_KS_PASS
for u in "$OUT"/unsigned/*-release-unsigned.apk; do
  abi=$(basename "$u" | sed 's/^app-//; s/-release-unsigned.apk$//')
  out="$OUT/EstudelyArk-$VER-$abi.apk"
  java --enable-native-access=ALL-UNNAMED -jar "$SIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass env:ARK_KS_PASS --key-pass env:ARK_KS_PASS --out "$out" "$u"
  java --enable-native-access=ALL-UNNAMED -jar "$SIGNER" verify "$out"
  echo "signed $out"
done
if [ -f "$OUT/unsigned/app-release.aab" ]; then
  # App bundles use JAR signing; Play App Signing then re-signs the APKs it serves.
  jarsigner -keystore "$KS" -storepass:env ARK_KS_PASS -keypass:env ARK_KS_PASS -sigalg SHA256withRSA -digestalg SHA-256 \
    -signedjar "$OUT/EstudelyArk-$VER.aab" "$OUT/unsigned/app-release.aab" "$ALIAS" >/dev/null
  jarsigner -verify "$OUT/EstudelyArk-$VER.aab" | tail -1
  echo "signed $OUT/EstudelyArk-$VER.aab"
fi
unset ARK_KS_PASS
rm -f "$OUT"/*.idsig
java --enable-native-access=ALL-UNNAMED -jar "$SIGNER" verify --print-certs "$OUT/EstudelyArk-$VER-universal.apk" | grep -E "SHA-256 digest|DN:"
(cd "$OUT" && shasum -a 256 EstudelyArk-*.apk EstudelyArk-*.aab 2>/dev/null > SHA256SUMS; cat SHA256SUMS)
rm -rf "$OUT/unsigned"
