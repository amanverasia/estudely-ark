#!/usr/bin/env python3
"""LOCAL TEST peer using libtorrent (127.0.0.1 only; DHT, LSD, UPnP, NAT-PMP off).

  tools/lt-venv/bin/python scripts/lt_peer.py TORRENT SAVE_DIR PORT [--only PREFIX ...] [--seconds N]

SAVE_DIR is the folder that contains (or will contain) the torrent's top folder.
With --only, every other file gets priority 0 (not downloaded). Prints progress, exits when
the wanted files are complete (or after --seconds), and lists any unwanted files that appeared on disk.
"""
import argparse, os, time
import libtorrent as lt

ap = argparse.ArgumentParser()
ap.add_argument("torrent"); ap.add_argument("save"); ap.add_argument("port", type=int)
ap.add_argument("--only", action="append", default=[])
ap.add_argument("--seconds", type=int, default=0, help="stay up this long (seeding); 0 = exit when complete")
a = ap.parse_args()

s = lt.session({
    "listen_interfaces": f"127.0.0.1:{a.port}", "enable_dht": False, "enable_lsd": False,
    "enable_upnp": False, "enable_natpmp": False, "allow_multiple_connections_per_ip": True,
    "alert_mask": lt.alert_category.error | lt.alert_category.status,
})
ti = lt.torrent_info(a.torrent)
fs = ti.files()
prio = None
if a.only:
    prio = []
    for i in range(fs.num_files()):
        rel = fs.file_path(i).split("/", 1)[1] if "/" in fs.file_path(i) else fs.file_path(i)
        prio.append(4 if any(rel.startswith(p) for p in a.only) else 0)
p = lt.add_torrent_params()
p.ti = ti; p.save_path = a.save
if prio is not None:
    p.file_priorities = prio
h = s.add_torrent(p)
t0 = time.time()
while True:
    st = h.status()
    print(f"{time.time()-t0:5.0f}s {st.state} done={st.total_wanted_done/1e6:.1f}/{st.total_wanted/1e6:.1f} MB "
          f"peers={st.num_peers} up={st.total_upload/1e6:.1f} MB", flush=True)
    for al in s.pop_alerts():
        if al.category() & lt.alert_category.error:
            print("ALERT", al.message(), flush=True)
    if a.seconds and time.time() - t0 > a.seconds:
        break
    if not a.seconds and st.is_finished:
        break
    if int(time.time() - t0) % 10 < 2:
        h.force_reannounce()
    time.sleep(2)

if a.only:
    top = os.path.join(a.save, ti.name())
    extra = []
    for root, _, files in os.walk(top):
        for n in files:
            rel = os.path.relpath(os.path.join(root, n), top)
            if not any(rel.startswith(x) for x in a.only):
                extra.append(rel)
    print(f"unwanted files on disk: {len(extra)}")
    for e in extra[:20]:
        print("  ", e)
