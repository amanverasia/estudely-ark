#!/bin/sh
# Fetch a small, representative subset of the release downloads for CI tests (about 5.3 GB, mostly the E4B model).
#   scripts/ci/fetch_ci_subset.sh DEST [--no-ai]
# Same relative paths as release/downloads, so make_release_spec.py builds a partial pack from it
# (missing items are skipped). Checks SHA-256 where we have it.
set -eu
D=$1; AI=${2:-}
mkdir -p "$D"
K=https://mirrors.dotsrc.org/kiwix
get() {  # relpath url [sha256]
  mkdir -p "$D/$(dirname "$1")"
  [ -s "$D/$1" ] || curl -fsSL --retry 5 --retry-delay 5 -o "$D/$1" "$2"
  if [ -n "${3:-}" ]; then
    got=$( (sha256sum "$D/$1" 2>/dev/null || shasum -a 256 "$D/$1") | cut -c1-64)
    [ "$got" = "$3" ] || { echo "CHECKSUM MISMATCH $1"; exit 1; }
  fi
  echo "ok $1"
}
get zim/zimgit-water_en_2024-08.zim $K/zim/other/zimgit-water_en_2024-08.zim
get zim/devdocs_en_python_2026-10.zim $K/zim/devdocs/devdocs_en_python_2026-10.zim
for f in fm 01 02; do
  get hesperian/en_wtnd_2025/en_wtnd_2025_$f.pdf https://hesperian.org/wp-content/uploads/pdf/en_wtnd_2025/en_wtnd_2025_$f.pdf
done
get readers/kiwix-tools_win-x86_64-3.8.1.zip $K/release/kiwix-tools/kiwix-tools_win-x86_64-3.8.1.zip
get readers/kiwix-tools_linux-x86_64-3.8.1.tar.gz $K/release/kiwix-tools/kiwix-tools_linux-x86_64-3.8.1.tar.gz
get readers/miniserve-0.35.0-x86_64-pc-windows-msvc.exe https://github.com/svenstaro/miniserve/releases/download/v0.35.0/miniserve-0.35.0-x86_64-pc-windows-msvc.exe 3229e75b40b3870382d23c9a0ecec880b9bd5f6e49160ce602bb15d00f275b6a
get readers/miniserve-0.35.0-x86_64-unknown-linux-musl https://github.com/svenstaro/miniserve/releases/download/v0.35.0/miniserve-0.35.0-x86_64-unknown-linux-musl c630ee030d5d9d83c88c5cc72f43ae215b0c214d64fd7afc92244fe369af2964
L=https://github.com/ggml-org/llama.cpp/releases/download/b12379
for a in win-cpu-x64.zip win-cpu-arm64.zip ubuntu-x64.tar.gz; do get ai/llama-b12379-bin-$a $L/llama-b12379-bin-$a; done
if [ "$AI" != "--no-ai" ]; then
  get ai/gemma-4-E4B_q4_0-it.gguf https://huggingface.co/google/gemma-4-E4B-it-qat-q4_0-gguf/resolve/4b4a2c1d584be7264f87aac328a1bc739ce81b6c/gemma-4-E4B_q4_0-it.gguf 676c35070db6dbe52f93e9c864ee0fba4eddea94b9c875d9cb10daff453fbaee
fi
"$(dirname "$0")/../fetch_mapviewer.sh" "$D"
if [ ! -f "$D/geonames/IN.txt" ]; then
  get geonames/IN.zip https://download.geonames.org/export/dump/IN.zip
  get geonames/admin1CodesASCII.txt https://download.geonames.org/export/dump/admin1CodesASCII.txt
  unzip -o -q "$D/geonames/IN.zip" IN.txt -d "$D/geonames"
fi
