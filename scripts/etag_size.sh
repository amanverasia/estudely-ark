#!/bin/sh
# Estimate size from Apache ETag "<size-hex>-<mtime-hex>" when no Content-Length is sent.
e=$(curl -sIL "$1" | grep -i '^etag:' | tail -1 | sed -E 's/.*"([0-9a-f]+)-.*/\1/')
printf '%s\t%s\n' "$1" "$((16#$e))"
