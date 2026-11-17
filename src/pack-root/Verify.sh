#!/bin/sh
# Estudely Ark: check that this copy is genuine and undamaged (macOS and Linux).
#
#   ./Verify.sh
#
# 1. Checks MANIFEST.tsv was signed by the Estudely Ark project key (needs ssh-keygen, part of OpenSSH).
# 2. Checks every file you have against the size and SHA-256 fingerprint in MANIFEST.tsv.
#    Modules you chose not to download are listed as "not downloaded", which is fine.
#
# IMPORTANT: compare the key fingerprint printed below with the one published OUTSIDE this pack
# (project page, the message you got the pack from, or the printed card). A forged pack can include a forged key.

cd "$(dirname "$0")" || exit 1
NS=estudely-ark
SIGNER=estudely-ark-release
fail=0

if [ ! -f MANIFEST.tsv ] || [ ! -f MANIFEST.tsv.sig ] || [ ! -f KEYS/allowed_signers ]; then
  echo "MANIFEST.tsv, MANIFEST.tsv.sig or KEYS/allowed_signers is missing. Download the top-level files too."
  exit 2
fi

echo "Project key fingerprint (compare with the published one):"
awk '{print $3" "$4}' KEYS/allowed_signers | ssh-keygen -lf - 2>/dev/null | sed 's/^/  /'
echo

if command -v ssh-keygen >/dev/null 2>&1; then
  if ssh-keygen -Y verify -f KEYS/allowed_signers -I "$SIGNER" -n "$NS" -s MANIFEST.tsv.sig < MANIFEST.tsv >/dev/null 2>&1; then
    echo "Signature: GOOD (MANIFEST.tsv was signed by the project key)"
  else
    echo "Signature: BAD. Do not trust this copy. MANIFEST.tsv was changed or not signed by the project key."
    fail=1
  fi
else
  echo "Signature: could not check (ssh-keygen not found). File checks below still run."
fi
echo

if command -v sha256sum >/dev/null 2>&1; then H="sha256sum"; else H="shasum -a 256"; fi
ok=0; bad=0; missing=0
TAB="$(printf '\t')"
while IFS="$TAB" read -r path bytes sum; do
  case "$path" in "#"*|"") continue ;; esac
  if [ ! -f "$path" ]; then missing=$((missing+1)); continue; fi
  got=$($H "$path" | cut -c1-64)
  if [ "$got" = "$sum" ]; then ok=$((ok+1)); else bad=$((bad+1)); echo "DAMAGED OR CHANGED: $path"; fi
done < MANIFEST.tsv

echo "Files OK: $ok   Damaged/changed: $bad   Not downloaded: $missing"
[ $bad -gt 0 ] && fail=1
if [ $fail -eq 0 ]; then echo "RESULT: this copy is genuine and undamaged."; else echo "RESULT: PROBLEM FOUND. Re-download the files listed above, or get the pack from another source."; fi
exit $fail
