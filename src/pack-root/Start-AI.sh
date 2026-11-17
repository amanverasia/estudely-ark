#!/bin/sh
# Estudely Ark: start a local AI assistant (macOS and Linux). Runs entirely on this computer.
#
#   ./Start-AI.sh          use the small model (Gemma 4 E4B)
#   ./Start-AI.sh 12b      use the larger model (Gemma 4 12B)
#
# Then open http://127.0.0.1:8472 in your web browser. Press Ctrl+C to stop.
# The AI can be wrong. For health or safety questions, check the guides in the library.

cd "$(dirname "$0")" || exit 1
ROOT="$(pwd)"

case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)                P=macos-arm64 ;;
  Darwin-x86_64)               P=macos-x64 ;;
  Linux-x86_64)                P=linux-x64 ;;
  Linux-aarch64|Linux-arm64)   P=linux-arm64 ;;
  *) echo "Sorry, this system is not supported: $(uname -sm)"; exit 1 ;;
esac

case "${1:-e4b}" in
  12b|12B) DIR="$ROOT/AI/Gemma-4-12B"; MODEL="gemma-4-12b-it-qat-q4_0.gguf"; MMPROJ="mmproj-gemma-4-12b-it-qat-q4_0.gguf" ;;
  *)       DIR="$ROOT/AI/Gemma-4-E4B"; MODEL="gemma-4-E4B_q4_0-it.gguf";     MMPROJ="gemma-4-E4B-it-mmproj.gguf" ;;
esac
[ -f "$DIR/$MODEL" ] || { echo "Model not found: $DIR/$MODEL"; echo "Download that AI folder first."; exit 1; }

RUNNER="$ROOT/AI/Runner-and-Instructions/llama.cpp/$P"
[ -f "$RUNNER/llama-server" ] || { echo "AI runner not found for $P in $RUNNER"; exit 1; }
[ "$P" = macos-arm64 ] || [ "$P" = macos-x64 ] && xattr -dr com.apple.quarantine "$RUNNER" 2>/dev/null
chmod +x "$RUNNER/llama-server" 2>/dev/null

set --
[ -f "$DIR/$MMPROJ" ] && set -- --mmproj "$DIR/$MMPROJ"   # image/audio input, if that file was downloaded
PORT=${AI_PORT:-8472}
HOST=${AI_HOST:-127.0.0.1}
CTX=${AI_CTX:-8192}

echo "Starting the AI. The first start can take a minute while the model loads."
echo "When you see 'server is listening', open http://127.0.0.1:$PORT in your web browser."
echo
cd "$RUNNER" || exit 1
# shellcheck disable=SC2086
exec ./llama-server --offline -m "$DIR/$MODEL" "$@" \
  --host "$HOST" --port "$PORT" -c "$CTX" --fit on --reasoning "${AI_THINK:-off}" ${AI_ARGS:-}
# AI_THINK=on gives better answers to hard questions, but is much slower on ordinary computers.
