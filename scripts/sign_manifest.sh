#!/bin/sh
# Maintainer only: sign a built pack's MANIFEST.tsv with the project's Ed25519 SSH key.
#
#   scripts/sign_manifest.sh PRIVATE_KEY PACK_DIR
#
# Writes PACK_DIR/MANIFEST.tsv.sig and PACK_DIR/KEYS/allowed_signers (public key only).
# The private key must never be inside PACK_DIR or this repository.
set -eu
KEY=$1; PACK=$2
case "$(cd "$(dirname "$KEY")" && pwd)/" in "$(cd "$PACK" && pwd)/"*) echo "refusing: private key is inside the pack"; exit 1;; esac
mkdir -p "$PACK/KEYS"
printf 'estudely-ark-release namespaces="estudely-ark" %s\n' "$(cut -d' ' -f1,2 "$KEY.pub")" > "$PACK/KEYS/allowed_signers"
rm -f "$PACK/MANIFEST.tsv.sig"
ssh-keygen -Y sign -f "$KEY" -n estudely-ark "$PACK/MANIFEST.tsv"
echo "signed. Fingerprint to publish outside the pack:"
ssh-keygen -lf "$KEY.pub"
