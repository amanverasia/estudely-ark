package cc.esdy.ark;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.libtorrent4j.AddTorrentParams;
import org.libtorrent4j.AlertListener;
import org.libtorrent4j.Entry;
import org.libtorrent4j.FileStorage;
import org.libtorrent4j.Priority;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SessionParams;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.TcpEndpoint;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.alerts.SaveResumeDataAlert;
import org.libtorrent4j.swig.announce_entry;
import org.libtorrent4j.swig.settings_pack;
import org.libtorrent4j.swig.torrent_flags_t;

/**
 * The app's one torrent session. Holds the user's module choices (persisted), downloads and seeds the chosen
 * folders of the v1.0 torrent, falls back to the web seed over Android's HTTPS when no peer delivers, and
 * verifies finished modules against the signed manifest with the built-in release key.
 */
public final class LibraryEngine {
    public static final String TORRENT_ASSET = "Estudely-Ark-v1.0-2026-11.torrent";
    private static LibraryEngine instance;

    public static synchronized LibraryEngine get(Context c) {
        if (instance == null) instance = new LibraryEngine(c.getApplicationContext());
        return instance;
    }

    public enum State { NOT_SELECTED, QUEUED, DOWNLOADING, CHECKING, READY, FAILED }

    public final ModuleCatalog catalog;
    public final TorrentInfo ti;
    private final byte[] torrentBytes;
    private final File saveDir, stateDir, resumeFile;
    private final SharedPreferences prefs;
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final List<String> trackers = new ArrayList<>(), webSeeds = new ArrayList<>();
    private SessionManager session;
    private TorrentHandle handle;
    /** Debug test switches (ignored in release builds). */
    public boolean testWebSeedOnly;
    /** Set to stop a running web-seed fetch quickly (Android's background time limit). */
    public volatile boolean cancel;
    private volatile java.util.concurrent.CountDownLatch resumeWritten;
    public final List<TcpEndpoint> testPeers = new ArrayList<>();

    private LibraryEngine(Context c) {
        try (InputStream in = c.getAssets().open(TORRENT_ASSET)) { torrentBytes = in.readAllBytes(); }
        catch (IOException e) { throw new IllegalStateException(e); }
        ti = new TorrentInfo(torrentBytes);
        saveDir = c.getFilesDir();
        stateDir = new File(c.getFilesDir(), "engine");
        stateDir.mkdirs();
        resumeFile = new File(stateDir, "resume.dat");
        prefs = c.getSharedPreferences("library", Context.MODE_PRIVATE);
        FileStorage fs = ti.files();
        List<String> paths = new ArrayList<>();
        List<Long> sizes = new ArrayList<>();
        String top = ti.name() + "/";
        for (int i = 0; i < fs.numFiles(); i++) {
            if (fs.padFileAt(i)) continue;
            paths.add(fs.filePath(i).substring(top.length()));
            sizes.add(fs.fileSize(i));
        }
        catalog = ModuleCatalog.build(paths, sizes);
        Map<String, Entry> d = Entry.bdecode(torrentBytes).dictionary();
        // libtorrent 2.1 keeps trackers and web seeds outside torrent_info; read them from the file.
        Set<String> t = new LinkedHashSet<>();
        if (d.containsKey("announce-list")) for (Entry tier : d.get("announce-list").list()) for (Entry u : tier.list()) t.add(u.string());
        if (d.containsKey("announce")) t.add(d.get("announce").string());
        trackers.addAll(t);
        if (d.containsKey("url-list")) {
            Entry ul = d.get("url-list");
            try { for (Entry u : ul.list()) webSeeds.add(u.string()); } catch (Exception e) { webSeeds.add(ul.string()); }
        }
    }

    // ---- logging ----

    public void addListener(Consumer<String> l) { listeners.add(l); }
    public void removeListener(Consumer<String> l) { listeners.remove(l); }

    void log(String s) {
        Log.i("EstudelyArk", s);
        for (Consumer<String> l : listeners) l.accept(s);
    }

    // ---- choices ----

    public Set<String> selected() { return new HashSet<>(prefs.getStringSet("selected", new HashSet<>())); }
    public Set<String> verified() { return new HashSet<>(prefs.getStringSet("verified", new HashSet<>())); }

