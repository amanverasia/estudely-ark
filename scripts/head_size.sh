#!/bin/sh
# Print "<url>\t<bytes>" using a HEAD request (follows redirects).
printf '%s\t%s\n' "$1" "$(curl -sIL "$1" | grep -i '^content-length:' | tail -1 | tr -dc 0-9)"
