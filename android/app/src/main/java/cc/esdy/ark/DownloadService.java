package cc.esdy.ark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import java.util.Set;

import org.libtorrent4j.TcpEndpoint;
import org.libtorrent4j.TorrentStatus;

/**
 * Downloads the selected modules in a foreground service (Android freezes background apps and cuts their
 * network; seen on a Pixel 9a about 75 s after leaving the screen), then verifies them and stops.
 */
public class DownloadService extends Service {
    static final String CHANNEL = "downloads";
    private static volatile boolean running;
    private volatile boolean stopRequested, timedOut;

    public static boolean isRunning() { return running; }

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
        if (running) return START_NOT_STICKY;
        running = true;
        LibraryEngine e = LibraryEngine.get(this);
        if (BuildConfig.DEBUG && intent != null) {
            e.testWebSeedOnly = intent.getBooleanExtra("webSeedOnly", false);
            e.testPeers.clear();
            String peer = intent.getStringExtra("directPeer"); // "host:port", debug test hook
            if (peer != null && peer.lastIndexOf(':') > 0)
                e.testPeers.add(new TcpEndpoint(peer.substring(0, peer.lastIndexOf(':')), Integer.parseInt(peer.substring(peer.lastIndexOf(':') + 1))));
        }
        new Thread(this::run, "download").start();
        return START_NOT_STICKY;
    }

    private void run() {
        LibraryEngine e = LibraryEngine.get(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        try {
            if (SeedJob.isActive()) { e.log("Pausing sharing for the download"); SeedJob.stopAndWait(); }
            e.start();
            long lastDone = -1, lastProgress = System.currentTimeMillis(), lastFallback = 0;
            boolean checked = false;
            while (!stopRequested) {
                if (!e.mobileDataAllowed() && !LibraryEngine.unmetered(this)) {
                    // Mobile data costs money: pause until Wi-Fi is back unless the user allowed it.
                    e.log("On mobile data: waiting for Wi-Fi");
                    e.stop();
                    nm.notify(1, note("Waiting for Wi-Fi", -1));
                    while (!stopRequested && !e.mobileDataAllowed() && !LibraryEngine.unmetered(this)) Thread.sleep(5000);
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
                if (!checking && e.allWantedDone()) break;
                Thread.sleep(2000);
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
            if (!stopRequested && e.allWantedDone()) {
                Set<String> verified = e.verified();
                for (ModuleCatalog.Module m : e.catalog.modules) {
                    if (!e.selected().contains(m.id) || verified.contains(m.id)) continue;
                    nm.notify(1, note("Checking " + m.title, -1));
                    e.verify(m);
                }
                e.log("All selected modules downloaded and checked");
                e.setPausedByLimit(false);
            }
        } catch (Exception ex) {
            e.log("Download stopped: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
        } finally {
            e.stop();
            running = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            if (timedOut) {
                // Android resets the budget when the user opens the app; MainActivity then carries on.
                e.setPausedByLimit(true);
                Notification n = new Notification.Builder(this, CHANNEL)
                        .setContentTitle("Download paused")
                        .setContentText("Android limits background downloads. Open Estudely Ark to continue.")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                        .setAutoCancel(true)
                        .build();
                nm.notify(2, n);
            }
            stopSelf();
        }
    }

    /** Android 15+: the daily dataSync time budget ran out. Stop within seconds or the app is killed. */
    @Override public void onTimeout(int startId, int fgsType) {
        LibraryEngine e = LibraryEngine.get(this);
        e.log("Android's background time limit was reached; open the app to continue");
        timedOut = true;
        stopRequested = true;
        e.cancel = true;
    }

    @Override public void onDestroy() { stopRequested = true; LibraryEngine.get(this).cancel = true; super.onDestroy(); }
}