    public synchronized void setSelected(Set<String> ids) {
        Set<String> v = verified();
        v.retainAll(ids);
        prefs.edit().putStringSet("selected", new HashSet<>(ids)).putStringSet("verified", v).apply();
        if (handle != null) handle.prioritizeFiles(priorities(ids));
    }

    public boolean mobileDataAllowed() { return prefs.getBoolean("mobileData", false); }
    public void setMobileDataAllowed(boolean on) { prefs.edit().putBoolean("mobileData", on).apply(); }

    /** True on Wi-Fi or another unmetered network. */
    public static boolean unmetered(Context c) {
        android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    /** Set when Android's foreground-service time limit stopped a download; cleared when it resumes or finishes. */
    public boolean pausedByLimit() { return prefs.getBoolean("pausedByLimit", false); }
    public void setPausedByLimit(boolean on) { prefs.edit().putBoolean("pausedByLimit", on).apply(); }

    public boolean sharingOn() { return prefs.getBoolean("sharing", false); }
    public void setSharing(boolean on) { prefs.edit().putBoolean("sharing", on).apply(); }
    /** Bytes this phone has uploaded to others, all time (libtorrent keeps the count in its resume data). */
    public long uploaded() { return prefs.getLong("uploaded", 0); }
    public synchronized void recordUpload() {
        TorrentStatus s = status();
        if (s != null && s.allTimeUpload() > uploaded()) prefs.edit().putLong("uploaded", s.allTimeUpload()).apply();
    }

    static String selectionKey(Set<String> ids) { return String.join(",", new java.util.TreeSet<>(ids)); }

    private void markVerified(String id, boolean ok) {
        Set<String> v = verified();
        if (ok) v.add(id); else v.remove(id);
        prefs.edit().putStringSet("verified", v).apply();
    }

    /** Relative path inside the pack folder, or null for pad files. */
    String rel(int i) {
        FileStorage fs = ti.files();
        if (fs.padFileAt(i)) return null;
        return fs.filePath(i).substring(ti.name().length() + 1);
    }

    boolean wanted(String rel, Set<String> ids) {
        if (rel == null || ids.isEmpty()) return false;
        for (String a : ModuleCatalog.ALWAYS) if (rel.equals(a)) return true;
        for (String id : ids) { ModuleCatalog.Module m = catalog.find(id); if (m != null && m.contains(rel)) return true; }
        return false;
    }

    Priority[] priorities(Set<String> ids) {
        Priority[] p = new Priority[ti.files().numFiles()];
        for (int i = 0; i < p.length; i++) p[i] = wanted(rel(i), ids) ? Priority.DEFAULT : Priority.IGNORE;
        return p;
    }

    public File packDir() { return new File(saveDir, ti.name()); }

    // ---- session ----

    public synchronized boolean isRunning() { return session != null; }

    public synchronized void start() {
        if (session != null) return;
        boolean webOnly = BuildConfig.DEBUG && testWebSeedOnly;
        SettingsPack s = new SettingsPack();
        s.setEnableDht(!webOnly);
        s.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), !webOnly);
        session = new SessionManager();
        session.addListener(new AlertListener() {
            @Override public int[] types() { return null; }
            @Override public void alert(Alert<?> a) {
                if (a instanceof SaveResumeDataAlert) writeResume(((SaveResumeDataAlert) a).params());
            }
        });
        // POSIX disk I/O: no disk/hash thread pool. With libtorrent's default (mmap, threaded) back end the app
        // aborted seconds into hashing (Scudo "invalid chunk state", two pool threads freeing the same address;
        // Pixel 9a and emulator, 17 Nov).
        SessionParams sp = new SessionParams(s);
        sp.setPosixDiskIO();
        session.start(sp);
        Set<String> ids = selected();
        // Resume data carries the file priorities it was saved with, and libtorrent applies them after our
        // prioritizeFiles call (seen on a Pixel: four new modules ignored, the old Food-only selection "100%").
        // So resume data saved for a different selection is dropped; the check from disk takes seconds.
        if (resumeFile.isFile() && !selectionKey(ids).equals(prefs.getString("resumeFor", ""))) {
            resumeFile.delete();
            log("Checking the files already on this phone");
        }
        Priority[] prio = priorities(ids);
        List<TcpEndpoint> peers = BuildConfig.DEBUG && !webOnly ? testPeers : new ArrayList<>();
        session.download(ti, saveDir, resumeFile.isFile() ? resumeFile : null, prio, peers, new torrent_flags_t());
        handle = session.find(ti.infoHashes().getBest());
        if (handle == null) { log("Could not add the torrent"); return; }
        handle.prioritizeFiles(prio); // resume data can carry an older selection
        if (!webOnly) for (String u : trackers) handle.swig().add_tracker(new announce_entry(u));
        // No add_url_seed: libtorrent4j's bundled OpenSSL fails on Android ("asio.ssl init fail") and, worse,
        // crashed the app (Scudo "invalid chunk state", double free inside OpenSSL on libtorrent's network
        // thread; emulator and Pixel 9a, 17 Nov). The web seed is fetched with Android's HTTPS instead
        // (fetchFromWebSeed). All trackers are UDP, so libtorrent never needs TLS.
        // Older resume data still lists the web seed, so remove it explicitly.
        for (String u : webSeeds) handle.swig().remove_url_seed(u);
        handle.resume();
        if (!webOnly) handle.forceReannounce();
        log("Connecting to other copies of the library (" + ids.size() + " part(s) selected)" + (webOnly ? " (TEST: web seed only)" : ""));
    }

    public synchronized void stop() {
        if (session == null) return;
        recordUpload();
        resumeWritten = new java.util.concurrent.CountDownLatch(1);
        saveResume();
        try { resumeWritten.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        session.stop();
        session = null;
        handle = null;
        log("Stopped");
    }

    public synchronized TorrentStatus status() { return handle == null ? null : handle.status(); }

    /** Bytes of wanted files that are complete, per module id. */
    public synchronized long[] moduleDone(List<ModuleCatalog.Module> mods) {
        long[] out = new long[mods.size()];
        if (handle == null) return out;
        long[] prog = handle.fileProgress();
        for (int i = 0; i < prog.length; i++) {
            String r = rel(i);
            if (r == null) continue;
            for (int k = 0; k < mods.size(); k++) if (mods.get(k).contains(r)) out[k] += prog[i];
        }
        return out;
    }

    /** Drops the "verified" mark of selected modules that libtorrent finds incomplete on disk (files deleted or damaged). */
    public synchronized void unverifyIncomplete() {
        long[] done = moduleDone(catalog.modules);
        Set<String> v = verified();
        for (int k = 0; k < done.length; k++) {
            ModuleCatalog.Module m = catalog.modules.get(k);
            if (v.contains(m.id) && done[k] < m.bytes) { markVerified(m.id, false); log(m.title + " is incomplete on disk; it will be downloaded and checked again"); }
        }
    }

    public State state(ModuleCatalog.Module m) {
        if (!selected().contains(m.id)) return State.NOT_SELECTED;
        if (verified().contains(m.id)) return State.READY;
        if (prefs.getBoolean("failed:" + m.id, false)) return State.FAILED;
        return isRunning() ? State.DOWNLOADING : State.QUEUED;
    }

    /**
     * True when every file of the current selection is complete on disk. Uses per-file progress, not
     * TorrentStatus.totalWanted: right after start the status can still reflect the selection stored in the
     * resume data (seen on a Pixel: old selection complete, so four new modules were "done" before downloading).
     */
    /** {bytes done, bytes wanted} for the current selection, from per-file progress. */
    public synchronized long[] wantedProgress() {
        long[] r = new long[2];
        if (handle == null) return r;
        Set<String> ids = selected();
        long[] prog = handle.fileProgress();
        FileStorage fs = ti.files();
        for (int i = 0; i < prog.length; i++) if (wanted(rel(i), ids)) { r[0] += prog[i]; r[1] += fs.fileSize(i); }
        return r;
    }

    public synchronized boolean allWantedDone() {
        if (handle == null) return false;
        Set<String> ids = selected();
        long[] prog = handle.fileProgress();
        FileStorage fs = ti.files();
        boolean any = false;
        for (int i = 0; i < prog.length; i++) {
            if (!wanted(rel(i), ids)) continue;
            any = true;
            if (prog[i] < fs.fileSize(i)) return false;
        }
        return any;
    }

    void saveResume() { if (handle != null && handle.isValid()) handle.saveResumeData(); }

    private void writeResume(AddTorrentParams p) {
        try {
            File tmp = new File(stateDir, "resume.tmp");
            Files.write(tmp.toPath(), AddTorrentParams.writeResumeDataBuf(p));
            if (!tmp.renameTo(resumeFile)) throw new IOException("rename failed");
            prefs.edit().putString("resumeFor", selectionKey(selected())).apply();
        } catch (Exception e) { log("Could not save resume data: " + e); }
        java.util.concurrent.CountDownLatch l = resumeWritten;
        if (l != null) l.countDown();
    }

    // ---- web seed fallback ----

    /**
     * libtorrent4j's HTTPS web-seed connections fail on Android ("asio.ssl init fail", its bundled OpenSSL), so
     * when no peer delivers, fetch each wanted file that libtorrent doesn't already have from the web seed with
     * Android's HTTPS. Downloads go to "<file>.part" and resume with Range requests; complete files replace
     * the originals. Files are never swapped under a live torrent: libtorrent4j 2.1 crashed (heap corruption in
     * its hashing thread) re-checking files replaced while the torrent was paused. So the session stops first and
     * the resume data is deleted; the restart then checks everything on disk from scratch, which also picks up
     * files fetched before a crash or kill instead of downloading them again.
     */
    static String seedUrl(String base, String path) {
        StringBuilder url = new StringBuilder(base);
        for (String seg : path.split("/")) url.append(android.net.Uri.encode(seg)).append('/');
        url.setLength(url.length() - 1);
        return url.toString();
    }

    public int fetchFromWebSeed() throws Exception {
        if (webSeeds.isEmpty()) return 0;
        TorrentHandle h;
        synchronized (this) { h = handle; }
        if (h == null) return 0;
        String base = webSeeds.get(0);
        if (!base.endsWith("/")) base += "/";
        Set<String> ids = selected();
        long[] prog = h.fileProgress();
        FileStorage fs = ti.files();
        List<Integer> todo = new ArrayList<>();
        for (int i = 0; i < fs.numFiles(); i++) if (wanted(rel(i), ids) && prog[i] < fs.fileSize(i)) todo.add(i);
        if (todo.isEmpty()) return 0;
        // Probe first, so an unreachable web seed (offline, or the item withdrawn: archive.org answered 403
        // on 17 Nov) doesn't cost a session restart and a full re-check.
        HttpURLConnection probe = (HttpURLConnection) new URL(seedUrl(base, fs.filePath(todo.get(0)))).openConnection();
        probe.setConnectTimeout(20000);
        probe.setReadTimeout(20000);
        probe.setRequestProperty("Range", "bytes=0-0");
        int pc = probe.getResponseCode();
        probe.disconnect();
        if (pc != 200 && pc != 206) throw new IOException("web seed answered HTTP " + pc);
        cancel = false;
        stop();
        if (resumeFile.exists() && !resumeFile.delete()) log("Could not delete resume data");
        int fetched = 0;
        try {
            for (int i : todo) {
                String path = fs.filePath(i);
                long size = fs.fileSize(i);
                File out = new File(saveDir, path), part = new File(saveDir, path + ".part");
                out.getParentFile().mkdirs();
                String url = seedUrl(base, path);
                for (int attempt = 1; ; attempt++) {
                    long have = part.isFile() ? part.length() : 0;
                    if (have > size) { part.delete(); have = 0; }
                    if (have == size) break;
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setInstanceFollowRedirects(true);
                        c.setConnectTimeout(30000);
                        c.setReadTimeout(60000);
                        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                        int code = c.getResponseCode();
                        if (code == 200 && have > 0) { have = 0; } // server ignored Range: start over
                        else if (code != 200 && code != 206) throw new IOException("HTTP " + code);
                        try (InputStream in = c.getInputStream(); RandomAccessFile o = new RandomAccessFile(part, "rw")) {
                            o.setLength(have);
                            o.seek(have);
                            byte[] buf = new byte[1 << 16];
                            for (int r; (r = in.read(buf)) > 0; ) {
                                if (cancel) throw new InterruptedException("cancelled");
                                o.write(buf, 0, r);
                            }
                        }
                    } catch (IOException e) {
                        if (attempt >= 5) throw new IOException(rel(i) + ": " + e.getMessage(), e);
                        log("Connection dropped (" + e.getMessage() + "); resuming " + new File(rel(i)).getName());
                        Thread.sleep(3000L * attempt);
                    }
                }
                if (out.exists() && !out.delete()) throw new IOException("cannot replace " + out);
                if (!part.renameTo(out)) throw new IOException("cannot move " + part);
                log(String.format("Web seed: fetched %s (%.1f MB)", rel(i), size / 1e6));
                fetched++;
            }
        } finally {
            start(); // no resume data, so libtorrent checks the files on disk
        }
        return fetched;
    }

    // ---- verification ----

    /** Checks one module: manifest signature (built-in key), then size and SHA-256 of each of its files. */
    public boolean verify(ModuleCatalog.Module m) {
        try {
            File dir = packDir();
            byte[] manifest = Files.readAllBytes(new File(dir, "MANIFEST.tsv").toPath());
            String sig = new String(Files.readAllBytes(new File(dir, "MANIFEST.tsv.sig").toPath()), StandardCharsets.US_ASCII);
            SshSig.verify(manifest, sig, SshSig.releaseKey(), SshSig.NAMESPACE);
            int ok = 0, bad = 0;
            for (String line : new String(manifest, StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] c = line.split("\t");
                if (!m.contains(c[0])) continue;
                File f = new File(dir, c[0]);
                if (!f.isFile() || f.length() != Long.parseLong(c[1]) || !sha256(f).equals(c[2])) { bad++; log("Verify: bad or missing " + c[0]); }
                else ok++;
            }
            boolean good = bad == 0 && ok > 0;
            markVerified(m.id, good);
            prefs.edit().putBoolean("failed:" + m.id, !good).apply();
            log(m.title + ": signature GOOD, " + ok + " file(s) checked" + (bad == 0 ? ", all good" : ", " + bad + " damaged or missing. Download again to repair"));
            return good;
        } catch (Exception e) {
            markVerified(m.id, false);
            prefs.edit().putBoolean("failed:" + m.id, true).apply();
            log(m.title + " could not be checked: " + e.getMessage());
            return false;
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[1 << 20];
        try (InputStream in = new FileInputStream(f)) { for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n); }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** Free bytes the app may still use for downloads. */
    public static long allocatableBytes(Context c) {
        try {
            android.os.storage.StorageManager sm = c.getSystemService(android.os.storage.StorageManager.class);
            return sm.getAllocatableBytes(sm.getUuidForPath(c.getFilesDir()));
        } catch (Exception e) {
            return c.getFilesDir().getUsableSpace();
        }
    }

    /** Bytes still to download for the given selection (files libtorrent doesn't have yet). */
    public synchronized long bytesStillNeeded(Set<String> ids) {
        FileStorage fs = ti.files();
        long[] prog = handle != null ? handle.fileProgress() : null;
        long need = 0;
        for (int i = 0; i < fs.numFiles(); i++) {
            if (!wanted(rel(i), ids)) continue;
            long have = prog != null ? prog[i] : (new File(saveDir, fs.filePath(i)).length() == fs.fileSize(i) ? fs.fileSize(i) : 0);
            need += fs.fileSize(i) - have;
        }
        return need;
    }

    /** Deletes the files of modules that are no longer selected. */
    public synchronized void deleteUnselected() {
        Set<String> ids = selected();
        FileStorage fs = ti.files();
        for (int i = 0; i < fs.numFiles(); i++) {
            String r = rel(i);
            if (r == null || wanted(r, ids)) continue;
            File f = new File(saveDir, fs.filePath(i));
            if (f.exists() && f.delete()) log("Removed " + r);
        }
    }
}
