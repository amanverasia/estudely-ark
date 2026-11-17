#!/usr/bin/env python3
"""Always-on seeder for an Estudely Ark release: downloads the chosen part of the pack, then seeds it.

  python scripts/seed.py TORRENT SAVE_DIR [--core] [--min-free-gb 20] [--status FILE] [--interface IFACE] [--port N]

--core downloads and seeds only the core: the top-level files, KEYS, Core/ and Shared-Readers/
(AI and Extras stay at priority 0). Built for a shared server:
  - small memory footprint (no extra disk cache settings beyond libtorrent's defaults, small send buffers)
  - no UPnP/NAT-PMP (servers have no home router to ask)
  - stops cleanly if free disk under SAVE_DIR drops below --min-free-gb
Writes a one-line status every 5 minutes to --status (default SAVE_DIR/../seed-status.txt).
Resume data is saved next to the status file, so restarts don't re-check everything.
"""
import argparse, os, shutil, signal, sys, time
import libtorrent as lt

ap = argparse.ArgumentParser()
ap.add_argument("torrent")
ap.add_argument("save")
ap.add_argument("--core", action="store_true")
ap.add_argument("--min-free-gb", type=float, default=20)
ap.add_argument("--status")
ap.add_argument("--interface", help="bind only to this network interface or IP (e.g. eth0); default: all")
ap.add_argument("--port", type=int, default=6881)
a = ap.parse_args()

save = os.path.abspath(a.save)
os.makedirs(save, exist_ok=True)
state_dir = os.path.dirname(save.rstrip("/"))
status_path = a.status or os.path.join(state_dir, "seed-status.txt")
resume_path = os.path.join(state_dir, "seed-resume.dat")

ses = lt.session({
    "user_agent": "EstudelyArkSeeder/1.0 libtorrent/" + lt.__version__,
    "listen_interfaces": f"{a.interface}:{a.port}" if a.interface else f"0.0.0.0:{a.port},[::]:{a.port}",
    "outgoing_interfaces": a.interface or "",
    "enable_dht": True, "enable_lsd": False, "enable_upnp": False, "enable_natpmp": False,
    "alert_mask": lt.alert_category.error | lt.alert_category.status | lt.alert_category.storage,
    "send_buffer_watermark": 512 * 1024,
    "connections_limit": 200,
})

ti = lt.torrent_info(a.torrent)
params = None
if os.path.exists(resume_path):
    try:
        params = lt.read_resume_data(open(resume_path, "rb").read())
        params.ti = ti
    except Exception:
        params = None
if params is None:
    params = lt.add_torrent_params()
    params.ti = ti
params.save_path = save

fs = ti.files()
prio = []
for i in range(fs.num_files()):
    if fs.file_flags(i) & lt.file_storage.flag_pad_file:
        prio.append(0); continue
    rel = fs.file_path(i).split("/", 1)[1] if "/" in fs.file_path(i) else fs.file_path(i)
    keep = (not a.core) or "/" not in rel or rel.split("/")[0] in ("Core", "Shared-Readers", "KEYS")
    prio.append(4 if keep else 0)
params.file_priorities = prio
h = ses.add_torrent(params)
h.prioritize_files(prio)  # resume data can carry the old selection; apply this run's explicitly
wanted = sum(fs.file_size(i) for i in range(fs.num_files()) if prio[i])
print(f"{ti.name()}: seeding {'core' if a.core else 'all'} ({wanted / 1e9:.2f} GB) into {save}", flush=True)

stop = False
def on_signal(*_):
    global stop
    stop = True
signal.signal(signal.SIGTERM, on_signal)
signal.signal(signal.SIGINT, on_signal)

def save_resume():
    h.save_resume_data(lt.torrent_handle.save_info_dict)
    t = time.time()
    while time.time() - t < 10:
        for al in ses.pop_alerts():
            if isinstance(al, lt.save_resume_data_alert):
                with open(resume_path + ".tmp", "wb") as f:
                    f.write(lt.write_resume_data_buf(al.params))
                os.replace(resume_path + ".tmp", resume_path)
                return
        time.sleep(0.2)

last_status = last_resume = 0
reason = "stopped by signal"
while not stop:
    for al in ses.pop_alerts():
        if al.category() & lt.alert_category.error:
            print("alert:", al.message(), flush=True)
    now = time.time()
    if now - last_status >= 300:
        s = h.status()
        free = shutil.disk_usage(save).free / 1e9
        line = (f"{time.strftime('%Y-%m-%d %H:%M:%S')} state={s.state} done={s.total_wanted_done / 1e9:.2f}/{s.total_wanted / 1e9:.2f}GB "
                f"peers={s.num_peers} seeds={s.num_seeds} up={s.upload_rate / 1e3:.0f}kB/s down={s.download_rate / 1e3:.0f}kB/s "
                f"uploaded_total={s.all_time_upload / 1e9:.2f}GB disk_free={free:.0f}GB")
        with open(status_path, "w") as f:
            f.write(line + "\n")
        print(line, flush=True)
        if free < a.min_free_gb:
            reason = f"free disk {free:.1f} GB is below the {a.min_free_gb} GB floor"
            break
        last_status = now
    if now - last_resume >= 1800:
        save_resume(); last_resume = now
    time.sleep(2)

save_resume()
print("exiting:", reason, flush=True)
with open(status_path, "a") as f:
    f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} exited: {reason}\n")
