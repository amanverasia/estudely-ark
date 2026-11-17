#!/usr/bin/env python3
"""Minimal BitTorrent HTTP tracker for LOCAL TESTS ONLY (binds 127.0.0.1).

Usage: python3 -I scripts/mini_tracker.py [port]     announce URL: http://127.0.0.1:<port>/announce
Keeps peers in memory, returns compact IPv4 peer lists. Not for public use.
"""
import socket, struct, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote_to_bytes

PEERS = {}  # info_hash -> {(ip, port): last_seen}


def bencode(x):
    if isinstance(x, int):
        return b"i%de" % x
    if isinstance(x, bytes):
        return b"%d:%s" % (len(x), x)
    if isinstance(x, str):
        return bencode(x.encode())
    if isinstance(x, dict):
        return b"d" + b"".join(bencode(k) + bencode(v) for k, v in sorted(x.items())) + b"e"
    raise TypeError(type(x))


class H(BaseHTTPRequestHandler):
    def do_GET(self):
        if not self.path.startswith("/announce"):
            self.send_error(404); return
        raw = self.path.split("?", 1)[1] if "?" in self.path else ""
        q = {}
        for part in raw.split("&"):
            if "=" in part:
                k, v = part.split("=", 1)
                q[k] = unquote_to_bytes(v)
        ih, port = q.get("info_hash"), int(q.get("port", b"0"))
        if not ih or len(ih) != 20:
            body = bencode({"failure reason": "bad info_hash"})
        else:
            peers = PEERS.setdefault(ih, {})
            me = (self.client_address[0], port)
            if q.get("event") == b"stopped":
                peers.pop(me, None)
            else:
                peers[me] = time.time()
            now = time.time()
            live = [p for p, t in peers.items() if now - t < 120 and p != me]
            compact = b"".join(socket.inet_aton(ip) + struct.pack(">H", pt) for ip, pt in live)
            body = bencode({"interval": 10, "min interval": 5, "peers": compact})
            print(f"announce {ih.hex()[:8]} from {me} event={q.get('event', b'').decode()} -> {len(live)} peers", flush=True)
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 6969
    ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
