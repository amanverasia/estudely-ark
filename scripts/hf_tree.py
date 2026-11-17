#!/usr/bin/env python3
"""Read HF tree JSON on stdin (curl .../api/models/<repo>/tree/<rev>?recursive=true); print path, bytes, sha256."""
import json, sys
for x in json.load(sys.stdin):
    if x.get("type") != "file": continue
    print(f"{x['path']}\t{x['size']}\t{(x.get('lfs') or {}).get('oid','')}")
