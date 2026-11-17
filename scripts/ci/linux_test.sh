#!/bin/sh
# CI test of a (partial) built pack on Linux x64: launchers via `sh`, library server, map assets, Verify, AI.
#   scripts/ci/linux_test.sh PACK [--no-ai]
set -u
PACK=$(cd "$1" && pwd); NOAI=${2:-}
fail=0
check() { name=$1; shift; if "$@" >/tmp/check.out 2>&1; then echo "PASS  $name"; r=PASS; else echo "FAIL  $name"; sed 's/^/  /' /tmp/check.out | tail -15; fail=1; r=FAIL; fi
  [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "| $r | $name |" >> "$GITHUB_STEP_SUMMARY"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
wait_url() { i=0; while [ $i -lt "$2" ]; do [ "$(code "$1")" = 200 ] && return 0; sleep 2; i=$((i+2)); done; return 1; }
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '| Result | Test |\n|---|---|\n' >> "$GITHUB_STEP_SUMMARY"

# Torrent clients and FAT/exFAT drives can drop the executable bit: test the `sh` route on non-executable files.
chmod -x "$PACK"/*.sh "$PACK"/*.command
check 'Verify.sh via sh reports genuine' sh -c "sh '$PACK/Verify.sh' | tee /dev/stderr | grep -q 'genuine and undamaged'"

NO_BROWSER=1 sh "$PACK/Start-Library.sh" > /tmp/lib.log 2>&1 &
LIB=$!
check 'Start-Library.sh serves START-HERE' wait_url http://127.0.0.1:8471/START-HERE.html 60
check 'kiwix-serve runs' wait_url http://127.0.0.1:8470/ 30
check 'ZIM opens by undated alias' test "$(code -L http://127.0.0.1:8470/content/zimgit-water_en/)" = 200
check 'Full-text search inside a ZIM' sh -c "curl -s 'http://127.0.0.1:8470/search?books.name=devdocs_en_python_2026-10&pattern=list' | grep -q results"
check 'Map fonts via HTTP Range (206)' test "$(code -r 0-99 http://127.0.0.1:8471/Core/05-Maps/Desktop-Map/fonts/glyphs.bin)" = 206
check 'Map place search index served' test "$(code http://127.0.0.1:8471/Core/05-Maps/Desktop-Map/places.tsv)" = 200
kill $LIB; sleep 2

if [ "$NOAI" != "--no-ai" ] && [ -f "$PACK/AI/Gemma-4-E4B/gemma-4-E4B_q4_0-it.gguf" ]; then
  AI_ARGS="-t 4" sh "$PACK/Start-AI.sh" > /tmp/ai.log 2>&1 &
  AI=$!
  check 'Start-AI.sh: llama-server becomes healthy' wait_url http://127.0.0.1:8472/health 600
  check 'AI answers a question (text)' sh -c "curl -s -m 900 http://127.0.0.1:8472/v1/chat/completions -H 'Content-Type: application/json' \
    -d '{\"messages\":[{\"role\":\"user\",\"content\":\"What is the capital of India? Answer in one short sentence.\"}],\"max_tokens\":40}' | tee /dev/stderr | grep -q Delhi"
  kill $AI 2>/dev/null; tail -5 /tmp/ai.log
fi
exit $fail
