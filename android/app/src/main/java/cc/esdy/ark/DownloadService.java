package cc.esdy.ark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;


import org.libtorrent4j.TcpEndpoint;
import org.libtorrent4j.TorrentStatus;

/** Downloads and verifies selected subjects in a foreground service. */
public class DownloadService extends Service {
    static final String CHANNEL = "downloads";
    public static final String ACTION_RESUME = "cc.esdy.ark.RESUME_DOWNLOADS";
    private static volatile boolean running;
    private static volatile DownloadService instance;
    private final Object controls = new Object();
    private volatile boolean stopRequested, timedOut, restartRequested, destroyed;
    private int latestStartId;

    public static boolean isRunning() { return running; }

    /** A pause is a control request to the existing worker, not a new foreground-service launch. */
    public static void pause(Context context) {
        LibraryEngine e = LibraryEngine.get(context);
        e.setPausedByUser(true);
        e.setPausedByLimit(false);
        DownloadService service = instance;
        if (service != null) {
            synchronized (service.controls) {
                service.stopRequested = true;
                service.restartRequested = false;
                service.timedOut = false;
                e.cancelTransfers();
            }
        }
        e.log("Download paused. Resume when you are ready");
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private Notification note(String text, int pct) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Estudely Ark")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pi)
                .setOngoing(true);
        if (pct >= 0) b.setProgress(100, pct, false);
        return b.build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, note("Starting", -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        LibraryEngine e = LibraryEngine.get(this);
        // Starting or resuming is explicit. Merely opening MainActivity must not clear a manual pause.
        e.setPausedByUser(false);
        e.setPausedByLimit(false);
        synchronized (controls) {
            latestStartId = startId;
            if (running) {
                // The old worker may still be writing its resume data. Do not race a second torrent session.
                restartRequested = true;
                return START_NOT_STICKY;
            }
            destroyed = false;
            stopRequested = timedOut = restartRequested = false;
            e.cancel = false;
            running = true;
        }
        if (BuildConfig.DEBUG && intent != null) {
            e.testWebSeedOnly = intent.getBooleanExtra("webSeedOnly", false);
            e.testPeers.clear();
            String peer = intent.getStringExtra("directPeer");
            if (peer != null && peer.lastIndexOf(':') > 0)
                e.testPeers.add(new TcpEndpoint(peer.substring(0, peer.lastIndexOf(':')), Integer.parseInt(peer.substring(peer.lastIndexOf(':') + 1))));
        }
        new Thread(this::run, "download").start();
        return START_NOT_STICKY;
    }

    private void run() {
        LibraryEngine e = LibraryEngine.get(this);
        for (;;) {
            download(e);
            synchronized (controls) {
                if (restartRequested && !destroyed && !e.pausedByUser()
                        && !e.verified().containsAll(e.selected())) {
                    restartRequested = stopRequested = timedOut = false;
                    e.cancel = false;
                    continue;
                }
                running = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                if (timedOut && !e.pausedByUser()) notifyTimeLimit(e);
                stopSelfResult(latestStartId);
                return;
            }
        }
    }

    private void download(LibraryEngine e) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        String finalMessage = null;
        try {
            if (SeedJob.isActive()) { e.log("Pausing sharing for the download"); SeedJob.stopAndWait(); }
            if (stopRequested) return;
            e.start();
            long lastDone = -1, lastProgress = System.currentTimeMillis(), lastFallback = 0;
            boolean checked = false;
            while (!stopRequested) {
                if (!e.mobileDataAllowed() && !LibraryEngine.unmetered(this)) {
                    e.log("On mobile data: waiting for Wi-Fi");
                    e.stop();
                    nm.notify(1, note("Waiting for Wi-Fi", -1));
                    while (!stopRequested && !e.mobileDataAllowed() && !LibraryEngine.unmetered(this)) waitFor(1000);
                    if (stopRequested) break;
                    e.log("Wi-Fi is back");
                    e.start();
                    checked = false;
                    lastProgress = System.currentTimeMillis();
                    continue;
                }
                TorrentStatus s = e.status();
                if (s == null) break;
                boolean checking = s.state() == TorrentStatus.State.CHECKING_FILES || s.state() == TorrentStatus.State.CHECKING_RESUME_DATA;
                if (!checking && !checked) { checked = true; e.unverifyIncomplete(); }
                if (!checking && e.allWantedDone()) {
                    // Re-check completion between subjects: the user can add a new download during verification.
                    for (ModuleCatalog.Module m : e.catalog.modules) {
                        if (stopRequested || !e.allWantedDone()) break;
                        if (!e.selected().contains(m.id) || e.verified().contains(m.id)) continue;
                        nm.notify(1, note("Checking " + m.title, -1));
                        e.verify(m);
                    }
                    if (stopRequested) break;
                    if (e.allWantedDone()) {
                        boolean good = e.verified().containsAll(e.selected());
                        e.log(good ? "All selected subjects downloaded and checked" : "Some subjects could not be checked. Download again to repair");
                        e.setPausedByLimit(false);
                        break;
                    }
                    checked = false;
                    continue;
                }
                if (!waitFor(2000)) break;
                long[] wp = e.wantedProgress();
                long done = wp[0], total = Math.max(1, wp[1]);
                int pct = (int) (100 * done / total);
                nm.notify(1, note(String.format("Downloading: %d%% of %.1f GB · %d peer(s)", pct, total / 1e9, s.numPeers()), pct));
                if (done != lastDone) { lastDone = done; lastProgress = System.currentTimeMillis(); }
                boolean stuck = !checking && System.currentTimeMillis() - lastProgress > 45_000;
                if (stuck && System.currentTimeMillis() - lastFallback > 10 * 60_000) {
                    lastFallback = System.currentTimeMillis();
                    e.log("Other copies are slow; trying the Internet Archive");
                    nm.notify(1, note("Fetching from the Internet Archive", -1));
                    try {
                        int n = e.fetchFromWebSeed();
                        e.log("Got " + n + " file(s) from the Internet Archive; checking them");
                    } catch (InterruptedException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        e.log("Internet Archive copy not available (" + ex.getMessage() + "); carrying on with other copies");
                    }
                    lastProgress = System.currentTimeMillis();
                }
            }
        } catch (InterruptedException ex) {
            if (!stopRequested) finalMessage = "Download interrupted; you can resume it";
        } catch (Exception ex) {
            if (!stopRequested) finalMessage = "Download stopped: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
        } finally {
            e.stop();
            if (finalMessage != null) e.log(finalMessage);
        }
    }

    /** Short waits keep pause responsive without interrupting a resume-data write. */
    private boolean waitFor(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (!stopRequested && System.currentTimeMillis() < end) Thread.sleep(Math.min(250, Math.max(1, end - System.currentTimeMillis())));
        return !stopRequested;
    }

    private void notifyTimeLimit(LibraryEngine e) {
        e.setPausedByLimit(true);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Download paused")
                .setContentText("Android limits background downloads. Open Estudely Ark to continue.")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(2, n);
    }

    @Override public void onTimeout(int startId, int fgsType) {
        LibraryEngine e = LibraryEngine.get(this);
        e.log("Android's background time limit was reached; open the app to continue");
        synchronized (controls) {
            timedOut = stopRequested = true;
            restartRequested = false;
            e.cancelTransfers();
        }
    }

    @Override public void onDestroy() {
        synchronized (controls) {
            destroyed = stopRequested = true;
            restartRequested = false;
            LibraryEngine.get(this).cancelTransfers();
            if (instance == this) instance = null;
        }
        super.onDestroy();
    }
}
