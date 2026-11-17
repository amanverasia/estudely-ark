#!/bin/sh
# Start the Estudely Ark seeder unless it is already running (e.g. from an @reboot crontab line).
# Optional: SEED_INTERFACE=eth0 SEED_PORT=6881 (set them in the crontab line or the environment).
cd "$HOME/estudely-seeder" || exit 1
if [ -f seed.pid ] && kill -0 "$(cat seed.pid)" 2>/dev/null && grep -q seed.py "/proc/$(cat seed.pid)/cmdline" 2>/dev/null; then
  echo "already running: $(cat seed.pid)"; exit 0
fi
setsid nohup nice -n 19 ionice -c 3 venv/bin/python -I seed.py Estudely-Ark-v1.0-2026-11.torrent /mnt/volume1/estudely-ark/data \
  --min-free-gb 20 ${SEED_INTERFACE:+--interface "$SEED_INTERFACE"} --port "${SEED_PORT:-6881}" >> seed.log 2>&1 < /dev/null &
echo $! > seed.pid
echo "started: $(cat seed.pid)"
